-- =====================================================================
-- V055 店铺管理：shop_info 加 通联费率 + 每店自动上架开关
--   tl_fee_rate   通联支付费率（平台运营可改的数字，展示/记录用）
--   auto_approve  该店商品是否免审核自动上架（1=是默认 0=需平台审核）
-- 兼容 MySQL 8.0.46，存储过程守卫幂等。
-- =====================================================================
DROP PROCEDURE IF EXISTS `_v055_add_shop_cols`;
DELIMITER $$
CREATE PROCEDURE `_v055_add_shop_cols`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='tl_fee_rate') THEN
        ALTER TABLE `shop_info` ADD COLUMN `tl_fee_rate` varchar(16) NULL COMMENT '通联支付费率（平台可调的数字）';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shop_info' AND COLUMN_NAME='auto_approve') THEN
        ALTER TABLE `shop_info` ADD COLUMN `auto_approve` tinyint NOT NULL DEFAULT 1 COMMENT '该店商品免审核自动上架 1是 0需审核';
    END IF;
END$$
DELIMITER ;
CALL `_v055_add_shop_cols`();
DROP PROCEDURE IF EXISTS `_v055_add_shop_cols`;

-- 平台运营 - 店铺管理(6560) 菜单
INSERT INTO system_menu
  (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
VALUES
  (6560,'店铺管理','merchant:platform:query',2,1,6500,'shop','ep:shop','platform/shop/index','PlatformShop',0,b'1',b'1',b'0','1',NOW(),'1',NOW(),b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),component=VALUES(component),component_name=VALUES(component_name),parent_id=VALUES(parent_id),deleted=b'0';
