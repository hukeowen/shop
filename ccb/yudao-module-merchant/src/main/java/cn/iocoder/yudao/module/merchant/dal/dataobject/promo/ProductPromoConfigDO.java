package cn.iocoder.yudao.module.merchant.dal.dataobject.promo;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

/**
 * 商品营销配置 DO
 *
 * 每商户每商品一条，承载商品级营销规则：
 *   - 消费积分倍率
 *   - 推 N 反 1 配置（启用 / N 值 / N 个比例）
 *   - 是否参与星级积分池
 *
 * 对应 docs/design/marketing-system-v6.md 第三节「商品级配置」。
 */
@TableName("product_promo_config")
@KeySequence("product_promo_config_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductPromoConfigDO extends TenantBaseDO {

    @TableId
    private Long id;

    /** 商品 SPU ID（在租户内唯一） */
    private Long spuId;

    // ========== 消费积分 ==========
    /** 每元返多少消费积分 */
    private BigDecimal consumePointRatio;

    // ========== 推 N 反 1 ==========
    /** 是否启用推 N 反 1 */
    private Boolean tuijianEnabled;
    /** N 值（推几个） */
    private Integer tuijianN;
    /** N 个返佣比例 JSON 数组(%)，长度 = tuijianN，例：[25,25,25,25] */
    private String tuijianRatios;

    // ========== v8 直推奖（自购 / parent 首贡献的 COMPLETED 期奖比例）==========
    /** 直推/间推奖比例 (%)；buyer 完成推 N 反 1 后每件按此比例返；parent COMPLETED 期 1 件价 × 此比例 */
    private BigDecimal directRate;

    // ========== v8 团队极差奖（按商品独立配置） ==========
    /** 星级数量 (0 = 不启用团队极差奖) */
    private Integer starCount;
    /** 各星级团队极差返奖比例 JSON 数组(%)，长度 = starCount，例：[1,2,3] */
    private String starRatios;
    /** 升星规则 JSON：[{"star":1,"directCount":2,"teamSales":30000},...] (teamSales 单位：分) */
    private String starUpgradeRules;

    // ========== v8 星级奖池（每商品独立累池） ==========
    /** 星级奖池入池比例 (%)；订单 spu 行实付 × 此比例 入 spu_star_pool */
    private BigDecimal poolRatio;

    /**
     * v8 奖池分配规则 JSON：[{"star":N,"ratio":pct,"mode":"EQUAL|LOTTERY","winners":?}]。
     * sum(ratio)=100 强校验；EQUAL = 该星全员均分；LOTTERY = 抽 winners 名，winners > 实际人数时全中。
     */
    private String poolDistRules;

    // ========== 积分池 ==========
    /** 是否参与星级积分池（v6 兼容字段；v8 用 poolRatio>0 判定）*/
    private Boolean poolEnabled;

}
