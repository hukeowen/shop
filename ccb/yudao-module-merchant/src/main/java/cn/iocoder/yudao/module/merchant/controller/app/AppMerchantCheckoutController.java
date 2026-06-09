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
    @Resource
    private cn.iocoder.yudao.module.merchant.dal.mysql.coupon.ShopCouponUserMapper shopCouponUserMapper;
    @Resource
    private cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper shopInfoMapper;

    // V043 消费积分抵扣依赖
    @Resource
    private cn.iocoder.yudao.module.merchant.service.promo.PromoConfigService promoConfigService;
    @Resource
    private cn.iocoder.yudao.module.merchant.service.promo.PromoPointService promoPointService;
    @Resource
    private cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopConsumePointDeductMapper consumePointDeductMapper;

    /** 通联兜底轮询（提单成功后立即调度，6 段退避主动查通联） */
    @Resource(name = "tradeOrderAllinpayPollingService")
    private cn.iocoder.yudao.module.merchant.service.allinpay.TradeOrderAllinpayPollingService tradeOrderAllinpayPollingService;

    /** 通联 cashier — 提单成功后用 trade_order.tenantId → shop_info 凭据签名拿支付链接 */
    @Resource
    private cn.iocoder.yudao.module.merchant.service.allinpay.AllinpayCashierService allinpayCashierService;

    /** 直接 mapper：积分全额抵扣免支付场景下绕过 updateOrderPrice 强校验 newPayPrice>0 */
    @Resource
    private cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper tradeOrderMapper;
    /** 订单行 mapper：免支付时把订单行 pay_price 也清零，避免残留 1 分被入账引擎读成"返 1 积分" */
    @Resource
    private cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper tradeOrderItemMapper;
    /** 线下转账收款记录 mapper：商户未开通在线支付时建单 + 回传收款码 */
    @Resource
    private cn.iocoder.yudao.module.merchant.dal.mysql.promo.ShopOfflinePaymentMapper shopOfflinePaymentMapper;
    /** 全部 TradeOrderHandler bean —— 全额抵扣免支付时同 offline-confirm 跑一遍 afterPayOrder */
    @Resource
    private java.util.List<cn.iocoder.yudao.module.trade.service.order.handler.TradeOrderHandler> tradeOrderHandlers;
    @Resource
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    /** 免支付路径用 REQUIRES_NEW 事务隔离：handler 内部 @Transactional 抛异常时
     *  只回滚 handler 自己，不污染 submit 主事务 markRollbackOnly */
    @Resource
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @PostMapping("/submit")
    @Operation(summary = "提交订单（支持店铺余额抵扣）")
    @Transactional(rollbackFor = Exception.class)
    @cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore  // C 端跨店下单：先 ignore，再按 body.tenantId 切 ctx
    public CommonResult<SubmitRespVO> submit(@Valid @RequestBody SubmitReqVO req,
                                              javax.servlet.http.HttpServletRequest httpReq) {
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
        // V039 营业闸门校验：未打卡 / 主动打烊 → 拒单
        cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO shopForCheck =
                shopInfoMapper.selectByTenantId(tenantId);
        if (shopForCheck == null
                || !cn.iocoder.yudao.module.merchant.util.ShopOperatingUtils.canOrder(shopForCheck)) {
            throw ServiceExceptionUtil.exception0(1_031_001_012,
                    "店铺已休业，无法下单");
        }
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

        // 3.5 V043: 消费积分抵扣（在余额抵扣之后、v8 推 N 反 1 之前；优先级符合 user 设计）
        //     约束：a) 商户 promo_config.consumePointRedeemEnabled = true
        //          b) 用户 consume_point_balance 足够
        //          c) 抵扣后 finalPayPrice 必须 ≥ 1 分（trade.updateOrderPrice 限制）
        int finalConsumePointDeductFen = 0;
        long finalConsumePointUsed = 0;
        if (Boolean.TRUE.equals(req.getUseConsumePoint())
                && req.getConsumePointDeductFen() != null
                && req.getConsumePointDeductFen() > 0) {
            // 不再校验商户开关：消费积分是用户资产，永远可抵扣（与推广积分一致）
            cn.iocoder.yudao.module.merchant.dal.dataobject.promo.PromoConfigDO promoConfig =
                    promoConfigService.getConfig();
            java.math.BigDecimal ratio = promoConfig == null ? null : promoConfig.getConsumePointRedeemRatio();
            if (ratio == null || ratio.signum() <= 0) {
                // 商户没配比例 → 默认 1 积分 = 1 分钱（即 100 积分 = 1 元）
                ratio = java.math.BigDecimal.ONE;
            }
            int requestFen = req.getConsumePointDeductFen();
            // 修复（1 分 bug）：trade.updateOrderPrice 不允许把订单价改到 0（newPayPrice>0），
            // 整单只能降到 1 分，剩 1 分由后面的「全额抵扣免支付」路径吃掉。原先调价与扣积分
            // 都按 (finalPayPrice-1) 计，导致 10 元订单纯积分全抵时只扣 999 积分、抵扣额记 ¥9.99，
            // 与前端展示(-¥10.00 / 1000 积分)、用户预期对不上。
            // 修法：意图抵光整单(coversAll)时，积分 / 抵扣记录按订单全额(含最后 1 分)计，
            //       调价仍只降到 1 分（trade 约束），那 1 分由免支付路径归零。
            // guard finalPayPrice >= 2：若前序抵扣已把订单压到 1 分，本块不再重复抵这 1 分。
            if (finalPayPrice >= 2) {
                int maxByRemain = finalPayPrice - 1;                   // 调价上限（保留 1 分线上）
                boolean coversAll = requestFen >= finalPayPrice;       // 消费积分足以抵光整单
                int redeemFen = coversAll ? finalPayPrice : Math.min(requestFen, maxByRemain);
                int priceDeductFen = Math.min(redeemFen, maxByRemain); // 实际调价（≤ 留 1 分）
                if (redeemFen > 0) {
                    // 反推积分数量 = ceil(redeemFen / ratio)，向上取整保证扣足额度
                    java.math.BigDecimal points = java.math.BigDecimal.valueOf(redeemFen)
                            .divide(ratio, 0, java.math.RoundingMode.CEILING);
                    long pointsUsed = points.longValueExact();
                    if (pointsUsed <= 0) {
                        throw ServiceExceptionUtil.exception0(1_031_001_032,
                                "积分抵扣金额过小");
                    }
                    // 扣减消费积分（带余额不足校验 + 幂等：sourceType=REDEEM, sourceId=orderId）
                    // submit 已切到 merchant tenant，promoPointService 内部 MyBatis-Plus 自动 where tenant_id
                    // → 与 OrderPaidListener / MerchantPromoOrderHandler 的 addConsumePoint 写入维度一致
                    boolean ok = promoPointService.deductConsumePoint(userId, pointsUsed,
                            "REDEEM", orderId, "下单消费积分抵扣");
                    if (!ok) {
                        throw ServiceExceptionUtil.exception0(1_031_001_033,
                                "消费积分扣减失败（余额不足或重复抵扣）");
                    }
                    // 改价（coversAll 时只降到 1 分，剩 1 分走免支付路径；否则正常留 1 分线上）
                    if (priceDeductFen > 0) {
                        TradeOrderUpdatePriceReqVO priceReq = new TradeOrderUpdatePriceReqVO();
                        priceReq.setId(orderId);
                        priceReq.setAdjustPrice(-priceDeductFen);
                        tradeOrderUpdateService.updateOrderPrice(priceReq);
                        finalPayPrice -= priceDeductFen;
                    }
                    finalConsumePointDeductFen = redeemFen;            // 抵扣额按全额(含最后 1 分)
                    finalConsumePointUsed = pointsUsed;
                    // 落抵扣记录（COMMITTED：balance 已扣 + 改价已成功；cancel 时退回）
                    // deductAmount 用 redeemFen，与扣减积分口径一致，退款时也一致
                    cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopConsumePointDeductDO deduct =
                            cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopConsumePointDeductDO
                                    .builder()
                                    .orderId(orderId)
                                    .userId(userId)
                                    .pointsUsed(pointsUsed)
                                    .ratioSnapshot(ratio)
                                    .deductAmount((long) redeemFen)
                                    .status(cn.iocoder.yudao.module.merchant.dal.dataobject.promo
                                            .ShopConsumePointDeductDO.STATUS_COMMITTED)
                                    .commitTime(java.time.LocalDateTime.now())
                                    .build();
                    consumePointDeductMapper.insert(deduct);
                }
            }
        }

        // 3.6 推广积分抵扣（用户主动）— 1 推广积分 = 1 分钱。在消费积分之后、v8 自动抵扣之前
        //     幂等：sourceType=REDEEM_ORDER, sourceId=orderId
        int finalPromoPointRedeemFen = 0;
        if (Boolean.TRUE.equals(req.getUsePromoPoint())
                && req.getPromoPointDeductFen() != null
                && req.getPromoPointDeductFen() > 0) {
            int requestFen = req.getPromoPointDeductFen();
            int maxByRemain = Math.max(0, finalPayPrice - 1);
            int actualDeductFen = Math.min(requestFen, maxByRemain);
            if (actualDeductFen > 0) {
                boolean ok = promoPointService.deductPromoPoint(userId, (long) actualDeductFen,
                        "REDEEM_ORDER", orderId, "下单推广积分抵扣");
                if (!ok) {
                    throw ServiceExceptionUtil.exception0(1_031_001_034,
                            "推广积分扣减失败（余额不足或重复抵扣）");
                }
                TradeOrderUpdatePriceReqVO priceReq = new TradeOrderUpdatePriceReqVO();
                priceReq.setId(orderId);
                priceReq.setAdjustPrice(-actualDeductFen);
                tradeOrderUpdateService.updateOrderPrice(priceReq);
                finalPayPrice -= actualDeductFen;
                finalPromoPointRedeemFen = actualDeductFen;
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

        // 5. 优惠券抵扣（最后一步，且必须在余额/积分抵扣之后；保留至少 1 分线上支付）
        int couponDeductFen = 0;
        if (req.getCouponUserId() != null && req.getCouponUserId() > 0) {
            cn.iocoder.yudao.module.merchant.dal.dataobject.coupon.ShopCouponUserDO cu =
                    shopCouponUserMapper.selectById(req.getCouponUserId());
            if (cu == null || !userId.equals(cu.getUserId())) {
                throw ServiceExceptionUtil.exception0(1_031_001_018, "优惠券不存在或不属于本人");
            }
            if (cu.getStatus() != null && cu.getStatus() != 0) {
                throw ServiceExceptionUtil.exception0(1_031_001_019, "优惠券已使用 / 失效");
            }
            if (cu.getExpireTime() != null && cu.getExpireTime().isBefore(java.time.LocalDateTime.now())) {
                throw ServiceExceptionUtil.exception0(1_031_001_020, "优惠券已过期");
            }
            // 校验店铺：券是 tenanted，cu.tenantId 必须 = 当前订单 tenant
            if (cu.getTenantId() != null && !tenantId.equals(cu.getTenantId())) {
                throw ServiceExceptionUtil.exception0(1_031_001_021, "优惠券不适用本店");
            }
            // 满减门槛按"订单原价"判断（与余额/积分抵扣口径一致）
            int orderGross = order.getPayPrice() == null ? 0 : order.getPayPrice();
            int minAmount = cu.getMinAmount() == null ? 0 : cu.getMinAmount();
            if (orderGross < minAmount) {
                throw ServiceExceptionUtil.exception0(1_031_001_022,
                        "订单金额未达 ¥" + (minAmount / 100.0) + " 不能使用本券");
            }
            int discount = cu.getDiscountAmount() == null ? 0 : cu.getDiscountAmount();
            int maxAllowed = Math.max(0, finalPayPrice - 1);  // 至少留 1 分线上
            int actualCouponDeduct = Math.min(discount, maxAllowed);
            if (actualCouponDeduct > 0) {
                // 原子核销
                int updated = shopCouponUserMapper.markUsedAtomic(cu.getId(), userId, orderId);
                if (updated == 0) {
                    throw ServiceExceptionUtil.exception0(1_031_001_023, "优惠券核销失败（并发或已用）");
                }
                TradeOrderUpdatePriceReqVO priceReq = new TradeOrderUpdatePriceReqVO();
                priceReq.setId(orderId);
                priceReq.setAdjustPrice(-actualCouponDeduct);
                tradeOrderUpdateService.updateOrderPrice(priceReq);
                finalPayPrice -= actualCouponDeduct;
                couponDeductFen = actualCouponDeduct;
            }
        }

        SubmitRespVO resp = new SubmitRespVO();
        resp.setOrderId(orderId);
        resp.setPayOrderId(order.getPayOrderId());
        resp.setBalanceDeductFen(finalDeductFen);
        resp.setConsumePointDeductFen(finalConsumePointDeductFen);
        resp.setConsumePointUsed(finalConsumePointUsed);
        resp.setPromoPointRedeemFen(finalPromoPointRedeemFen);
        resp.setPromoDeductFen(finalPromoDeductFen);
        resp.setPromoDeductCount(totalPromoDeductCount);
        resp.setCouponDeductFen(couponDeductFen);
        resp.setPayPrice(finalPayPrice);
        resp.setPayMode("ONLINE"); // 默认在线支付；下方按"免支付 / 线下转账"覆盖

        // 5.5 全额积分/余额抵扣免支付：若用户主动抵扣（balance/consume/promo）已覆盖到只剩 ≤1 分，
        //     trade.updateOrderPrice 拒 newPayPrice=0，所以 1 分留给"内部免单"路径处理：
        //       a) 直接 mapper 标 pay_status=true、status=30、pay_time=now
        //       b) 跑 trade tradeOrderHandlers.afterPayOrder（库存扣减 / 营销引擎等）
        //       c) 发 OrderOfflineConfirmedEvent → merchant 营销引擎补 v8 副作用
        //       d) finalPayPrice 设 0，不调通联
        boolean fullyCoveredByRedeem = finalPayPrice <= 1
                && (finalDeductFen > 0 || finalConsumePointDeductFen > 0
                    || finalPromoPointRedeemFen > 0 || couponDeductFen > 0);
        if (fullyCoveredByRedeem) {
            try {
                // 直接在主事务里 mark-paid（同事务连写，不会锁等待）。
                // 不再跑 trade tradeOrderHandlers：
                //   - 库存：trade.createOrder 时已预扣，无需重复
                //   - member.point/level：内部 @Transactional 抛 NPE 会污染主事务（已踩坑）
                //   - 分销/优惠券赠送：本场景非必要
                // v8 营销 + 商户余额入账等副作用走 OrderOfflineConfirmedEvent → OrderPaidListener
                cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO upd =
                        new cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO();
                upd.setId(orderId);
                upd.setPayStatus(Boolean.TRUE);
                upd.setPayTime(java.time.LocalDateTime.now());
                upd.setStatus(30); // COMPLETED
                upd.setPayPrice(0); // 全额抵扣免支付：trade_order.payPrice 归 0
                                    // 修商户端订单详情「实付 ¥0.01」（原先 updateOrderPrice 受
                                    // newPayPrice>0 限制只能降到 1 分，残留的 1 分这里清掉）
                tradeOrderMapper.updateById(upd);

                // 订单行实付也清零：updateOrderPrice 只能把订单行降到 1 分（newPayPrice>0 约束），
                // 残留的 1 分会被 MerchantPromoOrderHandler.afterPayOrder 读成 item.payPrice → 倒返 1 积分。
                // 全额积分/余额抵扣实付为 0，不应再返任何消费积分（防"用积分付款又赚积分"刷分），
                // 故订单行 pay_price 一并归 0，让入账引擎读到的实付 = 0。
                cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO itemZero =
                        new cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO();
                itemZero.setPayPrice(0);
                tradeOrderItemMapper.update(itemZero,
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<
                                cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO>()
                                .eq(cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO::getOrderId,
                                        orderId));

                // 发事件：OrderPaidListener 会异步跑 merchantPromoOrderHandler.afterPayOrder（v8 营销）
                // 全额抵扣实付为 0 → 返积分基于 0，避免「抵扣后又按原价返积分」刷分
                int payPriceForEvent = 0;
                eventPublisher.publishEvent(new cn.iocoder.yudao.module.merchant.event.OrderOfflineConfirmedEvent(
                        this, orderId, tenantId, userId, payPriceForEvent));
                resp.setPayPrice(0);
                resp.setPayMode("FREE"); // 全额抵扣免支付
                finalPayPrice = 0;  // 跳过通联
                org.slf4j.LoggerFactory.getLogger(getClass())
                        .info("[checkout 免支付] orderId={} 余额/积分全额抵扣，跳过通联", orderId);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(getClass())
                        .error("[checkout 免支付] orderId={} 标记已支付失败，仍走通联兜底", orderId, e);
            }
        }

        // 6. 还有线上支付金额时：按商户是否开通在线支付通道分流
        //    - 已开通通联 → 走通联收银台（原逻辑）
        //    - 未开通通联 → 线下转账模式：建 shop_offline_payment 记录 + 回传商户收款码，
        //      顾客看码付款 + 上传凭证，商户核对后手动「确认收款」(offline-confirm)
        if (finalPayPrice > 0) {
            boolean merchantOnlinePay = shopForCheck != null
                    && (Boolean.TRUE.equals(shopForCheck.getOnlinePayEnabled())
                        || Boolean.TRUE.equals(shopForCheck.getTlEnabled()));
            if (!merchantOnlinePay) {
                // ===== 线下转账模式 =====
                resp.setPayMode("OFFLINE");
                try {
                    // 显式标记订单支付渠道，便于商户端 / 对账区分线下单
                    cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO mark =
                            new cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO();
                    mark.setId(orderId);
                    mark.setPayChannelCode("offline_transfer");
                    tradeOrderMapper.updateById(mark);
                    // 建线下收款记录（status=0 待顾客付款 + 上传凭证）；order_id UNIQUE 幂等
                    shopOfflinePaymentMapper.insert(cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopOfflinePaymentDO.builder()
                            .orderId(orderId)
                            .userId(userId)
                            .payPrice(finalPayPrice)
                            .status(cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopOfflinePaymentDO.STATUS_WAIT_PAY)
                            .build());
                } catch (Exception e) {
                    org.slf4j.LoggerFactory.getLogger(getClass())
                            .warn("[checkout 线下转账] 建收款记录失败 orderId={}: {}", orderId, e.getMessage());
                }
                // 回传商户收款码 + 店铺信息，C 端付款页直接展示
                resp.setWechatPayQrUrl(shopForCheck == null ? null : shopForCheck.getWechatPayQrUrl());
                resp.setAlipayPayQrUrl(shopForCheck == null ? null : shopForCheck.getAlipayPayQrUrl());
                resp.setMerchantMobile(shopForCheck == null ? null : shopForCheck.getMobile());
                resp.setShopName(shopForCheck == null ? null : shopForCheck.getShopName());
                org.slf4j.LoggerFactory.getLogger(getClass())
                        .info("[checkout 线下转账] orderId={} 商户未开通在线支付，转线下收款 应付={}分", orderId, finalPayPrice);
                return success(resp);
            }
            // ===== 在线支付（通联）模式 =====
            try {
                // 透传客户端 UA：通联根据 UA 推支付方式（微信浏览器→微信支付）。
                //     null 时通联兜底 Android Chrome → iPhone/微信里可能推 Apple Pay（错的）
                String clientUA = httpReq == null ? null : httpReq.getHeader("User-Agent");
                cn.iocoder.yudao.module.merchant.service.allinpay.AllinpayCashierService.CashierForm form =
                        allinpayCashierService.buildCashierFormForTrade(orderId, clientUA);
                if (form != null && form.getRedirectUrl() != null) {
                    resp.setCashierUrl(form.getRedirectUrl());
                }
            } catch (Exception e) {
                // 通联未就绪（商户未配 tl_enabled / 私钥未配 / 通联接口超时）→
                // 不阻塞下单（订单已落库），让用户在订单列表"立即付款"重试
                org.slf4j.LoggerFactory.getLogger(getClass())
                        .warn("[checkout] 通联拿支付链接失败 orderId={} tenantId={}: {}",
                                orderId, tenantId, e.getMessage());
            }
            try {
                tradeOrderAllinpayPollingService.schedulePolling(orderId);
            } catch (Exception e) {
                // 调度失败不阻塞下单，scanWaitingOrders 60s 后会兜底捞回
                org.slf4j.LoggerFactory.getLogger(getClass())
                        .warn("[checkout] 调度通联兜底轮询失败 orderId={}: {}", orderId, e.getMessage());
            }
        }

        return success(resp);
    }

    /**
     * v8 抵扣预演：checkout 进入时调用，让 UI 展示"抵扣前 / 抵扣 K 件 / 抵扣后"。
     * 不写库、不创建订单，纯只读计算。
     */
    /**
     * 「立即付款」入口 — 给指定 trade_order 重新拿通联支付链接。
     *
     * <p>用户在订单列表点"立即付款"调本接口，后端校验：
     * <ol>
     *   <li>订单存在 + 属于本人 + status=UNPAID</li>
     *   <li>商户已配通联（tl_enabled=true + cusId/RSA 齐）</li>
     * </ol>
     * 通过后调 cashier 拿 redirectUrl 返前端。</p>
     */
    @PostMapping("/cashier-link")
    @Operation(summary = "为待付款订单获取通联支付链接（订单列表立即付款用）")
    @cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore
    public CommonResult<java.util.Map<String, Object>> getCashierLink(
            @org.springframework.web.bind.annotation.RequestParam("orderId") Long orderId,
            javax.servlet.http.HttpServletRequest httpReq) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            throw ServiceExceptionUtil.exception0(1_031_001_010, "请先登录");
        }
        try {
            String clientUA = httpReq == null ? null : httpReq.getHeader("User-Agent");
            cn.iocoder.yudao.module.merchant.service.allinpay.AllinpayCashierService.CashierForm form =
                    allinpayCashierService.buildCashierFormForTrade(orderId, clientUA);
            java.util.Map<String, Object> resp = new java.util.HashMap<>();
            resp.put("cashierUrl", form.getRedirectUrl());
            return success(resp);
        } catch (IllegalStateException e) {
            // 商户未开通通联 / 凭据缺失 / 通联接口异常
            throw ServiceExceptionUtil.exception0(1_031_001_024, e.getMessage());
        }
    }

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

        /** 选用的优惠券 user 记录 ID（shop_coupon_user.id；不传 = 不用券） */
        private Long couponUserId;

        /** 是否启用消费积分抵扣（商户在 promo_config 必须先 enabled） */
        private Boolean useConsumePoint;

        /** 拟抵扣的消费积分对应的订单金额（分）。后端按 promo_config.consumePointRedeemRatio 反推积分数量。 */
        @javax.validation.constraints.Min(value = 0, message = "积分抵扣金额不能为负")
        @javax.validation.constraints.Max(value = 100_000_000, message = "积分抵扣金额过大")
        private Integer consumePointDeductFen;

        /** 是否启用推广积分抵扣 */
        private Boolean usePromoPoint;

        /** 拟抵扣的推广积分对应金额（分）。1 推广积分 = 1 分钱，1:1。 */
        @javax.validation.constraints.Min(value = 0, message = "推广积分抵扣金额不能为负")
        @javax.validation.constraints.Max(value = 100_000_000, message = "推广积分抵扣金额过大")
        private Integer promoPointDeductFen;
    }

    @Data
    public static class SubmitRespVO {
        private Long orderId;
        private Long payOrderId;
        /** 实际抵扣的余额（分） */
        private Integer balanceDeductFen;
        /** 实际抵扣的消费积分对应金额（分） */
        private Integer consumePointDeductFen;
        /** 实际扣减的消费积分数量 */
        private Long consumePointUsed;
        /** 实际抵扣的推广积分对应金额（分；与扣减积分数量 1:1） */
        private Integer promoPointRedeemFen;
        /** v8: 推 N 反 1 / 直推奖 抵扣金额（分） */
        private Integer promoDeductFen;
        /** v8: 推 N 反 1 / 直推奖 抵扣件数（按 SPU 累加） */
        private Integer promoDeductCount;
        /** 优惠券抵扣金额（分） */
        private Integer couponDeductFen;
        /** 抵扣后还需线上支付的金额（分） */
        private Integer payPrice;

        /**
         * 通联收银台支付链接（仅 payPrice>0 且商户已配通联时有值）。
         * 前端拿到后直接 location.href 跳转通联收银台。
         */
        private String cashierUrl;

        /**
         * 支付方式：
         *   ONLINE  = 通联在线支付（用 cashierUrl 跳转）
         *   OFFLINE = 线下转账（商户未开通在线支付，用下方收款码 + 上传凭证页）
         *   FREE    = 余额/积分全额抵扣免支付（直接成功）
         */
        private String payMode;

        /** 线下转账：商户微信收款码 URL */
        private String wechatPayQrUrl;
        /** 线下转账：商户支付宝收款码 URL */
        private String alipayPayQrUrl;
        /** 线下转账：商户客服电话 */
        private String merchantMobile;
        /** 线下转账：店铺名称 */
        private String shopName;
    }

}
