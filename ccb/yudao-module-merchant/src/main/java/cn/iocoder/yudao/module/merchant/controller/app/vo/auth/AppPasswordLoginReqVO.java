package cn.iocoder.yudao.module.merchant.controller.app.vo.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * H5 / 演示用 — 手机号 + 密码登录。
 * 不发短信、不验真，首次输入即注册（password 走 BCrypt 落库）。
 */
@Schema(description = "摊小二 - 手机号+密码登录 Req（不验真，首次输入即注册）")
@Data
public class AppPasswordLoginReqVO {

    @Schema(description = "手机号", requiredMode = Schema.RequiredMode.REQUIRED, example = "13800138000")
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String mobile;

    @Schema(description = "密码（≥6 位）", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456")
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度 6-64")
    private String password;

}
