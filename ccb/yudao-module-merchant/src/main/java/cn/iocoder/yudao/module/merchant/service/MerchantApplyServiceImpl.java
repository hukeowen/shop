package cn.iocoder.yudao.module.merchant.service;

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
import cn.iocoder.yudao.module.merchant.enums.MerchantLogRecordConstants;
import cn.iocoder.yudao.module.system.controller.admin.tenant.vo.tenant.TenantSaveReqVO;
import cn.iocoder.yudao.module.system.service.sms.SmsSendService;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import com.mzt.logapi.starter.annotation.LogRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.*;

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
    /** 批量过期每批上限（与 selectExpiredActive LIMIT 保持一致） */
    private static final int EXPIRE_BATCH_SIZE = 200;
    /** 占位密码长度（SecureRandom 生成，永不外发） */
    private static final int PLACEHOLDER_PASSWORD_LENGTH = 32;
    /** 占位密码字符集（base64 url-safe，不含易混淆字符；包含数字+大小写+@!#%& 以满足后台密码复杂度校验） */
    private static final char[] PLACEHOLDER_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789@!#%&".toCharArray();

    /** 线程安全的 SecureRandom，用于生成占位密码 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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
    @Lazy
    @Resource
    private MerchantService merchantService;

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
            throw exception(APPLY_NOT_FOUND);
        }
        if (apply.getStatus() != 0) {
            throw exception(APPLY_ALREADY_AUDITED);
        }
        if (!Boolean.TRUE.equals(auditReqVO.getApproved())
                && (auditReqVO.getRejectReason() == null || auditReqVO.getRejectReason().trim().isEmpty())) {
            throw exception(REJECT_REASON_REQUIRED);
        }

        if (Boolean.TRUE.equals(auditReqVO.getApproved())) {
            approveApply(apply, auditorId);
        } else {
            rejectApply(apply, auditorId, auditReqVO.getRejectReason());
        }
    }

    // ==================== 审核通过 ====================

    @LogRecord(type = MerchantLogRecordConstants.MERCHANT_APPLY_TYPE,
            subType = MerchantLogRecordConstants.MERCHANT_APPLY_AUDIT_SUB_TYPE,
            bizNo = "{{#apply.id}}",
            success = MerchantLogRecordConstants.MERCHANT_APPLY_APPROVE_SUCCESS,
            extra = "{{#apply}}")
    private void approveApply(MerchantApplyDO apply, Long auditorId) {
        // 1. 创建租户（账号密码由首次登录短信验证码流程设置，此处用随机密码占位）
        TenantSaveReqVO tenantReqVO = buildTenantSaveReqVO(apply);
        Long tenantId = tenantService.createTenant(tenantReqVO);
        log.info("[approveApply] 商户 {} 审核通过，创建租户 tenantId={}", apply.getMobile(), tenantId);

        // 2. 初始化 shop_info
        initShopInfo(apply, tenantId);

        // 3. 初始化订阅记录（30天试用 + 1次AI成片配额）
        initTenantSubscription(tenantId);

        // 3.5 复制 admin 租户的 pay_app + pay_channel 到新租户
        // 不做这步：trade 模块下单按 payAppKey='mall' 找不到 → 用户在商户店铺无法支付
        // 与 createMerchantFromMember (邀请码秒开通路径) 的初始化流程对齐
        merchantService.copyPayResourcesToNewTenant(tenantId);

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

        // 6. 发送审核通过短信（不含密码，引导用户通过短信验证码登录）
        sendApprovedSms(apply.getMobile(), apply.getShopName());
    }

    private TenantSaveReqVO buildTenantSaveReqVO(MerchantApplyDO apply) {
        TenantSaveReqVO vo = new TenantSaveReqVO();
        vo.setName(apply.getShopName());
        vo.setContactName(apply.getShopName());
        vo.setContactMobile(apply.getMobile());
        vo.setStatus(0); // 启用
        vo.setPackageId(defaultPackageId);
        // 系统租户有效期设为 100 年，实际订阅由 tenant_subscription 管理
        vo.setExpireTime(LocalDateTime.now().plusYears(100));
        vo.setAccountCount(999);
        // 管理员账号：手机号作为用户名，密码由首次短信验证码登录后修改
        vo.setUsername(apply.getMobile());
        vo.setPassword(generatePlaceholderPassword());
        vo.setWebsites(Collections.emptyList());
        return vo;
    }

    /**
     * 生成占位密码（SecureRandom 高熵，永不外发）。
     *
     * <p>商户应使用短信验证码首次登录，登录后强制修改密码；该占位密码仅用于通过
     * system-user 创建时的密码复杂度校验。</p>
     *
     * <p>实现要点：
     * <ul>
     *     <li>使用 {@link SecureRandom}（非 {@code Random}、非 {@code ThreadLocalRandom}）</li>
     *     <li>{@value #PLACEHOLDER_PASSWORD_LENGTH} 位长，字符集包含大小写字母、数字、符号，覆盖常见密码策略</li>
     *     <li>与商户个人信息（手机号/姓名）无关，泄露一条无法推断其他账号</li>
     * </ul>
     */
    private String generatePlaceholderPassword() {
        StringBuilder sb = new StringBuilder(PLACEHOLDER_PASSWORD_LENGTH);
        for (int i = 0; i < PLACEHOLDER_PASSWORD_LENGTH; i++) {
            sb.append(PLACEHOLDER_CHARS[SECURE_RANDOM.nextInt(PLACEHOLDER_CHARS.length)]);
        }
        return sb.toString();
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

    private void sendApprovedSms(String mobile, String shopName) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("shopName", shopName);
            smsSendService.sendSingleSmsToMember(mobile, null, SMS_TEMPLATE_APPROVED, params);
        } catch (Exception e) {
            log.warn("[sendApprovedSms] 发送审核通过短信失败，mobile={}", mobile, e);
        }
    }

    // ==================== 审核驳回 ====================

    @LogRecord(type = MerchantLogRecordConstants.MERCHANT_APPLY_TYPE,
            subType = MerchantLogRecordConstants.MERCHANT_APPLY_AUDIT_SUB_TYPE,
            bizNo = "{{#apply.id}}",
            success = MerchantLogRecordConstants.MERCHANT_APPLY_REJECT_SUCCESS)
    private void rejectApply(MerchantApplyDO apply, Long auditorId, String rejectReason) {
        MerchantApplyDO update = new MerchantApplyDO();
        update.setId(apply.getId());
        update.setStatus(2);
        update.setAuditorId(auditorId);
        update.setAuditTime(LocalDateTime.now());
        update.setRejectReason(rejectReason);
        merchantApplyMapper.updateById(update);

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
        if (days <= 0) {
            throw exception(RENEW_DAYS_INVALID);
        }
        TenantSubscriptionDO subscription = tenantSubscriptionMapper.selectByTenantId(tenantId);
        if (subscription == null) {
            throw exception(SUBSCRIPTION_NOT_FOUND);
        }
        TenantSubscriptionDO update = new TenantSubscriptionDO();
        update.setId(subscription.getId());
        // 已过期从今天起算；未过期从到期时间续
        LocalDateTime base = subscription.getExpireTime().isBefore(LocalDateTime.now())
                ? LocalDateTime.now() : subscription.getExpireTime();
        update.setExpireTime(base.plusDays(days));
        update.setStatus(2); // 正式
        tenantSubscriptionMapper.updateById(update);

        // 触发推N返1检查
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
            throw exception(SUBSCRIPTION_NOT_FOUND);
        }
        TenantSubscriptionDO update = new TenantSubscriptionDO();
        update.setId(subscription.getId());
        update.setStatus(4); // 禁用
        tenantSubscriptionMapper.updateById(update);
        log.info("[disableSubscription] 租户 tenantId={} 订阅已禁用", tenantId);
    }

    @Override
    public void expireOverdueSubscriptions() {
        // 每次扫描最多 EXPIRE_BATCH_SIZE 条（由 selectExpiredActive 的 LIMIT 控制），
        // 若积压超过一批则循环处理，避免剩余行滞留到下个调度周期才过期。
        // 额外加 maxIterations 上限防御：最极端情况下单次调度最多处理 EXPIRE_BATCH_SIZE * 50 = 10,000 条。
        int totalProcessed = 0;
        int iterations = 0;
        int maxIterations = 50;
        while (iterations++ < maxIterations) {
            List<TenantSubscriptionDO> overdueList = tenantSubscriptionMapper.selectExpiredActive();
            if (overdueList.isEmpty()) {
                break;
            }
            List<Long> ids = overdueList.stream()
                    .map(TenantSubscriptionDO::getId)
                    .collect(Collectors.toList());
            tenantSubscriptionMapper.batchExpire(ids);
            totalProcessed += ids.size();
            // 未达到批次上限说明剩余无过期记录，无需继续
            if (ids.size() < EXPIRE_BATCH_SIZE) {
                break;
            }
        }
        if (totalProcessed > 0) {
            log.info("[expireOverdueSubscriptions] 本轮共批量过期 {} 条订阅（迭代 {} 次）",
                    totalProcessed, iterations);
        }
        if (iterations >= maxIterations) {
            log.warn("[expireOverdueSubscriptions] 达到最大迭代次数 {}，可能存在大量积压，请检查调度频率",
                    maxIterations);
        }
    }

}
