package cn.iocoder.yudao.module.video.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.video.controller.admin.vo.*;
import cn.iocoder.yudao.module.video.dal.dataobject.VideoTaskDO;
import cn.iocoder.yudao.module.video.service.VideoTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - AI视频任务")
@RestController
@RequestMapping("/video/task")
@Validated
public class VideoTaskController {

    @Resource
    private VideoTaskService videoTaskService;

    @GetMapping("/page")
    @Operation(summary = "获取视频任务分页")
    @PreAuthorize("@ss.hasPermission('video:task:query')")
    public CommonResult<PageResult<VideoTaskRespVO>> getVideoTaskPage(@Valid VideoTaskPageReqVO pageReqVO) {
        PageResult<VideoTaskDO> pageResult = videoTaskService.getVideoTaskPage(pageReqVO);
        return success(new PageResult<>(pageResult.getList().stream().map(this::convert).collect(Collectors.toList()), pageResult.getTotal()));
    }

    @GetMapping("/get")
    @Operation(summary = "获取视频任务详情")
    @Parameter(name = "id", description = "任务编号", required = true)
    @PreAuthorize("@ss.hasPermission('video:task:query')")
    public CommonResult<VideoTaskRespVO> getVideoTask(@RequestParam("id") Long id) {
        VideoTaskDO task = videoTaskService.getVideoTask(id);
        return success(convert(task));
    }

    @PostMapping("/publish-douyin")
    @Operation(summary = "发布视频到抖音")
    @Parameter(name = "id", description = "任务编号", required = true)
    @PreAuthorize("@ss.hasPermission('video:task:publish')")
    public CommonResult<Boolean> publishToDouyin(@RequestParam("id") Long id) {
        videoTaskService.publishToDouyin(id);
        return success(true);
    }

    private VideoTaskRespVO convert(VideoTaskDO task) {
        if (task == null) {
            return null;
        }
        VideoTaskRespVO respVO = new VideoTaskRespVO();
        respVO.setId(task.getId());
        respVO.setMerchantId(task.getMerchantId());
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
        return respVO;
    }

}
