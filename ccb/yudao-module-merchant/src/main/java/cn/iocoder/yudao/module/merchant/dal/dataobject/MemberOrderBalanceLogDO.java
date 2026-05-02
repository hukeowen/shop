package cn.iocoder.yudao.module.merchant.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 订单余额抵扣日志：用户在 checkout 时使用店铺余额抵扣订单金额，
 * 每个订单只能抵扣一次（UNIQUE 索引 + @Transactional 配合实现幂等）。
 *
 * <p>资金审计表，<b>不</b>继承 BaseDO — 不带 deleted 软删字段，避免
 * "selectOne(deleted=0) 看不到但 INSERT 撞 UNIQUE" 的不一致行为。</p>
 */
@TableName("member_order_balance_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberOrderBalanceLogDO {

    @TableId
    private Long id;

    private Long userId;

    private Long tenantId;

    private Long orderId;

    /** 抵扣金额（分） */
    private Integer amount;

    private String creator;
    private LocalDateTime createTime;
    private String updater;
    private LocalDateTime updateTime;

}
