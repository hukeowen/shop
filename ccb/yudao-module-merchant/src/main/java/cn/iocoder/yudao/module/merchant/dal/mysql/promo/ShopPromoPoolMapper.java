package cn.iocoder.yudao.module.merchant.dal.mysql.promo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopPromoPoolDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ShopPromoPoolMapper extends BaseMapperX<ShopPromoPoolDO> {

    /** 取本商户的池子（仅一条；不存在返 null） */
    default ShopPromoPoolDO selectCurrent() {
        return selectOne(new LambdaQueryWrapperX<ShopPromoPoolDO>());
    }

    /**
     * 原子累加池余额；返 1 = 成功，0 = 行不存在。
     *
     * 走原生 SQL，避免 SELECT-改-UPDATE 的丢更新；和 user_star 余额走同一套并发约束。
     * tenant_id 由 yudao 的 TenantLineInnerInterceptor 通过 JSqlParser 自动注入 WHERE 条件。
     */
    @Update("UPDATE shop_promo_pool "
            + "SET balance = balance + #{delta}, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = b'0'")
    int addBalance(@Param("id") Long id, @Param("delta") long delta);

    /**
     * 条件清池（结算后用）：仅当余额 = expectedBalance 才清零；
     * 防并发场景下 settle 已开始读 100，期间又有 deposit +50 → 你不应清掉额外的 50。
     */
    @Update("UPDATE shop_promo_pool "
            + "SET balance = 0, last_settled_at = NOW(), update_time = NOW() "
            + "WHERE id = #{id} AND deleted = b'0' AND balance = #{expectedBalance}")
    int clearIfBalanceEquals(@Param("id") Long id, @Param("expectedBalance") long expectedBalance);

}
