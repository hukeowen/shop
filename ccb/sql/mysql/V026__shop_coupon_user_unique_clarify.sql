-- =============================================================================
-- V026: shop_coupon_user UNIQUE 索引语义明确化
--
-- 现状：UNIQUE (user_id, coupon_id, deleted) — 含 deleted 字段
--   语义：软删后用户可重新领同一张券（因为 (user_id, coupon_id, deleted=b'0')
--   被软删后唯一性已不在 UNIQUE 范围内，新插入 (user_id, coupon_id, deleted=b'0')
--   不冲突）。
--
-- 业务确认：
--   - 商户后台软删一张券（券模板 shop_coupon.deleted=b'1'）后，user 的领取记录
--     仍保留供历史追溯，但用户不会重新看到该券因为 selectEnabledByTenant 已过滤
--   - 用户主动「丢弃」自己的券（未实现）应该走"撤回 → 软删 + 不允许重领"语义
--     —— 此时 (user_id, coupon_id, deleted=b'0') UNIQUE 已生效，软删后再次 INSERT
--     新行（deleted=b'0'）不会被拦
--
-- 决定：保持当前 UNIQUE (user_id, coupon_id, deleted) — 软删后允许重领。
-- 业务上「同一用户同一券限领一张」的约束在 grab() 接口层用 selectByUserIdAndCouponId
-- 幂等检查 + DB UNIQUE 兜底（仅对 deleted=b'0' 的活跃记录）共同保证。
--
-- 本迁移仅写入约束注释，不改 schema。
-- =============================================================================

ALTER TABLE `shop_coupon_user`
  DROP INDEX `uk_user_coupon`,
  ADD UNIQUE KEY `uk_user_coupon` (`user_id`, `coupon_id`, `deleted`)
  COMMENT '同一用户同一券限领一张（软删后可重领；硬约束在 deleted=b''0'' 范围内）';
