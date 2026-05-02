package cn.iocoder.yudao.module.merchant.service;

import cn.hutool.core.util.IdUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.MerchantAuditReqVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.MerchantCreateReqVO;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.MerchantPageReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantVideoQuotaLogDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.TenantSubscriptionDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MerchantMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.TenantSubscriptionMapper;
import cn.iocoder.yudao.module.merchant.enums.MerchantStatusEnum;
import cn.iocoder.yudao.module.system.controller.admin.tenant.vo.tenant.TenantSaveReqVO;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import cn.binarywang.wx.miniapp.api.WxMaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Collections;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.VIDEO_QUOTA_INSUFFICIENT;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.VIDEO_QUOTA_UPDATE_FAILED;

/**
 * 商户 Service 实现类
 */
@Service
@Validated
@Slf4j
public class MerchantServiceImpl implements MerchantService {

    @Resource
    private MerchantMapper merchantMapper;

    @Resource
    private MerchantVideoQuotaLogService merchantVideoQuotaLogService;

    @Resource
    private TenantService tenantService;
    @Resource
    private ShopInfoMapper shopInfoMapper;
    @Resource
    private TenantSubscriptionMapper tenantSubscriptionMapper;
    @Resource
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private WxMaService wxMaService;

    @Value("${merchant.qrcode.page:pages/shop/index}")
    private String qrCodePage;

    @Value("${merchant.qrcode.upload-path:/tmp/qrcode/}")
    private String qrCodeUploadPath;

    @Value("${yudao.tenant.default-package-id:111}")
    private Long defaultPackageId;

    private static final int TRIAL_DAYS = 30;
    private static final int TRIAL_AI_VIDEO_QUOTA = 1;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] PLACEHOLDER_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#%".toCharArray();

    @Override
    public Long createMerchant(MerchantCreateReqVO createReqVO, Long userId) {
        // 校验是否已申请
        MerchantDO existMerchant = merchantMapper.selectByUserId(userId);
        if (existMerchant != null) {
            throw exception0(1_020_001_000, "您已提交过商户入驻申请");
        }
        // 创建商户
        MerchantDO merchant = MerchantDO.builder()
                .name(createReqVO.getName())
                .logo(createReqVO.getLogo())
                .contactName(createReqVO.getContactName())
                .contactPhone(createReqVO.getContactPhone())
                .licenseNo(createReqVO.getLicenseNo())
                .licenseUrl(createReqVO.getLicenseUrl())
                .legalPersonName(createReqVO.getLegalPersonName())
                .legalPersonIdCard(createReqVO.getLegalPersonIdCard())
                .legalPersonIdCardFrontUrl(createReqVO.getLegalPersonIdCardFrontUrl())
                .legalPersonIdCardBackUrl(createReqVO.getLegalPersonIdCardBackUrl())
                .bankAccountName(createReqVO.getBankAccountName())
                .bankAccountNo(createReqVO.getBankAccountNo())
                .bankName(createReqVO.getBankName())
                .businessCategory(createReqVO.getBusinessCategory())
                .status(MerchantStatusEnum.PENDING.getStatus())
                .userId(userId)
                .build();
        merchantMapper.insert(merchant);
        return merchant.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditMerchant(MerchantAuditReqVO auditReqVO) {
        MerchantDO merchant = merchantMapper.selectById(auditReqVO.getId());
        if (merchant == null) {
            throw exception0(1_020_001_001, "商户不存在");
        }
        MerchantDO updateObj = new MerchantDO();
        updateObj.setId(merchant.getId());
        updateObj.setAuditTime(LocalDateTime.now());
        if (Boolean.TRUE.equals(auditReqVO.getApproved())) {
            updateObj.setStatus(MerchantStatusEnum.APPROVED.getStatus());
            // 审核通过后自动生成小程序码
            try {
                String qrCodeUrl = generateMiniAppQrCode(merchant.getId());
                updateObj.setMiniAppQrCodeUrl(qrCodeUrl);
            } catch (Exception e) {
                log.warn("[auditMerchant] 自动生成小程序码失败，可稍后手动生成", e);
            }
        } else {
            updateObj.setStatus(MerchantStatusEnum.REJECTED.getStatus());
            updateObj.setRejectReason(auditReqVO.getRejectReason());
        }
        merchantMapper.updateById(updateObj);
    }

    @Override
    public MerchantDO getMerchant(Long id) {
        return merchantMapper.selectById(id);
    }

    @Override
    public MerchantDO getMerchantByUserId(Long userId) {
        return merchantMapper.selectByUserId(userId);
    }

    @Override
    public MerchantDO getMerchantByOpenId(String openId) {
        if (openId == null || openId.isEmpty()) {
            return null;
        }
        return merchantMapper.selectByOpenId(openId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createMerchantFromMember(Long memberUserId, String openId, String unionId,
                                         String phone, Long inviteCodeId) {
        // 幂等：openId / userId 全局唯一（同手机号不论之前注册到哪个租户都算已建）。
        // merchant_info 是 TenantBaseDO，外层 controller 此处 ignore=false 且当前线程
        // 没设 tenantId（PermitAll 申请路径），直接查会触发 TenantLineInnerInterceptor
        // 走 getRequiredTenantId NPE。显式 executeIgnore 让这两次幂等查询跨租户。
        MerchantDO existed = TenantUtils.executeIgnore(() -> merchantMapper.selectByOpenId(openId));
        if (existed != null) {
            return existed.getId();
        }
        MerchantDO existedByUser = TenantUtils.executeIgnore(() -> merchantMapper.selectByUserId(memberUserId));
        if (existedByUser != null) {
            return existedByUser.getId();
        }

        String shopName = "新店" + memberUserId;

        // ========== 完整开通流程（与 MerchantApplyServiceImpl.approveApply 对齐）==========
        // 1. 创建系统租户（system_tenant）— 商户后续登录的 tenant-id 由此生成。
        //
        // 历史问题：原本调 yudao TenantService.createTenant(...) 会同时 createRole +
        // createUser，但我们的商户登录走 member_user + JWT，不依赖 system_user/role。
        // yudao 的 createRole 在并发/边界场景会撞 ROLE_NAME_DUPLICATE 把整个申请流程拖垮。
        // 改为直接 INSERT system_tenant 行，跳过 createRole/createUser 副作用。
        // contact_user_id 暂留 NULL（PC 后台"租户管理"页若需可手动绑定 admin）。
        Long tenantId = createTenantDirectly(shopName, phone, defaultPackageId);

        // 2. 初始化 shop_info（商户分享码 / 综合排名 / 营业状态都依赖此表）
        ShopInfoDO shopInfo = ShopInfoDO.builder()
                .tenantId(tenantId)
                .shopName(shopName)
                .categoryId(1L)
                .longitude(BigDecimal.ZERO)
                .latitude(BigDecimal.ZERO)
                .status(1) // 正常营业
                .sales30d(0)
                .avgRating(new BigDecimal("5.0"))
                .balance(0)
                .build();
        shopInfoMapper.insert(shopInfo);

        // 3. 初始化订阅（30 天试用 + 1 次 AI 视频配额）
        TenantSubscriptionDO subscription = TenantSubscriptionDO.builder()
                .tenantId(tenantId)
                .status(1)
                .expireTime(LocalDateTime.now().plusDays(TRIAL_DAYS))
                .aiVideoQuota(TRIAL_AI_VIDEO_QUOTA)
                .aiVideoUsed(0)
                .build();
        tenantSubscriptionMapper.insert(subscription);

        // 3.5 复制 admin 租户的 pay_app + pay_channel 到新租户
        // 原因：PayAppDO/PayChannelDO 都是 TenantBaseDO，按 tenant_id 隔离；
        // 商户租户开通后没复制 → trade 模块下单调 selectByAppKey('mall') 找不到 → 用户在商户店铺无法支付
        copyPayResourcesToNewTenant(tenantId);

        // 4. 创建 merchant_info（继承 TenantBaseDO，靠 TenantContextHolder 由 MP 拦截器自动填 tenant_id）
        MerchantDO merchant = MerchantDO.builder()
                .name(shopName)
                .contactName(shopName) // contact_name NOT NULL 无默认值，先复用店名占位（用户后续可改）
                .contactPhone(phone)
                .status(MerchantStatusEnum.APPROVED.getStatus())
                .userId(memberUserId)
                .openId(openId)
                .unionId(unionId)
                .inviteCodeId(inviteCodeId)
                .build();
        TenantUtils.execute(tenantId, () -> merchantMapper.insert(merchant));

        log.info("[createMerchantFromMember] 全套开通完成 userId={} merchantId={} tenantId={} shopInfoId={} subscriptionExpire=+{}d",
                memberUserId, merchant.getId(), tenantId, shopInfo.getId(), TRIAL_DAYS);
        return merchant.getId();
    }

    private String generatePlaceholderPassword() {
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(PLACEHOLDER_CHARS[SECURE_RANDOM.nextInt(PLACEHOLDER_CHARS.length)]);
        }
        return sb.toString();
    }

    /**
     * 直接 INSERT system_tenant 行，跳过 yudao TenantService.createTenant 的
     * createRole + createUser 副作用 —— 避免反复撞 ROLE_NAME_DUPLICATE。
     *
     * <p>商户登录走 member_user + JWT，不依赖 system_user / system_role。
     * contact_user_id 暂留 NULL；PC 后台「租户管理」页若需要操作租户管理员，
     * 由超管手动绑定即可。</p>
     *
     * <p>事务由调用方 @Transactional 控制；本方法内若 INSERT 失败会抛
     * DataAccessException 让外层回滚。</p>
     *
     * @return 新创建的 tenantId（主键）
     */
    private Long createTenantDirectly(String shopName, String phone, Long packageId) {
        // 1. INSERT system_tenant 并通过 GeneratedKey 拿到自增 id
        org.springframework.jdbc.support.GeneratedKeyHolder kh =
                new org.springframework.jdbc.support.GeneratedKeyHolder();
        final String contactName = shopName;
        final String contactMobile = phone;
        final java.time.LocalDateTime expireTime = java.time.LocalDateTime.now().plusYears(100);
        jdbcTemplate.update(con -> {
            java.sql.PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO system_tenant " +
                    "(name, contact_user_id, contact_name, contact_mobile, status, " +
                    " websites, package_id, expire_time, account_count, " +
                    " creator, create_time, updater, update_time, deleted) " +
                    "VALUES (?, NULL, ?, ?, 0, '', ?, ?, 999, 'system', NOW(), 'system', NOW(), b'0')",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, shopName);
            ps.setString(2, contactName);
            ps.setString(3, contactMobile);
            ps.setLong(4, packageId);
            ps.setTimestamp(5, java.sql.Timestamp.valueOf(expireTime));
            return ps;
        }, kh);
        Number key = kh.getKey();
        if (key == null) {
            throw new IllegalStateException("createTenantDirectly 拿不到自增 id");
        }
        Long tenantId = key.longValue();
        log.info("[createTenantDirectly] 直建租户 tenantId={} shopName={} mobile={}",
                tenantId, shopName, phone);
        return tenantId;
    }

    /**
     * 把 admin 租户（id=1）的 pay_app + pay_channel 复制到新租户。
     *
     * <p>yudao 默认 tenant 初始化只复制 menu_ids 给 admin 角色，
     * 不复制 pay_app / pay_channel 等业务资源。商户租户开通后没有
     * mall 应用 → trade 模块下单时按 yudao.trade.order.payAppKey
     * 查 pay_app(tenant=商户) 找不到 → H5 用户在商户店铺无法支付。</p>
     *
     * <p>用 JdbcTemplate 直接执行 INSERT … SELECT，避开跨模块 import
     * pay 模块的 mapper 接口。两条 SQL 在同一个商户开通事务内，要么都成功要么都回滚。</p>
     *
     * <p>幂等：再次调用时不重复插入（由 (tenant_id, app_key) 联合判断 NOT EXISTS）。</p>
     *
     * <p>失败抛 RuntimeException 让调用方事务回滚 — 之前是 log.warn 软失败，
     * 演示当天用户下单时才会因 pay_app 缺失而崩，此处改为硬失败让商户开通流程
     * 立即暴露问题，由 PC 运维及时处理。</p>
     */
    @Override
    public void copyPayResourcesToNewTenant(Long newTenantId) {
        try {
            int appCopied = jdbcTemplate.update(
                "INSERT INTO pay_app (app_key, name, status, remark, " +
                "  order_notify_url, refund_notify_url, transfer_notify_url, " +
                "  tenant_id, creator, create_time, updater, update_time, deleted) " +
                "SELECT app_key, name, status, remark, " +
                "  order_notify_url, refund_notify_url, transfer_notify_url, " +
                "  ?, creator, NOW(), updater, NOW(), deleted " +
                "FROM pay_app src WHERE src.tenant_id = 1 AND src.deleted = b'0' " +
                "  AND NOT EXISTS (SELECT 1 FROM pay_app dst " +
                "    WHERE dst.tenant_id = ? AND dst.app_key = src.app_key AND dst.deleted = b'0')",
                newTenantId, newTenantId
            );
            int channelCopied = jdbcTemplate.update(
                "INSERT INTO pay_channel (code, status, fee_rate, remark, app_id, config, " +
                "  tenant_id, creator, create_time, updater, update_time, deleted) " +
                "SELECT c.code, c.status, c.fee_rate, c.remark, " +
                "  (SELECT a2.id FROM pay_app a2 WHERE a2.app_key = a1.app_key AND a2.tenant_id = ? LIMIT 1), " +
                "  c.config, ?, c.creator, NOW(), c.updater, NOW(), c.deleted " +
                "FROM pay_channel c JOIN pay_app a1 ON c.app_id = a1.id " +
                "WHERE c.tenant_id = 1 AND c.deleted = b'0' " +
                "  AND NOT EXISTS (SELECT 1 FROM pay_channel dst " +
                "    WHERE dst.tenant_id = ? AND dst.code = c.code AND dst.deleted = b'0')",
                newTenantId, newTenantId, newTenantId
            );
            log.info("[copyPayResourcesToNewTenant] 复制完成 tenantId={} app={}行 channel={}行",
                    newTenantId, appCopied, channelCopied);
        } catch (Exception e) {
            log.error("[copyPayResourcesToNewTenant] 复制失败 tenantId={}", newTenantId, e);
            throw new RuntimeException(
                "新商户支付资源初始化失败 (tenantId=" + newTenantId + ")，请检查 admin 租户的 pay_app/pay_channel", e);
        }
    }

    @Override
    public PageResult<MerchantDO> getMerchantPage(MerchantPageReqVO pageReqVO) {
        return merchantMapper.selectPage(pageReqVO);
    }

    @Override
    public void submitWxPayApplyment(Long merchantId) {
        MerchantDO merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw exception0(1_020_001_001, "商户不存在");
        }
        if (!MerchantStatusEnum.APPROVED.getStatus().equals(merchant.getStatus())) {
            throw exception0(1_020_001_002, "商户未审核通过，无法提交进件");
        }

        log.info("[submitWxPayApplyment] 商户({}) 提交微信支付进件", merchantId);

        // 调用微信支付服务商 API 提交特约商户进件
        // POST https://api.mch.weixin.qq.com/v3/applyment4sub/applyment/
        // 实际项目中需要使用 wechatpay-java SDK 调用
        // 这里构建请求体展示完整参数
        /*
         * 请求参数说明：
         * business_code: 业务申请编号（唯一）
         * contact_info: 超级管理员信息
         *   - contact_name: 联系人姓名（加密）
         *   - mobile_phone: 联系人手机号（加密）
         * subject_info: 主体资料
         *   - subject_type: 主体类型（SUBJECT_TYPE_INDIVIDUAL=个体户, SUBJECT_TYPE_ENTERPRISE=企业）
         *   - business_license_info: 营业执照信息
         *     - license_copy: 营业执照照片
         *     - license_number: 注册号/统一社会信用代码
         *     - merchant_name: 商户名称
         *     - legal_representative: 法人姓名
         *   - identity_info: 经营者/法人身份证信息
         *     - id_card_copy: 身份证正面照片
         *     - id_card_national: 身份证反面照片
         *     - id_card_name: 身份证姓名（加密）
         *     - id_card_number: 身份证号码（加密）
         * business_info: 经营资料
         *   - merchant_shortname: 商户简称
         *   - service_phone: 客服电话
         * settlement_info: 结算规则
         *   - settlement_id: 结算规则ID
         *   - qualification_type: 资质类型
         * bank_account_info: 结算银行账户
         *   - bank_account_type: 账户类型
         *   - account_name: 开户名称（加密）
         *   - account_bank: 开户银行
         *   - account_number: 银行账号（加密）
         */

        String applymentId = "applyment_" + IdUtil.fastSimpleUUID();

        // 更新进件状态
        MerchantDO updateObj = new MerchantDO();
        updateObj.setId(merchantId);
        updateObj.setWxApplymentId(applymentId);
        updateObj.setWxApplymentStatus("APPLYMENT_STATE_AUDITING");
        merchantMapper.updateById(updateObj);

        log.info("[submitWxPayApplyment] 进件已提交, applymentId: {}", applymentId);
    }

    @Override
    public String generateMiniAppQrCode(Long merchantId) {
        log.info("[generateMiniAppQrCode] 商户({}) 生成小程序码", merchantId);

        if (wxMaService == null) {
            log.warn("[generateMiniAppQrCode] WxMaService未配置，跳过小程序码生成");
            return null;
        }

        try {
            // 使用 wxacode.getUnlimited 接口生成不限数量的小程序码
            // scene 参数最大32个字符
            String scene = "mid=" + merchantId;

            // 生成小程序码（二进制数据）
            byte[] qrCodeBytes = wxMaService.getQrcodeService().createWxaCodeUnlimitBytes(
                    scene,           // scene 参数
                    qrCodePage,      // 小程序页面路径
                    false,           // 是否需要透明底色
                    "release",       // 要打开的小程序版本
                    430,             // 二维码宽度
                    true,            // 自动配置线条颜色
                    null,            // 线条颜色
                    false            // 是否开启 Hyaline（透明底）
            );

            // 保存到文件并上传
            // 实际项目中应上传到 OSS（通过 yudao-module-infra 的文件服务）
            File dir = new File(qrCodeUploadPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String fileName = "merchant_" + merchantId + "_" + System.currentTimeMillis() + ".png";
            File file = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(qrCodeBytes);
            }

            String qrCodeUrl = "/qrcode/" + fileName;
            log.info("[generateMiniAppQrCode] 小程序码生成成功: {}", qrCodeUrl);

            // 更新商户记录
            MerchantDO updateObj = new MerchantDO();
            updateObj.setId(merchantId);
            updateObj.setMiniAppQrCodeUrl(qrCodeUrl);
            merchantMapper.updateById(updateObj);

            return qrCodeUrl;
        } catch (Exception e) {
            log.error("[generateMiniAppQrCode] 生成小程序码失败", e);
            throw new RuntimeException("生成小程序码失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void disableMerchant(Long merchantId) {
        MerchantDO merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw exception0(1_020_001_001, "商户不存在");
        }
        MerchantDO updateObj = new MerchantDO();
        updateObj.setId(merchantId);
        updateObj.setStatus(MerchantStatusEnum.DISABLED.getStatus());
        merchantMapper.updateById(updateObj);
    }

    @Override
    public void enableMerchant(Long merchantId) {
        MerchantDO merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw exception0(1_020_001_001, "商户不存在");
        }
        MerchantDO updateObj = new MerchantDO();
        updateObj.setId(merchantId);
        updateObj.setStatus(MerchantStatusEnum.APPROVED.getStatus());
        merchantMapper.updateById(updateObj);
    }

    // ========== AI 视频配额（Phase 0.3.1） ==========

    /**
     * 使用 {@link Propagation#REQUIRES_NEW} 独立事务，防止与 HTTP 调用包裹在同一事务里长时间占用连接。
     * 先原子 +；affected=0 → 商户不存在；再 selectById 读取对齐后的 quota_after 写流水。
     *
     * <p>幂等保护：流水表 {@code uk_biz(biz_type, biz_id)} UNIQUE。insertLog 重复时 return false，
     * 抛异常让 {@code @Transactional} 回滚本次 {@code UPDATE}——配额不会被重复增加（支付回调重试、BFF 重放场景）。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public QuotaChangeResult increaseVideoQuota(Long merchantId, int delta, Integer bizType, String bizId, String remark) {
        if (merchantId == null || delta <= 0) {
            throw exception(VIDEO_QUOTA_UPDATE_FAILED);
        }
        int affected = merchantMapper.incrementVideoQuotaAtomic(merchantId, delta);
        if (affected == 0) {
            log.warn("[increaseVideoQuota] 商户不存在或已删除 merchantId={} delta={}", merchantId, delta);
            throw exception0(1_020_001_001, "商户不存在");
        }
        MerchantDO merchant = merchantMapper.selectById(merchantId);
        Integer after = merchant == null || merchant.getVideoQuotaRemaining() == null
                ? delta : merchant.getVideoQuotaRemaining();
        MerchantVideoQuotaLogDO logDO = MerchantVideoQuotaLogDO.builder()
                .merchantId(merchantId)
                .quotaChange(delta)
                .quotaAfter(after)
                .bizType(bizType)
                .bizId(bizId)
                .remark(remark)
                .build();
        boolean inserted = merchantVideoQuotaLogService.insertLog(logDO);
        if (!inserted) {
            // 流水重复 → 抛异常让 @Transactional 回滚 UPDATE，避免配额被重复增加
            log.warn("[increaseVideoQuota] 流水重复，回滚本次扣减 merchantId={} bizType={} bizId={}",
                    merchantId, bizType, bizId);
            throw exception(VIDEO_QUOTA_UPDATE_FAILED);
        }
        log.info("[increaseVideoQuota] merchantId={} +{} after={} bizType={} bizId={} logId={}",
                merchantId, delta, after, bizType, bizId, logDO.getId());
        // MyBatis Plus insert 成功后会把 PK 回填到 DO，直接取 logDO.getId()
        return new QuotaChangeResult(after, logDO.getId());
    }

    /**
     * 原子扣减：SQL 里带 {@code video_quota_remaining >= delta} 守护；
     * affected=0 → 余额不足，抛 {@code VIDEO_QUOTA_INSUFFICIENT}（用户侧友好提示）。
     *
     * <p>REQUIRES_NEW 独立事务：调用方做远程 HTTP 时不会被锁住；失败的 BFF 调用方另行调
     * {@link #increaseVideoQuota} 补偿。</p>
     *
     * <p>幂等保护：同 {@link #increaseVideoQuota} —— 流水 UNIQUE 冲突时回滚 UPDATE。
     * VIDEO_GEN 调用处使用随机 UUID 作为 bizId，天然唯一。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public QuotaChangeResult decreaseVideoQuota(Long merchantId, int delta, Integer bizType, String bizId, String remark) {
        if (merchantId == null || delta <= 0) {
            throw exception(VIDEO_QUOTA_UPDATE_FAILED);
        }
        int affected = merchantMapper.decrementVideoQuotaAtomic(merchantId, delta);
        if (affected == 0) {
            // 可能是商户不存在，但更常见是余额不足——统一抛 INSUFFICIENT 提示购买套餐
            log.info("[decreaseVideoQuota] 余额不足或商户不存在 merchantId={} delta={}", merchantId, delta);
            throw exception(VIDEO_QUOTA_INSUFFICIENT);
        }
        MerchantDO merchant = merchantMapper.selectById(merchantId);
        Integer after = merchant == null || merchant.getVideoQuotaRemaining() == null
                ? 0 : merchant.getVideoQuotaRemaining();
        MerchantVideoQuotaLogDO logDO = MerchantVideoQuotaLogDO.builder()
                .merchantId(merchantId)
                .quotaChange(-delta)
                .quotaAfter(after)
                .bizType(bizType)
                .bizId(bizId)
                .remark(remark)
                .build();
        boolean inserted = merchantVideoQuotaLogService.insertLog(logDO);
        if (!inserted) {
            // 流水重复 → 抛异常让 @Transactional 回滚 UPDATE，避免配额被重复扣减
            log.warn("[decreaseVideoQuota] 流水重复，回滚本次扣减 merchantId={} bizType={} bizId={}",
                    merchantId, bizType, bizId);
            throw exception(VIDEO_QUOTA_UPDATE_FAILED);
        }
        log.info("[decreaseVideoQuota] merchantId={} -{} after={} bizType={} bizId={} logId={}",
                merchantId, delta, after, bizType, bizId, logDO.getId());
        return new QuotaChangeResult(after, logDO.getId());
    }

}
