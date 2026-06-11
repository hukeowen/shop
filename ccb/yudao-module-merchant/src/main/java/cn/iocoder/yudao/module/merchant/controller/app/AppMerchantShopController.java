package cn.iocoder.yudao.module.merchant.controller.app;

import cn.hutool.crypto.SecureUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopBrokerageConfigDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MerchantMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopBrokerageConfigMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper;
import cn.iocoder.yudao.module.merchant.service.KycSignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 商户小程序 - 店铺设置（#26）
 */
@Tag(name = "商户小程序 - 店铺设置")
@RestController
@RequestMapping("/merchant/mini/shop")
@Validated
public class AppMerchantShopController {

    @Value("${merchant.field-encrypt-key}")
    private String fieldEncryptKey;

    @Resource
    private ShopInfoMapper shopInfoMapper;
    @Resource
    private ShopBrokerageConfigMapper shopBrokerageConfigMapper;
    @Resource
    private MerchantMapper merchantMapper;
    @Resource
    private KycSignService kycSignService;

    @GetMapping("/info")
    @Operation(summary = "获取店铺信息")
    public CommonResult<ShopInfoDO> getShopInfo() {
        Long tenantId = TenantContextHolder.getTenantId();
        return success(shopInfoMapper.selectByTenantId(tenantId));
    }

    @PutMapping("/info")
    @Operation(summary = "更新店铺信息（名称/封面/简介/公告/营业时间/地址）")
    public CommonResult<Boolean> updateShopInfo(@Valid @RequestBody ShopInfoDO updateDO) {
        Long tenantId = TenantContextHolder.getTenantId();
        ShopInfoDO existing = shopInfoMapper.selectByTenantId(tenantId);
        if (existing == null) {
            throw exception0(1_020_005_000, "店铺信息不存在");
        }
        // 只允许更新可编辑字段
        ShopInfoDO update = new ShopInfoDO();
        update.setId(existing.getId());
        update.setShopName(updateDO.getShopName());
        update.setCoverUrl(updateDO.getCoverUrl());
        update.setDescription(updateDO.getDescription());
        update.setNotice(updateDO.getNotice());
        update.setBusinessHours(updateDO.getBusinessHours());
        update.setBusinessHoursJson(updateDO.getBusinessHoursJson());
        update.setManualClosed(updateDO.getManualClosed());
        update.setBusinessType(updateDO.getBusinessType()); // V040
        update.setMobile(updateDO.getMobile());
        update.setLongitude(updateDO.getLongitude());
        update.setLatitude(updateDO.getLatitude());
        update.setAddress(updateDO.getAddress());
        update.setFeatureTags(updateDO.getFeatureTags()); // 之前漏了：特色标签保存不进 DB
        update.setWechatPayQrUrl(updateDO.getWechatPayQrUrl()); // 线下转账微信收款码
        update.setAlipayPayQrUrl(updateDO.getAlipayPayQrUrl()); // 线下转账支付宝收款码
        shopInfoMapper.updateById(update);
        return success(true);
    }

    // ==================== 营业打卡 + 状态 (V039) ====================

    @PostMapping("/check-in")
    @Operation(summary = "今日营业打卡（每天首次进商户首页时点一次，记录 today_open_at = today）")
    public CommonResult<java.util.Map<String, Object>> checkIn() {
        Long tenantId = TenantContextHolder.getTenantId();
        ShopInfoDO existing = shopInfoMapper.selectByTenantId(tenantId);
        if (existing == null) {
            throw exception0(1_020_005_000, "店铺信息不存在");
        }
        java.time.LocalDate today = java.time.LocalDate.now();
        ShopInfoDO update = new ShopInfoDO();
        update.setId(existing.getId());
        update.setTodayOpenAt(today);
        // 打卡同时清掉主动打烊，"开张"动作语义清晰
        update.setManualClosed(false);
        shopInfoMapper.updateById(update);
        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("todayOpenAt", today.toString());
        resp.put("manualClosed", false);
        return success(resp);
    }

    @GetMapping("/operating-status")
    @Operation(summary = "查询当前店铺营业状态（OPEN / OUTSIDE_HOURS / HIDDEN）+ 各闸门细节")
    public CommonResult<java.util.Map<String, Object>> operatingStatus() {
        Long tenantId = TenantContextHolder.getTenantId();
        ShopInfoDO shop = shopInfoMapper.selectByTenantId(tenantId);
        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        if (shop == null) {
            resp.put("status", "HIDDEN");
            resp.put("checkedInToday", false);
            resp.put("manualClosed", false);
            return success(resp);
        }
        java.time.LocalDate today = java.time.LocalDate.now();
        boolean checkedIn = shop.getTodayOpenAt() != null && shop.getTodayOpenAt().equals(today);
        resp.put("status",
                cn.iocoder.yudao.module.merchant.util.ShopOperatingUtils
                        .computeStatus(shop).name());
        resp.put("checkedInToday", checkedIn);
        resp.put("todayOpenAt", shop.getTodayOpenAt() == null ? null : shop.getTodayOpenAt().toString());
        resp.put("manualClosed", Boolean.TRUE.equals(shop.getManualClosed()));
        resp.put("businessHoursJson", shop.getBusinessHoursJson());
        return success(resp);
    }

    @PutMapping("/manual-closed")
    @Operation(summary = "切换主动打烊开关")
    public CommonResult<Boolean> setManualClosed(@RequestParam("closed") Boolean closed) {
        Long tenantId = TenantContextHolder.getTenantId();
        ShopInfoDO existing = shopInfoMapper.selectByTenantId(tenantId);
        if (existing == null) {
            throw exception0(1_020_005_000, "店铺信息不存在");
        }
        ShopInfoDO update = new ShopInfoDO();
        update.setId(existing.getId());
        update.setManualClosed(closed);
        shopInfoMapper.updateById(update);
        return success(true);
    }

    @GetMapping("/brokerage-config")
    @Operation(summary = "获取返佣与积分配置")
    public CommonResult<ShopBrokerageConfigDO> getBrokerageConfig() {
        ShopBrokerageConfigDO config = shopBrokerageConfigMapper.selectCurrent();
        if (config == null) {
            // 返回默认空配置（前端第一次进入时展示默认值）
            config = new ShopBrokerageConfigDO();
            config.setBrokerageEnabled(false);
            config.setFirstBrokeragePercent(java.math.BigDecimal.ZERO);
            config.setSecondBrokeragePercent(java.math.BigDecimal.ZERO);
            config.setFreezeDays(7);
            config.setPushReturnEnabled(false);
            config.setPushN(5);
            config.setReturnAmount(0);
            config.setPointPerYuan(0);
            config.setMinWithdrawAmount(10000);
        }
        return success(config);
    }

    @PutMapping("/brokerage-config")
    @Operation(summary = "保存返佣与积分配置（upsert）")
    public CommonResult<Boolean> saveBrokerageConfig(@RequestBody ShopBrokerageConfigDO reqDO) {
        ShopBrokerageConfigDO existing = shopBrokerageConfigMapper.selectCurrent();
        if (existing == null) {
            shopBrokerageConfigMapper.insert(reqDO);
        } else {
            reqDO.setId(existing.getId());
            shopBrokerageConfigMapper.updateById(reqDO);
        }
        return success(true);
    }

    @PutMapping("/status")
    @Operation(summary = "更新营业状态（1正常 2暂停营业）")
    public CommonResult<Boolean> updateShopStatus(@RequestParam("status") Integer status) {
        Long tenantId = TenantContextHolder.getTenantId();
        ShopInfoDO existing = shopInfoMapper.selectByTenantId(tenantId);
        if (existing == null) {
            throw exception0(1_020_005_000, "店铺信息不存在");
        }
        ShopInfoDO update = new ShopInfoDO();
        update.setId(existing.getId());
        update.setStatus(status);
        shopInfoMapper.updateById(update);
        return success(true);
    }

    // ==================== 店铺二维码 ====================

    @GetMapping("/qrcode")
    @Operation(summary = "获取店铺专属二维码URL")
    public CommonResult<java.util.Map<String, String>> getQrCode() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        MerchantDO merchant = merchantMapper.selectByUserId(userId);
        String url = merchant != null ? merchant.getMiniAppQrCodeUrl() : null;
        java.util.Map<String, String> result = new java.util.HashMap<>();
        result.put("qrCodeUrl", url);
        return success(result);
    }

    // ==================== 在线支付开通申请 ====================

    @GetMapping("/pay-apply")
    @Operation(summary = "获取在线支付申请状态")
    public CommonResult<ShopInfoDO> getPayApply() {
        Long tenantId = TenantContextHolder.getTenantId();
        ShopInfoDO shop = shopInfoMapper.selectByTenantId(tenantId);
        if (shop == null) {
            throw exception0(1_020_005_000, "店铺信息不存在");
        }
        // 返回脱敏副本，不修改 MyBatis 缓存中的原始对象
        ShopInfoDO resp = new ShopInfoDO();
        resp.setId(shop.getId());
        resp.setTenantId(shop.getTenantId());
        resp.setShopName(shop.getShopName());
        resp.setTlMchId(shop.getTlMchId());
        resp.setPayApplyStatus(shop.getPayApplyStatus());
        resp.setOnlinePayEnabled(shop.getOnlinePayEnabled());
        resp.setPayApplyRejectReason(shop.getPayApplyRejectReason());
        // 进件 KYC 资质回显：只回 TOS key，前端拿到后调 /oss/sign 现签 1h 预签名 URL 显示
        resp.setIdCardFrontKey(shop.getIdCardFrontKey());
        resp.setIdCardBackKey(shop.getIdCardBackKey());
        resp.setBusinessLicenseKey(shop.getBusinessLicenseKey());
        // 进件结构化资料回显（非敏感字段明文回显，便于驳回后重提；敏感字段脱敏）
        resp.setComproperty(shop.getComproperty());
        resp.setMerchantFullName(shop.getMerchantFullName());
        resp.setLegalName(shop.getLegalName());
        resp.setLegalIdExpire(shop.getLegalIdExpire());
        resp.setCreditCode(shop.getCreditCode());
        resp.setCreditCodeExpire(shop.getCreditCodeExpire());
        resp.setSettleAcctName(shop.getSettleAcctName());
        resp.setSettleAcctType(shop.getSettleAcctType());
        resp.setSettleBankName(shop.getSettleBankName());
        resp.setSettleBankCode(shop.getSettleBankCode());
        resp.setSettleCnapsNo(shop.getSettleCnapsNo());
        resp.setContactEmail(shop.getContactEmail());
        // v047 进件全类型字段回显
        resp.setMccId(shop.getMccId());
        resp.setServicePhone(shop.getServicePhone());
        resp.setLegalIdType(shop.getLegalIdType());
        resp.setBusAddress(shop.getBusAddress());
        resp.setDistrictCode(shop.getDistrictCode());
        resp.setContactPerson(shop.getContactPerson());
        resp.setContactPhone(shop.getContactPhone());
        resp.setClearMode(shop.getClearMode());
        resp.setAcctTp(shop.getAcctTp());
        resp.setHolderName(shop.getHolderName());
        resp.setHolderExpire(shop.getHolderExpire());
        resp.setRegisterFund(shop.getRegisterFund());
        resp.setStaffTotal(shop.getStaffTotal());
        resp.setOperateLimit(shop.getOperateLimit());
        resp.setInspect(shop.getInspect());
        resp.setThrCertFlag(shop.getThrCertFlag());
        resp.setOrganCode(shop.getOrganCode());
        resp.setOrganExpire(shop.getOrganExpire());
        resp.setBusContactPerson(shop.getBusContactPerson());
        resp.setBusContactTel(shop.getBusContactTel());
        resp.setPubAcctInfo(shop.getPubAcctInfo());
        resp.setLegalHoldPicKey(shop.getLegalHoldPicKey());
        resp.setBizPlacePicKey(shop.getBizPlacePicKey());
        resp.setSettleBankPicKey(shop.getSettleBankPicKey());
        resp.setAcctLicensePicKey(shop.getAcctLicensePicKey());
        resp.setPersonHeadPicKey(shop.getPersonHeadPicKey());
        // 敏感字段脱敏（@TableField EncryptTypeHandler 读出来已是明文，这里只回前4后4）
        resp.setLegalIdNo(maskTail(shop.getLegalIdNo()));
        resp.setSettleAcctNo(maskTail(shop.getSettleAcctNo()));
        resp.setHolderIdNo(maskTail(shop.getHolderIdNo()));
        resp.setSettleIdNo(maskTail(shop.getSettleIdNo()));
        // 通联密钥脱敏（开通后由系统下发，前端只读展示前4后4）
        if (shop.getTlMchKey() != null) {
            try {
                String plain = SecureUtil.aes(fieldEncryptKey.getBytes()).decryptStr(shop.getTlMchKey());
                resp.setTlMchKey(plain.length() > 8
                        ? plain.substring(0, 4) + "****" + plain.substring(plain.length() - 4)
                        : "****");
            } catch (Exception e) {
                resp.setTlMchKey("****");
            }
        }
        return success(resp);
    }

    @GetMapping("/kyc-sign")
    @Operation(summary = "签发自己店铺 KYC 资质 TOS key 的临时 GET URL")
    public CommonResult<java.util.Map<String, String>> signOwnKycKey(@RequestParam String key,
                                                                     @RequestParam(defaultValue = "3600") int ttl) {
        Long tenantId = TenantContextHolder.getTenantId();
        ShopInfoDO shop = shopInfoMapper.selectByTenantId(tenantId);
        if (shop == null) {
            throw exception0(1_020_005_000, "店铺信息不存在");
        }
        // 必须是自己店铺持有的资质照 key 之一，否则越权
        java.util.Set<String> ownKeys = new java.util.HashSet<>(java.util.Arrays.asList(
                shop.getIdCardFrontKey(), shop.getIdCardBackKey(), shop.getBusinessLicenseKey(),
                shop.getLegalHoldPicKey(), shop.getBizPlacePicKey(), shop.getSettleBankPicKey(),
                shop.getAcctLicensePicKey(), shop.getPersonHeadPicKey()));
        if (!ownKeys.contains(key)) {
            throw exception0(1_020_005_004, "key 不属于当前店铺");
        }
        java.util.Map<String, String> resp = new java.util.HashMap<>();
        resp.put("url", kycSignService.sign(key, ttl));
        return success(resp);
    }

    @PostMapping("/pay-apply")
    @Operation(summary = "提交在线支付开通申请（进件资料：法人 + 营业执照 + 结算账户 + 资质照）")
    public CommonResult<Boolean> submitPayApply(@RequestBody ShopInfoDO reqDO) {
        Long tenantId = TenantContextHolder.getTenantId();
        ShopInfoDO existing = shopInfoMapper.selectByTenantId(tenantId);
        if (existing == null) {
            throw exception0(1_020_005_000, "店铺信息不存在");
        }
        Integer currentStatus = existing.getPayApplyStatus();
        if (currentStatus != null && currentStatus == 1) {
            throw exception0(1_020_005_001, "已提交申请，请等待审核");
        }
        if (currentStatus != null && currentStatus == 2) {
            throw exception0(1_020_005_002, "在线支付已开通，无需重复申请");
        }
        boolean personal = "4".equals(reqDO.getComproperty()); // 个人商户无营业执照
        // 必填校验：身份证正反面始终必填；营业执照非个人必填
        if (isBlank(reqDO.getIdCardFrontKey()) || isBlank(reqDO.getIdCardBackKey())) {
            throw exception0(1_020_005_003, "请上传法人身份证正反面");
        }
        if (!personal && isBlank(reqDO.getBusinessLicenseKey())) {
            throw exception0(1_020_005_003, "请上传营业执照");
        }
        // 必填校验：进件结构化资料
        if (isBlank(reqDO.getComproperty())) {
            throw exception0(1_020_005_005, "请选择商户性质");
        }
        if (isBlank(reqDO.getLegalName())) {
            throw exception0(1_020_005_005, "请填写法人/经营者姓名");
        }
        if (isBlank(reqDO.getLegalIdNo()) || reqDO.getLegalIdNo().contains("****")) {
            throw exception0(1_020_005_005, "请填写法人证件号");
        }
        if (isBlank(reqDO.getSettleAcctName())) {
            throw exception0(1_020_005_005, "请填写结算账户名");
        }
        if (isBlank(reqDO.getSettleAcctNo()) || reqDO.getSettleAcctNo().contains("****")) {
            throw exception0(1_020_005_005, "请填写结算账户号");
        }
        if (isBlank(reqDO.getSettleAcctType())) {
            throw exception0(1_020_005_005, "请选择账户类型（对私/对公）");
        }
        if (isBlank(reqDO.getSettleBankName())) {
            throw exception0(1_020_005_005, "请填写开户银行");
        }
        if (!personal && isBlank(reqDO.getCreditCode())) {
            throw exception0(1_020_005_005, "请填写统一社会信用代码（营业执照号）");
        }

        ShopInfoDO update = new ShopInfoDO();
        update.setId(existing.getId());
        update.setIdCardFrontKey(reqDO.getIdCardFrontKey());
        update.setIdCardBackKey(reqDO.getIdCardBackKey());
        update.setBusinessLicenseKey(reqDO.getBusinessLicenseKey());
        // 进件结构化资料
        update.setComproperty(reqDO.getComproperty());
        update.setMerchantFullName(reqDO.getMerchantFullName());
        update.setLegalName(reqDO.getLegalName());
        update.setLegalIdExpire(reqDO.getLegalIdExpire());
        update.setCreditCode(reqDO.getCreditCode());
        update.setCreditCodeExpire(reqDO.getCreditCodeExpire());
        update.setSettleAcctName(reqDO.getSettleAcctName());
        update.setSettleAcctType(reqDO.getSettleAcctType());
        update.setSettleBankName(reqDO.getSettleBankName());
        update.setSettleBankCode(reqDO.getSettleBankCode());
        update.setSettleCnapsNo(reqDO.getSettleCnapsNo());
        update.setContactEmail(reqDO.getContactEmail());
        // ===== v047 进件全类型字段 =====
        update.setMccId(reqDO.getMccId());
        update.setServicePhone(reqDO.getServicePhone());
        update.setLegalIdType(reqDO.getLegalIdType());
        update.setAddress(reqDO.getAddress());        // 注册地址（进件用，预填=店铺地址，可改）
        update.setBusAddress(reqDO.getBusAddress());
        update.setDistrictCode(reqDO.getDistrictCode());
        update.setContactPerson(reqDO.getContactPerson());
        update.setContactPhone(reqDO.getContactPhone());
        update.setClearMode(reqDO.getClearMode());
        update.setAcctTp(reqDO.getAcctTp());
        update.setHolderName(reqDO.getHolderName());
        update.setHolderExpire(reqDO.getHolderExpire());
        update.setRegisterFund(reqDO.getRegisterFund());
        update.setStaffTotal(reqDO.getStaffTotal());
        update.setOperateLimit(reqDO.getOperateLimit());
        update.setInspect(reqDO.getInspect());
        update.setThrCertFlag(reqDO.getThrCertFlag());
        update.setOrganCode(reqDO.getOrganCode());
        update.setOrganExpire(reqDO.getOrganExpire());
        update.setBusContactPerson(reqDO.getBusContactPerson());
        update.setBusContactTel(reqDO.getBusContactTel());
        update.setPubAcctInfo(reqDO.getPubAcctInfo());
        update.setLegalHoldPicKey(reqDO.getLegalHoldPicKey());
        update.setBizPlacePicKey(reqDO.getBizPlacePicKey());
        update.setSettleBankPicKey(reqDO.getSettleBankPicKey());
        update.setAcctLicensePicKey(reqDO.getAcctLicensePicKey());
        update.setPersonHeadPicKey(reqDO.getPersonHeadPicKey());
        // 敏感字段：仅当提交了真实值（非脱敏）才更新，避免重提时把明文写成 ****
        if (!isBlank(reqDO.getLegalIdNo()) && !reqDO.getLegalIdNo().contains("****")) {
            update.setLegalIdNo(reqDO.getLegalIdNo());
        }
        if (!isBlank(reqDO.getSettleAcctNo()) && !reqDO.getSettleAcctNo().contains("****")) {
            update.setSettleAcctNo(reqDO.getSettleAcctNo());
        }
        if (!isBlank(reqDO.getHolderIdNo()) && !reqDO.getHolderIdNo().contains("****")) {
            update.setHolderIdNo(reqDO.getHolderIdNo());
        }
        if (!isBlank(reqDO.getSettleIdNo()) && !reqDO.getSettleIdNo().contains("****")) {
            update.setSettleIdNo(reqDO.getSettleIdNo());
        }
        update.setPayApplyStatus(1); // 审核中
        update.setPayApplyRejectReason(null);
        shopInfoMapper.updateById(update);
        return success(true);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** 敏感串脱敏：保留前 4 后 4，中间 ****；不足 8 位整体 ****。 */
    private static String maskTail(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        if (s.length() <= 8) {
            return "****";
        }
        return s.substring(0, 4) + "****" + s.substring(s.length() - 4);
    }

}
