-- description: 清理 system_role 重复（同 tenant_id + name + deleted=0）+ 加唯一索引
-- author: huliang
-- date: 2026-04-30
--
-- 背景：yudao 上游 createTenant 内部会创建 admin 角色（默认 name='管理员'），
--       多次申请商户后同 (tenant_id, name) 出现 2+ 条 → RoleServiceImpl.
--       validateRoleDuplicate 调 selectByName 撞 TooManyResultsException →
--       商户申请失败。
-- 修法：dedup（保留 id 最小那条，其余软删）+ 加 (tenant_id, name, deleted) 复合唯一索引

-- 1. 软删除同 (tenant_id, name) 重复的多余行
UPDATE `system_role` t1
JOIN (
    SELECT tenant_id, name, MIN(id) AS keep_id
    FROM `system_role`
    WHERE deleted = 0
    GROUP BY tenant_id, name
    HAVING COUNT(*) > 1
) t2 ON t1.tenant_id = t2.tenant_id AND t1.name = t2.name AND t1.id != t2.keep_id
SET t1.deleted = 1;

-- 2. 同样清 system_role.code 重复（yudao 也会按 code 校验）
UPDATE `system_role` t1
JOIN (
    SELECT tenant_id, code, MIN(id) AS keep_id
    FROM `system_role`
    WHERE deleted = 0 AND code IS NOT NULL AND code != ''
    GROUP BY tenant_id, code
    HAVING COUNT(*) > 1
) t2 ON t1.tenant_id = t2.tenant_id AND t1.code = t2.code AND t1.id != t2.keep_id
SET t1.deleted = 1;

-- 3. 加唯一索引（防未来再次累积重复）
DROP PROCEDURE IF EXISTS `add_idx_role_uniq`;
DELIMITER $$
CREATE PROCEDURE `add_idx_role_uniq`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_role'
                      AND INDEX_NAME = 'uk_tenant_name_active') THEN
        ALTER TABLE `system_role` ADD UNIQUE KEY `uk_tenant_name_active` (`tenant_id`, `name`, `deleted`);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_role'
                      AND INDEX_NAME = 'uk_tenant_code_active') THEN
        ALTER TABLE `system_role` ADD UNIQUE KEY `uk_tenant_code_active` (`tenant_id`, `code`, `deleted`);
    END IF;
END$$
DELIMITER ;
CALL `add_idx_role_uniq`();
DROP PROCEDURE `add_idx_role_uniq`;
