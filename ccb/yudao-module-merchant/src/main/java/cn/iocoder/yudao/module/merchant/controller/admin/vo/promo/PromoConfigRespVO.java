package cn.iocoder.yudao.module.merchant.controller.admin.vo.promo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 商户营销配置 响应")
@Data
public class PromoConfigRespVO {

    @Schema(description = "记录 ID（首次未保存时为空）")
    private Long id;

    @Schema(description = "平台星级数量")
    private Integer starLevelCount;

    @Schema(description = "每星级团队极差抽成比例(%) JSON 数组")
    private String commissionRates;

    @Schema(description = "升星门槛 JSON 数组")
    private String starUpgradeRules;

    @Schema(description = "推广积分→消费积分 转换比例")
    private BigDecimal pointConversionRatio;

    @Schema(description = "推广积分提现门槛(分)")
    private Integer withdrawThreshold;

    @Schema(description = "是否启用星级积分池")
    private Boolean poolEnabled;

    @Schema(description = "入池比例(%)")
    private BigDecimal poolRatio;

    @Schema(description = "可参与瓜分的星级 JSON 数组")
    private String poolEligibleStars;

    @Schema(description = "分配方式 ALL/STAR")
    private String poolDistributeMode;

    @Schema(description = "结算 cron 表达式")
    private String poolSettleCron;

    @Schema(description = "抽奖中奖占比(%)")
    private BigDecimal poolLotteryRatio;

    @Schema(description = "cron 自动结算模式 FULL/LOTTERY")
    private String poolSettleMode;

}
