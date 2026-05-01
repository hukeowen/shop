package cn.iocoder.yudao.module.video.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI视频生成任务 DO
 */
@TableName(value = "video_task", autoResultMap = true)
@KeySequence("video_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VideoTaskDO extends TenantBaseDO {

    /**
     * 任务编号
     */
    private Long id;
    /**
     * 商户编号
     */
    private Long merchantId;
    /**
     * 用户编号
     */
    private Long userId;
    /**
     * 视频标题
     */
    private String title;
    /**
     * 视频描述/文案
     */
    private String description;
    /**
     * 上传的图片URL列表
     *
     * <p>schema 列是 json NOT NULL —— 必须用 JacksonTypeHandler 序列化成 JSON 数组；
     * 之前用 StringListTypeHandler 是逗号分隔字符串，前端 JSON.parse 会挂。</p>
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> imageUrls;
    /**
     * 背景音乐URL（可选）
     */
    private String bgmUrl;
    /**
     * TTS语音URL（生成后填入）
     */
    private String ttsAudioUrl;
    /**
     * 生成的视频URL
     */
    private String videoUrl;
    /**
     * 视频时长（秒）
     */
    private Integer duration;
    /**
     * 任务状态：0-待处理 1-生成中 2-已完成 3-失败
     */
    private Integer status;
    /**
     * 失败原因
     */
    private String failReason;
    /**
     * 抖音发布状态：0-未发布 1-发布中 2-已发布 3-发布失败
     */
    private Integer douyinPublishStatus;
    /**
     * 抖音视频ID
     */
    private String douyinItemId;
    /**
     * 抖音发布时间
     */
    private LocalDateTime douyinPublishTime;

    // ==================== V014 新增（B 改造 Step 2）：把前端 store.tasks 的元数据落库 ====================

    /**
     * BGM 风格 key（confirm 页用户从 6 选 1）：
     * street_food_yelling / cozy_explore / asmr_macro / elegant_tea / trendy_pop / emotional_story / none
     * none = 主动选不混 BGM；空 = sidecar 走默认 cozy_explore
     */
    private String bgmStyle;

    /**
     * 即梦 CV 端卡海报 URL（detail 页单独下载用，与视频独立）
     */
    private String posterUrl;

    /**
     * TTS 音色 key（cancan / qingxin / ...）
     */
    private String voiceKey;

    /**
     * 画面比例：9:16 / 16:9 / 1:1
     */
    private String ratio;

    /**
     * 视频封面图 URL（一般取 image_urls[0] 或第一幕的起始帧）
     */
    private String coverUrl;
}
