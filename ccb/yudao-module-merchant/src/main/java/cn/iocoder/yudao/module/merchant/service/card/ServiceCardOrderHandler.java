package cn.iocoder.yudao.module.merchant.service.card;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.service.order.handler.TradeOrderHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * 线上支付成功后发服务卡。
 *
 * <p>trade 模块支付成功后会遍历所有 {@link TradeOrderHandler}.afterPayOrder；
 * 线下到店付款不走 trade 链，由 OrderPaidListener 另行调用 {@link ServiceCardService#issueForOrder}。
 * 两条路都走 issueForOrder，且其内部幂等，重复触发安全。</p>
 */
@Component
@Slf4j
public class ServiceCardOrderHandler implements TradeOrderHandler {

    @Resource
    private ServiceCardService serviceCardService;

    @Override
    public void afterPayOrder(TradeOrderDO order, List<TradeOrderItemDO> orderItems) {
        if (order == null || order.getId() == null) {
            return;
        }
        try {
            // 切到订单所属租户，让卡定义查询 / 发卡插入按店隔离
            TenantUtils.execute(order.getTenantId(), () -> serviceCardService.issueForOrder(order.getId()));
        } catch (Exception e) {
            log.error("[ServiceCardOrderHandler] 发卡失败 orderId={}", order.getId(), e);
        }
    }

}
