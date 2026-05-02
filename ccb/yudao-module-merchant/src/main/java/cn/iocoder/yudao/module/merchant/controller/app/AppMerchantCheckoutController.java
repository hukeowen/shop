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

    @PostMapping("/submit")
    @Operation(summary = "提交订单（支持店铺余额抵扣）")
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<SubmitRespVO> submit(@Valid @RequestBody SubmitReqVO req) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        Long tenantId = TenantContextHolder.getTenantId();
        if (userId == null || userId <= 0) {
            throw ServiceExceptionUtil.exception0(1_031_001_010, "请先登录");
        }
        if (tenantId == null || tenantId <= 0) {
            throw ServiceExceptionUtil.exception0(1_031_001_011, "未识别店铺");
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
        if (useBalance && balanceFen > 0 && finalPayPrice > 0) {
            // 必须留至少 1 分线上支付（updateOrderPrice 不允许 0 元）
            int maxAllowed = Math.max(0, finalPayPrice - 1);
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

        SubmitRespVO resp = new SubmitRespVO();
        resp.setOrderId(orderId);
        resp.setPayOrderId(order.getPayOrderId());
        resp.setBalanceDeductFen(finalDeductFen);
        resp.setPayPrice(finalPayPrice);
        return success(resp);
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class SubmitReqVO {

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
        /** 抵扣后还需线上支付的金额（分） */
        private Integer payPrice;
    }

}
