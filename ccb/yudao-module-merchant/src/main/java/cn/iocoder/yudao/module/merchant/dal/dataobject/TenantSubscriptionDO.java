package cn.iocoder.yudao.module.merchant.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 租户订阅状态 DO
 *
 * 平台级表（不继承 TenantBaseDO），记录每个商户租户的订阅状态与 AI 成片配额。
 */
@TableName("tenant_subscription")
@KeySequence("tenant_subscription_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantSubscriptionDO extends BaseDO {

    @TableId
    private Long id;

    /** 租户ID（唯一） */
    private Long tenantId;
    /**
     * 订阅状态
     * 1=试用 2=正式 3=过期 4=禁用
     */
    private Integer status;
    /** 到期时间 */
    private LocalDateTime expireTime;
    /** 剩余 AI 成片次数 */
    private Integer aiVideoQuota;
    /** 已使用 AI 成片次数 */
    private Integer aiVideoUsed;

}
