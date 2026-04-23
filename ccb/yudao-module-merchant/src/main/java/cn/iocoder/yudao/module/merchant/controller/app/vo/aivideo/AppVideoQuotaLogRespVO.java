package cn.iocoder.yudao.module.merchant.controller.app.vo.aivideo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "商户小程序 - AI 视频配额流水 Response VO")
@Data
public class AppVideoQuotaLogRespVO {

    @Schema(description = "流水 ID")
    private Long id;

    @Schema(description = "变动值（正=增 负=减）")
    private Integer quotaChange;

    @Schema(description = "变动后余量")
    private Integer quotaAfter;

    @Schema(description = "业务类型：1=购买套餐 2=视频生成扣减 3=生成失败回补 4=平台手动调整")
    private Integer bizType;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
