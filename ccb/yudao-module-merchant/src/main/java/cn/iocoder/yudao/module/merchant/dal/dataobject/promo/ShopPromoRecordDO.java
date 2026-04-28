package cn.iocoder.yudao.module.merchant.dal.dataobject.promo;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 推广积分流水。
 *
 * source_type ∈ { DIRECT, QUEUE, COMMISSION, POOL, CONVERT, WITHDRAW }
 *   amount > 0 = 收入；amount < 0 = 支出（CONVERT/WITHDRAW）。
 *   balance_after = 该流水落账后的用户余额（账本一致性校验用）。
 */
@TableName("shop_promo_record")
@KeySequence("shop_promo_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopPromoRecordDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long userId;

    private String sourceType;

    private Long amount;

    private Long balanceAfter;

    /** 来源 ID（订单/池子轮次/提现申请） */
    private Long sourceId;

    private String remark;

}
