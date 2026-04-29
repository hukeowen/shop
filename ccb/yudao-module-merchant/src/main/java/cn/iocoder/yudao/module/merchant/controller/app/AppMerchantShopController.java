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
        update.setMobile(updateDO.getMobile());
        update.setLongitude(updateDO.getLongitude());
        update.setLatitude(updateDO.getLatitude());
        update.setAddress(updateDO.getAddress());
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
        resp.setTlMchId(shop.getTlMchId());
        resp.setPayApplyStatus(shop.getPayApplyStatus());
        resp.setOnlinePayEnabled(shop.getOnlinePayEnabled());
        resp.setPayApplyRejectReason(shop.getPayApplyRejectReason());
        // 进件 KYC 资质回显：只回 TOS key，前端拿到后调 /oss/sign 现签 1h 预签名 URL 显示
        resp.setIdCardFrontKey(shop.getIdCardFrontKey());
        resp.setIdCardBackKey(shop.getIdCardBackKey());
        resp.setBusinessLicenseKey(shop.getBusinessLicenseKey());
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
        // 必须是自己店铺持有的 3 个 key 之一，否则越权
        if (!key.equals(shop.getIdCardFrontKey())
                && !key.equals(shop.getIdCardBackKey())
                && !key.equals(shop.getBusinessLicenseKey())) {
            throw exception0(1_020_005_004, "key 不属于当前店铺");
        }
        java.util.Map<String, String> resp = new java.util.HashMap<>();
        resp.put("url", kycSignService.sign(key, ttl));
        return success(resp);
    }

    @PostMapping("/pay-apply")
    @Operation(summary = "提交在线支付开通申请（KYC 资质：身份证正反 + 营业执照）")
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
        // 必填校验：3 张资质照（前端走 acl='private' 上传，提交的是 TOS key）
        if (reqDO.getIdCardFrontKey() == null || reqDO.getIdCardFrontKey().isEmpty()
                || reqDO.getIdCardBackKey() == null || reqDO.getIdCardBackKey().isEmpty()
                || reqDO.getBusinessLicenseKey() == null || reqDO.getBusinessLicenseKey().isEmpty()) {
            throw exception0(1_020_005_003, "请上传身份证正反面与营业执照");
        }
        ShopInfoDO update = new ShopInfoDO();
        update.setId(existing.getId());
        update.setIdCardFrontKey(reqDO.getIdCardFrontKey());
        update.setIdCardBackKey(reqDO.getIdCardBackKey());
        update.setBusinessLicenseKey(reqDO.getBusinessLicenseKey());
        update.setPayApplyStatus(1); // 审核中
        update.setPayApplyRejectReason(null);
        shopInfoMapper.updateById(update);
        return success(true);
    }

}
