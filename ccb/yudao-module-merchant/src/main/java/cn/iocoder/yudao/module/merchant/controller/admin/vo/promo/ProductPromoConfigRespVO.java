package cn.iocoder.yudao.module.merchant.controller.admin.vo.promo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 商品营销配置 响应")
@Data
public class ProductPromoConfigRespVO {

    @Schema(description = "商品 SPU ID")
    private Long spuId;

    @Schema(description = "每元返多少消费积分")
    private BigDecimal consumePointRatio;

    @Schema(description = "是否启用推 N 反 1")
    private Boolean tuijianEnabled;

    @Schema(description = "N 值（推几个）")
    private Integer tuijianN;

    @Schema(description = "N 个返佣比例 JSON 数组(%)")
    private String tuijianRatios;

    @Schema(description = "是否参与星级积分池")
    private Boolean poolEnabled;

}
