-- =============================================================================
-- V043：消费积分抵扣下单（订单结算时用消费积分抵扣商品金额）
--
-- 业务规则（用户在 checkout 中确认）：
--   1) 抵扣比例：1 积分 = consume_point_redeem_ratio 分钱（默认 1.0000，即 1:1）
--      · 商户在「营销配置」可调
--   2) 抵扣上限：≤ 100% 商品总价（订单可全额积分支付）
--   3) 资金优先级：余额(walletBalance) 优先扣 → 然后才用消费积分
--      · 由前端/Checkout 控制顺序，后端只校验「积分扣减额 ≤ remaining」
--   4) 退款 / 取消：原路退回积分（不退现金等价）
--
-- 表设计：
--   - shop_promo_config 加两列：开关 + 比例（每商户一份）
--   - 新表 shop_consume_point_deduct：抵扣记录，按订单维度 (orderId UNIQUE)，
--     PENDING(下单时锁额度) → COMMITTED(afterPayOrder 真正扣 balance + 写流水) → CANCELED(订单取消)
--   - 之所以引入独立表而不是只用 shop_consume_point_record：
--     a) 流水表只能写已发生的扣减（COMMITTED 后），订单未支付前需要预占额度防止重复使用
--     b) 取消订单需要找回这次扣减是否真正提交过，避免误退积分
-- =============================================================================
SET NAMES utf8mb4;

-- 通用列存在性检查
DROP PROCEDURE IF EXISTS v043_add_column;
DELIMITER //
CREATE PROCEDURE v043_add_column(
  IN p_table VARCHAR(64) CHARACTER SET utf8mb4,
  IN p_column VARCHAR(64) CHARACTER SET utf8mb4,
  IN p_def TEXT CHARACTER SET utf8mb4
)
BEGIN
  DECLARE col_count INT DEFAULT 0;
  SELECT COUNT(*) INTO col_count
    FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = p_table
     AND COLUMN_NAME = p_column;
  IF col_count = 0 THEN
    SET @ddl := CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_def);
    PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END//
DELIMITER ;

-- 1. shop_promo_config 加抵扣开关与比例
CALL v043_add_column('shop_promo_config', 'consume_point_redeem_enabled',
  '`consume_point_redeem_enabled` BIT(1) NOT NULL DEFAULT b''0'' COMMENT ''是否启用消费积分抵扣下单'' AFTER `withdraw_threshold`');

CALL v043_add_column('shop_promo_config', 'consume_point_redeem_ratio',
  '`consume_point_redeem_ratio` DECIMAL(8,4) NOT NULL DEFAULT 1.0000 COMMENT ''抵扣比例：1 积分 = X 分钱（默认 1.0000）'' AFTER `consume_point_redeem_enabled`');

DROP PROCEDURE IF EXISTS v043_add_column;

-- 2. shop_consume_point_deduct：订单消费积分抵扣记录
CREATE TABLE IF NOT EXISTS `shop_consume_point_deduct` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `tenant_id`       BIGINT       NOT NULL DEFAULT 0,
  `order_id`        BIGINT       NOT NULL                    COMMENT 'trade_order.id',
  `user_id`         BIGINT       NOT NULL                    COMMENT '抵扣用户',
  `points_used`     BIGINT       NOT NULL                    COMMENT '使用的消费积分数量（分）',
  `ratio_snapshot`  DECIMAL(8,4) NOT NULL                    COMMENT '下单时商户比例快照（1积分=X分钱）',
  `deduct_amount`   BIGINT       NOT NULL                    COMMENT '实际抵扣订单金额（分）= points_used * ratio',
  `status`          VARCHAR(16)  NOT NULL DEFAULT 'PENDING'  COMMENT 'PENDING=下单已写未扣 / COMMITTED=支付后已扣并写流水 / CANCELED=订单取消未扣',
  `commit_time`     DATETIME     DEFAULT NULL                COMMENT '提交扣减时间（afterPayOrder）',
  `cancel_time`     DATETIME     DEFAULT NULL                COMMENT '取消时间（afterCancelOrder）',
  `point_log_id`    BIGINT       DEFAULT NULL                COMMENT 'shop_consume_point_record.id（COMMITTED 后回填）',
  `creator`     VARCHAR(64)  DEFAULT '',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater`     VARCHAR(64)  DEFAULT '',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     BIT(1)       NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order` (`order_id`),
  KEY `idx_tenant_user` (`tenant_id`, `user_id`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单消费积分抵扣记录（PENDING→COMMITTED|CANCELED 状态机）';
