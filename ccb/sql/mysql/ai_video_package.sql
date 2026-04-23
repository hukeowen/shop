-- 幂等脚本：已存在列 / 表跳过。回滚 SQL 在注释尾部。
-- =====================================================================
-- Phase 0.3.1 ─ 视频配额 + 套餐（支付侧回调在 0.3.3 再接）
-- =====================================================================
-- 关联表：merchant_info（DO 名 MerchantDO 的实际 table name）
-- 本迁移脚本追加一列 video_quota_remaining 到 merchant_info 表，
-- 并新增两张业务表：ai_video_package（平台级套餐定义）
-- 和 merchant_video_quota_log（商户配额流水账）。
-- =====================================================================

-- 1. 套餐定义表（平台级，tenant_id 恒为 0，不按商户/租户隔离查询）
CREATE TABLE IF NOT EXISTS `ai_video_package` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name` VARCHAR(64) NOT NULL COMMENT '套餐名，如「体验装 5 条」',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '套餐描述（销售话术）',
    `video_count` INT NOT NULL COMMENT '附赠视频条数',
    `price` BIGINT NOT NULL COMMENT '售价（分）',
    `original_price` BIGINT DEFAULT NULL COMMENT '划线原价（分）',
    `sort` INT DEFAULT 0 COMMENT '排序值，倒序展示',
    `status` TINYINT DEFAULT 0 COMMENT '0=上架 1=下架',
    `creator` VARCHAR(64) DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_status_sort` (`status`, `sort` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 视频套餐';

-- 2. 配额流水账（所有 + / - 都写一条，便于审计与对账）
CREATE TABLE IF NOT EXISTS `merchant_video_quota_log` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `merchant_id` BIGINT NOT NULL COMMENT '商户 ID',
    `quota_change` INT NOT NULL COMMENT '变动值（正数 = 增加，负数 = 扣减）',
    `quota_after` INT NOT NULL COMMENT '变动后余量（事务内对齐）',
    `biz_type` TINYINT NOT NULL COMMENT '1=购买套餐 2=视频生成扣减 3=生成失败回补 4=平台手动调整',
    `biz_id` VARCHAR(64) DEFAULT NULL COMMENT '业务外键（套餐订单号/视频任务 ID/操作员 ID）',
    `remark` VARCHAR(255) DEFAULT NULL,
    `creator` VARCHAR(64) DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_merchant_time` (`merchant_id`, `create_time` DESC),
    KEY `idx_biz` (`biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 视频配额流水';

-- 3. 商户表补列：video_quota_remaining（INFORMATION_SCHEMA 守护，允许反复执行）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'merchant_info' AND COLUMN_NAME = 'video_quota_remaining');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `merchant_info` ADD COLUMN `video_quota_remaining` INT NOT NULL DEFAULT 0 COMMENT ''AI 视频剩余配额条数'' AFTER `douyin_token_expire_time`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 回滚（生产环境慎用）：
-- DROP TABLE IF EXISTS `merchant_video_quota_log`;
-- DROP TABLE IF EXISTS `ai_video_package`;
-- ALTER TABLE `merchant_info` DROP COLUMN `video_quota_remaining`;
-- =====================================================================
