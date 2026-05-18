package cn.iocoder.yudao.module.merchant.dal.dataobject.saas;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 商户开店分享码：商户邀请新商户入驻。
 *
 * <p>商户 A 在「我的-分享开店码」生成 code → A 分享 URL `?invite={code}` →
 * 新人 B 注册入驻时解析 code → 写 shop_user_referral(B_user, parent=A_user)
 * → B 之后在平台店铺买套餐时，v8 引擎沿 referral 链给 A 发奖。</p>
 *
 * <p>与 {@code merchant_invite_code}（BD 地推人员邀请码）区分：
 * 这张表是「商户邀请商户」语义，是 v8 推广引擎入口。</p>
 */
@TableName("merchant_invite_share_code")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantInviteShareCodeDO extends TenantBaseDO {

    @TableId
    private Long id;

    /** 邀请人商户的 member_user.id（v8 推广引擎 parent_user_id） */
    private Long referrerUserId;

    /** 邀请人商户的 tenant_id */
    private Long referrerTenantId;

    /** 分享码（6-8 位，全局唯一） */
    private String code;

    /** 已使用次数（成功注册的商户数） */
    private Integer usedCount;

    /** 是否启用 */
    private Boolean enabled;

    /** 备注 */
    private String remark;

}
