package cn.iocoder.yudao.module.video.controller.app.vo.bff;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * BFF - 即梦AI 查询图生视频任务结果 请求 VO。
 */
@Schema(description = "商户小程序 - BFF 即梦AI 查询任务 Request VO")
@Data
public class BffJimengQueryReqVO {

    @Schema(description = "提交任务时返回的 task_id", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "c56b5ec6-9b5e-4b2e-9cf2-abcdef012345")
    @NotBlank(message = "taskId 不能为空")
    @Size(max = 128, message = "taskId 长度不能超过 128")
    private String taskId;

}
