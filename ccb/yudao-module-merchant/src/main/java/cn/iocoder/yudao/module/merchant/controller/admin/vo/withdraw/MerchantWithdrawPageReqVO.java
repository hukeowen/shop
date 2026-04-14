package cn.iocoder.yudao.module.merchant.controller.admin.vo.withdraw;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 商户提现申请分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class MerchantWithdrawPageReqVO extends PageParam {

    @Schema(description = "审核状态：0待审核 1已转账 2驳回", example = "0")
    private Integer status;

    @Schema(description = "租户ID", example = "1")
    private Long tenantId;

}
