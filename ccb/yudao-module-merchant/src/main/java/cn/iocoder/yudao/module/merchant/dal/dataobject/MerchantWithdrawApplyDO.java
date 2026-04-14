package cn.iocoder.yudao.module.merchant.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 商户向平台申请提现记录
 * 平台级表，无 tenant_id 隔离，继承 BaseDO
 */
@TableName("merchant_withdraw_apply")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantWithdrawApplyDO extends BaseDO {

    /** 申请商户的租户ID */
    private Long tenantId;

    /** 商户店铺名称（冗余） */
    private String shopName;

    /** 申请提现金额（分） */
    private Integer amount;

    /** 提现方式：1微信扫码 2支付宝扫码 3银行转账 */
    private Integer withdrawType;

    /** 账户姓名 */
    private String accountName;

    /** 账号/收款码URL */
    private String accountNo;

    /** 银行名称（银行转账时填写） */
    private String bankName;

    /** 审核状态：0待审核 1已转账 2驳回 */
    private Integer status;

    /** 驳回原因 */
    private String rejectReason;

    /** 审核员管理员ID */
    private Long auditorId;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 转账凭证截图 OSS URL */
    private String voucherUrl;

}
