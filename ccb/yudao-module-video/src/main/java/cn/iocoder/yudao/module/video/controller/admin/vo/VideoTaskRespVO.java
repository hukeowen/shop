package cn.iocoder.yudao.module.video.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "AI视频任务 Response VO")
@Data
public class VideoTaskRespVO {

    @Schema(description = "任务编号")
    private Long id;

    @Schema(description = "商户编号")
    private Long merchantId;

    @Schema(description = "视频标题")
    private String title;

    @Schema(description = "视频描述")
    private String description;

    @Schema(description = "图片URL列表")
    private List<String> imageUrls;

    @Schema(description = "生成的视频URL")
    private String videoUrl;

    @Schema(description = "视频时长（秒）")
    private Integer duration;

    @Schema(description = "任务状态")
    private Integer status;

    @Schema(description = "失败原因")
    private String failReason;

    @Schema(description = "抖音发布状态")
    private Integer douyinPublishStatus;

    @Schema(description = "抖音视频ID")
    private String douyinItemId;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
