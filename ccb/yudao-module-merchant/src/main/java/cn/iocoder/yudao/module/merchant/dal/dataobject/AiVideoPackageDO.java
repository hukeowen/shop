package cn.iocoder.yudao.module.merchant.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * AI 视频套餐 DO（Phase 0.3.1）。
 *
 * <p>平台级资源：{@code tenant_id} 恒为 0，查询不按租户过滤。
 * 继承 {@link BaseDO}（<em>不是</em> TenantBaseDO），避免 MP 租户拦截器追加
 * {@code tenant_id = ?} 把不同租户的 admin 屏蔽掉。</p>
 */
@TableName("ai_video_package")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiVideoPackageDO extends BaseDO {

    @TableId
    private Long id;

    /** 套餐名，如「体验装 5 条」。 */
    private String name;

    /** 套餐描述（销售话术）。 */
    private String description;

    /** 附赠视频条数。 */
    private Integer videoCount;

    /** 售价（单位：分）。 */
    private Long price;

    /** 划线原价（单位：分，可选）。 */
    private Long originalPrice;

    /** 排序值，倒序展示（大→前）。 */
    private Integer sort;

    /** 状态：0=上架 1=下架。 */
    private Integer status;

}
