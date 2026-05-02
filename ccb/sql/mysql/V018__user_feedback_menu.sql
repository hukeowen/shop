-- =============================================================================
-- 用户反馈管理 - 菜单 / 权限 / 角色绑定 / 套餐绑定
--
-- 解决 Phase 6 review CRIT-2：admin 控制器 @PreAuthorize 引用的权限点
-- merchant:user-feedback:query / update 在 system_menu 中不存在，
-- 导致页面对所有用户 403。
--
-- 注意：本文件可重复执行（DELETE + INSERT，防 ID 冲突）。
-- ID 段：6210..6219，避开 V005 占用的 6100..6199。
-- =============================================================================

DELETE FROM `system_menu` WHERE id BETWEEN 6210 AND 6219;

INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
  -- 6210：可访问页面（vue3-admin views/merchant/userFeedback/index.vue）
  (6210, '用户反馈管理', 'merchant:user-feedback:query', 2, 50, 6100, 'user-feedback', 'ep:chat-line-square',
        'merchant/userFeedback/index', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  -- 6211 / 6212：查询 / 更新 按钮
  (6211, '用户反馈 查询', 'merchant:user-feedback:query',  3, 10, 6210, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6212, '用户反馈 处理', 'merchant:user-feedback:update', 3, 20, 6210, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- 授予 超级管理员（role_id=1）
DELETE FROM `system_role_menu` WHERE role_id = 1 AND menu_id BETWEEN 6210 AND 6219;

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
  (1, 6210, 'admin', NOW(), 'admin', NOW(), b'0', 1),
  (1, 6211, 'admin', NOW(), 'admin', NOW(), b'0', 1),
  (1, 6212, 'admin', NOW(), 'admin', NOW(), b'0', 1);

-- 把"用户反馈管理"页加进"普通套餐"id=111 的 menu_ids，让所有租户管理员都可见
-- 子租户只能看到自己 tenantId 的反馈（控制器 platformTenantId 校验）
UPDATE `system_tenant_package`
SET `menu_ids` = (
  SELECT JSON_ARRAY_APPEND(
    JSON_ARRAY_APPEND(
      JSON_ARRAY_APPEND(`menu_ids`, '$', 6210),
      '$', 6211),
    '$', 6212)
  FROM (SELECT `menu_ids` FROM `system_tenant_package` WHERE id = 111) t
)
WHERE id = 111
  AND NOT JSON_CONTAINS(`menu_ids`, '6210', '$');
