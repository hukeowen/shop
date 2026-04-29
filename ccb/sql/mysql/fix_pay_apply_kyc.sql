-- Phase 1.8: 在线支付进件 KYC 资质字段（身份证 + 营业执照）
-- 在 shop_info 表追加进件资质 URL 列（幂等）
--
-- 商户提交开通申请时上传 3 张图：身份证正/反面 + 营业执照
-- PC 后台审核通过后，由后端自动调通联接口开户（演示阶段为 mock fake tlMchId）

DROP PROCEDURE IF EXISTS `shop_info_add_col_pay_apply_kyc`;
DELIMITER $$
CREATE PROCEDURE `shop_info_add_col_pay_apply_kyc`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_info' AND COLUMN_NAME = 'id_card_front_url') THEN
        ALTER TABLE `shop_info` ADD COLUMN `id_card_front_url` VARCHAR(512) NULL DEFAULT NULL COMMENT '法人身份证正面照';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_info' AND COLUMN_NAME = 'id_card_back_url') THEN
        ALTER TABLE `shop_info` ADD COLUMN `id_card_back_url` VARCHAR(512) NULL DEFAULT NULL COMMENT '法人身份证背面照';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_info' AND COLUMN_NAME = 'business_license_url') THEN
        ALTER TABLE `shop_info` ADD COLUMN `business_license_url` VARCHAR(512) NULL DEFAULT NULL COMMENT '营业执照照片';
    END IF;
END$$
DELIMITER ;
CALL `shop_info_add_col_pay_apply_kyc`();
DROP PROCEDURE `shop_info_add_col_pay_apply_kyc`;
