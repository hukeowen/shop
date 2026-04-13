package cn.iocoder.yudao.module.video.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - AI视频任务分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class VideoTaskPageReqVO extends PageParam {

    @Schema(description = "商户编号")
    private Long merchantId;

    @Schema(description = "任务状态")
    private Integer status;

    @Schema(description = "视频标题")
    private String title;

}
