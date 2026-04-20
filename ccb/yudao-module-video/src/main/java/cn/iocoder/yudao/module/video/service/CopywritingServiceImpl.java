package cn.iocoder.yudao.module.video.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.video.config.VolcanoEngineProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 豆包 LLM 文案生成实现
 *
 * <p>走火山方舟 Chat Completions，OpenAI 兼容协议。</p>
 */
@Service
@Slf4j
public class CopywritingServiceImpl implements CopywritingService {

    /** 单句最大字数（超出自动截断，避免 TTS 节奏过长） */
    private static final int MAX_LINE_CHARS = 20;
    /** 最少句数（少于该数视为生成失败，走重试/降级） */
    private static final int MIN_LINES = 6;
    /** 最多句数（多了视频会过长） */
    private static final int MAX_LINES = 18;

    private static final String SYSTEM_PROMPT =
            "你是一位专业的抖音/微信短视频文案策划。根据用户提供的商品介绍，生成一段适合竖屏短视频的口播文案。" +
            "要求：\n" +
            "1) 开头 3 秒要有强钩子（提问/冲突/悬念），吸引用户不划走；\n" +
            "2) 中间突出商品核心卖点和性价比，贴近生活场景；\n" +
            "3) 结尾引导扫码进店/下单；\n" +
            "4) 全文口语化、有节奏感，总字数 120~220；\n" +
            "5) 逐句输出，每句不超过 15 个中文字；\n" +
            "6) 严格仅以 JSON 数组格式返回，例如 [\"第一句\", \"第二句\"]，不要返回任何额外文字、不要使用 Markdown 代码块。";

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @Resource
    private VolcanoEngineProperties properties;

    @Override
    public List<String> generateCopywriting(String shopName, String userDescription) {
        if (StrUtil.isBlank(properties.getArkApiKey())) {
            throw new IllegalStateException("video.volcano-engine.ark-api-key 未配置（环境变量 ARK_API_KEY）");
        }

        String userContent = buildUserPrompt(shopName, userDescription);

        Map<String, Object> body = new HashMap<>();
        body.put("model", properties.getLlmModel());
        body.put("messages", Arrays.asList(
                messageOf("system", SYSTEM_PROMPT),
                messageOf("user", userContent)));
        body.put("temperature", 0.85);
        body.put("top_p", 0.9);
        body.put("max_tokens", 800);
        // 请求 JSON 响应，豆包 1.5+ 支持 response_format
        Map<String, String> responseFormat = new HashMap<>();
        responseFormat.put("type", "json_object");
        // 注意：部分推理接入点要求 type=text，如遇 400 切回默认
        body.put("response_format", responseFormat);

        Request request = new Request.Builder()
                .url(properties.getArkChatUrl())
                .post(RequestBody.create(
                        JsonUtils.toJsonString(body), MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer " + properties.getArkApiKey())
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.error("[generateCopywriting] Ark LLM 调用失败 status={}, body={}",
                        response.code(), responseBody);
                throw new RuntimeException("LLM 文案生成失败：HTTP " + response.code());
            }

            JsonNode root = JsonUtils.parseTree(responseBody);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isEmpty()) {
                log.error("[generateCopywriting] Ark 返回内容为空 body={}", responseBody);
                throw new RuntimeException("LLM 返回内容为空");
            }
            List<String> lines = parseLines(content.asText());
            if (lines.size() < MIN_LINES) {
                throw new RuntimeException("LLM 生成文案句数不足：" + lines.size() + " < " + MIN_LINES);
            }
            return sanitize(lines);
        } catch (IOException e) {
            log.error("[generateCopywriting] Ark LLM 调用 IO 异常", e);
            throw new RuntimeException("LLM 文案生成 IO 异常", e);
        }
    }

    // ==================== 私有方法 ====================

    private static Map<String, String> messageOf(String role, String content) {
        Map<String, String> msg = new HashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        return msg;
    }

    private String buildUserPrompt(String shopName, String userDescription) {
        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotBlank(shopName)) {
            sb.append("店铺名称：").append(shopName).append("\n");
        }
        sb.append("商品/店铺描述：").append(StrUtil.nullToEmpty(userDescription));
        return sb.toString();
    }

    /**
     * 从 LLM 返回文本中抽取逐句文案。
     *
     * <p>兼容三种格式：
     * 1) 纯 JSON 数组 {@code ["句1","句2"]}；
     * 2) 对象含 lines 字段 {@code {"lines":[...]}}；
     * 3) 模型没按指令返回，退化按换行拆分。</p>
     */
    private List<String> parseLines(String content) {
        String trimmed = content.trim();
        // 去除可能的 ```json ... ``` Markdown 包裹
        if (trimmed.startsWith("```")) {
            int first = trimmed.indexOf('\n');
            int last = trimmed.lastIndexOf("```");
            if (first > 0 && last > first) {
                trimmed = trimmed.substring(first + 1, last).trim();
            }
        }

        try {
            JsonNode node = JsonUtils.parseTree(trimmed);
            if (node.isArray()) {
                return jsonArrayToList(node);
            }
            if (node.isObject()) {
                for (String key : Arrays.asList("lines", "copywriting", "sentences", "data")) {
                    JsonNode arr = node.get(key);
                    if (arr != null && arr.isArray()) {
                        return jsonArrayToList(arr);
                    }
                }
            }
        } catch (RuntimeException ignore) {
            // fall through
        }
        // 退化方案：按换行或中文标点切句
        return Arrays.stream(trimmed.split("[\r\n]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private List<String> jsonArrayToList(JsonNode arr) {
        List<String> result = new ArrayList<>(arr.size());
        arr.forEach(n -> {
            String s = n.asText().trim();
            if (!s.isEmpty()) {
                result.add(s);
            }
        });
        return result;
    }

    private List<String> sanitize(List<String> lines) {
        List<String> trimmed = lines.stream()
                // 去除可能的序号前缀 "1. "/"1、"/"第一句："
                .map(s -> s.replaceFirst("^\\s*([0-9]+[\\.、:：\\)]|第[一二三四五六七八九十]句[:：]?)\\s*", ""))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.length() > MAX_LINE_CHARS ? s.substring(0, MAX_LINE_CHARS) : s)
                .collect(Collectors.toList());
        if (trimmed.size() > MAX_LINES) {
            return Collections.unmodifiableList(trimmed.subList(0, MAX_LINES));
        }
        return Collections.unmodifiableList(trimmed);
    }

}
