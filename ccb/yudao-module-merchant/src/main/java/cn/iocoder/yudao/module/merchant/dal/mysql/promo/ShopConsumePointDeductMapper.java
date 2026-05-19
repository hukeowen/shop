package cn.iocoder.yudao.module.merchant.dal.mysql.promo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopConsumePointDeductDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ShopConsumePointDeductMapper extends BaseMapperX<ShopConsumePointDeductDO> {

    /** 按订单查抵扣记录（UNIQUE，最多一条） */
    default ShopConsumePointDeductDO selectByOrderId(Long orderId) {
        return selectOne(new LambdaQueryWrapperX<ShopConsumePointDeductDO>()
                .eq(ShopConsumePointDeductDO::getOrderId, orderId));
    }
}
