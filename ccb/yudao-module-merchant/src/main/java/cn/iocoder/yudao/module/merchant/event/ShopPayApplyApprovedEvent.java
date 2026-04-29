package cn.iocoder.yudao.module.merchant.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 在线支付开通申请审核通过事件
 *
 * <p>由 {@code MerchantShopController.auditPayApply} 在状态置 2 后 publish；
 * 监听器 {@code ShopPayApplyApprovedListener} 异步调通联进件 API
 * （AllinpayMerchantClient.openMerchant）发起真实开户。</p>
 */
@Getter
public class ShopPayApplyApprovedEvent extends ApplicationEvent {

    private final Long shopId;
    private final Long auditorUserId;

    public ShopPayApplyApprovedEvent(Object source, Long shopId, Long auditorUserId) {
        super(source);
        this.shopId = shopId;
        this.auditorUserId = auditorUserId;
    }
}
