package cn.iocoder.yudao.module.merchant.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MemberOrderBalanceLogDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MemberOrderBalanceLogMapper extends BaseMapperX<MemberOrderBalanceLogDO> {

    default MemberOrderBalanceLogDO selectByUserTenantOrder(Long userId, Long tenantId, Long orderId) {
        return selectOne(MemberOrderBalanceLogDO::getUserId, userId,
                MemberOrderBalanceLogDO::getTenantId, tenantId,
                MemberOrderBalanceLogDO::getOrderId, orderId);
    }

}
