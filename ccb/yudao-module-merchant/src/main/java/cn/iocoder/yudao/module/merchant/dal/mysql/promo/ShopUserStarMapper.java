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

    default ShopUserStarDO selectByUserId(Long userId) {
        return selectOne(new LambdaQueryWrapperX<ShopUserStarDO>()
                .eq(ShopUserStarDO::getUserId, userId));
    }

    /**
     * 行锁读取：必须在 @Transactional 内调用，用于"读旧余额 + 原子写 + 计算新余额"流程，
     * 让 balanceAfter 严格等于本次写后的余额（防并发交叉）。
     */
    @Select("SELECT * FROM shop_user_star WHERE user_id = #{userId} AND deleted = b'0' FOR UPDATE")
    ShopUserStarDO selectByUserIdForUpdate(@Param("userId") Long userId);

    /** 列出某商户内所有"current_star >= ?"的用户（积分池可参与瓜分用户名单） */
    default List<ShopUserStarDO> selectListByCurrentStarGe(int starInclusive) {
        return selectList(new LambdaQueryWrapperX<ShopUserStarDO>()
                .ge(ShopUserStarDO::getCurrentStar, starInclusive));
    }

    /** 按星级集合查（积分池白名单） */
    default List<ShopUserStarDO> selectListByCurrentStarIn(Collection<Integer> stars) {
        if (stars == null || stars.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapperX<ShopUserStarDO>()
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
     */
    @Update("UPDATE shop_user_star "
            + "SET promo_point_balance = promo_point_balance + #{delta}, update_time = NOW() "
            + "WHERE user_id = #{userId} AND deleted = b'0' "
            + "  AND promo_point_balance + #{delta} >= 0")
    int addPromoPointBalance(@Param("userId") Long userId, @Param("delta") long delta);

    /** 原子调整消费积分余额。 */
    @Update("UPDATE shop_user_star "
            + "SET consume_point_balance = consume_point_balance + #{delta}, update_time = NOW() "
            + "WHERE user_id = #{userId} AND deleted = b'0' "
            + "  AND consume_point_balance + #{delta} >= 0")
    int addConsumePointBalance(@Param("userId") Long userId, @Param("delta") long delta);

    /** 原子累加 直推下级数。 */
    @Update("UPDATE shop_user_star "
            + "SET direct_count = direct_count + #{delta}, update_time = NOW() "
            + "WHERE user_id = #{userId} AND deleted = b'0'")
    int addDirectCount(@Param("userId") Long userId, @Param("delta") int delta);

    /** 原子累加 团队链路销售份数。 */
    @Update("UPDATE shop_user_star "
            + "SET team_sales_count = team_sales_count + #{delta}, update_time = NOW() "
            + "WHERE user_id = #{userId} AND deleted = b'0'")
    int addTeamSalesCount(@Param("userId") Long userId, @Param("delta") int delta);

    /**
     * 升星更新（终生制：仅在 newStar > 当前星级时落库）。
     */
    @Update("UPDATE shop_user_star "
            + "SET current_star = #{newStar}, upgraded_at = NOW(), update_time = NOW() "
            + "WHERE user_id = #{userId} AND deleted = b'0' AND current_star < #{newStar}")
    int upgradeStarIfHigher(@Param("userId") Long userId, @Param("newStar") int newStar);

}
