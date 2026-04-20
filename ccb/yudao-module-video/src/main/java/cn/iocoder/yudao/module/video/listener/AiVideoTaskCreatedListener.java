package cn.iocoder.yudao.module.video.listener;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import cn.iocoder.yudao.module.merchant.event.AiVideoCopywritingConfirmedEvent;
import cn.iocoder.yudao.module.merchant.event.AiVideoTaskCreatedEvent;
import cn.iocoder.yudao.module.merchant.service.AiVideoTaskService;
import cn.iocoder.yudao.module.merchant.service.MerchantService;
import cn.iocoder.yudao.module.video.service.CopywritingService;
import cn.iocoder.yudao.module.video.service.VideoGenerateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import javax.annotation.Resource;
import java.util.List;

/**
 * AI 成片事件监听器（生产版）
 *
 * <p>把 merchant 模块的两个事件分别落到完整产线：</p>
 * <ul>
 *     <li>{@link AiVideoTaskCreatedEvent} —— 任务创建：调豆包 LLM 生成逐句文案 → 回写 merchant
 *         (status: 1→2 等待确认)</li>
 *     <li>{@link AiVideoCopywritingConfirmedEvent} —— 用户确认文案：TTS + Seedance 图生视频 →
 *         视频持久化到 OSS → 回写 merchant (status: 3→4 完成 / 5 失败)</li>
 * </ul>
 *
 * <p><b>事务对齐</b>：使用 {@link TransactionalEventListener} + {@code AFTER_COMMIT}，仅在发布方
 * 事务成功提交后处理；叠加 {@link Async} 释放发布方线程，外部 API 同步等待都不阻塞 HTTP 请求。</p>
 *
 * <p><b>失败语义</b>：监听器异常不会回滚发布方事务（事务已提交）。所有失败必须显式调用
 * {@code aiVideoTaskService.onCopywritingFailed} 或 {@code onTaskComplete(false, ...)}，
 * 让 merchant 把任务状态置为 5=失败，否则前端会一直停留在"生成中"。</p>
 */
@Component
@Slf4j
public class AiVideoTaskCreatedListener {

    @Resource
    private AiVideoTaskService aiVideoTaskService;
    @Resource
    private CopywritingService copywritingService;
    @Resource
    private VideoGenerateService videoGenerateService;
    @Resource
    private MerchantService merchantService;

    // ==================== 阶段 1：LLM 文案生成 ====================

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAiVideoTaskCreated(AiVideoTaskCreatedEvent event) {
        Long taskId = event.getAiVideoTaskId();
        log.info("[onAiVideoTaskCreated] 收到AI成片任务事件 taskId={}, merchantId={}",
                taskId, event.getMerchantId());
        try {
            String shopName = resolveShopName(event.getMerchantId());
            List<String> lines = copywritingService.generateCopywriting(
                    shopName, event.getUserDescription());
            if (lines == null || lines.isEmpty()) {
                TenantUtils.executeIgnore(() -> aiVideoTaskService.onCopywritingFailed(taskId, "LLM 生成文案为空"));
                return;
            }
            // ai_video_task 是租户隔离表，@Async 切线程后租户上下文丢失，回写必须 executeIgnore
            TenantUtils.executeIgnore(() -> aiVideoTaskService.onCopywritingGenerated(taskId, lines));
        } catch (Exception e) {
            log.error("[onAiVideoTaskCreated] 文案生成失败 taskId={}", taskId, e);
            try {
                TenantUtils.executeIgnore(() -> aiVideoTaskService.onCopywritingFailed(taskId,
                        "AI 文案生成失败：" + safeMessage(e)));
            } catch (Exception inner) {
                log.error("[onAiVideoTaskCreated] 回写失败状态本身异常 taskId={}", taskId, inner);
            }
        }
    }

    // ==================== 阶段 2：视频合成 ====================

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCopywritingConfirmed(AiVideoCopywritingConfirmedEvent event) {
        Long taskId = event.getAiVideoTaskId();
        log.info("[onCopywritingConfirmed] 收到文案确认事件 taskId={}, lines={}",
                taskId, event.getFinalCopywriting().size());
        try {
            // 1. 取任务原始数据（图片列表）；ai_video_task 为租户隔离表，@Async 下需 executeIgnore
            cn.iocoder.yudao.module.merchant.dal.dataobject.AiVideoTaskDO task =
                    TenantUtils.executeIgnore(() ->
                            aiVideoTaskService.getTask(taskId, event.getUserId()));

            // 2. Seedance 图生视频（异步任务 + 内部轮询）
            String remoteVideoUrl = videoGenerateService.generateVideoFromImages(
                    task.getImageUrls(), event.getFinalCopywriting());

            // 3. 落到自有 OSS（避免 Seedance 临时 URL 24h 过期）
            String ossVideoUrl = videoGenerateService.persistToOss(remoteVideoUrl);

            // 4. （可选）TTS 旁路上传，便于后续配音替换；失败仅记日志，不阻塞主流程
            try {
                String text = String.join(" ", event.getFinalCopywriting());
                String audioUrl = videoGenerateService.textToSpeech(text);
                log.info("[onCopywritingConfirmed] TTS 已生成 taskId={}, audioUrl={}", taskId, audioUrl);
            } catch (Exception ttsEx) {
                log.warn("[onCopywritingConfirmed] TTS 旁路失败（不阻塞主流程） taskId={}", taskId, ttsEx);
            }

            // 5. 封面：取首张原图作为封面（Seedance 暂未提供 cover_url 字段）
            String coverUrl = task.getImageUrls() != null && !task.getImageUrls().isEmpty()
                    ? task.getImageUrls().get(0) : null;

            TenantUtils.executeIgnore(() -> aiVideoTaskService.onTaskComplete(
                    taskId, true, ossVideoUrl, coverUrl, null));
        } catch (Exception e) {
            log.error("[onCopywritingConfirmed] 视频合成失败 taskId={}", taskId, e);
            try {
                TenantUtils.executeIgnore(() -> aiVideoTaskService.onTaskComplete(
                        taskId, false, null, null, "视频合成失败：" + safeMessage(e)));
            } catch (Exception inner) {
                log.error("[onCopywritingConfirmed] 回写失败状态本身异常 taskId={}", taskId, inner);
            }
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 通过 merchantId 反查店铺名。
     *
     * <p>监听器在 @Async 切线程后 TenantContextHolder 已清空，不能用 TenantUtils；
     * merchant_info 表为租户隔离表，这里通过 TenantIgnore 语义读取（见 MerchantService 实现）。</p>
     */
    private String resolveShopName(Long merchantId) {
        if (merchantId == null) return "";
        try {
            // @Async 切线程后 TenantContextHolder 已清空，merchant_info 表受 MP 租户拦截器过滤；
            // 用 TenantUtils.executeIgnore 绕过 tenant_id 谓词，避免查不到。
            MerchantDO merchant = TenantUtils.executeIgnore(() -> merchantService.getMerchant(merchantId));
            return merchant != null && merchant.getName() != null ? merchant.getName() : "";
        } catch (Exception e) {
            log.debug("[resolveShopName] 解析店铺名失败，使用空 shopName merchantId={}", merchantId, e);
            return "";
        }
    }

    private static String safeMessage(Throwable e) {
        String msg = e.getMessage();
        if (msg == null) {
            msg = e.getClass().getSimpleName();
        }
        // 失败原因字段长度 200，预留缩略
        return msg.length() > 180 ? msg.substring(0, 180) : msg;
    }

}
