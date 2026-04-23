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

}
