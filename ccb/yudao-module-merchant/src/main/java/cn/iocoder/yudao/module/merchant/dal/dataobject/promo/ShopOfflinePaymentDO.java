package cn.iocoder.yudao.module.merchant.dal.dataobject.promo;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 线下转账收款记录 DO（按订单一条）。
 *
 * <p>商户未开通在线支付通道时的兜底收款链路：
 * <ol>
 *   <li>顾客下单 → checkout 检测商户无在线支付 → 建本记录（status=0）+ 回传商户收款码</li>
 *   <li>顾客看码付款后上传付款凭证 → status=1（待商户确认）</li>
 *   <li>商户在订单详情核对凭证 → 点「确认收款」走 offline-confirm → status=2（已确认）</li>
 *   <li>凭证不符商户可驳回 → status=3，顾客可重新上传</li>
 * </ol>
 *
 * <p>订单本身仍停留在 trade_order.status=0（待支付），子状态机由本表承载，
 * 避免改动 trade 模块的 TradeOrderStatusEnum 影响其它流程。</p>
 */
@TableName("shop_offline_payment")
@KeySequence("shop_offline_payment_seq")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopOfflinePaymentDO extends TenantBaseDO {

    /** 待付款上传：已建单，等顾客付款 + 上传凭证 */
    public static final int STATUS_WAIT_PAY = 0;
    /** 已上传待确认：顾客已传凭证，等商户核对 */
    public static final int STATUS_SUBMITTED = 1;
    /** 商户已确认收款 */
    public static final int STATUS_CONFIRMED = 2;
    /** 商户驳回（凭证不符），顾客可重传 */
    public static final int STATUS_REJECTED = 3;

    @TableId
    private Long id;

    /** 交易订单 ID（trade_order.id，全局唯一） */
    private Long orderId;

    /** 买家用户 ID */
    private Long userId;

    /** 应付金额（分） */
    private Integer payPrice;

    /** 顾客上传的付款凭证截图 URL */
    private String proofUrl;

    /** 顾客付款渠道：wechat / alipay */
    private String payChannel;

    /** 顾客备注（如转账后四位 / 留言） */
    private String buyerRemark;

    /** 状态：见 STATUS_* 常量 */
    private Integer status;

    /** 顾客提交凭证时间 */
    private LocalDateTime submitTime;

    /** 商户确认收款时间 */
    private LocalDateTime confirmTime;

    /** 商户驳回原因 */
    private String rejectReason;

}
