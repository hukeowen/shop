package cn.iocoder.yudao.module.merchant.controller.admin.vo.shop;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 在线支付开通申请分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ShopPayApplyPageReqVO extends PageParam {

    @Schema(description = "申请状态：0未申请 1审核中 2已开通 3已驳回", example = "1")
    private Integer status;

}
