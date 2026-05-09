package cn.iocoder.yudao.module.merchant.dal.mysql.promo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.SpuStarPoolDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SpuStarPoolMapper extends BaseMapperX<SpuStarPoolDO> {

    default SpuStarPoolDO selectBySpuId(Long spuId) {
        return selectOne(new LambdaQueryWrapperX<SpuStarPoolDO>()
                .eq(SpuStarPoolDO::getSpuId, spuId));
    }

    /** 原子入池：pool_balance += amount, total_in += amount */
    @Update("UPDATE spu_star_pool "
            + "SET pool_balance = pool_balance + #{amount}, total_in = total_in + #{amount}, update_time = NOW() "
            + "WHERE spu_id = #{spuId} AND deleted = b'0'")
    int incrementPool(@Param("spuId") Long spuId, @Param("amount") long amount);

}
