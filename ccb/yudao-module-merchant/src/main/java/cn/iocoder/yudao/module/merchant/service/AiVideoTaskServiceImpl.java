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
 * <p>通过 {@link AiVideoTaskCreatedEvent} 事件与 video 模块解耦，
 * 避免 merchant ↔ video 循环依赖。</p>
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
        // 检查配额
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

        // 发布事件，由 video 模块监听并异步触发文案生成（解耦循环依赖）
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
    public void confirmCopywriting(AppAiVideoConfirmReqVO reqVO, Long userId) {
        AiVideoTaskDO task = aiVideoTaskMapper.selectById(reqVO.getTaskId());
        if (task == null) {
            throw exception(AI_VIDEO_TASK_NOT_FOUND);
        }
        if (!userId.equals(task.getUserId())) {
            throw exception(AI_VIDEO_NO_PERMISSION);
        }
        if (task.getStatus() != STATUS_WAIT_CONFIRM) {
            throw exception(AI_VIDEO_STATUS_INVALID);
        }

        AiVideoTaskDO update = new AiVideoTaskDO();
        update.setId(task.getId());
        update.setFinalCopywriting(reqVO.getFinalCopywriting());
        update.setBgmId(reqVO.getBgmId());
        update.setStatus(STATUS_GENERATING);
        aiVideoTaskMapper.updateById(update);
        log.info("[confirmCopywriting] 触发视频合成 taskId={}", task.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onTaskComplete(Long taskId, Boolean success, String videoUrl, String coverUrl, String failReason) {
        AiVideoTaskDO task = aiVideoTaskMapper.selectById(taskId);
        if (task == null) {
            log.warn("[onTaskComplete] 任务不存在 taskId={}", taskId);
            return;
        }

        AiVideoTaskDO update = new AiVideoTaskDO();
        update.setId(taskId);

        if (Boolean.TRUE.equals(success)) {
            update.setStatus(STATUS_COMPLETED);
            update.setVideoUrl(videoUrl);
            update.setCoverUrl(coverUrl);

            // 原子扣减配额，幂等保护
            if (!Boolean.TRUE.equals(task.getQuotaDeducted())) {
                TenantSubscriptionDO subscription = tenantSubscriptionMapper.selectByTenantId(task.getTenantId());
                if (subscription != null) {
                    int affected = tenantSubscriptionMapper.incrementAiVideoUsedAtomic(subscription.getId());
                    if (affected > 0) {
                        log.info("[onTaskComplete] 扣减配额成功 taskId={}, tenantId={}", taskId, task.getTenantId());
                    } else {
                        log.warn("[onTaskComplete] 配额扣减冲突，忽略 taskId={}", taskId);
                    }
                }
                update.setQuotaDeducted(true);
            }
        } else {
            update.setStatus(STATUS_FAILED);
            update.setFailReason(failReason);
        }

        aiVideoTaskMapper.updateById(update);
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
