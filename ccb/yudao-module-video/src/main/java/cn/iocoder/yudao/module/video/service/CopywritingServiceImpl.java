package cn.iocoder.yudao.module.video.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.video.config.VolcanoEngineProperties;
import cn.iocoder.yudao.module.video.service.dto.AiVideoScriptDTO;
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

    /**
     * 富脚本 system prompt — 一次性输出 lines + visualPrompt + bgmStyle
     *
     * <p>核心理念：不预设行业模板（避免烧烤/茶楼/水果店枚举不完）；让 LLM 看
     * 店铺名 + 老板描述自由推断行业，再自动补齐运镜/风格/BGM 词汇。</p>
     */
    private static final String RICH_SCRIPT_SYSTEM_PROMPT =
            "你是抖音爆款短视频脚本顾问。根据用户的店铺名 + 商品描述，输出一份完整的拍摄脚本，包含三部分：\n\n" +
            "【1. 口播文案 lines】6-10 句，每句 ≤ 15 中文字\n" +
            "  - 开头 3 秒强钩子（数字/反差/疑问句之一）\n" +
            "  - 中间突出 1-2 个核心卖点 + 价格/优势\n" +
            "  - 结尾引导扫码进店 / 评论区领优惠\n" +
            "  - 口语化、像隔壁老板说话，避免'美食人间值得'空话\n" +
            "  - 严禁极限词：最/第一/全网/绝对/独家（违反广告法会被限流）\n\n" +
            "【2. 视觉镜头 visualPrompt】英文，1 句话内（≤ 80 词），喂给图生视频模型 Seedance\n" +
            "  - 结构：[主体] + [动作] + [环境] + [运镜] + [风格]\n" +
            "  - 必须含运镜词（push-in/pull-out/pan/tilt-up/dolly/macro shot/slow motion 至少一个）\n" +
            "  - 必须含风格词（cinematic/food photography/vlog/asmr/golden hour/film grain 至少一个）\n" +
            "  - 例：'Hand brushing chili oil on grilling skewers, sizzling sparks, warm street food market night, push-in macro lens, cinematic film grain, golden hour'\n\n" +
            "【3. BGM 风格 bgmStyle】从下面 6 个 key 选最匹配的一个：\n" +
            "  - street_food_yelling : 街头叫卖型（烧烤/夜市/路边摊，鼓点强节奏快）\n" +
            "  - cozy_explore       : 探店温馨型（咖啡馆/网红店/餐厅，轻快流行）\n" +
            "  - asmr_macro         : ASMR 静谧型（茶叶/水果切片/烘焙，环境音）\n" +
            "  - elegant_tea        : 中式优雅型（茶馆/酒/古典，丝竹悠扬）\n" +
            "  - trendy_pop         : 网红跟风型（奶茶/甜品/时尚单品，电子流行）\n" +
            "  - emotional_story    : 情感故事型（老板访谈/手艺传承/品牌故事，暖音情感）\n\n" +
            "严格仅以下面 JSON 格式返回（不要 Markdown 代码块、不要额外文字）：\n" +
            "{\n" +
            "  \"lines\": [\"第一句\", \"第二句\", ...],\n" +
            "  \"visualPrompt\": \"english cinematic prompt with camera motion + style\",\n" +
            "  \"bgmStyle\": \"street_food_yelling\"\n" +
            "}";

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @Resource
    private VolcanoEngineProperties properties;

    @Override
    public AiVideoScriptDTO generateRichScript(String shopName, String userDescription) {
        if (StrUtil.isBlank(properties.getArkApiKey())) {
            throw new IllegalStateException("video.volcano-engine.ark-api-key 未配置（环境变量 ARK_API_KEY）");
        }
        String userContent = buildUserPrompt(shopName, userDescription);

        Map<String, Object> body = new HashMap<>();
        body.put("model", properties.getLlmModel());
        body.put("messages", Arrays.asList(
                messageOf("system", RICH_SCRIPT_SYSTEM_PROMPT),
                messageOf("user", userContent)));
        body.put("temperature", 0.85);
        body.put("top_p", 0.9);
        body.put("max_tokens", 1200);
        Map<String, String> responseFormat = new HashMap<>();
        responseFormat.put("type", "json_object");
        body.put("response_format", responseFormat);

        Request request = new Request.Builder()
                .url(properties.getArkChatUrl())
                .post(RequestBody.create(JsonUtils.toJsonString(body), MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer " + properties.getArkApiKey())
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.error("[generateRichScript] Ark LLM 调用失败 status={}, body={}", response.code(), responseBody);
                throw new RuntimeException("LLM 富脚本生成失败：HTTP " + response.code());
            }
            JsonNode root = JsonUtils.parseTree(responseBody);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isEmpty()) {
                throw new RuntimeException("LLM 返回内容为空");
            }
            return parseRichScript(content.asText());
        } catch (IOException e) {
            log.error("[generateRichScript] Ark LLM IO 异常", e);
            throw new RuntimeException("LLM 富脚本生成 IO 异常", e);
        }
    }

    /** 解析富脚本 JSON：{ lines, visualPrompt, bgmStyle }；任一字段缺失给合理默认值 */
    private AiVideoScriptDTO parseRichScript(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int first = trimmed.indexOf('\n');
            int last = trimmed.lastIndexOf("```");
            if (first > 0 && last > first) trimmed = trimmed.substring(first + 1, last).trim();
        }
        JsonNode node = JsonUtils.parseTree(trimmed);
        List<String> lines = node.has("lines") && node.get("lines").isArray()
                ? sanitize(jsonArrayToList(node.get("lines")))
                : Collections.emptyList();
        String visualPrompt = node.path("visualPrompt").asText("").trim();
        if (visualPrompt.isEmpty()) {
            // 兜底：用第一句中文 + 通用运镜
            visualPrompt = "Cinematic vertical product video, push-in macro lens, food photography, golden hour lighting, film grain";
        }
        String bgmStyle = node.path("bgmStyle").asText("cozy_explore").trim();
        if (bgmStyle.isEmpty()) bgmStyle = "cozy_explore";
        return AiVideoScriptDTO.builder()
                .lines(lines.isEmpty() ? Collections.singletonList("欢迎光临") : lines)
                .visualPrompt(visualPrompt)
                .bgmStyle(bgmStyle)
                .build();
    }

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
