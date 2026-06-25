-- =====================================================================
-- V048 服务卡包 / 核销
-- 商家创建商品时可挂若干「服务卡」(子权益项)，用户购买后在该店生成卡实例，
-- 到店出示二维码/数字码，商家核销。时间(有效期)或次数(限次)谁先到都不能再用。
--
-- 例：汽车美容 ¥1288 服务包 → 洗车卡(2年/不限次) + 保养卡(2年/10次)
--
-- 幂等：CREATE TABLE IF NOT EXISTS（全新表，无需存储过程守卫）
-- MySQL 8.0.46 / utf8mb4
-- =====================================================================

-- ---------- 1) 卡定义（挂在 SPU 上，商家建商品时配） ----------
CREATE TABLE IF NOT EXISTS `shop_service_card_def` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
  `spu_id`        BIGINT       NOT NULL                COMMENT '所属商品 SPU ID',
  `name`          VARCHAR(64)  NOT NULL                COMMENT '卡名称，如「洗车卡」「保养卡」',
  `validity_days` INT          NOT NULL DEFAULT 365    COMMENT '有效天数（从用户付款日起算）',
  `max_count`     INT          DEFAULT NULL            COMMENT '可核销次数；NULL=不限次数',
  `description`   VARCHAR(500) DEFAULT ''              COMMENT '卡说明/使用须知',
  `sort`          INT          NOT NULL DEFAULT 0      COMMENT '排序',
  `creator`     VARCHAR(64)  DEFAULT ''            COMMENT '创建者',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`     VARCHAR(64)  DEFAULT ''            COMMENT '更新者',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     BIT(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`   BIGINT       NOT NULL DEFAULT 0    COMMENT '租户ID',
  PRIMARY KEY (`id`),
  KEY `idx_spu` (`tenant_id`, `spu_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '服务卡定义（商品维度，商家配置）';

-- ---------- 2) 用户持有的卡实例（付款后发卡） ----------
CREATE TABLE IF NOT EXISTS `shop_service_card` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
  `def_id`       BIGINT       NOT NULL                COMMENT '卡定义 ID',
  `spu_id`       BIGINT       NOT NULL                COMMENT '商品 SPU ID',
  `order_id`     BIGINT       NOT NULL                COMMENT '来源订单 ID',
  `user_id`      BIGINT       NOT NULL                COMMENT '持卡用户 ID',
  `name`         VARCHAR(64)  NOT NULL                COMMENT '卡名称（下单时快照）',
  `card_no`      VARCHAR(40)  NOT NULL                COMMENT '核销码（全局唯一，二维码/数字码同值）',
  `start_time`   DATETIME     NOT NULL                COMMENT '生效时间（=付款时间）',
  `expire_time`  DATETIME     NOT NULL                COMMENT '到期时间（=付款时间+有效天数）',
  `max_count`    INT          DEFAULT NULL            COMMENT '可核销次数快照；NULL=不限次',
  `used_count`   INT          NOT NULL DEFAULT 0      COMMENT '已核销次数',
  `status`       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE 可用 / USED_UP 次数用尽 / EXPIRED 已过期',
  `creator`     VARCHAR(64)  DEFAULT ''            COMMENT '创建者',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`     VARCHAR(64)  DEFAULT ''            COMMENT '更新者',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     BIT(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`   BIGINT       NOT NULL DEFAULT 0    COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_card_no` (`card_no`),
  KEY `idx_user` (`user_id`, `tenant_id`),
  KEY `idx_order_def` (`order_id`, `def_id`),
  KEY `idx_tenant_status` (`tenant_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户服务卡实例（付款发卡）';

-- ---------- 3) 核销流水 ----------
CREATE TABLE IF NOT EXISTS `shop_service_card_verify` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
  `card_id`      BIGINT       NOT NULL                COMMENT '卡实例 ID',
  `user_id`      BIGINT       NOT NULL                COMMENT '持卡用户 ID',
  `verifier_id`  BIGINT       DEFAULT NULL            COMMENT '核销操作人（商户登录用户）ID',
  `verify_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '核销时间',
  `count_before` INT          DEFAULT NULL            COMMENT '核销前已用次数',
  `count_after`  INT          DEFAULT NULL            COMMENT '核销后已用次数',
  `remark`       VARCHAR(255) DEFAULT ''              COMMENT '核销备注',
  `creator`     VARCHAR(64)  DEFAULT ''            COMMENT '创建者',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`     VARCHAR(64)  DEFAULT ''            COMMENT '更新者',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     BIT(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`   BIGINT       NOT NULL DEFAULT 0    COMMENT '租户ID',
  PRIMARY KEY (`id`),
  KEY `idx_card` (`card_id`),
  KEY `idx_tenant_time` (`tenant_id`, `verify_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '服务卡核销流水';
