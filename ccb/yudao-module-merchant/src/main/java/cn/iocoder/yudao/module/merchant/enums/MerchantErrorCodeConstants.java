package cn.iocoder.yudao.module.merchant.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * 商户模块错误码
 *
 * <p>范围：1-020-000-000 ~ 1-020-099-999</p>
 */
public interface MerchantErrorCodeConstants {

    // ========== 商户申请 1-020-002-xxx ==========
    ErrorCode APPLY_NOT_FOUND          = new ErrorCode(1_020_002_000, "申请记录不存在");
    ErrorCode APPLY_ALREADY_AUDITED    = new ErrorCode(1_020_002_001, "申请已审核，请勿重复操作");
    ErrorCode SUBSCRIPTION_NOT_FOUND   = new ErrorCode(1_020_002_002, "订阅记录不存在");
    ErrorCode REJECT_REASON_REQUIRED   = new ErrorCode(1_020_002_003, "驳回时必须填写驳回原因");

    // ========== 商户认证 1-020-004-xxx ==========
    ErrorCode AUTH_TENANT_NOT_FOUND    = new ErrorCode(1_020_004_000, "手机号未关联任何商户，请先完成入驻申请");
    ErrorCode AUTH_TENANT_ID_REQUIRED  = new ErrorCode(1_020_004_001, "请先调用 /resolve-tenant 获取 tenantId 并在 Header 中携带 tenant-id");

    // ========== AI 成片 1-020-006-xxx ==========
    ErrorCode AI_VIDEO_QUOTA_EXHAUSTED = new ErrorCode(1_020_006_000, "AI成片配额已用完，请购买更多配额");
    ErrorCode AI_VIDEO_TASK_NOT_FOUND  = new ErrorCode(1_020_006_001, "任务不存在");
    ErrorCode AI_VIDEO_STATUS_INVALID  = new ErrorCode(1_020_006_002, "当前任务状态不支持该操作");
    ErrorCode AI_VIDEO_SUBSCRIPTION_NOT_FOUND = new ErrorCode(1_020_006_003, "订阅记录不存在");
    ErrorCode AI_VIDEO_NOT_COMPLETED   = new ErrorCode(1_020_006_004, "视频尚未生成完成，无法发布");
    ErrorCode AI_VIDEO_URL_MISSING     = new ErrorCode(1_020_006_005, "视频URL不存在，无法发布");
    ErrorCode AI_VIDEO_PAY_NOT_READY   = new ErrorCode(1_020_006_008, "配额购买功能正在对接支付系统，敬请期待");
    ErrorCode AI_VIDEO_MERCHANT_NOT_FOUND = new ErrorCode(1_020_006_010, "请先完成商户入驻");
    ErrorCode AI_VIDEO_NO_PERMISSION   = new ErrorCode(1_020_006_011, "无权操作该任务");

    // ========== 内部接口 1-020-009-xxx ==========
    ErrorCode INTERNAL_TOKEN_INVALID   = new ErrorCode(1_020_009_000, "内部接口鉴权失败");

}
