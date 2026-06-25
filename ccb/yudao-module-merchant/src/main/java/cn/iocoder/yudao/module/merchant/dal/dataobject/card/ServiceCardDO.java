package cn.iocoder.yudao.module.merchant.dal.dataobject.card;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 用户持有的服务卡实例（付款后按 {@link ServiceCardDefDO} 发放）。
 *
 * <p>核销可用判定（统一覆盖「不限次」「限次」「谁先到」）：
 * {@code now < expireTime && (maxCount == null || usedCount < maxCount)}。</p>
 *
 * <p>每次购买独立开卡：同一用户多次购买同一服务包 → 多条独立卡实例，各自有效期 / 次数互不干扰。</p>
 */
@TableName("shop_service_card")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceCardDO extends TenantBaseDO {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_USED_UP = "USED_UP";
    public static final String STATUS_EXPIRED = "EXPIRED";

    @TableId
    private Long id;

    /** 卡定义 ID */
    private Long defId;

    /** 商品 SPU ID */
    private Long spuId;

    /** 来源订单 ID */
    private Long orderId;

    /** 持卡用户 ID */
    private Long userId;

    /** 卡名称（下单时快照） */
    private String name;

    /** 核销码（全局唯一，二维码与数字码同值） */
    private String cardNo;

    /** 生效时间（= 付款时间） */
    private LocalDateTime startTime;

    /** 到期时间（= 付款时间 + 有效天数） */
    private LocalDateTime expireTime;

    /** 可核销次数快照；null = 不限次 */
    private Integer maxCount;

    /** 已核销次数 */
    private Integer usedCount;

    /** 状态：ACTIVE 可用 / USED_UP 次数用尽 / EXPIRED 已过期 */
    private String status;

}
