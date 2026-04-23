package cn.iocoder.yudao.module.video.client;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
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
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.TTS_FAILED;

/**
 * 豆包 openspeech v3 TTS（单向流式 MP3）BFF 客户端。
 *
 * <p>响应是 NDJSON 流——每行一个 JSON，形如 {@code {"code":..,"data":"<base64chunk>","message":..}}。
 * 累积 base64 解码拼接即得到完整 MP3。</p>
 */
@Component
@Slf4j
public class TtsBffClient {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");

    private static final String DEFAULT_SPEAKER = "zh_male_beijingxiaoye_emo_v2_mars_bigtts";

    @Resource
    private VolcanoEngineProperties props;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    /**
     * 合成一段 MP3 音频字节流。
     *
     * @param text  文本（建议 ≤ 300 字）
     * @param voice 音色，传空则使用 {@link #DEFAULT_SPEAKER}
     * @return MP3 字节数组
     */
    public byte[] generateMp3(String text, String voice) {
        String apiKey = props.getAccessToken();
        if (apiKey == null || apiKey.isEmpty()) {
            throw exception(TTS_FAILED, "VOLCANO_ACCESS_TOKEN 未配置");
        }
        String resourceId = props.getTtsV3ResourceId();
        String url = props.getTtsV3ApiUrl();
        String speaker = (voice == null || voice.isEmpty()) ? DEFAULT_SPEAKER : voice;

        // 与前端 sidecar 对齐的 additions 配置
        Map<String, Object> additionsObj = new LinkedHashMap<>();
        additionsObj.put("disable_markdown_filter", true);
        additionsObj.put("enable_language_detector", true);
        additionsObj.put("enable_latex_tn", true);
        additionsObj.put("disable_default_bit_rate", true);
        additionsObj.put("max_length_to_filter_parenthesis", 0);
        Map<String, Object> cacheConfig = new LinkedHashMap<>();
        cacheConfig.put("text_type", 1);
        cacheConfig.put("use_cache", true);
        additionsObj.put("cache_config", cacheConfig);

        Map<String, Object> audioParams = new LinkedHashMap<>();
        audioParams.put("format", "mp3");
        audioParams.put("sample_rate", 24000);

        Map<String, Object> reqParams = new LinkedHashMap<>();
        reqParams.put("text", text);
        reqParams.put("speaker", speaker);
        reqParams.put("additions", JsonUtils.toJsonString(additionsObj));
        reqParams.put("audio_params", audioParams);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("req_params", reqParams);
        String bodyJson = JsonUtils.toJsonString(payload);

        int textLen = text == null ? 0 : text.length();
        log.info("[bff/tts] url={} speaker={} textLen={} resourceId={}",
                url, speaker, textLen, resourceId);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(bodyJson, JSON_MEDIA_TYPE))
                .addHeader("x-api-key", apiKey)
                .addHeader("X-Api-Resource-Id", resourceId)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                String errText = body != null ? clip(body.string(), 300) : "";
                log.warn("[bff/tts] http={} bodyPrefix={}", response.code(), errText);
                throw exception(TTS_FAILED, "HTTP " + response.code() + "：" + errText);
            }
            return readNdjsonMp3(body.byteStream());
        } catch (IOException ioe) {
            log.error("[bff/tts] io error", ioe);
            throw exception(TTS_FAILED, "网络异常：" + ioe.getMessage());
        }
    }

    /**
     * 按行读 NDJSON，解析 base64 data chunk，拼接后返回完整 MP3。
     */
    private static byte[] readNdjsonMp3(InputStream in) throws IOException {
        ByteArrayOutputStream mp3 = new ByteArrayOutputStream();
        Base64.Decoder decoder = Base64.getDecoder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String s = line.trim();
                if (s.isEmpty()) {
                    continue;
                }
                Map<String, Object> obj;
                try {
                    obj = JsonUtils.parseObject(s, Map.class);
                } catch (Exception parseErr) {
                    // 非 JSON 行忽略
                    continue;
                }
                if (obj == null) {
                    continue;
                }
                Object codeObj = obj.get("code");
                if (codeObj instanceof Number) {
                    int code = ((Number) codeObj).intValue();
                    if (code != 0 && code != 20000000) {
                        Object msg = obj.get("message");
                        throw new IOException("volc code " + code + "：" + (msg == null ? "" : msg));
                    }
                }
                Object dataObj = obj.get("data");
                if (dataObj instanceof String && !((String) dataObj).isEmpty()) {
                    byte[] chunk = decoder.decode((String) dataObj);
                    mp3.write(chunk);
                }
            }
        }
        byte[] result = mp3.toByteArray();
        if (result.length == 0) {
            throw exception(TTS_FAILED, "未返回音频数据");
        }
        return result;
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

}
