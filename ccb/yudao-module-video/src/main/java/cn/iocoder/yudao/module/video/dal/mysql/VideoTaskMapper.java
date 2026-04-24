package cn.iocoder.yudao.module.video.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.video.controller.admin.vo.VideoTaskPageReqVO;
import cn.iocoder.yudao.module.video.dal.dataobject.VideoTaskDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface VideoTaskMapper extends BaseMapperX<VideoTaskDO> {

    default PageResult<VideoTaskDO> selectPage(VideoTaskPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<VideoTaskDO>()
                .eqIfPresent(VideoTaskDO::getMerchantId, reqVO.getMerchantId())
                .eqIfPresent(VideoTaskDO::getStatus, reqVO.getStatus())
                .likeIfPresent(VideoTaskDO::getTitle, reqVO.getTitle())
                .orderByDesc(VideoTaskDO::getId));
    }

    default List<VideoTaskDO> selectByMerchantId(Long merchantId) {
        return selectList(VideoTaskDO::getMerchantId, merchantId);
    }

    default List<VideoTaskDO> selectListByUserId(Long userId) {
        return selectList(new LambdaQueryWrapperX<VideoTaskDO>()
                .eq(VideoTaskDO::getUserId, userId)
                .orderByDesc(VideoTaskDO::getId)
                .last("LIMIT 50"));
    }

}
