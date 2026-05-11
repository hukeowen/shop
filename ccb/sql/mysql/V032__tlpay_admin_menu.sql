-- V032 管理后台菜单 + 权限点：通联支付配置
--
-- 挂在「营销配置」6100 下；权限点：merchant:tlpay:{query,edit}
-- 注意：使用 INFORMATION_SCHEMA / NOT EXISTS 包装做幂等
SET NAMES utf8mb4;

-- 6300 通联支付配置（菜单）
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 6300, '通联支付配置', '', 2, 50, 6100, 'tlpay', 'ep:money', 'merchant/tlpay/index', 'MerchantTlpayConfig', 0, 1, 1, 1, '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 6300);

-- 6301 通联配置 查询（权限点）
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, status, creator, create_time, updater, update_time, deleted)
SELECT 6301, '通联配置 查询', 'merchant:tlpay:query', 3, 1, 6300, '', 0, '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 6301);

-- 6302 通联配置 编辑（权限点）
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, status, creator, create_time, updater, update_time, deleted)
SELECT 6302, '通联配置 编辑', 'merchant:tlpay:edit', 3, 2, 6300, '', 0, '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 6302);

-- 把权限点分配给超级管理员 role 1（与现有 merchant:* 权限保持一致）
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted)
SELECT 1, 6300, '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM system_role_menu WHERE role_id = 1 AND menu_id = 6300);

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted)
SELECT 1, 6301, '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM system_role_menu WHERE role_id = 1 AND menu_id = 6301);

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted)
SELECT 1, 6302, '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM system_role_menu WHERE role_id = 1 AND menu_id = 6302);
