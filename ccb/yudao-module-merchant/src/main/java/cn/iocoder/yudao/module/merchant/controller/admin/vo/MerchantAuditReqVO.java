package cn.iocoder.yudao.module.merchant.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - 商户审核 Request VO")
@Data
public class MerchantAuditReqVO {

    @Schema(description = "商户编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "商户编号不能为空")
    private Long id;

    @Schema(description = "是否通过", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "审核结果不能为空")
    private Boolean approved;

    @Schema(description = "拒绝原因")
    private String rejectReason;

}
