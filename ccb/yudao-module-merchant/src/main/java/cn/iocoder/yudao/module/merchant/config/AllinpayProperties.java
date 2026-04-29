package cn.iocoder.yudao.module.merchant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 通联收付通配置
 *
 * <p>通联收付通是通联支付旗下的二级商户聚合方案：平台统一在通联开户（拿到 platformOrgId），
 * 然后为每个入驻商户走"商户进件"流程开通独立子户（tlMchId），用户在子户下下单时资金 T+1
 * 直达子户账户，平台不碰钱（持牌合规）。</p>
 *
 * <p>API 文档：<a href="https://prodoc.allinpay.com/doc/256/">通联收付通进件文档</a></p>
 *
 * <p>必要前置：</p>
 * <ul>
 *     <li>注册成为通联收付通平台机构（拿 platformOrgId）</li>
 *     <li>申请 RSA 密钥对：私钥本地保管（用于请求签名），公钥上传通联（用于通联回调验签）</li>
 *     <li>下载通联公钥：用于验证通联回调推送的签名</li>
 * </ul>
 */
@Configuration
@ConfigurationProperties(prefix = "merchant.allinpay")
@Data
public class AllinpayProperties {

    /** 是否启用通联收付通；false 时 client 走 noop（开发期 / 资质未下来） */
    private boolean enabled = false;

    /** 通联开放接口 base url；生产 https://vsp.allinpay.com，沙箱 https://test-vsp.allinpay.com */
    private String apiBaseUrl = "https://test-vsp.allinpay.com";

    /** 平台机构号（通联分配的 orgId，进件时作为父级机构） */
    private String orgId = "";

    /** 平台 RSA 私钥 PEM (PKCS#8 格式)；本地保管，签发请求用 */
    private String platformRsaPrivateKey = "";

    /** 通联 RSA 公钥 PEM (X509 格式)；通联下发，验证回调签名用 */
    private String allinpayRsaPublicKey = "";

    /** 进件结果回调 webhook URL（必须公网可达 HTTPS）；通联开户成功/失败时 POST 推送 */
    private String registerNotifyUrl = "";

    /** 支付结果回调 webhook URL */
    private String payNotifyUrl = "";

    /** HTTP 超时秒，连接 / 读取 */
    private int connectTimeoutSec = 5;
    private int readTimeoutSec = 15;
}
