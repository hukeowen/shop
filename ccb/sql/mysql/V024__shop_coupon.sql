-- =============================================================================
-- V024: 优惠券系统（商户自建模板 + 用户领取记录）
--
-- 设计原则：
--   1. 商户在 me/coupon 页自建券模板（满减券为主，未来扩展折扣券）
--   2. 一个商户=一个租户，shop_coupon 是 TenantBaseDO；自动按 tenant 隔离
--   3. 用户领取后写 shop_coupon_user，与用户 + 券模板 + 状态绑定
--   4. 仅做文案展示 + 领取记录，不参与结算（结算逻辑在后续迭代）
-- =============================================================================

-- ========== 券模板表 ==========
CREATE TABLE IF NOT EXISTS `shop_coupon` (
  `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '券模板 ID',
  `tenant_id`        BIGINT      NOT NULL DEFAULT 0      COMMENT '租户 ID（一商户一租户）',

  -- 内容
  `name`             VARCHAR(64) NOT NULL                COMMENT '券名（如：满 50 减 5）',
  `discount_amount`  INT         NOT NULL                COMMENT '券面额（分），满 50 减 5 → 500',
  `min_amount`       INT         NOT NULL DEFAULT 0      COMMENT '使用门槛（分），0=无门槛',
  `tag`              VARCHAR(16)          DEFAULT NULL   COMMENT '标签：NEW=新人专享 / NORMAL=通用，空=通用',

  -- 库存
  `total_count`      INT         NOT NULL DEFAULT 0      COMMENT '发行总量，0=不限',
  `taken_count`      INT         NOT NULL DEFAULT 0      COMMENT '已领数量',

  -- 有效期（领后多少天有效；0=与券模板共生命周期）
  `valid_days`       INT         NOT NULL DEFAULT 30     COMMENT '领取后有效天数',
  `start_time`       DATETIME             DEFAULT NULL   COMMENT '券生效开始时间，NULL=立即',
  `end_time`         DATETIME             DEFAULT NULL   COMMENT '券失效时间，NULL=永久',

  -- 状态
  `status`           TINYINT     NOT NULL DEFAULT 0      COMMENT '0=上架 1=下架',

  -- 公共
  `creator`          VARCHAR(64)          DEFAULT ''     COMMENT '创建者',
  `create_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater`          VARCHAR(64)          DEFAULT ''     COMMENT '更新者',
  `update_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`          BIT(1)      NOT NULL DEFAULT b'0'   COMMENT '是否删除',

  PRIMARY KEY (`id`),
  KEY `idx_tenant_status` (`tenant_id`, `status`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '店铺优惠券模板';


-- ========== 用户领取记录表 ==========
CREATE TABLE IF NOT EXISTS `shop_coupon_user` (
  `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '记录 ID',
  `tenant_id`        BIGINT      NOT NULL DEFAULT 0      COMMENT '租户 ID（与券模板一致）',

  `coupon_id`        BIGINT      NOT NULL                COMMENT '券模板 ID',
  `user_id`          BIGINT      NOT NULL                COMMENT '领券用户 ID',

  -- 快照（领取时定格，避免商户改券面额导致历史不一致）
  `discount_amount`  INT         NOT NULL                COMMENT '面额（分），来自模板快照',
  `min_amount`       INT         NOT NULL                COMMENT '门槛（分）',

  -- 时效
  `effective_time`   DATETIME    NOT NULL                COMMENT '生效时间',
  `expire_time`      DATETIME    NOT NULL                COMMENT '过期时间',

  -- 状态
  `status`           TINYINT     NOT NULL DEFAULT 0      COMMENT '0=未使用 1=已使用 2=已过期',
  `use_time`         DATETIME             DEFAULT NULL   COMMENT '使用时间',
  `order_id`         BIGINT               DEFAULT NULL   COMMENT '使用时关联的订单 ID',

  -- 公共
  `creator`          VARCHAR(64)          DEFAULT ''     COMMENT '创建者',
  `create_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater`          VARCHAR(64)          DEFAULT ''     COMMENT '更新者',
  `update_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`          BIT(1)      NOT NULL DEFAULT b'0'   COMMENT '是否删除',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_coupon` (`user_id`, `coupon_id`, `deleted`) COMMENT '同一用户同一券限领一张',
  KEY `idx_tenant_user` (`tenant_id`, `user_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户领取的优惠券记录';
