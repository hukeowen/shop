package cn.iocoder.yudao.module.video.controller.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 视频任务详情 + 全部分镜（前端 detail/confirm/resume 进入时一次拉全）。
 */
@Schema(description = "AI 视频 - 任务详情含分镜 RespVO")
@Data
public class VideoTaskWithScenesRespVO {

    @Schema(description = "任务 id")
    private Long id;

    @Schema(description = "商户 id")
    private Long merchantId;

    @Schema(description = "用户 id")
    private Long userId;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "原图 URL 列表（OSS 公网）")
    private List<String> imageUrls;

    @Schema(description = "封面 URL")
    private String coverUrl;

    @Schema(description = "海报 URL")
    private String posterUrl;

    @Schema(description = "BGM 风格 key")
    private String bgmStyle;

    @Schema(description = "TTS 音色 key")
    private String voiceKey;

    @Schema(description = "画面比例")
    private String ratio;

    @Schema(description = "最终视频 URL")
    private String videoUrl;

    @Schema(description = "时长（秒）")
    private Integer duration;

    @Schema(description = "状态：0待处理 1生成中 2已完成 3失败")
    private Integer status;

    @Schema(description = "失败原因")
    private String failReason;

    @Schema(description = "抖音发布状态")
    private Integer douyinPublishStatus;

    @Schema(description = "抖音视频 id")
    private String douyinItemId;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "分镜列表（按 sceneIndex 升序，可能为空）")
    private List<SceneItem> scenes;

    @Schema(description = "单幕分镜")
    @Data
    public static class SceneItem {
        @Schema(description = "分镜 id")
        private Long id;
        @Schema(description = "序号")
        private Integer sceneIndex;
        @Schema(description = "用第几张图")
        private Integer imgIdx;
        @Schema(description = "起始帧 URL")
        private String startImageUrl;
        @Schema(description = "口播台词")
        private String narration;
        @Schema(description = "视觉 prompt")
        private String visualPrompt;
        @Schema(description = "一句话亮点")
        private String imageSummary;
        @Schema(description = "时长")
        private Integer duration;
        @Schema(description = "是否端卡")
        private Boolean isEndCard;
        @Schema(description = "状态")
        private String status;
        @Schema(description = "失败原因")
        private String failReason;
        @Schema(description = "Seedance task_id")
        private String clipTaskId;
        @Schema(description = "该幕视频 URL")
        private String clipUrl;
        @Schema(description = "TTS 音频 URL")
        private String audioUrl;
    }
}
