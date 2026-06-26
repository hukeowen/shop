-- =====================================================================
-- V052 平台运营 - 店铺套餐 菜单（6520，挂在 6500 平台运营 下）
-- 幂等：ON DUPLICATE KEY UPDATE
-- =====================================================================

INSERT INTO system_menu
  (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
VALUES
  (6520,'店铺套餐','merchant:platform:query',2,2,6500,'subscription','ep:price-tag','platform/subscription/index','PlatformSubscription',0,b'1',b'1',b'0','1',NOW(),'1',NOW(),b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),component=VALUES(component),component_name=VALUES(component_name),parent_id=VALUES(parent_id),deleted=b'0';
