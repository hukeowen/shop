-- =====================================================================
-- V056 商品级营销：product_promo_config 加 自然队列/自然推 开关
--   natural_push_enabled  NULL=用商户级兜底 1=队首返奖 0=吞奖（仅真自然用户订单）
-- 兼容 MySQL 8.0.46，存储过程守卫幂等。
-- =====================================================================
DROP PROCEDURE IF EXISTS `_v056_add_natural_push`;
DELIMITER $$
CREATE PROCEDURE `_v056_add_natural_push`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_promo_config' AND COLUMN_NAME='natural_push_enabled') THEN
        ALTER TABLE `product_promo_config` ADD COLUMN `natural_push_enabled` bit(1) NULL COMMENT '自然队列/自然推 商品级开关 NULL=商户级兜底';
    END IF;
END$$
DELIMITER ;
CALL `_v056_add_natural_push`();
DROP PROCEDURE IF EXISTS `_v056_add_natural_push`;
