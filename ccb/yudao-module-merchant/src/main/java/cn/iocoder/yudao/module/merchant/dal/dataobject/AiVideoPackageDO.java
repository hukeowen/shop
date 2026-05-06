package cn.iocoder.yudao.module.merchant.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
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
 * <p>平台级表（非租户隔离）。继承 {@link BaseDO}，<em>不要</em>在 INSERT 时显式设置 tenantId——
 * SQL 表 {@code tenant_id NOT NULL DEFAULT 0} 靠 DDL 默认值兜底，避免 MP 租户拦截器追加
 * {@code tenant_id = ?} 把不同租户的 admin 屏蔽掉。</p>
 */
// 平台级套餐（tenant_id=0），所有商户租户共享。
// yudao TenantDatabaseInterceptor 的逻辑是「BaseDO 子类 + 无 @TenantIgnore → 仍加 WHERE tenant_id=ctx」，
// 所以必须显式标 @TenantIgnore，否则商户登录后查这个表只会看到 tenant_id=新租户 的（空）。
@TenantIgnore
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
