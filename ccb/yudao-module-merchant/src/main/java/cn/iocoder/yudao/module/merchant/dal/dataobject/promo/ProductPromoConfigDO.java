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

    // ========== 自然队列（自然推）—— 商品级优先，商户级 shop_promo_config 兜底 ==========
    /** 该商品「自然队列/自然推」开关：null=用商户级兜底；true=队首返奖；false=吞奖。
     *  仅作用于真自然用户（无推荐人）订单。 */
    private Boolean naturalPushEnabled;

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
    // ⚠️ V044 合规整改后，团队极差奖已永久禁用（CommissionServiceImpl.handleOrderPaidV8 入口 return 兜底）。
    // 下方字段保留兼容旧数据，starRatios 字段**语义重定义**为"邀请奖按推荐人星级差异化比例"（V044 复用）。
    // 长期计划：新增独立字段 directCommissionRatiosByStar 取代 starRatios 语义复用。
    /** 星级数量 (V044 后表示 VIP 等级数；不再启用团队极差) */
    private Integer starCount;
    /**
     * V044 重定义：邀请奖按"推荐人自身星级"差异化比例 JSON 数组(%)，长度 = starCount。
     * 例：[20, 22, 25, 30, 35] = 1 星 20% / 2 星 22% / ... / 5 星 35%。
     * 上限 35% 硬约束（PromoQueueServiceImpl.resolveParentStarRatio 服务端截断）。
     *
     * <p>历史语义（已废）：团队极差返奖比例（按 ancestor 星级抽水）。
     * V044 整改后该路径已禁用，字段保留兼容旧数据，但读取端 (CommissionServiceImpl)
     * 已 return 不执行。</p>
     */
    @Deprecated  // 字段名 starRatios 含义已变；新代码建议用专属字段命名（待迁移）
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
