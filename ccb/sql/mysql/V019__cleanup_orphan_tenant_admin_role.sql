-- =============================================================================
-- V019: 清理孤儿"租户管理员"角色（修申请商户失败错 1002002001）
--
-- 背景：yudao 的 TenantServiceImpl.createTenant 用 @DSTransactional，与外层 controller
-- 的 @Transactional 不嵌套。当 applyMerchantBySms 流程中途某一步失败时，外层事务回滚
-- 不会带着 createTenant 内部已提交的 system_tenant + system_role 一起回滚，结果留下：
--   1) system_tenant 行 contact_user_id IS NULL（createUser 没成功）
--   2) system_role(name='租户管理员', tenant_id=该残留 tenant id) 行
-- 下一次 createTenant 用新 tenant_id 时本不应撞 — 但 yudao 内部 selectByName 在
-- 极少数边界场景下会扫到这些残留并触发 ROLE_NAME_DUPLICATE。
--
-- 本迁移做两件事（都幂等）：
--   1. 删除"未挂 contact_user_id 的孤儿 system_tenant"（演示残留）
--   2. 删除 tenant_id 已不指向有效 system_tenant 行的"租户管理员"角色
-- 真实业务数据（已开通商户的租户/角色）不受影响 —— 它们 contact_user_id 一定非空。
--
-- 注意：这是 **物理删除**，不是软删。审计场景请改成 UPDATE deleted=b'1'。
-- =============================================================================

-- 1. 清掉残留孤儿 tenant（contact_user_id IS NULL 的部分初始化失败 tenant）
--    保留 yudao 默认的"芋道源码"主租户（id=1，contact_user_id=1）和已激活租户
DELETE FROM `system_tenant`
WHERE deleted = b'0'
  AND id != 1
  AND contact_user_id IS NULL;

-- 2. 清掉指向不存在 tenant 的"租户管理员" role（含上方刚删的孤儿）
DELETE FROM `system_role`
WHERE name = '租户管理员'
  AND deleted = b'0'
  AND tenant_id NOT IN (SELECT id FROM `system_tenant` WHERE deleted = b'0');

-- 3. 清掉指向不存在 tenant 的孤儿 system_users（避免下一次 createUser 撞 username 重复）
--    yudao TenantServiceImpl.createUser 用 mobile 作为 username，残留 user 行会让
--    新申请同手机号时撞 USER_USERNAME_EXISTS（虽不报本次错，但同样阻塞流程）
DELETE FROM `system_users`
WHERE deleted = b'0'
  AND tenant_id NOT IN (SELECT id FROM `system_tenant` WHERE deleted = b'0');

-- 4. 清掉孤儿 system_user_role（关联表）
DELETE FROM `system_user_role`
WHERE deleted = b'0'
  AND user_id NOT IN (SELECT id FROM `system_users` WHERE deleted = b'0');
