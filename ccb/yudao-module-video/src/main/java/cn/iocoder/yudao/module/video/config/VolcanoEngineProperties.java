package cn.iocoder.yudao.module.video.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 火山引擎配置
 */
@Component
@ConfigurationProperties(prefix = "video.volcano-engine")
@Data
public class VolcanoEngineProperties {

    /**
     * TTS 应用ID
     */
    private String appId;

    /**
     * TTS 访问令牌
     */
    private String accessToken;

    /**
     * TTS 集群
     */
    private String cluster = "volcano_tts";

    /**
     * TTS 音色类型
     */
    private String voiceType = "zh_female_qingxin";

    /**
     * TTS API 地址
     */
    private String ttsApiUrl = "https://openspeech.bytedance.com/api/v1/tts";

    /**
     * 火山引擎 AccessKey ID（用于视频生成API鉴权）
     */
    private String accessKeyId;

    /**
     * 火山引擎 AccessKey Secret
     */
    private String accessKeySecret;

    /**
     * 一键成片 API 地址
     */
    private String videoApiUrl = "https://open.volcengineapi.com/api/v1/smart_creation/video/create";

}
