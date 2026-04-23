package cn.iocoder.yudao.module.merchant.controller.admin.vo.aivideo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - AI 视频套餐分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AiVideoPackagePageReqVO extends PageParam {

    @Schema(description = "套餐名称（模糊）", example = "体验装")
    private String name;

    @Schema(description = "状态：0=上架 1=下架", example = "0")
    private Integer status;

}
