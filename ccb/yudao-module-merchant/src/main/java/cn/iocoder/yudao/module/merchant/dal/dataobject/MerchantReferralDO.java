package cn.iocoder.yudao.module.merchant.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 平台商户推荐裂变记录
 * 平台级表，无 tenant_id 隔离，继承 BaseDO
 */
@TableName("merchant_referral")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantReferralDO extends BaseDO {

    /** 主键 */
    private Long id;

    /** 推荐人手机号（对应某商户的联系手机） */
    private String referrerMobile;

    /** 推荐人租户ID（审核通过后关联） */
    private Long referrerTenantId;

    /** 被推荐商户的租户ID */
    private Long refereeTenantId;

    /** 被推荐商户首次付费时间 */
    private LocalDateTime paidAt;

    /** 是否已触发返利（推N达标后置为 true） */
    private Boolean rewarded;

    /** 返利触发时间 */
    private LocalDateTime rewardTime;

}
