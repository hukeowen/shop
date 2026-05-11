-- V034 SaaS 订阅体系
-- 1) merchant_info 加 service_expire_at / service_package_level / is_platform
-- 2) saas_package_config 套餐定义表 + 298/1688 种子数据
-- 3) merchant_subscription_order 订阅订单表（商户付款记录 + 通联流水）

SET NAMES utf8mb4;

-- ============ merchant_info 字段 ============

SET @col := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'merchant_info' AND COLUMN_NAME = 'service_expire_at');
SET @sql := IF(@col = 0,
    'ALTER TABLE merchant_info ADD COLUMN service_expire_at DATETIME NULL COMMENT ''SaaS 服务到期时间（NULL = 永久 / 平台商户）''',
    'SELECT ''service_expire_at exists''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'merchant_info' AND COLUMN_NAME = 'service_package_level');
SET @sql := IF(@col = 0,
    'ALTER TABLE merchant_info ADD COLUMN service_package_level VARCHAR(16) NOT NULL DEFAULT ''TRIAL'' COMMENT ''当前生效套餐：TRIAL=30 天试用(PRO 功能) / BASIC=298 / PRO=1688 / PLATFORM=平台商户''',
    'SELECT ''service_package_level exists''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'merchant_info' AND COLUMN_NAME = 'is_platform');
SET @sql := IF(@col = 0,
    'ALTER TABLE merchant_info ADD COLUMN is_platform BIT(1) NOT NULL DEFAULT b''0'' COMMENT ''是否平台商户（永久 + 全功能 + 不会过期）''',
    'SELECT ''is_platform exists''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ============ saas_package_config 套餐定义表 ============

CREATE TABLE IF NOT EXISTS saas_package_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    level VARCHAR(16) NOT NULL COMMENT 'BASIC / PRO',
    name VARCHAR(64) NOT NULL COMMENT '套餐名',
    price_fen INT NOT NULL COMMENT '年费（分）',
    duration_days INT NOT NULL DEFAULT 365 COMMENT '一次购买的天数（默认 365）',
    ai_video_grant INT NOT NULL DEFAULT 0 COMMENT '赠送 AI 视频次数',
    features VARCHAR(512) DEFAULT NULL COMMENT 'JSON 数组：可用功能 key 列表',
    sort INT NOT NULL DEFAULT 0 COMMENT '展示排序（小→大）',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=上架 1=下架',
    creator VARCHAR(64) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_level (level, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SaaS 套餐配置';

-- 种子数据：298 基础 + 1688 全功能
INSERT IGNORE INTO saas_package_config (id, level, name, price_fen, duration_days, ai_video_grant, features, sort, status)
VALUES
    (1, 'BASIC', '基础包', 29800, 365, 10, '["order","tuijian"]', 1, 0),
    (2, 'PRO', '全功能包', 168800, 365, 30, '["order","tuijian","team","star","pool","brokerage"]', 2, 0);

-- ============ merchant_subscription_order 订阅订单表 ============

CREATE TABLE IF NOT EXISTS merchant_subscription_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    merchant_id BIGINT NOT NULL COMMENT 'merchant_info.id',
    level VARCHAR(16) NOT NULL COMMENT '购买的套餐档位 BASIC/PRO',
    price_fen INT NOT NULL,
    duration_days INT NOT NULL,
    ai_video_grant INT NOT NULL,
    pay_status TINYINT NOT NULL DEFAULT 0 COMMENT '0=WAITING 1=PAID 2=CANCELLED',
    pay_at DATETIME DEFAULT NULL,
    pay_amount_fen INT DEFAULT NULL COMMENT '实际通联回执金额',
    tl_reqsn VARCHAR(32) DEFAULT NULL COMMENT '通联交易 reqsn（S 前缀）',
    expire_before DATETIME DEFAULT NULL COMMENT '付款前的到期时间（审计用）',
    expire_after DATETIME DEFAULT NULL COMMENT '付款后扩展到的到期时间',
    creator VARCHAR(64) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    KEY idx_merchant (merchant_id),
    KEY idx_pay_status (pay_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SaaS 商户订阅订单';
