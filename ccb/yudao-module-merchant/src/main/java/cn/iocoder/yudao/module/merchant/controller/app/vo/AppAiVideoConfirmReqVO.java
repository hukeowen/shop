package cn.iocoder.yudao.module.merchant.controller.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "商户小程序 - AI成片确认文案 Request VO")
@Data
public class AppAiVideoConfirmReqVO {

    @Schema(description = "任务ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "任务ID不能为空")
    private Long taskId;

    @Schema(description = "最终确认的文案列表（可手动修改）")
    private List<String> finalCopywriting;

    @Schema(description = "背景音乐ID")
    private Integer bgmId;

}
