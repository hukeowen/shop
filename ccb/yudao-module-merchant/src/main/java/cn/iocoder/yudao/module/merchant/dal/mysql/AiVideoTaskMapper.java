package cn.iocoder.yudao.module.merchant.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.AiVideoTaskDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiVideoTaskMapper extends BaseMapperX<AiVideoTaskDO> {

    default PageResult<AiVideoTaskDO> selectPage(Long userId, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<AiVideoTaskDO>()
                .eqIfPresent(AiVideoTaskDO::getUserId, userId)
                .orderByDesc(AiVideoTaskDO::getId));
    }

}
