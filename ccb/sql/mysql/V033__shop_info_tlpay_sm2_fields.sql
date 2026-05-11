-- V033 通联收付通 — 商户 SM2 签名字段
-- 通联同时支持 RSA / SM2 两套签名（商户在通联控制台配的是哪种就用哪种）
-- V031 已加 tl_rsa_private_key / tl_rsa_public_key（RSA 模式）
-- 本迁移补 tl_sm2_private_key / tl_sm2_public_key（SM2 模式 — 国密合规场景）
--
-- 加密：和 RSA 字段一样用 AES TypeHandler（EncryptTypeHandler）
-- shop_info 已设 autoResultMap=true，新字段加 @TableField 后自动生效

SET NAMES utf8mb4;

SET @col := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_info' AND COLUMN_NAME = 'tl_sm2_private_key');
SET @sql := IF(@col = 0,
    'ALTER TABLE shop_info ADD COLUMN tl_sm2_private_key TEXT NULL COMMENT ''商户 SM2 私钥 PEM（AES 加密存储；通联签名 PLAIN 编码 userId=appid）''',
    'SELECT ''tl_sm2_private_key exists''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_info' AND COLUMN_NAME = 'tl_sm2_public_key');
SET @sql := IF(@col = 0,
    'ALTER TABLE shop_info ADD COLUMN tl_sm2_public_key TEXT NULL COMMENT ''通联 SM2 公钥 PEM（AES 加密存储；验回调签名）''',
    'SELECT ''tl_sm2_public_key exists''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
