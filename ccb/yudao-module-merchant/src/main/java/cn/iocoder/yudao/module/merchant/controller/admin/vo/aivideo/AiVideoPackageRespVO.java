package cn.iocoder.yudao.module.merchant.controller.admin.vo.aivideo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - AI 视频套餐 Response VO")
@Data
public class AiVideoPackageRespVO {

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

    @Schema(description = "排序值")
    private Integer sort;

    @Schema(description = "状态：0=上架 1=下架")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
