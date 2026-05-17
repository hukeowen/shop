-- =============================================================================
-- V041：v8 星级奖池「分配规则 + 手工结算」
--
-- 新增能力（详见 docs/design/marketing-system-v8.md 第八节延伸）：
--   1. 每个 SPU 配一组「按星级分配规则」JSON：sum(ratio)=100，每星可选 EQUAL/LOTTERY
--   2. 商户手工触发结算 → 按规则把 spu_star_pool.pool_balance 全部分发到星级用户的推广积分
--   3. 落两张审计表：结算单 + 中奖明细，可回溯每次"分了多少给谁"
--
-- 路径：merchant 商品配置页配规则 → 列表点"立即结算" → MerchantSpuPoolSettleService 事务执行
-- =============================================================================
SET NAMES utf8mb4;

-- 通用列存在性检查（同 V028 风格，可独立调用避免跨文件依赖）
DROP PROCEDURE IF EXISTS v041_add_column;
DELIMITER //
CREATE PROCEDURE v041_add_column(
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

-- 1. product_promo_config 加分配规则列
--    JSON 形态：[{"star":5,"ratio":30,"mode":"EQUAL"},{"star":4,"ratio":20,"mode":"LOTTERY","winners":10},...]
--    约束在 Service 层校验：
--      - 0 < star <= product_promo_config.star_count
--      - sum(ratio) = 100
--      - mode in (EQUAL, LOTTERY)；LOTTERY 必须有 winners >= 1
CALL v041_add_column('product_promo_config', 'pool_dist_rules',
  '`pool_dist_rules` TEXT DEFAULT NULL COMMENT ''奖池分配规则 JSON [{star,ratio,mode:EQUAL|LOTTERY,winners?}]，sum(ratio)=100'' AFTER `pool_ratio`');

DROP PROCEDURE IF EXISTS v041_add_column;

-- 2. spu_star_pool_settle_record：结算单（每次手工结算一行）
CREATE TABLE IF NOT EXISTS `spu_star_pool_settle_record` (
  `id`                   BIGINT      NOT NULL AUTO_INCREMENT,
  `tenant_id`            BIGINT      NOT NULL DEFAULT 0,
  `spu_id`               BIGINT      NOT NULL,
  `pool_balance_before`  BIGINT      NOT NULL                COMMENT '结算前池余额（分）',
  `pool_balance_after`   BIGINT      NOT NULL                COMMENT '结算后池余额（分）— 一般 = 0，无人可分时残值留池',
  `total_distributed`    BIGINT      NOT NULL                COMMENT '实际分配总额（分）= sum(payout_item.amount)',
  `rules_snapshot`       TEXT                                COMMENT '结算时的规则 JSON 快照（防规则被改后无法回溯）',
  `random_seed`          BIGINT      NOT NULL DEFAULT 0      COMMENT '抽奖随机种子（可复核），无抽奖时 = 0',
  `operator_id`          BIGINT                              COMMENT '操作人（管理员 user_id）',
  `operator_name`        VARCHAR(64) DEFAULT ''              COMMENT '操作人姓名（冗余便于审计）',
  `remark`               VARCHAR(255) DEFAULT ''             COMMENT '备注/触发原因',
  `creator`              VARCHAR(64) DEFAULT '',
  `create_time`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater`              VARCHAR(64) DEFAULT '',
  `update_time`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`              BIT(1)      NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_spu_time` (`tenant_id`, `spu_id`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'v8 SPU 级星级奖池结算单（每次手工结算一行）';

-- 3. spu_star_pool_payout_item：结算明细（每个中奖/均分用户一行）
CREATE TABLE IF NOT EXISTS `spu_star_pool_payout_item` (
  `id`                BIGINT      NOT NULL AUTO_INCREMENT,
  `tenant_id`         BIGINT      NOT NULL DEFAULT 0,
  `settle_id`         BIGINT      NOT NULL                   COMMENT '结算单 id（外键，事务内写）',
  `spu_id`            BIGINT      NOT NULL,
  `user_id`           BIGINT      NOT NULL,
  `star`              INT         NOT NULL                   COMMENT '结算时该用户在该 SPU 上的星级',
  `mode`              VARCHAR(16) NOT NULL                   COMMENT 'EQUAL=均分 / LOTTERY=抽中',
  `amount`            BIGINT      NOT NULL                   COMMENT '该用户分到的推广积分（分）',
  `point_ledger_id`   BIGINT      DEFAULT 0                  COMMENT 'shop_user_point_log.id（回查积分流水用，0=未记或失败）',
  `creator`           VARCHAR(64) DEFAULT '',
  `create_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater`           VARCHAR(64) DEFAULT '',
  `update_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`           BIT(1)      NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_settle` (`settle_id`),
  KEY `idx_tenant_user_spu` (`tenant_id`, `user_id`, `spu_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'v8 SPU 级星级奖池结算明细（每中奖/均分用户一行）';
