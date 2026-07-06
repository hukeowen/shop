package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.merchant.service.allinpay.AllinpayCashierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * App / 小程序 - 微信小程序收银台调起。
 *
 * <p>前端拿到 {userName(原始ID), path(带签名)} 后：
 * <ul>
 *   <li>小程序：uni.navigateToMiniProgram({appId: 收银台appId, path})</li>
 *   <li>App：plus.share weixin launchMiniProgram({id: userName, path, type:0})</li>
 * </ul>
 * 用户在通联收银台小程序里用微信/支付宝付款，付完手动返回，App/小程序侧靠订单轮询落地。</p>
 */
@Tag(name = "App端 - 微信小程序收银台调起")
@RestController
@RequestMapping("/merchant/mp-pay")
@Validated
public class AppMerchantMpPayController {

    @Resource
    private AllinpayCashierService cashierService;

    @GetMapping("/cashier")
    @Operation(summary = "获取小程序收银台调起信息（原始ID + 带签名 path）")
    @Parameter(name = "tradeOrderId", description = "trade_order.id", required = true)
    @TenantIgnore
    public CommonResult<AllinpayCashierService.MpCashier> cashier(
            @RequestParam("tradeOrderId") Long tradeOrderId) {
        return success(cashierService.buildMpCashierForTrade(tradeOrderId));
    }
}
