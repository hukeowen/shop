package cn.iocoder.yudao.module.merchant.service.saas;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.saas.SaasPackageConfigDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MerchantMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.saas.SaasPackageConfigMapper;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.service.order.handler.TradeOrderHandler;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * V042：SaaS 套餐订单升档钩子。
 *
 * <p>当 trade_order paid 后，识别 item.spu_id 是平台店铺的套餐 SPU 时：
 * <ol>
 *   <li>累加 buyer 商户的 service_expire_at（基线 = max(now, current_expire) + duration_days）</li>
 *   <li>升档 service_package_level（BASIC ↔ PRO 取高）</li>
 *   <li>累加 video_quota_remaining（AI 视频额度按套餐 ai_video_grant 增加）</li>
 * </ol>
 *
 * <p>注：每个 order item 是独立的套餐购买（一般 count=1）。多 item 时每个都触发一次。</p>
 *
 * <p>买家是 trade_order.user_id（member_user.id）；通过 merchant_info.user_id 反查商户主体；
 * 反查不到 → log warn 跳过（非商户用户买了套餐，没有商户身份可升档）。</p>
 *
 * <p>幂等：trade.updateOrderPaid 自带状态机 CAS，handler 链上游已确保订单 status 不会重复推进；
 * 这里靠 SaaS 套餐升档的天然幂等（多次累加 = 多次续期，符合预期）。</p>
 */
@Component
@Slf4j
public class SaasPackageTradeOrderHandler implements TradeOrderHandler {

    @Resource
    private SaasPackageConfigMapper saasPackageConfigMapper;
    @Resource
    private MerchantMapper merchantMapper;

    @Override
    public void afterPayOrder(TradeOrderDO order, List<TradeOrderItemDO> orderItems) {
        if (order == null || orderItems == null || orderItems.isEmpty()) {
            return;
        }
        Long buyerId = order.getUserId();
        if (buyerId == null) {
            return;
        }
        for (TradeOrderItemDO item : orderItems) {
            try {
                processOneItem(buyerId, order.getId(), item);
            } catch (Exception e) {
                log.error("[SaasPackageHandler] order={} spu={} 套餐升档失败",
                        order.getId(), item.getSpuId(), e);
            }
        }
    }

    private void processOneItem(Long buyerUserId, Long orderId, TradeOrderItemDO item) {
        Long spuId = item.getSpuId();
        if (spuId == null) return;

        // 1. 反查套餐：item.spu_id 不在 saas_package_config.spu_id 里 → 不是套餐，跳过
        SaasPackageConfigDO pkg = saasPackageConfigMapper.selectBySpuId(spuId);
        if (pkg == null) {
            return;
        }

        // 2. 反查 merchant_info（by user_id；跨租户全表）— 用户必须已是商户才能升档
        MerchantDO merchant = TenantUtils.executeIgnore(
                () -> merchantMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MerchantDO>()
                                .eq(MerchantDO::getUserId, buyerUserId)));
        if (merchant == null) {
            log.warn("[SaasPackageHandler] order={} userId={} 未开通商户，不能升档（建议先入驻）",
                    orderId, buyerUserId);
            return;
        }

        // 3. 计算 expire 累加（按 item.count 倍率买几年）
        int count = item.getCount() == null ? 1 : item.getCount();
        int totalDays = pkg.getDurationDays() * count;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime baseline = merchant.getServiceExpireAt() != null
                && merchant.getServiceExpireAt().isAfter(now)
                ? merchant.getServiceExpireAt() : now;
        LocalDateTime newExpireAt = baseline.plusDays(totalDays);

        // 4. 升档：BASIC + PRO → PRO；同档续期
        String newLevel = decideLevelAfterPurchase(merchant.getServicePackageLevel(), pkg.getLevel());

        // 5. 累加 AI 视频额度
        int totalAiGrant = (pkg.getAiVideoGrant() == null ? 0 : pkg.getAiVideoGrant()) * count;

        // 6. 原子 UPDATE merchant
        final LocalDateTime expireFinal = newExpireAt;
        final String levelFinal = newLevel;
        final int grantFinal = totalAiGrant;
        TenantUtils.executeIgnore(() -> {
            merchantMapper.update(null,
                    new LambdaUpdateWrapper<MerchantDO>()
                            .eq(MerchantDO::getId, merchant.getId())
                            .set(MerchantDO::getServiceExpireAt, expireFinal)
                            .set(MerchantDO::getServicePackageLevel, levelFinal)
                            .setSql("video_quota_remaining = COALESCE(video_quota_remaining, 0) + " + grantFinal));
            return null;
        });

        log.info("[SaasPackageHandler] ✅ order={} userId={} merchantId={} 套餐升档 "
                + "level={} expireAt={} aiVideoGrant += {} (pkg={} count={})",
                orderId, buyerUserId, merchant.getId(),
                newLevel, newExpireAt, totalAiGrant, pkg.getLevel(), count);
    }

    /** 同时持有 PRO + BASIC 时优先 PRO；首次购买直接套用购买档。 */
    private String decideLevelAfterPurchase(String currentLevel, String purchaseLevel) {
        if (Objects.equals(currentLevel, SaasPackageConfigDO.LEVEL_PRO)
                || Objects.equals(purchaseLevel, SaasPackageConfigDO.LEVEL_PRO)) {
            return SaasPackageConfigDO.LEVEL_PRO;
        }
        return SaasPackageConfigDO.LEVEL_BASIC;
    }

}
