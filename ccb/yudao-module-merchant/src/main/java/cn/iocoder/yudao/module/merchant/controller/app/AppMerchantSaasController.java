package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.saas.MerchantSubscriptionOrderDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.saas.SaasPackageConfigDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper;
import cn.iocoder.yudao.module.merchant.service.MerchantService;
import cn.iocoder.yudao.module.merchant.service.allinpay.AllinpayCashierService;
import cn.iocoder.yudao.module.merchant.service.saas.SaasSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 商户小程序 - SaaS 套餐购买 / 续费 / 服务状态查询。
 *
 * <p>支付走平台商户的通联凭据（平台向商户收钱，资金到平台账户）。</p>
 */
@Tag(name = "商户小程序 - SaaS 订阅")
@RestController
@RequestMapping("/merchant/mini/saas")
@Validated
@Slf4j
public class AppMerchantSaasController {

    /** 平台商户 tenant_id（V035 种子写死 999） */
    private static final long PLATFORM_TENANT_ID = 999L;

    @Resource
    private SaasSubscriptionService subscriptionService;
    @Resource
    private MerchantService merchantService;
    @Resource
    private AllinpayCashierService cashierService;
    @Resource
    private ShopInfoMapper shopInfoMapper;

    @GetMapping("/packages")
    @Operation(summary = "列出可购套餐（续费页用）")
    @TenantIgnore
    public CommonResult<List<SaasPackageConfigDO>> listPackages() {
        return success(subscriptionService.listEnabledPackages());
    }

    @GetMapping("/my-status")
    @Operation(summary = "我的服务状态（到期时间 / 当前级别 / 是否过期）")
    @TenantIgnore
    public CommonResult<Map<String, Object>> myStatus() {
        MerchantDO merchant = getMerchantOrThrow();
        Map<String, Object> r = new HashMap<>();
        r.put("merchantId", merchant.getId());
        r.put("isPlatform", Boolean.TRUE.equals(merchant.getIsPlatform()));
        r.put("level", subscriptionService.getEffectiveLevel(merchant));
        r.put("rawLevel", merchant.getServicePackageLevel());
        r.put("expireAt", merchant.getServiceExpireAt());
        r.put("expired", subscriptionService.isExpired(merchant));
        r.put("aiVideoQuota", merchant.getVideoQuotaRemaining());
        return success(r);
    }

    @PostMapping("/purchase")
    @Operation(summary = "购买/续费 — 建订阅订单 + 调通联 cashier 拿支付链接")
    @TenantIgnore
    public CommonResult<Map<String, Object>> purchase(
            @RequestParam("level") String level,
            HttpServletRequest httpReq) {
        MerchantDO merchant = getMerchantOrThrow();
        if (Boolean.TRUE.equals(merchant.getIsPlatform())) {
            throw ServiceExceptionUtil.exception0(400, "平台商户无需购买套餐");
        }
        // 1. 建订阅订单
        MerchantSubscriptionOrderDO order = subscriptionService.createSubscriptionOrder(merchant.getId(), level);

        // 2. 调通联 cashier — 用平台商户的通联凭据（不是发起购买的商户自己的）
        //    因为是平台收钱（SaaS 订阅），资金到平台账户
        Map<String, Object> resp = new HashMap<>();
        resp.put("orderId", order.getId());
        resp.put("reqsn", order.getTlReqsn());
        resp.put("priceFen", order.getPriceFen());
        try {
            String ua = httpReq == null ? null : httpReq.getHeader("User-Agent");
            // 用平台商户 (tenant_id=999) 的 shop_info 凭据
            ShopInfoDO platformShop = TenantUtils.executeIgnore(() -> shopInfoMapper.selectByTenantId(PLATFORM_TENANT_ID));
            if (platformShop == null) {
                log.warn("[saas/purchase] 平台商户 tenant=999 shop_info 不存在，无法拿通联支付链接");
                resp.put("cashierUrl", null);
                return success(resp);
            }
            AllinpayCashierService.TlpayCredential cred =
                    cashierService.merchantCredentialForTenant(PLATFORM_TENANT_ID);
            AllinpayCashierService.CashierForm form = cashierService.buildCashierFormWithCredential(
                    order.getTlReqsn(), order.getPriceFen().longValue(),
                    "摊小二·" + order.getLevel() + " 套餐", ua, cred);
            resp.put("cashierUrl", form == null ? null : form.getRedirectUrl());
        } catch (Exception e) {
            log.warn("[saas/purchase] orderId={} 调通联失败：{}", order.getId(), e.getMessage());
            // 不阻塞，让前端用户重试
        }
        return success(resp);
    }

    private MerchantDO getMerchantOrThrow() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            throw ServiceExceptionUtil.exception0(401, "请先登录");
        }
        MerchantDO merchant = merchantService.getMerchantByUserId(userId);
        if (merchant == null) {
            throw ServiceExceptionUtil.exception0(404, "商户不存在");
        }
        return merchant;
    }
}
