package cn.iocoder.yudao.module.merchant.dal.dataobject.promo;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * v8 SPU 级星级奖池结算单（每次手工结算一行）。
 *
 * <p>结算时记快照（rules_snapshot），即便之后规则被改，回溯能看到当时按什么规则发的。</p>
 */
@TableName("spu_star_pool_settle_record")
@KeySequence("spu_star_pool_settle_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpuStarPoolSettleRecordDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long spuId;

    /** 结算前池余额（分） */
    private Long poolBalanceBefore;

    /** 结算后池余额（分）— 通常 = 0；某星级无人时残值留池 */
    private Long poolBalanceAfter;

    /** 实际分配总额（分）= sum(payout_item.amount) */
    private Long totalDistributed;

    /** 结算时的规则 JSON 快照 */
    private String rulesSnapshot;

    /** 抽奖随机种子（可复核），无抽奖时 = 0 */
    private Long randomSeed;

    /** 操作人 user_id（来自 SecurityFrameworkUtils.getLoginUserId）；为空表示系统触发 */
    private Long operatorId;

    /** 操作人姓名（冗余便于审计） */
    private String operatorName;

    /** 备注 */
    private String remark;

}
