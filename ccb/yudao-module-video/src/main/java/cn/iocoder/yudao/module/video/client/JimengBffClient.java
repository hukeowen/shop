package cn.iocoder.yudao.module.video.client;

import cn.iocoder.yudao.module.video.config.VolcanoEngineProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.JIMENG_CALL_FAILED;

/**
 * 即梦AI（火山引擎视觉 CV）BFF 客户端。
 *
 * <p>使用火山 HMAC-SHA256 风格的 SigV4 签名（注意 algorithm 是 {@code HMAC-SHA256}，非 AWS 的 {@code AWS4-HMAC-SHA256}）。</p>
 *
 * <p>签名派生链：{@code SK -> shortDate -> region -> service -> 'request'}。</p>
 */
@Component
@Slf4j
public class JimengBffClient {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");

    private static final String ALGORITHM = "HMAC-SHA256";
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SIGNED_HEADERS = "content-type;x-date";

    private static final DateTimeFormatter X_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    @Resource
    private VolcanoEngineProperties props;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    /**
     * 调用即梦AI（火山 CV）某个 Action。
     *
     * @param action   e.g. {@code CVSync2AsyncSubmitTask} / {@code CVSync2AsyncGetResult}
     * @param bodyJson 序列化好的请求 JSON
     * @return 响应体 JSON 字符串
     */
    public String callAction(String action, String bodyJson) {
        String ak = props.getJimengAccessKey();
        String sk = props.getJimengSecretKey();
        if (ak == null || ak.isEmpty() || sk == null || sk.isEmpty()) {
            throw exception(JIMENG_CALL_FAILED, "JIMENG_AK / JIMENG_SK 未配置");
        }
        String endpoint = props.getJimengEndpoint();
        String version = props.getJimengVersion();
        String region = props.getJimengRegion();
        String service = props.getJimengService();

        String xDate = X_DATE_FORMATTER.format(Instant.now());
        String shortDate = xDate.substring(0, 8);
        String credential = shortDate + "/" + region + "/" + service + "/request";
        String bodyStr = bodyJson == null ? "" : bodyJson;
        String bodyHash = sha256Hex(bodyStr);

        // path 固定 "/"，query 按 Action=...&Version=... 顺序
        String canonicalRequest = String.join("\n",
                "POST",
                "/",
                "Action=" + action + "&Version=" + version,
                "content-type:application/json",
                "x-date:" + xDate,
                "",
                SIGNED_HEADERS,
                bodyHash);

        String hashedCanon = sha256Hex(canonicalRequest);
        String stringToSign = ALGORITHM + "\n" + xDate + "\n" + credential + "\n" + hashedCanon;

        byte[] signingKey = sk.getBytes(StandardCharsets.UTF_8);
        for (String seed : new String[]{shortDate, region, service, "request"}) {
            signingKey = hmacSha256(signingKey, seed);
        }
        String signature = toHex(hmacSha256(signingKey, stringToSign));

        String authorization = ALGORITHM
                + " Credential=" + ak + "/" + credential
                + ", SignedHeaders=" + SIGNED_HEADERS
                + ", Signature=" + signature;

        HttpUrl url;
        try {
            // 保留 endpoint 基础 URL，追加 query 参数
            HttpUrl base = HttpUrl.get(new URL(endpoint));
            url = base.newBuilder()
                    .addQueryParameter("Action", action)
                    .addQueryParameter("Version", version)
                    .build();
        } catch (Exception e) {
            throw exception(JIMENG_CALL_FAILED, "endpoint 非法：" + endpoint);
        }

        int bodyBytes = bodyStr.getBytes(StandardCharsets.UTF_8).length;
        log.info("[bff/jimeng] action={} akPrefix={}*** region={} service={} bodyBytes={}",
                action, safePrefix(ak), region, service, bodyBytes);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(bodyStr, JSON_MEDIA_TYPE))
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Date", xDate)
                .addHeader("Authorization", authorization)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            String text = body != null ? body.string() : "";
            if (!response.isSuccessful()) {
                log.warn("[bff/jimeng] action={} http={} bodyPrefix={}",
                        action, response.code(), clip(text, 200));
                throw exception(JIMENG_CALL_FAILED,
                        "HTTP " + response.code() + "：" + clip(text, 200));
            }
            return text;
        } catch (IOException ioe) {
            log.error("[bff/jimeng] action={} io error", action, ioe);
            throw exception(JIMENG_CALL_FAILED, "网络异常：" + ioe.getMessage());
        }
    }

    // ==================== 工具 ====================

    private static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(key, HMAC_SHA256));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw exception(JIMENG_CALL_FAILED, "HMAC 计算失败：" + e.getMessage());
        }
    }

    private static String sha256Hex(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return toHex(md.digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw exception(JIMENG_CALL_FAILED, "SHA-256 计算失败：" + e.getMessage());
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static String safePrefix(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        return key.length() <= 4 ? key.substring(0, Math.min(2, key.length())) : key.substring(0, 4);
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

}
