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
}
