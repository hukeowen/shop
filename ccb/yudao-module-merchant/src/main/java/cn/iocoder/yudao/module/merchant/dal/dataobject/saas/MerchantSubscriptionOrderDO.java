package cn.iocoder.yudao.module.merchant.dal.dataobject.saas;

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
 * SaaS 商户订阅订单 — 商户购买 SaaS 套餐的支付记录。
 *
 * <p>生命周期：</p>
 * <ol>
 *   <li>WAITING：创建后调通联 cashier，等用户支付</li>
 *   <li>PAID：通联回调 / 轮询命中 → 把商户 service_expire_at += duration_days
 *       + ai_video_quota_remaining += ai_video_grant + service_package_level 切到新档</li>
 *   <li>CANCELLED：用户主动取消或超时未付</li>
 * </ol>
 *
 * <p>通联 reqsn 用 S 前缀（S${id}），跟 trade(T${id}) / package(${id} 纯数字) 区分。</p>
 */
@TableName("merchant_subscription_order")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantSubscriptionOrderDO extends BaseDO {

    public static final int PAY_STATUS_WAITING = 0;
    public static final int PAY_STATUS_PAID = 1;
    public static final int PAY_STATUS_CANCELLED = 2;

    @TableId
    private Long id;
    private Long merchantId;
    /** 购买的套餐档位 — 创建时 snapshot 自 saas_package_config */
    private String level;
    private Integer priceFen;
    private Integer durationDays;
    private Integer aiVideoGrant;
    /** 0=WAITING 1=PAID 2=CANCELLED */
    private Integer payStatus;
    private LocalDateTime payAt;
    private Integer payAmountFen;
    /** 通联 reqsn（S 前缀） */
    private String tlReqsn;
    /** 付款前的到期时间（审计用） */
    private LocalDateTime expireBefore;
    /** 付款后扩展到的到期时间（审计用） */
    private LocalDateTime expireAfter;
}
