package cn.iocoder.yudao.module.video.controller.app.vo.bff;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

@Schema(description = "AI 视频 - 多幕分镜生成请求 VO")
@Data
public class BffMultiSceneScriptReqVO {

    @Schema(description = "店铺名（用于 LLM 推断行业）")
    @Size(max = 64, message = "店铺名最长 64 字")
    private String shopName;

    @Schema(description = "商户口语化描述（4-500 字）")
    @Size(min = 0, max = 500, message = "描述最多 500 字")
    private String userDescription;

    @Schema(description = "图片公网 URL 列表（1-6 张，每张 ≤ 1024 字符）")
    @NotEmpty(message = "imageUrls 不能为空")
    @Size(min = 1, max = 6, message = "图片数量需 1-6 张")
    private List<@Size(max = 1024) String> imageUrls;

    @Schema(description = "期望幕数（一般 = imageUrls.size()，cap 1-6）")
    @Min(value = 1, message = "sceneCount 至少 1")
    @Max(value = 6, message = "sceneCount 至多 6")
    private Integer sceneCount;

    @Schema(description = "每幕时长（秒），影响 narration 字数提示")
    @Min(value = 1, message = "sceneDuration 至少 1 秒")
    @Max(value = 30, message = "sceneDuration 至多 30 秒")
    private Integer sceneDuration;
}
