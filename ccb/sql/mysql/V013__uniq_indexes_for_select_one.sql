-- description: 给 selectOne 真正依赖唯一性的列补 UNIQUE 索引，防 TooManyResults 重演
-- author: huliang
-- date: 2026-04-30
--
-- 背景：code-review 发现两处 mapper 用 selectOne，但 schema 只是普通 KEY：
--   1) shop_info.tl_open_order_id —— 通联进件失败重试可能产生重复 outOrderId
--   2) merchant_referral.referee_tenant_id —— 重复 recordReferral 会插多条
-- 同时 merchant_apply.mobile 在历史脏数据下也会有多条 (status=2 reject + 重新 submit)
-- 修法：先去重保留 id 最小的活记录，再加 UNIQUE 索引。

-- ============== 1. shop_info.tl_open_order_id ==============
DROP PROCEDURE IF EXISTS `add_uk_shop_info_tl_open_order_id`;
DELIMITER $$
CREATE PROCEDURE `add_uk_shop_info_tl_open_order_id`()
BEGIN
    -- 1.1 软删除重复的非空 tl_open_order_id（保留 id 最小那条）
    UPDATE `shop_info` t1
    JOIN (
        SELECT tl_open_order_id, MIN(id) AS keep_id
        FROM `shop_info`
        WHERE tl_open_order_id IS NOT NULL AND tl_open_order_id != '' AND deleted = 0
        GROUP BY tl_open_order_id
        HAVING COUNT(*) > 1
    ) t2 ON t1.tl_open_order_id = t2.tl_open_order_id AND t1.id != t2.keep_id
    SET t1.deleted = 1;

    -- 1.2 升原 idx_tl_open_order_id 为 UNIQUE
    IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_info'
                  AND INDEX_NAME = 'idx_tl_open_order_id') THEN
        ALTER TABLE `shop_info` DROP INDEX `idx_tl_open_order_id`;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_info'
                      AND INDEX_NAME = 'uk_tl_open_order_active') THEN
        -- 复合 (tl_open_order_id, deleted) 让软删除后空位可重用
        ALTER TABLE `shop_info` ADD UNIQUE KEY `uk_tl_open_order_active` (`tl_open_order_id`, `deleted`);
    END IF;
END$$
DELIMITER ;
CALL `add_uk_shop_info_tl_open_order_id`();
DROP PROCEDURE `add_uk_shop_info_tl_open_order_id`;

-- ============== 2. merchant_referral.referee_tenant_id ==============
DROP PROCEDURE IF EXISTS `add_uk_merchant_referral_referee`;
DELIMITER $$
CREATE PROCEDURE `add_uk_merchant_referral_referee`()
BEGIN
    -- 2.1 软删除重复 referee_tenant_id（保留 id 最小）
    UPDATE `merchant_referral` t1
    JOIN (
        SELECT referee_tenant_id, MIN(id) AS keep_id
        FROM `merchant_referral`
        WHERE referee_tenant_id IS NOT NULL AND deleted = 0
        GROUP BY referee_tenant_id
        HAVING COUNT(*) > 1
    ) t2 ON t1.referee_tenant_id = t2.referee_tenant_id AND t1.id != t2.keep_id
    SET t1.deleted = 1;

    -- 2.2 加 UNIQUE
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'merchant_referral'
                      AND INDEX_NAME = 'uk_referee_tenant_active') THEN
        ALTER TABLE `merchant_referral` ADD UNIQUE KEY `uk_referee_tenant_active` (`referee_tenant_id`, `deleted`);
    END IF;
END$$
DELIMITER ;
CALL `add_uk_merchant_referral_referee`();
DROP PROCEDURE `add_uk_merchant_referral_referee`;

-- ============== 3. merchant_apply.mobile —— 同手机号同状态去重 ==============
-- 业务允许同手机号多条申请记录（驳回后可重新提交），所以这里只清"同 mobile + 同 status=0 待审核"重复
-- status 终态 (1/2) 历史保留，不动
DROP PROCEDURE IF EXISTS `dedupe_merchant_apply_pending`;
DELIMITER $$
CREATE PROCEDURE `dedupe_merchant_apply_pending`()
BEGIN
    UPDATE `merchant_apply` t1
    JOIN (
        SELECT mobile, MIN(id) AS keep_id
        FROM `merchant_apply`
        WHERE mobile IS NOT NULL AND mobile != '' AND status = 0 AND deleted = 0
        GROUP BY mobile
        HAVING COUNT(*) > 1
    ) t2 ON t1.mobile = t2.mobile AND t1.status = 0 AND t1.id != t2.keep_id
    SET t1.deleted = 1;
END$$
DELIMITER ;
CALL `dedupe_merchant_apply_pending`();
DROP PROCEDURE `dedupe_merchant_apply_pending`;
