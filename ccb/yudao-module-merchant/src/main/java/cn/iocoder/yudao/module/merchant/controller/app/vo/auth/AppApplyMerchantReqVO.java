package cn.iocoder.yudao.module.merchant.controller.app.vo.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Schema(description = "摊小二 - 开通商户（邀请码 + 可选手机号） Req")
@Data
public class AppApplyMerchantReqVO {

    @Schema(description = "BD 邀请码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "邀请码不能为空")
    private String inviteCode;

    @Schema(description = "getPhoneNumber encryptedData（若会员尚未绑定手机号则必填）")
    private String encryptedData;

    @Schema(description = "getPhoneNumber iv（若会员尚未绑定手机号则必填）")
    private String iv;
}
