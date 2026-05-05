package cn.iocoder.yudao.module.video.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import cn.iocoder.yudao.module.merchant.enums.ai.VideoQuotaBizTypeEnum;
import cn.iocoder.yudao.module.merchant.service.MerchantService;
import cn.iocoder.yudao.module.merchant.service.MerchantVideoQuotaLogService;
import cn.iocoder.yudao.module.video.client.ArkBffClient;
import cn.iocoder.yudao.module.video.client.JimengBffClient;
import cn.iocoder.yudao.module.video.client.TtsBffClient;
import cn.iocoder.yudao.module.video.config.VolcanoEngineProperties;
import cn.iocoder.yudao.module.video.controller.app.vo.bff.BffJimengQueryReqVO;
import cn.iocoder.yudao.module.video.controller.app.vo.bff.BffJimengSubmitReqVO;
import cn.iocoder.yudao.module.video.controller.app.vo.bff.BffMultiSceneScriptReqVO;
import cn.iocoder.yudao.module.video.controller.app.vo.bff.BffRichScriptReqVO;
import cn.iocoder.yudao.module.video.controller.app.vo.bff.BffTtsReqVO;
import cn.iocoder.yudao.module.video.service.CopywritingService;
import cn.iocoder.yudao.module.video.service.dto.AiVideoMultiSceneScriptDTO;
import cn.iocoder.yudao.module.video.service.dto.AiVideoScriptDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.AI_VIDEO_MERCHANT_NOT_FOUND;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.BFF_BODY_TOO_LARGE;

/**
 * 商户小程序 - AI 视频 BFF 代理接口。
 *
 * <p>Phase 0.1：把原先暴露在前端的三把 API key（Ark / 即梦 / 豆包 TTS）下沉到服务端。
 * 商户 JWT 鉴权天然拦截匿名访问；所有端点前置商户校验。</p>
 */
@Tag(name = "商户小程序 - AI 视频 BFF 代理")
@RestController
@RequestMapping("/merchant/mini/ai-video/bff")
@Validated
@Slf4j
public class AppMerchantAiVideoBffController {

    /** Ark chat 请求体上限（64KB） */
    private static final int ARK_BODY_LIMIT = 64 * 1024;

    /** 即梦 i2v 固定模型 */
    private static final String JIMENG_I2V_REQ_KEY = "jimeng_i2v_first_v30";

    /** frames 允许值 */
    private static final Set<Integer> ALLOWED_FRAMES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(121, 241)));

    /** 复用 Jackson 解析/序列化 Ark chat 透传 body（sanitize 用）。 */
    private static final ObjectMapper ARK_BODY_MAPPER = new ObjectMapper();

    /**
     * 商户级串行锁：火山即梦免费/低配额账号同时进行的视频生成任务并发上限 = 1。
     * 即使前端 H5 没重 build（仍跑老 Promise.all 并发版本），后端这道锁兜底
     * 让同一 merchant 的 jimeng/submit 严格串行 → 避免撞 50430/Sign 401。
     */
    private static final ConcurrentHashMap<Long, ReentrantLock> SUBMIT_LOCKS = new ConcurrentHashMap<>();

    @Resource
    private ArkBffClient arkBffClient;
    @Resource
    private JimengBffClient jimengBffClient;
    @Resource
    private TtsBffClient ttsBffClient;
    @Resource
    private MerchantService merchantService;
    @Resource
    private MerchantVideoQuotaLogService merchantVideoQuotaLogService;
    @Resource
    private VolcanoEngineProperties volcanoEngineProperties;
    @Resource
    private CopywritingService copywritingService;

    /**
     * Ark Chat 代理。
     *
     * <p>请求体必须是合法 JSON。服务端会做 sanitize：</p>
     * <ul>
     *     <li>{@code model} 必须在 {@link VolcanoEngineProperties#getArkAllowedModels()} 白名单内，
     *         否则抛 {@code ARK_CHAT_FAILED}。</li>
     *     <li>{@code temperature} 若出现，必须在 {@code [0, 1]}；越界抛 {@code ARK_CHAT_FAILED}。</li>
     * </ul>
     * <p>校验通过后，重新序列化 body 再透传到 Ark（即使原始 body 有未使用的字段也会透传）。</p>
     */
    @PostMapping("/ark/chat")
    @Operation(summary = "AI 对话（Ark Chat 代理）")
    public CommonResult<Object> arkChat(HttpServletRequest request) throws IOException {
        MerchantDO merchant = getMerchantOrThrow();

        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase().contains("application/json")) {
            throw exception(cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.ARK_CHAT_FAILED,
                    "Content-Type 必须为 application/json");
        }

        byte[] raw = readBodyWithLimit(request, ARK_BODY_LIMIT);
        String bodyJson = new String(raw, StandardCharsets.UTF_8);

        // 1. 解析 + 校验白名单 / temperature
        Map<String, Object> bodyMap;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = ARK_BODY_MAPPER.readValue(bodyJson, Map.class);
            bodyMap = parsed;
        } catch (JsonProcessingException e) {
            throw exception(cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.ARK_CHAT_FAILED,
                    "请求体不是合法 JSON");
        }
        validateArkBody(bodyMap);

        // 2. 重新序列化再透传（sanitize 路径）
        String sanitizedJson;
        try {
            sanitizedJson = ARK_BODY_MAPPER.writeValueAsString(bodyMap);
        } catch (JsonProcessingException e) {
            throw exception(cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.ARK_CHAT_FAILED,
                    "请求体序列化失败");
        }

        log.info("[bff/ark] merchantId={} bodyBytes={} model={}",
                merchant.getId(), raw.length, bodyMap.get("model"));

        String respText = arkBffClient.chat(sanitizedJson);
        // Ark 返回也是 JSON；尝试解析为对象以便 CommonResult 序列化
        Object parsed;
        try {
            parsed = JsonUtils.parseObject(respText, Object.class);
        } catch (Exception e) {
            parsed = respText;
        }
        return success(parsed);
    }

    /**
     * 校验 Ark chat 请求体：model 白名单 + temperature 上下界。违反则抛 {@code ARK_CHAT_FAILED}。
     */
    private void validateArkBody(Map<String, Object> body) {
        Object modelObj = body.get("model");
        if (!(modelObj instanceof String) || ((String) modelObj).isEmpty()) {
            throw exception(cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.ARK_CHAT_FAILED,
                    "缺少 model");
        }
        String model = (String) modelObj;
        Set<String> allowed = volcanoEngineProperties.getArkAllowedModels();
        if (allowed == null || !allowed.contains(model)) {
            throw exception(cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.ARK_CHAT_FAILED,
                    "模型 " + model + " 未授权");
        }
        Object tempObj = body.get("temperature");
        if (tempObj != null) {
            double temperature;
            if (tempObj instanceof Number) {
                temperature = ((Number) tempObj).doubleValue();
            } else {
                throw exception(cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.ARK_CHAT_FAILED,
                        "temperature 类型非法");
            }
            if (temperature < 0.0 || temperature > 1.0) {
                throw exception(cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.ARK_CHAT_FAILED,
                        "temperature 越界");
            }
        }
    }

    /**
     * 即梦AI 提交图生视频任务。
     *
     * <p>Phase 0.3.2：引入预扣/回补模式。</p>
     * <ol>
     *     <li>前置帧数校验（失败不扣）</li>
     *     <li>{@link MerchantService#decreaseVideoQuota} 原子扣 1——SQL 里有
     *         {@code video_quota_remaining >= 1} 守护，并发安全；余额不足抛
     *         {@code VIDEO_QUOTA_INSUFFICIENT}，前端据此引导跳套餐页。</li>
     *     <li>远程调即梦。成功直接返；失败兜底
     *         {@link MerchantService#increaseVideoQuota}（biz_type=VIDEO_REFUND）。</li>
     * </ol>
     *
     * <p>扣减 / 回补方法都是 REQUIRES_NEW 独立事务，HTTP 远程不会被事务包裹。
     * 如果回补本身失败（极端异常），我们吞异常只打日志，把原始错误抛给 GlobalExceptionHandler——
     * 否则会覆盖更有意义的上游报错；这种罕见 case 可由 {@code merchant_video_quota_log}
     * 对账补偿（后续 Phase 的定时任务再做）。</p>
     */
    @PostMapping("/jimeng/submit")
    @Operation(summary = "即梦AI 提交图生视频任务")
    public CommonResult<Object> jimengSubmit(@Valid @RequestBody BffJimengSubmitReqVO req) {
        MerchantDO merchant = getMerchantOrThrow();
        if (!ALLOWED_FRAMES.contains(req.getFrames())) {
            throw exception(
                    cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.JIMENG_CALL_FAILED,
                    "frames 仅支持 121 或 241");
        }

        // 单商户串行锁：避免前端并发提交多张图触发火山 50430/Sign 401
        // （前端已改串行，但 H5 未重 build 的旧浏览器仍可能并发）
        // 持锁期间真正调火山，下一请求等前一个返回再发。最多等 5 分钟，超时报忙。
        ReentrantLock lock = SUBMIT_LOCKS.computeIfAbsent(merchant.getId(), k -> new ReentrantLock(true));
        boolean locked;
        try {
            locked = lock.tryLock(5, java.util.concurrent.TimeUnit.MINUTES);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw exception(
                    cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.JIMENG_CALL_FAILED,
                    "请求被中断，请重试");
        }
        if (!locked) {
            throw exception(
                    cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.JIMENG_CALL_FAILED,
                    "您有视频正在生成中，请稍后再提交");
        }
        try {
            return jimengSubmitInternal(merchant, req);
        } finally {
            lock.unlock();
        }
    }

    private CommonResult<Object> jimengSubmitInternal(MerchantDO merchant, BffJimengSubmitReqVO req) {
        // 1) 预扣 1 条（quota 不足→抛 VIDEO_QUOTA_INSUFFICIENT）
        // bizId 暂用临时 UUID；真正的即梦 taskId 在下游返回后可由业务回执补登
        // Phase 0.3.3：decreaseVideoQuota 返回类型由 int 改成 QuotaChangeResult，这里只用余额,
        // logId 由 {@code merchant_package_order.quota_log_id} / 对账任务消费，不在本路径需要。
        String preDebitBizId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        merchantService.decreaseVideoQuota(
                merchant.getId(), 1,
                VideoQuotaBizTypeEnum.VIDEO_GEN.getCode(),
                preDebitBizId,
                "即梦图生视频扣减");

        // 2) 远程调用（事务之外）
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("req_key", JIMENG_I2V_REQ_KEY);
        payload.put("image_urls", Collections.singletonList(req.getImageUrl()));
        payload.put("prompt", req.getPrompt());
        payload.put("frames", req.getFrames());
        payload.put("seed", req.getSeed() == null ? -1 : req.getSeed());

        String bodyJson = JsonUtils.toJsonString(payload);
        log.info("[bff/jimeng] submit merchantId={} frames={} promptLen={} preDebitBizId={}",
                merchant.getId(), req.getFrames(),
                req.getPrompt() == null ? 0 : req.getPrompt().length(),
                preDebitBizId);

        try {
            String respText = jimengBffClient.callAction("CVSync2AsyncSubmitTask", bodyJson);
            // 成功：把即梦 task_id 回写到预扣流水 remark，完成审计链（非关键路径，失败不影响主流程）
            try {
                String taskId = extractJimengTaskId(respText);
                if (taskId != null && !taskId.isEmpty()) {
                    merchantVideoQuotaLogService.appendTaskIdToRemark(
                            merchant.getId(), preDebitBizId, taskId);
                }
            } catch (Exception auditEx) {
                log.warn("[bff/jimeng] 审计链回写 taskId 失败 merchantId={} preDebitBizId={}",
                        merchant.getId(), preDebitBizId, auditEx);
            }
            return success(parseOrRaw(respText));
        } catch (RuntimeException e) {
            // 3) 失败：回补 1 条
            try {
                String msg = e.getMessage() == null ? "unknown" : e.getMessage();
                // 截断备注，避免超过 remark 列长度（255）
                String truncated = msg.length() > 80 ? msg.substring(0, 80) : msg;
                merchantService.increaseVideoQuota(
                        merchant.getId(), 1,
                        VideoQuotaBizTypeEnum.VIDEO_REFUND.getCode(),
                        preDebitBizId,
                        "即梦调用失败自动回补: " + truncated);
            } catch (Exception refundEx) {
                log.error("[bff/jimeng] 扣减成功但回补失败 merchantId={} preDebitBizId={}",
                        merchant.getId(), preDebitBizId, refundEx);
            }
            throw e;
        }
    }

    /**
     * 即梦AI 查询图生视频任务结果。
     */
    @PostMapping("/jimeng/query")
    @Operation(summary = "即梦AI 查询任务结果")
    public CommonResult<Object> jimengQuery(@Valid @RequestBody BffJimengQueryReqVO req) {
        MerchantDO merchant = getMerchantOrThrow();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("req_key", JIMENG_I2V_REQ_KEY);
        payload.put("task_id", req.getTaskId());

        String bodyJson = JsonUtils.toJsonString(payload);
        log.info("[bff/jimeng] query merchantId={} taskId={}", merchant.getId(), req.getTaskId());

        String respText = jimengBffClient.callAction("CVSync2AsyncGetResult", bodyJson);
        return success(parseOrRaw(respText));
    }

    /**
     * 富脚本生成（即梦 API 优化第一波 ③）。
     *
     * <p>把"老板写不出的运镜词汇"由后端 LLM 自动补齐：一次输出</p>
     * <ul>
     *     <li>{@code lines}: 6-10 句口播文案（TTS 念）</li>
     *     <li>{@code visualPrompt}: 一句英文运镜+风格 prompt（喂 Seedance）</li>
     *     <li>{@code bgmStyle}: 6 选 1 BGM 风格 key（sidecar 据此选 mp3 混音）</li>
     * </ul>
     *
     * <p>前端 ai-video 流程：先调本接口拿 bgmStyle 推荐（auto-fill confirm 页 BGM 选择器），
     * 同时把 visualPrompt 作为各幕 Seedance 的基底英文运镜词。</p>
     */
    @PostMapping("/script/rich")
    @Operation(summary = "AI 视频 - 富脚本生成（lines + visualPrompt + bgmStyle）")
    public CommonResult<AiVideoScriptDTO> generateRichScript(@Valid @RequestBody BffRichScriptReqVO req) {
        MerchantDO merchant = getMerchantOrThrow();
        log.info("[bff/script/rich] merchantId={} shopName={} descLen={}",
                merchant.getId(),
                req.getShopName() == null ? "" : req.getShopName(),
                req.getUserDescription() == null ? 0 : req.getUserDescription().length());
        String shopName = req.getShopName();
        if (shopName == null || shopName.isEmpty()) {
            // 没传店铺名，用 merchant_info.name 兜底，让 LLM prompt 上下文更完整
            shopName = merchant.getName();
        }
        AiVideoScriptDTO dto = copywritingService.generateRichScript(shopName, req.getUserDescription());
        return success(dto);
    }

    /**
     * 多幕分镜脚本生成（即梦 API 优化第一波 ③ 的多幕扩展版）。
     *
     * <p>用豆包 vision-pro 看图分幕：N 张图 → N 个 scene，每幕自带
     * narration + visualPrompt + imageSummary，外加全局 bgmStyle。</p>
     *
     * <p>把前端 {@code scriptLlm.js} 自拼 system prompt 调 ark/chat 收到后端，
     * 业务规则（imgIdx 不重不漏、最后一幕固定 CTA、屏蔽词剔除、字数截断、
     * bgmStyle 白名单）全部在 service 层 postProcess 强制兜底，前端只负责显示。</p>
     */
    @PostMapping("/script/multi-scene")
    @Operation(summary = "AI 视频 - 多幕分镜脚本（视觉 LLM 看图）")
    public CommonResult<AiVideoMultiSceneScriptDTO> generateMultiSceneScript(
            @Valid @RequestBody BffMultiSceneScriptReqVO req) {
        MerchantDO merchant = getMerchantOrThrow();
        log.info("[bff/script/multi-scene] merchantId={} imgs={} n={} dur={}",
                merchant.getId(),
                req.getImageUrls() == null ? 0 : req.getImageUrls().size(),
                req.getSceneCount(), req.getSceneDuration());
        String shopName = req.getShopName();
        if (shopName == null || shopName.isEmpty()) {
            shopName = merchant.getName();
        }
        AiVideoMultiSceneScriptDTO dto = copywritingService.generateMultiSceneScript(
                shopName,
                req.getUserDescription(),
                req.getImageUrls(),
                req.getSceneCount() == null ? req.getImageUrls().size() : req.getSceneCount(),
                req.getSceneDuration() == null ? 10 : req.getSceneDuration());
        return success(dto);
    }

    /**
     * 豆包 TTS 合成，直接返回 MP3 字节流（{@code audio/mpeg}）。
     */
    @PostMapping("/tts")
    @Operation(summary = "豆包 TTS 合成 (MP3)")
    public ResponseEntity<byte[]> tts(@Valid @RequestBody BffTtsReqVO req) {
        MerchantDO merchant = getMerchantOrThrow();
        log.info("[bff/tts] merchantId={} textLen={} voice={}",
                merchant.getId(), req.getText().length(),
                req.getVoice() == null ? "" : req.getVoice());

        byte[] mp3 = ttsBffClient.generateMp3(req.getText(), req.getVoice());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
        headers.setContentLength(mp3.length);
        headers.setCacheControl("no-cache");
        return new ResponseEntity<>(mp3, headers, org.springframework.http.HttpStatus.OK);
    }

    // ==================== 内部工具 ====================

    private MerchantDO getMerchantOrThrow() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        MerchantDO merchant = userId == null ? null : merchantService.getMerchantByUserId(userId);
        if (merchant == null) {
            throw exception(AI_VIDEO_MERCHANT_NOT_FOUND);
        }
        return merchant;
    }

    /**
     * 读完 request body，若超过限制则抛 {@code BFF_BODY_TOO_LARGE}。
     */
    private static byte[] readBodyWithLimit(HttpServletRequest request, int limit) throws IOException {
        java.io.InputStream in = request.getInputStream();
        byte[] buf = new byte[Math.min(8192, limit + 1)];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int read;
        int total = 0;
        while ((read = in.read(buf)) != -1) {
            total += read;
            if (total > limit) {
                throw exception(BFF_BODY_TOO_LARGE);
            }
            out.write(buf, 0, read);
        }
        return out.toByteArray();
    }

    /**
     * 尝试把响应解析为 JSON 对象；失败则保留原文。
     */
    private static Object parseOrRaw(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        try {
            Object parsed = JsonUtils.parseObject(text, Object.class);
            return parsed == null ? text : parsed;
        } catch (Exception e) {
            return text;
        }
    }

    /**
     * 从即梦 CVSync2AsyncSubmitTask 响应里抽取 {@code data.task_id}。
     * 响应形如 {@code {"code":10000,"data":{"task_id":"xxx"},...}}。找不到返回 null。
     */
    @SuppressWarnings("unchecked")
    private static String extractJimengTaskId(String respText) {
        if (respText == null || respText.isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> root = JsonUtils.parseObject(respText, Map.class);
            if (root == null) {
                return null;
            }
            Object dataObj = root.get("data");
            if (!(dataObj instanceof Map)) {
                return null;
            }
            Object taskId = ((Map<String, Object>) dataObj).get("task_id");
            return taskId == null ? null : taskId.toString();
        } catch (Exception e) {
            return null;
        }
    }

}
