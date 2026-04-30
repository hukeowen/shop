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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

/**
 * AI视频任务 Service 实现类
 *
 * <p><b>定位</b>：admin 后台直接发起的视频任务（无 merchant 入口），保留以兼容历史接口。
 * 商户小程序的主流程走 {@code AiVideoTaskService}（merchant 模块）+ {@link AiVideoTaskCreatedListener}。</p>
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
        // video_task.description / image_urls 都是 NOT NULL，入参缺失就直接抛业务码
        if (createReqVO.getDescription() == null || createReqVO.getDescription().trim().isEmpty()) {
            throw exception0(1_030_001_010, "视频描述不能为空");
        }
        if (createReqVO.getImageUrls() == null || createReqVO.getImageUrls().isEmpty()) {
            throw exception0(1_030_001_011, "图片列表不能为空");
        }
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

        VideoTaskDO updateObj = new VideoTaskDO();
        updateObj.setId(taskId);
        updateObj.setStatus(VideoTaskStatusEnum.PROCESSING.getStatus());
        videoTaskMapper.updateById(updateObj);

        try {
            // 由 description 拆出逐句作为 Seedance prompt（按中文标点分句）
            List<String> lines = splitToLines(task.getDescription());

            // 1. Seedance 图生视频（异步任务 + 内部轮询）
            String remoteVideoUrl = videoGenerateService.generateVideoFromImages(task.getImageUrls(), lines);
            // 2. 落到自有 OSS
            String videoUrl = videoGenerateService.persistToOss(remoteVideoUrl);
            // 3. （可选）TTS
            String audioUrl = null;
            try {
                audioUrl = videoGenerateService.textToSpeech(task.getDescription());
            } catch (Exception ttsEx) {
                log.warn("[processVideoGeneration] TTS 失败（不阻塞主流程） taskId={}", taskId, ttsEx);
            }

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
            updateObj.setFailReason(e.getMessage() != null && e.getMessage().length() > 200
                    ? e.getMessage().substring(0, 200) : e.getMessage());
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

        VideoTaskDO updateObj = new VideoTaskDO();
        updateObj.setId(taskId);
        updateObj.setDouyinPublishStatus(DouyinPublishStatusEnum.PUBLISHING.getStatus());
        videoTaskMapper.updateById(updateObj);

        try {
            String videoId = douyinService.uploadVideo(task.getMerchantId(), task.getVideoUrl());
            String itemId = douyinService.publishVideo(task.getMerchantId(), videoId, task.getTitle());

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

    @Override
    public Long registerClientTask(String title, String description, List<String> imageUrls, Long merchantId, Long userId) {
        // video_task.description / image_urls / merchant_id / user_id 都是 NOT NULL
        if (description == null || description.trim().isEmpty()) {
            throw exception0(1_030_001_010, "视频描述不能为空");
        }
        if (imageUrls == null || imageUrls.isEmpty()) {
            throw exception0(1_030_001_011, "图片列表不能为空");
        }
        if (merchantId == null || merchantId <= 0L) {
            throw exception0(1_030_001_012, "缺少商户身份，无法落库");
        }
        if (userId == null || userId <= 0L) {
            throw exception0(1_030_001_013, "缺少用户身份，无法落库");
        }
        VideoTaskDO task = VideoTaskDO.builder()
                .merchantId(merchantId)
                .userId(userId)
                .title(title != null ? title : description)
                .description(description)
                .imageUrls(imageUrls)
                .status(VideoTaskStatusEnum.PROCESSING.getStatus())
                .douyinPublishStatus(DouyinPublishStatusEnum.UNPUBLISHED.getStatus())
                .build();
        videoTaskMapper.insert(task);
        return task.getId();
    }

    @Override
    public void syncClientTaskStatus(Long id, Integer dbStatus, String videoUrl, String failReason) {
        VideoTaskDO updateObj = new VideoTaskDO();
        updateObj.setId(id);
        updateObj.setStatus(dbStatus);
        if (videoUrl != null && !videoUrl.isEmpty()) {
            updateObj.setVideoUrl(videoUrl);
        }
        if (failReason != null && !failReason.isEmpty()) {
            String truncated = failReason.length() > 200 ? failReason.substring(0, 200) : failReason;
            updateObj.setFailReason(truncated);
        }
        videoTaskMapper.updateById(updateObj);
    }

    @Override
    public List<VideoTaskDO> listByUserId(Long userId) {
        return videoTaskMapper.selectListByUserId(userId);
    }

    private List<String> splitToLines(String description) {
        if (description == null || description.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return Arrays.stream(description.split("[，。！？,.!?\n]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

}
