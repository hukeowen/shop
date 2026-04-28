package cn.iocoder.yudao.module.merchant.dal.dataobject.promo;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 星级积分池结算批次（每轮一条）。
 *
 * mode = LOTTERY (抽奖) / FULL (均分)
 * distribute_mode = ALL (全员均分) / STAR (按星级均分)
 * winners = JSON 数组 [{"userId":1,"star":3,"amount":100}, ...]
 */
@TableName("shop_promo_pool_round")
@KeySequence("shop_promo_pool_round_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopPromoPoolRoundDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long totalAmount;

    private String mode;

    private String distributeMode;

    private Integer participantCount;

    private Integer winnerCount;

    private String winners;

    private LocalDateTime settledAt;

}
