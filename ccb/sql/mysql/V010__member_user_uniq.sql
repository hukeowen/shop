-- description: 清理 member_user 重复 mobile + 加唯一索引；防御 selectOne 撞 TooManyResults
-- author: huliang
-- date: 2026-04-30
--
-- 背景：之前测试时同一个手机号被注册了多次（password-login + apply-merchant-by-sms 等），
--       member_user.mobile 列没唯一索引 → selectByMobile 撞 TooManyResultsException。
-- 修法：保留每个手机号 id 最小的一条（最早注册），其余软删；然后给 mobile 加唯一索引。

-- 1. 软删除 mobile 重复的多余行（保留 id 最小那条）
UPDATE `member_user` t1
JOIN (
    SELECT mobile, MIN(id) AS keep_id
    FROM `member_user`
    WHERE mobile IS NOT NULL AND mobile != '' AND deleted = 0
    GROUP BY mobile
    HAVING COUNT(*) > 1
) t2 ON t1.mobile = t2.mobile AND t1.id != t2.keep_id
SET t1.deleted = 1;

-- 2. 同样清 merchant_info.open_id 重复行
UPDATE `merchant_info` t1
JOIN (
    SELECT open_id, MIN(id) AS keep_id
    FROM `merchant_info`
    WHERE open_id IS NOT NULL AND open_id != '' AND deleted = 0
    GROUP BY open_id
    HAVING COUNT(*) > 1
) t2 ON t1.open_id = t2.open_id AND t1.id != t2.keep_id
SET t1.deleted = 1;

-- 3. 加唯一索引（限定 deleted=0 的行；MySQL 不直接支持 partial index，用普通唯一索引但仅生效于活记录）
-- 注意：mobile NULL/空字符串不参与唯一约束（MySQL 标准行为）
DROP PROCEDURE IF EXISTS `add_idx_member_user_mobile`;
DELIMITER $$
CREATE PROCEDURE `add_idx_member_user_mobile`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member_user'
                      AND INDEX_NAME = 'uk_mobile_active') THEN
        -- 用 mobile + deleted 复合：deleted=0 时 mobile 唯一，deleted=1 后可重用
        ALTER TABLE `member_user` ADD UNIQUE KEY `uk_mobile_active` (`mobile`, `deleted`);
    END IF;
END$$
DELIMITER ;
CALL `add_idx_member_user_mobile`();
DROP PROCEDURE `add_idx_member_user_mobile`;

DROP PROCEDURE IF EXISTS `add_idx_merchant_open_id`;
DELIMITER $$
CREATE PROCEDURE `add_idx_merchant_open_id`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'merchant_info'
                      AND INDEX_NAME = 'uk_open_id_active') THEN
        ALTER TABLE `merchant_info` ADD UNIQUE KEY `uk_open_id_active` (`open_id`, `deleted`);
    END IF;
END$$
DELIMITER ;
CALL `add_idx_merchant_open_id`();
DROP PROCEDURE `add_idx_merchant_open_id`;
