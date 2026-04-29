package cn.iocoder.yudao.module.merchant.listener;

import cn.hutool.crypto.SecureUtil;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper;
import cn.iocoder.yudao.module.merchant.event.ShopPayApplyApprovedEvent;
import cn.iocoder.yudao.module.merchant.service.allinpay.AllinpayMerchantClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import javax.annotation.Resource;

/**
 * 监听 PC 后台审核通过事件 → 异步调通联进件 API
 *
 * <p>状态机：审核通过(2) → openMerchant 同步发请求 → 拿 outOrderId 写库 + 状态改 4
 *      → 通联异步回调 webhook（{@link cn.iocoder.yudao.module.merchant.controller.admin.AllinpayNotifyController}）
 *      → 写真 tlMchId / tlMchKey + 状态改回 2 (已开通) 或 3 (驳回)。</p>
 *
 * <p>失败兜底：openMerchant 抛异常时仅 log；状态留在 2 (已开通)。运维可在 PC 后台
 * 手工重试（重新点"通过"再 publish 一次事件）；或定时任务扫 status=2 但 tl_mch_id IS NULL
 * 的店铺，主动调 queryMerchantStatus 拉取通联状态。</p>
 */
@Component
@Slf4j
public class ShopPayApplyApprovedListener {

    @Resource
    private AllinpayMerchantClient allinpayClient;
    @Resource
    private ShopInfoMapper shopInfoMapper;

    @Value("${merchant.field-encrypt-key:dev_key_12345678}")
    private String fieldEncryptKey;

    /**
     * 异步触发通联进件
     *
     * <p>事务监听 AFTER_COMMIT：仅当 auditPayApply 主事务（state=2 入库）成功提交后才触发，
     * 避免主事务回滚后还把开户调出去。</p>
     *
     * <p>跨租户写：ShopInfoDO 是平台级表（不继承 TenantBaseDO），不受 MP 拦截器
     * 自动过滤；但保险起见仍 wrap TenantUtils.executeIgnore。</p>
     */
    @Async("aiVideoTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShopPayApplyApproved(ShopPayApplyApprovedEvent event) {
        Long shopId = event.getShopId();
        ShopInfoDO shop = TenantUtils.executeIgnore(() -> shopInfoMapper.selectById(shopId));
        if (shop == null) {
            log.warn("[ShopPayApplyApproved] 店铺 {} 不存在，跳过通联开户", shopId);
            return;
        }
        // 已经发起过开户请求且未拿到结果，避免重复触发
        if (shop.getTlOpenOrderId() != null && !shop.getTlOpenOrderId().isEmpty()
                && shop.getTlMchId() != null) {
            log.info("[ShopPayApplyApproved] 店铺 {} 已开通通联号 {}，跳过", shopId, shop.getTlMchId());
            return;
        }

        try {
            AllinpayMerchantClient.OpenMerchantResult result = allinpayClient.openMerchant(shop);
            ShopInfoDO update = new ShopInfoDO();
            update.setId(shopId);
            update.setTlOpenOrderId(result.getOutOrderId());

            switch (result.getStatus()) {
                case APPROVED:
                    // 同步即审核通过（少见，但通联接口允许）
                    update.setTlMchId(result.getTlMchId());
                    if (result.getTlMchKey() != null) {
                        update.setTlMchKey(SecureUtil.aes(fieldEncryptKey.getBytes()).encryptHex(result.getTlMchKey()));
                    }
                    update.setPayApplyStatus(2);
                    log.info("[ShopPayApplyApproved] 店铺 {} 通联同步开户成功 mchId={}", shopId, result.getTlMchId());
                    break;
                case REJECTED:
                    update.setPayApplyStatus(3);
                    update.setPayApplyRejectReason("通联拒绝：" + result.getRejectReason());
                    log.warn("[ShopPayApplyApproved] 店铺 {} 通联拒绝 reason={}", shopId, result.getRejectReason());
                    break;
                case PENDING:
                default:
                    // 异步进件中：状态置 4，等通联 webhook 回调
                    update.setPayApplyStatus(4);
                    log.info("[ShopPayApplyApproved] 店铺 {} 通联进件中 outOrderId={}", shopId, result.getOutOrderId());
                    break;
            }
            TenantUtils.executeIgnore(() -> shopInfoMapper.updateById(update));
        } catch (Exception e) {
            log.error("[ShopPayApplyApproved] 店铺 {} 调通联开户失败", shopId, e);
            // 失败不抛：状态保留 2 (已开通)，由运维定时任务/手工补救
        }
    }
}
