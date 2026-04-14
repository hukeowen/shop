package cn.iocoder.yudao.module.merchant.controller.admin.vo.apply;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 租户订阅分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class TenantSubscriptionPageReqVO extends PageParam {

    @Schema(description = "订阅状态：1试用 2正式 3过期 4禁用", example = "1")
    private Integer status;

    @Schema(description = "租户ID", example = "1")
    private Long tenantId;

}
