package cn.iocoder.yudao.module.merchant.controller.admin.vo.apply;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - 商户入驻申请审核 Request VO")
@Data
public class MerchantApplyAuditReqVO {

    @Schema(description = "申请编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "申请编号不能为空")
    private Long id;

    @Schema(description = "是否通过：true=通过 false=驳回", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "审核结果不能为空")
    private Boolean approved;

    @Schema(description = "驳回原因（approved=false 时必填）", example = "营业执照照片不清晰")
    private String rejectReason;

}
