package cn.iocoder.yudao.module.video.service;

import cn.hutool.core.util.IdUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.video.config.VolcanoEngineProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 视频生成 Service 实现类
 * 集成火山引擎一键成片 API + TTS API
 */
@Service
@Slf4j
public class VideoGenerateServiceImpl implements VideoGenerateService {

    @Resource
    private VolcanoEngineProperties volcanoProperties;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    @Override
    public String textToSpeech(String text) {
        log.info("[textToSpeech] 开始TTS转换，文本长度: {}", text.length());

        Map<String, Object> body = new HashMap<>();
        // app 信息
        Map<String, String> app = new HashMap<>();
        app.put("appid", volcanoProperties.getAppId());
        app.put("token", volcanoProperties.getAccessToken());
        app.put("cluster", volcanoProperties.getCluster());
        body.put("app", app);
        // 用户信息
        Map<String, String> user = new HashMap<>();
        user.put("uid", "merchant_tts");
        body.put("user", user);
        // 音频参数
        Map<String, Object> audio = new HashMap<>();
        audio.put("voice_type", volcanoProperties.getVoiceType());
        audio.put("encoding", "mp3");
        audio.put("speed_ratio", 1.0);
        body.put("audio", audio);
        // 请求参数
        Map<String, String> request = new HashMap<>();
        request.put("reqid", IdUtil.fastSimpleUUID());
        request.put("text", text);
        request.put("operation", "query");
        body.put("request", request);

        Request httpRequest = new Request.Builder()
                .url(volcanoProperties.getTtsApiUrl())
                .post(RequestBody.create(JsonUtils.toJsonString(body), MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer;" + volcanoProperties.getAccessToken())
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("TTS API调用失败: " + response.code());
            }
            String responseBody = response.body() != null ? response.body().string() : "";
            log.info("[textToSpeech] TTS API响应成功");
            // 解析响应，提取 base64 音频数据
            Map<String, Object> result = JsonUtils.parseObject(responseBody, Map.class);
            if (result != null && result.containsKey("data")) {
                String audioBase64 = (String) result.get("data");
                // 解码 base64 音频数据，保存为临时文件并返回可访问的URL
                byte[] audioBytes = Base64.getDecoder().decode(audioBase64);
                String fileName = "tts_" + System.currentTimeMillis() + ".mp3";
                java.io.File dir = new java.io.File("/tmp/audio/");
                if (!dir.exists()) { dir.mkdirs(); }
                java.io.File audioFile = new java.io.File(dir, fileName);
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(audioFile)) {
                    fos.write(audioBytes);
                }
                return "/audio/" + fileName;
            }
            throw new RuntimeException("TTS API返回数据异常: " + responseBody);
        } catch (IOException e) {
            log.error("[textToSpeech] TTS API调用异常", e);
            throw new RuntimeException("TTS API调用异常", e);
        }
    }

    @Override
    public String generateVideo(List<String> imageUrls, String audioUrl, String bgmUrl) {
        log.info("[generateVideo] 调用火山引擎一键成片，图片数量: {}", imageUrls.size());

        // 构建一键成片请求
        // 火山引擎智能创作 API: https://www.volcengine.com/docs/6392/1399418
        Map<String, Object> body = new HashMap<>();
        body.put("reqId", IdUtil.fastSimpleUUID());

        // 视频编辑参数
        Map<String, Object> editParam = new HashMap<>();

        // 素材列表 - 每张图片作为一个片段
        List<Map<String, Object>> mediaList = imageUrls.stream().map(url -> {
            Map<String, Object> media = new HashMap<>();
            media.put("mediaUrl", url);
            media.put("mediaType", "image");
            media.put("duration", 3000); // 每张图片展示3秒
            return media;
        }).collect(Collectors.toList());
        editParam.put("mediaList", mediaList);

        // 音频轨道
        List<Map<String, Object>> audioList = new ArrayList<>();
        if (audioUrl != null) {
            Map<String, Object> ttsAudio = new HashMap<>();
            ttsAudio.put("mediaUrl", audioUrl);
            ttsAudio.put("mediaType", "audio");
            ttsAudio.put("volume", 1.0);
            audioList.add(ttsAudio);
        }
        if (bgmUrl != null) {
            Map<String, Object> bgm = new HashMap<>();
            bgm.put("mediaUrl", bgmUrl);
            bgm.put("mediaType", "audio");
            bgm.put("volume", 0.3); // 背景音乐音量低一些
            audioList.add(bgm);
        }
        if (!audioList.isEmpty()) {
            editParam.put("audioList", audioList);
        }

        // 视频参数
        Map<String, Object> videoParam = new HashMap<>();
        videoParam.put("width", 1080);
        videoParam.put("height", 1920); // 竖屏 9:16
        videoParam.put("fps", 30);
        editParam.put("videoParam", videoParam);

        body.put("editParam", editParam);

        Request httpRequest = new Request.Builder()
                .url(volcanoProperties.getVideoApiUrl())
                .post(RequestBody.create(JsonUtils.toJsonString(body), MediaType.parse("application/json")))
                .addHeader("Authorization", volcanoProperties.getAccessKeyId() + ":" + volcanoProperties.getAccessKeySecret())
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("一键成片API调用失败: " + response.code());
            }
            String responseBody = response.body() != null ? response.body().string() : "";
            log.info("[generateVideo] 一键成片API响应成功");
            // 解析响应获取视频URL
            Map<String, Object> result = JsonUtils.parseObject(responseBody, Map.class);
            if (result != null && result.containsKey("data")) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                if (data.containsKey("videoUrl")) {
                    return (String) data.get("videoUrl");
                }
                // 异步任务模式：返回任务ID，需要轮询获取结果
                if (data.containsKey("taskId")) {
                    return pollVideoResult((String) data.get("taskId"));
                }
            }
            throw new RuntimeException("一键成片API返回异常: " + responseBody);
        } catch (IOException e) {
            log.error("[generateVideo] 一键成片API调用异常", e);
            throw new RuntimeException("一键成片API调用异常", e);
        }
    }

    /**
     * 轮询视频生成结果
     */
    private String pollVideoResult(String taskId) {
        log.info("[pollVideoResult] 开始轮询视频生成结果, taskId: {}", taskId);
        int maxRetries = 60; // 最多等待5分钟
        for (int i = 0; i < maxRetries; i++) {
            try {
                Thread.sleep(5000); // 每5秒查询一次
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("轮询被中断", e);
            }

            Request request = new Request.Builder()
                    .url(volcanoProperties.getVideoApiUrl() + "/query?taskId=" + taskId)
                    .get()
                    .addHeader("Authorization", volcanoProperties.getAccessKeyId() + ":" + volcanoProperties.getAccessKeySecret())
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) continue;
                String responseBody = response.body() != null ? response.body().string() : "";
                Map<String, Object> result = JsonUtils.parseObject(responseBody, Map.class);
                if (result != null && result.containsKey("data")) {
                    Map<String, Object> data = (Map<String, Object>) result.get("data");
                    String status = (String) data.get("status");
                    if ("done".equals(status) && data.containsKey("videoUrl")) {
                        log.info("[pollVideoResult] 视频生成完成: {}", data.get("videoUrl"));
                        return (String) data.get("videoUrl");
                    }
                    if ("failed".equals(status)) {
                        throw new RuntimeException("视频生成失败: " + data.get("message"));
                    }
                }
            } catch (IOException e) {
                log.warn("[pollVideoResult] 查询失败，重试中", e);
            }
        }
        throw new RuntimeException("视频生成超时");
    }

}
