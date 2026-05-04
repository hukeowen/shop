package cn.iocoder.yudao.module.video.service;

import cn.iocoder.yudao.module.video.config.VolcanoEngineProperties;
import cn.iocoder.yudao.module.video.service.dto.AiVideoMultiSceneScriptDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 {@link CopywritingServiceImpl#postProcessMultiScene} 的业务规则后处理。
 *
 * <p>不调真 LLM —— 直接喂各种 LLM 假回包给 postProcessMultiScene，
 * 验证：</p>
 * <ul>
 *     <li>imgIdx 修正：去重 / 越界 / 缺失补位</li>
 *     <li>narration 字数限制 + 屏蔽词剔除</li>
 *     <li>最后一幕强制覆盖为 FIXED_CTA</li>
 *     <li>visualPrompt 缺失兜底</li>
 *     <li>bgmStyle 白名单兜底</li>
 *     <li>scenes 数量过多 / 不足的兜底</li>
 *     <li>非法 JSON 整体兜底</li>
 *     <li>fallbackMultiSceneScript 无 ARK key 时的形态</li>
 * </ul>
 */
class CopywritingServiceMultiSceneTest {

    private static final String FIXED_CTA = "微信扫码下单";
    private CopywritingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CopywritingServiceImpl();
        VolcanoEngineProperties props = new VolcanoEngineProperties();
        // 留空 ARK_API_KEY，让 generateMultiSceneScript 走兜底
        ReflectionTestUtils.setField(service, "properties", props);
    }

    @Test
    void postProcess_normal_keepsLLMOutput() {
        String raw = "{\n" +
                "  \"title\":\"小院烤地瓜\",\n" +
                "  \"bgmStyle\":\"street_food_yelling\",\n" +
                "  \"scenes\":[\n" +
                "    {\"imgIdx\":0,\"imageSummary\":\"流糖心\",\"narration\":\"现烤蜜薯流糖心\",\"visualPrompt\":\"Hand brushing chili oil on grilling skewers, push-in macro lens, cinematic golden hour\"},\n" +
                "    {\"imgIdx\":1,\"imageSummary\":\"金黄外皮\",\"narration\":\"外皮金黄香气扑鼻\",\"visualPrompt\":\"Slow motion close-up of golden potato skin cracking, food photography, film grain\"},\n" +
                "    {\"imgIdx\":2,\"imageSummary\":\"扫码二维码\",\"narration\":\"微信扫码下单\",\"visualPrompt\":\"Static QR code shot, warm lighting, vlog style\"}\n" +
                "  ]\n" +
                "}";
        AiVideoMultiSceneScriptDTO dto = service.postProcessMultiScene(raw, 3, 3, 10);

        assertEquals("小院烤地瓜", dto.getTitle());
        assertEquals("street_food_yelling", dto.getBgmStyle());
        assertEquals(3, dto.getScenes().size());
        // imgIdx 不重不漏
        assertImgIdxComplete(dto.getScenes(), 3);
        // 最后一幕强制 CTA
        assertEquals(FIXED_CTA, dto.getScenes().get(2).getNarration());
    }

    @Test
    void postProcess_duplicateImgIdx_isReassigned() {
        // LLM 给重复 imgIdx，必须自动修复使每张图都用上
        String raw = "{\n" +
                "  \"title\":\"测试\",\n" +
                "  \"bgmStyle\":\"cozy_explore\",\n" +
                "  \"scenes\":[\n" +
                "    {\"imgIdx\":0,\"narration\":\"句一\",\"visualPrompt\":\"prompt one push-in macro cinematic\"},\n" +
                "    {\"imgIdx\":0,\"narration\":\"句二\",\"visualPrompt\":\"prompt two pan slow motion film grain\"},\n" +
                "    {\"imgIdx\":0,\"narration\":\"微信扫码下单\",\"visualPrompt\":\"prompt three tilt-up vlog\"}\n" +
                "  ]\n" +
                "}";
        AiVideoMultiSceneScriptDTO dto = service.postProcessMultiScene(raw, 3, 3, 10);
        assertImgIdxComplete(dto.getScenes(), 3);
    }

    @Test
    void postProcess_outOfRangeImgIdx_isClampedAndReassigned() {
        String raw = "{\n" +
                "  \"title\":\"测试\",\n" +
                "  \"bgmStyle\":\"cozy_explore\",\n" +
                "  \"scenes\":[\n" +
                "    {\"imgIdx\":99,\"narration\":\"句一\",\"visualPrompt\":\"prompt one push-in macro cinematic\"},\n" +
                "    {\"imgIdx\":-3,\"narration\":\"句二\",\"visualPrompt\":\"prompt two pan slow motion film grain\"},\n" +
                "    {\"imgIdx\":1,\"narration\":\"微信扫码下单\",\"visualPrompt\":\"prompt three tilt-up vlog\"}\n" +
                "  ]\n" +
                "}";
        AiVideoMultiSceneScriptDTO dto = service.postProcessMultiScene(raw, 3, 3, 10);
        assertImgIdxComplete(dto.getScenes(), 3);
    }

    @Test
    void postProcess_lastNarrationKeepsLLMCopy_appendsCtaIfMissing() {
        // LLM 给的最后一幕只有对白没结尾 CTA → 兜底追加，不替换前面
        String raw = "{\n" +
                "  \"title\":\"测试\",\n" +
                "  \"bgmStyle\":\"cozy_explore\",\n" +
                "  \"scenes\":[\n" +
                "    {\"imgIdx\":0,\"narration\":\"句一\",\"visualPrompt\":\"push-in macro cinematic\"},\n" +
                "    {\"imgIdx\":1,\"narration\":\"句二\",\"visualPrompt\":\"pan slow motion film grain\"},\n" +
                "    {\"imgIdx\":2,\"narration\":\"出锅热腾腾\",\"visualPrompt\":\"tilt-up vlog\"}\n" +
                "  ]\n" +
                "}";
        AiVideoMultiSceneScriptDTO dto = service.postProcessMultiScene(raw, 3, 3, 10);
        // 最后一幕 = 原对白 + ， + CTA（不再覆盖丢前面对白）
        assertEquals("出锅热腾腾，" + FIXED_CTA, dto.getScenes().get(2).getNarration());
        // 前面两幕保留
        assertEquals("句一", dto.getScenes().get(0).getNarration());
        assertEquals("句二", dto.getScenes().get(1).getNarration());
    }

    @Test
    void postProcess_lastNarrationAlreadyHasCta_isNotDuplicated() {
        // LLM 已经按 prompt 末尾收口 → 不重复追加
        String raw = "{\n" +
                "  \"title\":\"测试\",\n" +
                "  \"bgmStyle\":\"cozy_explore\",\n" +
                "  \"scenes\":[\n" +
                "    {\"imgIdx\":0,\"narration\":\"句一\",\"visualPrompt\":\"push-in macro cinematic\"},\n" +
                "    {\"imgIdx\":1,\"narration\":\"句二\",\"visualPrompt\":\"pan slow motion film grain\"},\n" +
                "    {\"imgIdx\":2,\"narration\":\"现拍现做新鲜出炉，" + FIXED_CTA + "\",\"visualPrompt\":\"tilt-up vlog\"}\n" +
                "  ]\n" +
                "}";
        AiVideoMultiSceneScriptDTO dto = service.postProcessMultiScene(raw, 3, 3, 10);
        String last = dto.getScenes().get(2).getNarration();
        assertTrue(last.endsWith(FIXED_CTA));
        // 不应重复出现两次 CTA
        int firstIdx = last.indexOf(FIXED_CTA);
        assertEquals(firstIdx, last.lastIndexOf(FIXED_CTA), "CTA 不应重复");
    }

    @Test
    void postProcess_blacklistWords_areStripped() {
        // narration 含"老板""限时""最"应被剔除
        String raw = "{\n" +
                "  \"title\":\"老板大放价\",\n" +
                "  \"bgmStyle\":\"cozy_explore\",\n" +
                "  \"scenes\":[\n" +
                "    {\"imgIdx\":0,\"narration\":\"老板赔本秒杀\",\"visualPrompt\":\"push-in macro cinematic\"},\n" +
                "    {\"imgIdx\":1,\"narration\":\"全网最低限时购买\",\"visualPrompt\":\"pan slow motion film grain\"},\n" +
                "    {\"imgIdx\":2,\"narration\":\"微信扫码下单\",\"visualPrompt\":\"tilt-up vlog\"}\n" +
                "  ]\n" +
                "}";
        AiVideoMultiSceneScriptDTO dto = service.postProcessMultiScene(raw, 3, 3, 10);
        // 第 1 幕不应再含黑词
        String n1 = dto.getScenes().get(0).getNarration();
        assertFalse(n1.contains("老板"), "老板应被剔除：" + n1);
        assertFalse(n1.contains("赔本"), "赔本应被剔除：" + n1);
        assertFalse(n1.contains("秒杀"), "秒杀应被剔除：" + n1);
        // 第 2 幕极限词 + 限时词
        String n2 = dto.getScenes().get(1).getNarration();
        assertFalse(n2.contains("最"), "最应被剔除：" + n2);
        assertFalse(n2.contains("全网"), "全网应被剔除：" + n2);
        assertFalse(n2.contains("限时"), "限时应被剔除：" + n2);
        // title 含"老板"也应剔
        assertFalse(dto.getTitle().contains("老板"), "title 不应含老板：" + dto.getTitle());
    }

    @Test
    void postProcess_narrationOver36Chars_isTruncated() {
        String longNarr = "蜜薯流糖心外皮金黄香气扑鼻流糖心外皮金黄香气扑鼻流糖心外皮金黄香气扑鼻"; // > 36
        String raw = "{\n" +
                "  \"title\":\"t\",\n" +
                "  \"bgmStyle\":\"cozy_explore\",\n" +
                "  \"scenes\":[\n" +
                "    {\"imgIdx\":0,\"narration\":\"" + longNarr + "\",\"visualPrompt\":\"push-in macro cinematic\"},\n" +
                "    {\"imgIdx\":1,\"narration\":\"微信扫码下单\",\"visualPrompt\":\"pan film grain\"}\n" +
                "  ]\n" +
                "}";
        AiVideoMultiSceneScriptDTO dto = service.postProcessMultiScene(raw, 2, 2, 10);
        assertTrue(dto.getScenes().get(0).getNarration().length() <= 36);
    }

    @Test
    void postProcess_missingVisualPrompt_fallsBack() {
        String raw = "{\n" +
                "  \"title\":\"t\",\n" +
                "  \"bgmStyle\":\"cozy_explore\",\n" +
                "  \"scenes\":[\n" +
                "    {\"imgIdx\":0,\"narration\":\"句一\",\"visualPrompt\":\"\"},\n" +
                "    {\"imgIdx\":1,\"narration\":\"微信扫码下单\"}\n" +
                "  ]\n" +
                "}";
        AiVideoMultiSceneScriptDTO dto = service.postProcessMultiScene(raw, 2, 2, 10);
        // 兜底 prompt 至少包含运镜/风格关键词之一
        for (AiVideoMultiSceneScriptDTO.Scene s : dto.getScenes()) {
            assertNotNull(s.getVisualPrompt());
            assertTrue(s.getVisualPrompt().length() > 10);
        }
    }

    @Test
    void postProcess_invalidBgmStyle_fallsBackToDefault() {
        String raw = "{\n" +
                "  \"title\":\"t\",\n" +
                "  \"bgmStyle\":\"random_unknown_style\",\n" +
                "  \"scenes\":[\n" +
                "    {\"imgIdx\":0,\"narration\":\"微信扫码下单\",\"visualPrompt\":\"push-in macro cinematic\"}\n" +
                "  ]\n" +
                "}";
        AiVideoMultiSceneScriptDTO dto = service.postProcessMultiScene(raw, 1, 1, 10);
        assertEquals("cozy_explore", dto.getBgmStyle());
    }

    @Test
    void postProcess_validBgmStyles_areAllAccepted() {
        for (String style : Arrays.asList("street_food_yelling", "cozy_explore", "asmr_macro",
                "elegant_tea", "trendy_pop", "emotional_story")) {
            String raw = "{\"title\":\"t\",\"bgmStyle\":\"" + style + "\",\"scenes\":[" +
                    "{\"imgIdx\":0,\"narration\":\"微信扫码下单\",\"visualPrompt\":\"push-in macro cinematic\"}]}";
            AiVideoMultiSceneScriptDTO dto = service.postProcessMultiScene(raw, 1, 1, 10);
            assertEquals(style, dto.getBgmStyle(), "合法风格应保留: " + style);
        }
    }

    @Test
    void postProcess_tooManyScenes_areTruncated() {
        StringBuilder sb = new StringBuilder("{\"title\":\"t\",\"bgmStyle\":\"cozy_explore\",\"scenes\":[");
        for (int i = 0; i < 10; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"imgIdx\":").append(i)
                    .append(",\"narration\":\"句\",\"visualPrompt\":\"push-in macro\"}");
        }
        sb.append("]}");
        AiVideoMultiSceneScriptDTO dto = service.postProcessMultiScene(sb.toString(), 3, 3, 10);
        assertEquals(3, dto.getScenes().size());
    }

    @Test
    void postProcess_tooFewScenes_arePadded() {
        // LLM 只给了 1 幕，但前端需要 3 幕
        String raw = "{\"title\":\"t\",\"bgmStyle\":\"cozy_explore\",\"scenes\":[" +
                "{\"imgIdx\":0,\"narration\":\"句一\",\"visualPrompt\":\"push-in macro\"}" +
                "]}";
        AiVideoMultiSceneScriptDTO dto = service.postProcessMultiScene(raw, 3, 3, 10);
        assertEquals(3, dto.getScenes().size());
        assertImgIdxComplete(dto.getScenes(), 3);
        // 最后一幕仍然 = CTA
        assertEquals(FIXED_CTA, dto.getScenes().get(2).getNarration());
    }

    @Test
    void postProcess_invalidJson_returnsSafeFallback() {
        String raw = "this is not valid json";
        AiVideoMultiSceneScriptDTO dto = service.postProcessMultiScene(raw, 3, 3, 10);
        assertNotNull(dto);
        assertEquals(3, dto.getScenes().size());
        assertImgIdxComplete(dto.getScenes(), 3);
        assertEquals(FIXED_CTA, dto.getScenes().get(2).getNarration());
    }

    @Test
    void postProcess_markdownCodeFence_isStripped() {
        String raw = "```json\n{\"title\":\"t\",\"bgmStyle\":\"cozy_explore\",\"scenes\":[" +
                "{\"imgIdx\":0,\"narration\":\"微信扫码下单\",\"visualPrompt\":\"push-in macro\"}" +
                "]}\n```";
        AiVideoMultiSceneScriptDTO dto = service.postProcessMultiScene(raw, 1, 1, 10);
        assertEquals(1, dto.getScenes().size());
        assertEquals(FIXED_CTA, dto.getScenes().get(0).getNarration());
    }

    @Test
    void generateMultiSceneScript_noArkKey_returnsFallback() {
        // properties.arkApiKey 为空，应返兜底
        AiVideoMultiSceneScriptDTO dto = service.generateMultiSceneScript(
                "测试店", "卖烤地瓜",
                Arrays.asList("https://oss/a.jpg", "https://oss/b.jpg", "https://oss/c.jpg"),
                3, 10);
        assertNotNull(dto);
        assertEquals(3, dto.getScenes().size());
        assertImgIdxComplete(dto.getScenes(), 3);
        // 兜底末幕也保留对白前缀 + CTA 收口（不再仅光秃 6 字）
        String last = dto.getScenes().get(2).getNarration();
        assertTrue(last.endsWith(FIXED_CTA), "末幕应以 CTA 收口：" + last);
        assertTrue(last.length() > FIXED_CTA.length(), "末幕应含对白前缀：" + last);
        // 兜底 BGM = cozy_explore
        assertEquals("cozy_explore", dto.getBgmStyle());
    }

    @Test
    void generateMultiSceneScript_emptyImageUrls_returnsFallback() {
        AiVideoMultiSceneScriptDTO dto = service.generateMultiSceneScript(
                "测试店", "desc", java.util.Collections.emptyList(), 3, 10);
        assertNotNull(dto);
        assertFalse(dto.getScenes().isEmpty());
    }

    @Test
    void generateMultiSceneScript_clampsSceneCountToMaxThree() {
        AiVideoMultiSceneScriptDTO dto = service.generateMultiSceneScript(
                "店", "desc",
                Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"), // 10 张图
                10, 10); // 请求 10 幕
        assertTrue(dto.getScenes().size() <= 3, "幕数应 cap 3 (业务约束：≤3 张图)");
    }

    @Test
    void generateMultiSceneScript_clampsSceneCountToMinOne() {
        AiVideoMultiSceneScriptDTO dto = service.generateMultiSceneScript(
                "店", "desc", Arrays.asList("a"), 0, 0);
        assertEquals(1, dto.getScenes().size());
    }

    @Test
    void generateMultiSceneScript_sceneCountCappedToImageCount() {
        // 2 张图但请求 5 幕 → cap 到 2
        AiVideoMultiSceneScriptDTO dto = service.generateMultiSceneScript(
                "店", "desc", Arrays.asList("a", "b"), 5, 10);
        assertEquals(2, dto.getScenes().size());
    }

    private static void assertImgIdxComplete(List<AiVideoMultiSceneScriptDTO.Scene> scenes, int n) {
        Set<Integer> seen = new HashSet<>();
        for (AiVideoMultiSceneScriptDTO.Scene s : scenes) {
            assertNotNull(s.getImgIdx(), "imgIdx 不应为 null");
            assertTrue(s.getImgIdx() >= 0 && s.getImgIdx() < n,
                    "imgIdx 越界: " + s.getImgIdx() + " / " + n);
            seen.add(s.getImgIdx());
        }
        assertEquals(n, seen.size(),
                "每张图都应被恰好用一次，实际 imgIdx set=" + seen + " expected n=" + n);
    }
}
