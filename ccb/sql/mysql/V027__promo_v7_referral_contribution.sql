-- =============================================================================
-- V027：推 N 反 1 算法 v7 改造
--
-- v6 → v7 核心变化：
--   1. 必须自购才激活资格（首单不返奖；之后自购或下级首单都计 cumulated +1）
--   2. 每个下级对每个上级每个商品只贡献 1 次（首单首次买该商品）
--   3. 推 N 反 1 期内：返「单件实付价 × (1/N)」给上级或自己（按次不按量）
--   4. 完成期（cumulated==N）：返「该商品行实付总额 × 间推百分比」（含自购和下级首单）
--   5. 自然推开关：仅作用于真自然用户（无 parent）；OFF 则吞奖；ON 走旧 A/B 队列
--
-- 兼容性：
--   - shop_queue_position 旧字段 layer/promoted_at 保留不删，但语义废弃；新代码读 state
--   - shop_queue_event 新增事件类型 ACTIVATE / DIRECT_COMMISSION（仅枚举值，DDL 不变）
--
-- 幂等：deploy.sh 不带 --reset 会重复跑，ALTER ADD COLUMN 必须用 PREPARE 包装。
-- MODIFY COLUMN 重复跑无副作用（已是目标尺寸 → noop）；UPDATE state 重复跑也无害。
-- =============================================================================

SET NAMES utf8mb4;

-- ========== 1. shop_promo_config 加 v7 配置字段 ==========
SET @x := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_promo_config'
             AND COLUMN_NAME = 'direct_commission_ratio');
SET @s := IF(@x = 0,
  "ALTER TABLE `shop_promo_config` ADD COLUMN `direct_commission_ratio` DECIMAL(5, 2) DEFAULT NULL COMMENT 'v7 间推百分比（如 10 表示 10%），完成推 N 反 1 后自购 / 下级首单的返奖比例' AFTER `full_cut_amount`",
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @x := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_promo_config'
             AND COLUMN_NAME = 'natural_push_enabled');
SET @s := IF(@x = 0,
  "ALTER TABLE `shop_promo_config` ADD COLUMN `natural_push_enabled` BIT(1) NOT NULL DEFAULT b'0' COMMENT 'v7 自然推开关（OFF=吞奖，ON=保留旧 A/B 层队列）；仅对真自然用户生效' AFTER `direct_commission_ratio`",
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ========== 2. shop_queue_position 加 v7 状态字段 ==========
-- v6 的 layer A/B + promoted_at 已不再使用；保留字段不删（避免 DROP 影响 DO 反序列化）。
-- 新算法只读 state 列：
--   NEW：从未自购过该商品（理论上不写到 DB，激活时直接 INSERT IN_PROGRESS）
--   IN_PROGRESS：已激活，cumulated < N
--   COMPLETED：cumulated == N，永久终态
SET @x := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_queue_position'
             AND COLUMN_NAME = 'state');
SET @s := IF(@x = 0,
  "ALTER TABLE `shop_queue_position` ADD COLUMN `state` VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS' COMMENT 'v7 状态机：NEW / IN_PROGRESS / COMPLETED' AFTER `accumulated_amount`",
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 历史数据迁移：QUEUEING → IN_PROGRESS；EXITED → COMPLETED
-- 重复跑：state 已经是 IN_PROGRESS / COMPLETED，重新计算还是同值 → 幂等无副作用
UPDATE `shop_queue_position`
   SET `state` = CASE WHEN `status` = 'EXITED' THEN 'COMPLETED' ELSE 'IN_PROGRESS' END;

-- ========== 3. 新建 shop_referral_contribution ==========
-- 记录某下级对某上级在某商品上是否已贡献过首单
-- v7 核心约束：每个 (parent, child, spu) 只能存在 1 条；child 后续对该 spu 的订单永远不再触发 parent 奖励
CREATE TABLE IF NOT EXISTS `shop_referral_contribution` (
  `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`        BIGINT      NOT NULL DEFAULT 0      COMMENT '租户 ID',

  `parent_user_id`   BIGINT      NOT NULL                COMMENT '上级用户 ID',
  `child_user_id`    BIGINT      NOT NULL                COMMENT '下级用户 ID',
  `spu_id`           BIGINT      NOT NULL                COMMENT '商品 SPU ID',

  -- 触发时的状态快照（审计 / 排查用）
  `parent_state_at`  VARCHAR(16) NOT NULL                COMMENT '触发时 parent 在该商品上的状态：IN_PROGRESS / COMPLETED',
  `award_amount`     BIGINT      NOT NULL DEFAULT 0      COMMENT '本次给 parent 的推广积分数（分）',
  `source_order_id`  BIGINT      NOT NULL                COMMENT '触发首贡献的订单 ID',

  `creator`          VARCHAR(64)          DEFAULT ''     COMMENT '创建者',
  `create_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater`          VARCHAR(64)          DEFAULT ''     COMMENT '更新者',
  `update_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`          BIT(1)      NOT NULL DEFAULT b'0'   COMMENT '是否删除',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_parent_child_spu` (`parent_user_id`, `child_user_id`, `spu_id`, `deleted`)
    COMMENT '同一对(上级,下级)在同一商品上仅允许 1 条贡献记录',
  KEY `idx_tenant_parent` (`tenant_id`, `parent_user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'v7 推 N 反 1：下级首贡献记录（防同一对重复触发）';

-- ========== 4. 扩相关列长度 ==========
-- v7 新事件 REFERRAL_COMMISSION (19) / REFERRAL_PROGRESS (17) / SELF_COMMISSION (15) 超过原 varchar(16)
-- MODIFY 重复跑：列已是 VARCHAR(32) → MySQL 还是发 ALTER 但是 noop，不报错 → 幂等
ALTER TABLE `shop_promo_record` MODIFY COLUMN `source_type` VARCHAR(32) NOT NULL COMMENT '来源类型枚举值';
ALTER TABLE `shop_consume_point_record` MODIFY COLUMN `source_type` VARCHAR(32) NOT NULL COMMENT '来源类型枚举值';
ALTER TABLE `shop_queue_event` MODIFY COLUMN `event_type` VARCHAR(32) NOT NULL COMMENT '事件类型枚举值';
