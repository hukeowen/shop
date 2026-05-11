package cn.iocoder.yudao.module.merchant.controller.admin.vo.tlpay;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - 通联支付配置保存 Request VO")
@Data
public class TlpayConfigSaveReqVO {

    @Schema(description = "shop_info id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "shop_info id 不能为空")
    private Long id;

    @Schema(description = "是否启用 0=关 1=开")
    private Boolean tlEnabled;

    @Schema(description = "通联 cusId / 商户号")
    private String tlMchId;

    @Schema(description = "通联 appId")
    private String tlAppId;

    @Schema(description = "签名算法 RSA / RSA2，默认 RSA")
    private String tlSignType;

    @Schema(description = "异步回调地址（可空 = 走全局默认）")
    private String tlNotifyUrl;

    /** 私钥 / 公钥：传 null 或空串 = 保留不动；传 "__CLEAR__" = 清空 */
    @Schema(description = "商户 RSA 私钥 PEM；空串 = 不变；__CLEAR__ = 清空")
    private String tlRsaPrivateKey;

    @Schema(description = "通联 RSA 公钥 PEM；空串 = 不变；__CLEAR__ = 清空")
    private String tlRsaPublicKey;

}
