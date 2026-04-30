package cn.iocoder.yudao.module.video.service;

import cn.iocoder.yudao.module.video.service.dto.AiVideoMultiSceneScriptDTO;
import cn.iocoder.yudao.module.video.service.dto.AiVideoScriptDTO;

import java.util.List;

/**
 * 短视频文案 Service
 *
 * <p>调用火山方舟（Ark）豆包 LLM，根据商户输入生成抖音/微信风格的逐句文案
 * + Seedance 视觉镜头 prompt（含运镜+风格英文 token）+ BGM 风格 key。</p>
 */
public interface CopywritingService {

    /**
     * 生成逐句文案（旧接口保留，内部调 generateRichScript 然后取 lines）
     *
     * @param shopName        店铺名
     * @param userDescription 商户的口语化描述
     * @return 逐句文案列表（每句 ≤ 15 字，共 6-10 句）
     */
    List<String> generateCopywriting(String shopName, String userDescription);

    /**
     * 生成富脚本（推荐，新流程）—— LLM 一次输出三件事：
     *   ① 口播文案 lines（TTS 念）
     *   ② 视觉镜头 prompt（Seedance 用，含运镜+风格英文 token）
     *   ③ BGM 风格 key（sidecar 据此选音乐混音）
     *
     * <p>这是"爆款视频质量"的核心改造点 —— 把"老板写不出的运镜词汇"
     * 由 LLM 自动补齐，生成的视频从"PPT 拼接"升级为"专业短片"。</p>
     */
    AiVideoScriptDTO generateRichScript(String shopName, String userDescription);

    /**
     * 生成多幕分镜脚本 — 用视觉模型（豆包 vision-pro）"看图分幕"。
     *
     * <p>每张图一幕，每幕自带 narration + visualPrompt + imageSummary，外加全局
     * bgmStyle。返回的 scenes 数量 ≤ sceneCount 且 ≤ imageUrls.size()；最后一幕
     * narration 固定为扫码引导文案（"扫码进店领优惠"）。</p>
     *
     * <p>把前端 scriptLlm.js 自拼 system prompt 调 ark/chat 的逻辑收到后端，
     * 前端只做 thin wrapper。</p>
     *
     * @param shopName        店铺名（用于 LLM 推断行业 / 选 BGM 风格）
     * @param userDescription 商户口语化描述
     * @param imageUrls       图片公网 URL 列表（豆包 vision 模型按 url 看图）
     * @param sceneCount      期望幕数（一般 = imageUrls.size()，cap 6）
     * @param sceneDuration   每幕时长（秒，影响 narration 字数提示）
     */
    AiVideoMultiSceneScriptDTO generateMultiSceneScript(
            String shopName, String userDescription,
            java.util.List<String> imageUrls,
            int sceneCount, int sceneDuration);
}
