package cn.iocoder.yudao.module.merchant.controller.app.vo.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Schema(description = "摊小二 - 微信小程序登录 Req")
@Data
public class AppWxMiniLoginReqVO {

    @Schema(description = "wx.login 返回的 code", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "code 不能为空")
    private String code;
}
