package cn.iocoder.yudao.module.video.controller.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 任务元数据 partial 更新（confirmTask 完成后落 bgmStyle/voiceKey/ratio/coverUrl，
 * detail 页生成海报后落 posterUrl，最终生成完成落 videoUrl/status/failReason）。
 */
@Schema(description = "AI 视频 - 任务元数据 patch ReqVO")
@Data
public class VideoTaskMetaPatchReqVO {

    @Schema(description = "任务 id")
    @NotNull(message = "id 不能为空")
    private Long id;

    @Schema(description = "标题")
    @Size(max = 256)
    private String title;

    @Schema(description = "描述/文案")
    @Size(max = 2000)
    private String description;

    @Schema(description = "图片 URL 列表")
    private List<@Size(max = 1024) String> imageUrls;

    @Schema(description = "BGM 风格 key (street_food_yelling / cozy_explore / asmr_macro / elegant_tea / trendy_pop / emotional_story / none)")
    @Size(max = 32)
    private String bgmStyle;

    @Schema(description = "海报 URL")
    @Size(max = 512)
    private String posterUrl;

    @Schema(description = "TTS 音色 key")
    @Size(max = 32)
    private String voiceKey;

    @Schema(description = "画面比例 9:16 / 16:9 / 1:1")
    @Size(max = 8)
    private String ratio;

    @Schema(description = "封面 URL")
    @Size(max = 512)
    private String coverUrl;

    @Schema(description = "最终视频 URL")
    @Size(max = 512)
    private String videoUrl;

    @Schema(description = "时长（秒）")
    private Integer duration;

    @Schema(description = "状态：0待处理 1生成中 2已完成 3失败")
    private Integer status;

    @Schema(description = "失败原因")
    @Size(max = 512)
    private String failReason;
}
