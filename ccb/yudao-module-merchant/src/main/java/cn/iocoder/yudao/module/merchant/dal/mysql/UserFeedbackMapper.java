package cn.iocoder.yudao.module.merchant.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.UserFeedbackDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserFeedbackMapper extends BaseMapperX<UserFeedbackDO> {

    default PageResult<UserFeedbackDO> selectPageByUser(Long userId, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<UserFeedbackDO>()
                .eq(UserFeedbackDO::getUserId, userId)
                .orderByDesc(UserFeedbackDO::getId));
    }

}
