package cn.iocoder.yudao.module.merchant.dal.dataobject.promo;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

/**
 * 商户营销配置 DO
 *
 * 每商户一条（tenant_id 唯一）。tenant_id 由 TenantBaseDO 自动注入与过滤。
 *
 * 对应 docs/design/marketing-system-v6.md 第二节「商户级配置」。
 */
@TableName("shop_promo_config")
@KeySequence("shop_promo_config_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromoConfigDO extends TenantBaseDO {

    @TableId
    private Long id;

    // ========== 平台星级 ==========
    /** 平台星级数量，默认 5 */
    private Integer starLevelCount;
    /** 每星级团队极差抽成比例(%) JSON 数组，与 starLevelCount 同长，例：[1,2,3,4,5] */
    private String commissionRates;
    /** 升星门槛 JSON 数组，每星一组：[{"directCount":2,"teamSales":3}, ...] */
    private String starUpgradeRules;
    /** 星级折扣比例 JSON 数组（百分制，100=原价），索引 = star。仅做文案展示。
     *  例：[100, 95, 92, 90, 88, 85] = 0 星 不打折 / 1 星 9.5 折 / ... 原型 ④ 用。 */
    private String starDiscountRates;

    /** 满减门槛（分），NULL=不启用。原型 ④ 底部「满 30 立减 5」 */
    private Integer fullCutThreshold;
    /** 减免金额（分），与 fullCutThreshold 同时存在才有效 */
    private Integer fullCutAmount;

    // ========== v7 推 N 反 1 ==========
    /** 间推百分比（如 10 = 10%），完成推 N 反 1 后自购 / 下级首单的返奖比例 */
    private BigDecimal directCommissionRatio;
    /** 自然推开关：仅作用于真自然用户（无 parent）订单。
     *  OFF（默认）= 吞奖；ON = 保留旧 A/B 层队列三机制（自然推/插队/自然出队）。 */
    private Boolean naturalPushEnabled;

    // ========== 推广积分 提现 / 转换 ==========
    /** 推广积分→消费积分 转换比例（默认 1:1） */
    private BigDecimal pointConversionRatio;
    /** 推广积分提现门槛(分，默认 10000=100元) */
    private Integer withdrawThreshold;

    // ========== 星级积分池 ==========
    /** 是否启用星级积分池 */
    private Boolean poolEnabled;
    /** 入池比例(%) */
    private BigDecimal poolRatio;
    /** 可参与瓜分的星级 JSON 数组，例：[1,2,3,4,5] */
    private String poolEligibleStars;
    /** 分配方式 ALL=全员均分 STAR=按星级均分 */
    private String poolDistributeMode;
    /** 结算 cron 表达式 */
    private String poolSettleCron;
    /** 抽奖中奖占比(%) */
    private BigDecimal poolLotteryRatio;
    /** cron 自动结算模式：FULL / LOTTERY；商户后台手动触发不受此限制 */
    private String poolSettleMode;

}
