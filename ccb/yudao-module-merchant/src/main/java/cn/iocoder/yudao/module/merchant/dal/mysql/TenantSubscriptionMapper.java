package cn.iocoder.yudao.module.merchant.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.apply.TenantSubscriptionPageReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.TenantSubscriptionDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Mapper
public interface TenantSubscriptionMapper extends BaseMapperX<TenantSubscriptionDO> {

    default TenantSubscriptionDO selectByTenantId(Long tenantId) {
        return selectOne(TenantSubscriptionDO::getTenantId, tenantId);
    }

    default PageResult<TenantSubscriptionDO> selectPage(TenantSubscriptionPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TenantSubscriptionDO>()
                .eqIfPresent(TenantSubscriptionDO::getStatus, reqVO.getStatus())
                .eqIfPresent(TenantSubscriptionDO::getTenantId, reqVO.getTenantId())
                .orderByDesc(TenantSubscriptionDO::getId));
    }

    /** 查询已过期但状态仍为试用(1)或正式(2)的订阅，用于定时任务批量过期 */
    default List<TenantSubscriptionDO> selectExpiredActive() {
        return selectList(new LambdaQueryWrapperX<TenantSubscriptionDO>()
                .in(TenantSubscriptionDO::getStatus, Arrays.asList(1, 2))
                .lt(TenantSubscriptionDO::getExpireTime, LocalDateTime.now()));
    }

}
