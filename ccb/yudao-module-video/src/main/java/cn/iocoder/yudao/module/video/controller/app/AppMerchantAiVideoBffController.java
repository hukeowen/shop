package cn.iocoder.yudao.module.video.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import cn.iocoder.yudao.module.merchant.service.MerchantService;
import cn.iocoder.yudao.module.video.client.ArkBffClient;
import cn.iocoder.yudao.module.video.client.JimengBffClient;
import cn.iocoder.yudao.module.video.client.TtsBffClient;
import cn.iocoder.yudao.module.video.controller.app.vo.bff.BffJimengQueryReqVO;
import cn.iocoder.yudao.module.video.controller.app.vo.bff.BffJimengSubmitReqVO;
import cn.iocoder.yudao.module.video.controller.app.vo.bff.BffTtsReqVO;
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

    @Resource
    private ArkBffClient arkBffClient;
    @Resource
    private JimengBffClient jimengBffClient;
    @Resource
    private TtsBffClient ttsBffClient;
    @Resource
    private MerchantService merchantService;

    /**
     * Ark Chat 代理。
     *
     * <p>请求体必须是合法 JSON，由调用方自行构造符合 Ark 协议的 payload；后端不解析业务字段，仅透传。</p>
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

        log.info("[bff/ark] merchantId={} bodyBytes={}", merchant.getId(), raw.length);

        String respText = arkBffClient.chat(bodyJson);
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
     * 即梦AI 提交图生视频任务。
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

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("req_key", JIMENG_I2V_REQ_KEY);
        payload.put("image_urls", Collections.singletonList(req.getImageUrl()));
        payload.put("prompt", req.getPrompt());
        payload.put("frames", req.getFrames());
        payload.put("seed", req.getSeed() == null ? -1 : req.getSeed());

        String bodyJson = JsonUtils.toJsonString(payload);
        log.info("[bff/jimeng] submit merchantId={} frames={} promptLen={}",
                merchant.getId(), req.getFrames(),
                req.getPrompt() == null ? 0 : req.getPrompt().length());

        String respText = jimengBffClient.callAction("CVSync2AsyncSubmitTask", bodyJson);
        return success(parseOrRaw(respText));
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

}
