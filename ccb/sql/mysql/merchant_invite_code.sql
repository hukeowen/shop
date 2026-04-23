-- ============================================================
-- Phase 0.2 迁移脚本：商户邀请码 + 摊小二统一小程序字段
--
-- 幂等性说明：
--   - 本脚本使用 CREATE TABLE IF NOT EXISTS 与 "条件列存在判断" 语法，可重复执行；
--   - 对 merchant_info / member_user 表使用了存储过程（ALTER TABLE ... ADD COLUMN IF NOT EXISTS
--     在 MySQL 5.x 不支持，故用 INFORMATION_SCHEMA 判断）。
--   - 反复执行不会报 "Duplicate column" 错误。
--
-- 变更要点：
--   - member_user.mini_app_open_id 采用 UNIQUE 索引（uk_mini_app_open_id）：
--     防并发 wxMiniLogin 重复注册同一 openid —— 并发两个请求同时 selectByOpenId 都拿到 null
--     然后双双 insert 的竞态场景下，UNIQUE 约束确保只有一条记录入表，另一条靠
--     INSERT IGNORE 吃掉 duplicate key error，最后统一回查拿到唯一会员记录。
--
-- 回滚 SQL（需要回滚时执行）：
--   DROP TABLE IF EXISTS `merchant_invite_code`;
--   ALTER TABLE `merchant_info`
--     DROP COLUMN `open_id`,
--     DROP COLUMN `union_id`,
--     DROP COLUMN `invite_code_id`;
--   ALTER TABLE `member_user` DROP INDEX `uk_mini_app_open_id`;
--   ALTER TABLE `member_user` DROP COLUMN `mini_app_open_id`;
--
-- 执行顺序：在 v2_business_tables.sql 之后
-- ============================================================

-- ----------------------------
-- 1. BD 商户邀请码表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `merchant_invite_code` (
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`              VARCHAR(32)  NOT NULL COMMENT '邀请码（6-12 位）',
    `operator_user_id`  BIGINT           DEFAULT NULL COMMENT '归属 BD 用户 ID（平台员工）',
    `usage_limit`       INT              DEFAULT -1 COMMENT '最大使用次数，-1 无限',
    `used_count`        INT              DEFAULT 0 COMMENT '已使用次数',
    `enabled`           BIT(1)           DEFAULT b'1' COMMENT '是否启用',
    `remark`            VARCHAR(255)     DEFAULT NULL COMMENT '备注',
    -- 公共字段
    `creator`           VARCHAR(64)      DEFAULT '' COMMENT '创建者',
    `create_time`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`           VARCHAR(64)      DEFAULT '' COMMENT '更新者',
    `update_time`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           BIT(1)  NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`         BIGINT  NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_operator` (`operator_user_id`),
    KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='BD 商户邀请码';

-- ----------------------------
-- 2. merchant_info 增加字段：open_id / union_id / invite_code_id
-- ----------------------------
DROP PROCEDURE IF EXISTS `merchant_info_add_col_phase_0_2`;
DELIMITER $$
CREATE PROCEDURE `merchant_info_add_col_phase_0_2`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'merchant_info' AND COLUMN_NAME = 'open_id') THEN
        ALTER TABLE `merchant_info` ADD COLUMN `open_id` VARCHAR(64) DEFAULT NULL COMMENT '摊小二商户小程序 OpenID' AFTER `user_id`;
        ALTER TABLE `merchant_info` ADD INDEX `idx_open_id` (`open_id`);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'merchant_info' AND COLUMN_NAME = 'union_id') THEN
        ALTER TABLE `merchant_info` ADD COLUMN `union_id` VARCHAR(64) DEFAULT NULL COMMENT '微信 UnionID' AFTER `open_id`;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'merchant_info' AND COLUMN_NAME = 'invite_code_id') THEN
        ALTER TABLE `merchant_info` ADD COLUMN `invite_code_id` BIGINT DEFAULT NULL COMMENT '注册时使用的邀请码 ID' AFTER `union_id`;
    END IF;
END$$
DELIMITER ;
CALL `merchant_info_add_col_phase_0_2`();
DROP PROCEDURE `merchant_info_add_col_phase_0_2`;

-- ----------------------------
-- 3. member_user 增加字段：mini_app_open_id
-- ----------------------------
DROP PROCEDURE IF EXISTS `member_user_add_col_phase_0_2`;
DELIMITER $$
CREATE PROCEDURE `member_user_add_col_phase_0_2`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member_user' AND COLUMN_NAME = 'mini_app_open_id') THEN
        ALTER TABLE `member_user` ADD COLUMN `mini_app_open_id` VARCHAR(64) DEFAULT NULL COMMENT '摊小二商户统一小程序 OpenID' AFTER `id`;
        -- UNIQUE 防并发 wxMiniLogin 重复注册同一 openid（竞态下靠 duplicate key 兜底）
        ALTER TABLE `member_user` ADD UNIQUE INDEX `uk_mini_app_open_id` (`mini_app_open_id`);
    END IF;
END$$
DELIMITER ;
CALL `member_user_add_col_phase_0_2`();
DROP PROCEDURE `member_user_add_col_phase_0_2`;
