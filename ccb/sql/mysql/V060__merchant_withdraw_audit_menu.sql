-- V060 平台运营 - 商户提现审核(6580) 菜单 + 审核按钮权限。幂等。
--   后端 MerchantWithdrawController(/merchant/withdraw/page|get|audit) + admin 页 merchant/withdraw/index 早已存在，
--   只缺菜单入口 → 平台点不进去。挂到「平台运营」(6500) 下。
--   商户在 tuo 端「提现申请（推广/邀请奖励）」提交 → 这里平台审核（通过传转账凭证 / 驳回填原因）。
INSERT INTO system_menu
  (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
VALUES
  (6580,'商户提现审核','merchant:withdraw:query',2,8,6500,'merchant-withdraw','ep:money','merchant/withdraw/index','MerchantWithdraw',0,b'1',b'1',b'0','1',NOW(),'1',NOW(),b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),component=VALUES(component),component_name=VALUES(component_name),parent_id=VALUES(parent_id),deleted=b'0';

INSERT INTO system_menu
  (id,name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,creator,create_time,updater,update_time,deleted)
VALUES
  (6581,'提现审核（通过/驳回）','merchant:withdraw:audit',3,1,6580,'','','',0,b'1',b'1','1',NOW(),'1',NOW(),b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),parent_id=VALUES(parent_id),deleted=b'0';
