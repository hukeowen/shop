package cn.iocoder.yudao.module.merchant.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI成片任务 DO（租户隔离）
 */
@TableName(value = "ai_video_task", autoResultMap = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiVideoTaskDO extends TenantBaseDO {

    /** 操作的商户管理员用户ID */
    private Long userId;

    /** 上传图片 OSS URL 列表（JSON数组） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> imageUrls;

    /** 用户输入的简短描述 */
    private String userDescription;

    /** AI生成的逐句文案（JSON数组） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> aiCopywriting;

    /** 用户确认后的最终文案 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> finalCopywriting;

    /** 背景音乐ID */
    private Integer bgmId;

    /**
     * 任务状态：
     * 0待处理 1文案生成中 2等待用户确认文案 3视频合成中 4完成 5失败
     */
    private Integer status;

    /** 生成视频的 OSS URL */
    private String videoUrl;

    /** 视频封面图 OSS URL */
    private String coverUrl;

    /** 失败原因 */
    private String failReason;

    /** 是否已扣减配额 */
    private Boolean quotaDeducted;

}
