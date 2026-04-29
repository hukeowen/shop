-- Phase 1.8: 在线支付进件 KYC 资质字段（身份证 + 营业执照）
-- 在 shop_info 表追加进件资质 TOS key 列（幂等）
--
-- 商户提交开通申请时上传 3 张图：身份证正/反面 + 营业执照
-- 用 acl='private' 上传到 TOS，DB 只存 key（不存 url，因为预签名 URL 会过期）；
-- 显示时由 sidecar /oss/sign?key=xxx 现签 1h 临时 URL。
-- 历史 *_url 字段（如演示期间已建）会被自动 rename 为 *_key 保留数据。

DROP PROCEDURE IF EXISTS `shop_info_add_col_pay_apply_kyc`;
DELIMITER $$
CREATE PROCEDURE `shop_info_add_col_pay_apply_kyc`()
BEGIN
    -- 历史已建的 *_url 列：rename 成 *_key（保留数据）
    IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_info' AND COLUMN_NAME = 'id_card_front_url')
       AND NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_info' AND COLUMN_NAME = 'id_card_front_key') THEN
        ALTER TABLE `shop_info` CHANGE COLUMN `id_card_front_url` `id_card_front_key`
            VARCHAR(512) NULL DEFAULT NULL COMMENT '法人身份证正面 TOS key（私有，访问需预签名）';
    END IF;
    IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_info' AND COLUMN_NAME = 'id_card_back_url')
       AND NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_info' AND COLUMN_NAME = 'id_card_back_key') THEN
        ALTER TABLE `shop_info` CHANGE COLUMN `id_card_back_url` `id_card_back_key`
            VARCHAR(512) NULL DEFAULT NULL COMMENT '法人身份证背面 TOS key（私有，访问需预签名）';
    END IF;
    IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_info' AND COLUMN_NAME = 'business_license_url')
       AND NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_info' AND COLUMN_NAME = 'business_license_key') THEN
        ALTER TABLE `shop_info` CHANGE COLUMN `business_license_url` `business_license_key`
            VARCHAR(512) NULL DEFAULT NULL COMMENT '营业执照 TOS key（私有，访问需预签名）';
    END IF;
    -- 全新建库：直接添加 *_key 列
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_info' AND COLUMN_NAME = 'id_card_front_key') THEN
        ALTER TABLE `shop_info` ADD COLUMN `id_card_front_key` VARCHAR(512) NULL DEFAULT NULL COMMENT '法人身份证正面 TOS key（私有，访问需预签名）';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_info' AND COLUMN_NAME = 'id_card_back_key') THEN
        ALTER TABLE `shop_info` ADD COLUMN `id_card_back_key` VARCHAR(512) NULL DEFAULT NULL COMMENT '法人身份证背面 TOS key（私有，访问需预签名）';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_info' AND COLUMN_NAME = 'business_license_key') THEN
        ALTER TABLE `shop_info` ADD COLUMN `business_license_key` VARCHAR(512) NULL DEFAULT NULL COMMENT '营业执照 TOS key（私有，访问需预签名）';
    END IF;
END$$
DELIMITER ;
CALL `shop_info_add_col_pay_apply_kyc`();
DROP PROCEDURE `shop_info_add_col_pay_apply_kyc`;
