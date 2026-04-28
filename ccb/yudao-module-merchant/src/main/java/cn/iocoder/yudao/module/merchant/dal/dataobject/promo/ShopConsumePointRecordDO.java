package cn.iocoder.yudao.module.merchant.dal.dataobject.promo;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 消费积分流水。
 * source_type ∈ { CONSUME, CONVERT, REDEEM }
 */
@TableName("shop_consume_point_record")
@KeySequence("shop_consume_point_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopConsumePointRecordDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long userId;

    private String sourceType;

    private Long amount;

    private Long balanceAfter;

    private Long sourceId;

    private String remark;

}
