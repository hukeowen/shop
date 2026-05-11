package cn.iocoder.yudao.module.merchant.service.saas;

import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.saas.MerchantSubscriptionOrderDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.saas.SaasPackageConfigDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MerchantMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.saas.MerchantSubscriptionOrderMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.saas.SaasPackageConfigMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class SaasSubscriptionServiceImpl implements SaasSubscriptionService {

    @Resource
    private SaasPackageConfigMapper packageConfigMapper;
    @Resource
    private MerchantSubscriptionOrderMapper subscriptionOrderMapper;
    @Resource
    private MerchantMapper merchantMapper;

    @Override
    public List<SaasPackageConfigDO> listEnabledPackages() {
        return packageConfigMapper.selectListEnabledOrderBySort();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantSubscriptionOrderDO createSubscriptionOrder(Long merchantId, String level) {
        if (merchantId == null) {
            throw ServiceExceptionUtil.exception0(400, "merchantId 不能为空");
        }
        if (level == null || level.isEmpty()) {
            throw ServiceExceptionUtil.exception0(400, "level 不能为空");
        }
        SaasPackageConfigDO pkg = packageConfigMapper.selectByLevel(level);
        if (pkg == null || pkg.getStatus() != 0) {
            throw ServiceExceptionUtil.exception0(404, "套餐不存在或已下架: " + level);
        }
        MerchantDO merchant = TenantUtils.executeIgnore(() -> merchantMapper.selectById(merchantId));
        if (merchant == null) {
            throw ServiceExceptionUtil.exception0(404, "商户不存在: " + merchantId);
        }
        if (Boolean.TRUE.equals(merchant.getIsPlatform())) {
            throw ServiceExceptionUtil.exception0(400, "平台商户无需购买套餐");
        }

        MerchantSubscriptionOrderDO order = MerchantSubscriptionOrderDO.builder()
                .merchantId(merchantId)
                .level(pkg.getLevel())
                .priceFen(pkg.getPriceFen())
                .durationDays(pkg.getDurationDays())
                .aiVideoGrant(pkg.getAiVideoGrant())
                .payStatus(MerchantSubscriptionOrderDO.PAY_STATUS_WAITING)
                .expireBefore(merchant.getServiceExpireAt())
                .build();
        subscriptionOrderMapper.insert(order);
        // 写 reqsn S 前缀（创建后 id 才有）
        order.setTlReqsn("S" + order.getId());
        subscriptionOrderMapper.updateById(order);
        log.info("[saas/createOrder] merchantId={} level={} priceFen={} orderId={} reqsn=S{}",
                merchantId, level, pkg.getPriceFen(), order.getId(), order.getId());
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markPaid(Long orderId, int paidAmountFen) {
        if (orderId == null) return;
        MerchantSubscriptionOrderDO order = subscriptionOrderMapper.selectById(orderId);
        if (order == null) {
            log.warn("[saas/markPaid] orderId={} 不存在", orderId);
            return;
        }
        if (order.getPayStatus() != null
                && order.getPayStatus() == MerchantSubscriptionOrderDO.PAY_STATUS_PAID) {
            log.info("[saas/markPaid] orderId={} 已是 PAID，幂等短路", orderId);
            return;
        }
        // 金额校验
        if (paidAmountFen != order.getPriceFen()) {
            log.warn("[saas/markPaid] 金额不一致 orderId={} expected={} actual={}",
                    orderId, order.getPriceFen(), paidAmountFen);
        }

        // 1. 加载商户（执行业务 ignore tenant）
        MerchantDO merchant = TenantUtils.executeIgnore(() -> merchantMapper.selectById(order.getMerchantId()));
        if (merchant == null) {
            log.error("[saas/markPaid] orderId={} merchant 不存在", orderId);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        // 基线：max(当前到期时间, 现在)
        LocalDateTime baseline = merchant.getServiceExpireAt() != null
                && merchant.getServiceExpireAt().isAfter(now)
                ? merchant.getServiceExpireAt() : now;
        LocalDateTime newExpireAt = baseline.plusDays(order.getDurationDays());

        // 2. CAS 标订单 PAID
        int rows = subscriptionOrderMapper.update(null,
                new LambdaUpdateWrapper<MerchantSubscriptionOrderDO>()
                        .eq(MerchantSubscriptionOrderDO::getId, orderId)
                        .eq(MerchantSubscriptionOrderDO::getPayStatus, MerchantSubscriptionOrderDO.PAY_STATUS_WAITING)
                        .set(MerchantSubscriptionOrderDO::getPayStatus, MerchantSubscriptionOrderDO.PAY_STATUS_PAID)
                        .set(MerchantSubscriptionOrderDO::getPayAt, now)
                        .set(MerchantSubscriptionOrderDO::getPayAmountFen, paidAmountFen)
                        .set(MerchantSubscriptionOrderDO::getExpireAfter, newExpireAt));
        if (rows == 0) {
            log.info("[saas/markPaid] orderId={} CAS 失败（并发或已处理），跳过", orderId);
            return;
        }

        // 3. 更新商户：到期时间 + AI 视频次数 + 升档（如果新档比旧档高）
        String newLevel = decideLevelAfterPurchase(merchant.getServicePackageLevel(), order.getLevel());
        // 用原子 SQL：service_expire_at = newExpireAt; ai_quota += grant; level = newLevel
        TenantUtils.executeIgnore(() -> {
            merchantMapper.update(null,
                    new LambdaUpdateWrapper<MerchantDO>()
                            .eq(MerchantDO::getId, merchant.getId())
                            .set(MerchantDO::getServiceExpireAt, newExpireAt)
                            .set(MerchantDO::getServicePackageLevel, newLevel)
                            .setSql("video_quota_remaining = COALESCE(video_quota_remaining, 0) + " + order.getAiVideoGrant()));
            return null;
        });
        log.info("[saas/markPaid] ✅ merchantId={} 已续期 expireAt={} newLevel={} aiVideoGrant +={}",
                merchant.getId(), newExpireAt, newLevel, order.getAiVideoGrant());
    }

    /**
     * 同时持有 PRO + BASIC 时优先 PRO；首次购买直接套用购买档。
     */
    private String decideLevelAfterPurchase(String currentLevel, String purchaseLevel) {
        if (Objects.equals(currentLevel, SaasPackageConfigDO.LEVEL_PRO)
                || Objects.equals(purchaseLevel, SaasPackageConfigDO.LEVEL_PRO)) {
            return SaasPackageConfigDO.LEVEL_PRO;
        }
        return purchaseLevel;
    }

    @Override
    public String getEffectiveLevel(MerchantDO merchant) {
        if (merchant == null) return "EXPIRED";
        if (Boolean.TRUE.equals(merchant.getIsPlatform())) {
            return SaasPackageConfigDO.LEVEL_PLATFORM;
        }
        // 到期 → EXPIRED（但 trial 仍按 PRO 体验）
        if (merchant.getServiceExpireAt() != null && merchant.getServiceExpireAt().isBefore(LocalDateTime.now())) {
            return "EXPIRED";
        }
        String level = merchant.getServicePackageLevel();
        if (level == null || level.isEmpty()) return SaasPackageConfigDO.LEVEL_TRIAL;
        return level;
    }

    @Override
    public boolean isExpired(MerchantDO merchant) {
        if (merchant == null) return true;
        if (Boolean.TRUE.equals(merchant.getIsPlatform())) return false;
        return merchant.getServiceExpireAt() != null
                && merchant.getServiceExpireAt().isBefore(LocalDateTime.now());
    }
}
