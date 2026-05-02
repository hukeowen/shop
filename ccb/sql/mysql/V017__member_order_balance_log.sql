-- ===== V017: 订单余额抵扣日志（幂等用） =====
-- 用户在 checkout 时勾选"店铺余额抵扣"，每个订单只能扣一次余额。
-- 唯一键 (user_id, tenant_id, order_id) 保证幂等。

CREATE TABLE IF NOT EXISTS `member_order_balance_log` (
    `id`         BIGINT  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT  NOT NULL COMMENT '用户 ID',
    `tenant_id`  BIGINT  NOT NULL COMMENT '店铺 tenantId（资产隔离）',
    `order_id`   BIGINT  NOT NULL COMMENT '订单 ID（唯一）',
    `amount`     INT     NOT NULL COMMENT '抵扣金额（分）',

    `creator`     VARCHAR(64) NULL,
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`     VARCHAR(64) NULL,
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     BIT(1)      NOT NULL DEFAULT b'0',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_tenant_order` (`user_id`, `tenant_id`, `order_id`),
    KEY `idx_order_id` (`order_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '用户订单余额抵扣日志（幂等）';
