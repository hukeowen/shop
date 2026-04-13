package cn.iocoder.yudao.module.video.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "用户 App - 创建AI视频任务 Request VO")
@Data
public class VideoTaskCreateReqVO {

    @Schema(description = "视频标题", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "视频标题不能为空")
    private String title;

    @Schema(description = "视频描述/文案", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "视频文案不能为空")
    private String description;

    @Schema(description = "图片URL列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "至少上传一张图片")
    private List<String> imageUrls;

    @Schema(description = "背景音乐URL")
    private String bgmUrl;

}
