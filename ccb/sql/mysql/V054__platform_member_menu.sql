-- V054 平台运营 - 会员管理(6550) 菜单。幂等。
INSERT INTO system_menu
  (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
VALUES
  (6550,'会员管理','merchant:platform:query',2,5,6500,'member','ep:user','platform/member/index','PlatformMember',0,b'1',b'1',b'0','1',NOW(),'1',NOW(),b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),component=VALUES(component),component_name=VALUES(component_name),parent_id=VALUES(parent_id),deleted=b'0';
