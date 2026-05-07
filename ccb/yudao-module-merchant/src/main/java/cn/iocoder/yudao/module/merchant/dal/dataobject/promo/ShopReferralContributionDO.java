package cn.iocoder.yudao.module.merchant.dal.dataobject.promo;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * v7 推 N 反 1：下级首贡献记录。
 *
 * <p>v7 核心约束：每个 (parent_user_id, child_user_id, spu_id) 仅存在 1 条。
 * 下级 child 后续在该 spu 的订单永远不再触发 parent 奖励 —— 通过 DB UNIQUE 强保证。</p>
 *
 * <p>触发场景：</p>
 * <ul>
 *   <li>parent 状态 = IN_PROGRESS：parent 拿「单件实付价 × (1/N)」+ parent.cumulated++</li>
 *   <li>parent 状态 = COMPLETED：parent 拿「订单中该商品行实付总额 × directCommissionRatio%」</li>
 * </ul>
 */
@TableName("shop_referral_contribution")
@KeySequence("shop_referral_contribution_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopReferralContributionDO extends TenantBaseDO {

    @TableId
    private Long id;

    /** 上级用户 ID */
    private Long parentUserId;

    /** 下级用户 ID */
    private Long childUserId;

    /** 商品 SPU ID */
    private Long spuId;

    /** 触发时 parent 在该商品的状态快照：IN_PROGRESS / COMPLETED */
    private String parentStateAt;

    /** 本次给 parent 的推广积分（分） */
    private Long awardAmount;

    /** 触发首贡献的订单 ID */
    private Long sourceOrderId;
}
