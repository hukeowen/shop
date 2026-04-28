package cn.iocoder.yudao.module.merchant.dal.dataobject.promo;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 用户推荐链关系（每商户独立）
 *
 * 一条 = 一个用户在一个商户下的"上级"绑定。`parent_user_id = 0` 表示自然用户。
 * 由 TenantBaseDO 自动注入并过滤 tenant_id；同一 user 在不同商户的 parent 互相隔离。
 */
@TableName("shop_user_referral")
@KeySequence("shop_user_referral_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopUserReferralDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long userId;

    /** 上级用户 ID；0 = 自然用户 */
    private Long parentUserId;

    private LocalDateTime boundAt;

    /** 建立绑定时关联的订单 ID（首单时绑定） */
    private Long boundOrderId;

}
