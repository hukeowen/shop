package cn.iocoder.yudao.module.merchant.dal.mysql.promo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.SpuStarPoolDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
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

    /**
     * 行锁读取（结算专用）：必须在 @Transactional 内调用，防止并发结算同时读到同一余额发两次。
     * tenant_id 由 MybatisPlus 多租户拦截器自动追加；这里不显式写，否则会因为双 tenant_id 冲突。
     */
    @Select("SELECT * FROM spu_star_pool WHERE spu_id = #{spuId} AND deleted = b'0' FOR UPDATE")
    SpuStarPoolDO selectBySpuIdForUpdate(@Param("spuId") Long spuId);

    /**
     * 结算专用：扣减池余额 + 累加 total_out。
     * 条件 pool_balance >= delta：防超扣（理论上拿到 FOR UPDATE 后不可能失败，作为兜底）。
     */
    @Update("UPDATE spu_star_pool "
            + "SET pool_balance = pool_balance - #{delta}, total_out = total_out + #{delta}, update_time = NOW() "
            + "WHERE spu_id = #{spuId} AND deleted = b'0' AND pool_balance >= #{delta}")
    int decrementPoolForSettle(@Param("spuId") Long spuId, @Param("delta") long delta);

}
