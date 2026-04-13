package cn.iocoder.yudao.module.video.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.video.controller.admin.vo.VideoTaskCreateReqVO;
import cn.iocoder.yudao.module.video.controller.admin.vo.VideoTaskPageReqVO;
import cn.iocoder.yudao.module.video.dal.dataobject.VideoTaskDO;

/**
 * AI视频任务 Service
 */
public interface VideoTaskService {

    /**
     * 创建视频生成任务
     */
    Long createVideoTask(VideoTaskCreateReqVO createReqVO, Long merchantId, Long userId);

    /**
     * 获取视频任务
     */
    VideoTaskDO getVideoTask(Long id);

    /**
     * 分页查询视频任务
     */
    PageResult<VideoTaskDO> getVideoTaskPage(VideoTaskPageReqVO pageReqVO);

    /**
     * 处理视频生成（异步）
     */
    void processVideoGeneration(Long taskId);

    /**
     * 发布视频到抖音
     */
    void publishToDouyin(Long taskId);

}
