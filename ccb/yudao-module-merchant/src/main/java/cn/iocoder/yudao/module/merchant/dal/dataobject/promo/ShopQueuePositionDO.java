package cn.iocoder.yudao.module.merchant.dal.dataobject.promo;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 商品队列位置（直推 + 插队 + 自然推三机制核心）。
 *
 * 每用户每商品一行；唯一键 (tenant_id, spu_id, user_id)。
 * 累计满 N 次（商品配置的 N）→ status = EXITED 出队，永不再返奖。
 * B 层老队头永不过期，没人买就一直挂着。
 */
@TableName("shop_queue_position")
@KeySequence("shop_queue_position_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopQueuePositionDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long spuId;

    private Long userId;

    /** A = 主动（自购 / 推下级成交）, B = 被动（仅自然消费过） */
    private String layer;

    /** 已被返奖次数；累计到商品的 N 即出队 */
    private Integer accumulatedCount;

    /** 累计已得推广积分(分) */
    private Long accumulatedAmount;

    private LocalDateTime joinedAt;

    /** 升 A 层时间；A 层内部按此排序，越早越先返 */
    private LocalDateTime promotedAt;

    private LocalDateTime exitedAt;

    /** QUEUEING / EXITED（v6 旧字段保留，新代码读 state） */
    private String status;

    /**
     * v7 状态机：IN_PROGRESS / COMPLETED。
     *
     * <p>v6 用 status + layer 表达；v7 简化为：
     * <ul>
     *   <li>IN_PROGRESS：已激活，cumulated &lt; N，自购 + 下级首单都触发 cumulated++</li>
     *   <li>COMPLETED：cumulated == N，永久终态，返「订单实付总额 × 间推比%」</li>
     * </ul></p>
     */
    private String state;

}
