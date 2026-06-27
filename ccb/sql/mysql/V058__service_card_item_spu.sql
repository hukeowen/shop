-- =====================================================================
-- V058 服务卡包改「选已有商品」：卡定义/卡实例增加 item_spu_id
--   item_spu_id = 该卡对应的单项服务商品（如 洗车/保养 的 SPU）。
--   服务包本身是一个普通商品(spu_id)，购买后按其下各 def 发卡，
--   每张卡通过 item_spu_id 指向真实的单项服务商品。
--   NULL 兼容老的「自定义卡」数据。
-- 兼容 MySQL 8.0.46，存储过程守卫幂等。
-- =====================================================================
DROP PROCEDURE IF EXISTS `_v058_add_item_spu`;
DELIMITER $$
CREATE PROCEDURE `_v058_add_item_spu`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_service_card_def' AND COLUMN_NAME='item_spu_id') THEN
        ALTER TABLE `shop_service_card_def` ADD COLUMN `item_spu_id` bigint NULL COMMENT '该卡对应的单项服务商品 SPU ID；NULL=自定义卡';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_service_card' AND COLUMN_NAME='item_spu_id') THEN
        ALTER TABLE `shop_service_card` ADD COLUMN `item_spu_id` bigint NULL COMMENT '该卡对应的单项服务商品 SPU ID；NULL=自定义卡';
    END IF;
END$$
DELIMITER ;
CALL `_v058_add_item_spu`();
DROP PROCEDURE IF EXISTS `_v058_add_item_spu`;
