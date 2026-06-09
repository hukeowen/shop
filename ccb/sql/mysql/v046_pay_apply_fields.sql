-- =====================================================================
-- v046 在线支付开通（通联进件）补全商户进件信息字段
--
-- 背景：原 pay-apply 只收 3 张资质照，通联进件接口（商户进件 merchantapi/add）
--       还需法人信息 + 营业执照 + 结算账户 等结构化字段。本迁移给 shop_info
--       补上这些字段，H5 表单收集、进件时随请求提交。
--
-- 敏感字段（法人证件号 legal_id_no / 结算账户号 settle_acct_no）按
-- EncryptTypeHandler AES 加密存储（与 tl_mch_key 同一套 MERCHANT_FIELD_ENCRYPT_KEY），
-- 所以列类型放宽到 varchar(255) 容纳密文。
--
-- 兼容：MySQL 8.0，存储过程守卫幂等。可重复执行。
-- =====================================================================

DROP PROCEDURE IF EXISTS `_v046_add_pay_apply_cols`;
DELIMITER $$
CREATE PROCEDURE `_v046_add_pay_apply_cols`()
BEGIN
    DECLARE _add_col TEXT;

    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='comproperty') THEN
        ALTER TABLE `shop_info` ADD COLUMN `comproperty` varchar(2) NULL COMMENT '商户性质：1企业 3个体户 4个人 5其他组织 6事业单位';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='merchant_full_name') THEN
        ALTER TABLE `shop_info` ADD COLUMN `merchant_full_name` varchar(128) NULL COMMENT '商户全称（进件 merchantname，空则用 shop_name）';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='legal_name') THEN
        ALTER TABLE `shop_info` ADD COLUMN `legal_name` varchar(32) NULL COMMENT '法人/经营者姓名';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='legal_id_no') THEN
        ALTER TABLE `shop_info` ADD COLUMN `legal_id_no` varchar(255) NULL COMMENT '法人证件号（AES 加密）';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='legal_id_expire') THEN
        ALTER TABLE `shop_info` ADD COLUMN `legal_id_expire` varchar(16) NULL COMMENT '法人证件有效期（yyyy-MM-dd 或 长期）';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='credit_code') THEN
        ALTER TABLE `shop_info` ADD COLUMN `credit_code` varchar(40) NULL COMMENT '统一社会信用代码 / 营业执照号';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='credit_code_expire') THEN
        ALTER TABLE `shop_info` ADD COLUMN `credit_code_expire` varchar(16) NULL COMMENT '营业执照有效期（yyyy-MM-dd 或 长期）';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='settle_acct_name') THEN
        ALTER TABLE `shop_info` ADD COLUMN `settle_acct_name` varchar(80) NULL COMMENT '结算账户名（开户名）';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='settle_acct_no') THEN
        ALTER TABLE `shop_info` ADD COLUMN `settle_acct_no` varchar(255) NULL COMMENT '结算账户号（AES 加密）';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='settle_acct_type') THEN
        ALTER TABLE `shop_info` ADD COLUMN `settle_acct_type` varchar(2) NULL COMMENT '账户类型：0对私 1对公';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='settle_bank_name') THEN
        ALTER TABLE `shop_info` ADD COLUMN `settle_bank_name` varchar(64) NULL COMMENT '开户银行名称（展示用）';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='settle_bank_code') THEN
        ALTER TABLE `shop_info` ADD COLUMN `settle_bank_code` varchar(16) NULL COMMENT '所属银行代码（通联附录8.3，可空待补）';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='settle_cnaps_no') THEN
        ALTER TABLE `shop_info` ADD COLUMN `settle_cnaps_no` varchar(32) NULL COMMENT '支付行号（通联附录8.5，对公必填，可空待补）';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='contact_email') THEN
        ALTER TABLE `shop_info` ADD COLUMN `contact_email` varchar(64) NULL COMMENT '联系邮箱';
    END IF;
END$$
DELIMITER ;
CALL `_v046_add_pay_apply_cols`();
DROP PROCEDURE IF EXISTS `_v046_add_pay_apply_cols`;
