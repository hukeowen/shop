-- =============================================================================
-- V025: shop_promo_config 加店级「满减」规则（仅文案展示用）
--
-- 对应原型 ④ 底部购物车栏「满 30 立减 5 · 还差 ¥17」（user-h5.html line 2726）
-- 商户在 me/promo-config 配置：满 X 元 立减 Y 元；C 端在 shop-home 实时算
-- 「还差多少元达门槛」。当前不参与结算；后续接入订单时再写入 trade。
--
-- 幂等：deploy.sh 不带 --reset 会重复跑，ALTER ADD COLUMN 必须用 PREPARE 包装。
-- =============================================================================

SET NAMES utf8mb4;

SET @x := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_promo_config'
             AND COLUMN_NAME = 'full_cut_threshold');
SET @s := IF(@x = 0,
  "ALTER TABLE `shop_promo_config` ADD COLUMN `full_cut_threshold` INT DEFAULT NULL COMMENT '满减门槛（分），NULL=不启用满减' AFTER `star_discount_rates`",
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @x := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_promo_config'
             AND COLUMN_NAME = 'full_cut_amount');
SET @s := IF(@x = 0,
  "ALTER TABLE `shop_promo_config` ADD COLUMN `full_cut_amount` INT DEFAULT NULL COMMENT '减免金额（分），与 full_cut_threshold 同时存在才有效' AFTER `full_cut_threshold`",
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
