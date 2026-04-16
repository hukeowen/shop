package cn.iocoder.yudao.module.merchant.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Collections;
import java.util.List;

/**
 * AI 成片任务创建事件
 *
 * <p>merchant 模块发布，video 模块监听并触发文案生成流程。
 * 通过事件解耦，避免 merchant ↔ video 循环依赖。</p>
 *
 * <p>配合 {@code @TransactionalEventListener(AFTER_COMMIT)} 使用，确保监听器
 * 在发布方事务提交之后才执行，防止事务回滚后仍触发下游。</p>
 */
@Getter
public class AiVideoTaskCreatedEvent extends ApplicationEvent {

    /** merchant 模块的 ai_video_task.id */
    private final Long aiVideoTaskId;
    /** 图片 URL 列表（不可变副本） */
    private final List<String> imageUrls;
    /** 用户输入描述 */
    private final String userDescription;
    /** 商户 ID */
    private final Long merchantId;
    /** 操作用户 ID */
    private final Long userId;

    public AiVideoTaskCreatedEvent(Object source, Long aiVideoTaskId, List<String> imageUrls,
                                   String userDescription, Long merchantId, Long userId) {
        super(source);
        this.aiVideoTaskId = aiVideoTaskId;
        this.imageUrls = imageUrls == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(imageUrls);
        this.userDescription = userDescription;
        this.merchantId = merchantId;
        this.userId = userId;
    }

}
