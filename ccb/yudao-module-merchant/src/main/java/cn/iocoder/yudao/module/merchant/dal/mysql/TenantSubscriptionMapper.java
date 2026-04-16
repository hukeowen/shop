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

    /**
     * 过期扫描批大小。常量化避免后续误参数化 {@code .last(...)} 导致 SQL 注入。
     */
    String EXPIRE_SCAN_LIMIT = "LIMIT 200";

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
                .last(EXPIRE_SCAN_LIMIT));
    }

    /**
     * 原子扣减 AI 成片配额：
     * {@code UPDATE ... SET ai_video_used = ai_video_used + 1
     *        WHERE id = ? AND tenant_id = ? AND ai_video_used < ai_video_quota}
     *
     * <p>使用 {@code tenant_id} 谓词防止跨租户写入（TenantSubscriptionDO 为平台级表，
     * 未启用 MP 自动租户过滤）；使用 {@code ai_video_used < ai_video_quota} 保证超卖
     * 在并发场景下依然失败（返回 0）。</p>
     *
     * @return 受影响行数：1 表示扣减成功；0 表示配额已耗尽或 tenant 不匹配
     */
    default int incrementAiVideoUsedAtomic(Long subscriptionId, Long tenantId) {
        return update(null, new LambdaUpdateWrapper<TenantSubscriptionDO>()
                .setSql("ai_video_used = ai_video_used + 1")
                .eq(TenantSubscriptionDO::getId, subscriptionId)
                .eq(TenantSubscriptionDO::getTenantId, tenantId)
                .apply("ai_video_used < ai_video_quota"));
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
