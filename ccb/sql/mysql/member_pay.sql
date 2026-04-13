-- ====================================================
-- member 模块 + pay 模块 建表 SQL
-- 生成自 Java 实体类，适用于 MySQL (InnoDB / utf8mb4)
-- ====================================================

-- ----------------------------
-- 1. member_address  用户收件地址
-- ----------------------------
CREATE TABLE IF NOT EXISTS `member_address` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
    `user_id`        BIGINT       NOT NULL DEFAULT 0 COMMENT '用户编号',
    `name`           VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '收件人名称',
    `mobile`         VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '手机号',
    `area_id`        BIGINT       NOT NULL DEFAULT 0 COMMENT '地区编号',
    `detail_address` VARCHAR(256) NOT NULL DEFAULT '' COMMENT '收件详细地址',
    `default_status` BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否默认',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`        VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`        VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收件地址';

-- ----------------------------
-- 2. member_config  会员配置
-- ----------------------------
CREATE TABLE IF NOT EXISTS `member_config` (
    `id`                            BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `point_trade_deduct_enable`     BIT(1) NOT NULL DEFAULT 0 COMMENT '积分抵扣开关',
    `point_trade_deduct_unit_price` INT    NOT NULL DEFAULT 0 COMMENT '积分抵扣单价（分），1 积分抵扣多少分',
    `point_trade_deduct_max_price`  INT    NOT NULL DEFAULT 0 COMMENT '积分抵扣最大值',
    `point_trade_give_point`        INT    NOT NULL DEFAULT 0 COMMENT '1 元赠送多少积分',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员配置';

-- ----------------------------
-- 3. member_group  用户分组
-- ----------------------------
CREATE TABLE IF NOT EXISTS `member_group` (
    `id`     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
    `name`   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '名称',
    `remark` VARCHAR(256) NOT NULL DEFAULT '' COMMENT '备注',
    `status` INT          NOT NULL DEFAULT 0 COMMENT '状态',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户分组';

-- ----------------------------
-- 4. member_experience_record  会员经验记录
-- ----------------------------
CREATE TABLE IF NOT EXISTS `member_experience_record` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
    `user_id`          BIGINT       NOT NULL DEFAULT 0 COMMENT '用户编号',
    `biz_type`         INT          NOT NULL DEFAULT 0 COMMENT '业务类型',
    `biz_id`           VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '业务编号',
    `title`            VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '标题',
    `description`      VARCHAR(256) NOT NULL DEFAULT '' COMMENT '描述',
    `experience`       INT          NOT NULL DEFAULT 0 COMMENT '经验变动值',
    `total_experience` INT          NOT NULL DEFAULT 0 COMMENT '变更后的总经验',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员经验记录';

-- ----------------------------
-- 5. member_level  会员等级
-- ----------------------------
CREATE TABLE IF NOT EXISTS `member_level` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
    `name`             VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '等级名称',
    `level`            INT          NOT NULL DEFAULT 0 COMMENT '等级',
    `experience`       INT          NOT NULL DEFAULT 0 COMMENT '升级所需经验',
    `discount_percent` INT          NOT NULL DEFAULT 0 COMMENT '享受折扣（百分比）',
    `icon`             VARCHAR(512) NOT NULL DEFAULT '' COMMENT '等级图标 URL',
    `background_url`   VARCHAR(512) NOT NULL DEFAULT '' COMMENT '等级背景图 URL',
    `status`           INT          NOT NULL DEFAULT 0 COMMENT '状态',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员等级';

-- ----------------------------
-- 6. member_level_record  会员等级变更记录
-- ----------------------------
CREATE TABLE IF NOT EXISTS `member_level_record` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
    `user_id`          BIGINT       NOT NULL DEFAULT 0 COMMENT '用户编号',
    `level_id`         BIGINT       NOT NULL DEFAULT 0 COMMENT '等级编号',
    `level`            INT          NOT NULL DEFAULT 0 COMMENT '会员等级',
    `discount_percent` INT          NOT NULL DEFAULT 0 COMMENT '享受折扣（百分比）',
    `experience`       INT          NOT NULL DEFAULT 0 COMMENT '升级经验',
    `user_experience`  INT          NOT NULL DEFAULT 0 COMMENT '会员此时的经验',
    `remark`           VARCHAR(256) NOT NULL DEFAULT '' COMMENT '备注',
    `description`      VARCHAR(256) NOT NULL DEFAULT '' COMMENT '描述',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员等级变更记录';

-- ----------------------------
-- 7. member_point_record  用户积分记录
-- ----------------------------
CREATE TABLE IF NOT EXISTS `member_point_record` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `user_id`     BIGINT       NOT NULL DEFAULT 0 COMMENT '用户编号',
    `biz_id`      VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '业务编码',
    `biz_type`    INT          NOT NULL DEFAULT 0 COMMENT '业务类型',
    `title`       VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '积分标题',
    `description` VARCHAR(256) NOT NULL DEFAULT '' COMMENT '积分描述',
    `point`       INT          NOT NULL DEFAULT 0 COMMENT '变动积分（正增负减）',
    `total_point` INT          NOT NULL DEFAULT 0 COMMENT '变动后的总积分',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户积分记录';

-- ----------------------------
-- 8. member_sign_in_config  签到规则
-- ----------------------------
CREATE TABLE IF NOT EXISTS `member_sign_in_config` (
    `id`         BIGINT NOT NULL AUTO_INCREMENT COMMENT '规则自增主键',
    `day`        INT    NOT NULL DEFAULT 0 COMMENT '签到第 x 天',
    `point`      INT    NOT NULL DEFAULT 0 COMMENT '奖励积分',
    `experience` INT    NOT NULL DEFAULT 0 COMMENT '奖励经验',
    `status`     INT    NOT NULL DEFAULT 0 COMMENT '状态',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='签到规则';

-- ----------------------------
-- 9. member_sign_in_record  签到记录
-- ----------------------------
CREATE TABLE IF NOT EXISTS `member_sign_in_record` (
    `id`         BIGINT NOT NULL AUTO_INCREMENT COMMENT '编号',
    `user_id`    BIGINT NOT NULL DEFAULT 0 COMMENT '签到用户编号',
    `day`        INT    NOT NULL DEFAULT 0 COMMENT '第几天签到',
    `point`      INT    NOT NULL DEFAULT 0 COMMENT '签到获得积分',
    `experience` INT    NOT NULL DEFAULT 0 COMMENT '签到获得经验',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='签到记录';

-- ----------------------------
-- 10. member_tag  会员标签
-- ----------------------------
CREATE TABLE IF NOT EXISTS `member_tag` (
    `id`   BIGINT      NOT NULL AUTO_INCREMENT COMMENT '编号',
    `name` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '标签名称',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员标签';

-- ----------------------------
-- 11. member_user  会员用户（继承 TenantBaseDO）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `member_user` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `mobile`            VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '手机号',
    `password`          VARCHAR(256) NOT NULL DEFAULT '' COMMENT '加密后的密码',
    `status`            INT          NOT NULL DEFAULT 0 COMMENT '帐号状态',
    `register_ip`       VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '注册 IP',
    `register_terminal` INT          NOT NULL DEFAULT 0 COMMENT '注册终端',
    `login_ip`          VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '最后登录 IP',
    `login_date`        DATETIME              DEFAULT NULL COMMENT '最后登录时间',
    `nickname`          VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '用户昵称',
    `avatar`            VARCHAR(512) NOT NULL DEFAULT '' COMMENT '用户头像 URL',
    `name`              VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '真实姓名',
    `sex`               INT          NOT NULL DEFAULT 0 COMMENT '性别',
    `birthday`          DATETIME              DEFAULT NULL COMMENT '出生日期',
    `area_id`           INT                   DEFAULT NULL COMMENT '所在地区编号',
    `mark`              VARCHAR(256) NOT NULL DEFAULT '' COMMENT '用户备注',
    `point`             INT          NOT NULL DEFAULT 0 COMMENT '积分',
    `tag_ids`           JSON                  DEFAULT NULL COMMENT '会员标签列表',
    `level_id`          BIGINT                DEFAULT NULL COMMENT '会员级别编号',
    `experience`        INT          NOT NULL DEFAULT 0 COMMENT '会员经验',
    `group_id`          BIGINT                DEFAULT NULL COMMENT '用户分组编号',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mobile` (`mobile`, `tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员用户';

-- ====================================================
-- pay 模块
-- ====================================================

-- ----------------------------
-- 12. pay_app  支付应用
-- ----------------------------
CREATE TABLE IF NOT EXISTS `pay_app` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '应用编号',
    `app_key`              VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '应用标识',
    `name`                 VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '应用名',
    `status`               INT          NOT NULL DEFAULT 0 COMMENT '状态',
    `remark`               VARCHAR(256) NOT NULL DEFAULT '' COMMENT '备注',
    `order_notify_url`     VARCHAR(512) NOT NULL DEFAULT '' COMMENT '支付结果回调地址',
    `refund_notify_url`    VARCHAR(512) NOT NULL DEFAULT '' COMMENT '退款结果回调地址',
    `transfer_notify_url`  VARCHAR(512) NOT NULL DEFAULT '' COMMENT '转账结果回调地址',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付应用';

-- ----------------------------
-- 13. pay_channel  支付渠道（继承 TenantBaseDO）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `pay_channel` (
    `id`       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '渠道编号',
    `code`     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '渠道编码',
    `status`   INT          NOT NULL DEFAULT 0 COMMENT '状态',
    `fee_rate` DOUBLE       NOT NULL DEFAULT 0 COMMENT '渠道费率（百分比）',
    `remark`   VARCHAR(256) NOT NULL DEFAULT '' COMMENT '备注',
    `app_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '应用编号',
    `config`   JSON                  DEFAULT NULL COMMENT '支付渠道配置（JSON）',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付渠道';

-- ----------------------------
-- 14. pay_demo_order  示例订单
-- ----------------------------
CREATE TABLE IF NOT EXISTS `pay_demo_order` (
    `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '订单编号',
    `user_id`          BIGINT      NOT NULL DEFAULT 0 COMMENT '用户编号',
    `spu_id`           BIGINT      NOT NULL DEFAULT 0 COMMENT '商品编号',
    `spu_name`         VARCHAR(64) NOT NULL DEFAULT '' COMMENT '商品名称',
    `price`            INT         NOT NULL DEFAULT 0 COMMENT '价格（分）',
    `pay_status`       BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否已支付',
    `pay_order_id`     BIGINT               DEFAULT NULL COMMENT '支付订单编号',
    `pay_time`         DATETIME             DEFAULT NULL COMMENT '付款时间',
    `pay_channel_code` VARCHAR(64)          DEFAULT NULL COMMENT '支付渠道编码',
    `pay_refund_id`    BIGINT               DEFAULT NULL COMMENT '支付退款单号',
    `refund_price`     INT         NOT NULL DEFAULT 0 COMMENT '退款金额（分）',
    `refund_time`      DATETIME             DEFAULT NULL COMMENT '退款完成时间',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='示例订单（演示支付与退款接入）';

-- ----------------------------
-- 15. pay_demo_withdraw  示例提现订单
-- ----------------------------
CREATE TABLE IF NOT EXISTS `pay_demo_withdraw` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '提现单编号',
    `subject`              VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '提现标题',
    `price`                INT          NOT NULL DEFAULT 0 COMMENT '提现金额（分）',
    `user_account`         VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '收款人账号',
    `user_name`            VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '收款人姓名',
    `type`                 INT          NOT NULL DEFAULT 0 COMMENT '提现方式',
    `status`               INT          NOT NULL DEFAULT 0 COMMENT '提现状态',
    `pay_transfer_id`      BIGINT               DEFAULT NULL COMMENT '转账单编号',
    `transfer_channel_code` VARCHAR(64)          DEFAULT NULL COMMENT '转账渠道编码',
    `transfer_time`        DATETIME             DEFAULT NULL COMMENT '转账成功时间',
    `transfer_error_msg`   VARCHAR(256)         DEFAULT NULL COMMENT '转账错误提示',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='示例提现订单（演示转账接入）';

-- ----------------------------
-- 16. pay_notify_log  支付通知日志
-- ----------------------------
CREATE TABLE IF NOT EXISTS `pay_notify_log` (
    `id`           BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志编号',
    `task_id`      BIGINT NOT NULL DEFAULT 0 COMMENT '通知任务编号',
    `notify_times` INT    NOT NULL DEFAULT 0 COMMENT '第几次被通知',
    `response`     TEXT            DEFAULT NULL COMMENT 'HTTP 响应结果',
    `status`       INT    NOT NULL DEFAULT 0 COMMENT '支付通知状态',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付通知日志';

-- ----------------------------
-- 17. pay_notify_task  支付通知任务（继承 TenantBaseDO）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `pay_notify_task` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
    `app_id`               BIGINT       NOT NULL DEFAULT 0 COMMENT '应用编号',
    `type`                 INT          NOT NULL DEFAULT 0 COMMENT '通知类型',
    `data_id`              BIGINT       NOT NULL DEFAULT 0 COMMENT '数据编号',
    `merchant_order_id`    VARCHAR(64)           DEFAULT NULL COMMENT '商户订单编号',
    `merchant_refund_id`   VARCHAR(64)           DEFAULT NULL COMMENT '商户退款编号',
    `merchant_transfer_id` VARCHAR(64)           DEFAULT NULL COMMENT '商户转账编号',
    `status`               INT          NOT NULL DEFAULT 0 COMMENT '通知状态',
    `next_notify_time`     DATETIME              DEFAULT NULL COMMENT '下一次通知时间',
    `last_execute_time`    DATETIME              DEFAULT NULL COMMENT '最后一次执行时间',
    `notify_times`         INT          NOT NULL DEFAULT 0 COMMENT '当前通知次数',
    `max_notify_times`     INT          NOT NULL DEFAULT 0 COMMENT '最大可通知次数',
    `notify_url`           VARCHAR(512) NOT NULL DEFAULT '' COMMENT '通知地址',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付通知任务';

-- ----------------------------
-- 18. pay_order  支付订单
-- ----------------------------
CREATE TABLE IF NOT EXISTS `pay_order` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '订单编号',
    `app_id`             BIGINT       NOT NULL DEFAULT 0 COMMENT '应用编号',
    `channel_id`         BIGINT                DEFAULT NULL COMMENT '渠道编号',
    `channel_code`       VARCHAR(64)           DEFAULT NULL COMMENT '渠道编码',
    `user_id`            BIGINT       NOT NULL DEFAULT 0 COMMENT '用户编号',
    `user_type`          INT          NOT NULL DEFAULT 0 COMMENT '用户类型',
    `merchant_order_id`  VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '商户订单编号',
    `subject`            VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '商品标题',
    `body`               VARCHAR(256) NOT NULL DEFAULT '' COMMENT '商品描述',
    `notify_url`         VARCHAR(512) NOT NULL DEFAULT '' COMMENT '异步通知地址',
    `price`              INT          NOT NULL DEFAULT 0 COMMENT '支付金额（分）',
    `channel_fee_rate`   DOUBLE       NOT NULL DEFAULT 0 COMMENT '渠道手续费（%）',
    `channel_fee_price`  INT          NOT NULL DEFAULT 0 COMMENT '渠道手续金额（分）',
    `status`             INT          NOT NULL DEFAULT 0 COMMENT '支付状态',
    `user_ip`            VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '用户 IP',
    `expire_time`        DATETIME              DEFAULT NULL COMMENT '订单失效时间',
    `success_time`       DATETIME              DEFAULT NULL COMMENT '订单支付成功时间',
    `extension_id`       BIGINT                DEFAULT NULL COMMENT '支付成功的订单拓展单编号',
    `no`                 VARCHAR(64)           DEFAULT NULL COMMENT '支付成功的外部订单号',
    `refund_price`       INT          NOT NULL DEFAULT 0 COMMENT '退款总金额（分）',
    `channel_user_id`    VARCHAR(64)           DEFAULT NULL COMMENT '渠道用户编号',
    `channel_order_no`   VARCHAR(64)           DEFAULT NULL COMMENT '渠道订单号',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付订单';

-- ----------------------------
-- 19. pay_order_extension  支付订单拓展
-- ----------------------------
CREATE TABLE IF NOT EXISTS `pay_order_extension` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '订单拓展编号',
    `no`                   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '外部订单号',
    `order_id`             BIGINT       NOT NULL DEFAULT 0 COMMENT '订单号',
    `channel_id`           BIGINT       NOT NULL DEFAULT 0 COMMENT '渠道编号',
    `channel_code`         VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '渠道编码',
    `user_ip`              VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '用户 IP',
    `status`               INT          NOT NULL DEFAULT 0 COMMENT '支付状态',
    `channel_extras`       JSON                  DEFAULT NULL COMMENT '支付渠道额外参数（JSON）',
    `channel_error_code`   VARCHAR(64)           DEFAULT NULL COMMENT '渠道错误码',
    `channel_error_msg`    VARCHAR(256)          DEFAULT NULL COMMENT '渠道错误信息',
    `channel_notify_data`  TEXT                  DEFAULT NULL COMMENT '渠道通知内容',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_no` (`no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付订单拓展';

-- ----------------------------
-- 20. pay_refund  支付退款单
-- ----------------------------
CREATE TABLE IF NOT EXISTS `pay_refund` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '退款单编号',
    `no`                  VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '外部退款号',
    `app_id`              BIGINT       NOT NULL DEFAULT 0 COMMENT '应用编号',
    `channel_id`          BIGINT       NOT NULL DEFAULT 0 COMMENT '渠道编号',
    `channel_code`        VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '渠道编码',
    `order_id`            BIGINT       NOT NULL DEFAULT 0 COMMENT '订单编号',
    `order_no`            VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '支付订单号',
    `user_id`             BIGINT       NOT NULL DEFAULT 0 COMMENT '用户编号',
    `user_type`           INT          NOT NULL DEFAULT 0 COMMENT '用户类型',
    `merchant_order_id`   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '商户订单编号',
    `merchant_refund_id`  VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '商户退款订单号',
    `notify_url`          VARCHAR(512) NOT NULL DEFAULT '' COMMENT '异步通知地址',
    `status`              INT          NOT NULL DEFAULT 0 COMMENT '退款状态',
    `pay_price`           INT          NOT NULL DEFAULT 0 COMMENT '支付金额（分）',
    `refund_price`        INT          NOT NULL DEFAULT 0 COMMENT '退款金额（分）',
    `reason`              VARCHAR(256)          DEFAULT NULL COMMENT '退款原因',
    `user_ip`             VARCHAR(64)           DEFAULT NULL COMMENT '用户 IP',
    `channel_order_no`    VARCHAR(64)           DEFAULT NULL COMMENT '渠道订单号',
    `channel_refund_no`   VARCHAR(64)           DEFAULT NULL COMMENT '渠道退款单号',
    `success_time`        DATETIME              DEFAULT NULL COMMENT '退款成功时间',
    `channel_error_code`  VARCHAR(64)           DEFAULT NULL COMMENT '渠道错误码',
    `channel_error_msg`   VARCHAR(256)          DEFAULT NULL COMMENT '渠道错误提示',
    `channel_notify_data` TEXT                  DEFAULT NULL COMMENT '渠道通知内容',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付退款单';

-- ----------------------------
-- 21. pay_transfer  转账单
-- ----------------------------
CREATE TABLE IF NOT EXISTS `pay_transfer` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
    `no`                   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '转账单号',
    `app_id`               BIGINT       NOT NULL DEFAULT 0 COMMENT '应用编号',
    `channel_id`           BIGINT       NOT NULL DEFAULT 0 COMMENT '转账渠道编号',
    `channel_code`         VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '转账渠道编码',
    `user_id`              BIGINT       NOT NULL DEFAULT 0 COMMENT '用户编号',
    `user_type`            INT          NOT NULL DEFAULT 0 COMMENT '用户类型',
    `merchant_transfer_id` VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '商户转账单编号',
    `subject`              VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '转账标题',
    `price`                INT          NOT NULL DEFAULT 0 COMMENT '转账金额（分）',
    `user_account`         VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '收款人账号',
    `user_name`            VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '收款人姓名',
    `status`               INT          NOT NULL DEFAULT 0 COMMENT '转账状态',
    `success_time`         DATETIME              DEFAULT NULL COMMENT '转账成功时间',
    `notify_url`           VARCHAR(512) NOT NULL DEFAULT '' COMMENT '异步通知地址',
    `user_ip`              VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '用户 IP',
    `channel_extras`       JSON                  DEFAULT NULL COMMENT '渠道额外参数（JSON）',
    `channel_transfer_no`  VARCHAR(64)           DEFAULT NULL COMMENT '渠道转账单号',
    `channel_error_code`   VARCHAR(64)           DEFAULT NULL COMMENT '渠道错误码',
    `channel_error_msg`    VARCHAR(256)          DEFAULT NULL COMMENT '渠道错误提示',
    `channel_notify_data`  TEXT                  DEFAULT NULL COMMENT '渠道通知内容',
    `channel_package_info` TEXT                  DEFAULT NULL COMMENT '渠道 package 信息（微信转账专用）',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='转账单';

-- ----------------------------
-- 22. pay_wallet  会员钱包
-- ----------------------------
CREATE TABLE IF NOT EXISTS `pay_wallet` (
    `id`             BIGINT NOT NULL AUTO_INCREMENT COMMENT '编号',
    `user_id`        BIGINT NOT NULL DEFAULT 0 COMMENT '用户编号',
    `user_type`      INT    NOT NULL DEFAULT 0 COMMENT '用户类型',
    `balance`        INT    NOT NULL DEFAULT 0 COMMENT '余额（分）',
    `freeze_price`   INT    NOT NULL DEFAULT 0 COMMENT '冻结金额（分）',
    `total_expense`  INT    NOT NULL DEFAULT 0 COMMENT '累计支出（分）',
    `total_recharge` INT    NOT NULL DEFAULT 0 COMMENT '累计充值（分）',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员钱包';

-- ----------------------------
-- 23. pay_wallet_recharge  会员钱包充值
-- ----------------------------
CREATE TABLE IF NOT EXISTS `pay_wallet_recharge` (
    `id`                  BIGINT      NOT NULL AUTO_INCREMENT COMMENT '编号',
    `wallet_id`           BIGINT      NOT NULL DEFAULT 0 COMMENT '钱包编号',
    `total_price`         INT         NOT NULL DEFAULT 0 COMMENT '用户实际到账余额（分）',
    `pay_price`           INT         NOT NULL DEFAULT 0 COMMENT '实际支付金额（分）',
    `bonus_price`         INT         NOT NULL DEFAULT 0 COMMENT '钱包赠送金额（分）',
    `package_id`          BIGINT               DEFAULT NULL COMMENT '充值套餐编号',
    `pay_status`          BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否已支付',
    `pay_order_id`        BIGINT               DEFAULT NULL COMMENT '支付订单编号',
    `pay_channel_code`    VARCHAR(64)          DEFAULT NULL COMMENT '支付成功的渠道编码',
    `pay_time`            DATETIME             DEFAULT NULL COMMENT '订单支付时间',
    `pay_refund_id`       BIGINT               DEFAULT NULL COMMENT '支付退款单编号',
    `refund_total_price`  INT         NOT NULL DEFAULT 0 COMMENT '退款总金额（分）',
    `refund_pay_price`    INT         NOT NULL DEFAULT 0 COMMENT '退款支付金额（分）',
    `refund_bonus_price`  INT         NOT NULL DEFAULT 0 COMMENT '退款赠送金额（分）',
    `refund_time`         DATETIME             DEFAULT NULL COMMENT '退款时间',
    `refund_status`       INT                  DEFAULT NULL COMMENT '退款状态',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员钱包充值';

-- ----------------------------
-- 24. pay_wallet_recharge_package  会员钱包充值套餐
-- ----------------------------
CREATE TABLE IF NOT EXISTS `pay_wallet_recharge_package` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '编号',
    `name`        VARCHAR(64) NOT NULL DEFAULT '' COMMENT '套餐名',
    `pay_price`   INT         NOT NULL DEFAULT 0 COMMENT '支付金额（分）',
    `bonus_price` INT         NOT NULL DEFAULT 0 COMMENT '赠送金额（分）',
    `status`      INT         NOT NULL DEFAULT 0 COMMENT '状态',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员钱包充值套餐';

-- ----------------------------
-- 25. pay_wallet_transaction  会员钱包流水
-- ----------------------------
CREATE TABLE IF NOT EXISTS `pay_wallet_transaction` (
    `id`        BIGINT      NOT NULL AUTO_INCREMENT COMMENT '编号',
    `no`        VARCHAR(64) NOT NULL DEFAULT '' COMMENT '流水号',
    `wallet_id` BIGINT      NOT NULL DEFAULT 0 COMMENT '钱包编号',
    `biz_type`  INT         NOT NULL DEFAULT 0 COMMENT '关联业务分类',
    `biz_id`    VARCHAR(64) NOT NULL DEFAULT '' COMMENT '关联业务编号',
    `title`     VARCHAR(64) NOT NULL DEFAULT '' COMMENT '流水说明',
    `price`     INT         NOT NULL DEFAULT 0 COMMENT '交易金额（分，正增负减）',
    `balance`   INT         NOT NULL DEFAULT 0 COMMENT '交易后余额（分）',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_no` (`no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员钱包流水';
