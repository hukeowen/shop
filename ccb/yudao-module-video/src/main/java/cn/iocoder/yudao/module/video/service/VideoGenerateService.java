package cn.iocoder.yudao.module.video.service;

import java.util.List;

/**
 * AI 视频生成 Service
 *
 * <p>火山方舟 Seedance 图生视频（字节目前最强）+ 豆包 TTS。</p>
 */
public interface VideoGenerateService {

    /**
     * 文字转语音（火山引擎 TTS），返回 OSS 可访问 URL。
     *
     * @param text 文本（逐句合并后传入）
     * @return 音频 OSS URL（mp3）
     */
    String textToSpeech(String text);

    /**
     * 基于首张图片 + 文案调用 Seedance 图生视频模型，返回生成视频的可公开访问 URL。
     *
     * <p>Seedance 目前支持 1 张参考图，API 内部由模型完成镜头运动、转场与时长控制；
     * 多图场景可在后续迭代中拆分为"分片生成 + 拼接"流程。</p>
     *
     * @param imageUrls 图片 URL 列表（至少 1 张，第 1 张作为首帧参考图）
     * @param lines     逐句文案（拼接后作为生成 prompt 的一部分）
     * @return Seedance 返回的视频 URL（由火山托管，有效期默认 24h，落库前请下载并转存到自有 OSS）
     */
    String generateVideoFromImages(List<String> imageUrls, List<String> lines);

    /**
     * 下载远端视频并落盘到自有 OSS。
     *
     * @param remoteVideoUrl Seedance 返回的临时 URL
     * @return 自有 OSS 持久化 URL
     */
    String persistToOss(String remoteVideoUrl);

}
