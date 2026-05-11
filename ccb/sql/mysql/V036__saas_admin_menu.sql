-- V036 admin 后台菜单 + 权限点：SaaS 套餐配置

SET NAMES utf8mb4;

-- 6400 一级菜单：SaaS 运营
INSERT IGNORE INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES (6400, 'SaaS 运营', '', 1, 60, 0, 'saas', 'ep:sell', '', 0, 1, 1, 1, '1', NOW(), '1', NOW(), b'0');

-- 6401 二级菜单：套餐配置
INSERT IGNORE INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES (6401, '套餐配置', '', 2, 1, 6400, 'package-config', 'ep:list', 'saas/packageConfig/index', 'SaasPackageConfig', 0, 1, 1, 1, '1', NOW(), '1', NOW(), b'0');

-- 6402 权限点：查询
INSERT IGNORE INTO system_menu (id, name, permission, type, sort, parent_id, path, status, creator, create_time, updater, update_time, deleted)
VALUES (6402, '套餐配置 查询', 'merchant:saas-package:query', 3, 1, 6401, '', 0, '1', NOW(), '1', NOW(), b'0');

-- 6403 权限点：编辑
INSERT IGNORE INTO system_menu (id, name, permission, type, sort, parent_id, path, status, creator, create_time, updater, update_time, deleted)
VALUES (6403, '套餐配置 编辑', 'merchant:saas-package:edit', 3, 2, 6401, '', 0, '1', NOW(), '1', NOW(), b'0');

-- 分配给超管 role 1
INSERT IGNORE INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted) VALUES (1, 6400, '1', NOW(), '1', NOW(), b'0');
INSERT IGNORE INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted) VALUES (1, 6401, '1', NOW(), '1', NOW(), b'0');
INSERT IGNORE INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted) VALUES (1, 6402, '1', NOW(), '1', NOW(), b'0');
INSERT IGNORE INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted) VALUES (1, 6403, '1', NOW(), '1', NOW(), b'0');
