package cn.iocoder.yudao.module.merchant.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商户入驻申请 DO
 *
 * 平台级表，无租户隔离（申请阶段尚未创建租户）。
 * 审核通过后 tenant_id 字段记录创建的租户编号。
 */
@TableName("merchant_apply")
@KeySequence("merchant_apply_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantApplyDO extends BaseDO {

    @TableId
    private Long id;

    // ========== 基本信息 ==========
    /** 店铺名称 */
    private String shopName;
    /** 经营类目ID */
    private Long categoryId;
    /** 联系手机号 */
    private String mobile;
    /** 推荐人手机号（选填，用于平台推N返1裂变） */
    private String referrerMobile;

    // ========== 资质材料（纯图片，人工审核） ==========
    /** 营业执照图片 OSS URL */
    private String licenseUrl;
    /** 法人身份证正面 OSS URL */
    private String idCardFront;
    /** 法人身份证背面 OSS URL */
    private String idCardBack;

    // ========== 摊位位置 ==========
    /** 经度 */
    private BigDecimal longitude;
    /** 纬度 */
    private BigDecimal latitude;
    /** 详细地址 */
    private String address;

    // ========== 微信收款配置 ==========
    /** 微信收款类型：0未配置 1已有商户号 2申请子商户 */
    private Integer wxMchType;
    /** 已有微信商户号（wxMchType=1 时填写） */
    private String wxMchId;

    // ========== 审核信息 ==========
    /** 审核状态：0待审核 1通过 2驳回 */
    private Integer status;
    /** 驳回原因 */
    private String rejectReason;
    /** 审核员管理员 ID */
    private Long auditorId;
    /** 审核时间 */
    private LocalDateTime auditTime;

    // ========== 开通后关联 ==========
    /** 审核通过后创建的租户 ID */
    private Long tenantId;

}
