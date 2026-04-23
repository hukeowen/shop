package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.aivideo.AiVideoQuotaLogPageReqVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.aivideo.AppAiVideoPackageRespVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.aivideo.AppMerchantPackagePurchaseReqVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.aivideo.AppMerchantPackagePurchaseRespVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.aivideo.AppVideoQuotaLogRespVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.aivideo.AppVideoQuotaRespVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.AiVideoPackageDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantPackageOrderDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantVideoQuotaLogDO;
import cn.iocoder.yudao.module.merchant.service.AiVideoPackageService;
import cn.iocoder.yudao.module.merchant.service.MerchantPackageOrderService;
import cn.iocoder.yudao.module.merchant.service.MerchantService;
import cn.iocoder.yudao.module.merchant.service.MerchantVideoQuotaLogService;
import cn.iocoder.yudao.module.pay.api.notify.dto.PayOrderNotifyReqDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import javax.validation.Valid;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.servlet.ServletUtils.getClientIP;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.AI_VIDEO_MERCHANT_NOT_FOUND;

/**
 * 商户小程序 - AI 视频配额 / 套餐列表 / 流水 / 购买套餐（Phase 0.3.2 + 0.3.3）。
 *
 * <p>所有 {@code /me}、{@code /packages}、{@code /logs}、{@code /packages/&#123;id&#125;/purchase}
 * 端点以 JWT 登录态为商户边界——当前登录用户对应的 merchant。
 * {@code /pay-callback} 是 {@code @PermitAll} 接口，仅供 yudao-module-pay 的
 * {@link cn.iocoder.yudao.module.pay.service.notify.PayNotifyService} 在支付成功后回调。</p>
 *
 * <p>pay 回调安全性：
 * <ul>
 *   <li>pay 模块只会 POST 到管理员在 {@code PayAppDO.orderNotifyUrl} 配置的 URL；
 *       伪造者需要知道该 URL 且能伪造 {@code PayOrderNotifyReqDTO}——但 Service 会
 *       再用 {@code PayOrderApi.getOrder} 校验 payOrderId 真实已支付</li>
 *   <li>校验失败（状态非 SUCCESS / 商户订单 ID 不对齐）直接抛异常，
 *       由 pay 模块的重试机制继续重试（共 5 次退避）</li>
 * </ul>
 * </p>
 */
@Tag(name = "商户小程序 - AI 视频配额")
@RestController
@RequestMapping("/merchant/mini/video-quota")
@Validated
@Slf4j
public class AppMerchantVideoQuotaController {

    @Resource
    private MerchantService merchantService;

    @Resource
    private AiVideoPackageService packageService;

    @Resource
    private MerchantVideoQuotaLogService quotaLogService;

    @Resource
    private MerchantPackageOrderService packageOrderService;

    @GetMapping("/me")
    @Operation(summary = "查询当前商户剩余视频配额")
    public CommonResult<AppVideoQuotaRespVO> me() {
        MerchantDO merchant = getMerchantOrThrow();
        AppVideoQuotaRespVO vo = new AppVideoQuotaRespVO();
        vo.setRemaining(merchant.getVideoQuotaRemaining() == null ? 0 : merchant.getVideoQuotaRemaining());
        vo.setUpdateTime(merchant.getUpdateTime());
        return success(vo);
    }

    @GetMapping("/packages")
    @Operation(summary = "查询在架 AI 视频套餐列表")
    public CommonResult<List<AppAiVideoPackageRespVO>> listPackages() {
        // 先校验商户身份（避免匿名扫库）
        getMerchantOrThrow();
        List<AiVideoPackageDO> list = packageService.listEnabledPackages();
        return success(BeanUtils.toBean(list, AppAiVideoPackageRespVO.class));
    }

    @GetMapping("/logs")
    @Operation(summary = "查询当前商户的视频配额流水")
    public CommonResult<PageResult<AppVideoQuotaLogRespVO>> logs(@Valid AiVideoQuotaLogPageReqVO reqVO) {
        MerchantDO merchant = getMerchantOrThrow();
        // 强制以当前商户 id 为过滤键，忽略 reqVO.merchantId（防越权）
        PageResult<MerchantVideoQuotaLogDO> page =
                quotaLogService.getLogPageByMerchant(merchant.getId(), reqVO.getBizType(), reqVO);
        return success(BeanUtils.toBean(page, AppVideoQuotaLogRespVO.class));
    }

    // ========== Phase 0.3.3：购买套餐 ==========

    @PostMapping("/packages/{packageId}/purchase")
    @Operation(summary = "购买 AI 视频套餐 - 创建业务订单 + 支付单，返回 payOrderId 供前端提交支付")
    @Parameter(name = "packageId", description = "套餐 ID", required = true, example = "1")
    public CommonResult<AppMerchantPackagePurchaseRespVO> purchase(
            @PathVariable("packageId") Long packageId,
            @RequestBody @Valid AppMerchantPackagePurchaseReqVO reqVO) {
        MerchantDO merchant = getMerchantOrThrow();
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        AppMerchantPackagePurchaseRespVO resp = packageOrderService.createOrder(
                merchant.getId(), loginUserId, packageId, reqVO.getChannelCode(), getClientIP());
        return success(resp);
    }

    /**
     * 支付成功回调 - 由 yudao-module-pay 的 PayNotifyService 在收到支付渠道成功回调后，
     * 按 {@code PayAppDO.orderNotifyUrl}（管理员在 admin-api/pay/app 配置）HTTP POST 到这里。
     *
     * <p>本端点需要管理员把 PayApp 的 orderNotifyUrl 配成：
     * {@code https://<外网地址>/app-api/merchant/mini/video-quota/pay-callback}
     * （和本 Controller 的 RequestMapping 路径一致）。</p>
     *
     * <p>安全边界：{@link PermitAll}。pay 模块的 notifyUrl 是私密配置，调用方需掌握
     * 业务订单号（merchantOrderId）+ payOrderId 配对才能成功；Service 会二次校验
     * payOrderId 对应的 PayOrderDO.status 是否真的是 SUCCESS——防伪造。</p>
     */
    @PostMapping("/pay-callback")
    @Operation(summary = "支付成功回调 - 由 yudao-module-pay 的 PayNotifyService 调用")
    @PermitAll
    public CommonResult<Boolean> onPayCallback(@RequestBody PayOrderNotifyReqDTO notify) {
        log.info("[onPayCallback] 收到支付回调 merchantOrderId={} payOrderId={}",
                notify.getMerchantOrderId(), notify.getPayOrderId());
        Long orderId;
        try {
            orderId = Long.valueOf(notify.getMerchantOrderId());
        } catch (NumberFormatException e) {
            log.warn("[onPayCallback] 非法 merchantOrderId，非套餐订单格式，忽略 {}", notify.getMerchantOrderId());
            return success(true); // 返回成功让 pay 模块停止重试（这本来就不是我们的单子）
        }

        // 不是套餐订单 → 返回成功停止重试（同一个 notifyUrl 理论只对应套餐应用，
        // 但为健壮兜底）
        MerchantPackageOrderDO order = packageOrderService.getByPayOrderId(notify.getPayOrderId());
        if (order == null) {
            log.warn("[onPayCallback] 未找到对应套餐订单 payOrderId={}，忽略", notify.getPayOrderId());
            return success(true);
        }
        // 对齐校验：merchantOrderId 必须等于本地订单 id
        if (!order.getId().equals(orderId)) {
            log.error("[onPayCallback] merchantOrderId {} 与 payOrderId {} 对应订单 {} 不一致，拒绝处理",
                    orderId, notify.getPayOrderId(), order.getId());
            // 返回失败让 pay 模块重试——也许 DB 正在同步；持续失败 pay 模块自己会转为 FAILURE
            return CommonResult.error(500, "订单不匹配");
        }

        // 幂等 + 加配额 + 更新状态
        packageOrderService.markPaid(order.getId(), null);
        return success(true);
    }

    // ========== helpers ==========

    private MerchantDO getMerchantOrThrow() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        MerchantDO merchant = userId == null ? null : merchantService.getMerchantByUserId(userId);
        if (merchant == null) {
            throw exception(AI_VIDEO_MERCHANT_NOT_FOUND);
        }
        return merchant;
    }

}
