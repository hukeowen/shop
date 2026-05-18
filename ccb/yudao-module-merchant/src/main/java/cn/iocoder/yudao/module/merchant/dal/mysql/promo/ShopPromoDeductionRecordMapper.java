package cn.iocoder.yudao.module.merchant.dal.mysql.promo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoDeductionRecordDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ShopPromoDeductionRecordMapper extends BaseMapperX<ShopPromoDeductionRecordDO> {

    /** 幂等检查：某 (orderId, userId, spuId) 是否已经跑过推 N 反 1 状态机 */
    default boolean existsByOrderUserSpu(Long orderId, Long userId, Long spuId) {
        return selectCount(new LambdaQueryWrapperX<ShopPromoDeductionRecordDO>()
                .eq(ShopPromoDeductionRecordDO::getOrderId, orderId)
                .eq(ShopPromoDeductionRecordDO::getUserId, userId)
                .eq(ShopPromoDeductionRecordDO::getSpuId, spuId)) > 0;
    }
}
