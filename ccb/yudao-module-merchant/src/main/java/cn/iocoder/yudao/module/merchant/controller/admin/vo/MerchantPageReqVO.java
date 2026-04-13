package cn.iocoder.yudao.module.merchant.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 商户分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MerchantPageReqVO extends PageParam {

    @Schema(description = "商户名称", example = "张三的店")
    private String name;

    @Schema(description = "商户状态", example = "0")
    private Integer status;

    @Schema(description = "联系电话", example = "13800138000")
    private String contactPhone;

}
