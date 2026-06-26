-- =====================================================================
-- V049 商户进件：门店照片（门头）+ 店内照片
--
-- 背景：在线支付开通（pay-apply）审核时，审核员除了证件照，还需要看
--       「门店照片（门头/招牌）」和「店内照片（经营场所内部）」来核实
--       商户真实存在。给 shop_info 补两个 TOS key 列，商户 H5 上传，
--       审核后台预览。
--
-- 说明：存的是私有对象的 TOS key（与 id_card_front_key 等同套），
--       前端拿 key 再调 /oss/sign 或后端 kyc-sign 现签 1h 预签名 URL 显示。
--
-- 兼容：MySQL 8.0.46，存储过程守卫幂等，可重复执行。
-- =====================================================================

DROP PROCEDURE IF EXISTS `_v049_add_store_photo_cols`;
DELIMITER $$
CREATE PROCEDURE `_v049_add_store_photo_cols`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='store_pic_key') THEN
        ALTER TABLE `shop_info` ADD COLUMN `store_pic_key` varchar(255) NULL COMMENT '门店照片（门头/招牌）TOS key';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='indoor_pic_key') THEN
        ALTER TABLE `shop_info` ADD COLUMN `indoor_pic_key` varchar(255) NULL COMMENT '店内照片（经营场所内部）TOS key';
    END IF;
END$$
DELIMITER ;
CALL `_v049_add_store_photo_cols`();
DROP PROCEDURE IF EXISTS `_v049_add_store_photo_cols`;
