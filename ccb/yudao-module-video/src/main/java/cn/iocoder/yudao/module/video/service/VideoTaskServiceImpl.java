package cn.iocoder.yudao.module.video.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.video.controller.admin.vo.VideoTaskCreateReqVO;
import cn.iocoder.yudao.module.video.controller.admin.vo.VideoTaskPageReqVO;
import cn.iocoder.yudao.module.video.dal.dataobject.VideoTaskDO;
import cn.iocoder.yudao.module.video.dal.mysql.VideoTaskMapper;
import cn.iocoder.yudao.module.video.enums.DouyinPublishStatusEnum;
import cn.iocoder.yudao.module.video.enums.VideoTaskStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

/**
 * AI视频任务 Service 实现类
 */
@Service
@Validated
@Slf4j
public class VideoTaskServiceImpl implements VideoTaskService {

    @Resource
    private VideoTaskMapper videoTaskMapper;

    @Resource
    private VideoGenerateService videoGenerateService;

    @Resource
    private DouyinService douyinService;

    @Override
    public Long createVideoTask(VideoTaskCreateReqVO createReqVO, Long merchantId, Long userId) {
        VideoTaskDO task = VideoTaskDO.builder()
                .merchantId(merchantId)
                .userId(userId)
                .title(createReqVO.getTitle())
                .description(createReqVO.getDescription())
                .imageUrls(createReqVO.getImageUrls())
                .bgmUrl(createReqVO.getBgmUrl())
                .status(VideoTaskStatusEnum.PENDING.getStatus())
                .douyinPublishStatus(DouyinPublishStatusEnum.UNPUBLISHED.getStatus())
                .build();
        videoTaskMapper.insert(task);

        // 异步处理视频生成
        processVideoGeneration(task.getId());

        return task.getId();
    }

    @Override
    public VideoTaskDO getVideoTask(Long id) {
        return videoTaskMapper.selectById(id);
    }

    @Override
    public PageResult<VideoTaskDO> getVideoTaskPage(VideoTaskPageReqVO pageReqVO) {
        return videoTaskMapper.selectPage(pageReqVO);
    }

    @Override
    @Async
    public void processVideoGeneration(Long taskId) {
        log.info("[processVideoGeneration] 开始处理视频生成任务: {}", taskId);
        VideoTaskDO task = videoTaskMapper.selectById(taskId);
        if (task == null) {
            return;
        }

        // 更新状态为生成中
        VideoTaskDO updateObj = new VideoTaskDO();
        updateObj.setId(taskId);
        updateObj.setStatus(VideoTaskStatusEnum.PROCESSING.getStatus());
        videoTaskMapper.updateById(updateObj);

        try {
            // 1. 文字转语音
            String audioUrl = videoGenerateService.textToSpeech(task.getDescription());

            // 2. 图片+音频合成视频
            String videoUrl = videoGenerateService.generateVideo(task.getImageUrls(), audioUrl, task.getBgmUrl());

            // 3. 更新任务状态
            updateObj = new VideoTaskDO();
            updateObj.setId(taskId);
            updateObj.setTtsAudioUrl(audioUrl);
            updateObj.setVideoUrl(videoUrl);
            updateObj.setStatus(VideoTaskStatusEnum.COMPLETED.getStatus());
            videoTaskMapper.updateById(updateObj);

            log.info("[processVideoGeneration] 视频生成完成: taskId={}, videoUrl={}", taskId, videoUrl);
        } catch (Exception e) {
            log.error("[processVideoGeneration] 视频生成失败: taskId={}", taskId, e);
            updateObj = new VideoTaskDO();
            updateObj.setId(taskId);
            updateObj.setStatus(VideoTaskStatusEnum.FAILED.getStatus());
            updateObj.setFailReason(e.getMessage());
            videoTaskMapper.updateById(updateObj);
        }
    }

    @Override
    public void publishToDouyin(Long taskId) {
        VideoTaskDO task = videoTaskMapper.selectById(taskId);
        if (task == null) {
            throw exception0(1_030_001_000, "视频任务不存在");
        }
        if (!VideoTaskStatusEnum.COMPLETED.getStatus().equals(task.getStatus())) {
            throw exception0(1_030_001_001, "视频尚未生成完成");
        }

        // 更新发布状态
        VideoTaskDO updateObj = new VideoTaskDO();
        updateObj.setId(taskId);
        updateObj.setDouyinPublishStatus(DouyinPublishStatusEnum.PUBLISHING.getStatus());
        videoTaskMapper.updateById(updateObj);

        try {
            // 1. 上传视频到抖音
            String videoId = douyinService.uploadVideo(task.getMerchantId(), task.getVideoUrl());

            // 2. 发布视频
            String itemId = douyinService.publishVideo(task.getMerchantId(), videoId, task.getTitle());

            // 3. 更新状态
            updateObj = new VideoTaskDO();
            updateObj.setId(taskId);
            updateObj.setDouyinPublishStatus(DouyinPublishStatusEnum.PUBLISHED.getStatus());
            updateObj.setDouyinItemId(itemId);
            updateObj.setDouyinPublishTime(LocalDateTime.now());
            videoTaskMapper.updateById(updateObj);

            log.info("[publishToDouyin] 视频发布到抖音成功: taskId={}, itemId={}", taskId, itemId);
        } catch (Exception e) {
            log.error("[publishToDouyin] 视频发布到抖音失败: taskId={}", taskId, e);
            updateObj = new VideoTaskDO();
            updateObj.setId(taskId);
            updateObj.setDouyinPublishStatus(DouyinPublishStatusEnum.FAILED.getStatus());
            videoTaskMapper.updateById(updateObj);
        }
    }

}
