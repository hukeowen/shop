package cn.iocoder.yudao.module.merchant.dal.mysql.coupon;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.coupon.ShopCouponUserDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Set;

@Mapper
public interface ShopCouponUserMapper extends BaseMapperX<ShopCouponUserDO> {

    /** 当前用户在当前租户领过的券模板 ID 集合（用来给前端 strip 标"已领取") */
    default Set<Long> selectTakenCouponIds(Long userId) {
        java.util.List<ShopCouponUserDO> list = selectList(
                new LambdaQueryWrapperX<ShopCouponUserDO>()
                        .eq(ShopCouponUserDO::getUserId, userId));
        Set<Long> ids = new java.util.HashSet<>(list.size());
        for (ShopCouponUserDO row : list) {
            ids.add(row.getCouponId());
        }
        return ids;
    }

    /** 单条领取记录幂等检查（同一用户同一券） */
    default ShopCouponUserDO selectByUserIdAndCouponId(Long userId, Long couponId) {
        return selectOne(new LambdaQueryWrapperX<ShopCouponUserDO>()
                .eq(ShopCouponUserDO::getUserId, userId)
                .eq(ShopCouponUserDO::getCouponId, couponId));
    }

    /** 用户在当前租户下未使用 + 未过期的券（按 effectiveTime 倒序） */
    default java.util.List<ShopCouponUserDO> selectUsableByUser(Long userId) {
        return selectList(new LambdaQueryWrapperX<ShopCouponUserDO>()
                .eq(ShopCouponUserDO::getUserId, userId)
                .eq(ShopCouponUserDO::getStatus, 0)
                .gt(ShopCouponUserDO::getExpireTime, java.time.LocalDateTime.now())
                .orderByDesc(ShopCouponUserDO::getEffectiveTime));
    }

    /**
     * 原子核销：仅当 status=0 且未过期才更新成 status=1 + useTime + orderId；
     * 返 1=成功；0=已用 / 已过期 / 不存在 / 用户不匹配
     */
    @org.apache.ibatis.annotations.Update("UPDATE shop_coupon_user SET status = 1, "
            + "use_time = NOW(), order_id = #{orderId}, update_time = NOW() "
            + "WHERE id = #{id} AND user_id = #{userId} AND status = 0 "
            + "AND deleted = b'0' AND expire_time > NOW()")
    int markUsedAtomic(@org.apache.ibatis.annotations.Param("id") Long id,
                       @org.apache.ibatis.annotations.Param("userId") Long userId,
                       @org.apache.ibatis.annotations.Param("orderId") Long orderId);
}
