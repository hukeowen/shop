package cn.iocoder.yudao.module.merchant.controller.admin.vo.aivideo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Schema(description = "管理后台 - AI 视频套餐 新增/修改 Request VO")
@Data
public class AiVideoPackageSaveReqVO {

    @Schema(description = "套餐 ID，修改时必填", example = "1024")
    private Long id;

    @Schema(description = "套餐名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "体验装 5 条")
    @NotBlank(message = "套餐名称不能为空")
    @Size(max = 64, message = "套餐名称长度不能超过 64 字")
    private String name;

    @Schema(description = "套餐描述", example = "新手特惠，含 5 条 15s 爆款视频")
    @Size(max = 255, message = "套餐描述长度不能超过 255 字")
    private String description;

    @Schema(description = "附赠视频条数", requiredMode = Schema.RequiredMode.REQUIRED, example = "5")
    @NotNull(message = "视频条数不能为空")
    @Min(value = 1, message = "视频条数必须大于 0")
    private Integer videoCount;

    @Schema(description = "售价（单位：分）", requiredMode = Schema.RequiredMode.REQUIRED, example = "3500")
    @NotNull(message = "售价不能为空")
    @Min(value = 0, message = "售价不能为负")
    private Long price;

    @Schema(description = "划线原价（单位：分）", example = "4900")
    @Min(value = 0, message = "划线原价不能为负")
    private Long originalPrice;

    @Schema(description = "排序值，大→前", example = "100")
    private Integer sort;

    @Schema(description = "状态：0=上架 1=下架", example = "0")
    private Integer status;

}
