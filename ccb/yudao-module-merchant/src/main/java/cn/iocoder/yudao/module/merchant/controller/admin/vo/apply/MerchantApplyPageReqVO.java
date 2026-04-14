package cn.iocoder.yudao.module.merchant.controller.admin.vo.apply;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 商户入驻申请分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class MerchantApplyPageReqVO extends PageParam {

    @Schema(description = "店铺名称（模糊）", example = "王记烤串")
    private String shopName;

    @Schema(description = "联系手机号（模糊）", example = "138")
    private String mobile;

    @Schema(description = "审核状态：0待审核 1通过 2驳回", example = "0")
    private Integer status;

}
