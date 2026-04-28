package cn.iocoder.yudao.module.merchant.dal.dataobject.promo;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 用户在每商户下的星级 + 双积分账户。
 *
 * 每用户每商户一条；终生制（current_star 只升不降）。
 * 同时承载推广积分余额 / 消费积分余额，避免再开两张账户表。
 */
@TableName("shop_user_star")
@KeySequence("shop_user_star_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopUserStarDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long userId;

    /** 直推下级数（直接推荐人 = 自己 的人数） */
    private Integer directCount;

    /** 团队链路销售份数（自己 + 全部下级累计销售"参与推 N 反 1"商品份数） */
    private Integer teamSalesCount;

    /** 当前星级，0 = 未达任何星级 */
    private Integer currentStar;

    private LocalDateTime upgradedAt;

    /** 推广积分余额(分) */
    private Long promoPointBalance;

    /** 消费积分余额(分) */
    private Long consumePointBalance;

}
