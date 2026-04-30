package cn.iocoder.yudao.module.video.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * AI 视频分镜 DO（一个 {@link VideoTaskDO} 一对多）。
 *
 * <p>每幕一行，存："这幕用第几张图、台词、英文运镜 prompt、Seedance task_id、
 * 该幕产出 clipUrl、状态、失败原因"等。</p>
 *
 * <p>状态机（前后端共用 string）：</p>
 * <ul>
 *     <li>{@code pending} 待处理（任务初创）</li>
 *     <li>{@code video_creating} 已提交即梦/Seedance 创建任务</li>
 *     <li>{@code video_running} 远程生成中</li>
 *     <li>{@code audio_muxing} 视频已就绪，sidecar 合 TTS+BGM 中</li>
 *     <li>{@code endcard_building} 端卡构建中（仅 isEndCard=true 时使用）</li>
 *     <li>{@code ready} 完成（clipUrl 有值）</li>
 *     <li>{@code video_failed} 失败（failReason 非空）</li>
 * </ul>
 *
 * <p>{@code (task_id, scene_index)} 在 schema 上有 UNIQUE 约束 (uk_task_scene)，
 * 重复保存同一幕会撞唯一索引 → 走 update 路径，配合 selectByTaskAndIndex 防重。</p>
 */
@TableName("video_scene")
@KeySequence("video_scene_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VideoSceneDO extends TenantBaseDO {

    @TableId
    private Long id;

    /** 所属任务 id（video_task.id） */
    private Long taskId;

    /** 分镜序号（0 起，按播放顺序） */
    private Integer sceneIndex;

    /** 该幕用第几张图（0 起，对应 video_task.image_urls） */
    private Integer imgIdx;

    /** 一句话亮点（中文，≤ 20 字） */
    private String imageSummary;

    /** 口播台词（TTS 念，≤ 36 字） */
    private String narration;

    /** Seedance 视觉 prompt（英文，含运镜+风格） */
    private String visualPrompt;

    /** 该幕起始帧图片 URL（从 image_urls[img_idx] 来） */
    private String startImageUrl;

    /** 即梦/Seedance 远程 task_id（轮询接管用） */
    private String clipTaskId;

    /** 该幕生成视频 URL（OSS） */
    private String clipUrl;

    /** TTS 配音 URL（如有） */
    private String audioUrl;

    /** 该幕时长（秒） */
    private Integer duration;

    /** 是否端卡（最后一幕，跳过 Seedance 走 sidecar /video/endcard） */
    private Boolean isEndCard;

    /** 状态机字符串，取值见类 javadoc */
    private String status;

    /** 失败原因（status=video_failed 时填） */
    private String failReason;
}
