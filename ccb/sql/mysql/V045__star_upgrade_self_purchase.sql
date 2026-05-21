-- V045: 升星新增 OR 分支 — 自购累计金额达标也升
--
-- 用户原话: 营销配置里设置「单独购买大于 X 元 即升 N 星」，与原有「直推 +
-- 团队累计」是 OR 关系，任一满足即升。
--
-- 改动：
--   1. shop_user_star 新加 self_purchase_amount 列（按 (user, spu) 累加 buyer 自己下单实付）
--   2. product_promo_config.star_upgrade_rules JSON 每条 rule 加可选字段
--      "selfPurchaseAmount": 100000  // 单位分；0 / 不传 = 不启用此分支
--   3. 升星判定：(direct_count + team_sales 都达标) OR (self_purchase >= rule.selfPurchaseAmount)
--      仅在 selfPurchaseAmount > 0 时 OR 分支才参与判定

ALTER TABLE shop_user_star
  ADD COLUMN IF NOT EXISTS self_purchase_amount BIGINT NOT NULL DEFAULT 0
  COMMENT '用户在该 SPU 上自己下单实付累计（分）— v8.1 升星 OR 分支用';
