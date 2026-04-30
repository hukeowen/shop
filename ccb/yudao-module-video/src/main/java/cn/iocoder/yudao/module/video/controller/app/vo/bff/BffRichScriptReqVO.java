package cn.iocoder.yudao.module.video.controller.app.vo.bff;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Schema(description = "AI 视频 - 富脚本生成请求 VO")
@Data
public class BffRichScriptReqVO {

    @Schema(description = "店铺名（用于 LLM 推断行业）", example = "王师傅烤地瓜")
    @Size(max = 64, message = "店铺名最长 64 字")
    private String shopName;

    @Schema(description = "商户对商品/店铺的口语化描述，LLM 据此生成文案 + 选 BGM 风格")
    @NotBlank(message = "描述不能为空")
    @Size(min = 4, max = 500, message = "描述需 4-500 字")
    private String userDescription;
}
