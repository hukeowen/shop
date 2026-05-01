package cn.iocoder.yudao.module.video.controller.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 单幕分镜 partial 更新（前端 runClip 路径每幕状态/产出同步）。
 * 仅覆盖非空字段，避免覆盖整段脚本。
 */
@Schema(description = "AI 视频 - 单幕状态/产出 patch ReqVO")
@Data
public class VideoScenePatchReqVO {

    @Schema(description = "所属任务 id")
    @NotNull(message = "taskId 不能为空")
    private Long taskId;

    @Schema(description = "分镜序号")
    @NotNull(message = "sceneIndex 不能为空")
    private Integer sceneIndex;

    @Schema(description = "状态")
    @Size(max = 32)
    private String status;

    @Schema(description = "即梦/Seedance 远程 task_id")
    @Size(max = 128)
    private String clipTaskId;

    @Schema(description = "该幕生成视频 URL")
    @Size(max = 1024)
    private String clipUrl;

    @Schema(description = "TTS 配音 URL")
    @Size(max = 1024)
    private String audioUrl;

    @Schema(description = "失败原因")
    @Size(max = 512)
    private String failReason;

    @Schema(description = "起始帧图片 URL（resumeTask 重算时回写）")
    @Size(max = 1024)
    private String startImageUrl;
}
