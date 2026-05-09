package cn.iocoder.yudao.module.merchant.dal.dataobject.promo;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * v8: 推 N 反 1 / 直推奖立即抵扣流水。
 *
 * <p>每个订单的 SPU 行单独一条记录，记录"产生积分 / 抵扣件数 / 实付金额"，便于对账和审计。</p>
 */
@TableName("shop_promo_deduction_record")
@KeySequence("shop_promo_deduction_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopPromoDeductionRecordDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long orderId;

    private Long orderItemId;

    private Long userId;

    private Long spuId;

    /** 单件价（分） */
    private Integer unitPrice;

    /** 订单件数 */
    private Integer totalCount;

    /** 本单 buyer 自购产生积分（分） — 抵扣前 */
    private Long producedAmount;

    /** 抵扣件数 K = floor(producedAmount / unitPrice) */
    private Integer deductCount;

    /** 实付金额（分）= (totalCount - deductCount) × unitPrice */
    private Integer actualPaid;

}
