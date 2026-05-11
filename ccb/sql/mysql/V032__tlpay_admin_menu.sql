-- V032 管理后台菜单 + 权限点：通联支付配置
--
-- 挂在「营销配置」6100 下；权限点：merchant:tlpay:{query,edit}
-- 用 INSERT IGNORE 做幂等（PK 冲突即跳过），可并发安全反复回放
SET NAMES utf8mb4;

-- 6300 通联支付配置（菜单）
INSERT IGNORE INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES (6300, '通联支付配置', '', 2, 50, 6100, 'tlpay', 'ep:money', 'merchant/tlpay/index', 'MerchantTlpayConfig', 0, 1, 1, 1, '1', NOW(), '1', NOW(), b'0');

-- 6301 通联配置 查询（权限点）
INSERT IGNORE INTO system_menu (id, name, permission, type, sort, parent_id, path, status, creator, create_time, updater, update_time, deleted)
VALUES (6301, '通联配置 查询', 'merchant:tlpay:query', 3, 1, 6300, '', 0, '1', NOW(), '1', NOW(), b'0');

-- 6302 通联配置 编辑（权限点）
INSERT IGNORE INTO system_menu (id, name, permission, type, sort, parent_id, path, status, creator, create_time, updater, update_time, deleted)
VALUES (6302, '通联配置 编辑', 'merchant:tlpay:edit', 3, 2, 6300, '', 0, '1', NOW(), '1', NOW(), b'0');

-- 把权限点分配给超级管理员 role 1（与现有 merchant:* 权限保持一致）
INSERT IGNORE INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted)
VALUES (1, 6300, '1', NOW(), '1', NOW(), b'0');

INSERT IGNORE INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted)
VALUES (1, 6301, '1', NOW(), '1', NOW(), b'0');

INSERT IGNORE INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted)
VALUES (1, 6302, '1', NOW(), '1', NOW(), b'0');
