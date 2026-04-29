-- Phase 1.7: 在线支付开通申请字段
-- 在 shop_info 表追加 online_pay 相关列（幂等，反复执行不报 Duplicate column）
--
-- 原本用 ADD COLUMN IF NOT EXISTS，那是 MariaDB 语法，MySQL 8 不支持；
-- 改用 INFORMATION_SCHEMA 判断的存储过程。

DROP PROCEDURE IF EXISTS `shop_info_add_col_online_pay`;
DELIMITER $$
CREATE PROCEDURE `shop_info_add_col_online_pay`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_info' AND COLUMN_NAME = 'online_pay_enabled') THEN
        ALTER TABLE `shop_info` ADD COLUMN `online_pay_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '在线支付是否已开通';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_info' AND COLUMN_NAME = 'pay_apply_status') THEN
        ALTER TABLE `shop_info` ADD COLUMN `pay_apply_status` TINYINT NULL DEFAULT NULL COMMENT '在线支付申请状态：0未申请 1审核中 2已开通 3已驳回';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_info' AND COLUMN_NAME = 'tl_mch_id') THEN
        ALTER TABLE `shop_info` ADD COLUMN `tl_mch_id` VARCHAR(64) NULL DEFAULT NULL COMMENT '通联支付商户号';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_info' AND COLUMN_NAME = 'tl_mch_key') THEN
        ALTER TABLE `shop_info` ADD COLUMN `tl_mch_key` VARCHAR(128) NULL DEFAULT NULL COMMENT '通联支付密钥';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_info' AND COLUMN_NAME = 'pay_apply_reject_reason') THEN
        ALTER TABLE `shop_info` ADD COLUMN `pay_apply_reject_reason` VARCHAR(255) NULL DEFAULT NULL COMMENT '审核驳回原因';
    END IF;
END$$
DELIMITER ;
CALL `shop_info_add_col_online_pay`();
DROP PROCEDURE `shop_info_add_col_online_pay`;
