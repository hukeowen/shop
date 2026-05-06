package cn.iocoder.yudao.module.merchant.dal.dataobject.coupon;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 用户领取的优惠券记录 DO。
 *
 * <p>tenant_id 与所领券模板一致。仅做"已领/已用/过期"状态记录，不参与结算
 * （结算应用在后续迭代）。</p>
 *
 * <p><b>UNIQUE 约束语义</b>：DB 层 {@code UNIQUE (user_id, coupon_id, deleted)}
 * 含 deleted 字段，所以：
 * <ul>
 *   <li>同一用户同一券在 deleted=b'0' 范围内仅能存一条（grab 时 UNIQUE 兜底）</li>
 *   <li>记录被软删后允许重新 INSERT 新行（业务预留：商户撤回 → 用户重新领）</li>
 * </ul>
 * 当前 grab() 接口层先做幂等查询 selectByUserIdAndCouponId，再依赖 UNIQUE 兜底
 * 极小并发概率。</p>
 */
@TableName("shop_coupon_user")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ShopCouponUserDO extends TenantBaseDO {

    @TableId
    private Long id;

    /** 券模板 ID */
    private Long couponId;
    /** 领券用户 ID */
    private Long userId;

    /** 面额（分），快照 */
    private Integer discountAmount;
    /** 门槛（分），快照 */
    private Integer minAmount;

    /** 生效时间 */
    private LocalDateTime effectiveTime;
    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 状态：0 = 未使用；1 = 已使用；2 = 已过期 */
    private Integer status;
    /** 使用时间 */
    private LocalDateTime useTime;
    /** 使用时关联订单 ID */
    private Long orderId;
}
