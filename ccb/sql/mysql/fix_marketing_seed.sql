-- =============================================================================
-- 摊小二 营销体系 v6 权限 / 菜单种子
--
-- 注意：本文件可重复执行（DELETE + INSERT，防 ID 冲突）。
-- 所有 ID 在 6100-6199 段，避免与 yudao 已有种子（max ≈ 5985）冲突。
-- =============================================================================

-- ----------------------------
-- 营销引擎 - 菜单 / 按钮
-- ----------------------------
DELETE FROM `system_menu` WHERE id BETWEEN 6100 AND 6199;

-- 6100：父菜单目录（type=1）— PC 后台 yudao-ui-admin-vue3 左侧导航顶级
-- 6107/6108/6109：实际可点击的菜单（type=2），挂 vue3-admin component path
-- 6101..6106：纯权限按钮（type=3），用于精细化「查询 / 更新 / 审批」按钮控制
INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
  (6100, '营销引擎',                    '',                                1, 100, 0,    'promo',         'ep:medal',           NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),

-- 6107 / 6108 / 6109：可访问页面（vue3-admin views/merchant/promo/**）
  (6107, '商户营销配置',                'merchant:promo-config:query',     2,   5, 6100, 'config',         'ep:setting',         'merchant/promo/config/index',         NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6108, '商品营销配置',                'merchant:product-promo-config:query', 2,  15, 6100, 'product-config', 'ep:goods',         'merchant/promo/productConfig/index',  NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6109, '推广积分提现',                'merchant:promo-withdraw:query',   2,  45, 6100, 'withdraw',       'ep:money',           'merchant/promo/withdraw/index',       NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),

-- 6101 / 6102：商户级营销配置 查询 / 更新（按钮）
  (6101, '商户营销配置 查询',            'merchant:promo-config:query',     3,  10, 6107, '',              '',                   '',   NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6102, '商户营销配置 更新',            'merchant:promo-config:update',    3,  20, 6107, '',              '',                   '',   NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),

-- 6103 / 6104：商品级营销配置 查询 / 更新（按钮）
  (6103, '商品营销配置 查询',            'merchant:product-promo-config:query',  3,  30, 6108, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6104, '商品营销配置 更新',            'merchant:product-promo-config:update', 3,  40, 6108, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),

-- 6105 / 6106：推广积分提现 查询 / 审批（按钮）
  (6105, '推广积分提现 查询',            'merchant:promo-withdraw:query',        3,  50, 6109, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6106, '推广积分提现 审批',            'merchant:promo-withdraw:approve',      3,  60, 6109, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- ----------------------------
-- 授予 超级管理员（role_id = 1）以上 4 个权限
-- ----------------------------
DELETE FROM `system_role_menu`
  WHERE role_id = 1 AND menu_id BETWEEN 6100 AND 6199;

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
  (1, 6100, 'admin', NOW(), 'admin', NOW(), b'0', 1),
  (1, 6101, 'admin', NOW(), 'admin', NOW(), b'0', 1),
  (1, 6102, 'admin', NOW(), 'admin', NOW(), b'0', 1),
  (1, 6103, 'admin', NOW(), 'admin', NOW(), b'0', 1),
  (1, 6104, 'admin', NOW(), 'admin', NOW(), b'0', 1),
  (1, 6105, 'admin', NOW(), 'admin', NOW(), b'0', 1),
  (1, 6106, 'admin', NOW(), 'admin', NOW(), b'0', 1),
  (1, 6107, 'admin', NOW(), 'admin', NOW(), b'0', 1),
  (1, 6108, 'admin', NOW(), 'admin', NOW(), b'0', 1),
  (1, 6109, 'admin', NOW(), 'admin', NOW(), b'0', 1);

-- ----------------------------
-- Quartz 定时任务：积分池自动结算 Job
--   handler_name 必须 = JobHandler bean 名（@Component 默认是首字母小写：promoPoolSettleJob）
--   状态默认 STOP(2)；商户接入后到 后台 → 基础设施 → 定时任务 启用
--   cron 默认每小时整点跑一次；Job 内部按各租户的 PromoConfig.poolSettleCron 决定是否真触发结算
-- ----------------------------
DELETE FROM `infra_job` WHERE id = 6200;
INSERT INTO `infra_job`
  (`id`, `name`, `status`, `handler_name`, `handler_param`, `cron_expression`,
   `retry_count`, `retry_interval`, `monitor_timeout`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
  (6200, '积分池自动结算 Job', 2, 'promoPoolSettleJob', NULL, '0 0 * * * ?',
   0, 0, 0, 'admin', NOW(), 'admin', NOW(), b'0');

-- ----------------------------
-- 备注：
-- 1. 商户子租户的"管理员角色"通常 code 是 'tenant_admin'，每个租户的 role_id 不同；
--    yudao 框架会在租户初始化时把"租户套餐 menu_ids"自动挂给该租户的 admin 角色，
--    所以只要把 menu_id (6100, 6107, 6108, 6109 必选；6101..6106 按需) 加入
--    相应的 system_tenant_package.menu_ids 字段即可。
--    这里不直接写 SQL（避免改坏既有套餐），交由部署后通过 UI 分配。
-- 2. 商户/用户 H5（uniapp）走 /app-api/merchant/mini/promo/**，无 @PreAuthorize，
--    不依赖以上权限即可工作。
-- =============================================================================
