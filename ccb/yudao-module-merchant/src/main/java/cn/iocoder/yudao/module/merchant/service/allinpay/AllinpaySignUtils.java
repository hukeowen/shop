package cn.iocoder.yudao.module.merchant.service.allinpay;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/**
 * 通联收付通签名工具
 *
 * <p>签名规范（参考 https://prodoc.allinpay.com/doc/256/ ）：</p>
 * <ol>
 *     <li>请求参数按 key 字典序排序</li>
 *     <li>拼接 key1=value1&amp;key2=value2&amp;...（跳过 sign 自身和空值）</li>
 *     <li>用平台 RSA 私钥对该字符串做 SHA256withRSA 签名</li>
 *     <li>结果 Base64 编码作为 sign 字段</li>
 * </ol>
 *
 * <p>验签反向：用通联公钥验证 SHA256withRSA 签名。</p>
 *
 * <p>使用 JDK 标准 {@link Signature} 不依赖 hutool RSA 装载方式差异，行为可预测。</p>
 */
public final class AllinpaySignUtils {

    private static final String ALG = "SHA256withRSA";

    private AllinpaySignUtils() {}

    /** 拼接待签名串（按 key 字典序，跳过 sign 字段和 null/空值） */
    public static String buildSignSource(Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>(params);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if ("sign".equalsIgnoreCase(e.getKey())) continue;
            String v = e.getValue();
            if (v == null || v.isEmpty()) continue;
            if (sb.length() > 0) sb.append('&');
            sb.append(e.getKey()).append('=').append(v);
        }
        return sb.toString();
    }

    /**
     * 用平台私钥对参数 Map 签名，返回 Base64 编码的签名串
     *
     * @param params              待签名参数（不含 sign 字段）
     * @param platformPrivateKey  平台 RSA 私钥 PEM (PKCS#8 base64 内容，可包含 BEGIN/END 头)
     */
    public static String signRequest(Map<String, String> params, String platformPrivateKey) {
        if (platformPrivateKey == null || platformPrivateKey.isEmpty()) {
            throw new IllegalStateException("通联平台私钥未配置 (merchant.allinpay.platform-rsa-private-key)");
        }
        String source = buildSignSource(params);
        try {
            PrivateKey pk = parsePrivateKey(platformPrivateKey);
            Signature s = Signature.getInstance(ALG);
            s.initSign(pk);
            s.update(source.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(s.sign());
        } catch (Exception e) {
            throw new IllegalStateException("通联签名失败：" + e.getMessage(), e);
        }
    }

    /**
     * 用通联公钥验证回调签名
     *
     * @param params             通联回调参数（不含 sign 字段；调用方需先把 sign 摘出来）
     * @param sign               sign 字段值（Base64）
     * @param allinpayPublicKey  通联 RSA 公钥 PEM (X509 base64 内容)
     */
    public static boolean verifyNotify(Map<String, String> params, String sign, String allinpayPublicKey) {
        if (sign == null || sign.isEmpty()) return false;
        if (allinpayPublicKey == null || allinpayPublicKey.isEmpty()) {
            throw new IllegalStateException("通联公钥未配置 (merchant.allinpay.allinpay-rsa-public-key)");
        }
        String source = buildSignSource(params);
        try {
            PublicKey pubk = parsePublicKey(allinpayPublicKey);
            Signature s = Signature.getInstance(ALG);
            s.initVerify(pubk);
            s.update(source.getBytes(StandardCharsets.UTF_8));
            return s.verify(Base64.getDecoder().decode(sign));
        } catch (Exception e) {
            return false;
        }
    }

    /** PEM (PKCS#8) → PrivateKey */
    private static PrivateKey parsePrivateKey(String pem) throws Exception {
        byte[] der = Base64.getDecoder().decode(stripPem(pem));
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    /** PEM (X509) → PublicKey */
    private static PublicKey parsePublicKey(String pem) throws Exception {
        byte[] der = Base64.getDecoder().decode(stripPem(pem));
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    /** 去掉 PEM 的 BEGIN/END 头和空白 */
    private static String stripPem(String pem) {
        return pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s+", "");
    }
}
