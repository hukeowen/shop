package cn.iocoder.yudao.module.merchant.controller.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

}
