package cn.iocoder.yudao.module.merchant.dal.dataobject.promo;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 星级积分池余额（每商户一条）。cron 结算后清零，下个周期重新累积。
 */
@TableName("shop_promo_pool")
@KeySequence("shop_promo_pool_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopPromoPoolDO extends TenantBaseDO {

    @TableId
    private Long id;

    /** 当前池余额(分) */
    private Long balance;

    private LocalDateTime lastSettledAt;

}
