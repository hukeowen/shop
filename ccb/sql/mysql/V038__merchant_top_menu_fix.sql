-- V038 商户管理一级目录修复 — 用空闲 ID 6200（5000 已被「AI 工作流」占用）
--
-- 背景：
--   ai_video_package_menu.sql / V037 假设 5000 是「商户管理」一级目录，但实际生产库
--   5000 已经被 AI 大模型下的「AI 工作流」占据（parent_id=2758, path=workflow）。
--   INSERT IGNORE 全跳过，「商户管理」从未建出来；6300（通联）、6401（SaaS 套餐）、
--   5006（配额流水）都挂错到了「AI 工作流」下。
--
-- 这次修复：
--   1. 用 id=6200 建「商户管理」一级目录（parent_id=0, path=/merchant, icon=ep:shop）
--   2. 把以下菜单全部重定向到 6200 下：
--      · 5006 配额流水          → parent_id=6200, sort=20
--      · 6300 通联支付配置      → parent_id=6200, sort=30
--      · 6401 SaaS 套餐配置     → parent_id=6200, sort=40
--   3. AI 视频套餐主入口 5001 因为 ID 冲突一直没建（5001 是 AI 工作流查询），
--      但 5006 配额流水可以独立挂在「商户管理」下用，已经够用 — 暂不补建套餐 list 入口
--   4. 给 role_id=1 关联
--
-- 幂等：INSERT IGNORE + UPDATE 反复跑 OK

SET NAMES utf8mb4;

-- ─── 1. 建商户管理一级目录（ID=6200，sort=90 排在营销引擎 100 前 + 公众号 100 后）───
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (6200, '商户管理', '', 1, 90, 0, '/merchant', 'ep:shop', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- 强制把 6200 重设到一级目录（防止之前被错误改过）
UPDATE system_menu SET parent_id = 0, sort = 90, path = '/merchant', icon = 'ep:shop',
       name = '商户管理', type = 1, status = 0, visible = b'1', deleted = b'0'
WHERE id = 6200;

-- ─── 2. 重映射子菜单到 6200 下 ─────────────────────────────────────
UPDATE system_menu SET parent_id = 6200, sort = 20 WHERE id = 5006;  -- 配额流水
UPDATE system_menu SET parent_id = 6200, sort = 30 WHERE id = 6300;  -- 通联支付配置
UPDATE system_menu SET parent_id = 6200, sort = 40 WHERE id = 6401;  -- SaaS 套餐配置

-- ─── 3. 兜底 role_id=1 关联（含商户管理一级目录） ──────────────────
INSERT IGNORE INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted) VALUES
  (1, 6200, '1', NOW(), '1', NOW(), b'0'),
  (1, 5006, '1', NOW(), '1', NOW(), b'0'),
  (1, 5007, '1', NOW(), '1', NOW(), b'0'),
  (1, 6300, '1', NOW(), '1', NOW(), b'0'),
  (1, 6301, '1', NOW(), '1', NOW(), b'0'),
  (1, 6302, '1', NOW(), '1', NOW(), b'0'),
  (1, 6401, '1', NOW(), '1', NOW(), b'0'),
  (1, 6402, '1', NOW(), '1', NOW(), b'0'),
  (1, 6403, '1', NOW(), '1', NOW(), b'0');
