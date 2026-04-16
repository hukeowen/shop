package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppMerchantAuthSmsReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantApplyDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MerchantApplyMapper;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginRespVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthSmsLoginReqVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthSmsSendReqVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthSocialLoginReqVO;
import cn.iocoder.yudao.module.system.enums.sms.SmsSceneEnum;
import cn.iocoder.yudao.module.system.service.auth.AdminAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.AUTH_TENANT_ID_REQUIRED;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.AUTH_TENANT_NOT_FOUND;

/**
 * 商户小程序认证 Controller
 *
 * <p>商户管理员通过手机号短信或微信小程序码登录，获取 JWT Token 用于后续 API 调用。</p>
 * <p>登录前先调用 /resolve-tenant 获取 tenantId，后续请求携带 tenant-id Header。</p>
 */
@Tag(name = "商户小程序 - 登录认证")
@RestController
@RequestMapping("/merchant/mini/auth")
@Validated
@Slf4j
public class AppMerchantAuthController {

    @Resource
    private AdminAuthService adminAuthService;
    @Resource
    private MerchantApplyMapper merchantApplyMapper;

    /**
     * 通过手机号解析租户ID（登录前调用，小程序需存储并在 Header 中携带）
     */
    @GetMapping("/resolve-tenant")
    @Operation(summary = "通过手机号查询对应租户ID")
    @Parameter(name = "mobile", description = "商户管理员手机号", required = true)
    @PermitAll
    @TenantIgnore
    public CommonResult<Long> resolveTenantByMobile(
            @RequestParam("mobile") @NotEmpty String mobile) {
        MerchantApplyDO apply = merchantApplyMapper.selectByMobile(mobile);
        if (apply == null || apply.getTenantId() == null) {
            throw exception(AUTH_TENANT_NOT_FOUND);
        }
        return success(apply.getTenantId());
    }

    /**
     * 发送短信验证码（需先设置 tenant-id Header）
     */
    @PostMapping("/sms-send")
    @Operation(summary = "发送短信验证码")
    @Parameter(name = "mobile", description = "手机号", required = true)
    @PermitAll
    public CommonResult<Boolean> sendSmsCode(@RequestParam("mobile") @NotEmpty String mobile) {
        requireTenantId();
        AuthSmsSendReqVO sendReqVO = new AuthSmsSendReqVO();
        sendReqVO.setMobile(mobile);
        sendReqVO.setScene(SmsSceneEnum.ADMIN_MEMBER_LOGIN.getScene());
        adminAuthService.sendSmsCode(sendReqVO);
        return success(true);
    }

    /**
     * 手机号 + 短信验证码登录
     */
    @PostMapping("/sms-login")
    @Operation(summary = "手机号短信验证码登录")
    @PermitAll
    public CommonResult<AuthLoginRespVO> smsLogin(@Valid @RequestBody AppMerchantAuthSmsReqVO reqVO) {
        requireTenantId();
        AuthSmsLoginReqVO smsLoginReqVO = AuthSmsLoginReqVO.builder()
                .mobile(reqVO.getMobile())
                .code(reqVO.getCode())
                .build();
        AuthLoginRespVO loginRespVO = adminAuthService.smsLogin(smsLoginReqVO);
        log.info("[smsLogin] 商户管理员登录成功，tenantId={}, userId={}", TenantContextHolder.getTenantId(), loginRespVO.getUserId());
        return success(loginRespVO);
    }

    /**
     * 微信小程序静默登录（需先通过 wx.login 获取 code，并在 Header 中携带已知 tenantId）
     */
    @PostMapping("/wx-login")
    @Operation(summary = "微信小程序OpenID静默登录")
    @PermitAll
    public CommonResult<AuthLoginRespVO> wxLogin(@Valid @RequestBody AuthSocialLoginReqVO reqVO) {
        requireTenantId();
        AuthLoginRespVO loginRespVO = adminAuthService.socialLogin(reqVO);
        log.info("[wxLogin] 微信小程序商户登录成功，tenantId={}, userId={}", TenantContextHolder.getTenantId(), loginRespVO.getUserId());
        return success(loginRespVO);
    }

    /**
     * 刷新 Token
     */
    @PostMapping("/refresh-token")
    @Operation(summary = "刷新访问令牌")
    @Parameter(name = "refreshToken", description = "刷新令牌", required = true)
    @PermitAll
    public CommonResult<AuthLoginRespVO> refreshToken(
            @RequestParam("refreshToken") @NotEmpty String refreshToken) {
        return success(adminAuthService.refreshToken(refreshToken));
    }

    // ==================== 私有方法 ====================

    /**
     * 从请求上下文获取 tenantId（通过 tenant-id Header 注入），未设置则抛异常
     */
    private Long requireTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw exception(AUTH_TENANT_ID_REQUIRED);
        }
        return tenantId;
    }

}
