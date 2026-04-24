package cn.iocoder.yudao.module.merchant.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 到店付款收款确认事件
 *
 * <p>商户点击"已收款"后发布，供后续积分/返佣流程监听处理。</p>
 */
@Getter
public class OrderOfflineConfirmedEvent extends ApplicationEvent {

    /** 交易订单编号 */
    private final Long orderId;
    /** 租户 ID */
    private final Long tenantId;
    /** 买家用户 ID */
    private final Long userId;
    /** 实付金额（分） */
    private final Integer payPrice;

    public OrderOfflineConfirmedEvent(Object source, Long orderId, Long tenantId,
                                      Long userId, Integer payPrice) {
        super(source);
        this.orderId = orderId;
        this.tenantId = tenantId;
        this.userId = userId;
        this.payPrice = payPrice;
    }

}
