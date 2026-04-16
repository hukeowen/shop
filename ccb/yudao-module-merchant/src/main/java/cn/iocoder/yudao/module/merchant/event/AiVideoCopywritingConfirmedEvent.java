package cn.iocoder.yudao.module.merchant.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Collections;
import java.util.List;

/**
 * AI 成片文案确认事件
 *
 * <p>用户在小程序确认最终文案后由 merchant 模块发布，video 模块监听后启动实际
 * 的视频合成流程；通过事件解耦，避免 merchant ↔ video 循环依赖。</p>
 *
 * <p>配合 {@code @TransactionalEventListener(AFTER_COMMIT)} 使用，确保监听器
 * 在发布方事务提交之后才执行，防止事务回滚后仍触发下游。</p>
 */
@Getter
public class AiVideoCopywritingConfirmedEvent extends ApplicationEvent {

    /** merchant 模块的 ai_video_task.id */
    private final Long aiVideoTaskId;
    /** 用户确认后的最终文案逐句列表（不可变副本） */
    private final List<String> finalCopywriting;
    /** 背景音乐 ID，可为 null */
    private final Integer bgmId;
    /** 操作用户 ID */
    private final Long userId;

    public AiVideoCopywritingConfirmedEvent(Object source, Long aiVideoTaskId,
                                            List<String> finalCopywriting, Integer bgmId, Long userId) {
        super(source);
        this.aiVideoTaskId = aiVideoTaskId;
        this.finalCopywriting = finalCopywriting == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(finalCopywriting);
        this.bgmId = bgmId;
        this.userId = userId;
    }

}
