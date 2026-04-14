package cn.iocoder.yudao.module.merchant.controller.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

@Schema(description = "商户小程序 - AI成片创建 Request VO")
@Data
public class AppAiVideoCreateReqVO {

    @Schema(description = "上传图片URL列表（3-9张）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "请至少上传3张图片")
    @Size(min = 3, max = 9, message = "图片数量为3-9张")
    private List<String> imageUrls;

    @Schema(description = "简短描述（最多200字）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "描述不能为空")
    @Size(max = 200, message = "描述不超过200字")
    private String userDescription;

}
