package cn.iocoder.yudao.module.merchant.controller.admin.vo.tlpay;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 通联支付配置 Response VO（私钥脱敏）")
@Data
public class TlpayConfigRespVO {

    @Schema(description = "shop_info id")
    private Long id;

    @Schema(description = "店铺租户 ID")
    private Long tenantId;

    @Schema(description = "店铺名")
    private String shopName;

    @Schema(description = "是否启用")
    private Boolean tlEnabled;

    @Schema(description = "通联 cusId / 商户号")
    private String tlMchId;

    @Schema(description = "通联 appId")
    private String tlAppId;

    @Schema(description = "签名算法 RSA / RSA2")
    private String tlSignType;

    @Schema(description = "异步回调地址")
    private String tlNotifyUrl;

    @Schema(description = "商户私钥是否已配置（true=已配；不返明文）")
    private Boolean privateKeyConfigured;

    @Schema(description = "通联公钥是否已配置")
    private Boolean publicKeyConfigured;

}
