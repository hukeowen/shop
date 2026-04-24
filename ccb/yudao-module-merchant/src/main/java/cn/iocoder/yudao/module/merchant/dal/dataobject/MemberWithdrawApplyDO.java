package cn.iocoder.yudao.module.merchant.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会员向商户申请提现记录
 */
@TableName("member_withdraw_apply")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberWithdrawApplyDO extends BaseDO {

    @TableId
    private Long id;

    /** 申请提现的用户ID */
    private Long userId;

    /** 商户租户ID */
    private Long tenantId;

    /** 提现金额（分） */
    private Integer amount;

    /** 提现方式：1微信 2支付宝 3银行 */
    private Integer withdrawType;

    /** 账户姓名 */
    private String accountName;

    /** 账号/收款码URL */
    private String accountNo;

    /** 银行名称（银行转账时填写） */
    private String bankName;

    /** 审核状态：0待审核 1已打款 2驳回 */
    private Integer status;

    /** 驳回原因 */
    private String rejectReason;

    /** 转账凭证截图 OSS URL */
    private String voucherUrl;

}
