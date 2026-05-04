package cn.iocoder.yudao.module.video.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 视频"多幕分镜"脚本 — 真正驱动前端独立分镜生成的 DTO。
 *
 * <p>不同于 {@link AiVideoScriptDTO}（整段单一 visualPrompt），这个 DTO 是
 * <b>每图一幕</b>：N 张图 → N 个 scene，每个 scene 自带 narration + visualPrompt
 * + imageSummary，外加一个全局 bgmStyle。</p>
 *
 * <p>这是把前端 {@code scriptLlm.js} 自拼 LLM prompt 收到后端的目标 DTO。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiVideoMultiSceneScriptDTO {

    /** 视频标题（8-16 字，画面感/情绪感，不要吆喝促销） */
    private String title;

    /** 全局 BGM 风格 key（同 AiVideoScriptDTO.bgmStyle） */
    private String bgmStyle;

    /** N 幕分镜（按播放顺序），每幕用对应图当起始帧 */
    private List<Scene> scenes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Scene {

        /** 该幕用第几张图（从 0 开始）；导演自由排序，不一定按上传顺序 */
        private Integer imgIdx;

        /** 一句话亮点（中文，描述这张图最动人的瞬间，给用户编辑参考） */
        private String imageSummary;

        /** 口播台词（TTS 念，自然口语；末幕含「微信扫码下单」收口） */
        private String narration;

        /** 即梦视觉镜头 prompt（英文，导演自由写运镜+焦距+光线+情绪，不限模板） */
        private String visualPrompt;

        /** 该幕时长（秒）：即梦 i2v_first_v30 仅支持 5 或 10；导演决定每幕快慢节奏 */
        private Integer duration;
    }
}
