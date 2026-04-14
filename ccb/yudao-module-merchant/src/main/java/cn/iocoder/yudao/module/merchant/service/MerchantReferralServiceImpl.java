package cn.iocoder.yudao.module.merchant.service;

import cn.iocoder.yudao.module.merchant.controller.admin.vo.referral.MerchantReferralConfigRespVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.referral.MerchantReferralRespVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantReferralDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.TenantSubscriptionDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MerchantApplyMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.MerchantReferralMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.TenantSubscriptionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 平台商户推荐裂变 Service 实现
 *
 * <p>推N返1规则：推荐人推荐 N 个商户完成首次付费续费，系统自动为推荐人延长 rewardDays 天订阅。</p>
 */
@Service
@Validated
@Slf4j
public class MerchantReferralServiceImpl implements MerchantReferralService {

    /** 推 N 个付费商户触发返利（可在配置文件中覆盖） */
    @Value("${merchant.referral.push-n:3}")
    private int pushN;

    /** 达标后奖励的天数（默认365天=1年） */
    @Value("${merchant.referral.reward-days:365}")
    private int rewardDays;

    /** 是否开启推N返1 */
    @Value("${merchant.referral.enabled:true}")
    private boolean enabled;

    @Resource
    private MerchantReferralMapper merchantReferralMapper;
    @Resource
    private MerchantApplyMapper merchantApplyMapper;
    @Resource
    private ShopInfoMapper shopInfoMapper;
    @Resource
    private TenantSubscriptionMapper tenantSubscriptionMapper;

    @Override
    public MerchantReferralConfigRespVO getConfig() {
        return new MerchantReferralConfigRespVO(pushN, rewardDays, enabled);
    }

    @Override
    public List<MerchantReferralRespVO> getReferralList(Long referrerTenantId) {
        List<MerchantReferralDO> list = merchantReferralMapper.selectListByReferrerTenantId(referrerTenantId);
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(this::convert).collect(Collectors.toList());
    }

    @Override
    public void recordReferral(String referrerMobile, Long refereeTenantId) {
        if (referrerMobile == null || referrerMobile.isEmpty()) {
            return;
        }
        // 避免重复录入
        MerchantReferralDO existing = merchantReferralMapper.selectByRefereeTenantId(refereeTenantId);
        if (existing != null) {
            return;
        }

        // 尝试查找推荐人的租户ID（推荐人本身也可能是平台商户）
        Long referrerTenantId = resolveReferrerTenantId(referrerMobile);

        MerchantReferralDO referral = MerchantReferralDO.builder()
                .referrerMobile(referrerMobile)
                .referrerTenantId(referrerTenantId)
                .refereeTenantId(refereeTenantId)
                .rewarded(false)
                .build();
        merchantReferralMapper.insert(referral);
        log.info("[recordReferral] 记录推荐关系：referrerMobile={}, refereeTenantId={}", referrerMobile, refereeTenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onRefereePaid(Long refereeTenantId) {
        if (!enabled) {
            return;
        }
        MerchantReferralDO referral = merchantReferralMapper.selectByRefereeTenantId(refereeTenantId);
        if (referral == null || referral.getPaidAt() != null) {
            // 无推荐关系或已记录过付费
            return;
        }

        // 标记付费时间
        MerchantReferralDO update = new MerchantReferralDO();
        update.setId(referral.getId());
        update.setPaidAt(LocalDateTime.now());
        merchantReferralMapper.updateById(update);

        // 推荐人 tenantId 需要已关联（可能审核时填写了 mobile 但推荐人本身还未开通）
        Long referrerTenantId = referral.getReferrerTenantId();
        if (referrerTenantId == null) {
            // 尝试补充推荐人 tenantId
            referrerTenantId = resolveReferrerTenantId(referral.getReferrerMobile());
            if (referrerTenantId != null) {
                MerchantReferralDO linkUpdate = new MerchantReferralDO();
                linkUpdate.setId(referral.getId());
                linkUpdate.setReferrerTenantId(referrerTenantId);
                merchantReferralMapper.updateById(linkUpdate);
            }
        }

        if (referrerTenantId == null) {
            log.info("[onRefereePaid] 推荐人 {} 尚未开通商户，跳过返利检查", referral.getReferrerMobile());
            return;
        }

        // 检查推荐人累计付费推荐数是否达到阈值
        long paidCount = merchantReferralMapper.countPaidByReferrerTenantId(referrerTenantId);
        if (paidCount < pushN) {
            log.info("[onRefereePaid] 推荐人 tenantId={} 累计付费推荐 {} 个，未达 {} 个，暂不返利",
                    referrerTenantId, paidCount, pushN);
            return;
        }

        // 检查是否已经触发过返利（幂等）
        boolean alreadyRewarded = merchantReferralMapper.selectListByReferrerTenantId(referrerTenantId)
                .stream().anyMatch(r -> Boolean.TRUE.equals(r.getRewarded()));
        if (alreadyRewarded) {
            log.info("[onRefereePaid] 推荐人 tenantId={} 已触发过返利，跳过", referrerTenantId);
            return;
        }

        // 触发返利：续费 rewardDays 天
        grantReward(referrerTenantId);

        // 将该推荐人所有记录标记为已奖励
        markAllRewarded(referrerTenantId);
        log.info("[onRefereePaid] 推荐人 tenantId={} 达到 {} 个有效推荐，奖励 {} 天订阅",
                referrerTenantId, pushN, rewardDays);
    }

    // ==================== 私有方法 ====================

    private Long resolveReferrerTenantId(String referrerMobile) {
        MerchantApplyDO apply = merchantApplyMapper.selectByMobile(referrerMobile);
        return apply != null ? apply.getTenantId() : null;
    }

    private void grantReward(Long referrerTenantId) {
        TenantSubscriptionDO subscription = tenantSubscriptionMapper.selectByTenantId(referrerTenantId);
        if (subscription == null) {
            log.warn("[grantReward] 推荐人 tenantId={} 无订阅记录，无法返利", referrerTenantId);
            return;
        }
        LocalDateTime base = subscription.getExpireTime().isBefore(LocalDateTime.now())
                ? LocalDateTime.now() : subscription.getExpireTime();
        TenantSubscriptionDO update = new TenantSubscriptionDO();
        update.setId(subscription.getId());
        update.setExpireTime(base.plusDays(rewardDays));
        update.setStatus(2); // 正式
        tenantSubscriptionMapper.updateById(update);
    }

    private void markAllRewarded(Long referrerTenantId) {
        List<MerchantReferralDO> records = merchantReferralMapper.selectListByReferrerTenantId(referrerTenantId);
        for (MerchantReferralDO r : records) {
            if (!Boolean.TRUE.equals(r.getRewarded())) {
                MerchantReferralDO upd = new MerchantReferralDO();
                upd.setId(r.getId());
                upd.setRewarded(true);
                upd.setRewardTime(LocalDateTime.now());
                merchantReferralMapper.updateById(upd);
            }
        }
    }

    private MerchantReferralRespVO convert(MerchantReferralDO referral) {
        MerchantReferralRespVO vo = new MerchantReferralRespVO();
        vo.setId(referral.getId());
        vo.setReferrerMobile(referral.getReferrerMobile());
        vo.setReferrerTenantId(referral.getReferrerTenantId());
        vo.setRefereeTenantId(referral.getRefereeTenantId());
        vo.setPaidAt(referral.getPaidAt());
        vo.setRewarded(referral.getRewarded());
        vo.setRewardTime(referral.getRewardTime());
        vo.setCreateTime(referral.getCreateTime());

        // 补充被推荐商户名称
        if (referral.getRefereeTenantId() != null) {
            ShopInfoDO shopInfo = shopInfoMapper.selectByTenantId(referral.getRefereeTenantId());
            if (shopInfo != null) {
                vo.setRefereeShopName(shopInfo.getShopName());
            }
        }
        return vo;
    }

}
