package cn.iocoder.yudao.module.merchant.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.merchant.service.promo.PromoQueueService;
import cn.iocoder.yudao.module.merchant.service.promo.StarService;
import cn.iocoder.yudao.module.merchant.service.promo.handler.MerchantPromoOrderHandler;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * V044 合规测试 — 临时 trigger endpoint（生产环境不应保留，测试完成后删除）。
 *
 * 用于真实下单链路 e2e 测试：构造 TradeOrderDO + TradeOrderItemDO 直接调
 * MerchantPromoOrderHandler.afterPayOrder，触发完整 V044 营销链路。
 */
@Tag(name = "V044 测试")
@RestController
@RequestMapping("/admin-api/v044-test")
@Slf4j
public class V044TestTriggerController {

    @Resource
    private MerchantPromoOrderHandler promoOrderHandler;
    @Resource
    private PromoQueueService promoQueueService;
    @Resource
    private StarService starService;

    /**
     * 触发真实 afterPayOrder 链路。
     * 直接构造 TradeOrder + items 注入处理器，验证 V044 整改后产生的 promo_record 符合规则。
     */
    @GetMapping("/trigger-pay-order")
    @Operation(summary = "V044 e2e: 触发真实订单支付完成链路")
    @PermitAll
    @TenantIgnore
    public CommonResult<Map<String, Object>> triggerPayOrder(
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("userId") Long userId,
            @RequestParam("spuId") Long spuId,
            @RequestParam("payPrice") Integer payPrice,
            @RequestParam("count") Integer count) {

        TradeOrderDO order = new TradeOrderDO();
        order.setId(System.currentTimeMillis());   // 用时间戳避免冲突
        order.setUserId(userId);
        order.setTenantId(tenantId);
        order.setPayPrice(payPrice);
        order.setPayStatus(true);

        TradeOrderItemDO item = new TradeOrderItemDO();
        item.setOrderId(order.getId());
        item.setUserId(userId);
        item.setSpuId(spuId);
        item.setSkuId(0L);
        item.setCount(count);
        item.setPayPrice(payPrice);

        log.info("[V044 e2e] 触发 afterPayOrder order={} user={} spu={} paid={} count={}",
                order.getId(), userId, spuId, payPrice, count);
        try {
            promoOrderHandler.afterPayOrder(order, Collections.singletonList(item));
        } catch (Exception e) {
            log.error("[V044 e2e] afterPayOrder 异常", e);
            Map<String, Object> err = new HashMap<>();
            err.put("ok", false);
            err.put("error", e.getMessage());
            err.put("class", e.getClass().getName());
            return success(err);
        }

        Map<String, Object> ok = new HashMap<>();
        ok.put("ok", true);
        ok.put("orderId", order.getId());
        return success(ok);
    }
}
