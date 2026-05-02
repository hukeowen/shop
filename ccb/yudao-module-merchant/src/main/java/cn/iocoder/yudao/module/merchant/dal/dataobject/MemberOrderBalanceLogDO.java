package cn.iocoder.yudao.module.merchant.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单余额抵扣日志：用户在 checkout 时使用店铺余额抵扣订单金额，
 * 每个订单只能抵扣一次（UNIQUE 索引 + @Transactional 配合实现幂等）。
 */
@TableName("member_order_balance_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberOrderBalanceLogDO extends BaseDO {

    @TableId
    private Long id;

    private Long userId;

    private Long tenantId;

    private Long orderId;

    /** 抵扣金额（分） */
    private Integer amount;

}
