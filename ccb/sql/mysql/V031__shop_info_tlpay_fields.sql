-- V031 通联收付通 — 每商户独立直清模式字段
-- 商户在通联自己开户：cus_id + RSA 密钥对 + 回调地址
-- 老字段 tl_mch_id / tl_mch_key 保留兼容；新字段语义化命名
--
-- 注意：rsa_private_key / rsa_public_key 是 PEM 文本，长度可达 2KB+，用 TEXT
-- notify_url 可空 = 走全局默认 https://www.doupaidoudian.com/api/pay/callback/tlpay

SET NAMES utf8mb4;

-- 是否启用通联（关闭则该商户不支持线上支付）
SET @col := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_info' AND COLUMN_NAME = 'tl_enabled');
SET @sql := IF(@col = 0,
    'ALTER TABLE shop_info ADD COLUMN tl_enabled BIT(1) NOT NULL DEFAULT b''0'' COMMENT ''通联是否启用 0=关 1=开''',
    'SELECT ''tl_enabled exists''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 通联应用 ID（部分接口必传）
SET @col := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_info' AND COLUMN_NAME = 'tl_app_id');
SET @sql := IF(@col = 0,
    'ALTER TABLE shop_info ADD COLUMN tl_app_id VARCHAR(64) NULL COMMENT ''通联 appId''',
    'SELECT ''tl_app_id exists''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 商户 RSA 私钥（PEM）— 用于请求通联接口签名
SET @col := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_info' AND COLUMN_NAME = 'tl_rsa_private_key');
SET @sql := IF(@col = 0,
    'ALTER TABLE shop_info ADD COLUMN tl_rsa_private_key TEXT NULL COMMENT ''商户 RSA 私钥 PEM''',
    'SELECT ''tl_rsa_private_key exists''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 通联 RSA 公钥（PEM）— 用于验证通联异步回调签名
SET @col := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_info' AND COLUMN_NAME = 'tl_rsa_public_key');
SET @sql := IF(@col = 0,
    'ALTER TABLE shop_info ADD COLUMN tl_rsa_public_key TEXT NULL COMMENT ''通联 RSA 公钥 PEM''',
    'SELECT ''tl_rsa_public_key exists''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 异步回调地址（可空 = 走全局 application.yaml 配置的默认）
SET @col := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_info' AND COLUMN_NAME = 'tl_notify_url');
SET @sql := IF(@col = 0,
    'ALTER TABLE shop_info ADD COLUMN tl_notify_url VARCHAR(512) NULL COMMENT ''通联异步回调地址''',
    'SELECT ''tl_notify_url exists''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 签名算法（默认 RSA，将来支持 RSA2 等）
SET @col := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_info' AND COLUMN_NAME = 'tl_sign_type');
SET @sql := IF(@col = 0,
    'ALTER TABLE shop_info ADD COLUMN tl_sign_type VARCHAR(16) NOT NULL DEFAULT ''RSA'' COMMENT ''签名算法 RSA/RSA2''',
    'SELECT ''tl_sign_type exists''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 备注：tl_mch_id 已存在（通联 cusId）；tl_mch_key 兼容老字段（独立直清下不再使用）
