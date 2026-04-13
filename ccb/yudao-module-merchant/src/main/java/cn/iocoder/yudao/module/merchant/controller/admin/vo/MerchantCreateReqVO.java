package cn.iocoder.yudao.module.merchant.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Schema(description = "管理后台 - 商户入驻 Request VO")
@Data
public class MerchantCreateReqVO {

    @Schema(description = "商户名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三的店")
    @NotBlank(message = "商户名称不能为空")
    private String name;

    @Schema(description = "商户logo")
    private String logo;

    @Schema(description = "联系人", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "联系人不能为空")
    private String contactName;

    @Schema(description = "联系电话", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "联系电话不能为空")
    private String contactPhone;

    @Schema(description = "营业执照号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "营业执照号不能为空")
    private String licenseNo;

    @Schema(description = "营业执照图片URL", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "营业执照图片不能为空")
    private String licenseUrl;

    @Schema(description = "法人姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "法人姓名不能为空")
    private String legalPersonName;

    @Schema(description = "法人身份证号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "法人身份证号不能为空")
    private String legalPersonIdCard;

    @Schema(description = "法人身份证正面图片URL", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "法人身份证正面图片不能为空")
    private String legalPersonIdCardFrontUrl;

    @Schema(description = "法人身份证反面图片URL", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "法人身份证反面图片不能为空")
    private String legalPersonIdCardBackUrl;

    @Schema(description = "结算银行账户名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "结算银行账户名不能为空")
    private String bankAccountName;

    @Schema(description = "结算银行账号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "结算银行账号不能为空")
    private String bankAccountNo;

    @Schema(description = "开户行", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "开户行不能为空")
    private String bankName;

    @Schema(description = "经营类目", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "经营类目不能为空")
    private String businessCategory;

}
