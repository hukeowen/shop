package cn.iocoder.yudao.module.merchant.service;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppAiVideoConfirmReqVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.AppAiVideoCreateReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.AiVideoTaskDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.TenantSubscriptionDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.AiVideoTaskMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.TenantSubscriptionMapper;
import cn.iocoder.yudao.module.merchant.event.AiVideoCopywritingConfirmedEvent;
import cn.iocoder.yudao.module.merchant.event.AiVideoTaskCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.*;

/**
 * AI 成片任务 Service 实现
 *
 * <p>流程（生产版）：
 * <pre>
 *   用户上传图片+描述
 *      │ createTask（STATUS_GENERATING_COPY=1）
 *      ▼ 发 AiVideoTaskCreatedEvent
 *   video 模块 Listener 调 LLM 生成文案
 *      │ 回调 onCopywritingGenerated（STATUS_WAIT_CONFIRM=2）
 *      ▼
 *   用户预览文案 → 确认（可修改）
 *      │ confirmCopywriting（STATUS_GENERATING=3）
 *      ▼ 发 AiVideoCopywritingConfirmedEvent
 *   video 模块 Listener 调 TTS + Seedance 生成视频 → 落 OSS
 *      │ 回调 onTaskComplete（STATUS_COMPLETED=4 或 STATUS_FAILED=5）
 *      ▼
 *   用户下载 / 发布抖音
 * </pre>
 *
 * <p><b>事务 &amp; 事件</b>：事件在发布方事务内发布，监听器使用
 * {@code @TransactionalEventListener(AFTER_COMMIT)}，保证仅事务成功提交后触发。
 * 回滚场景不会产生幽灵任务。</p>
 */
@Service
@Validated
@Slf4j
public class AiVideoTaskServiceImpl implements AiVideoTaskService {

    /** 1 - 文案生成中 */
    private static final int STATUS_GENERATING_COPY = 1;
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
    private MerchantService merchantService;
    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTask(AppAiVideoCreateReqVO reqVO, Long tenantId, Long userId) {
        // 1. 快速失败校验配额（真正的原子扣减在 onTaskComplete 执行，由 SQL 谓词保证不超卖）
        TenantSubscriptionDO subscription = tenantSubscriptionMapper.selectByTenantId(tenantId);
        if (subscription == null || subscription.getAiVideoQuota() <= subscription.getAiVideoUsed()) {
            throw exception(AI_VIDEO_QUOTA_EXHAUSTED);
        }

        // 2. 校验商户入驻
        MerchantDO merchant = merchantService.getMerchantByUserId(userId);
        if (merchant == null) {
            throw exception(AI_VIDEO_MERCHANT_NOT_FOUND);
        }

        // 3. 创建任务记录（状态：文案生成中）
        AiVideoTaskDO task = AiVideoTaskDO.builder()
                .userId(userId)
                .imageUrls(reqVO.getImageUrls())
                .userDescription(reqVO.getUserDescription())
                .status(STATUS_GENERATING_COPY)
                .quotaDeducted(false)
                .build();
        aiVideoTaskMapper.insert(task);
        log.info("[createTask] 创建AI成片任务 id={}, tenantId={}, userId={}",
                task.getId(), tenantId, userId);

        // 4. 发布事件，由 video 模块通过 @TransactionalEventListener(AFTER_COMMIT) 监听
        eventPublisher.publishEvent(new AiVideoTaskCreatedEvent(
                this, task.getId(), reqVO.getImageUrls(), reqVO.getUserDescription(),
                merchant.getId(), userId));

        return task.getId();
    }

    @Override
    public AiVideoTaskDO getTask(Long id, Long userId) {
        AiVideoTaskDO task = aiVideoTaskMapper.selectById(id);
        if (task == null) {
            throw exception(AI_VIDEO_TASK_NOT_FOUND);
        }
        if (!userId.equals(task.getUserId())) {
            throw exception(AI_VIDEO_NO_PERMISSION);
        }
        return task;
    }

    // ==================== 阶段 1：LLM 文案回调 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onCopywritingGenerated(Long taskId, List<String> copywriting) {
        if (copywriting == null || copywriting.isEmpty()) {
            throw new IllegalArgumentException("copywriting 不能为空，请调用 onCopywritingFailed");
        }
        AiVideoTaskDO task = aiVideoTaskMapper.selectById(taskId);
        if (task == null) {
            log.warn("[onCopywritingGenerated] 任务不存在 taskId={}", taskId);
            throw exception(AI_VIDEO_TASK_NOT_FOUND);
        }

        // 先写 ai_copywriting 字段
        AiVideoTaskDO payload = new AiVideoTaskDO();
        payload.setId(taskId);
        payload.setAiCopywriting(copywriting);
        aiVideoTaskMapper.updateById(payload);

        // 乐观并发：仅在 status=GENERATING_COPY 时才推进到 WAIT_CONFIRM
        // 若被用户中途取消或重复回调（status 已为其他值），本次 update 失败，记录日志即可
        int updated = aiVideoTaskMapper.updateStatusIfMatch(
                taskId, task.getUserId(), STATUS_GENERATING_COPY, STATUS_WAIT_CONFIRM);
        if (updated == 0) {
            log.warn("[onCopywritingGenerated] 状态推进失败（可能已被用户确认或重复回调），taskId={}, currentStatus={}",
                    taskId, task.getStatus());
            return;
        }
        log.info("[onCopywritingGenerated] 文案已写入，等待用户确认 taskId={}, lines={}",
                taskId, copywriting.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onCopywritingFailed(Long taskId, String failReason) {
        AiVideoTaskDO task = aiVideoTaskMapper.selectById(taskId);
        if (task == null) {
            log.warn("[onCopywritingFailed] 任务不存在 taskId={}", taskId);
            return;
        }
        AiVideoTaskDO payload = new AiVideoTaskDO();
        payload.setId(taskId);
        payload.setStatus(STATUS_FAILED);
        payload.setFailReason(truncate(failReason));
        aiVideoTaskMapper.updateById(payload);
        log.warn("[onCopywritingFailed] 文案生成失败 taskId={}, reason={}", taskId, failReason);
    }

    // ==================== 阶段 2：用户确认文案 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmCopywriting(AppAiVideoConfirmReqVO reqVO, Long userId) {
        AiVideoTaskDO task = aiVideoTaskMapper.selectById(reqVO.getTaskId());
        if (task == null) {
            throw exception(AI_VIDEO_TASK_NOT_FOUND);
        }
        if (!userId.equals(task.getUserId())) {
            throw exception(AI_VIDEO_NO_PERMISSION);
        }

        // 文案为空时复用 AI 生成结果；否则使用用户编辑后的版本
        List<String> finalLines = (reqVO.getFinalCopywriting() != null
                && !reqVO.getFinalCopywriting().isEmpty())
                ? reqVO.getFinalCopywriting()
                : task.getAiCopywriting();
        if (finalLines == null || finalLines.isEmpty()) {
            throw exception(AI_VIDEO_STATUS_INVALID);
        }

        // 先写入确认数据（文案/BGM），再乐观并发推进状态
        AiVideoTaskDO payload = new AiVideoTaskDO();
        payload.setId(task.getId());
        payload.setFinalCopywriting(finalLines);
        payload.setBgmId(reqVO.getBgmId());
        aiVideoTaskMapper.updateById(payload);

        // 乐观并发：仅在 status=WAIT_CONFIRM 时推进到 GENERATING，防止并发双击/重复触发
        int updated = aiVideoTaskMapper.updateStatusIfMatch(
                task.getId(), userId, STATUS_WAIT_CONFIRM, STATUS_GENERATING);
        if (updated == 0) {
            throw exception(AI_VIDEO_STATUS_INVALID);
        }

        // 发布文案确认事件，video 模块监听后启动视频合成（事务提交后才触发）
        eventPublisher.publishEvent(new AiVideoCopywritingConfirmedEvent(
                this, task.getId(), finalLines, reqVO.getBgmId(), userId));

        log.info("[confirmCopywriting] 文案已确认，待 video 模块合成 taskId={}", task.getId());
    }

    // ==================== 阶段 3：视频完成回调 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onTaskComplete(Long taskId, Boolean success, String videoUrl, String coverUrl, String failReason) {
        AiVideoTaskDO task = aiVideoTaskMapper.selectById(taskId);
        if (task == null) {
            // 抛异常而非静默返回，让上游 video 模块能够记录/重试/告警
            log.warn("[onTaskComplete] 任务不存在 taskId={}", taskId);
            throw exception(AI_VIDEO_TASK_NOT_FOUND);
        }

        AiVideoTaskDO update = new AiVideoTaskDO();
        update.setId(taskId);

        if (Boolean.TRUE.equals(success)) {
            update.setStatus(STATUS_COMPLETED);
            update.setVideoUrl(videoUrl);
            update.setCoverUrl(coverUrl);
            aiVideoTaskMapper.updateById(update);

            // 幂等原子扣减配额：
            //  1) 先 CAS 置 ai_video_task.quota_deducted=true（仅 false→true），只有首次成功才会继续
            //  2) 再 UPDATE tenant_subscription 带 tenant_id 和 used<quota 谓词，保证跨租户隔离且不超卖
            int cas = aiVideoTaskMapper.markQuotaDeductedAtomic(taskId);
            if (cas != 1) {
                log.info("[onTaskComplete] 配额已扣减过（幂等），taskId={}", taskId);
                return;
            }
            TenantSubscriptionDO subscription = tenantSubscriptionMapper.selectByTenantId(task.getTenantId());
            if (subscription == null) {
                log.warn("[onTaskComplete] 订阅记录不存在，跳过配额扣减 taskId={}, tenantId={}",
                        taskId, task.getTenantId());
                return;
            }
            int affected = tenantSubscriptionMapper.incrementAiVideoUsedAtomic(
                    subscription.getId(), task.getTenantId());
            if (affected == 1) {
                log.info("[onTaskComplete] 扣减配额成功 taskId={}, tenantId={}", taskId, task.getTenantId());
            } else {
                log.warn("[onTaskComplete] 扣减配额失败（配额耗尽或 tenant 不匹配），taskId={}, tenantId={}",
                        taskId, task.getTenantId());
            }
        } else {
            update.setStatus(STATUS_FAILED);
            update.setFailReason(truncate(failReason));
            aiVideoTaskMapper.updateById(update);
        }

        log.info("[onTaskComplete] 任务回调处理完成 taskId={}, success={}", taskId, success);
    }

    // ==================== 查询 ====================

    @Override
    public PageResult<AiVideoTaskDO> getTaskPage(Long userId, PageParam pageParam) {
        return aiVideoTaskMapper.selectPage(userId, pageParam);
    }

    @Override
    public TenantSubscriptionDO getQuota(Long tenantId) {
        return tenantSubscriptionMapper.selectByTenantId(tenantId);
    }

    // ==================== 私有方法 ====================

    /** fail_reason 字段长度 200，超长截断避免 DB 报错 */
    private static String truncate(String reason) {
        if (reason == null) return null;
        return reason.length() > 200 ? reason.substring(0, 200) : reason;
    }

}
