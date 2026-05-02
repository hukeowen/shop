package cn.iocoder.yudao.module.merchant.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户反馈（C 端「帮助与反馈」）。
 *
 * 平台级表，tenant_id 作为普通字段：来自店铺 = 该店 tenantId / 通用反馈 = 0。
 * 不继承 TenantBaseDO，避免被多租户拦截器自动过滤；后台需要看全部反馈。
 */
@TableName("user_feedback")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFeedbackDO extends BaseDO {

    @TableId
    private Long id;

    /** 来自店铺则为店铺 tenantId；通用反馈 = 0 */
    private Long tenantId;

    private Long userId;

    /** BUG / FEATURE / PAYMENT / ACCOUNT / SHOP / OTHER */
    private String category;

    private String content;

    /** 联系方式（手机号/微信，可选） */
    private String contact;

    /** 附图 URL JSON 数组（可选，最多 6 张） */
    private String images;

    /** 0 = 待处理 / 1 = 处理中 / 2 = 已解决 / 3 = 已关闭 */
    private Integer status;

    private String reply;

    private LocalDateTime repliedAt;

}
