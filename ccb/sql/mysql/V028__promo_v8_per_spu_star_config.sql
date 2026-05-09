-- =============================================================================
-- V028：v7 → v8 改造 — 星级配置从商户级降到商品级 + 多件循环 + 本单抵扣 + 团队极差按商品
--
-- 核心规则变化（详见 docs/design/marketing-system-v8.md）：
--   1. 多件订单按件循环 N 次进入推 N 反 1 状态机（之前一单算 1 次）
--   2. 本单产生积分立即抵扣本单（按 SPU 独立、整件取整）
--   3. parent 首贡献奖按"1 件价"封顶（不论 child 几件）
--   4. 团队极差奖按商品级配置（之前商户级）；沿链就近递增算法
--   5. 升星按商品独立（user_star 加 spu_id 维度）
--   6. 星级奖池按商品独立累池
-- =============================================================================

-- 幂等约定：deploy.sh 不带 --reset 时会重复跑全部 V*.sql，
-- 所有 ALTER TABLE ADD COLUMN 必须用 INFORMATION_SCHEMA + PREPARE 包装，
-- 否则第二次跑会报 Duplicate column。

-- 强制 utf8mb4：deploy.sh mysql_safe 的默认连接 charset 可能是 latin1，
-- procedure 字符串参数（含中文 COMMENT）会按连接 charset 解析 → 报
-- "Incorrect string value '\x89'..."。在 V028 顶部统一 SET NAMES。
SET NAMES utf8mb4;

-- 通用列存在性检查工具：用 procedure 简化重复 PREPARE
DROP PROCEDURE IF EXISTS v028_add_column;
DELIMITER //
CREATE PROCEDURE v028_add_column(
  IN p_table VARCHAR(64) CHARACTER SET utf8mb4,
  IN p_column VARCHAR(64) CHARACTER SET utf8mb4,
  IN p_def TEXT CHARACTER SET utf8mb4
)
BEGIN
  DECLARE col_count INT DEFAULT 0;
  SELECT COUNT(*) INTO col_count
    FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = p_table
     AND COLUMN_NAME = p_column;
  IF col_count = 0 THEN
    SET @ddl := CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_def);
    PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END//
DELIMITER ;

-- 1. product_promo_config 加商品级星级配置 + 池子（5 列幂等）
CALL v028_add_column('product_promo_config', 'direct_rate',
  '`direct_rate` DECIMAL(5, 2) DEFAULT 0.00 COMMENT ''直推/间推奖比例（%）'' AFTER `tuijian_ratios`');
CALL v028_add_column('product_promo_config', 'star_count',
  '`star_count` INT NOT NULL DEFAULT 0 COMMENT ''该商品的星级数量（0=不启用团队极差奖）'' AFTER `direct_rate`');
CALL v028_add_column('product_promo_config', 'star_ratios',
  '`star_ratios` VARCHAR(255) DEFAULT NULL COMMENT ''各星级团队极差返奖比例 JSON 数组(%)'' AFTER `star_count`');
CALL v028_add_column('product_promo_config', 'star_upgrade_rules',
  '`star_upgrade_rules` TEXT DEFAULT NULL COMMENT ''升星规则 JSON [{star,directCount,teamSales(分)}]'' AFTER `star_ratios`');
CALL v028_add_column('product_promo_config', 'pool_ratio',
  '`pool_ratio` DECIMAL(5, 2) DEFAULT 0.00 COMMENT ''星级奖池入池比例(%)'' AFTER `star_upgrade_rules`');

-- 2. shop_user_star 改为 (user, spu) 维度（2 列幂等）
CALL v028_add_column('shop_user_star', 'spu_id',
  '`spu_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''商品 SPU ID（v8: 0=v7 老数据兼容）'' AFTER `user_id`');
CALL v028_add_column('shop_user_star', 'team_sales_amount',
  '`team_sales_amount` BIGINT NOT NULL DEFAULT 0 COMMENT ''团队链路在该商品上的销售实付累计(分)'' AFTER `team_sales_count`');

DROP PROCEDURE IF EXISTS v028_add_column;

-- 老索引可能挡 (user, spu) 双键 INSERT，先丢老索引（如有）
-- 老唯一键有两种历史形式：
--   - uk_user_id (user_id)              — 早期 single-tenant 版本
--   - uk_tenant_user (tenant_id, user_id) — marketing.sql 全新部署版本
-- 都跟 (tenant_id, user_id, spu_id) 改造冲突 → 必须全部 DROP 后再建新唯一键
SET @x := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_user_star'
             AND INDEX_NAME = 'uk_user_id');
SET @s := IF(@x > 0, 'ALTER TABLE shop_user_star DROP INDEX uk_user_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @x := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_user_star'
             AND INDEX_NAME = 'uk_tenant_user');
SET @s := IF(@x > 0, 'ALTER TABLE shop_user_star DROP INDEX uk_tenant_user', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 加 (tenant, user, spu) 唯一索引（v8）
SET @x := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_user_star'
             AND INDEX_NAME = 'uk_tenant_user_spu');
SET @s := IF(@x = 0,
  'ALTER TABLE shop_user_star ADD UNIQUE KEY uk_tenant_user_spu (tenant_id, user_id, spu_id, deleted)',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. spu_star_pool：按商品独立的奖池累计
CREATE TABLE IF NOT EXISTS `spu_star_pool` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `tenant_id`     BIGINT       NOT NULL DEFAULT 0,
  `spu_id`        BIGINT       NOT NULL,
  `pool_balance`  BIGINT       NOT NULL DEFAULT 0  COMMENT '当前池余额（分）',
  `total_in`      BIGINT       NOT NULL DEFAULT 0  COMMENT '历史累计入池金额（分）',
  `total_out`     BIGINT       NOT NULL DEFAULT 0  COMMENT '历史累计发放金额（分）',
  `creator`       VARCHAR(64)           DEFAULT '',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater`       VARCHAR(64)           DEFAULT '',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`       BIT(1)       NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_spu` (`tenant_id`, `spu_id`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'v8 商品级星级奖池累计';

-- 4. 抵扣流水：跟踪每笔订单 spu 行的"产生积分 / 抵扣件数 / 实付件数"对账用
CREATE TABLE IF NOT EXISTS `shop_promo_deduction_record` (
  `id`              BIGINT      NOT NULL AUTO_INCREMENT,
  `tenant_id`       BIGINT      NOT NULL,
  `order_id`        BIGINT      NOT NULL,
  `order_item_id`   BIGINT      NOT NULL,
  `user_id`         BIGINT      NOT NULL,
  `spu_id`          BIGINT      NOT NULL,
  `unit_price`      INT         NOT NULL  COMMENT '单件价（分）',
  `total_count`     INT         NOT NULL  COMMENT '订单件数',
  `produced_amount` BIGINT      NOT NULL  COMMENT '本单产生积分（分）',
  `deduct_count`    INT         NOT NULL  COMMENT '抵扣件数 K',
  `actual_paid`     INT         NOT NULL  COMMENT '实付金额（分） = (total_count - K) × unit_price',
  `creator`         VARCHAR(64)          DEFAULT '',
  `create_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater`         VARCHAR(64)          DEFAULT '',
  `update_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`         BIT(1)      NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_order` (`tenant_id`, `order_id`),
  KEY `idx_user_spu` (`user_id`, `spu_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'v8 推 N 反 1 / 直推奖立即抵扣流水（订单粒度，按 SPU 行）';
