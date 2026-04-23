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

import java.time.LocalDateTime;

/**
 * AI 视频套餐订单 DO（Phase 0.3.3）。
 *
 * <p>商户购买套餐的业务订单，与 {@code pay_order}（yudao-module-pay）解耦：
 * 本表承载业务语义（哪个商户买哪个套餐、支付到账后给多少配额）；
 * pay_order 承载支付渠道语义（对接微信 / 支付宝的钱流）。
 * 两张表通过 {@link #payOrderId} 1:1 映射，由 {@code uk_pay_order} UNIQUE 保证。</p>
 *
 * <p>平台级表（非租户隔离）。继承 {@link BaseDO}，<em>不要</em>在 INSERT 时显式设置 tenantId——
 * SQL 表 {@code tenant_id NOT NULL DEFAULT 0} 靠 DDL 默认值兜底，避免 MP 租户拦截器追加
 * {@code tenant_id = ?} 干扰跨租户查询。</p>
 *
 * <p>幂等设计：支付回调 {@code controller} 调用
 * {@link cn.iocoder.yudao.module.merchant.service.MerchantService#increaseVideoQuota} 时，
 * 以 {@code bizType=PACKAGE_PURCHASE(1), bizId=payOrderId} 为幂等键；
 * {@code merchant_video_quota_log} 的 {@code uk_biz} UNIQUE 约束保证重复回调不会重复加配额。</p>
 */
@TableName("merchant_package_order")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MerchantPackageOrderDO extends BaseDO {

    /** 支付状态常量：待支付。 */
    public static final int PAY_STATUS_WAITING = 0;
    /** 支付状态常量：已支付。 */
    public static final int PAY_STATUS_PAID = 10;
    /** 支付状态常量：已关闭（超时 / 用户取消）。 */
    public static final int PAY_STATUS_CLOSED = 20;
    /** 支付状态常量：已退款。 */
    public static final int PAY_STATUS_REFUNDED = 30;

    @TableId
    private Long id;

    /** 商户 ID。 */
    private Long merchantId;

    /** 套餐 ID（指向 {@code ai_video_package.id}）。 */
    private Long packageId;

    /** 下单时套餐名称快照（防套餐后续改名影响历史订单）。 */
    private String packageName;

    /** 套餐包含视频条数快照。 */
    private Integer videoCount;

    /** 下单实付金额（分）快照。 */
    private Long price;

    /** pay_order 的主键（可为空：极少数创建支付单失败的场景）。 */
    private Long payOrderId;

    /**
     * 支付状态：
     * <ul>
     *   <li>{@link #PAY_STATUS_WAITING}=待支付</li>
     *   <li>{@link #PAY_STATUS_PAID}=已支付</li>
     *   <li>{@link #PAY_STATUS_CLOSED}=已关闭</li>
     *   <li>{@link #PAY_STATUS_REFUNDED}=已退款</li>
     * </ul>
     */
    private Integer payStatus;

    /** 支付成功时间。 */
    private LocalDateTime payTime;

    /** 支付成功时写入 {@code merchant_video_quota_log} 的主键，便于审计。 */
    private Long quotaLogId;

}
