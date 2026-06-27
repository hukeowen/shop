-- V057 平台运营 - 商品评价总览(6570) 菜单。幂等。
-- 跨租户查看各店铺评价（市场反馈#13：评价后台需呈现）。
INSERT INTO system_menu
  (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
VALUES
  (6570,'商品评价','merchant:platform:query',2,7,6500,'comment','ep:chat-line-square','platform/comment/index','PlatformComment',0,b'1',b'1',b'0','1',NOW(),'1',NOW(),b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),component=VALUES(component),component_name=VALUES(component_name),parent_id=VALUES(parent_id),deleted=b'0';
