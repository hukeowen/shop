package cn.iocoder.yudao.module.merchant.dal.mysql.promo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.promo.ShopOfflinePaymentDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 线下转账收款记录 Mapper。
 */
@Mapper
public interface ShopOfflinePaymentMapper extends BaseMapperX<ShopOfflinePaymentDO> {

    /** 按订单 ID 取收款记录（order_id UNIQUE，最多一条）。 */
    default ShopOfflinePaymentDO selectByOrderId(Long orderId) {
        return selectOne(ShopOfflinePaymentDO::getOrderId, orderId);
    }

}
