package cn.iocoder.yudao.module.video.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.merchant.dal.dataobject.AiVideoTaskDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import cn.iocoder.yudao.module.merchant.service.AiVideoTaskService;
import cn.iocoder.yudao.module.merchant.service.MerchantService;
import cn.iocoder.yudao.module.video.service.DouyinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.AI_VIDEO_MERCHANT_NOT_FOUND;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.AI_VIDEO_URL_MISSING;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 商户小程序 - 抖音授权与发布
 *
 * <p>从 AppMerchantAiVideoController 迁移至 video 模块，
 * 避免 merchant &lt;-&gt; video 循环依赖。</p>
 */
@Tag(name = "商户小程序 - 抖音授权与发布")
@RestController
@RequestMapping("/merchant/mini/ai-video")
@Validated
@Slf4j
public class AppMerchantDouyinController {

    @Resource
    private DouyinService douyinService;
    @Resource
    private MerchantService merchantService;
    @Resource
    private AiVideoTaskService aiVideoTaskService;

    @GetMapping("/douyin/auth-url")
    @Operation(summary = "获取抖音 OAuth 授权URL")
    @Parameter(name = "redirectUri", description = "授权回调地址", required = true)
    public CommonResult<String> getDouyinAuthUrl(@RequestParam("redirectUri") String redirectUri) {
        MerchantDO merchant = getMerchantOrThrow();
        return success(douyinService.getOAuthUrl(merchant.getId(), redirectUri));
    }

    @PostMapping("/douyin/publish")
    @Operation(summary = "发布视频到抖音")
    @Parameter(name = "taskId", description = "AI成片任务ID", required = true)
    public CommonResult<Boolean> publishToDouyin(@RequestParam("taskId") Long taskId) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        AiVideoTaskDO task = aiVideoTaskService.getTask(taskId, userId);
        if (task.getVideoUrl() == null || task.getVideoUrl().isEmpty()) {
            throw exception(AI_VIDEO_URL_MISSING);
        }

        MerchantDO merchant = getMerchantOrThrow();
        String douyinVideoId = douyinService.uploadVideo(merchant.getId(), task.getVideoUrl());
        String itemId = douyinService.publishVideo(merchant.getId(), douyinVideoId, task.getUserDescription());
        log.info("[publishToDouyin] taskId={}, douyinItemId={}", taskId, itemId);
        return success(true);
    }

    private MerchantDO getMerchantOrThrow() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        MerchantDO merchant = merchantService.getMerchantByUserId(userId);
        if (merchant == null) {
            throw exception(AI_VIDEO_MERCHANT_NOT_FOUND);
        }
        return merchant;
    }

}
