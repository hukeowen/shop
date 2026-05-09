-- =============================================================================
-- V029：trade_cart 去重 + 加 UNIQUE
--
-- 问题：
--   trade_cart 老 schema 只有 PRIMARY KEY(id)，没 UNIQUE。
--   addCart 是 select-then-insert 两阶段：
--     先 selectByUserIdAndSkuId 找已有，找到就 UPDATE count，找不到就 INSERT。
--   并发场景下两次 select 同时返 null → 两次 INSERT 都成功 → 同 (user, sku)
--   出现多行，购物车列表里看到同商品两条记录、件数对不上。
--
-- 修复：
--   1. dedup 现有重复行：合并 count 到 id 最小那行，删除后续行
--   2. 加 UNIQUE (tenant_id, user_id, sku_id, deleted) 杜绝再发生
--      注意要含 deleted：MyBatis Plus 软删除把 deleted=1 行留在表里，
--      不带 deleted 的 UNIQUE 会让"先加→删→再加"撞 UNIQUE 失败
-- =============================================================================

SET NAMES utf8mb4;

-- 1. 先找出"重复 (tenant, user, sku) 中 id 不是最小"的行的 id 列表
DROP TEMPORARY TABLE IF EXISTS _v029_dup_cart;
CREATE TEMPORARY TABLE _v029_dup_cart AS
SELECT t.id AS keep_id, t.sum_count
  FROM (
    SELECT MIN(id) AS id, SUM(count) AS sum_count
      FROM trade_cart
     WHERE deleted = 0
     GROUP BY tenant_id, user_id, sku_id
    HAVING COUNT(*) > 1
  ) t;

-- 2. 把重复组的 sum_count 写回 keep_id 那行
UPDATE trade_cart c
  JOIN _v029_dup_cart d ON c.id = d.keep_id
   SET c.count = d.sum_count, c.update_time = NOW()
 WHERE c.deleted = 0;

-- 3. 软删除其余重复行（保留 id 最小那行）
UPDATE trade_cart c
  JOIN (
    SELECT MIN(id) AS keep_id, tenant_id, user_id, sku_id
      FROM trade_cart
     WHERE deleted = 0
     GROUP BY tenant_id, user_id, sku_id
    HAVING COUNT(*) > 1
  ) k ON c.tenant_id = k.tenant_id AND c.user_id = k.user_id AND c.sku_id = k.sku_id
   AND c.id <> k.keep_id
   SET c.deleted = 1, c.update_time = NOW()
 WHERE c.deleted = 0;

DROP TEMPORARY TABLE IF EXISTS _v029_dup_cart;

-- 4. 加 UNIQUE（幂等：已存在则跳过）
SET @x := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_cart'
             AND INDEX_NAME = 'uk_tenant_user_sku');
SET @s := IF(@x = 0,
  'ALTER TABLE trade_cart ADD UNIQUE KEY uk_tenant_user_sku (tenant_id, user_id, sku_id, deleted)',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
