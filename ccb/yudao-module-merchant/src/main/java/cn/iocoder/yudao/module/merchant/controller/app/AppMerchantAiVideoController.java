package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppAiVideoConfirmReqVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppAiVideoCreateReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.AiVideoTaskDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.TenantSubscriptionDO;
import cn.iocoder.yudao.module.merchant.service.AiVideoTaskService;
import cn.iocoder.yudao.module.merchant.service.MerchantService;
import cn.iocoder.yudao.module.video.service.DouyinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.*;

/**
 * 商户小程序 - AI成片（#27 #28）
 *
 * <p>业务逻辑委托给 {@link AiVideoTaskService}；
 * 抖音发布委托给 DouyinService。</p>
 */
@Tag(name = "商户小程序 - AI成片")
@RestController
@RequestMapping("/merchant/mini/ai-video")
@Validated
@Slf4j
public class AppMerchantAiVideoController {

    /** 内部回调接口鉴权 Token（配置项：merchant.internal-token） */
    @Value("${merchant.internal-token:}")
    private String internalToken;

    @Resource
    private AiVideoTaskService aiVideoTaskService;
    @Resource
    private MerchantService merchantService;
    @Resource
    private DouyinService douyinService;

    // ==================== #27 上传图片+输入描述+确认文案 ====================

    @PostMapping("/create")
    @Operation(summary = "创建AI成片任务（上传图片+描述）")
    public CommonResult<Long> createTask(@Valid @RequestBody AppAiVideoCreateReqVO reqVO) {
        Long tenantId = TenantContextHolder.getTenantId();
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return success(aiVideoTaskService.createTask(reqVO, tenantId, userId));
    }

    @GetMapping("/get")
    @Operation(summary = "获取任务详情（含AI生成的文案）")
    @Parameter(name = "id", description = "任务ID", required = true)
    public CommonResult<AiVideoTaskDO> getTask(@RequestParam("id") Long id) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return success(aiVideoTaskService.getTask(id, userId));
    }

    @PostMapping("/confirm")
    @Operation(summary = "确认文案并开始视频合成")
    public CommonResult<Boolean> confirmCopywriting(@Valid @RequestBody AppAiVideoConfirmReqVO reqVO) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        aiVideoTaskService.confirmCopywriting(reqVO, userId);
        return success(true);
    }

    // ==================== 抖音授权与发布 ====================

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
        log.info("[publishToDouyin] 发布成功 taskId={}, douyinItemId={}", taskId, itemId);
        return success(true);
    }

    // ==================== 视频生成完成回调（内部调用） ====================

    /**
     * 仅供内部服务调用，必须携带 X-Internal-Token Header 进行鉴权。
     * 配置项：merchant.internal-token（不配置则拒绝所有请求）
     */
    @PostMapping("/complete-callback")
    @Operation(summary = "视频生成完成回调（内部调用，需携带 X-Internal-Token）")
    @PermitAll
    public CommonResult<Boolean> onTaskComplete(
            @RequestHeader("X-Internal-Token") String token,
            @RequestParam("taskId") Long taskId,
            @RequestParam("success") Boolean success,
            @RequestParam(value = "videoUrl", required = false) String videoUrl,
            @RequestParam(value = "coverUrl", required = false) String coverUrl,
            @RequestParam(value = "failReason", required = false) String failReason) {
        validateInternalToken(token);
        aiVideoTaskService.onTaskComplete(taskId, success, videoUrl, coverUrl, failReason);
        return success(true);
    }

    // ==================== #28 历史记录+配额 ====================

    @GetMapping("/page")
    @Operation(summary = "分页查询AI成片历史记录")
    public CommonResult<PageResult<AiVideoTaskDO>> getTaskPage(@Valid PageParam pageParam) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return success(aiVideoTaskService.getTaskPage(userId, pageParam));
    }

    @GetMapping("/quota")
    @Operation(summary = "查询AI成片配额（剩余/已用）")
    public CommonResult<TenantSubscriptionDO> getQuota() {
        Long tenantId = TenantContextHolder.getTenantId();
        return success(aiVideoTaskService.getQuota(tenantId));
    }

    @PostMapping("/buy-quota")
    @Operation(summary = "购买AI成片配额（创建待支付订单）")
    @Parameter(name = "count", description = "购买次数", required = true)
    public CommonResult<String> buyQuota(@RequestParam("count") Integer count) {
        // TODO: 创建 PayOrder，在支付成功回调中调用 addQuota(tenantId, count)
        throw exception(AI_VIDEO_PAY_NOT_READY);
    }

    // ==================== 私有方法 ====================

    private MerchantDO getMerchantOrThrow() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        MerchantDO merchant = merchantService.getMerchantByUserId(userId);
        if (merchant == null) {
            throw exception(AI_VIDEO_MERCHANT_NOT_FOUND);
        }
        return merchant;
    }

    private void validateInternalToken(String token) {
        if (internalToken == null || internalToken.isEmpty() || !internalToken.equals(token)) {
            log.warn("[validateInternalToken] 内部接口鉴权失败");
            throw exception(INTERNAL_TOKEN_INVALID);
        }
    }

}
