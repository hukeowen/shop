package cn.iocoder.yudao.module.video.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.video.config.VolcanoEngineProperties;
import cn.iocoder.yudao.module.video.service.dto.AiVideoMultiSceneScriptDTO;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    // ==================== 多幕分镜（generateMultiSceneScript） ====================

    /** 多幕的最少幕数（前端 imageCount cap 1） */
    private static final int MULTI_MIN_SCENES = 1;
    /** 多幕的最多幕数（≤ 6 张图，再多浪费 enhance/seedance 配额） */
    private static final int MULTI_MAX_SCENES = 6;
    /** 单幕 narration 最长字数（10s 幕用上限；5s 幕在 prompt 里限到一半） */
    private static final int MULTI_NARRATION_MAX_CHARS = 36;
    /** 视频总时长上限（秒）— 抖音算法对 ≤30s 短视频流量更友好 */
    private static final int MULTI_MAX_TOTAL_SEC = 30;
    /** 即梦 i2v_first_v30 仅支持的两档 duration */
    private static final Set<Integer> ALLOWED_DURATIONS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(5, 10)));
    /** duration 缺失/非法时的默认值 */
    private static final int DEFAULT_SCENE_DURATION = 5;
    /** 最后一幕结尾必须以这 6 个字收口（前端 scriptLlm.js 里的 FIXED_CTA 同步） */
    private static final String FIXED_CTA = "微信扫码下单";
    /** 兜底通用 visual prompt（含运镜+风格词，避免 LLM 缺值时 Seedance 拿空字符串） */
    private static final String FALLBACK_VISUAL_PROMPT =
            "Cinematic vertical product shot, push-in macro lens, food photography, golden hour lighting, film grain, soft natural color";
    /** 多幕分镜默认视觉模型（豆包 vision pro） */
    private static final String VISION_MODEL = "doubao-1-5-vision-pro-32k-250115";
    /** 多幕分镜默认 BGM 风格（LLM 没返时兜底） */
    private static final String DEFAULT_BGM_STYLE = "cozy_explore";
    /** 6 个合法 BGM 风格 key（与 sidecar/bgm/<style>_*.mp3 一致） */
    private static final Set<String> VALID_BGM_STYLES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "street_food_yelling", "cozy_explore", "asmr_macro",
            "elegant_tea", "trendy_pop", "emotional_story"
    )));
    /** 屏蔽词（吆喝/促销/极限词，违反广告法或 LLM 易踩雷） */
    private static final List<String> BLACKLIST_WORDS = Arrays.asList(
            "老板", "赔本", "大减价", "限时", "秒杀", "小黄车", "购物车", "点击链接",
            "最", "第一", "全网", "绝对", "独家"
    );

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

    // ==================== 多幕分镜（视觉模型 + 后处理） ====================

    @Override
    public AiVideoMultiSceneScriptDTO generateMultiSceneScript(
            String shopName, String userDescription,
            List<String> imageUrls, int sceneCount, int sceneDuration) {
        // 1. 入参规整：scenes / images 数量都 cap [1, 6]，sceneDuration ≥ 1
        List<String> imgs = imageUrls == null
                ? Collections.emptyList()
                : imageUrls.stream().filter(StrUtil::isNotBlank).collect(Collectors.toList());
        int imgCount = clamp(imgs.size(), MULTI_MIN_SCENES, MULTI_MAX_SCENES);
        int n = clamp(sceneCount, MULTI_MIN_SCENES, MULTI_MAX_SCENES);
        if (imgCount > 0 && n > imgCount) {
            // 幕数超过图片数会逼 LLM 重复用图，体验不好；硬切到 imgCount
            n = imgCount;
        }
        int dur = Math.max(1, sceneDuration);

        if (StrUtil.isBlank(properties.getArkApiKey())) {
            log.warn("[generateMultiSceneScript] ARK_API_KEY 未配置，返回兜底脚本");
            return fallbackMultiSceneScript(n, dur);
        }
        if (imgs.isEmpty()) {
            log.warn("[generateMultiSceneScript] imageUrls 为空，返回兜底");
            return fallbackMultiSceneScript(n, dur);
        }

        // 2. 调豆包 vision-pro：messages 含 system + user (含图)
        try {
            String content = callVisionLlmForScript(shopName, userDescription, imgs, n, dur);
            return postProcessMultiScene(content, imgCount, n, dur);
        } catch (Exception e) {
            log.error("[generateMultiSceneScript] 调用 / 解析失败，返回兜底", e);
            return fallbackMultiSceneScript(n, dur);
        }
    }

    /** 真实调 vision-pro 拿原始 JSON 文本；失败抛 RuntimeException 让外层兜底 */
    private String callVisionLlmForScript(
            String shopName, String userDescription,
            List<String> imageUrls, int n, int dur) {
        String systemPrompt = buildMultiSceneSystemPrompt(n, dur);
        String userText = buildMultiSceneUserText(shopName, userDescription, imageUrls.size(), n, dur);

        // 视觉消息：text + N 个 image_url（detail=low 节省 token）
        List<Object> userContents = new ArrayList<>();
        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("type", "text");
        textPart.put("text", userText);
        userContents.add(textPart);
        for (int i = 0; i < imageUrls.size(); i++) {
            Map<String, Object> imgPart = new LinkedHashMap<>();
            imgPart.put("type", "image_url");
            Map<String, Object> imgUrl = new LinkedHashMap<>();
            imgUrl.put("url", imageUrls.get(i));
            imgUrl.put("detail", "low");
            imgPart.put("image_url", imgUrl);
            userContents.add(imgPart);
        }

        Map<String, Object> systemMsg = new LinkedHashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userContents);

        Map<String, Object> body = new LinkedHashMap<>();
        // 使用视觉模型；如果配置里 ark-allowed-models 没包含会怎样？
        // 此调用走 service 直连 Ark（不经 BFF /ark/chat），不受 BFF 白名单限制；
        // 但仍建议在 properties.arkAllowedModels 里包含 vision 模型，便于运维
        body.put("model", VISION_MODEL);
        body.put("messages", Arrays.asList(systemMsg, userMsg));
        body.put("temperature", 0.85);
        body.put("top_p", 0.9);
        body.put("max_tokens", 1500);
        Map<String, String> responseFormat = new LinkedHashMap<>();
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
                log.error("[generateMultiSceneScript] vision LLM 失败 status={} body={}",
                        response.code(), responseBody);
                throw new RuntimeException("vision LLM HTTP " + response.code());
            }
            JsonNode root = JsonUtils.parseTree(responseBody);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isEmpty()) {
                throw new RuntimeException("vision LLM 返回内容为空");
            }
            return content.asText();
        } catch (IOException e) {
            throw new RuntimeException("vision LLM IO 异常", e);
        }
    }

    private String buildMultiSceneSystemPrompt(int n, int dur) {
        return "你是抖音爆款短视频导演，专为线下小店老板拍摄帮助门店转化的竖屏小广告。\n"
                + "我会给你 " + n + " 张店铺/商品照片。你的工作：站在导演视角整体看完所有图，"
                + "**自由决定播放顺序、自由分配每幕时长（5 或 10 秒）、自由写台词、自由写运镜**，目标是 15 秒内抓住人。\n\n"
                + "【硬规则】违反算失败：\n"
                + "1) 输出 N 幕（N = 图片数 = " + n + "），每幕一个对象 {imgIdx, imageSummary, narration, visualPrompt, duration}；\n"
                + "2) imgIdx 取值 0.." + (n - 1) + "，**每张图必须用上且只用一次**（顺序由你决定，不一定按上传顺序；从最有视觉冲击的开篇）；\n"
                + "3) duration 仅可取 5 或 10（即梦 i2v_first_v30 仅支持这两档）；**全部幕时长之和必须 ≤ " + MULTI_MAX_TOTAL_SEC + " 秒**；信息密度高的镜头给 5s 紧凑，需要慢节奏铺氛围给 10s；\n"
                + "4) narration 中文自然口语，**用每幕 duration ×3 作为字数上限**（5s≤15 字、10s≤30 字），不要书面体；\n"
                + "5) visualPrompt 英文 1 句 ≤ 80 词，导演自由组合：[主体]+[动作动词，让画面真的动起来]+[环境]+[运镜，自由发挥]+[镜头/光线/质感]；\n"
                + "   **画面必须有动作**：蒸汽腾起 / 油花飞溅 / 拉丝爆汁 / 切刀落下 / 推车进店 / 风吹布幔 / 招牌点亮 / 顾客入座，避免只「轻轻摇晃」的死镜头；\n"
                + "   **运镜由你决定**：推/拉/摇/移/跟/升降/手持/特写微距/变焦切换；不限模板，你是导演；\n"
                + "6) imageSummary 中文一句（≤ 20 字），描述该幕图片最动人的细节；\n"
                + "7) 全文严禁：老板/赔本/大减价/限时/秒杀/小黄车/购物车/点击链接；\n"
                + "8) 严禁广告法极限词：最/第一/全网/绝对/独家；\n"
                + "9) **第 1 幕必须是 3 秒强钩子**（视觉冲击/反差/特写/数字/情绪），不要平铺直叙；\n"
                + "10) 最后一幕：narration = 完整对白（呼应画面/收束情绪）+ 末尾 6 字「" + FIXED_CTA + "」收口（一字不差）；前面几幕不要提扫码/二维码/下单；\n"
                + "11) 输出全局 bgmStyle，6 选 1：street_food_yelling / cozy_explore / asmr_macro / elegant_tea / trendy_pop / emotional_story；\n"
                + "12) **严格仅按下面 JSON 返回**，不要 Markdown 代码块、不要额外文字：\n"
                + "{\n"
                + "  \"title\": \"视频标题（8-16字，画面感/情绪）\",\n"
                + "  \"bgmStyle\": \"cozy_explore\",\n"
                + "  \"scenes\": [\n"
                + "    {\"imgIdx\": 0, \"imageSummary\": \"...\", \"narration\": \"...\", \"visualPrompt\": \"...\", \"duration\": 5},\n"
                + "    ...\n"
                + "  ]\n"
                + "}\n";
    }

    private String buildMultiSceneUserText(String shopName, String userDescription, int imgCount, int n, int dur) {
        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotBlank(shopName)) {
            sb.append("店铺名称：").append(shopName).append("\n");
        }
        sb.append("商品/店铺背景：").append(StrUtil.nullToEmpty(userDescription)).append("\n");
        sb.append("共 ").append(imgCount).append(" 张图，请输出 ").append(n)
                .append(" 幕脚本（每幕 ").append(dur).append(" 秒，共 ")
                .append(n * dur).append(" 秒）。\n");
        sb.append("注意：最后一幕 narration 必须包含画面对白 + 结尾 6 字「").append(FIXED_CTA).append("」收口（一字不差）。");
        return sb.toString();
    }

    /**
     * 后处理 LLM 返回：解析 + 强制规则。
     *
     * <p>不信任 LLM 输出，全部硬规则在 Java 这层兜：</p>
     * <ul>
     *     <li>imgIdx 越界 / 重复 / 缺失 → 自动修正分配未使用的索引</li>
     *     <li>narration 字数超 → 截断；最后一幕强制覆盖为 FIXED_CTA</li>
     *     <li>visualPrompt 缺失 → 用 FALLBACK_VISUAL_PROMPT</li>
     *     <li>bgmStyle 不在白名单 → DEFAULT_BGM_STYLE</li>
     *     <li>scenes 数量不足 → 用兜底 scene 补满</li>
     *     <li>scenes 数量超出 → 截断到 n</li>
     *     <li>违禁词扫描 → 替换为安全词（中文）/ 跳过（英文）</li>
     * </ul>
     */
    AiVideoMultiSceneScriptDTO postProcessMultiScene(String rawContent, int imgCount, int n, int dur) {
        String trimmed = rawContent == null ? "" : rawContent.trim();
        if (trimmed.startsWith("```")) {
            int first = trimmed.indexOf('\n');
            int last = trimmed.lastIndexOf("```");
            if (first > 0 && last > first) trimmed = trimmed.substring(first + 1, last).trim();
        }

        String title = "新品推荐";
        String bgmStyle = DEFAULT_BGM_STYLE;
        List<RawScene> rawScenes = new ArrayList<>();

        try {
            JsonNode root = JsonUtils.parseTree(trimmed);
            String t = root.path("title").asText("").trim();
            if (!t.isEmpty()) title = sanitizeChinese(t, 30);
            String b = root.path("bgmStyle").asText("").trim();
            if (VALID_BGM_STYLES.contains(b)) bgmStyle = b;
            JsonNode sceneArr = root.get("scenes");
            if (sceneArr != null && sceneArr.isArray()) {
                for (JsonNode s : sceneArr) {
                    int idx = s.path("imgIdx").asInt(-1);
                    String summary = s.path("imageSummary").asText("").trim();
                    String narration = s.path("narration").asText("").trim();
                    String vp = s.path("visualPrompt").asText("").trim();
                    int durSec = s.path("duration").asInt(DEFAULT_SCENE_DURATION);
                    if (!ALLOWED_DURATIONS.contains(durSec)) durSec = DEFAULT_SCENE_DURATION;
                    rawScenes.add(new RawScene(idx, summary, narration, vp, durSec));
                }
            }
        } catch (Exception e) {
            log.warn("[postProcessMultiScene] 解析失败 raw={} err={}", trimmed.substring(0, Math.min(200, trimmed.length())), e.getMessage());
        }

        // ① 数量不足 → 用空 scene 补；超出 → 截断
        while (rawScenes.size() < n) rawScenes.add(new RawScene(-1, "", "", "", DEFAULT_SCENE_DURATION));
        if (rawScenes.size() > n) rawScenes = new ArrayList<>(rawScenes.subList(0, n));

        // ② imgIdx 修正：去重 + 越界回收 + 缺失补位
        rawScenes = reassignImgIdx(rawScenes, imgCount);

        // ③ 文本规整：narration 限字 + 屏蔽词、visualPrompt 缺失兜底、imageSummary 限字
        for (RawScene s : rawScenes) {
            s.narration = sanitizeChinese(s.narration, MULTI_NARRATION_MAX_CHARS);
            s.imageSummary = sanitizeChinese(s.imageSummary, 20);
            if (StrUtil.isBlank(s.visualPrompt) || s.visualPrompt.length() < 10) {
                s.visualPrompt = FALLBACK_VISUAL_PROMPT;
            }
        }

        // ④ 总时长校验：sum(duration) > 30s 时把最后几幕从 10 → 5 倒着压，直到 ≤ 30
        capTotalDuration(rawScenes);

        // ⑤ 最后一幕 narration 兜底：保留 LLM 生成的对白，仅在末尾缺 CTA 时追加（不覆盖前面）
        if (!rawScenes.isEmpty()) {
            RawScene last = rawScenes.get(rawScenes.size() - 1);
            last.narration = ensureCtaSuffix(last.narration);
        }

        // ⑥ build DTO
        List<AiVideoMultiSceneScriptDTO.Scene> scenes = new ArrayList<>(rawScenes.size());
        for (RawScene r : rawScenes) {
            scenes.add(AiVideoMultiSceneScriptDTO.Scene.builder()
                    .imgIdx(r.imgIdx)
                    .imageSummary(r.imageSummary)
                    .narration(r.narration)
                    .visualPrompt(r.visualPrompt)
                    .duration(r.duration)
                    .build());
        }
        return AiVideoMultiSceneScriptDTO.builder()
                .title(title.isEmpty() ? "新品推荐" : title)
                .bgmStyle(bgmStyle)
                .scenes(scenes)
                .build();
    }

    /**
     * imgIdx 修正：保证 0..imgCount-1 每个值最多用一次。
     * 算法：先一遍扫描收集"已用过"的合法 idx；遇到非法/重复/越界的 scene 标记待补；
     * 然后再扫一遍把"未被任何 scene 用上"的 idx 按顺序分给待补。
     * 这样原 LLM 给的合理分配尽量保留，错误 / 缺失才修。
     */
    private List<RawScene> reassignImgIdx(List<RawScene> scenes, int imgCount) {
        Set<Integer> used = new HashSet<>();
        boolean[] need = new boolean[scenes.size()];
        for (int i = 0; i < scenes.size(); i++) {
            RawScene s = scenes.get(i);
            int idx = s.imgIdx;
            if (idx < 0 || idx >= imgCount || used.contains(idx)) {
                need[i] = true;
            } else {
                used.add(idx);
            }
        }
        // 收集未使用的 idx（按顺序）
        List<Integer> remaining = new ArrayList<>();
        for (int j = 0; j < imgCount; j++) {
            if (!used.contains(j)) remaining.add(j);
        }
        int rIdx = 0;
        for (int i = 0; i < scenes.size(); i++) {
            if (!need[i]) continue;
            int idx;
            if (rIdx < remaining.size()) {
                idx = remaining.get(rIdx++);
            } else {
                // 极端：scenes 比 imgCount 多 → 复用 i % imgCount
                idx = i % Math.max(1, imgCount);
            }
            scenes.get(i).imgIdx = idx;
            used.add(idx);
        }
        return scenes;
    }

    /**
     * 最后一幕兜底：narration 末尾如果不是 FIXED_CTA 则追加。
     * <p>保留 LLM 给的完整对白；只在结尾缺 CTA 时补，不覆盖前面内容。
     * 末尾标点（。！.!）若挨着 CTA 会先剥掉，避免 "美味。微信扫码下单" 变成 "美味。。微信扫码下单"。</p>
     */
    private String ensureCtaSuffix(String narration) {
        String s = narration == null ? "" : narration.trim();
        if (s.endsWith(FIXED_CTA)) {
            return s;
        }
        // 已包含 CTA 但不在结尾（比如中间出现），不动它，仅在末尾追加
        s = s.replaceFirst("[。！!.\\s]+$", "");
        return s.isEmpty() ? FIXED_CTA : s + "，" + FIXED_CTA;
    }

    /**
     * 中文文本清洗：去序号前缀（"1. "/"第一句："）、屏蔽词替换为""、最长 maxChars 截断。
     */
    private String sanitizeChinese(String s, int maxChars) {
        if (s == null) return "";
        String x = s.replaceFirst(
                "^\\s*([0-9]+[\\.、:：\\)]|第[一二三四五六七八九十]句[:：]?)\\s*", "").trim();
        for (String bad : BLACKLIST_WORDS) {
            if (x.contains(bad)) {
                x = x.replace(bad, "");
            }
        }
        x = x.trim();
        if (x.length() > maxChars) x = x.substring(0, maxChars);
        return x;
    }

    /** Ark Key 缺失 / LLM 整体失败时，返一份合法兜底脚本，前端不崩。 */
    private AiVideoMultiSceneScriptDTO fallbackMultiSceneScript(int n, int dur) {
        // 兜底：总时长按 30s 内平均，每幕统一 5s（最稳）；如果 dur 入参指定了 10s 且 n*10 ≤30 也允许
        int perScene = (dur == 10 && n * 10 <= MULTI_MAX_TOTAL_SEC) ? 10 : DEFAULT_SCENE_DURATION;
        List<AiVideoMultiSceneScriptDTO.Scene> scenes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String narr = (i == n - 1) ? "现拍现做，新鲜出炉，" + FIXED_CTA : "现拍现做，新鲜出炉";
            scenes.add(AiVideoMultiSceneScriptDTO.Scene.builder()
                    .imgIdx(i)
                    .imageSummary("")
                    .narration(narr)
                    .visualPrompt(FALLBACK_VISUAL_PROMPT)
                    .duration(perScene)
                    .build());
        }
        return AiVideoMultiSceneScriptDTO.builder()
                .title("新品推荐")
                .bgmStyle(DEFAULT_BGM_STYLE)
                .scenes(scenes)
                .build();
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    /** 内部可变持有 imgIdx / 文本字段 / duration，方便 reassignImgIdx 与 capTotalDuration 修改 */
    private static class RawScene {
        int imgIdx;
        String imageSummary;
        String narration;
        String visualPrompt;
        int duration;
        RawScene(int imgIdx, String imageSummary, String narration, String visualPrompt, int duration) {
            this.imgIdx = imgIdx;
            this.imageSummary = imageSummary == null ? "" : imageSummary;
            this.narration = narration == null ? "" : narration;
            this.visualPrompt = visualPrompt == null ? "" : visualPrompt;
            this.duration = ALLOWED_DURATIONS.contains(duration) ? duration : DEFAULT_SCENE_DURATION;
        }
    }

    /**
     * 总时长上限：sum(scenes.duration) > MULTI_MAX_TOTAL_SEC 时倒序把 10s 的幕降成 5s。
     * <p>从最后一幕往前压（开篇钩子优先保留时长），直到 ≤ 30s 为止。
     * 全是 5s 仍超（n &gt; 6）的极端情况已被 MULTI_MAX_SCENES 上限拦掉。</p>
     */
    private void capTotalDuration(List<RawScene> scenes) {
        int total = 0;
        for (RawScene s : scenes) total += s.duration;
        if (total <= MULTI_MAX_TOTAL_SEC) return;
        for (int i = scenes.size() - 1; i >= 0 && total > MULTI_MAX_TOTAL_SEC; i--) {
            if (scenes.get(i).duration > 5) {
                total -= (scenes.get(i).duration - 5);
                scenes.get(i).duration = 5;
            }
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
