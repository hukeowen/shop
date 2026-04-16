package cn.iocoder.yudao.module.merchant.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.AiVideoTaskDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiVideoTaskMapper extends BaseMapperX<AiVideoTaskDO> {

    default PageResult<AiVideoTaskDO> selectPage(Long userId, PageParam pageParam) {
        // 使用 eq（而非 eqIfPresent）：userId 必须传入，防止 null 时查询跨用户
        return selectPage(pageParam, new LambdaQueryWrapperX<AiVideoTaskDO>()
                .eq(AiVideoTaskDO::getUserId, userId)
                .orderByDesc(AiVideoTaskDO::getId));
    }

    /**
     * 乐观并发：仅在当前状态等于 {@code expectedStatus} 时才更新为 {@code newStatus}。
     *
     * @return 受影响行数；0 表示任务不存在、不属于该用户或状态已变更
     */
    default int updateStatusIfMatch(Long id, Long userId, Integer expectedStatus, Integer newStatus) {
        return update(null, new LambdaUpdateWrapper<AiVideoTaskDO>()
                .set(AiVideoTaskDO::getStatus, newStatus)
                .eq(AiVideoTaskDO::getId, id)
                .eq(AiVideoTaskDO::getUserId, userId)
                .eq(AiVideoTaskDO::getStatus, expectedStatus));
    }

    /**
     * 幂等标记配额已扣减：仅在 quota_deducted=false 时置为 true。
     *
     * @return 1 表示首次扣减（调用方应继续扣减订阅）；0 表示已扣减过或任务不存在
     */
    default int markQuotaDeductedAtomic(Long id) {
        return update(null, new LambdaUpdateWrapper<AiVideoTaskDO>()
                .set(AiVideoTaskDO::getQuotaDeducted, true)
                .eq(AiVideoTaskDO::getId, id)
                .eq(AiVideoTaskDO::getQuotaDeducted, false));
    }

}
