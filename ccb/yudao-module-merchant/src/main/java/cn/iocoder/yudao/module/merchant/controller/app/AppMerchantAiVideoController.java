package cn.iocoder.yudao.module.merchant.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppAiVideoConfirmReqVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppAiVideoCreateReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.AiVideoTaskDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.TenantSubscriptionDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.AiVideoTaskMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.TenantSubscriptionMapper;
import cn.iocoder.yudao.module.merchant.service.MerchantService;
import cn.iocoder.yudao.module.video.controller.admin.vo.VideoTaskCreateReqVO;
import cn.iocoder.yudao.module.video.dal.dataobject.VideoTaskDO;
import cn.iocoder.yudao.module.video.service.DouyinService;
import cn.iocoder.yudao.module.video.service.VideoTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 商户小程序 - AI成片（#27 #28）
 *
 * <p>视频生成委托给 yudao-module-video 的 VideoTaskService；
 * 抖音发布委托给 DouyinService。</p>
 */
@Tag(name = "商户小程序 - AI成片")
@RestController
@RequestMapping("/merchant/mini/ai-video")
@Validated
@Slf4j
public class AppMerchantAiVideoController {

    // ---- 任务状态常量（与 AiVideoTaskDO.status 对齐） ----
    /** 0 - 待处理 */
    private static final int STATUS_PENDING = 0;
    /** 2 - 等待用户确认文案 */
    private static final int STATUS_WAIT_CONFIRM = 2;
    /** 3 - 视频合成中 */
    private static final int STATUS_GENERATING = 3;
    /** 4 - 完成 */
    private static final int STATUS_COMPLETED = 4;
    /** 5 - 失败 */
    private static final int STATUS_FAILED = 5;

    @Resource
    private AiVideoTaskMapper aiVideoTaskMapper;
    @Resource
    private TenantSubscriptionMapper tenantSubscriptionMapper;
    @Resource
    private VideoTaskService videoTaskService;
    @Resource
    private DouyinService douyinService;
    @Resource
    private MerchantService merchantService;

    // ==================== #27 上传图片+输入描述+确认文案 ====================

    @PostMapping("/create")
    @Operation(summary = "创建AI成片任务（上传图片+描述）")
    public CommonResult<Long> createTask(@Valid @RequestBody AppAiVideoCreateReqVO reqVO) {
        Long tenantId = TenantContextHolder.getTenantId();
        Long userId = SecurityFrameworkUtils.getLoginUserId();

        // 检查配额
        TenantSubscriptionDO subscription = tenantSubscriptionMapper.selectByTenantId(tenantId);
        if (subscription == null || subscription.getAiVideoQuota() <= subscription.getAiVideoUsed()) {
            throw exception0(1_020_006_000, "AI成片配额已用完，请购买更多配额");
        }

        // 获取商户信息（用于委托视频模块）
        cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO merchant =
                merchantService.getMerchantByUserId(userId);
        if (merchant == null) {
            throw exception0(1_020_006_010, "请先完成商户入驻");
        }

        // 在本模块创建任务记录（保存图片和描述，用于文案确认流程）
        AiVideoTaskDO task = AiVideoTaskDO.builder()
                .userId(userId)
                .imageUrls(reqVO.getImageUrls())
                .userDescription(reqVO.getUserDescription())
                .status(STATUS_PENDING)
                .quotaDeducted(false)
                .build();
        aiVideoTaskMapper.insert(task);
        log.info("[createTask] 创建AI成片任务 id={}, tenantId={}", task.getId(), tenantId);

        // 委托视频模块异步触发文案生成
        VideoTaskCreateReqVO videoReqVO = new VideoTaskCreateReqVO();
        videoReqVO.setTitle(reqVO.getUserDescription());
        videoReqVO.setDescription(reqVO.getUserDescription());
        videoReqVO.setImageUrls(reqVO.getImageUrls());
        videoTaskService.createVideoTask(videoReqVO, merchant.getId(), userId);

        return success(task.getId());
    }

    @GetMapping("/get")
    @Operation(summary = "获取任务详情（含AI生成的文案）")
    @Parameter(name = "id", description = "任务ID", required = true)
    public CommonResult<AiVideoTaskDO> getTask(@RequestParam("id") Long id) {
        AiVideoTaskDO task = aiVideoTaskMapper.selectById(id);
        if (task == null) {
            throw exception0(1_020_006_001, "任务不存在");
        }
        // 任务归属校验：只能查看自己的任务
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (!userId.equals(task.getUserId())) {
            throw exception0(1_020_006_011, "无权查看该任务");
        }
        return success(task);
    }

    @PostMapping("/confirm")
    @Operation(summary = "确认文案并开始视频合成")
    public CommonResult<Boolean> confirmCopywriting(@Valid @RequestBody AppAiVideoConfirmReqVO reqVO) {
        AiVideoTaskDO task = aiVideoTaskMapper.selectById(reqVO.getTaskId());
        if (task == null) {
            throw exception0(1_020_006_001, "任务不存在");
        }
        // 任务归属校验
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (!userId.equals(task.getUserId())) {
            throw exception0(1_020_006_011, "无权操作该任务");
        }
        if (task.getStatus() != STATUS_WAIT_CONFIRM) {
            throw exception0(1_020_006_002, "当前任务状态不支持确认文案");
        }

        // 更新任务：保存最终文案，进入合成阶段
        AiVideoTaskDO update = new AiVideoTaskDO();
        update.setId(task.getId());
        update.setFinalCopywriting(reqVO.getFinalCopywriting());
        update.setBgmId(reqVO.getBgmId());
        update.setStatus(STATUS_GENERATING);
        aiVideoTaskMapper.updateById(update);

        // 委托视频模块异步处理生成（通过回调 /complete-callback 更新状态并扣减配额）
        log.info("[confirmCopywriting] 触发视频合成 taskId={}", task.getId());
        return success(true);
    }

    // ==================== 抖音授权与发布 ====================

    @GetMapping("/douyin/auth-url")
    @Operation(summary = "获取抖音 OAuth 授权URL")
    @Parameter(name = "redirectUri", description = "授权回调地址", required = true)
    public CommonResult<String> getDouyinAuthUrl(@RequestParam("redirectUri") String redirectUri) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO merchant =
                merchantService.getMerchantByUserId(userId);
        if (merchant == null) {
            throw exception0(1_020_006_010, "请先完成商户入驻");
        }
        String authUrl = douyinService.getOAuthUrl(merchant.getId(), redirectUri);
        return success(authUrl);
    }

    @PostMapping("/douyin/publish")
    @Operation(summary = "发布视频到抖音")
    @Parameter(name = "taskId", description = "AI成片任务ID", required = true)
    public CommonResult<Boolean> publishToDouyin(@RequestParam("taskId") Long taskId) {
        AiVideoTaskDO task = aiVideoTaskMapper.selectById(taskId);
        if (task == null) {
            throw exception0(1_020_006_001, "任务不存在");
        }
        // 任务归属校验
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (!userId.equals(task.getUserId())) {
            throw exception0(1_020_006_011, "无权操作该任务");
        }
        if (task.getStatus() != STATUS_COMPLETED) {
            throw exception0(1_020_006_004, "视频尚未生成完成，无法发布");
        }
        if (task.getVideoUrl() == null || task.getVideoUrl().isEmpty()) {
            throw exception0(1_020_006_005, "视频URL不存在，无法发布");
        }

        cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO merchant =
                merchantService.getMerchantByUserId(userId);
        if (merchant == null) {
            throw exception0(1_020_006_010, "请先完成商户入驻");
        }

        // 上传视频到抖音，再发布
        String douyinVideoId = douyinService.uploadVideo(merchant.getId(), task.getVideoUrl());
        String itemId = douyinService.publishVideo(merchant.getId(), douyinVideoId, task.getUserDescription());
        log.info("[publishToDouyin] 发布成功 taskId={}, douyinItemId={}", taskId, itemId);
        return success(true);
    }

    // ==================== 视频生成完成回调（内部调用） ====================

    @PostMapping("/complete-callback")
    @Operation(summary = "视频生成完成回调（内部调用）")
    public CommonResult<Boolean> onTaskComplete(
            @RequestParam("taskId") Long taskId,
            @RequestParam("success") Boolean success,
            @RequestParam(value = "videoUrl", required = false) String videoUrl,
            @RequestParam(value = "coverUrl", required = false) String coverUrl,
            @RequestParam(value = "failReason", required = false) String failReason) {
        AiVideoTaskDO task = aiVideoTaskMapper.selectById(taskId);
        if (task == null) {
            log.warn("[onTaskComplete] 任务不存在 taskId={}", taskId);
            return CommonResult.success(false);
        }

        AiVideoTaskDO update = new AiVideoTaskDO();
        update.setId(taskId);

        if (Boolean.TRUE.equals(success)) {
            update.setStatus(STATUS_COMPLETED);
            update.setVideoUrl(videoUrl);
            update.setCoverUrl(coverUrl);

            // 配额扣减：仅在首次完成时扣减，防止重复扣减
            if (!Boolean.TRUE.equals(task.getQuotaDeducted())) {
                Long tenantId = task.getTenantId();
                TenantSubscriptionDO subscription = tenantSubscriptionMapper.selectByTenantId(tenantId);
                if (subscription != null) {
                    TenantSubscriptionDO quotaUpdate = new TenantSubscriptionDO();
                    quotaUpdate.setId(subscription.getId());
                    quotaUpdate.setAiVideoUsed(subscription.getAiVideoUsed() + 1);
                    tenantSubscriptionMapper.updateById(quotaUpdate);
                    log.info("[onTaskComplete] 扣减配额成功 taskId={}, tenantId={}", taskId, tenantId);
                }
                update.setQuotaDeducted(true);
            }
        } else {
            update.setStatus(STATUS_FAILED);
            update.setFailReason(failReason);
        }

        aiVideoTaskMapper.updateById(update);
        log.info("[onTaskComplete] 任务完成回调处理 taskId={}, success={}", taskId, success);
        return CommonResult.success(true);
    }

    // ==================== #28 历史记录+配额 ====================

    @GetMapping("/page")
    @Operation(summary = "分页查询AI成片历史记录")
    public CommonResult<PageResult<AiVideoTaskDO>> getTaskPage(@Valid PageParam pageParam) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return success(aiVideoTaskMapper.selectPage(userId, pageParam));
    }

    @GetMapping("/quota")
    @Operation(summary = "查询AI成片配额（剩余/已用）")
    public CommonResult<TenantSubscriptionDO> getQuota() {
        Long tenantId = TenantContextHolder.getTenantId();
        return success(tenantSubscriptionMapper.selectByTenantId(tenantId));
    }

    @PostMapping("/buy-quota")
    @Operation(summary = "购买AI成片配额（创建待支付订单）")
    @Parameter(name = "count", description = "购买次数", required = true)
    public CommonResult<String> buyQuota(@RequestParam("count") Integer count) {
        Long tenantId = TenantContextHolder.getTenantId();
        TenantSubscriptionDO subscription = tenantSubscriptionMapper.selectByTenantId(tenantId);
        if (subscription == null) {
            throw exception0(1_020_006_003, "订阅记录不存在");
        }
        // TODO: 创建 PayOrder，在支付成功回调中调用 addQuota(tenantId, count)
        // 暂时返回提示，待支付模块对接后替换
        throw exception0(1_020_006_008, "配额购买功能正在对接支付系统，敬请期待");
    }

}
