package cn.iocoder.yudao.module.merchant.service;

import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * KYC 证件 TOS 私有对象临时 URL 签发
 *
 * <p>商户上传身份证/营业执照走 acl=private，DB 只存 TOS key。访问时由本服务调用
 * sidecar /oss/sign 现签 1h 预签名 GET URL（绑定 X-Internal-Token 防止越权）。</p>
 *
 * <p>该服务自身不做权限校验 —— 调用方（商户 H5 / PC 后台）必须先校验调用人有权
 * 访问该 key 才能调本服务。</p>
 */
@Slf4j
@Service
public class KycSignService {

    private static final Pattern URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");

    @Value("${merchant.internal-token:}")
    private String internalToken;

    @Value("${merchant.sidecar-url:http://127.0.0.1:8081}")
    private String sidecarUrl;

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build();

    /**
     * 签发一个 TOS key 的 GET 预签名 URL
     *
     * @param key TOS object key（如 'tanxiaoer/kyc/xxx.jpg'）
     * @param ttl 秒，60 ~ 86400，越短越安全
     * @return 预签名 URL（含 X-Amz-Signature 等查询参数）
     */
    public String sign(String key, int ttl) {
        if (key == null || key.isEmpty()) throw new IllegalArgumentException("key 为空");
        if (key.contains("..") || key.startsWith("/")) throw new IllegalArgumentException("key 非法");
        if (internalToken == null || internalToken.isEmpty()) {
            throw new IllegalStateException("merchant.internal-token 未配置 — 无法调 sidecar");
        }
        int safeTtl = Math.min(86400, Math.max(60, ttl));
        HttpUrl url = HttpUrl.parse(sidecarUrl + "/oss/sign").newBuilder()
                .addQueryParameter("key", key)
                .addQueryParameter("ttl", String.valueOf(safeTtl))
                .build();
        Request req = new Request.Builder()
                .url(url)
                .header("X-Internal-Token", internalToken)
                .get()
                .build();
        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                log.warn("[KycSign] sidecar /oss/sign 返 {} body={}", resp.code(), body);
                throw new RuntimeException("sidecar 签发失败 " + resp.code());
            }
            java.util.regex.Matcher m = URL_PATTERN.matcher(body);
            if (!m.find()) throw new RuntimeException("sidecar 返回未含 url 字段：" + body);
            return m.group(1);
        } catch (IOException e) {
            log.warn("[KycSign] 调 sidecar 失败", e);
            throw new RuntimeException("KYC 签发失败：" + e.getMessage(), e);
        }
    }
}
