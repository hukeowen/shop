-- V037 admin 菜单重组：通联支付配置 + SaaS 套餐 → 归入「商户管理」(5000) 目录
--
-- 历史脉络：
--   V032 把 6300「通联支付配置」挂在 6100「营销引擎」下 — 语义不对
--   V036 给 SaaS 套餐建了独立一级菜单 6400「SaaS 运营」+ 子菜单 6401 — 太碎
--   ai_video_package_menu.sql 已经在 5000 下建了「商户管理」目录，AI 视频套餐挂这里
--
-- 这次重组：
--   · 6300 通联支付配置  → parent_id=5000，sort=30（5001 AI 视频套餐 之后）
--   · 6401 SaaS 套餐配置 → parent_id=5000，sort=40
--   · 6400 SaaS 运营（独立一级目录）→ 删除（合并到 5000 商户管理下）
--
-- 兜底：万一 V032 / V036 没生效，先 INSERT IGNORE 把缺失的菜单 + 权限点 + role_menu 关联补齐。
-- 全文件可反复执行（INSERT IGNORE + UPDATE 天然幂等）。

SET NAMES utf8mb4;

-- ───────────────────────────────────────────────────────────────────────
-- 1. 兜底：商户管理一级目录 5000（来自 ai_video_package_menu.sql，万一也没跑）
-- ───────────────────────────────────────────────────────────────────────
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (5000, '商户管理', '', 1, 90, 0, '/merchant', 'ep:shop', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- ───────────────────────────────────────────────────────────────────────
-- 2. 兜底：V032 通联支付配置 6300 / 6301 / 6302（若未生效则补建）
-- ───────────────────────────────────────────────────────────────────────
INSERT IGNORE INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES (6300, '通联支付配置', '', 2, 30, 5000, 'tlpay', 'ep:money', 'merchant/tlpay/index', 'MerchantTlpayConfig', 0, 1, 1, 1, '1', NOW(), '1', NOW(), b'0');

INSERT IGNORE INTO system_menu (id, name, permission, type, sort, parent_id, path, status, creator, create_time, updater, update_time, deleted)
VALUES (6301, '通联配置 查询', 'merchant:tlpay:query', 3, 1, 6300, '', 0, '1', NOW(), '1', NOW(), b'0');

INSERT IGNORE INTO system_menu (id, name, permission, type, sort, parent_id, path, status, creator, create_time, updater, update_time, deleted)
VALUES (6302, '通联配置 编辑', 'merchant:tlpay:edit', 3, 2, 6300, '', 0, '1', NOW(), '1', NOW(), b'0');

-- ───────────────────────────────────────────────────────────────────────
-- 3. 兜底：V036 SaaS 套餐 6401 / 6402 / 6403（若未生效则补建，直接挂 5000）
-- ───────────────────────────────────────────────────────────────────────
INSERT IGNORE INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES (6401, 'SaaS 套餐配置', '', 2, 40, 5000, 'saas-package', 'ep:list', 'saas/packageConfig/index', 'SaasPackageConfig', 0, 1, 1, 1, '1', NOW(), '1', NOW(), b'0');

INSERT IGNORE INTO system_menu (id, name, permission, type, sort, parent_id, path, status, creator, create_time, updater, update_time, deleted)
VALUES (6402, '套餐配置 查询', 'merchant:saas-package:query', 3, 1, 6401, '', 0, '1', NOW(), '1', NOW(), b'0');

INSERT IGNORE INTO system_menu (id, name, permission, type, sort, parent_id, path, status, creator, create_time, updater, update_time, deleted)
VALUES (6403, '套餐配置 编辑', 'merchant:saas-package:edit', 3, 2, 6401, '', 0, '1', NOW(), '1', NOW(), b'0');

-- ───────────────────────────────────────────────────────────────────────
-- 4. 重映射：已存在的 6300 / 6401 强制挂到 5000 下（覆盖 V032 / V036 的老 parent_id）
--    同时统一 sort + path，让导航顺序合理
-- ───────────────────────────────────────────────────────────────────────
UPDATE system_menu SET parent_id = 5000, sort = 30, path = 'tlpay'        WHERE id = 6300;
UPDATE system_menu SET parent_id = 5000, sort = 40, path = 'saas-package' WHERE id = 6401;

-- ───────────────────────────────────────────────────────────────────────
-- 5. 删除独立的 6400 「SaaS 运营」一级目录（已合并到 5000 商户管理下）
-- ───────────────────────────────────────────────────────────────────────
DELETE FROM system_role_menu WHERE menu_id = 6400;
DELETE FROM system_menu      WHERE id      = 6400;

-- ───────────────────────────────────────────────────────────────────────
-- 6. 兜底：给超管 role_id=1 重新赋予所有相关菜单（INSERT IGNORE 幂等）
-- ───────────────────────────────────────────────────────────────────────
INSERT IGNORE INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted) VALUES
  (1, 5000, '1', NOW(), '1', NOW(), b'0'),
  (1, 6300, '1', NOW(), '1', NOW(), b'0'),
  (1, 6301, '1', NOW(), '1', NOW(), b'0'),
  (1, 6302, '1', NOW(), '1', NOW(), b'0'),
  (1, 6401, '1', NOW(), '1', NOW(), b'0'),
  (1, 6402, '1', NOW(), '1', NOW(), b'0'),
  (1, 6403, '1', NOW(), '1', NOW(), b'0');
