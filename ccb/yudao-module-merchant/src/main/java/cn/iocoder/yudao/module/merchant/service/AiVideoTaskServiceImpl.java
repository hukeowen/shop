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

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.*;

/**
 * AI 成片任务 Service 实现
 *
 * <p>通过 {@link AiVideoTaskCreatedEvent} / {@link AiVideoCopywritingConfirmedEvent}
 * 事件与 video 模块解耦，避免 merchant ↔ video 循环依赖。</p>
 *
 * <p><b>事务与事件时序</b>：事件在事务内发布，但监听器使用
 * {@code @TransactionalEventListener(AFTER_COMMIT)}，保证仅在本方事务成功提交后才触发下游，
 * 回滚场景不会产生幽灵任务。</p>
 */
@Service
@Validated
@Slf4j
public class AiVideoTaskServiceImpl implements AiVideoTaskService {

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
    private MerchantService merchantService;
    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTask(AppAiVideoCreateReqVO reqVO, Long tenantId, Long userId) {
        // 检查配额（快速失败；真正的原子扣减在 onTaskComplete 执行，由 SQL 谓词保证不超卖）
        TenantSubscriptionDO subscription = tenantSubscriptionMapper.selectByTenantId(tenantId);
        if (subscription == null || subscription.getAiVideoQuota() <= subscription.getAiVideoUsed()) {
            throw exception(AI_VIDEO_QUOTA_EXHAUSTED);
        }

        // 校验商户入驻
        MerchantDO merchant = merchantService.getMerchantByUserId(userId);
        if (merchant == null) {
            throw exception(AI_VIDEO_MERCHANT_NOT_FOUND);
        }

        // 创建任务记录
        AiVideoTaskDO task = AiVideoTaskDO.builder()
                .userId(userId)
                .imageUrls(reqVO.getImageUrls())
                .userDescription(reqVO.getUserDescription())
                .status(STATUS_PENDING)
                .quotaDeducted(false)
                .build();
        aiVideoTaskMapper.insert(task);
        log.info("[createTask] 创建AI成片任务 id={}, tenantId={}", task.getId(), tenantId);

        // 发布事件，由 video 模块通过 @TransactionalEventListener(AFTER_COMMIT) 监听
        // Spring 保证在本方事务提交之后才触发监听器，回滚时不触发
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

        // 先写入确认数据（文案/BGM），再通过乐观并发推进状态
        AiVideoTaskDO payload = new AiVideoTaskDO();
        payload.setId(task.getId());
        payload.setFinalCopywriting(reqVO.getFinalCopywriting());
        payload.setBgmId(reqVO.getBgmId());
        aiVideoTaskMapper.updateById(payload);

        // 乐观并发：仅在 status=WAIT_CONFIRM 时推进到 GENERATING，防止并发双击造成重复触发
        int updated = aiVideoTaskMapper.updateStatusIfMatch(
                task.getId(), userId, STATUS_WAIT_CONFIRM, STATUS_GENERATING);
        if (updated == 0) {
            throw exception(AI_VIDEO_STATUS_INVALID);
        }

        // 发布确认事件，video 模块监听后启动视频合成（事务提交后才触发）
        eventPublisher.publishEvent(new AiVideoCopywritingConfirmedEvent(
                this, task.getId(), reqVO.getFinalCopywriting(), reqVO.getBgmId(), userId));

        log.info("[confirmCopywriting] 文案已确认，待 video 模块合成 taskId={}", task.getId());
    }

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
                // 极端情况下订阅已被删除，仅记录日志，不回滚（任务记录已标记完成）
                log.warn("[onTaskComplete] 订阅记录不存在，跳过配额扣减 taskId={}, tenantId={}",
                        taskId, task.getTenantId());
                return;
            }
            int affected = tenantSubscriptionMapper.incrementAiVideoUsedAtomic(
                    subscription.getId(), task.getTenantId());
            if (affected == 1) {
                log.info("[onTaskComplete] 扣减配额成功 taskId={}, tenantId={}", taskId, task.getTenantId());
            } else {
                // 配额已耗尽或 tenant 不匹配 —— 由于 createTask 已校验配额，这里通常意味着超卖竞态，
                // 人工核对后可通过运维工具修正（不回滚任务完成状态）
                log.warn("[onTaskComplete] 扣减配额失败（配额耗尽或 tenant 不匹配），taskId={}, tenantId={}",
                        taskId, task.getTenantId());
            }
        } else {
            update.setStatus(STATUS_FAILED);
            update.setFailReason(failReason);
            aiVideoTaskMapper.updateById(update);
        }

        log.info("[onTaskComplete] 任务回调处理完成 taskId={}, success={}", taskId, success);
    }

    @Override
    public PageResult<AiVideoTaskDO> getTaskPage(Long userId, PageParam pageParam) {
        return aiVideoTaskMapper.selectPage(userId, pageParam);
    }

    @Override
    public TenantSubscriptionDO getQuota(Long tenantId) {
        return tenantSubscriptionMapper.selectByTenantId(tenantId);
    }

}
