package cn.iocoder.yudao.module.video.service.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * AI 视频"富脚本" — LLM 一次输出三件事：
 *   ① 口播文案 lines (TTS 念)
 *   ② 视觉镜头 prompt (Seedance 用，含运镜 + 风格英文 token)
 *   ③ BGM 风格 key (sidecar 据此选 mp3 混音)
 *
 * 这种结构化输出是"爆款视频质量"的关键 — 把"老板写不出的运镜词汇"
 * 由 LLM 自动补齐，生成的视频从"PPT 拼接"升级为"专业短片"。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiVideoScriptDTO {

    /** 口播文案（TTS 用，每句 ≤ 15 字，6-10 句） */
    private List<String> lines;

    /**
     * Seedance 视觉 prompt（含运镜+风格的英文 prompt，1 句即可）
     * 模板：[主体] + [动作] + [环境] + [运镜] + [风格]
     * 例：'Hand brushing chili oil on grilling skewers,
     *      sizzling sparks, warm street food market night,
     *      push-in macro lens, cinematic film grain, golden hour'
     *
     * 关键运镜词：push-in / pull-out / pan / tilt-up / dolly / macro shot / slow motion
     * 关键风格词：cinematic / food photography / vlog / asmr / golden hour / film grain
     */
    private String visualPrompt;

    /**
     * BGM 风格 key（sidecar 据此从 bgm/ 目录选音乐）
     * 候选：street_food_yelling / cozy_explore / asmr_macro / elegant_tea
     *      trendy_pop / emotional_story
     */
    private String bgmStyle;
}
