package cn.iocoder.yudao.module.merchant.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.Size;

@Schema(description = "管理后台 - 创建邀请码 Req")
@Data
public class MerchantInviteCodeCreateReqVO {

    @Schema(description = "最大使用次数，-1 无限", example = "100")
    @Min(value = -1, message = "usageLimit 必须 >= -1")
    private Integer usageLimit;

    @Schema(description = "备注")
    @Size(max = 255)
    private String remark;
}
