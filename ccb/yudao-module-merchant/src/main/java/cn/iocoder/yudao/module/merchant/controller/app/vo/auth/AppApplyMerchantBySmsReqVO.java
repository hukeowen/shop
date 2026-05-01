package cn.iocoder.yudao.module.merchant.controller.app.vo.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 「店铺名 + 手机号 + 短信验证码」一键申请商户（不要邀请码）。
 *
 * <p>流程：官网 → 商户申请页 → 填店铺名 + 手机 → 调 /send-sms-code 发码 →
 * 输入收到的码 → 提交本接口 → 自动建租户 + 店铺。</p>
 *
 * <p>申请成功后用同样手机号 + 任意密码 (≥6 位) 在商户登录页登录即可。</p>
 *
 * <p>验证码：生产 yudao.sms-code.demo-mode=false → 真发短信；
 * demo-mode=true → 全场景固定 demo-code（默认 888888，由 yaml 控制）。</p>
 */
@Schema(description = "摊小二 - 店铺名+手机号+SMS 一键申请商户")
@Data
public class AppApplyMerchantBySmsReqVO {

    @Schema(description = "店铺名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "老王烧烤摊")
    @NotBlank(message = "店铺名称不能为空")
    @Size(min = 2, max = 32, message = "店铺名长度 2-32")
    private String shopName;

    @Schema(description = "手机号", requiredMode = Schema.RequiredMode.REQUIRED, example = "13800138000")
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String mobile;

    @Schema(description = "短信验证码（当前固定 888888，待接入真 SMS 服务时改为真验码）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "888888")
    @NotBlank(message = "验证码不能为空")
    private String smsCode;
}
