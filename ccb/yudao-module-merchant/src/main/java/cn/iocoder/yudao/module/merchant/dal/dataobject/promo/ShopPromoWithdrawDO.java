package cn.iocoder.yudao.module.merchant.dal.dataobject.promo;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 推广积分提现申请（商户线下结算）。
 * status ∈ { PENDING, APPROVED, REJECTED, PAID }
 */
@TableName("shop_promo_withdraw")
@KeySequence("shop_promo_withdraw_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopPromoWithdrawDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long userId;

    private Long amount;

    private String status;

    private LocalDateTime applyAt;

    private LocalDateTime processedAt;

    /** 审批人 admin_user_id */
    private Long processorId;

    private String processorRemark;

}
