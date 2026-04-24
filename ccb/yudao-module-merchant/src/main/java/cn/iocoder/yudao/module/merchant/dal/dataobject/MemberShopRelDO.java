package cn.iocoder.yudao.module.merchant.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会员×商户关系（余额/积分/推荐人）
 *
 * 平台级表，不继承 TenantBaseDO，tenant_id 作为普通字段标识归属商户。
 * 跨租户读写需配合 @TenantIgnore。
 */
@TableName("member_shop_rel")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberShopRelDO extends BaseDO {

    @TableId
    private Long id;

    /** 会员用户ID */
    private Long userId;

    /** 商户租户ID（即 shop_info.tenant_id） */
    private Long tenantId;

    /** 在该店铺的余额（分） */
    private Integer balance;

    /** 在该店铺的积分 */
    private Integer points;

    /** 推荐人用户ID（一级上线） */
    private Long referrerUserId;

    /** 首次进店时间 */
    private LocalDateTime firstVisitAt;

    /** 最近进店时间 */
    private LocalDateTime lastVisitAt;

}
