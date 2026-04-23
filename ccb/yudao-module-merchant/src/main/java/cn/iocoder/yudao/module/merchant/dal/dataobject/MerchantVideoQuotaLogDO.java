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
 * 商户 AI 视频配额流水 DO（Phase 0.3.1）。
 *
 * <p>所有 {@code video_quota_remaining} 的 +/- 都必须写一条，作为对账与审计的唯一可信源。</p>
 *
 * <p>按 {@code merchant_id} 过滤，<em>不按</em> tenant 过滤——商户和管理后台是跨租户统一视角。
 * 因此继承 {@link BaseDO} 而非 TenantBaseDO，避免 MP 租户拦截器追加
 * {@code tenant_id = ?}。</p>
 */
@TableName("merchant_video_quota_log")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MerchantVideoQuotaLogDO extends BaseDO {

    @TableId
    private Long id;

    /** 商户 ID。 */
    private Long merchantId;

    /** 变动值（正数=增加，负数=扣减）。 */
    private Integer quotaChange;

    /** 变动后余量（事务内与 {@code merchant_info.video_quota_remaining} 对齐）。 */
    private Integer quotaAfter;

    /**
     * 业务类型。对应
     * {@link cn.iocoder.yudao.module.merchant.enums.ai.VideoQuotaBizTypeEnum}：
     * 1=购买套餐 2=视频生成扣减 3=生成失败回补 4=平台手动调整。
     */
    private Integer bizType;

    /** 业务外键：套餐订单号 / 视频任务 ID / 操作员 ID 等。可为空。 */
    private String bizId;

    /** 备注。 */
    private String remark;

}
