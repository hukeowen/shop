-- Phase 1.7: 在线支付开通申请字段
-- 在 shop_info 表追加 online_pay 相关列

ALTER TABLE shop_info
    ADD COLUMN IF NOT EXISTS online_pay_enabled  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '在线支付是否已开通',
    ADD COLUMN IF NOT EXISTS pay_apply_status    TINYINT      NULL     DEFAULT NULL COMMENT '在线支付申请状态：0未申请 1审核中 2已开通 3已驳回',
    ADD COLUMN IF NOT EXISTS tl_mch_id          VARCHAR(64)  NULL     DEFAULT NULL COMMENT '通联支付商户号',
    ADD COLUMN IF NOT EXISTS tl_mch_key         VARCHAR(128) NULL     DEFAULT NULL COMMENT '通联支付密钥',
    ADD COLUMN IF NOT EXISTS pay_apply_reject_reason VARCHAR(255) NULL DEFAULT NULL COMMENT '审核驳回原因';
