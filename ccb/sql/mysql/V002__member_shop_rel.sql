CREATE TABLE IF NOT EXISTS `member_shop_rel` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`         BIGINT       NOT NULL COMMENT '会员用户ID',
  `tenant_id`       BIGINT       NOT NULL COMMENT '商户租户ID（即 shop_info.tenant_id）',
  `balance`         INT          NOT NULL DEFAULT 0 COMMENT '在该店铺的余额（分）',
  `points`          INT          NOT NULL DEFAULT 0 COMMENT '在该店铺的积分',
  `referrer_user_id` BIGINT      NULL COMMENT '推荐人用户ID（一级上线）',
  `first_visit_at`  DATETIME     NULL COMMENT '首次进店时间',
  `last_visit_at`   DATETIME     NULL COMMENT '最近进店时间',
  `creator`         VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`         VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_tenant` (`user_id`, `tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员×商户关系（余额/积分/推荐人）';
