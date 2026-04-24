CREATE TABLE IF NOT EXISTS `member_withdraw_apply` (
  `id`             BIGINT     NOT NULL AUTO_INCREMENT,
  `user_id`        BIGINT     NOT NULL,
  `tenant_id`      BIGINT     NOT NULL COMMENT '商户租户ID',
  `amount`         INT        NOT NULL COMMENT '提现金额（分）',
  `withdraw_type`  TINYINT    NOT NULL COMMENT '1微信 2支付宝 3银行',
  `account_name`   VARCHAR(64) NULL,
  `account_no`     VARCHAR(128) NULL,
  `bank_name`      VARCHAR(64)  NULL,
  `status`         TINYINT    NOT NULL DEFAULT 0 COMMENT '0待审核 1已打款 2驳回',
  `reject_reason`  VARCHAR(255) NULL,
  `voucher_url`    VARCHAR(512) NULL,
  `creator` VARCHAR(64) NOT NULL DEFAULT '', `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` VARCHAR(64) NOT NULL DEFAULT '', `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员提现申请';
