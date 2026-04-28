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

    // ========== 积分池 ==========
    /** 是否参与星级积分池 */
    private Boolean poolEnabled;

}
