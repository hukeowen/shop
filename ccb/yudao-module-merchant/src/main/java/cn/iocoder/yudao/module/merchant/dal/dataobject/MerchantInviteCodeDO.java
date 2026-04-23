package cn.iocoder.yudao.module.merchant.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * BD 商户邀请码 DO
 *
 * <p>一个 BD 地推人员可以拥有一组邀请码，分发给线下商户使用；
 * 商户注册时必须填写有效邀请码，才能开通商户身份。</p>
 */
@TableName("merchant_invite_code")
@KeySequence("merchant_invite_code_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MerchantInviteCodeDO extends TenantBaseDO {

    /**
     * 主键
     */
    private Long id;

    /**
     * 邀请码（6-12 位，全局唯一）
     */
    private String code;

    /**
     * 归属 BD 用户 ID（平台员工）
     */
    private Long operatorUserId;

    /**
     * 最大使用次数，-1 无限
     */
    private Integer usageLimit;

    /**
     * 已使用次数
     */
    private Integer usedCount;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 备注
     */
    private String remark;
}
