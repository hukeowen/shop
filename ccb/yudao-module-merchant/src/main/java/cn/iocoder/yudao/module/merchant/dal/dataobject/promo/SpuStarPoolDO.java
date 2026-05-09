package cn.iocoder.yudao.module.merchant.dal.dataobject.promo;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * v8: 商品级星级奖池累计。
 *
 * <p>每商品一条；每订单 spu 行实付 × poolRatio% 入池。
 * 池内分发规则后续制定（暂只入池，不发）。</p>
 */
@TableName("spu_star_pool")
@KeySequence("spu_star_pool_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpuStarPoolDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long spuId;

    /** 当前池余额（分） */
    private Long poolBalance;

    /** 历史累计入池金额（分） */
    private Long totalIn;

    /** 历史累计发放金额（分） */
    private Long totalOut;

}
