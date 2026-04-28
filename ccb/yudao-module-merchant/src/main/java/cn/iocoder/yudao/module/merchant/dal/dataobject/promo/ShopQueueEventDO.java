package cn.iocoder.yudao.module.merchant.dal.dataobject.promo;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

/**
 * 队列事件流水：每次返奖 / 出队都写一条，方便审计和追溯。
 *
 * event_type ∈ { DIRECT, QUEUE, SELF_PURCHASE, EXIT }
 *   DIRECT         买家有上级 → 上级直接拿奖
 *   QUEUE          自然用户购买 → 队首拿奖
 *   SELF_PURCHASE  B 层老用户自购同款 → 自己拿奖（插队）
 *   EXIT           累计满 N 次 → 出队事件
 */
@TableName("shop_queue_event")
@KeySequence("shop_queue_event_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopQueueEventDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long spuId;

    private String eventType;

    private Long beneficiaryUserId;

    /** 触发用户（买家） */
    private Long sourceUserId;

    private Long sourceOrderId;

    /** 受益人当时的累计位置序号 (1-based)；EXIT 事件 = N */
    private Integer positionIndex;

    /** 该位置的比例(%) */
    private BigDecimal ratioPercent;

    /** 返推广积分金额(分) */
    private Long amount;

}
