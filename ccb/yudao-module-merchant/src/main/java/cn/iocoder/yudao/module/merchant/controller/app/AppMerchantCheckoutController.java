package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MemberShopRelDO;
import cn.iocoder.yudao.module.merchant.service.MemberShopRelService;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderUpdatePriceReqVO;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderCreateReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderUpdateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * C 端 - 结账 wrapper：单事务完成「订单创建 + 店铺余额抵扣 + 订单改价」。
 *
 * <p>解决 Phase 4 的 CRIT-2：原先前端先 trade/order/create 再 deduct-for-order
 * 是两步非原子，且 trade_order.payPrice 永远不被余额更新，导致用户付了余额还会被
 * 微信收全款。本端点把三件事捆在 @Transactional 里：</p>
 *
 * <ol>
 *   <li>预校验：余额 ≥ 抵扣金额，且抵扣金额 < 应付金额（必须留 ≥1 分线上支付）</li>
 *   <li>调用 {@code tradeOrderUpdateService.createOrder(...)} 生成订单</li>
 *   <li>{@code MemberShopRelService.deductBalanceForOrder} 扣余额 + 写日志（UNIQUE 幂等）</li>
 *   <li>{@code tradeOrderUpdateService.updateOrderPrice(adjust=-balanceFen)}
 *       原子改 trade_order + trade_order_item + pay_order</li>
 * </ol>
 *
 * <p>任意一步失败 → 整个事务回滚，余额恢复、订单不入库。</p>
 */
@Tag(name = "C 端 - 结账（含店铺余额抵扣）")
@RestController
@RequestMapping("/merchant/mini/checkout")
@Validated
public class AppMerchantCheckoutController {

    @Resource
    private TradeOrderUpdateService tradeOrderUpdateService;
    @Resource
    private MemberShopRelService memberShopRelService;
    @Resource
    private cn.iocoder.yudao.module.merchant.service.promo.ProductPromoConfigService productPromoConfigService;
    @Resource
    private cn.iocoder.yudao.module.merchant.service.promo.PromoQueueService promoQueueService;
    @Resource
    private cn.iocoder.yudao.module.trade.service.order.TradeOrderQueryService tradeOrderQueryService;
    @Resource
    private cn.iocoder.yudao.module.product.service.sku.ProductSkuService productSkuService;

    @PostMapping("/submit")
    @Operation(summary = "提交订单（支持店铺余额抵扣）")
    @Transactional(rollbackFor = Exception.class)
    @cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore  // C 端跨店下单：先 ignore，再按 body.tenantId 切 ctx
    public CommonResult<SubmitRespVO> submit(@Valid @RequestBody SubmitReqVO req) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null || userId <= 0) {
            throw ServiceExceptionUtil.exception0(1_031_001_010, "请先登录");
        }
        // 关键：C 端用户 token tenant ≠ 商户租户。
        // 前端不能传 header 'tenant-id' 商户租户（会被 TenantSecurityWebFilter 拦 403）。
        // 这里按 body.tenantId 切 ctx，让 trade.createOrder 把订单写到商户租户。
        Long tenantId = req.getTenantId();
        if (tenantId == null || tenantId <= 0) {
            throw ServiceExceptionUtil.exception0(1_031_001_011, "未识别店铺");
        }
        TenantContextHolder.setTenantId(tenantId);
        TenantContextHolder.setIgnore(false);
        boolean useBalance = Boolean.TRUE.equals(req.getUseShopBalance());
        int balanceFen = req.getBalanceFen() == null ? 0 : req.getBalanceFen();

        // 1. 预校验余额（避免在 trade 创建后才发现余额不足导致回滚浪费资源）
        if (useBalance && balanceFen > 0) {
            MemberShopRelDO rel = memberShopRelService.getByUserAndTenant(userId, tenantId);
            int currentBalance = rel == null || rel.getBalance() == null ? 0 : rel.getBalance();
            if (currentBalance < balanceFen) {
                throw ServiceExceptionUtil.exception0(1_031_001_012,
                        "余额不足，当前余额：" + (currentBalance / 100.0) + " 元");
            }
        }

        // 2. 创建交易订单（同事务）
        TradeOrderDO order = tradeOrderUpdateService.createOrder(userId, req.getOrder());
        Long orderId = order.getId();

        // 3. 抵扣余额 + 改价（仅在余额抵扣启用且金额 > 0 时执行）
        int finalDeductFen = 0;
        int finalPayPrice = order.getPayPrice() == null ? 0 : order.getPayPrice();
        if (useBalance && balanceFen > 0) {
            // MAJ-3 修复：必须保留至少 ¥0.01 线上支付（trade 的 updateOrderPrice
            // 强校验 newPayPrice > 0）。订单金额过小时直接报错，不再静默"扣了但没扣"。
            if (finalPayPrice <= 1) {
                throw ServiceExceptionUtil.exception0(1_031_001_017,
                        "订单金额过小（≤ ¥0.01），无法启用余额抵扣，请取消余额抵扣后再提交");
            }
            int maxAllowed = finalPayPrice - 1;
            finalDeductFen = Math.min(balanceFen, maxAllowed);
            if (finalDeductFen > 0) {
                memberShopRelService.deductBalanceForOrder(userId, tenantId, orderId, finalDeductFen);

                TradeOrderUpdatePriceReqVO priceReq = new TradeOrderUpdatePriceReqVO();
                priceReq.setId(orderId);
                priceReq.setAdjustPrice(-finalDeductFen);
                tradeOrderUpdateService.updateOrderPrice(priceReq);
                finalPayPrice -= finalDeductFen;
            }
        }

        // 4. v8: 推 N 反 1 / 直推奖 本单立即抵扣
        //    按订单中每个 spu 行预演产生积分 → K = floor(produced / unitPrice) → 调订单价格
        int finalPromoDeductFen = 0;
        int totalPromoDeductCount = 0;
        try {
            java.util.List<cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO> items =
                    tradeOrderQueryService.getOrderItemListByOrderId(orderId);
            int totalDeductFen = 0;
            for (cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO item : items) {
                Long spuId = item.getSpuId();
                Integer cnt = item.getCount();
                // unitPrice 必须用商品定价 item.getPrice()，与 MerchantPromoOrderHandler 的取值一致。
                // 之前曾用 payPrice/cnt（余额抵扣后单价偏小），导致 checkout 与 handler 算出的 K 不一致 →
                // 抵扣金额与状态机推进件数错配。修正为统一商品定价。
                Integer unitPrice = item.getPrice();
                if (spuId == null || unitPrice == null || unitPrice <= 0 || cnt == null || cnt <= 0) continue;
                cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO promoCfg =
                        productPromoConfigService.getBySpuId(spuId);
                if (promoCfg == null || !Boolean.TRUE.equals(promoCfg.getTuijianEnabled())) continue;
                long produced = promoQueueService.previewProducedForOrder(promoCfg, userId, spuId, unitPrice, cnt);
                if (produced <= 0) continue;
                int k = (int) (produced / unitPrice);
                if (k <= 0) continue;
                if (k >= cnt) k = cnt;  // 不超过总件数
                totalPromoDeductCount += k;
                totalDeductFen += k * unitPrice;
            }
            // 抵扣后 payPrice 必须 ≥ 1 分（trade 限制）
            if (totalDeductFen > 0) {
                int maxAllowed = Math.max(0, finalPayPrice - 1);
                int actualDeduct = Math.min(totalDeductFen, maxAllowed);
                if (actualDeduct > 0) {
                    TradeOrderUpdatePriceReqVO priceReq = new TradeOrderUpdatePriceReqVO();
                    priceReq.setId(orderId);
                    priceReq.setAdjustPrice(-actualDeduct);
                    tradeOrderUpdateService.updateOrderPrice(priceReq);
                    finalPayPrice -= actualDeduct;
                    finalPromoDeductFen = actualDeduct;
                }
            }
        } catch (Exception e) {
            // 抵扣失败不阻塞下单（用户会按全额支付，对账时可在 deduction_record 缺失发现）。
            // log 级别 error + 完整 stacktrace + 关键字段，便于监控告警与人工补偿
            org.slf4j.LoggerFactory.getLogger(getClass())
                    .error("[checkout v8 抵扣失败] orderId={} userId={} tenantId={} grossPay={} balanceFen={}",
                            orderId, userId, tenantId, order.getPayPrice(), finalDeductFen, e);
        }

        SubmitRespVO resp = new SubmitRespVO();
        resp.setOrderId(orderId);
        resp.setPayOrderId(order.getPayOrderId());
        resp.setBalanceDeductFen(finalDeductFen);
        resp.setPromoDeductFen(finalPromoDeductFen);
        resp.setPromoDeductCount(totalPromoDeductCount);
        resp.setPayPrice(finalPayPrice);
        return success(resp);
    }

    /**
     * v8 抵扣预演：checkout 进入时调用，让 UI 展示"抵扣前 / 抵扣 K 件 / 抵扣后"。
     * 不写库、不创建订单，纯只读计算。
     */
    @PostMapping("/preview-deduction")
    @Operation(summary = "v8 推广积分抵扣预演")
    @cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore
    public CommonResult<PreviewDeductionRespVO> previewDeduction(@Valid @RequestBody PreviewDeductionReqVO req) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null || userId <= 0) {
            throw ServiceExceptionUtil.exception0(1_031_001_010, "请先登录");
        }
        Long tenantId = req.getTenantId();
        if (tenantId == null || tenantId <= 0) {
            throw ServiceExceptionUtil.exception0(1_031_001_011, "未识别店铺");
        }
        TenantContextHolder.setTenantId(tenantId);
        TenantContextHolder.setIgnore(false);

        PreviewDeductionRespVO resp = new PreviewDeductionRespVO();
        java.util.List<PreviewDeductionRespVO.ItemDeduction> itemList = new java.util.ArrayList<>();
        long originalPay = 0;
        long deductFen = 0;
        int deductCount = 0;
        if (req.getItems() == null || req.getItems().isEmpty()) {
            resp.setOriginalPay(0); resp.setDeductFen(0); resp.setFinalPay(0); resp.setDeductCount(0);
            resp.setItems(itemList);
            return success(resp);
        }
        // 拉一次性 sku 拿 spuId / price
        java.util.Set<Long> skuIds = new java.util.HashSet<>();
        for (PreviewDeductionReqVO.Item it : req.getItems()) {
            if (it.getSkuId() != null) skuIds.add(it.getSkuId());
        }
        java.util.List<cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO> skus =
                skuIds.isEmpty() ? java.util.Collections.emptyList() : productSkuService.getSkuList(skuIds);
        java.util.Map<Long, cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO> skuMap = new java.util.HashMap<>();
        for (cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO s : skus) skuMap.put(s.getId(), s);

        // 按 SPU 聚合（同 SPU 多 SKU 算一行 — preview 用商品级 unit price 行平均）
        java.util.Map<Long, int[]> spuAgg = new java.util.LinkedHashMap<>();  // spuId → [count, sumPrice]
        for (PreviewDeductionReqVO.Item it : req.getItems()) {
            cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO sku = skuMap.get(it.getSkuId());
            if (sku == null || sku.getSpuId() == null || sku.getPrice() == null) continue;
            int cnt = it.getCount() == null ? 1 : it.getCount();
            int price = sku.getPrice();
            originalPay += (long) price * cnt;
            spuAgg.merge(sku.getSpuId(), new int[]{cnt, price * cnt},
                    (a, b) -> new int[]{a[0] + b[0], a[1] + b[1]});
        }

        for (java.util.Map.Entry<Long, int[]> e : spuAgg.entrySet()) {
            Long spuId = e.getKey();
            int cnt = e.getValue()[0];
            int totalPrice = e.getValue()[1];
            int unitPrice = cnt > 0 ? totalPrice / cnt : 0;
            cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ProductPromoConfigDO cfg =
                    productPromoConfigService.getBySpuId(spuId);
            PreviewDeductionRespVO.ItemDeduction line = new PreviewDeductionRespVO.ItemDeduction();
            line.setSpuId(spuId);
            line.setCount(cnt);
            line.setUnitPrice(unitPrice);
            line.setProducedAmount(0L);
            line.setDeductCount(0);
            line.setDeductFen(0);
            line.setTuijianEnabled(cfg != null && Boolean.TRUE.equals(cfg.getTuijianEnabled()));
            if (line.isTuijianEnabled() && unitPrice > 0 && cnt > 0) {
                long produced = promoQueueService.previewProducedForOrder(cfg, userId, spuId, unitPrice, cnt);
                int k = unitPrice > 0 ? (int) (produced / unitPrice) : 0;
                if (k > cnt) k = cnt;
                line.setProducedAmount(produced);
                line.setDeductCount(k);
                line.setDeductFen(k * unitPrice);
                deductCount += k;
                deductFen += (long) k * unitPrice;
            }
            itemList.add(line);
        }
        resp.setOriginalPay((int) Math.min(originalPay, Integer.MAX_VALUE));
        resp.setDeductFen((int) Math.min(deductFen, Integer.MAX_VALUE));
        resp.setFinalPay((int) Math.max(0, originalPay - deductFen));
        resp.setDeductCount(deductCount);
        resp.setItems(itemList);
        return success(resp);
    }

    @Data
    public static class PreviewDeductionReqVO {
        @javax.validation.constraints.NotNull(message = "tenantId 不能为空")
        private Long tenantId;
        @javax.validation.constraints.NotNull(message = "items 不能为空")
        private java.util.List<Item> items;

        @Data
        public static class Item {
            @javax.validation.constraints.NotNull
            private Long skuId;
            @javax.validation.constraints.NotNull
            @javax.validation.constraints.Min(1)
            private Integer count;
        }
    }

    @Data
    public static class PreviewDeductionRespVO {
        /** 抵扣前应付（分）= sum(unitPrice × count) */
        private Integer originalPay;
        /** 推广积分抵扣金额（分）*/
        private Integer deductFen;
        /** 抵扣后应付（分）*/
        private Integer finalPay;
        /** 抵扣件数（按 SPU 累加）*/
        private Integer deductCount;
        /** 每个 SPU 行的预演明细 */
        private java.util.List<ItemDeduction> items;

        @Data
        public static class ItemDeduction {
            private Long spuId;
            private Integer count;
            private Integer unitPrice;
            /** 推 N 反 1 是否启用（前端据此决定显不显示抵扣行）*/
            private boolean tuijianEnabled;
            /** 本单买 count 件预计产生积分（分）*/
            private Long producedAmount;
            /** 抵扣件数 K = floor(produced / unitPrice) */
            private Integer deductCount;
            /** 抵扣金额（分）= K × unitPrice */
            private Integer deductFen;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class SubmitReqVO {

        /**
         * 目标商户租户 ID（必填）。
         *
         * <p>C 端用户跨店下单时由前端从 URL query 读 tenantId 显式传过来；
         * 后端按 body 切 TenantContextHolder 写订单，避免 header 'tenant-id'
         * 与 token user.tenantId 不一致触发 TenantSecurityWebFilter 403。</p>
         */
        @javax.validation.constraints.NotNull(message = "tenantId 不能为空")
        private Long tenantId;

        @Valid
        @javax.validation.constraints.NotNull(message = "订单数据不能为空")
        private AppTradeOrderCreateReqVO order;

        /** 是否启用店铺余额抵扣 */
        private Boolean useShopBalance;

        /** 拟抵扣的余额金额（分），将与"实付价格 - 1"取最小值，保留至少 1 分线上支付 */
        @javax.validation.constraints.Min(value = 0, message = "余额抵扣金额不能为负")
        @javax.validation.constraints.Max(value = 100_000_000, message = "余额抵扣金额过大")
        private Integer balanceFen;
    }

    @Data
    public static class SubmitRespVO {
        private Long orderId;
        private Long payOrderId;
        /** 实际抵扣的余额（分） */
        private Integer balanceDeductFen;
        /** v8: 推 N 反 1 / 直推奖 抵扣金额（分） */
        private Integer promoDeductFen;
        /** v8: 推 N 反 1 / 直推奖 抵扣件数（按 SPU 累加） */
        private Integer promoDeductCount;
        /** 抵扣后还需线上支付的金额（分） */
        private Integer payPrice;
    }

}
