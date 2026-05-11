package cn.iocoder.yudao.module.merchant.dal.mysql.saas;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.saas.MerchantSubscriptionOrderDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MerchantSubscriptionOrderMapper extends BaseMapperX<MerchantSubscriptionOrderDO> {

    default List<MerchantSubscriptionOrderDO> selectListByMerchant(Long merchantId) {
        return selectList(new LambdaQueryWrapperX<MerchantSubscriptionOrderDO>()
                .eq(MerchantSubscriptionOrderDO::getMerchantId, merchantId)
                .orderByDesc(MerchantSubscriptionOrderDO::getId));
    }

    default List<MerchantSubscriptionOrderDO> selectListWaitingOlderThan(long minutesAgo) {
        java.time.LocalDateTime threshold = java.time.LocalDateTime.now().minusMinutes(minutesAgo);
        return selectList(new LambdaQueryWrapperX<MerchantSubscriptionOrderDO>()
                .eq(MerchantSubscriptionOrderDO::getPayStatus, MerchantSubscriptionOrderDO.PAY_STATUS_WAITING)
                .lt(MerchantSubscriptionOrderDO::getCreateTime, threshold));
    }
}
