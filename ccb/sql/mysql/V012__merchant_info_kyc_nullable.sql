-- description: merchant_info 的 KYC 字段从 NOT NULL 改成 NULL，让 SMS 一键申请能落地
-- author: huliang
-- date: 2026-04-30
--
-- 背景：merchant.sql 当年是按"完整入驻申请表"建的，license_no/legal_person_*/bank_*
--       全是 NOT NULL 无默认值。但 applyMerchantBySms 走的是"先建商户壳，后补 KYC"流程：
--       SMS 申请只有店名 + 手机号，KYC 字段后续在商户后台补录。
-- 修法：把所有 KYC 字段改成可空，仅保留 name/contact_phone/user_id 这种创建时必填项。
--       contact_name 也保持非空，由 createMerchantFromMember 用店名占位。

DROP PROCEDURE IF EXISTS `merchant_info_kyc_nullable`;
DELIMITER $$
CREATE PROCEDURE `merchant_info_kyc_nullable`()
BEGIN
    DECLARE _exists INT;

    -- license_no
    SELECT COUNT(*) INTO _exists FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'merchant_info'
        AND COLUMN_NAME = 'license_no' AND IS_NULLABLE = 'NO';
    IF _exists > 0 THEN
        ALTER TABLE `merchant_info` MODIFY COLUMN `license_no` VARCHAR(64) NULL COMMENT '营业执照号';
    END IF;

    -- license_url
    SELECT COUNT(*) INTO _exists FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'merchant_info'
        AND COLUMN_NAME = 'license_url' AND IS_NULLABLE = 'NO';
    IF _exists > 0 THEN
        ALTER TABLE `merchant_info` MODIFY COLUMN `license_url` VARCHAR(512) NULL COMMENT '营业执照图片URL';
    END IF;

    -- legal_person_name
    SELECT COUNT(*) INTO _exists FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'merchant_info'
        AND COLUMN_NAME = 'legal_person_name' AND IS_NULLABLE = 'NO';
    IF _exists > 0 THEN
        ALTER TABLE `merchant_info` MODIFY COLUMN `legal_person_name` VARCHAR(64) NULL COMMENT '法人姓名';
    END IF;

    -- legal_person_id_card
    SELECT COUNT(*) INTO _exists FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'merchant_info'
        AND COLUMN_NAME = 'legal_person_id_card' AND IS_NULLABLE = 'NO';
    IF _exists > 0 THEN
        ALTER TABLE `merchant_info` MODIFY COLUMN `legal_person_id_card` VARCHAR(32) NULL COMMENT '法人身份证号';
    END IF;

    -- legal_person_id_card_front_url
    SELECT COUNT(*) INTO _exists FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'merchant_info'
        AND COLUMN_NAME = 'legal_person_id_card_front_url' AND IS_NULLABLE = 'NO';
    IF _exists > 0 THEN
        ALTER TABLE `merchant_info` MODIFY COLUMN `legal_person_id_card_front_url` VARCHAR(512) NULL COMMENT '法人身份证正面图片URL';
    END IF;

    -- legal_person_id_card_back_url
    SELECT COUNT(*) INTO _exists FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'merchant_info'
        AND COLUMN_NAME = 'legal_person_id_card_back_url' AND IS_NULLABLE = 'NO';
    IF _exists > 0 THEN
        ALTER TABLE `merchant_info` MODIFY COLUMN `legal_person_id_card_back_url` VARCHAR(512) NULL COMMENT '法人身份证反面图片URL';
    END IF;

    -- bank_account_name
    SELECT COUNT(*) INTO _exists FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'merchant_info'
        AND COLUMN_NAME = 'bank_account_name' AND IS_NULLABLE = 'NO';
    IF _exists > 0 THEN
        ALTER TABLE `merchant_info` MODIFY COLUMN `bank_account_name` VARCHAR(128) NULL COMMENT '结算银行账户名';
    END IF;

    -- bank_account_no
    SELECT COUNT(*) INTO _exists FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'merchant_info'
        AND COLUMN_NAME = 'bank_account_no' AND IS_NULLABLE = 'NO';
    IF _exists > 0 THEN
        ALTER TABLE `merchant_info` MODIFY COLUMN `bank_account_no` VARCHAR(64) NULL COMMENT '结算银行账号';
    END IF;

    -- bank_name
    SELECT COUNT(*) INTO _exists FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'merchant_info'
        AND COLUMN_NAME = 'bank_name' AND IS_NULLABLE = 'NO';
    IF _exists > 0 THEN
        ALTER TABLE `merchant_info` MODIFY COLUMN `bank_name` VARCHAR(128) NULL COMMENT '开户行';
    END IF;

    -- business_category
    SELECT COUNT(*) INTO _exists FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'merchant_info'
        AND COLUMN_NAME = 'business_category' AND IS_NULLABLE = 'NO';
    IF _exists > 0 THEN
        ALTER TABLE `merchant_info` MODIFY COLUMN `business_category` VARCHAR(128) NULL COMMENT '经营类目';
    END IF;
END$$
DELIMITER ;
CALL `merchant_info_kyc_nullable`();
DROP PROCEDURE `merchant_info_kyc_nullable`;
