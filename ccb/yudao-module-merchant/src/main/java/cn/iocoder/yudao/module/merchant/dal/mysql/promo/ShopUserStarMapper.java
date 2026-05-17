package cn.iocoder.yudao.module.merchant.dal.mysql.promo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopUserStarDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ShopUserStarMapper extends BaseMapperX<ShopUserStarDO> {

    /**
     * v8: 按 (user, spu) 维度查；spu_id=0 是 v7 老数据兼容（账户余额维度），新代码尽量用 selectByUserAndSpu。
     * 兼容性：保留 selectByUserId 是因为推广积分余额仍然按 user 全局维度（spu_id=0 那条）。
     */
    default ShopUserStarDO selectByUserId(Long userId) {
        return selectOne(new LambdaQueryWrapperX<ShopUserStarDO>()
                .eq(ShopUserStarDO::getUserId, userId)
                .eq(ShopUserStarDO::getSpuId, 0L));
    }

    /** v8: 按 (user, spu) 维度查 — 该用户在该商品上的星级 / 直推数 / 团队链路销售。 */
    default ShopUserStarDO selectByUserAndSpu(Long userId, Long spuId) {
        return selectOne(new LambdaQueryWrapperX<ShopUserStarDO>()
                .eq(ShopUserStarDO::getUserId, userId)
                .eq(ShopUserStarDO::getSpuId, spuId));
    }

    /**
     * 行锁读取：必须在 @Transactional 内调用，用于"读旧余额 + 原子写 + 计算新余额"流程，
     * 让 balanceAfter 严格等于本次写后的余额（防并发交叉）。
     * v8: 仍按 user 全局账户（spu_id=0）维度锁；推广积分余额跨商品共享。
     */
    @Select("SELECT * FROM shop_user_star WHERE user_id = #{userId} AND spu_id = 0 AND deleted = b'0' FOR UPDATE")
    ShopUserStarDO selectByUserIdForUpdate(@Param("userId") Long userId);

    /** 列出某商户内所有"current_star >= ?"的用户（积分池可参与瓜分用户名单）。v8: 限定 spu_id=0 全局星级。 */
    default List<ShopUserStarDO> selectListByCurrentStarGe(int starInclusive) {
        return selectList(new LambdaQueryWrapperX<ShopUserStarDO>()
                .eq(ShopUserStarDO::getSpuId, 0L)
                .ge(ShopUserStarDO::getCurrentStar, starInclusive));
    }

    /** 按星级集合查（积分池白名单）。v8: 限定 spu_id=0 全局星级，避免一个用户在多商品上被重复计入。 */
    default List<ShopUserStarDO> selectListByCurrentStarIn(Collection<Integer> stars) {
        if (stars == null || stars.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapperX<ShopUserStarDO>()
                .eq(ShopUserStarDO::getSpuId, 0L)
                .in(ShopUserStarDO::getCurrentStar, stars));
    }

    // ============================================================
    // 原子累加 / 累减（避免 SELECT-改-UPDATE 引起的丢更新）
    // 所有方法返回受影响行数：
    //   1 = 成功；0 = 行不存在 或 余额不足（扣减场景）
    // 余额校验：扣减传负 delta，WHERE 加上 col + delta >= 0 兜底
    // ============================================================

    /**
     * 原子调整推广积分余额。delta 为正 = 入账；为负 = 扣减（要求 余额 + delta ≥ 0）。
     * v8: 必须限定 spu_id=0（全局账户行），否则会撞上 v8 的 (user, spu>0) 行导致 rows>1 抛错。
     */
    @Update("UPDATE shop_user_star "
            + "SET promo_point_balance = promo_point_balance + #{delta}, update_time = NOW() "
            + "WHERE user_id = #{userId} AND spu_id = 0 AND deleted = b'0' "
            + "  AND promo_point_balance + #{delta} >= 0")
    int addPromoPointBalance(@Param("userId") Long userId, @Param("delta") long delta);

    /** 原子调整消费积分余额。v8: 限定 spu_id=0。 */
    @Update("UPDATE shop_user_star "
            + "SET consume_point_balance = consume_point_balance + #{delta}, update_time = NOW() "
            + "WHERE user_id = #{userId} AND spu_id = 0 AND deleted = b'0' "
            + "  AND consume_point_balance + #{delta} >= 0")
    int addConsumePointBalance(@Param("userId") Long userId, @Param("delta") long delta);

    /** 原子累加 直推下级数。v8: 限定 spu_id=0（v7 老路径全局账户）。 */
    @Update("UPDATE shop_user_star "
            + "SET direct_count = direct_count + #{delta}, update_time = NOW() "
            + "WHERE user_id = #{userId} AND spu_id = 0 AND deleted = b'0'")
    int addDirectCount(@Param("userId") Long userId, @Param("delta") int delta);

    /** 原子累加 团队链路销售份数。v8: 限定 spu_id=0（v7 老路径全局账户）。 */
    @Update("UPDATE shop_user_star "
            + "SET team_sales_count = team_sales_count + #{delta}, update_time = NOW() "
            + "WHERE user_id = #{userId} AND spu_id = 0 AND deleted = b'0'")
    int addTeamSalesCount(@Param("userId") Long userId, @Param("delta") int delta);

    /**
     * 升星更新（终生制：仅在 newStar > 当前星级时落库）。
     * v8: 限定 spu_id=0（v7 老路径全局账户）。
     */
    @Update("UPDATE shop_user_star "
            + "SET current_star = #{newStar}, upgraded_at = NOW(), update_time = NOW() "
            + "WHERE user_id = #{userId} AND spu_id = 0 AND deleted = b'0' AND current_star < #{newStar}")
    int upgradeStarIfHigher(@Param("userId") Long userId, @Param("newStar") int newStar);

    // ============================================================
    // v8: 按 (user, spu) 维度的原子操作（升星按商品独立）
    // ============================================================

    @Update("UPDATE shop_user_star "
            + "SET direct_count = direct_count + #{delta}, update_time = NOW() "
            + "WHERE user_id = #{userId} AND spu_id = #{spuId} AND deleted = b'0'")
    int addDirectCountBySpu(@Param("userId") Long userId, @Param("spuId") Long spuId, @Param("delta") int delta);

    @Update("UPDATE shop_user_star "
            + "SET team_sales_count = team_sales_count + #{cntDelta}, "
            + "    team_sales_amount = team_sales_amount + #{amtDelta}, update_time = NOW() "
            + "WHERE user_id = #{userId} AND spu_id = #{spuId} AND deleted = b'0'")
    int addTeamSalesBySpu(@Param("userId") Long userId, @Param("spuId") Long spuId,
                          @Param("cntDelta") int cntDelta, @Param("amtDelta") long amtDelta);

    @Update("UPDATE shop_user_star "
            + "SET current_star = #{newStar}, upgraded_at = NOW(), update_time = NOW() "
            + "WHERE user_id = #{userId} AND spu_id = #{spuId} AND deleted = b'0' AND current_star < #{newStar}")
    int upgradeStarIfHigherBySpu(@Param("userId") Long userId, @Param("spuId") Long spuId, @Param("newStar") int newStar);

    /** v8 奖池结算用：列出在指定 SPU 上达到指定星级的用户。 */
    default List<ShopUserStarDO> selectListBySpuAndStar(Long spuId, int star) {
        return selectList(new LambdaQueryWrapperX<ShopUserStarDO>()
                .eq(ShopUserStarDO::getSpuId, spuId)
                .eq(ShopUserStarDO::getCurrentStar, star));
    }

}
