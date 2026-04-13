package cn.iocoder.yudao.module.video.service;

/**
 * AI 视频生成 Service
 * 负责调用火山引擎 TTS + FFmpeg 合成视频
 */
public interface VideoGenerateService {

    /**
     * 文字转语音（火山引擎 TTS）
     *
     * @param text 文本内容
     * @return 音频文件URL
     */
    String textToSpeech(String text);

    /**
     * 图片 + 音频合成视频
     *
     * @param imageUrls 图片URL列表
     * @param audioUrl  音频URL
     * @param bgmUrl    背景音乐URL（可选）
     * @return 视频文件URL
     */
    String generateVideo(java.util.List<String> imageUrls, String audioUrl, String bgmUrl);

}
