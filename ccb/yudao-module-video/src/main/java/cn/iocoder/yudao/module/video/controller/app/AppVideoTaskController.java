package cn.iocoder.yudao.module.video.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import cn.iocoder.yudao.module.video.controller.admin.vo.VideoTaskCreateReqVO;
import cn.iocoder.yudao.module.video.controller.admin.vo.VideoTaskRespVO;
import cn.iocoder.yudao.module.video.dal.dataobject.VideoTaskDO;
import cn.iocoder.yudao.module.video.enums.VideoTaskStatusEnum;
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
