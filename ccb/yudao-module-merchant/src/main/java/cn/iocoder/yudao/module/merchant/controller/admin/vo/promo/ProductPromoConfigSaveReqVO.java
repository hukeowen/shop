package cn.iocoder.yudao.module.merchant.controller.admin.vo.promo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Schema(description = "管理后台 - 商品营销配置 保存请求")
@Data
public class ProductPromoConfigSaveReqVO {

    @Schema(description = "商品 SPU ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private Long spuId;

    @Schema(description = "每元返多少消费积分", example = "1.00")
    @NotNull
    @DecimalMin("0.00")
    private BigDecimal consumePointRatio;

    @Schema(description = "是否启用推 N 反 1")
    @NotNull
    private Boolean tuijianEnabled;

    @Schema(description = "N 值（推几个）", example = "4")
    @NotNull
    @Min(0)
    private Integer tuijianN;

    @Schema(description = "N 个返佣比例 JSON 数组(%)，长度 = tuijianN", example = "[25,25,25,25]")
    @NotNull
    private String tuijianRatios;

    @Schema(description = "是否参与星级积分池")
    @NotNull
    private Boolean poolEnabled;

}
