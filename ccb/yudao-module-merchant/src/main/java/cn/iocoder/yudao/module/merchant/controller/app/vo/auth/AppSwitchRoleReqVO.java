package cn.iocoder.yudao.module.merchant.controller.app.vo.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Schema(description = "摊小二 - 切换角色 Req")
@Data
public class AppSwitchRoleReqVO {

    @Schema(description = "目标角色：merchant 或 member", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "role 不能为空")
    @Pattern(regexp = "merchant|member", message = "role 只能是 merchant 或 member")
    private String role;
}
