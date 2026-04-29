package cn.iocoder.yudao.module.video.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * AI 视频任务异步线程池
 *
 * <p>默认 Spring @Async 用 SimpleAsyncTaskExecutor，每个任务起一个新线程，
 * 多商户并发下单时会爆线程数。本配置提供专用线程池：</p>
 * <ul>
 *     <li>core=2 / max=4 — 同时最多 4 个 AI 视频任务在跑</li>
 *     <li>queue=50 — 排队上限，防止超量请求把内存吃光</li>
 *     <li>拒绝策略 CallerRunsPolicy — 队列满时调用方线程执行（HTTP 入口同步等，
 *         相当于背压；其他场景由 Future 超时兜底）</li>
 *     <li>thread-name-prefix=ai-video- — 日志里好区分</li>
 * </ul>
 *
 * <p>使用：监听器加 {@code @Async("aiVideoTaskExecutor")}</p>
 *
 * <p>调优：通过 {@code merchant.ai-video.async.*} 配置项覆盖默认值，
 * 重启生效。</p>
 */
@Configuration
public class AiVideoAsyncConfig {

    @Value("${merchant.ai-video.async.core-pool-size:2}")
    private int corePoolSize;
    @Value("${merchant.ai-video.async.max-pool-size:4}")
    private int maxPoolSize;
    @Value("${merchant.ai-video.async.queue-capacity:50}")
    private int queueCapacity;
    @Value("${merchant.ai-video.async.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    @Bean("aiVideoTaskExecutor")
    public ThreadPoolTaskExecutor aiVideoTaskExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(corePoolSize);
        exec.setMaxPoolSize(maxPoolSize);
        exec.setQueueCapacity(queueCapacity);
        exec.setKeepAliveSeconds(keepAliveSeconds);
        exec.setThreadNamePrefix("ai-video-");
        // 队列满 + 线程满时由提交线程执行，形成背压；不让用户请求悄悄消失
        RejectedExecutionHandler handler = new ThreadPoolExecutor.CallerRunsPolicy();
        exec.setRejectedExecutionHandler(handler);
        // 优雅关闭：等待已提交任务完成，最多等 60s
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(60);
        exec.initialize();
        return exec;
    }
}
