package cn.iocoder.yudao.module.video.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 火山引擎配置
 *
 * <p>覆盖三个服务：</p>
 * <ul>
 *     <li>火山方舟（Ark）：LLM 文案 + Seedance 图生视频，走 {@code https://ark.cn-beijing.volces.com}</li>
 *     <li>TTS（豆包语音）：走 {@code https://openspeech.bytedance.com}</li>
 *     <li>智能创作 / 其它老接口：兼容历史 {@link #videoApiUrl}</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "video.volcano-engine")
@Data
public class VolcanoEngineProperties {

    // ==================== TTS（豆包语音） ====================

    /** TTS 应用ID */
    private String appId;
    /** TTS 访问令牌 */
    private String accessToken;
    /** TTS 集群 */
    private String cluster = "volcano_tts";
    /** TTS 音色类型（默认清新女声） */
    private String voiceType = "zh_female_qingxin";
    /** TTS API 地址 */
    private String ttsApiUrl = "https://openspeech.bytedance.com/api/v1/tts";

    // ==================== 方舟 Ark（LLM + Seedance 视频） ====================

    /**
     * Ark API Key（火山方舟控制台 → API Key 管理）
     * 环境变量：ARK_API_KEY
     */
    private String arkApiKey;

    /** Ark Chat Completions 地址（豆包 LLM） */
    private String arkChatUrl = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";

    /**
     * 豆包 LLM Endpoint ID 或模型名
     * <p>控制台创建推理接入点后填入 ep-xxxxx，或直接用如 {@code doubao-1-5-pro-32k-250115} 的模型名。</p>
     */
    private String llmModel = "doubao-1-5-pro-32k-250115";

    /** Ark 内容生成（Seedance 图生视频）地址 */
    private String arkGenerationUrl = "https://ark.cn-beijing.volces.com/api/v3/contents/generations/tasks";

    /**
     * Seedance 图生视频模型 Endpoint ID 或模型名
     * <p>推荐 {@code doubao-seedance-1-0-pro-250528}（Seedance Pro，图生视频 1080P 10s，字节目前最强）。</p>
     */
    private String seedanceModel = "doubao-seedance-1-0-pro-250528";

    /** Seedance 视频生成最大轮询次数（默认 5s × 120 = 10 分钟） */
    private int seedanceMaxPollAttempts = 120;

    /** Seedance 视频生成轮询间隔（毫秒） */
    private long seedancePollIntervalMillis = 5000L;

    // ==================== 旧版（保留以兼容） ====================

    /** 火山引擎 AccessKey ID（旧智能创作 API 鉴权，已不再用于 AI 成片主链路） */
    private String accessKeyId;
    /** 火山引擎 AccessKey Secret */
    private String accessKeySecret;
    /** 旧一键成片 API 地址 */
    private String videoApiUrl = "https://open.volcengineapi.com/api/v1/smart_creation/video/create";

}
