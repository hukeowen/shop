package cn.iocoder.yudao.module.merchant.controller.admin.vo.promo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Schema(description = "管理后台 - 商户营销配置 保存请求")
@Data
public class PromoConfigSaveReqVO {

    @Schema(description = "平台星级数量", example = "5")
    @NotNull
    @Min(1)
    private Integer starLevelCount;

    @Schema(description = "每星级团队极差抽成比例(%) JSON 数组，长度=starLevelCount", example = "[1,2,3,4,5]")
    @NotNull
    private String commissionRates;

    @Schema(description = "升星门槛 JSON 数组，长度=starLevelCount", example = "[{\"directCount\":2,\"teamSales\":3}]")
    @NotNull
    private String starUpgradeRules;

    @Schema(description = "星级折扣比例 JSON 数组，索引=star（百分制 100=原价）",
            example = "[100,95,92,90,88,85]")
    private String starDiscountRates;

    @Schema(description = "推广积分→消费积分 转换比例", example = "1.00")
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal pointConversionRatio;

    @Schema(description = "推广积分提现门槛(分)", example = "10000")
    @NotNull
    @Min(0)
    private Integer withdrawThreshold;

    @Schema(description = "是否启用星级积分池")
    @NotNull
    private Boolean poolEnabled;

    @Schema(description = "入池比例(%)", example = "5.00")
    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    private BigDecimal poolRatio;

    @Schema(description = "可参与瓜分的星级 JSON 数组", example = "[3,4,5]")
    @NotNull
    private String poolEligibleStars;

    @Schema(description = "分配方式 ALL/STAR", example = "ALL")
    @NotNull
    private String poolDistributeMode;

    @Schema(description = "结算 cron 表达式", example = "0 0 0 1 * ?")
    @NotNull
    private String poolSettleCron;

    @Schema(description = "抽奖中奖占比(%)", example = "5.00")
    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    private BigDecimal poolLotteryRatio;

    @Schema(description = "cron 自动结算模式 FULL/LOTTERY", example = "FULL")
    @NotNull
    private String poolSettleMode;

}
