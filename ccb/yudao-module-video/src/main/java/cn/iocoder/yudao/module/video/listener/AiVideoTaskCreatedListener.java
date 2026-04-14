package cn.iocoder.yudao.module.video.listener;

import cn.iocoder.yudao.module.merchant.event.AiVideoTaskCreatedEvent;
import cn.iocoder.yudao.module.video.controller.admin.vo.VideoTaskCreateReqVO;
import cn.iocoder.yudao.module.video.service.VideoTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * AI成片任务创建事件监听器
 *
 * <p>监听 merchant 模块发布的 {@link AiVideoTaskCreatedEvent}，
 * 异步触发视频模块的文案生成流程。</p>
 */
@Component
@Slf4j
public class AiVideoTaskCreatedListener {

    @Resource
    private VideoTaskService videoTaskService;

    @Async
    @EventListener
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
            log.error("[onAiVideoTaskCreated] 触发文案生成失败 aiVideoTaskId={}", event.getAiVideoTaskId(), e);
        }
    }

}
