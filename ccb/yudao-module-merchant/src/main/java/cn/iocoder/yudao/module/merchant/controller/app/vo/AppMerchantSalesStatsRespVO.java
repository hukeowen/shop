package cn.iocoder.yudao.module.merchant.controller.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "商户小程序 - 销售统计 Response VO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppMerchantSalesStatsRespVO {

    @Schema(description = "周期标识：day / week / month / year")
    private String period;

    @Schema(description = "周期描述（如 2026-05 / 2026 / 2026-W19）")
    private String periodLabel;

    // ========== 总览大数 ==========
    @Schema(description = "销售总金额（订单按定价合计，分）")
    private Long salesAmount;

    @Schema(description = "实付总金额（v8 抵扣后客付，分）")
    private Long actualPayAmount;

    @Schema(description = "推广积分发出总额（分）")
    private Long promoIssued;

    @Schema(description = "入池累计（分）")
    private Long poolDeposit;

    @Schema(description = "退款 / 售后金额（分）")
    private Long refundAmount;

    @Schema(description = "净收入 = 实付 - 退款（分）")
    private Long netIncome;

    @Schema(description = "订单数")
    private Long orderCount;

    // ========== 资金分布 ==========
    @Schema(description = "用户应付未提余额合计（分）")
    private Long pendingBalance;

    @Schema(description = "已提现累计（分）")
    private Long withdrawnAmount;

    @Schema(description = "待处理提现申请数")
    private Long pendingWithdrawCount;

    @Schema(description = "店铺余额（分）")
    private Long shopBalance;

    // ========== 趋势：双线 ==========
    @Schema(description = "趋势日期标签（按 period 切粒度，可能 30 天 / 12 月）")
    private List<String> trendLabels;

    @Schema(description = "对应日期销售额（分）")
    private List<Long> trendSales;

    @Schema(description = "对应日期实付额（分，v8 抵扣后）")
    private List<Long> trendActualPay;

    // ========== 客户洞察 ==========
    @Schema(description = "客单价（分）")
    private Long avgOrderValue;

    @Schema(description = "复购率（%）")
    private Double repurchaseRate;

    @Schema(description = "新客订单数")
    private Long newCustomerOrders;

    @Schema(description = "老客订单数")
    private Long oldCustomerOrders;

    @Schema(description = "推荐客户占比（%）= 通过邀请链路下单的订单 / 总订单")
    private Double referralOrderRatio;

}
