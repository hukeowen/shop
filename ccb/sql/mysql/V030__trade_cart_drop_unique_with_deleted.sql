-- =============================================================================
-- V030：撤 V029 加的 UNIQUE — 跟 yudao 软删冲突
--
-- V029 加了 UNIQUE (tenant_id, user_id, sku_id, deleted) 防 cart 双行。
-- 但实际场景：
--   1. user 加购 → cart A deleted=0
--   2. trade 下单成功 → 软删 cart A → cart A deleted=1
--   3. user 再加 → cart B deleted=0（不撞 A 因 deleted 不同）
--   4. trade 又下单 → 软删 cart B → cart B 设 deleted=1 →
--      撞 cart A 的 (tenant,user,sku,deleted=1) → SQLIntegrityConstraintViolation
--   → 第二次下单后立即崩
--
-- mysql 5.7 不支持 partial index "WHERE deleted=0"，functional index 也不支持。
-- 只能放弃 UNIQUE，依赖应用层 select-then-insert + catch DuplicateKey 兜底。
-- 加多线程并发 race 防护退化为依赖 yudao trade 模块层面的乐观行为，但
-- yudao 实测下来没出现过 race 双行（之前的 9+15 双行是因为某种登录串号
-- 导致用户 token 切了 tenantId 后两次 add 走了不同路径——本仓库已修）。
--
-- 修：
--   1. hard delete 所有 deleted=1 历史行（避免再撞 UNIQUE）
--   2. DROP UNIQUE uk_tenant_user_sku
--   3. 不再加 UNIQUE — 应用层 + 用户级 race 几率极低，可接受
-- =============================================================================

SET NAMES utf8mb4;

-- 1. 清理软删行，避免撞 UNIQUE
DELETE FROM trade_cart WHERE deleted = b'1';

-- 2. DROP V029 加的 UNIQUE（幂等：检查存在）
SET @x := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_cart'
             AND INDEX_NAME = 'uk_tenant_user_sku');
SET @s := IF(@x > 0, 'ALTER TABLE trade_cart DROP INDEX uk_tenant_user_sku', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
