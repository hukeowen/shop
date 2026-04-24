package cn.iocoder.yudao.module.merchant.controller.admin.vo.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - 在线支付开通审核 Request VO")
@Data
public class ShopPayApplyAuditReqVO {

    @Schema(description = "店铺编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "店铺编号不能为空")
    private Long shopId;

    @Schema(description = "是否通过：true=通过 false=驳回", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "审核结果不能为空")
    private Boolean approved;

    @Schema(description = "驳回原因（approved=false 时必填）")
    private String rejectReason;

}
