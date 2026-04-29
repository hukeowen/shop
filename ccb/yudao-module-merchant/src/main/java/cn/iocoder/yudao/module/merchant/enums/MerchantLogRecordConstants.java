package cn.iocoder.yudao.module.merchant.enums;

/**
 * Merchant 模块操作日志常量
 *
 * <p>关键业务节点必须留审计日志，方便事后追溯/合规审计：</p>
 * <ul>
 *   <li>商户开通 / 申请审核（资金链路前置）</li>
 *   <li>在线支付开通申请审核（涉及资金通道）</li>
 *   <li>提现申请 / 打款（资金出站）</li>
 *   <li>KYC 资质修改（敏感数据变动）</li>
 * </ul>
 *
 * <p>使用：在 service 方法上加 {@code @LogRecord(type=...,subType=...,bizNo=...,success=...)}
 * yudao 框架（{@code mzt-biz-log}）会自动落库到 {@code system_operate_log}。</p>
 */
public interface MerchantLogRecordConstants {

    // ============ 商户入驻申请 ============
    String MERCHANT_APPLY_TYPE = "商户入驻";
    String MERCHANT_APPLY_AUDIT_SUB_TYPE = "审核入驻申请";
    String MERCHANT_APPLY_APPROVE_SUCCESS = "审核通过商户【{{#apply.shopName}}】，创建租户 ID={{#tenantId}}";
    String MERCHANT_APPLY_REJECT_SUCCESS = "驳回商户【{{#apply.shopName}}】入驻，原因：{{#rejectReason}}";

    // ============ 在线支付开通审核 ============
    String SHOP_PAY_APPLY_TYPE = "店铺在线支付";
    String SHOP_PAY_APPLY_AUDIT_SUB_TYPE = "审核在线支付开通";
    String SHOP_PAY_APPLY_APPROVE_SUCCESS = "通过店铺【{{#shop.shopName}}】(ID={{#shop.id}}) 在线支付开通；触发通联进件";
    String SHOP_PAY_APPLY_REJECT_SUCCESS = "驳回店铺【{{#shop.shopName}}】在线支付开通：{{#rejectReason}}";
    String SHOP_PAY_APPLY_SUBMIT_SUB_TYPE = "提交在线支付进件资质";
    String SHOP_PAY_APPLY_SUBMIT_SUCCESS = "店铺【{{#shop.shopName}}】提交 KYC 资质，状态→审核中";

    // ============ 通联进件回调 ============
    String ALLINPAY_NOTIFY_TYPE = "通联收付通通知";
    String ALLINPAY_OPEN_SUB_TYPE = "通联开户回调";
    String ALLINPAY_OPEN_SUCCESS = "店铺 {{#shopId}} 通联开户成功 mchId={{#tlMchId}}";
    String ALLINPAY_REJECT_SUCCESS = "店铺 {{#shopId}} 通联拒绝：{{#reason}}";

    // ============ 提现 ============
    String SHOP_WITHDRAW_TYPE = "商户提现";
    String SHOP_WITHDRAW_APPLY_SUB_TYPE = "申请提现";
    String SHOP_WITHDRAW_APPLY_SUCCESS = "商户申请提现 {{#amount}} 分";
    String SHOP_WITHDRAW_AUDIT_SUB_TYPE = "审核提现申请";
    String SHOP_WITHDRAW_APPROVE_SUCCESS = "通过提现申请 ID={{#withdrawId}} 金额={{#amount}}";
    String SHOP_WITHDRAW_REJECT_SUCCESS = "驳回提现申请 ID={{#withdrawId}}：{{#rejectReason}}";
    String SHOP_WITHDRAW_PAY_SUB_TYPE = "提现打款";
    String SHOP_WITHDRAW_PAY_SUCCESS = "提现打款完成 ID={{#withdrawId}} 金额={{#amount}}";

    // ============ AI 视频任务 ============
    String AI_VIDEO_TYPE = "AI 视频任务";
    String AI_VIDEO_CREATE_SUB_TYPE = "创建 AI 视频任务";
    String AI_VIDEO_CREATE_SUCCESS = "创建任务 ID={{#taskId}}，扣减配额 1 次";
    String AI_VIDEO_COMPLETE_SUB_TYPE = "AI 视频任务完成";
    String AI_VIDEO_COMPLETE_SUCCESS = "任务 ID={{#taskId}} 合成完成，videoUrl={{#videoUrl}}";
    String AI_VIDEO_FAIL_SUCCESS = "任务 ID={{#taskId}} 合成失败：{{#reason}}";
}
