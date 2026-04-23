package cn.iocoder.yudao.module.video.client;

import cn.iocoder.yudao.module.video.config.VolcanoEngineProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.ARK_CHAT_FAILED;

/**
 * 火山方舟（Ark）LLM Chat BFF 客户端。
 *
 * <p>前端浏览器不得持有 {@code ARK_API_KEY}，由本客户端在服务端代理调用。
 * 请求体由调用方（Controller）透传，保持对 Ark 协议的兼容性。</p>
 *
 * <p>日志策略：只打印 Authorization 前缀 + 长度，不打印 messages 内容，避免隐私泄漏与日志膨胀。</p>
 */
@Component
@Slf4j
public class ArkBffClient {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");

    /** 单次重试的最大次数（不含首次请求） */
    private static final int MAX_RETRY = 3;

    /** 退避基数（毫秒），指数退避 */
    private static final long BACKOFF_BASE_MILLIS = 500L;

    @Resource
    private VolcanoEngineProperties props;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    /**
     * 调用 Ark Chat Completions 接口。
     *
     * @param bodyJson 原样透传给 Ark 的 JSON 体（含 model / messages / temperature 等）
     * @return Ark 的原始响应 JSON 字符串
     */
    public String chat(String bodyJson) {
        String apiKey = props.getArkApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            throw exception(ARK_CHAT_FAILED, "ARK_API_KEY 未配置");
        }
        String url = props.getArkChatUrl();
        int bodyBytes = bodyJson == null ? 0 : bodyJson.getBytes().length;
        log.info("[bff/ark] url={} authPrefix={}*** keyLen={} bodyBytes={}",
                url, safePrefix(apiKey), apiKey.length(), bodyBytes);

        IOException lastIo = null;
        String lastErrBody = null;
        int lastStatus = -1;
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(bodyJson == null ? "" : bodyJson, JSON_MEDIA_TYPE))
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                int code = response.code();
                ResponseBody body = response.body();
                String text = body != null ? body.string() : "";
                if (response.isSuccessful()) {
                    return text;
                }
                lastStatus = code;
                lastErrBody = text;
                boolean shouldRetry = code >= 500
                        || (text != null && text.toLowerCase().contains("concurrent limit"));
                if (!shouldRetry || attempt == MAX_RETRY) {
                    break;
                }
                log.warn("[bff/ark] retry attempt={} status={} bodyPrefix={}",
                        attempt + 1, code, clip(text, 120));
            } catch (IOException ioe) {
                lastIo = ioe;
                if (attempt == MAX_RETRY) {
                    break;
                }
                log.warn("[bff/ark] io retry attempt={} msg={}", attempt + 1, ioe.getMessage());
            }
            sleepBackoff(attempt);
        }
        if (lastIo != null) {
            throw exception(ARK_CHAT_FAILED, "网络异常：" + lastIo.getMessage());
        }
        throw exception(ARK_CHAT_FAILED, "HTTP " + lastStatus + "：" + clip(lastErrBody, 200));
    }

    private static String safePrefix(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        return key.length() <= 6 ? key.substring(0, Math.min(2, key.length())) : key.substring(0, 6);
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static void sleepBackoff(int attempt) {
        try {
            Thread.sleep(BACKOFF_BASE_MILLIS * (1L << attempt));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

}
