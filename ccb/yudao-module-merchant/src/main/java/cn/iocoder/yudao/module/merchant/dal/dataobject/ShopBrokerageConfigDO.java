package cn.iocoder.yudao.module.merchant.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 商户返佣与积分配置 DO
 * 每个商户租户一条记录，租户级别隔离
 */
@TableName("shop_brokerage_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopBrokerageConfigDO extends TenantBaseDO {

    /** 主键 */
    private Long id;

    /** 是否开启返佣 */
    private Boolean brokerageEnabled;

    /** 一级佣金比例（%） */
    private BigDecimal firstBrokeragePercent;

    /** 二级佣金比例（%） */
    private BigDecimal secondBrokeragePercent;

    /** 佣金冻结天数 */
    private Integer freezeDays;

    /** 是否开启推N返1活动 */
    private Boolean pushReturnEnabled;

    /** 推广大使需带来的有效订单数 */
    private Integer pushN;

    /** 达标奖励金额（分） */
    private Integer returnAmount;

    /** 消费1元赠积分数（0=关闭积分） */
    private Integer pointPerYuan;

    /** 最低提现金额（分） */
    private Integer minWithdrawAmount;

}
