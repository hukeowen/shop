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
 * 店铺优惠券模板 DO（商户自建，按租户隔离）。
 *
 * <p>对应原型 ④ 店铺详情的 coupon-strip 横向券领取条（user-h5.html line 2598-2628）。
 * C 端用户在 shop-home 看到当前店铺的可领券；商户在 me/coupon 自建/上下架。</p>
 */
@TableName("shop_coupon")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ShopCouponDO extends TenantBaseDO {

    @TableId
    private Long id;

    /** 券名，如「满 50 减 5」「新人专享 立减 ¥3」 */
    private String name;
    /** 券面额（分） */
    private Integer discountAmount;
    /** 使用门槛（分），0 = 无门槛 */
    private Integer minAmount;
    /** 标签：NEW = 新人专享；NORMAL = 通用；空 = 通用 */
    private String tag;

    /** 发行总量，0 = 不限 */
    private Integer totalCount;
    /** 已领数量 */
    private Integer takenCount;

    /** 领取后有效天数 */
    private Integer validDays;
    /** 券生效开始时间，NULL = 立即 */
    private LocalDateTime startTime;
    /** 券失效时间，NULL = 永久 */
    private LocalDateTime endTime;

    /** 状态：0 = 上架；1 = 下架 */
    private Integer status;
}
