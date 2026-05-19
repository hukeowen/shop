package cn.iocoder.yudao.module.merchant.dal.dataobject.promo;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单消费积分抵扣记录（V043）。
 * <p>每订单一行，UNIQUE(order_id)。状态机：
 * <ul>
 *   <li>{@link #STATUS_PENDING} - submit 已写记录但 balance 未扣（仅在用"PENDING→COMMITTED"模式时出现；
 *       当前实现采用"submit 即时扣 + cancel 退回"，所以默认直接落 COMMITTED）</li>
 *   <li>{@link #STATUS_COMMITTED} - balance 已扣 + shop_consume_point_record 已写</li>
 *   <li>{@link #STATUS_CANCELED} - 订单取消、积分已退回</li>
 * </ul>
 */
@TableName("shop_consume_point_deduct")
@KeySequence("shop_consume_point_deduct_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopConsumePointDeductDO extends TenantBaseDO {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMMITTED = "COMMITTED";
    public static final String STATUS_CANCELED = "CANCELED";

    @TableId
    private Long id;

    private Long orderId;
    private Long userId;
    /** 抵扣使用的积分数量（= shop_user_star.consume_point_balance 扣减量） */
    private Long pointsUsed;
    /** 下单时商户配置快照：1 积分 = ratioSnapshot 分钱 */
    private BigDecimal ratioSnapshot;
    /** 实际抵扣订单金额（分） = pointsUsed × ratioSnapshot（向下取整） */
    private Long deductAmount;
    /** PENDING / COMMITTED / CANCELED */
    private String status;
    private LocalDateTime commitTime;
    private LocalDateTime cancelTime;
    /** shop_consume_point_record.id（COMMITTED 后回填，便于审计） */
    private Long pointLogId;
}
