package cn.iocoder.yudao.module.video.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 抖音开放平台配置
 */
@Component
@ConfigurationProperties(prefix = "video.douyin")
@Data
public class DouyinProperties {

    /**
     * 应用 client_key
     */
    private String clientKey;

    /**
     * 应用 client_secret
     */
    private String clientSecret;

}
