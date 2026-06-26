-- =====================================================================
-- V051 平台运营总览 独立菜单（不改 yudao 自带商城菜单）
--   6500 平台运营（目录）
--     6510 商品总览（跨租户全部店铺商品）
-- 超管(super_admin)默认可见所有菜单，无需额外授权。
-- 幂等：ON DUPLICATE KEY UPDATE，可重复执行。
-- =====================================================================

INSERT INTO system_menu
  (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
VALUES
  (6500,'平台运营','',1,91,0,'/platform','ep:data-board','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),icon=VALUES(icon),type=VALUES(type),parent_id=VALUES(parent_id),deleted=b'0';

INSERT INTO system_menu
  (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
VALUES
  (6510,'商品总览','merchant:platform:query',2,1,6500,'product','ep:goods','platform/product/index','PlatformProductOverview',0,b'1',b'1',b'0','1',NOW(),'1',NOW(),b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),component=VALUES(component),component_name=VALUES(component_name),parent_id=VALUES(parent_id),deleted=b'0';
