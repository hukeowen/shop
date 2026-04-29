-- =============================================================================
-- 给 shop_promo_config 加 pool_settle_mode 列（cron 自动结算用）
--
-- 幂等：先查 information_schema 判断列是否存在，不存在才 ADD COLUMN。
-- 历史商户记录默认 FULL（全员均分），与之前的隐式行为一致。
-- =============================================================================

SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'shop_promo_config'
    AND column_name = 'pool_settle_mode'
);

SET @ddl = IF(@col_exists = 0,
  "ALTER TABLE `shop_promo_config` ADD COLUMN `pool_settle_mode` VARCHAR(16) NOT NULL DEFAULT 'FULL' COMMENT 'cron 自动结算模式：FULL=全员均分 / LOTTERY=抽奖' AFTER `pool_lottery_ratio`",
  "SELECT 'pool_settle_mode column already exists, skipping' AS noop"
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
