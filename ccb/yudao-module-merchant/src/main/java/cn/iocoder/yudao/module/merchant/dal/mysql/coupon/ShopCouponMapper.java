package cn.iocoder.yudao.module.merchant.dal.mysql.coupon;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.coupon.ShopCouponDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ShopCouponMapper extends BaseMapperX<ShopCouponDO> {

    /** 商户自己看：所有非删除券，按 sort 由近到远 */
    default List<ShopCouponDO> selectAllByMerchant() {
        return selectList(new LambdaQueryWrapperX<ShopCouponDO>()
                .orderByDesc(ShopCouponDO::getId));
    }

    /** C 端拉某店可领券：仅 status=0 上架；不限制库存（前端按 totalCount-takenCount 自己判） */
    default List<ShopCouponDO> selectEnabledByTenant() {
        return selectList(new LambdaQueryWrapperX<ShopCouponDO>()
                .eq(ShopCouponDO::getStatus, 0)
                .orderByDesc(ShopCouponDO::getId));
    }

    /**
     * 原子递增 taken_count（防 grab 并发超发）。
     *
     * <p>只在 total_count=0（不限）或 taken_count&lt;total_count 时执行 +1。
     * 返回 affected rows：1 = 成功 +1；0 = 已领完或券不存在/已下架（caller 应当报"已领完"）。</p>
     *
     * <p>与 status=0 (上架) 一并校验避免下架后还能被抢。</p>
     */
    @Update("UPDATE shop_coupon SET taken_count = taken_count + 1, update_time = NOW() " +
            "WHERE id = #{id} " +
            "  AND deleted = b'0' " +
            "  AND status = 0 " +
            "  AND (total_count = 0 OR taken_count < total_count)")
    int atomicIncrTaken(@Param("id") Long id);
}
