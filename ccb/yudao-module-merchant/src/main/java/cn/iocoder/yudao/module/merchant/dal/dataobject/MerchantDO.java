package cn.iocoder.yudao.module.merchant.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 商户 DO
 */
@TableName("merchant_info")
@KeySequence("merchant_info_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MerchantDO extends TenantBaseDO {

    /**
     * 商户编号
     */
    private Long id;
    /**
     * 商户名称（店铺名）
     */
    private String name;
    /**
     * 商户logo
     */
    private String logo;
    /**
     * 联系人
     */
    private String contactName;
    /**
     * 联系电话
     */
    private String contactPhone;
    /**
     * 营业执照号
     */
    private String licenseNo;
    /**
     * 营业执照图片URL
     */
    private String licenseUrl;
    /**
     * 法人姓名
     */
    private String legalPersonName;
    /**
     * 法人身份证号
     */
    private String legalPersonIdCard;
    /**
     * 法人身份证正面图片URL
     */
    private String legalPersonIdCardFrontUrl;
    /**
     * 法人身份证反面图片URL
     */
    private String legalPersonIdCardBackUrl;
    /**
     * 结算银行账户名
     */
    private String bankAccountName;
    /**
     * 结算银行账号
     */
    private String bankAccountNo;
    /**
     * 开户行
     */
    private String bankName;
    /**
     * 经营类目
     */
    private String businessCategory;
    /**
     * 商户状态：0-待审核 1-审核通过 2-审核拒绝 3-已禁用
     */
    private Integer status;
    /**
     * 审核拒绝原因
     */
    private String rejectReason;
    /**
     * 审核时间
     */
    private LocalDateTime auditTime;
    /**
     * 微信支付子商户号（sub_mch_id）
     */
    private String wxSubMchId;
    /**
     * 微信支付进件申请单号
     */
    private String wxApplymentId;
    /**
     * 微信支付进件状态
     */
    private String wxApplymentStatus;
    /**
     * 小程序码URL（带商户参数的专属码）
     */
    private String miniAppQrCodeUrl;
    /**
     * 抖音授权 access_token
     */
    private String douyinAccessToken;
    /**
     * 抖音授权 refresh_token
     */
    private String douyinRefreshToken;
    /**
     * 抖音授权 openid
     */
    private String douyinOpenId;
    /**
     * 抖音授权过期时间
     */
    private LocalDateTime douyinTokenExpireTime;

    /**
     * AI 视频剩余配额条数（Phase 0.3.1）。
     *
     * <p><b>强制用原子 SQL 扣减</b>：仅靠
     * {@code UPDATE merchant_info SET video_quota_remaining = video_quota_remaining - ?
     *       WHERE id = ? AND deleted = 0 AND video_quota_remaining >= ?}
     * 判定余额，<em>不要</em>在 Service 里先 {@code selectById} 再 {@code updateById}——
     * 并发下会超扣。见 {@code MerchantMapper#decrementVideoQuotaAtomic(Long, int)}
     * 和 {@code MerchantServiceImpl#decreaseVideoQuota(...)}。</p>
     */
    private Integer videoQuotaRemaining;

    /**
     * 关联的用户编号
     */
    private Long userId;

    // ========== 摊小二统一小程序扩展字段（Phase 0.2） ==========

    /**
     * 摊小二商户小程序 OpenID
     */
    private String openId;

    /**
     * 微信开放平台 UnionID（同主体不同小程序/公众号共享）
     */
    private String unionId;

    /**
     * 注册时使用的邀请码 ID
     */
    private Long inviteCodeId;

}
