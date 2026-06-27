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
    @javax.annotation.Resource
    private cn.iocoder.yudao.module.merchant.service.allinpay.SaasOrderAllinpayPollingService saasPollingService;
    @Resource
    private ShopInfoMapper shopInfoMapper;
    // V042 收尾：套餐走「999 平台店商品交易订单」复用推广引擎
    @Resource
    private cn.iocoder.yudao.module.trade.service.order.TradeOrderUpdateService tradeOrderUpdateService;
    @Resource
    private cn.iocoder.yudao.module.product.service.sku.ProductSkuService productSkuService;
    @Resource
    private cn.iocoder.yudao.module.merchant.dal.mysql.saas.SaasPackageConfigMapper packageConfigMapper;
    @Resource(name = "tradeOrderAllinpayPollingService")
    private cn.iocoder.yudao.module.merchant.service.allinpay.TradeOrderAllinpayPollingService tradeOrderAllinpayPollingService;

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
        String level = subscriptionService.getEffectiveLevel(merchant);
        Map<String, Object> r = new HashMap<>();
        r.put("merchantId", merchant.getId());
        r.put("isPlatform", Boolean.TRUE.equals(merchant.getIsPlatform()));
        r.put("level", level);
        r.put("rawLevel", merchant.getServicePackageLevel());
        r.put("expireAt", merchant.getServiceExpireAt());
        r.put("expired", subscriptionService.isExpired(merchant));
        r.put("aiVideoQuota", merchant.getVideoQuotaRemaining());
        // 套餐展示名取 saas_package_config.name，保证「我的」页与续费页、平台后台一致（不再前端写死「全功能包」）
        String packageName = null;
        try {
            for (SaasPackageConfigDO c : subscriptionService.listEnabledPackages()) {
                if (c.getLevel() != null && c.getLevel().equals(level)) { packageName = c.getName(); break; }
            }
        } catch (Exception ignore) {}
        r.put("packageName", packageName);
        return success(r);
    }

    @PostMapping("/purchase")
    @Operation(summary = "购买/续费 — 建平台店(999)套餐商品「交易订单」+ 通联收银台")
    @TenantIgnore
    public CommonResult<Map<String, Object>> purchase(
            @RequestParam("level") String level,
            HttpServletRequest httpReq) {
        // V042 收尾：套餐 = 平台店(999)的商品，走「标准交易订单」而非旧订阅单。
        // 这样支付成功后自动触发 TradeOrderHandler 链：
        //   ① SaasPackageTradeOrderHandler → 给买家商户续期/升档(解锁功能)/加 AI 次数
        //   ② v8 推广引擎 → 按套餐商品的「推 3 反 1」给推荐人(A)发推广积分（A 必须已买套餐在队列里才有资格）
        MerchantDO merchant = getMerchantOrThrow();
        if (Boolean.TRUE.equals(merchant.getIsPlatform())) {
            throw ServiceExceptionUtil.exception0(400, "平台商户无需购买套餐");
        }
        final Long buyerUserId = SecurityFrameworkUtils.getLoginUserId();

        // 1. 套餐 → 平台店商品 SPU / SKU
        SaasPackageConfigDO pkg = packageConfigMapper.selectByLevel(level);
        if (pkg == null || pkg.getStatus() == null || pkg.getStatus() != 0) {
            throw ServiceExceptionUtil.exception0(404, "套餐不存在或已下架: " + level);
        }
        if (pkg.getSpuId() == null) {
            throw ServiceExceptionUtil.exception0(500, "套餐未关联平台商品，请联系平台运营");
        }
        final Long spuId = pkg.getSpuId();
        List<cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO> skus =
                TenantUtils.execute(PLATFORM_TENANT_ID, () -> productSkuService.getSkuListBySpuId(spuId));
        if (skus == null || skus.isEmpty()) {
            throw ServiceExceptionUtil.exception0(500, "套餐商品无规格，请联系平台运营");
        }
        final Long skuId = skus.get(0).getId();

        // 收件信息：套餐是虚拟商品走「自提」(无需真实地址/门店，商户无感)，name/mobile 取买家店铺
        ShopInfoDO buyerShop = TenantUtils.executeIgnore(() -> shopInfoMapper.selectByTenantId(merchant.getTenantId()));
        final String receiverName = buyerShop != null && buyerShop.getShopName() != null
                ? buyerShop.getShopName() : "套餐订阅";
        final String receiverMobile = buyerShop != null && buyerShop.getMobile() != null
                ? buyerShop.getMobile() : "13000000000";
        final String ua = httpReq == null ? null : httpReq.getHeader("User-Agent");
        final String pkgName = pkg.getName();

        // 2. 在平台店(999)租户下建交易订单 + 拿通联收银台 + 排程兜底轮询
        Map<String, Object> resp = new HashMap<>();
        TenantUtils.execute(PLATFORM_TENANT_ID, () -> {
            cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderCreateReqVO req =
                    new cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderCreateReqVO();
            cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderSettlementReqVO.Item item =
                    new cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderSettlementReqVO.Item();
            item.setSkuId(skuId);
            item.setCount(1);
            req.setItems(java.util.Collections.singletonList(item));
            req.setPointStatus(false);
            req.setDeliveryType(cn.iocoder.yudao.module.trade.enums.delivery.DeliveryTypeEnum.PICK_UP.getType());
            req.setReceiverName(receiverName);
            req.setReceiverMobile(receiverMobile);
            req.setRemark("SaaS 套餐：" + pkgName);
            cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO order =
                    tradeOrderUpdateService.createOrder(buyerUserId, req);
            resp.put("orderId", order.getId());
            resp.put("priceFen", order.getPayPrice());
            try {
                AllinpayCashierService.CashierForm form = cashierService.buildCashierFormForTrade(order.getId(), ua);
                resp.put("cashierUrl", form == null ? null : form.getRedirectUrl());
            } catch (Exception e) {
                log.warn("[saas/purchase] orderId={} 拿通联收银台失败：{}", order.getId(), e.getMessage());
            }
            try {
                tradeOrderAllinpayPollingService.schedulePolling(order.getId());
            } catch (Exception ignore) { }
            log.info("[saas/purchase] ✅ 套餐交易订单 orderId={} buyer={} spu={} level={} priceFen={}",
                    order.getId(), buyerUserId, spuId, level, order.getPayPrice());
            return null;
        });
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
