package cn.iocoder.yudao.module.merchant.controller.app.vo.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Schema(description = "摊小二 - 绑定手机号 Req")
@Data
public class AppBindPhoneReqVO {

    @Schema(description = "getPhoneNumber 返回的 encryptedData", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "encryptedData 不能为空")
    private String encryptedData;

    @Schema(description = "getPhoneNumber 返回的 iv", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "iv 不能为空")
    private String iv;
}
