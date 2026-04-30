package cn.iocoder.yudao.module.merchant.controller.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "商户小程序 - 首页数据看板 Response VO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppMerchantDashboardRespVO {

    // ========== 今日数据 ==========
    @Schema(description = "今日订单数")
    private Long todayOrderCount;

    @Schema(description = "今日销售额（分）")
    private Long todayOrderAmount;

    @Schema(description = "今日新增会员数")
    private Long todayNewMemberCount;

    // ========== 待处理 ==========
    @Schema(description = "待发货订单数")
    private Long pendingShipmentCount;

    @Schema(description = "待核销订单数（自提未核销）")
    private Long pendingVerifyCount;

    @Schema(description = "售后待处理数")
    private Long pendingAfterSaleCount;

    // ========== 经营概览 ==========
    @Schema(description = "在售商品数")
    private Long activeSpuCount;

    @Schema(description = "累计会员数")
    private Long totalMemberCount;

    // ========== 最近 7 天趋势 ==========
    @Schema(description = "最近 7 天日期标签 MM-DD（含今天，长度 7）")
    private List<String> trendLabels;

    @Schema(description = "最近 7 天每日订单数（与 trendLabels 一一对应）")
    private List<Long> trendOrderCounts;

    @Schema(description = "最近 7 天每日销售额（分，与 trendLabels 一一对应）")
    private List<Long> trendSalesAmount;

    // ========== 最近 30 天热销 Top3 ==========
    @Schema(description = "最近 30 天销量前 3 商品")
    private List<TopProductVO> topProducts;

    @Schema(description = "热销商品 Item")
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopProductVO {

        @Schema(description = "SPU id")
        private Long spuId;

        @Schema(description = "商品名称（订单快照里的 spu_name）")
        private String name;

        @Schema(description = "商品主图")
        private String picUrl;

        @Schema(description = "近 30 天累计销量（件）")
        private Long salesCount;

        @Schema(description = "近 30 天累计销售额（分）")
        private Long salesAmount;
    }

}
