package cn.iocoder.yudao.module.merchant.controller.app.vo.aivideo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "商户小程序 - AI 视频配额 Response VO")
@Data
public class AppVideoQuotaRespVO {

    @Schema(description = "剩余视频条数")
    private Integer remaining;

    @Schema(description = "最近更新时间")
    private LocalDateTime updateTime;

}
