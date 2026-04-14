package cn.iocoder.yudao.module.merchant.controller.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.math.BigDecimal;

/**
 * 用户端 - 商户入驻申请 Request VO（对应入驻表单5步）
 */
@Schema(description = "用户端 - 商户入驻申请 Request VO")
@Data
public class AppMerchantApplyReqVO {

    // ===== 第1步：基本信息 =====

    @Schema(description = "店铺名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "店铺名称不能为空")
    private String shopName;

    @Schema(description = "经营类目ID", example = "1")
    private Long categoryId;

    @Schema(description = "联系手机号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "联系手机号不能为空")
    private String mobile;

    @Schema(description = "推荐人手机号（选填）")
    private String referrerMobile;

    // ===== 第2步：上传资质 =====

    @Schema(description = "营业执照图片URL", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "营业执照不能为空")
    private String licenseUrl;

    @Schema(description = "法人身份证正面URL", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "身份证正面不能为空")
    private String idCardFront;

    @Schema(description = "法人身份证背面URL", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "身份证背面不能为空")
    private String idCardBack;

    // ===== 第3步：位置授权 =====

    @Schema(description = "经度")
    private BigDecimal longitude;

    @Schema(description = "纬度")
    private BigDecimal latitude;

    @Schema(description = "详细地址")
    private String address;

    // ===== 第4步：收款配置（可跳过） =====

    @Schema(description = "微信收款类型：0未配置 1已有商户号 2申请子商户")
    private Integer wxMchType;

    @Schema(description = "已有微信商户号（wxMchType=1时填写）")
    private String wxMchId;

}
