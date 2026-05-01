package cn.iocoder.yudao.module.video.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import cn.iocoder.yudao.module.video.controller.admin.vo.VideoTaskCreateReqVO;
import cn.iocoder.yudao.module.video.controller.admin.vo.VideoTaskRespVO;
import cn.iocoder.yudao.module.video.controller.app.vo.VideoScenePatchReqVO;
import cn.iocoder.yudao.module.video.controller.app.vo.VideoSceneSaveReqVO;
import cn.iocoder.yudao.module.video.controller.app.vo.VideoTaskMetaPatchReqVO;
import cn.iocoder.yudao.module.video.controller.app.vo.VideoTaskWithScenesRespVO;
import cn.iocoder.yudao.module.video.dal.dataobject.VideoSceneDO;
import cn.iocoder.yudao.module.video.dal.dataobject.VideoTaskDO;
import cn.iocoder.yudao.module.video.enums.VideoTaskStatusEnum;
import cn.iocoder.yudao.module.video.service.VideoSceneService;
import cn.iocoder.yudao.module.video.service.VideoTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.AI_VIDEO_MERCHANT_NOT_FOUND;

@Tag(name = "用户 App - AI视频")
@RestController
@RequestMapping("/video/app/task")
@Validated
public class AppVideoTaskController {

    @Resource
    private VideoTaskService videoTaskService;

    @Resource
    private VideoSceneService videoSceneService;

    @Resource
    private cn.iocoder.yudao.module.merchant.service.MerchantService merchantService;

    @PostMapping("/create")
    @Operation(summary = "创建AI视频任务")
    public CommonResult<Long> createVideoTask(@Valid @RequestBody VideoTaskCreateReqVO createReqVO) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO merchant = merchantService.getMerchantByUserId(userId);
        if (merchant == null) {
            throw exception(AI_VIDEO_MERCHANT_NOT_FOUND);
        }
        return success(videoTaskService.createVideoTask(createReqVO, merchant.getId(), userId));
    }

    @GetMapping("/get")
    @Operation(summary = "获取视频任务详情")
    @Parameter(name = "id", description = "任务编号", required = true)
    public CommonResult<VideoTaskRespVO> getVideoTask(@RequestParam("id") Long id) {
        VideoTaskDO task = videoTaskService.getVideoTask(id);
        if (task == null) {
            return success(null);
        }
        VideoTaskRespVO respVO = new VideoTaskRespVO();
        respVO.setId(task.getId());
        respVO.setTitle(task.getTitle());
        respVO.setDescription(task.getDescription());
        respVO.setImageUrls(task.getImageUrls());
        respVO.setVideoUrl(task.getVideoUrl());
        respVO.setDuration(task.getDuration());
        respVO.setStatus(task.getStatus());
        respVO.setFailReason(task.getFailReason());
        respVO.setDouyinPublishStatus(task.getDouyinPublishStatus());
        respVO.setDouyinItemId(task.getDouyinItemId());
        respVO.setCreateTime(task.getCreateTime());
        return success(respVO);
    }

    @PostMapping("/publish-douyin")
    @Operation(summary = "发布视频到抖音")
    @Parameter(name = "id", description = "任务编号", required = true)
    public CommonResult<Boolean> publishToDouyin(@RequestParam("id") Long id) {
        videoTaskService.publishToDouyin(id);
        return success(true);
    }

    @GetMapping("/my-page")
    @Operation(summary = "查询我的视频任务列表（最近50条）")
    public CommonResult<List<VideoTaskRespVO>> myPage() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        List<VideoTaskDO> tasks = videoTaskService.listByUserId(userId);
        List<VideoTaskRespVO> voList = tasks.stream().map(t -> {
            VideoTaskRespVO vo = new VideoTaskRespVO();
            vo.setId(t.getId());
            vo.setTitle(t.getTitle());
            vo.setDescription(t.getDescription());
            vo.setImageUrls(t.getImageUrls());
            vo.setVideoUrl(t.getVideoUrl());
            vo.setDuration(t.getDuration());
            vo.setStatus(t.getStatus());
            vo.setFailReason(t.getFailReason());
            vo.setDouyinPublishStatus(t.getDouyinPublishStatus());
            vo.setDouyinItemId(t.getDouyinItemId());
            vo.setCreateTime(t.getCreateTime());
            return vo;
        }).collect(Collectors.toList());
        return success(voList);
    }

    @GetMapping("/get-with-scenes")
    @Operation(summary = "获取视频任务详情 + 全部分镜（B 改造：一次拉全）")
    @Parameter(name = "id", description = "任务编号", required = true)
    public CommonResult<VideoTaskWithScenesRespVO> getWithScenes(@RequestParam("id") Long id) {
        VideoTaskDO task = videoTaskService.getVideoTask(id);
        if (task == null) {
            return success(null);
        }
        // 商户隔离：仅能看自己商户下的任务
        Long currentUserId = SecurityFrameworkUtils.getLoginUserId();
        MerchantDO merchant = currentUserId == null ? null : merchantService.getMerchantByUserId(currentUserId);
        if (merchant == null || !merchant.getId().equals(task.getMerchantId())) {
            // 不是当前商户的任务，按 404 处理（避免返 403 暴露任务存在）
            return success(null);
        }
        List<VideoSceneDO> sceneDOs = videoSceneService.listByTaskId(id);
        VideoTaskWithScenesRespVO vo = new VideoTaskWithScenesRespVO();
        vo.setId(task.getId());
        vo.setMerchantId(task.getMerchantId());
        vo.setUserId(task.getUserId());
        vo.setTitle(task.getTitle());
        vo.setDescription(task.getDescription());
        vo.setImageUrls(task.getImageUrls());
        vo.setCoverUrl(task.getCoverUrl());
        vo.setPosterUrl(task.getPosterUrl());
        vo.setBgmStyle(task.getBgmStyle());
        vo.setVoiceKey(task.getVoiceKey());
        vo.setRatio(task.getRatio());
        vo.setVideoUrl(task.getVideoUrl());
        vo.setDuration(task.getDuration());
        vo.setStatus(task.getStatus());
        vo.setFailReason(task.getFailReason());
        vo.setDouyinPublishStatus(task.getDouyinPublishStatus());
        vo.setDouyinItemId(task.getDouyinItemId());
        vo.setCreateTime(task.getCreateTime());
        List<VideoTaskWithScenesRespVO.SceneItem> sceneItems = sceneDOs.stream().map(s -> {
            VideoTaskWithScenesRespVO.SceneItem item = new VideoTaskWithScenesRespVO.SceneItem();
            item.setId(s.getId());
            item.setSceneIndex(s.getSceneIndex());
            item.setImgIdx(s.getImgIdx());
            item.setStartImageUrl(s.getStartImageUrl());
            item.setNarration(s.getNarration());
            item.setVisualPrompt(s.getVisualPrompt());
            item.setImageSummary(s.getImageSummary());
            item.setDuration(s.getDuration());
            item.setIsEndCard(s.getIsEndCard());
            item.setStatus(s.getStatus());
            item.setFailReason(s.getFailReason());
            item.setClipTaskId(s.getClipTaskId());
            item.setClipUrl(s.getClipUrl());
            item.setAudioUrl(s.getAudioUrl());
            return item;
        }).collect(Collectors.toList());
        vo.setScenes(sceneItems);
        return success(vo);
    }

    @PostMapping("/scenes/save")
    @Operation(summary = "批量保存分镜（UPSERT）")
    public CommonResult<Boolean> saveScenes(@Valid @RequestBody VideoSceneSaveReqVO req) {
        // 商户隔离 + 任务存在性校验
        VideoTaskDO task = ensureMyTask(req.getTaskId());
        // 转换 DTO → DO
        List<VideoSceneDO> dos = req.getScenes().stream().map(s -> VideoSceneDO.builder()
                .taskId(req.getTaskId())
                .sceneIndex(s.getSceneIndex())
                .imgIdx(s.getImgIdx())
                .startImageUrl(s.getStartImageUrl())
                .narration(s.getNarration())
                .visualPrompt(s.getVisualPrompt())
                .imageSummary(s.getImageSummary())
                .duration(s.getDuration())
                .isEndCard(s.getIsEndCard())
                .status(s.getStatus())
                .build()).collect(Collectors.toList());
        videoSceneService.saveScenes(task.getId(), dos);
        return success(true);
    }

    @PutMapping("/scenes/patch")
    @Operation(summary = "单幕状态/产出 patch（runClip 路径用）")
    public CommonResult<Boolean> patchScene(@Valid @RequestBody VideoScenePatchReqVO req) {
        VideoTaskDO task = ensureMyTask(req.getTaskId());
        VideoSceneDO patch = VideoSceneDO.builder()
                .status(req.getStatus())
                .clipTaskId(req.getClipTaskId())
                .clipUrl(req.getClipUrl())
                .audioUrl(req.getAudioUrl())
                .failReason(req.getFailReason())
                .startImageUrl(req.getStartImageUrl())
                .build();
        videoSceneService.updateScenePartial(task.getId(), req.getSceneIndex(), patch);
        return success(true);
    }

    @PutMapping("/update-meta")
    @Operation(summary = "任务元数据 patch（confirmTask / 海报生成 / 最终落库时用）")
    public CommonResult<Boolean> patchMeta(@Valid @RequestBody VideoTaskMetaPatchReqVO req) {
        VideoTaskDO task = ensureMyTask(req.getId());
        VideoTaskDO patch = VideoTaskDO.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .imageUrls(req.getImageUrls())
                .bgmStyle(req.getBgmStyle())
                .posterUrl(req.getPosterUrl())
                .voiceKey(req.getVoiceKey())
                .ratio(req.getRatio())
                .coverUrl(req.getCoverUrl())
                .videoUrl(req.getVideoUrl())
                .duration(req.getDuration())
                .status(req.getStatus())
                .failReason(req.getFailReason())
                .build();
        videoTaskService.updateTaskMeta(task.getId(), patch);
        return success(true);
    }

    /**
     * 商户隔离 + 任务存在性校验：当前登录商户必须是该任务的拥有者。
     * 不是 → 抛 AI_VIDEO_MERCHANT_NOT_FOUND（避免暴露任务是否存在）。
     */
    private VideoTaskDO ensureMyTask(Long taskId) {
        VideoTaskDO task = videoTaskService.getVideoTask(taskId);
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        MerchantDO merchant = userId == null ? null : merchantService.getMerchantByUserId(userId);
        if (task == null || merchant == null || !merchant.getId().equals(task.getMerchantId())) {
            throw exception(AI_VIDEO_MERCHANT_NOT_FOUND);
        }
        return task;
    }

    @PostMapping("/register")
    @Operation(summary = "注册客户端任务（图片已就绪，仅落库）")
    public CommonResult<Long> register(@Valid @RequestBody RegisterReqVO req) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        MerchantDO merchant = merchantService.getMerchantByUserId(userId);
        if (merchant == null) {
            // 没有商户身份直接抛业务码，不再用 merchantId=0 落孤儿数据
            throw exception(AI_VIDEO_MERCHANT_NOT_FOUND);
        }
        Long dbId = videoTaskService.registerClientTask(req.getTitle(), req.getDescription(),
                req.getImageUrls(), merchant.getId(), userId);
        return success(dbId);
    }

    @PutMapping("/sync-status")
    @Operation(summary = "同步客户端任务最终状态到数据库")
    public CommonResult<Boolean> syncStatus(@Valid @RequestBody SyncStatusReqVO req) {
        int dbStatus = req.getClientStatus() >= 4
                ? (req.getClientStatus() == 5 ? VideoTaskStatusEnum.FAILED.getStatus() : VideoTaskStatusEnum.COMPLETED.getStatus())
                : VideoTaskStatusEnum.PROCESSING.getStatus();
        videoTaskService.syncClientTaskStatus(req.getDbId(), dbStatus, req.getVideoUrl(), req.getFailReason());
        return success(true);
    }

    @Data
    static class RegisterReqVO {
        @NotEmpty(message = "图片列表不能为空")
        private List<String> imageUrls;
        @NotBlank(message = "任务描述不能为空")
        private String description;
        private String title;
    }

    @Data
    static class SyncStatusReqVO {
        private Long dbId;
        private Integer clientStatus;
        private String videoUrl;
        private String failReason;
    }

}
