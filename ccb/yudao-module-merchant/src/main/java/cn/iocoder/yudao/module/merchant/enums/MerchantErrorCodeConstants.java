package cn.iocoder.yudao.module.merchant.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * 商户模块错误码
 *
 * <p>范围：1-021-000-000 ~ 1-021-099-999</p>
 * <p>说明：原 1-020-xxx 段与 yudao-module-crm 冲突（即使当前 crm 被注释，后续启用仍会碰撞），
 * 已整体迁移至 1-021-xxx。</p>
 */
public interface MerchantErrorCodeConstants {

    // ========== 商户申请 1-021-002-xxx ==========
    ErrorCode APPLY_NOT_FOUND          = new ErrorCode(1_021_002_000, "申请记录不存在");
    ErrorCode APPLY_ALREADY_AUDITED    = new ErrorCode(1_021_002_001, "申请已审核，请勿重复操作");
    ErrorCode SUBSCRIPTION_NOT_FOUND   = new ErrorCode(1_021_002_002, "订阅记录不存在");
    ErrorCode REJECT_REASON_REQUIRED   = new ErrorCode(1_021_002_003, "驳回时必须填写驳回原因");
    ErrorCode RENEW_DAYS_INVALID       = new ErrorCode(1_021_002_004, "续费天数必须大于 0");

    // ========== 商户认证 1-021-004-xxx ==========
    ErrorCode AUTH_TENANT_NOT_FOUND    = new ErrorCode(1_021_004_000, "手机号未关联任何商户，请先完成入驻申请");
    ErrorCode AUTH_TENANT_ID_REQUIRED  = new ErrorCode(1_021_004_001, "请先调用 /resolve-tenant 获取 tenantId 并在 Header 中携带 tenant-id");

    // ========== AI 成片 1-021-006-xxx ==========
    ErrorCode AI_VIDEO_QUOTA_EXHAUSTED = new ErrorCode(1_021_006_000, "AI成片配额已用完，请购买更多配额");
    ErrorCode AI_VIDEO_TASK_NOT_FOUND  = new ErrorCode(1_021_006_001, "任务不存在");
    ErrorCode AI_VIDEO_STATUS_INVALID  = new ErrorCode(1_021_006_002, "当前任务状态不支持该操作");
    ErrorCode AI_VIDEO_SUBSCRIPTION_NOT_FOUND = new ErrorCode(1_021_006_003, "订阅记录不存在");
    ErrorCode AI_VIDEO_NOT_COMPLETED   = new ErrorCode(1_021_006_004, "视频尚未生成完成，无法发布");
    ErrorCode AI_VIDEO_URL_MISSING     = new ErrorCode(1_021_006_005, "视频URL不存在，无法发布");
    ErrorCode AI_VIDEO_PAY_NOT_READY   = new ErrorCode(1_021_006_008, "配额购买功能正在对接支付系统，敬请期待");
    ErrorCode AI_VIDEO_MERCHANT_NOT_FOUND = new ErrorCode(1_021_006_010, "请先完成商户入驻");
    ErrorCode AI_VIDEO_NO_PERMISSION   = new ErrorCode(1_021_006_011, "无权操作该任务");

    // ========== 内部接口 1-021-009-xxx ==========
    ErrorCode INTERNAL_TOKEN_INVALID   = new ErrorCode(1_021_009_000, "内部接口鉴权失败");

    // ========== AI 视频 BFF 1-021-010-xxx ==========
    ErrorCode ARK_CHAT_FAILED          = new ErrorCode(1_021_010_001, "AI 对话失败：{}");
    ErrorCode JIMENG_CALL_FAILED       = new ErrorCode(1_021_010_002, "视频生成服务调用失败：{}");
    ErrorCode TTS_FAILED               = new ErrorCode(1_021_010_003, "语音合成失败：{}");
    ErrorCode BFF_BODY_TOO_LARGE       = new ErrorCode(1_021_010_004, "请求体过大");

    // ========== 统一登录 / 角色 / 邀请码 1-021-011-xxx ==========
    ErrorCode INVITE_CODE_NOT_FOUND    = new ErrorCode(1_021_011_001, "邀请码不存在");
    ErrorCode INVITE_CODE_EXHAUSTED    = new ErrorCode(1_021_011_002, "邀请码已用完");
    ErrorCode INVITE_CODE_DISABLED     = new ErrorCode(1_021_011_003, "邀请码已禁用");
    ErrorCode WX_LOGIN_FAILED          = new ErrorCode(1_021_011_004, "微信登录失败，请稍后重试");
    ErrorCode PHONE_DECRYPT_FAILED     = new ErrorCode(1_021_011_005, "手机号解密失败，请重新获取");
    ErrorCode PHONE_REQUIRED           = new ErrorCode(1_021_011_006, "需要先授权手机号");
    ErrorCode ROLE_NOT_GRANTED         = new ErrorCode(1_021_011_007, "当前用户没有该角色");
    ErrorCode SESSION_KEY_EXPIRED      = new ErrorCode(1_021_011_008, "登录态已过期，请重新登录");
    ErrorCode PASSWORD_INVALID         = new ErrorCode(1_021_011_009, "手机号或密码错误");

    // ========== 视频配额 / 套餐 1-021-012-xxx ==========
    ErrorCode PACKAGE_NOT_FOUND        = new ErrorCode(1_021_012_001, "套餐不存在");
    ErrorCode PACKAGE_NOT_AVAILABLE    = new ErrorCode(1_021_012_002, "套餐已下架");
    ErrorCode VIDEO_QUOTA_INSUFFICIENT = new ErrorCode(1_021_012_003, "视频配额不足，请先购买套餐");
    ErrorCode VIDEO_QUOTA_UPDATE_FAILED = new ErrorCode(1_021_012_004, "视频配额更新失败，请稍后重试");

    // ========== 套餐订单 / 支付 1-021-013-xxx ==========
    ErrorCode PACKAGE_ORDER_NOT_FOUND     = new ErrorCode(1_021_013_001, "套餐订单不存在");
    ErrorCode PACKAGE_ORDER_ALREADY_PAID  = new ErrorCode(1_021_013_002, "套餐订单已支付，请勿重复操作");
    ErrorCode PAY_APP_ID_NOT_CONFIGURED   = new ErrorCode(1_021_013_003, "套餐支付应用未配置，请联系管理员");
    ErrorCode PACKAGE_ORDER_OPENID_MISSING = new ErrorCode(1_021_013_004, "缺少微信 OpenID，无法发起小程序支付");
    ErrorCode PACKAGE_ORDER_PAY_ORDER_MISMATCH = new ErrorCode(1_021_013_005, "支付单与订单不匹配，拒绝处理");
    /**
     * 支付回调二次校验失败：反查 pay_order 后发现状态非 SUCCESS，或
     * pay_order.price 与本地业务订单 price 不一致（防金额篡改 / 伪造回调）。
     * 抛出后由 pay 模块的重试机制决定下一步——一般持续校验失败意味着请求不合法，重试到上限自动失败。
     */
    ErrorCode PACKAGE_ORDER_PAY_NOT_SUCCESS    = new ErrorCode(1_021_013_006, "支付单未成功或金额不匹配");
    ErrorCode PACKAGE_ORDER_CHANNEL_UNSUPPORTED = new ErrorCode(1_021_013_007, "暂不支持该支付渠道");
    ErrorCode PACKAGE_ORDER_PRICE_INVALID       = new ErrorCode(1_021_013_008, "套餐价格非法");

}
