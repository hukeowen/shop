package cn.iocoder.yudao.module.video.controller.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 批量保存视频分镜（前端 confirmTask / regenerateScript 落库用）。
 *
 * <p>UPSERT 语义：列表里每个 scene 按 (taskId, sceneIndex) 唯一定位，
 * 已存在则 update 不存在则 insert。列表里没出现的 sceneIndex 不会被删。</p>
 */
@Schema(description = "AI 视频 - 批量保存分镜 ReqVO")
@Data
public class VideoSceneSaveReqVO {

    @Schema(description = "所属任务 id", example = "12345")
    @NotNull(message = "taskId 不能为空")
    private Long taskId;

    @Schema(description = "分镜列表（cap 1-3 与前端图片上限一致）")
    @NotEmpty(message = "scenes 不能为空")
    @Size(min = 1, max = 3, message = "分镜数量需 1-3")
    @Valid
    private List<SceneItem> scenes;

    @Schema(description = "单幕分镜")
    @Data
    public static class SceneItem {

        @Schema(description = "分镜序号（0 起，按播放顺序）")
        @NotNull(message = "sceneIndex 不能为空")
        private Integer sceneIndex;

        @Schema(description = "用第几张图（0 起）")
        private Integer imgIdx;

        @Schema(description = "起始帧图片 URL")
        @Size(max = 1024)
        private String startImageUrl;

        @Schema(description = "口播台词")
        @Size(max = 255)
        private String narration;

        @Schema(description = "Seedance 视觉 prompt（英文）")
        @Size(max = 1024)
        private String visualPrompt;

        @Schema(description = "一句话亮点（中文）")
        @Size(max = 255)
        private String imageSummary;

        @Schema(description = "该幕时长（秒）")
        private Integer duration;

        @Schema(description = "是否端卡")
        private Boolean isEndCard;

        @Schema(description = "状态：pending / video_creating / video_running / audio_muxing / endcard_building / ready / video_failed")
        @Size(max = 32)
        private String status;
    }
}
