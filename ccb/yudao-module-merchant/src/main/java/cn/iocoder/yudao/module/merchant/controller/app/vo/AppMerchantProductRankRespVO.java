package cn.iocoder.yudao.module.merchant.controller.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "商户小程序 - 商品排行行")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppMerchantProductRankRespVO {
    @Schema(description = "排名")
    private Integer rank;
    @Schema(description = "SPU id")
    private Long spuId;
    @Schema(description = "商品名")
    private String name;
    @Schema(description = "主图")
    private String picUrl;
    @Schema(description = "周期销量（件）")
    private Long salesCount;
    @Schema(description = "周期销售额（分）")
    private Long salesAmount;
    @Schema(description = "周期实付额（分）")
    private Long actualPayAmount;
}
