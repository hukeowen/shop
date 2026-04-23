package cn.iocoder.yudao.module.merchant.controller.app.vo.aivideo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "商户小程序 - AI 视频套餐 Response VO")
@Data
public class AppAiVideoPackageRespVO {

    @Schema(description = "套餐 ID")
    private Long id;

    @Schema(description = "套餐名称")
    private String name;

    @Schema(description = "套餐描述")
    private String description;

    @Schema(description = "附赠视频条数")
    private Integer videoCount;

    @Schema(description = "售价（单位：分）")
    private Long price;

    @Schema(description = "划线原价（单位：分）")
    private Long originalPrice;

}
