package cn.iocoder.yudao.module.merchant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 摊小二商户统一小程序配置
 *
 * <p>注意：这是【摊小二商户端专属】小程序，不同于 yudao 自带的 wx.miniapp 会员小程序。
 * 需要独立的 appId / appSecret 以便跟会员端小程序区分。</p>
 */
@Component
@ConfigurationProperties(prefix = "wechat.mini-app")
@Data
public class WeChatMiniAppProperties {

    /**
     * 小程序 AppID（来自 ${WECHAT_MINI_APP_ID:}）
     */
    private String appId;

    /**
     * 小程序 AppSecret（来自 ${WECHAT_MINI_APP_SECRET:}）
     */
    private String appSecret;

    /**
     * code2Session 接口地址，默认官方地址
     */
    private String jscode2sessionUrl = "https://api.weixin.qq.com/sns/jscode2session";

    /**
     * sessionKey 缓存 TTL 秒数，默认 600 秒（与微信 sessionKey 有效期一致）
     */
    private int sessionKeyTtlSeconds = 600;

    /**
     * OkHttp 调微信超时秒数
     */
    private int httpTimeoutSeconds = 8;
}
