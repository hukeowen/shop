package cn.iocoder.yudao.module.merchant.dal.dataobject.promo;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * v8 SPU 级星级奖池结算明细（每中奖/均分用户一行）。
 *
 * <p>外键到 spu_star_pool_settle_record.id；同一结算单内 user_id 可能出现多次仅当同时命中多星（按设计不允许）。</p>
 */
@TableName("spu_star_pool_payout_item")
@KeySequence("spu_star_pool_payout_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpuStarPoolPayoutItemDO extends TenantBaseDO {

    @TableId
    private Long id;

    /** 结算单 id */
    private Long settleId;

    private Long spuId;

    private Long userId;

    /** 结算时该用户在该 SPU 上的星级 */
    private Integer star;

    /** EQUAL=均分 / LOTTERY=抽中 */
    private String mode;

    /** 该用户分到的推广积分（分） */
    private Long amount;

    /** shop_user_point_log.id（回查积分流水用，0=未记或失败） */
    private Long pointLedgerId;

}
