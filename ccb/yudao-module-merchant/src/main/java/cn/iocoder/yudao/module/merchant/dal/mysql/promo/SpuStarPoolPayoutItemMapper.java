package cn.iocoder.yudao.module.merchant.dal.mysql.promo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.SpuStarPoolPayoutItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SpuStarPoolPayoutItemMapper extends BaseMapperX<SpuStarPoolPayoutItemDO> {

    default List<SpuStarPoolPayoutItemDO> selectListBySettleId(Long settleId) {
        return selectList(new LambdaQueryWrapperX<SpuStarPoolPayoutItemDO>()
                .eq(SpuStarPoolPayoutItemDO::getSettleId, settleId)
                .orderByDesc(SpuStarPoolPayoutItemDO::getAmount));
    }

}
