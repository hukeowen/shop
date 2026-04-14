package cn.iocoder.yudao.module.merchant.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * AI成片任务创建事件
 *
 * <p>merchant 模块发布，video 模块监听并触发文案生成流程。
 * 通过事件解耦，避免 merchant ↔ video 循环依赖。</p>
 */
@Getter
public class AiVideoTaskCreatedEvent extends ApplicationEvent {

    /** merchant 模块的 ai_video_task.id */
    private final Long aiVideoTaskId;
    /** 图片 URL 列表 */
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
        this.imageUrls = imageUrls;
        this.userDescription = userDescription;
        this.merchantId = merchantId;
        this.userId = userId;
    }

}
