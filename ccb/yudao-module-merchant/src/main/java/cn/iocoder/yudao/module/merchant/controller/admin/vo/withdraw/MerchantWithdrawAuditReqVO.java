package cn.iocoder.yudao.module.merchant.controller.admin.vo.withdraw;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - 商户提现审核 Request VO")
@Data
public class MerchantWithdrawAuditReqVO {

    @Schema(description = "申请编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "申请编号不能为空")
    private Long id;

    @Schema(description = "是否通过：true=已转账 false=驳回", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "审核结果不能为空")
    private Boolean approved;

    @Schema(description = "转账凭证截图URL（approved=true 时必填）")
    private String voucherUrl;

    @Schema(description = "驳回原因（approved=false 时必填）")
    private String rejectReason;

}
