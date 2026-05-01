package cn.iocoder.yudao.module.video.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.video.controller.admin.vo.VideoTaskCreateReqVO;
import cn.iocoder.yudao.module.video.controller.admin.vo.VideoTaskPageReqVO;
import cn.iocoder.yudao.module.video.dal.dataobject.VideoTaskDO;

import java.util.List;

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

    /**
     * 注册客户端发起的任务（不触发异步生成，仅落库）
     */
    Long registerClientTask(String title, String description, List<String> imageUrls, Long merchantId, Long userId);

    /**
     * 同步客户端任务最终状态（status/videoUrl/failReason）
     */
    void syncClientTaskStatus(Long id, Integer dbStatus, String videoUrl, String failReason);

    /**
     * 查询用户的任务列表（最近50条）
     */
    List<VideoTaskDO> listByUserId(Long userId);

    /**
     * 部分更新任务的元数据（B 改造 Step 2）—— 仅覆盖 patch 中非空字段，
     * 用于前端 createTask / confirmTask 完成后把 bgmStyle / posterUrl / voiceKey /
     * ratio / coverUrl / title / description / imageUrls / status / failReason
     * 等元数据同步落库。
     *
     * <p>幂等：基于 id 直接 selectById + updateById；不存在则忽略。null/空字段
     * 不覆盖原值，避免误清。</p>
     */
    void updateTaskMeta(Long id, VideoTaskDO patch);

}
