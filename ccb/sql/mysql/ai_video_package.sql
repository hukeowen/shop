-- 幂等脚本：已存在列 / 表跳过。回滚 SQL 在注释尾部。
-- =====================================================================
-- Phase 0.3.1 ─ 视频配额 + 套餐（支付侧回调在 0.3.3 再接）
-- =====================================================================
-- 关联表：merchant_info（DO 名 MerchantDO 的实际 table name）
-- 本迁移脚本追加一列 video_quota_remaining 到 merchant_info 表，
-- 并新增两张业务表：ai_video_package（平台级套餐定义）
-- 和 merchant_video_quota_log（商户配额流水账）。
--
-- 关键约束：merchant_video_quota_log.(biz_type, biz_id) UNIQUE
-- → 防支付回调重复加配额；VIDEO_GEN 扣减用 UUID 天然唯一不受影响；
--   MANUAL_ADJUST 的 biz_id 可能为 NULL，MySQL UNIQUE 对 NULL 视为不等，
--   多条 NULL 允许共存，OK。
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
    UNIQUE KEY `uk_biz` (`biz_type`, `biz_id`)
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

-- =====================================================================
-- Phase 0.3.3 ─ 商户买套餐的业务订单（和 pay_order 解耦）
-- =====================================================================
-- 本表与 yudao-module-pay 的 pay_order 1:1 映射（通过 uk_pay_order 约束），
-- 但两张表独立生命周期：pay_order 记录支付渠道 + 金额结算，
-- merchant_package_order 记录商户业务语义（哪个商户买哪个套餐、支付到账后给多少配额）。
--
-- 幂等链路：
--   - 支付渠道回调 → pay_order 更新 status=10 → pay 模块 POST notify_url
--   - merchant 模块回调 controller → increaseVideoQuota(biz_type=1, biz_id=payOrderId)
--   - merchant_video_quota_log 的 UNIQUE uk_biz(biz_type, biz_id) 保证配额不重复增加
-- =====================================================================
CREATE TABLE IF NOT EXISTS `merchant_package_order` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `merchant_id` BIGINT NOT NULL COMMENT '商户 ID',
    `package_id` BIGINT NOT NULL COMMENT '套餐 ID',
    `package_name` VARCHAR(64) NOT NULL COMMENT '下单时套餐名称快照（防止套餐改名影响历史订单）',
    `video_count` INT NOT NULL COMMENT '套餐包含视频条数快照（下单时锁定）',
    `price` BIGINT NOT NULL COMMENT '下单实付金额（分）快照',
    `pay_order_id` BIGINT DEFAULT NULL COMMENT 'pay_order 的主键（可为空：创建失败时）',
    `pay_status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=待支付 10=已支付 20=已关闭 30=已退款',
    `pay_time` DATETIME DEFAULT NULL COMMENT '支付成功时间',
    `quota_log_id` BIGINT DEFAULT NULL COMMENT '支付成功时写入 merchant_video_quota_log 的主键',
    `creator` VARCHAR(64) DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_merchant` (`merchant_id`, `create_time` DESC),
    UNIQUE KEY `uk_pay_order` (`pay_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 视频套餐订单';

-- =====================================================================
-- 回滚（生产环境慎用）：
-- DROP TABLE IF EXISTS `merchant_package_order`;
-- =====================================================================
