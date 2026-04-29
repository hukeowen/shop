package cn.iocoder.yudao.module.video.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.video.config.VolcanoEngineProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AI 视频生成实现
 *
 * <p>流程：</p>
 * <ol>
 *     <li>{@link #textToSpeech(String)} — 调用火山豆包 TTS → base64 解码 → 通过 {@link FileApi} 上传 OSS → 返回 URL；</li>
 *     <li>{@link #generateVideoFromImages(List, List)} — 调用火山方舟 Seedance 图生视频异步接口，
 *         根据 task_id 轮询直到 {@code status=succeeded}，返回模型侧托管的视频 URL；</li>
 *     <li>{@link #persistToOss(String)} — 下载 Seedance 临时 URL 的视频流，通过 {@link FileApi} 落到自有 OSS。</li>
 * </ol>
 */
@Service
@Slf4j
public class VideoGenerateServiceImpl implements VideoGenerateService {

    /** 视频持久化文件大小上限（字节），超过则拒绝，避免恶意/异常消耗带宽与 OSS 空间 */
    private static final long MAX_VIDEO_BYTES = 200L * 1024 * 1024; // 200MB
    /** 下载超时（Seedance 视频最长约 60MB，5 分钟足够） */
    private static final int DOWNLOAD_READ_TIMEOUT_SECONDS = 300;

    @Resource
    private VolcanoEngineProperties properties;

    @Resource
    private FileApi fileApi;

    private final OkHttpClient llmHttpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private final OkHttpClient downloadHttpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(DOWNLOAD_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    // ==================== TTS ====================

    @Override
    public String textToSpeech(String text) {
        if (StrUtil.isBlank(text)) {
            throw new IllegalArgumentException("TTS 文本不能为空");
        }
        if (StrUtil.isBlank(properties.getAppId()) || StrUtil.isBlank(properties.getAccessToken())) {
            throw new IllegalStateException("TTS 未配置：video.volcano-engine.app-id / access-token");
        }
        log.info("[textToSpeech] 开始TTS转换，文本长度: {}", text.length());

        Map<String, Object> body = new HashMap<>();
        Map<String, String> app = new HashMap<>();
        app.put("appid", properties.getAppId());
        app.put("token", properties.getAccessToken());
        app.put("cluster", properties.getCluster());
        body.put("app", app);

        Map<String, String> user = new HashMap<>();
        user.put("uid", "merchant_tts");
        body.put("user", user);

        Map<String, Object> audio = new HashMap<>();
        audio.put("voice_type", properties.getVoiceType());
        audio.put("encoding", "mp3");
        audio.put("speed_ratio", 1.0);
        body.put("audio", audio);

        Map<String, String> request = new HashMap<>();
        request.put("reqid", IdUtil.fastSimpleUUID());
        request.put("text", text);
        request.put("operation", "query");
        body.put("request", request);

        Request httpRequest = new Request.Builder()
                .url(properties.getTtsApiUrl())
                .post(RequestBody.create(
                        JsonUtils.toJsonString(body), MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer;" + properties.getAccessToken())
                .build();

        try (Response response = llmHttpClient.newCall(httpRequest).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.error("[textToSpeech] TTS 调用失败 status={}, body={}", response.code(), responseBody);
                throw new RuntimeException("TTS API 调用失败: HTTP " + response.code());
            }
            JsonNode root = JsonUtils.parseTree(responseBody);
            String base64Audio = root.path("data").asText("");
            if (base64Audio.isEmpty()) {
                log.error("[textToSpeech] TTS 响应中 data 为空: {}", responseBody);
                throw new RuntimeException("TTS 响应异常：无 data 字段");
            }
            byte[] audioBytes = Base64.getDecoder().decode(base64Audio);
            // 上传到自有 OSS，返回可公网访问 URL
            String fileName = "tts_" + System.currentTimeMillis() + "_" + IdUtil.fastSimpleUUID() + ".mp3";
            String ossUrl = fileApi.createFile(audioBytes, fileName, "ai-video/audio/", "audio/mpeg");
            log.info("[textToSpeech] TTS 完成，音频已上传 OSS：{}", ossUrl);
            return ossUrl;
        } catch (IOException e) {
            log.error("[textToSpeech] TTS IO 异常", e);
            throw new RuntimeException("TTS API 调用 IO 异常", e);
        }
    }

    // ==================== Seedance 图生视频 ====================

    @Override
    public String generateVideoFromImages(List<String> imageUrls, List<String> lines) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            throw new IllegalArgumentException("图片列表不能为空");
        }
        if (StrUtil.isBlank(properties.getArkApiKey())) {
            throw new IllegalStateException("video.volcano-engine.ark-api-key 未配置（环境变量 ARK_API_KEY）");
        }

        String prompt = buildSeedancePrompt(lines);
        String taskId = createSeedanceTask(imageUrls, prompt);
        log.info("[generateVideoFromImages] Seedance 任务已创建 taskId={}", taskId);
        return pollSeedanceVideoUrl(taskId);
    }

    @Override
    public String generateVideoFromImagesWithPrompt(List<String> imageUrls, String visualPrompt) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            throw new IllegalArgumentException("图片列表不能为空");
        }
        if (StrUtil.isBlank(properties.getArkApiKey())) {
            throw new IllegalStateException("video.volcano-engine.ark-api-key 未配置");
        }
        if (StrUtil.isBlank(visualPrompt)) {
            throw new IllegalArgumentException("visualPrompt 不能为空");
        }
        String taskId = createSeedanceTask(imageUrls, visualPrompt);
        log.info("[generateVideoFromImagesWithPrompt] Seedance 任务已创建 taskId={} prompt={}",
                taskId, visualPrompt.length() > 100 ? visualPrompt.substring(0, 100) + "..." : visualPrompt);
        return pollSeedanceVideoUrl(taskId);
    }

    /**
     * 调用火山方舟内容生成接口创建异步任务。
     *
     * <p>Seedance 当前采用 OpenAI 风格的 messages 协议：
     * <pre>
     * {
     *   "model": "doubao-seedance-1-0-pro-250528",
     *   "content": [
     *     {"type": "text", "text": "商品介绍..."},
     *     {"type": "image_url", "image_url": {"url": "https://..."}}
     *   ]
     * }
     * </pre>
     * 返回 {@code {"id": "cgt-xxx"}}，即任务 id。
     */
    /**
     * 创建 Seedance 任务（支持首尾帧关键帧 — 改造点 ②）
     *
     * <p>imageUrls.size() >= 2 时第 2 张作为 last_image，Seedance 自动生成
     * "从图1到图2的过渡视频"，比单图运动感强 10 倍 — 这是即梦 API 的核心
     * 杀手锏，老板传 2 张图就能拍出"过程感"（切肉→穿串、空盘→上菜、远景→人挤人）。</p>
     */
    private String createSeedanceTask(List<String> imageUrls, String prompt) {
        List<Map<String, Object>> content = new ArrayList<>();
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        // 通过 --rs/--dur/--rt 控制分辨率/时长/比例；Seedance 支持 5s/10s，1080P
        textPart.put("text", prompt + "  --rs 1080p --dur 10 --rt 9:16");
        content.add(textPart);

        // 首帧（必须）
        Map<String, Object> firstImg = new HashMap<>();
        firstImg.put("type", "image_url");
        Map<String, String> firstUrl = new HashMap<>();
        firstUrl.put("url", imageUrls.get(0));
        firstImg.put("image_url", firstUrl);
        firstImg.put("role", "first_frame");
        content.add(firstImg);

        // 尾帧（可选，2+ 图时启用，让 Seedance 生成过渡视频）
        if (imageUrls.size() >= 2 && StrUtil.isNotBlank(imageUrls.get(1))) {
            Map<String, Object> lastImg = new HashMap<>();
            lastImg.put("type", "image_url");
            Map<String, String> lastUrl = new HashMap<>();
            lastUrl.put("url", imageUrls.get(1));
            lastImg.put("image_url", lastUrl);
            lastImg.put("role", "last_frame");
            content.add(lastImg);
            log.info("[createSeedanceTask] 启用首尾帧过渡：first={} last={}",
                    imageUrls.get(0), imageUrls.get(1));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", properties.getSeedanceModel());
        body.put("content", content);

        Request request = new Request.Builder()
                .url(properties.getArkGenerationUrl())
                .post(RequestBody.create(
                        JsonUtils.toJsonString(body), MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer " + properties.getArkApiKey())
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = llmHttpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.error("[createSeedanceTask] 创建失败 status={}, body={}", response.code(), responseBody);
                throw new RuntimeException("Seedance 任务创建失败: HTTP " + response.code());
            }
            JsonNode root = JsonUtils.parseTree(responseBody);
            String taskId = root.path("id").asText("");
            if (taskId.isEmpty()) {
                throw new RuntimeException("Seedance 未返回 task id：" + responseBody);
            }
            return taskId;
        } catch (IOException e) {
            throw new RuntimeException("Seedance 任务创建 IO 异常", e);
        }
    }

    /**
     * 轮询 Seedance 任务直到完成或失败。
     *
     * <p>GET {@code /api/v3/contents/generations/tasks/{id}} 响应：
     * <pre>
     * {
     *   "id": "cgt-xxx",
     *   "status": "queued|running|succeeded|failed|cancelled",
     *   "content": {"video_url": "https://..."},
     *   "error": {"code":"...","message":"..."}
     * }
     * </pre>
     */
    private String pollSeedanceVideoUrl(String taskId) {
        String url = properties.getArkGenerationUrl() + "/" + taskId;
        int maxAttempts = properties.getSeedanceMaxPollAttempts();
        long interval = properties.getSeedancePollIntervalMillis();
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Seedance 轮询被中断", e);
            }

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Authorization", "Bearer " + properties.getArkApiKey())
                    .build();

            try (Response response = llmHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("[pollSeedanceVideoUrl] 轮询异常 status={}, attempt={}",
                            response.code(), attempt);
                    continue;
                }
                String responseBody = response.body() != null ? response.body().string() : "";
                JsonNode root = JsonUtils.parseTree(responseBody);
                String status = root.path("status").asText("");
                switch (status) {
                    case "succeeded":
                        String videoUrl = root.path("content").path("video_url").asText("");
                        if (videoUrl.isEmpty()) {
                            throw new RuntimeException("Seedance 成功但未返回 video_url：" + responseBody);
                        }
                        log.info("[pollSeedanceVideoUrl] Seedance 完成 taskId={}, url={}", taskId, videoUrl);
                        return videoUrl;
                    case "failed":
                    case "cancelled":
                        String msg = root.path("error").path("message").asText("unknown");
                        throw new RuntimeException("Seedance 生成失败：" + status + " / " + msg);
                    default:
                        // queued / running — 继续轮询
                        if (attempt % 6 == 0) {
                            log.info("[pollSeedanceVideoUrl] 等待中 taskId={}, status={}, attempt={}/{}",
                                    taskId, status, attempt, maxAttempts);
                        }
                }
            } catch (IOException e) {
                log.warn("[pollSeedanceVideoUrl] 轮询 IO 异常，继续重试 attempt={}", attempt, e);
            }
        }
        throw new RuntimeException("Seedance 轮询超时（" + (maxAttempts * interval / 1000) + "s），taskId=" + taskId);
    }

    private String buildSeedancePrompt(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "商品短视频广告，突出卖点，竖屏构图，镜头缓慢推拉";
        }
        // 取前几句关键信息作为提示词，避免 prompt 过长
        StringBuilder sb = new StringBuilder();
        sb.append("根据以下文案生成一段抖音风格短视频广告，竖屏构图，镜头缓慢推拉、有节奏感：\n");
        int take = Math.min(lines.size(), 6);
        for (int i = 0; i < take; i++) {
            sb.append(lines.get(i));
            if (i < take - 1) sb.append("；");
        }
        return sb.toString();
    }

    // ==================== 持久化 ====================

    @Override
    public String persistToOss(String remoteVideoUrl) {
        if (StrUtil.isBlank(remoteVideoUrl)) {
            throw new IllegalArgumentException("视频 URL 为空，无法落盘");
        }
        Request request = new Request.Builder().url(remoteVideoUrl).get().build();
        try (Response response = downloadHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("下载视频失败：HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new RuntimeException("下载视频失败：body 为空");
            }
            long contentLength = body.contentLength();
            if (contentLength > MAX_VIDEO_BYTES) {
                throw new RuntimeException("视频大小超限：" + contentLength + " > " + MAX_VIDEO_BYTES);
            }
            byte[] videoBytes = body.bytes();
            if (videoBytes.length == 0) {
                throw new RuntimeException("下载视频为空");
            }
            if (videoBytes.length > MAX_VIDEO_BYTES) {
                throw new RuntimeException("视频大小超限：" + videoBytes.length);
            }
            String fileName = "video_" + System.currentTimeMillis() + "_" + IdUtil.fastSimpleUUID() + ".mp4";
            String ossUrl = fileApi.createFile(videoBytes, fileName, "ai-video/video/", "video/mp4");
            log.info("[persistToOss] 视频已落到 OSS：{} ({} bytes)", ossUrl, videoBytes.length);
            return ossUrl;
        } catch (IOException e) {
            throw new RuntimeException("下载视频 IO 异常", e);
        }
    }

}
