package cn.iocoder.yudao.module.video.controller.app.vo.bff;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * BFF - 即梦AI 提交图生视频任务 请求 VO。
 */
@Schema(description = "商户小程序 - BFF 即梦AI 提交任务 Request VO")
@Data
public class BffJimengSubmitReqVO {

    @Schema(description = "首帧图片 URL", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "https://cdn.example.com/first.jpg")
    @NotBlank(message = "imageUrl 不能为空")
    @Size(max = 1000, message = "imageUrl 长度不能超过 1000")
    private String imageUrl;

    @Schema(description = "运镜 / 动作提示词", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "镜头缓慢推近，人物微笑")
    @NotBlank(message = "prompt 不能为空")
    @Size(max = 500, message = "prompt 长度不能超过 500")
    private String prompt;

    @Schema(description = "帧数（仅支持 121 或 241）", requiredMode = Schema.RequiredMode.REQUIRED, example = "121")
    @NotNull(message = "frames 不能为空")
    private Integer frames;

    @Schema(description = "随机种子，-1 表示随机", example = "-1")
    private Integer seed;

}
