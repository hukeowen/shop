package cn.iocoder.yudao.module.video.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.video.controller.admin.vo.VideoTaskCreateReqVO;
import cn.iocoder.yudao.module.video.controller.admin.vo.VideoTaskRespVO;
import cn.iocoder.yudao.module.video.dal.dataobject.VideoTaskDO;
import cn.iocoder.yudao.module.video.service.VideoTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

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
            throw new RuntimeException("请先完成商户入驻");
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

}
