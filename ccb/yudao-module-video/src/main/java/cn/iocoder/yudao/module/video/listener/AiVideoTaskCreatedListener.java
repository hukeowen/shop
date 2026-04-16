package cn.iocoder.yudao.module.video.listener;

import cn.iocoder.yudao.module.merchant.event.AiVideoCopywritingConfirmedEvent;
import cn.iocoder.yudao.module.merchant.event.AiVideoTaskCreatedEvent;
import cn.iocoder.yudao.module.video.controller.admin.vo.VideoTaskCreateReqVO;
import cn.iocoder.yudao.module.video.service.VideoTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import javax.annotation.Resource;

/**
 * AI 成片事件监听器
 *
 * <p>监听 merchant 模块发布的两个事件：
 * <ul>
 *     <li>{@link AiVideoTaskCreatedEvent} —— 任务创建，触发视频生成流程</li>
 *     <li>{@link AiVideoCopywritingConfirmedEvent} —— 用户确认文案（当前 video 模块为单次全流程，
 *         此事件暂仅记录日志；待 video 模块支持"文案→审核→合成"分步流程后替换实现）</li>
 * </ul>
 *
 * <p><b>事务对齐</b>：使用 {@link TransactionalEventListener} + {@code AFTER_COMMIT}
 * 保证仅在发布方（merchant）事务成功提交后才处理，否则会出现"幽灵任务"（回滚后仍下发下游）。
 * 叠加 {@link Async} 以避免阻塞发布方提交后的返回链路。</p>
 */
@Component
@Slf4j
public class AiVideoTaskCreatedListener {

    @Resource
    private VideoTaskService videoTaskService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAiVideoTaskCreated(AiVideoTaskCreatedEvent event) {
        log.info("[onAiVideoTaskCreated] 收到AI成片任务事件 aiVideoTaskId={}, merchantId={}",
                event.getAiVideoTaskId(), event.getMerchantId());
        try {
            VideoTaskCreateReqVO reqVO = new VideoTaskCreateReqVO();
            reqVO.setTitle(event.getUserDescription());
            reqVO.setDescription(event.getUserDescription());
            reqVO.setImageUrls(event.getImageUrls());
            videoTaskService.createVideoTask(reqVO, event.getMerchantId(), event.getUserId());
        } catch (Exception e) {
            // 监听器异常不会回滚发布方事务（已提交），此处仅记录；
            // 失败恢复依赖上层重试/补偿，例如调度任务扫描 status=0 超过阈值的记录
            log.error("[onAiVideoTaskCreated] 触发文案生成失败 aiVideoTaskId={}", event.getAiVideoTaskId(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCopywritingConfirmed(AiVideoCopywritingConfirmedEvent event) {
        // 当前 video 模块 createVideoTask 已启动完整合成流程（PENDING→PROCESSING→COMPLETED），
        // 此事件暂无后续动作；待 video 模块实现"文案先行→等待确认→合成视频"分步流程后，
        // 在此调用 videoTaskService.startVideoGeneration(...) 并移除此 TODO。
        log.info("[onCopywritingConfirmed] 收到文案确认事件 aiVideoTaskId={}（当前 video 流程一次性完成，暂不处理）",
                event.getAiVideoTaskId());
    }

}
