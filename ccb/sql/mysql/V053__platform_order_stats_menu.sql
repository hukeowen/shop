-- =====================================================================
-- V053 平台运营 - 数据概览(6540,排首) + 订单总览(6530) 菜单
-- 幂等：ON DUPLICATE KEY UPDATE
-- =====================================================================

INSERT INTO system_menu
  (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
VALUES
  (6540,'数据概览','merchant:platform:query',2,0,6500,'overview','ep:data-line','platform/stats/index','PlatformStats',0,b'1',b'1',b'0','1',NOW(),'1',NOW(),b'0'),
  (6530,'订单总览','merchant:platform:query',2,3,6500,'order','ep:list','platform/order/index','PlatformOrder',0,b'1',b'1',b'0','1',NOW(),'1',NOW(),b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),component=VALUES(component),component_name=VALUES(component_name),parent_id=VALUES(parent_id),sort=VALUES(sort),deleted=b'0';
