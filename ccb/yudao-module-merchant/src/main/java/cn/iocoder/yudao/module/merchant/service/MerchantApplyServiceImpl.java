package cn.iocoder.yudao.module.merchant.service;

import cn.hutool.core.util.RandomUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.apply.MerchantApplyAuditReqVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.apply.MerchantApplyPageReqVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.apply.TenantSubscriptionPageReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantApplyDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.TenantSubscriptionDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MerchantApplyMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.TenantSubscriptionMapper;
import cn.iocoder.yudao.module.system.controller.admin.tenant.vo.tenant.TenantSaveReqVO;
import cn.iocoder.yudao.module.system.service.sms.SmsSendService;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import org.springframework.context.annotation.Lazy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

/**
 * 商户入驻申请 Service 实现
 */
@Service
@Validated
@Slf4j
public class MerchantApplyServiceImpl implements MerchantApplyService {

    /** 试用期天数 */
    private static final int TRIAL_DAYS = 30;
    /** 试用期赠送 AI 成片次数 */
    private static final int TRIAL_AI_VIDEO_QUOTA = 1;
    /** 审核通过短信模板 */
    private static final String SMS_TEMPLATE_APPROVED = "MERCHANT_APPLY_APPROVED";
    /** 审核驳回短信模板 */
    private static final String SMS_TEMPLATE_REJECTED = "MERCHANT_APPLY_REJECTED";

    @Resource
    private MerchantApplyMapper merchantApplyMapper;
    @Resource
    private ShopInfoMapper shopInfoMapper;
    @Resource
    private TenantSubscriptionMapper tenantSubscriptionMapper;
    @Resource
    private TenantService tenantService;
    @Resource
    private SmsSendService smsSendService;
    @Lazy
    @Resource
    private MerchantReferralService merchantReferralService;

    /** 默认租户套餐 ID（需在配置文件中设置 merchant.default-package-id） */
    @Value("${merchant.default-package-id:1}")
    private Long defaultPackageId;

    @Override
    public PageResult<MerchantApplyDO> getApplyPage(MerchantApplyPageReqVO pageReqVO) {
        return merchantApplyMapper.selectPage(pageReqVO);
    }

    @Override
    public MerchantApplyDO getApply(Long id) {
        return merchantApplyMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditApply(MerchantApplyAuditReqVO auditReqVO, Long auditorId) {
        MerchantApplyDO apply = merchantApplyMapper.selectById(auditReqVO.getId());
        if (apply == null) {
            throw exception0(1_020_002_000, "申请记录不存在");
        }
        if (apply.getStatus() != 0) {
            throw exception0(1_020_002_001, "申请已审核，请勿重复操作");
        }
        if (!Boolean.TRUE.equals(auditReqVO.getApproved())
                && (auditReqVO.getRejectReason() == null || auditReqVO.getRejectReason().isBlank())) {
            throw exception0(1_020_002_003, "驳回时必须填写驳回原因");
        }

        if (Boolean.TRUE.equals(auditReqVO.getApproved())) {
            approveApply(apply, auditorId);
        } else {
            rejectApply(apply, auditorId, auditReqVO.getRejectReason());
        }
    }

    // ==================== 审核通过 ====================

    private void approveApply(MerchantApplyDO apply, Long auditorId) {
        // 1. 创建租户（同步创建商户管理员账号）
        String password = RandomUtil.randomNumbers(8);
        TenantSaveReqVO tenantReqVO = buildTenantSaveReqVO(apply, password);
        Long tenantId = tenantService.createTenant(tenantReqVO);
        log.info("[approveApply] 商户 {} 审核通过，创建租户 tenantId={}", apply.getMobile(), tenantId);

        // 2. 初始化 shop_info
        initShopInfo(apply, tenantId);

        // 3. 初始化订阅记录（30天试用 + 1次AI成片配额）
        initTenantSubscription(tenantId);

        // 4. 更新申请记录
        MerchantApplyDO update = new MerchantApplyDO();
        update.setId(apply.getId());
        update.setStatus(1);
        update.setAuditorId(auditorId);
        update.setAuditTime(LocalDateTime.now());
        update.setTenantId(tenantId);
        merchantApplyMapper.updateById(update);

        // 5. 记录推荐关系（若填写了推荐人手机号）
        merchantReferralService.recordReferral(apply.getReferrerMobile(), tenantId);

        // 6. 发送短信通知
        sendApprovedSms(apply.getMobile(), apply.getShopName(), password);
    }

    private TenantSaveReqVO buildTenantSaveReqVO(MerchantApplyDO apply, String password) {
        TenantSaveReqVO vo = new TenantSaveReqVO();
        vo.setName(apply.getShopName());
        vo.setContactName(apply.getShopName());
        vo.setContactMobile(apply.getMobile());
        vo.setStatus(0); // 启用
        vo.setPackageId(defaultPackageId);
        // 系统租户有效期设为 100 年，实际订阅由 tenant_subscription 管理
        vo.setExpireTime(LocalDateTime.now().plusYears(100));
        vo.setAccountCount(999);
        // 管理员账号：手机号作为用户名
        vo.setUsername(apply.getMobile());
        vo.setPassword(password);
        vo.setWebsites(Collections.emptyList());
        return vo;
    }

    private void initShopInfo(MerchantApplyDO apply, Long tenantId) {
        ShopInfoDO shopInfo = ShopInfoDO.builder()
                .tenantId(tenantId)
                .shopName(apply.getShopName())
                .categoryId(apply.getCategoryId())
                .longitude(apply.getLongitude() != null ? apply.getLongitude() : BigDecimal.ZERO)
                .latitude(apply.getLatitude() != null ? apply.getLatitude() : BigDecimal.ZERO)
                .address(apply.getAddress())
                .status(1) // 正常营业
                .sales30d(0)
                .avgRating(new BigDecimal("5.0"))
                .build();
        shopInfoMapper.insert(shopInfo);
    }

    private void initTenantSubscription(Long tenantId) {
        TenantSubscriptionDO subscription = TenantSubscriptionDO.builder()
                .tenantId(tenantId)
                .status(1) // 试用中
                .expireTime(LocalDateTime.now().plusDays(TRIAL_DAYS))
                .aiVideoQuota(TRIAL_AI_VIDEO_QUOTA)
                .aiVideoUsed(0)
                .build();
        tenantSubscriptionMapper.insert(subscription);
    }

    private void sendApprovedSms(String mobile, String shopName, String password) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("shopName", shopName);
            params.put("password", password);
            smsSendService.sendSingleSmsToMember(mobile, null, SMS_TEMPLATE_APPROVED, params);
        } catch (Exception e) {
            log.warn("[sendApprovedSms] 发送审核通过短信失败，mobile={}", mobile, e);
        }
    }

    // ==================== 审核驳回 ====================

    private void rejectApply(MerchantApplyDO apply, Long auditorId, String rejectReason) {
        MerchantApplyDO update = new MerchantApplyDO();
        update.setId(apply.getId());
        update.setStatus(2);
        update.setAuditorId(auditorId);
        update.setAuditTime(LocalDateTime.now());
        update.setRejectReason(rejectReason);
        merchantApplyMapper.updateById(update);

        // 发送驳回短信
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("shopName", apply.getShopName());
            params.put("reason", rejectReason);
            smsSendService.sendSingleSmsToMember(apply.getMobile(), null, SMS_TEMPLATE_REJECTED, params);
        } catch (Exception e) {
            log.warn("[rejectApply] 发送驳回短信失败，mobile={}", apply.getMobile(), e);
        }
    }

    // ==================== 订阅管理 ====================

    @Override
    public TenantSubscriptionDO getSubscription(Long tenantId) {
        return tenantSubscriptionMapper.selectByTenantId(tenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void renewSubscription(Long tenantId, int days) {
        TenantSubscriptionDO subscription = tenantSubscriptionMapper.selectByTenantId(tenantId);
        if (subscription == null) {
            throw exception0(1_020_002_002, "订阅记录不存在");
        }
        TenantSubscriptionDO update = new TenantSubscriptionDO();
        update.setId(subscription.getId());
        // 已过期从今天起算；未过期从到期时间续
        LocalDateTime base = subscription.getExpireTime().isBefore(LocalDateTime.now())
                ? LocalDateTime.now() : subscription.getExpireTime();
        update.setExpireTime(base.plusDays(days));
        update.setStatus(2); // 正式
        tenantSubscriptionMapper.updateById(update);
        // 触发推N返1检查：如果该商户是被推荐的，标记已付费并检查推荐人是否达到奖励阈值
        try {
            merchantReferralService.onRefereePaid(tenantId);
        } catch (Exception e) {
            log.warn("[renewSubscription] 推N返1检查失败，不影响续费：tenantId={}", tenantId, e);
        }
    }

    @Override
    public PageResult<TenantSubscriptionDO> getSubscriptionPage(TenantSubscriptionPageReqVO pageReqVO) {
        return tenantSubscriptionMapper.selectPage(pageReqVO);
    }

    @Override
    public void disableSubscription(Long tenantId) {
        TenantSubscriptionDO subscription = tenantSubscriptionMapper.selectByTenantId(tenantId);
        if (subscription == null) {
            throw exception0(1_020_002_002, "订阅记录不存在");
        }
        TenantSubscriptionDO update = new TenantSubscriptionDO();
        update.setId(subscription.getId());
        update.setStatus(4); // 禁用
        tenantSubscriptionMapper.updateById(update);
        log.info("[disableSubscription] 租户 tenantId={} 订阅已禁用", tenantId);
    }

    @Override
    public void expireOverdueSubscriptions() {
        java.util.List<TenantSubscriptionDO> overdueList = tenantSubscriptionMapper.selectExpiredActive();
        if (overdueList.isEmpty()) {
            return;
        }
        for (TenantSubscriptionDO sub : overdueList) {
            TenantSubscriptionDO update = new TenantSubscriptionDO();
            update.setId(sub.getId());
            update.setStatus(3); // 过期
            tenantSubscriptionMapper.updateById(update);
        }
        log.info("[expireOverdueSubscriptions] 批量过期 {} 条订阅", overdueList.size());
    }

}
