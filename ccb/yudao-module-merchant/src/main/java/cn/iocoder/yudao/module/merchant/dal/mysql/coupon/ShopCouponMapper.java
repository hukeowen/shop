package cn.iocoder.yudao.module.merchant.dal.mysql.coupon;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.coupon.ShopCouponDO;
import org.apache.ibatis.annotations.Mapper;

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
}
