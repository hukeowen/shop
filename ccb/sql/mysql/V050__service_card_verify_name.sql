-- =====================================================================
-- V050 服务卡核销流水：冗余服务名快照 card_name
--
-- 背景：核销记录原来只存 card_id，展示时实时关联卡名。一旦服务卡/商品
--       被删（软删），关联查不到 → 历史核销记录服务名变空。核销流水是
--       审计数据，应在核销当时把服务名快照下来，永不丢。
--
-- 兼容：MySQL 8.0.46，存储过程守卫幂等，可重复执行。
-- =====================================================================

DROP PROCEDURE IF EXISTS `_v050_add_verify_card_name`;
DELIMITER $$
CREATE PROCEDURE `_v050_add_verify_card_name`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_service_card_verify' AND COLUMN_NAME='card_name') THEN
        ALTER TABLE `shop_service_card_verify` ADD COLUMN `card_name` varchar(64) NULL COMMENT '服务卡名称快照（核销当时记下）' AFTER `card_id`;
    END IF;
END$$
DELIMITER ;
CALL `_v050_add_verify_card_name`();
DROP PROCEDURE IF EXISTS `_v050_add_verify_card_name`;

-- 回填历史：用关联卡名（含已软删的卡也回填，尽量补全老记录）
UPDATE `shop_service_card_verify` v
JOIN `shop_service_card` c ON c.id = v.card_id
SET v.card_name = c.name
WHERE (v.card_name IS NULL OR v.card_name = '') AND c.name IS NOT NULL;
