-- =============================================================================
-- 摊小二 营销体系 v6 数据表（11 张）
-- 对应 docs/design/marketing-system-v6.md
-- 双积分（消费/推广）+ 直推/插队/自然推队列 + 团队极差递减 + 星级评定 + 积分池
--
-- 开发期 DDL：直接全量建表，不做 ALTER 增量。重建库不心疼。
-- =============================================================================

-- ----------------------------
-- 1. shop_promo_config  商户级营销配置（每商户一条）
-- ----------------------------
DROP TABLE IF EXISTS `shop_promo_config`;
CREATE TABLE `shop_promo_config` (
  `id`                       BIGINT         NOT NULL AUTO_INCREMENT COMMENT '编号',
  -- 平台星级
  `star_level_count`         TINYINT        NOT NULL DEFAULT 5      COMMENT '平台星级数量',
  `commission_rates`         VARCHAR(256)   NOT NULL DEFAULT '[1,2,3,4,5]'
                                                                    COMMENT '每星级团队极差抽成比例(%) JSON 数组，与 star_level_count 同长',
  `star_upgrade_rules`       VARCHAR(1024)  NOT NULL DEFAULT '[{"directCount":2,"teamSales":3},{"directCount":3,"teamSales":9},{"directCount":5,"teamSales":27},{"directCount":8,"teamSales":81},{"directCount":12,"teamSales":243}]'
                                                                    COMMENT '升星门槛 JSON 数组，每星一组(直推数+团队链路销售份数)',
  -- 推广积分提现 / 转换
  `point_conversion_ratio`   DECIMAL(5, 2)  NOT NULL DEFAULT 1.00   COMMENT '推广积分→消费积分 转换比例（默认 1:1）',
  `withdraw_threshold`       INT            NOT NULL DEFAULT 10000  COMMENT '推广积分提现门槛(分，默认 100 元)',
  -- 星级积分池
  `pool_enabled`             BIT(1)         NOT NULL DEFAULT b'0'   COMMENT '是否启用星级积分池',
  `pool_ratio`               DECIMAL(5, 2)  NOT NULL DEFAULT 0.00   COMMENT '入池比例(%)',
  `pool_eligible_stars`      VARCHAR(64)    NOT NULL DEFAULT '[]'   COMMENT '可参与瓜分的星级 JSON 数组 [1,2,3,4,5]',
  `pool_distribute_mode`     VARCHAR(16)    NOT NULL DEFAULT 'ALL'  COMMENT '分配方式：ALL=全员均分 / STAR=按星级均分',
  `pool_settle_cron`         VARCHAR(64)    NOT NULL DEFAULT '0 0 0 1 * ?'
                                                                    COMMENT '结算 cron 表达式（默认每月 1 号 0 点）',
  `pool_lottery_ratio`       DECIMAL(5, 2)  NOT NULL DEFAULT 5.00   COMMENT '抽奖中奖占比(%)',
  `pool_settle_mode`         VARCHAR(16)    NOT NULL DEFAULT 'FULL' COMMENT 'cron 自动结算模式：FULL=全员均分 / LOTTERY=抽奖；商户后台手动触发不受此限制',
  -- 标准字段
  `creator`     VARCHAR(64)  DEFAULT ''                COMMENT '创建者',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                                       COMMENT '创建时间',
  `updater`     VARCHAR(64)  DEFAULT ''                COMMENT '更新者',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                                                       COMMENT '更新时间',
  `deleted`     BIT(1)       NOT NULL DEFAULT b'0'     COMMENT '是否删除',
  `tenant_id`   BIGINT       NOT NULL DEFAULT 0        COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商户营销配置（每商户一条）';

-- ----------------------------
-- 2. product_promo_config  商品级营销配置（每商品一条）
-- ----------------------------
DROP TABLE IF EXISTS `product_promo_config`;
CREATE TABLE `product_promo_config` (
  `id`                  BIGINT        NOT NULL AUTO_INCREMENT      COMMENT '编号',
  `spu_id`              BIGINT        NOT NULL                     COMMENT '商品 SPU ID',
  -- 消费积分
  `consume_point_ratio` DECIMAL(5, 2) NOT NULL DEFAULT 0.00        COMMENT '每元返多少消费积分',
  -- 推 N 反 1
  `tuijian_enabled`     BIT(1)        NOT NULL DEFAULT b'0'        COMMENT '是否启用推 N 反 1',
  `tuijian_n`           TINYINT       NOT NULL DEFAULT 0           COMMENT 'N 值（推几个）',
  `tuijian_ratios`      VARCHAR(256)  NOT NULL DEFAULT '[]'        COMMENT 'N 个返佣比例 JSON 数组(%)，长度 = tuijian_n',
  -- 积分池
  `pool_enabled`        BIT(1)        NOT NULL DEFAULT b'0'        COMMENT '是否参与星级积分池',
  -- 标准字段
  `creator`     VARCHAR(64)  DEFAULT ''                COMMENT '创建者',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP    COMMENT '创建时间',
  `updater`     VARCHAR(64)  DEFAULT ''                COMMENT '更新者',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                                                       COMMENT '更新时间',
  `deleted`     BIT(1)       NOT NULL DEFAULT b'0'     COMMENT '是否删除',
  `tenant_id`   BIGINT       NOT NULL DEFAULT 0        COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_spu` (`tenant_id`, `spu_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品营销配置（每商品一条）';

-- ----------------------------
-- 3. shop_user_referral  用户推荐链关系（每商户独立）
-- ----------------------------
DROP TABLE IF EXISTS `shop_user_referral`;
CREATE TABLE `shop_user_referral` (
  `id`             BIGINT  NOT NULL AUTO_INCREMENT COMMENT '编号',
  `user_id`        BIGINT  NOT NULL                COMMENT '用户ID',
  `parent_user_id` BIGINT  NOT NULL DEFAULT 0      COMMENT '上级用户ID（直接推荐人），0 = 自然用户',
  `bound_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                                   COMMENT '绑定关系建立时间',
  `bound_order_id` BIGINT  DEFAULT NULL            COMMENT '建立绑定时关联的订单ID',
  `creator`     VARCHAR(64)  DEFAULT ''            COMMENT '创建者',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                                   COMMENT '创建时间',
  `updater`     VARCHAR(64)  DEFAULT ''            COMMENT '更新者',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                                                   COMMENT '更新时间',
  `deleted`     BIT(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`   BIGINT       NOT NULL DEFAULT 0    COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_user` (`tenant_id`, `user_id`),
  KEY `idx_parent` (`tenant_id`, `parent_user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户推荐链关系（每商户独立，user_id 与 parent_user_id 都是 yudao member.user_id）';

-- ----------------------------
-- 4. shop_user_star  用户星级与积分账户（每商户独立）
-- ----------------------------
DROP TABLE IF EXISTS `shop_user_star`;
CREATE TABLE `shop_user_star` (
  `id`                    BIGINT   NOT NULL AUTO_INCREMENT COMMENT '编号',
  `user_id`               BIGINT   NOT NULL                COMMENT '用户ID',
  `direct_count`          INT      NOT NULL DEFAULT 0      COMMENT '直推下级数（直接推荐人 = 自己）',
  `team_sales_count`      INT      NOT NULL DEFAULT 0      COMMENT '团队链路销售份数（自己+所有下级累计销售参与商品份数）',
  `current_star`          TINYINT  NOT NULL DEFAULT 0      COMMENT '当前星级 0=未达任何星级',
  `upgraded_at`           DATETIME DEFAULT NULL            COMMENT '最近一次升星时间',
  `promo_point_balance`   BIGINT   NOT NULL DEFAULT 0      COMMENT '推广积分余额(分)',
  `consume_point_balance` BIGINT   NOT NULL DEFAULT 0      COMMENT '消费积分余额(分)',
  `creator`     VARCHAR(64)  DEFAULT ''            COMMENT '创建者',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                                   COMMENT '创建时间',
  `updater`     VARCHAR(64)  DEFAULT ''            COMMENT '更新者',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                                                   COMMENT '更新时间',
  `deleted`     BIT(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`   BIGINT       NOT NULL DEFAULT 0    COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_user` (`tenant_id`, `user_id`),
  KEY `idx_star` (`tenant_id`, `current_star`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户在每商户下的星级与积分账户（双积分余额放这里，方便查询）';

-- ----------------------------
-- 5. shop_queue_position  商品队列位置（每用户每商品一行）
-- ----------------------------
DROP TABLE IF EXISTS `shop_queue_position`;
CREATE TABLE `shop_queue_position` (
  `id`                  BIGINT     NOT NULL AUTO_INCREMENT COMMENT '编号',
  `spu_id`              BIGINT     NOT NULL                COMMENT '商品 SPU ID',
  `user_id`             BIGINT     NOT NULL                COMMENT '用户ID',
  `layer`               VARCHAR(8) NOT NULL DEFAULT 'B'    COMMENT '层级 A=主动 B=被动',
  `accumulated_count`   INT        NOT NULL DEFAULT 0      COMMENT '累计被返次数',
  `accumulated_amount`  BIGINT     NOT NULL DEFAULT 0      COMMENT '累计推广积分(分)',
  `joined_at`           DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP
                                                           COMMENT '入队时间(B 层)',
  `promoted_at`         DATETIME   DEFAULT NULL            COMMENT '升 A 层时间（A 层排序用）',
  `exited_at`           DATETIME   DEFAULT NULL            COMMENT '出队时间',
  `status`              VARCHAR(16) NOT NULL DEFAULT 'QUEUEING'
                                                           COMMENT '状态 QUEUEING=排队中 EXITED=已出队',
  `creator`     VARCHAR(64)  DEFAULT ''            COMMENT '创建者',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                                   COMMENT '创建时间',
  `updater`     VARCHAR(64)  DEFAULT ''            COMMENT '更新者',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                                                   COMMENT '更新时间',
  `deleted`     BIT(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`   BIGINT       NOT NULL DEFAULT 0    COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_spu_user` (`tenant_id`, `spu_id`, `user_id`),
  KEY `idx_queue_head` (`tenant_id`, `spu_id`, `status`, `layer`, `promoted_at`, `joined_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品队列位置（直推+插队+自然推机制核心表，永不过期，无 cron 清理）';

-- ----------------------------
-- 6. shop_queue_event  队列事件流水（每次返奖记录）
-- ----------------------------
DROP TABLE IF EXISTS `shop_queue_event`;
CREATE TABLE `shop_queue_event` (
  `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
  `spu_id`               BIGINT       NOT NULL                COMMENT '商品 SPU ID',
  `event_type`           VARCHAR(16)  NOT NULL                COMMENT '类型 DIRECT=直推 / QUEUE=自然推 / SELF_PURCHASE=自购插队 / EXIT=出队',
  `beneficiary_user_id`  BIGINT       NOT NULL                COMMENT '受益用户ID',
  `source_user_id`       BIGINT       NOT NULL                COMMENT '触发用户ID（买家）',
  `source_order_id`      BIGINT       DEFAULT NULL            COMMENT '关联订单ID',
  `position_index`       INT          NOT NULL DEFAULT 0      COMMENT '位置序号(从 1 开始，对应 tuijian_ratios 第几个)',
  `ratio_percent`        DECIMAL(5, 2) NOT NULL DEFAULT 0.00  COMMENT '该位置比例(%)',
  `amount`               BIGINT       NOT NULL DEFAULT 0      COMMENT '返推广积分金额(分)',
  `creator`     VARCHAR(64)  DEFAULT ''            COMMENT '创建者',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                                   COMMENT '创建时间',
  `updater`     VARCHAR(64)  DEFAULT ''            COMMENT '更新者',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                                                   COMMENT '更新时间',
  `deleted`     BIT(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`   BIGINT       NOT NULL DEFAULT 0    COMMENT '租户ID',
  PRIMARY KEY (`id`),
  KEY `idx_beneficiary` (`tenant_id`, `beneficiary_user_id`, `create_time`),
  KEY `idx_order`       (`tenant_id`, `source_order_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '队列事件流水';

-- ----------------------------
-- 7. shop_promo_record  推广积分流水
-- ----------------------------
DROP TABLE IF EXISTS `shop_promo_record`;
CREATE TABLE `shop_promo_record` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '编号',
  `user_id`         BIGINT       NOT NULL                 COMMENT '用户ID',
  `source_type`     VARCHAR(16)  NOT NULL                 COMMENT '来源 DIRECT=直推 / QUEUE=队列返 / COMMISSION=团队极差 / POOL=积分池 / CONVERT=转消费积分 / WITHDRAW=提现',
  `amount`          BIGINT       NOT NULL                 COMMENT '金额(分)，正=收入 负=支出',
  `balance_after`   BIGINT       NOT NULL                 COMMENT '变动后余额(分)',
  `source_id`       BIGINT       DEFAULT NULL             COMMENT '来源ID（订单/池子轮次/提现申请）',
  `remark`          VARCHAR(256) DEFAULT NULL             COMMENT '备注',
  `creator`     VARCHAR(64)  DEFAULT ''            COMMENT '创建者',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                                   COMMENT '创建时间',
  `updater`     VARCHAR(64)  DEFAULT ''            COMMENT '更新者',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                                                   COMMENT '更新时间',
  `deleted`     BIT(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`   BIGINT       NOT NULL DEFAULT 0    COMMENT '租户ID',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`tenant_id`, `user_id`, `create_time`),
  KEY `idx_source`    (`tenant_id`, `source_type`, `source_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '推广积分流水';

-- ----------------------------
-- 8. shop_consume_point_record  消费积分流水
-- ----------------------------
DROP TABLE IF EXISTS `shop_consume_point_record`;
CREATE TABLE `shop_consume_point_record` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
  `user_id`         BIGINT       NOT NULL                COMMENT '用户ID',
  `source_type`     VARCHAR(16)  NOT NULL                COMMENT '来源 CONSUME=消费返 / CONVERT=从推广转入 / REDEEM=下单抵扣',
  `amount`          BIGINT       NOT NULL                COMMENT '金额(分)，正=收入 负=支出',
  `balance_after`   BIGINT       NOT NULL                COMMENT '变动后余额(分)',
  `source_id`       BIGINT       DEFAULT NULL            COMMENT '来源ID',
  `remark`          VARCHAR(256) DEFAULT NULL            COMMENT '备注',
  `creator`     VARCHAR(64)  DEFAULT ''            COMMENT '创建者',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                                   COMMENT '创建时间',
  `updater`     VARCHAR(64)  DEFAULT ''            COMMENT '更新者',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                                                   COMMENT '更新时间',
  `deleted`     BIT(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`   BIGINT       NOT NULL DEFAULT 0    COMMENT '租户ID',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`tenant_id`, `user_id`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '消费积分流水';

-- ----------------------------
-- 9. shop_promo_pool  星级积分池余额（每商户一条）
-- ----------------------------
DROP TABLE IF EXISTS `shop_promo_pool`;
CREATE TABLE `shop_promo_pool` (
  `id`              BIGINT   NOT NULL AUTO_INCREMENT COMMENT '编号',
  `balance`         BIGINT   NOT NULL DEFAULT 0      COMMENT '当前池子余额(推广积分,分)',
  `last_settled_at` DATETIME DEFAULT NULL            COMMENT '最近一次结算时间',
  `creator`     VARCHAR(64)  DEFAULT ''            COMMENT '创建者',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                                   COMMENT '创建时间',
  `updater`     VARCHAR(64)  DEFAULT ''            COMMENT '更新者',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                                                   COMMENT '更新时间',
  `deleted`     BIT(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`   BIGINT       NOT NULL DEFAULT 0    COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '星级积分池余额（每商户一条，cron 结算后清零）';

-- ----------------------------
-- 10. shop_promo_pool_round  星级积分池结算批次
-- ----------------------------
DROP TABLE IF EXISTS `shop_promo_pool_round`;
CREATE TABLE `shop_promo_pool_round` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
  `total_amount`      BIGINT       NOT NULL                COMMENT '本轮结算总金额(分)',
  `mode`              VARCHAR(16)  NOT NULL                COMMENT '模式 LOTTERY=抽奖 / FULL=均分',
  `distribute_mode`   VARCHAR(16)  NOT NULL                COMMENT '分配 ALL=全员 / STAR=按星级',
  `participant_count` INT          NOT NULL DEFAULT 0      COMMENT '参与用户数',
  `winner_count`      INT          NOT NULL DEFAULT 0      COMMENT '中奖人数（FULL 模式 = participant_count）',
  `winners`           MEDIUMTEXT                           COMMENT '中奖明细 JSON 数组 [{"userId":1,"star":3,"amount":100}]',
  `settled_at`        DATETIME     NOT NULL                COMMENT '结算时间',
  `creator`     VARCHAR(64)  DEFAULT ''            COMMENT '创建者',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                                   COMMENT '创建时间',
  `updater`     VARCHAR(64)  DEFAULT ''            COMMENT '更新者',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                                                   COMMENT '更新时间',
  `deleted`     BIT(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`   BIGINT       NOT NULL DEFAULT 0    COMMENT '租户ID',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_time` (`tenant_id`, `settled_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '星级积分池结算批次（每轮一条，含中奖明细）';

-- ----------------------------
-- 11. shop_promo_withdraw  推广积分提现申请
-- ----------------------------
DROP TABLE IF EXISTS `shop_promo_withdraw`;
CREATE TABLE `shop_promo_withdraw` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT     COMMENT '编号',
  `user_id`          BIGINT       NOT NULL                    COMMENT '用户ID',
  `amount`           BIGINT       NOT NULL                    COMMENT '提现金额(推广积分,分)',
  `status`           VARCHAR(16)  NOT NULL DEFAULT 'PENDING'  COMMENT '状态 PENDING=待审核 / APPROVED=已通过 / REJECTED=已驳回 / PAID=已结算',
  `apply_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                                              COMMENT '申请时间',
  `processed_at`     DATETIME     DEFAULT NULL                COMMENT '处理时间',
  `processor_id`     BIGINT       DEFAULT NULL                COMMENT '商户审批人 admin_user_id',
  `processor_remark` VARCHAR(256) DEFAULT NULL                COMMENT '审批备注（驳回原因 / 线下结算凭证）',
  `creator`     VARCHAR(64)  DEFAULT ''            COMMENT '创建者',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                                   COMMENT '创建时间',
  `updater`     VARCHAR(64)  DEFAULT ''            COMMENT '更新者',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                                                   COMMENT '更新时间',
  `deleted`     BIT(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`   BIGINT       NOT NULL DEFAULT 0    COMMENT '租户ID',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`tenant_id`, `user_id`, `apply_at`),
  KEY `idx_status`    (`tenant_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '推广积分提现申请（商户线下结算）';
