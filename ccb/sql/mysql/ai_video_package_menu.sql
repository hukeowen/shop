-- ============================================================
-- AI 视频套餐管理菜单 + 权限（Phase 0.3.4）
-- 执行前确认 ID 5000-5007 未被占用
-- ============================================================

-- 商户管理（一级目录）
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (5000, '商户管理', '', 1, 90, 0, '/merchant', 'ep:shop', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- AI 视频套餐（二级页面）
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (5001, 'AI视频套餐', '', 2, 10, 5000, 'ai-video-package', 'ep:video-play', 'merchant/aiVideoPackage/index', 'MerchantAiVideoPackage', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- 套餐权限
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(5002, '套餐查询', 'merchant:ai-video-package:query',  3, 1, 5001, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(5003, '套餐创建', 'merchant:ai-video-package:create', 3, 2, 5001, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(5004, '套餐更新', 'merchant:ai-video-package:update', 3, 3, 5001, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(5005, '套餐删除', 'merchant:ai-video-package:delete', 3, 4, 5001, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- 配额流水（二级页面）
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (5006, '配额流水', '', 2, 20, 5000, 'quota-log', 'ep:tickets', 'merchant/aiVideoPackage/QuotaLog', 'MerchantAiVideoQuotaLog', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- 配额流水权限
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (5007, '流水查询', 'merchant:ai-video-package:query', 3, 1, 5006, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- 将以上菜单赋予超级管理员角色（role_id=1）
INSERT IGNORE INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(1, 5000, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5001, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5002, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5003, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5004, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5005, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5006, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5007, 'admin', NOW(), 'admin', NOW(), b'0');
