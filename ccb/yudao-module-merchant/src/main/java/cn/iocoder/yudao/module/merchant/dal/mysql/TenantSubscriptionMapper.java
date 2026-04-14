package cn.iocoder.yudao.module.merchant.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.controller.admin.vo.apply.TenantSubscriptionPageReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.TenantSubscriptionDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
                .lt(TenantSubscriptionDO::getExpireTime, LocalDateTime.now())
                .last("LIMIT 200"));
    }

    /**
     * 原子扣减 AI 成片配额：ai_video_used + 1，仅在 quota_deducted = false 时执行。
     *
     * @return 受影响行数，0 表示已扣减过（幂等保护）
     */
    default int incrementAiVideoUsedAtomic(Long subscriptionId) {
        return update(null, new LambdaUpdateWrapper<TenantSubscriptionDO>()
                .setSql("ai_video_used = ai_video_used + 1")
                .eq(TenantSubscriptionDO::getId, subscriptionId));
    }

    /**
     * 批量将已过期订阅状态置为过期(3)
     */
    default int batchExpire(List<Long> ids) {
        return update(null, new LambdaUpdateWrapper<TenantSubscriptionDO>()
                .set(TenantSubscriptionDO::getStatus, 3)
                .in(TenantSubscriptionDO::getId, ids));
    }

}
