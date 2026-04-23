package cn.iocoder.yudao.module.video.controller.app.vo.bff;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * BFF - 豆包 TTS 合成 请求 VO。
 */
@Schema(description = "商户小程序 - BFF 豆包 TTS Request VO")
@Data
public class BffTtsReqVO {

    @Schema(description = "待合成文本", requiredMode = Schema.RequiredMode.REQUIRED, example = "欢迎来到摊小二")
    @NotBlank(message = "text 不能为空")
    @Size(max = 300, message = "text 长度不能超过 300")
    private String text;

    @Schema(description = "音色；留空使用默认音色",
            example = "zh_male_beijingxiaoye_emo_v2_mars_bigtts")
    @Size(max = 128, message = "voice 长度不能超过 128")
    private String voice;

}
