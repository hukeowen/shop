-- ====================================================
-- 铺星 v2 业务表迁移脚本
-- 执行顺序：在所有基础表创建完毕后执行
-- 包含：merchant_apply / tenant_subscription / shop_info /
--       shop_brokerage_config / merchant_referral / ai_video_task
--       以及 trade_brokerage_user 的 share_code 字段追加
-- ====================================================

-- ----------------------------
-- 1. merchant_apply  商户入驻申请表
-- 说明：替代旧的 merchant_info，仅用于入驻审核流程
-- ----------------------------
DROP TABLE IF EXISTS `merchant_apply`;
CREATE TABLE `merchant_apply` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '申请编号',
    -- 基本信息
    `shop_name`       VARCHAR(50)  NOT NULL COMMENT '店铺名称',
    `category_id`     BIGINT       NOT NULL DEFAULT 0 COMMENT '经营类目ID（关联 product_category）',
    `mobile`          VARCHAR(20)  NOT NULL COMMENT '联系手机号',
    `referrer_mobile` VARCHAR(20)           DEFAULT NULL COMMENT '推荐人手机号（选填，用于平台推N返1裂变）',
    -- 资质材料（纯图片，人工审核，无 OCR）
    `license_url`     VARCHAR(512) NOT NULL COMMENT '营业执照图片 OSS URL',
    `id_card_front`   VARCHAR(512) NOT NULL COMMENT '法人身份证正面 OSS URL',
    `id_card_back`    VARCHAR(512) NOT NULL COMMENT '法人身份证背面 OSS URL',
    -- 摊位位置
    `longitude`       DECIMAL(10, 7)        DEFAULT NULL COMMENT '经度',
    `latitude`        DECIMAL(10, 7)        DEFAULT NULL COMMENT '纬度',
    `address`         VARCHAR(200)          DEFAULT NULL COMMENT '详细地址（如：XX夜市B区23号摊位）',
    -- 微信收款配置（可跳过）
    `wx_mch_type`     TINYINT      NOT NULL DEFAULT 0 COMMENT '微信收款类型：0未配置 1已有商户号 2申请子商户',
    `wx_mch_id`       VARCHAR(64)           DEFAULT NULL COMMENT '已有微信商户号（wx_mch_type=1时填写）',
    -- 审核
    `status`          TINYINT      NOT NULL DEFAULT 0 COMMENT '审核状态：0待审核 1通过 2驳回',
    `reject_reason`   VARCHAR(200)          DEFAULT NULL COMMENT '驳回原因',
    `auditor_id`      BIGINT                DEFAULT NULL COMMENT '审核员管理员ID',
    `audit_time`      DATETIME              DEFAULT NULL COMMENT '审核时间',
    -- 开通后关联
    `tenant_id`       BIGINT                DEFAULT NULL COMMENT '审核通过后创建的租户ID',
    -- 公共字段
    `creator`         VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '创建者',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`         VARCHAR(64)           DEFAULT '' COMMENT '更新者',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_mobile` (`mobile`),
    KEY `idx_status` (`status`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '商户入驻申请';

-- ----------------------------
-- 2. tenant_subscription  租户订阅状态
-- 说明：每个商户租户一条记录，管理试用/正式/到期状态及 AI 成片配额
-- ----------------------------
DROP TABLE IF EXISTS `tenant_subscription`;
CREATE TABLE `tenant_subscription` (
    `id`             BIGINT   NOT NULL AUTO_INCREMENT COMMENT '编号',
    `tenant_id`      BIGINT   NOT NULL COMMENT '租户ID（每租户唯一）',
    `status`         TINYINT  NOT NULL DEFAULT 1 COMMENT '订阅状态：1试用 2正式 3过期 4禁用',
    `expire_time`    DATETIME NOT NULL COMMENT '到期时间',
    `ai_video_quota` INT      NOT NULL DEFAULT 0 COMMENT '剩余 AI 成片次数',
    `ai_video_used`  INT      NOT NULL DEFAULT 0 COMMENT '已使用 AI 成片次数',
    -- 公共字段（非租户隔离表，无 tenant_id 业务字段）
    `creator`        VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建者',
    `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`        VARCHAR(64)          DEFAULT '' COMMENT '更新者',
    `update_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_id` (`tenant_id`),
    KEY `idx_status_expire` (`status`, `expire_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '租户订阅状态（试用/正式/过期/AI配额）';

-- ----------------------------
-- 3. shop_info  店铺详情
-- 说明：每个商户租户一条，存储首页展示所需信息；tenant_id 为业务唯一键
-- ----------------------------
DROP TABLE IF EXISTS `shop_info`;
CREATE TABLE `shop_info` (
    `id`             BIGINT         NOT NULL AUTO_INCREMENT COMMENT '编号',
    `tenant_id`      BIGINT         NOT NULL COMMENT '所属租户ID（唯一，一租户一店铺）',
    `shop_name`      VARCHAR(50)    NOT NULL COMMENT '店铺名称',
    `category_id`    BIGINT         NOT NULL DEFAULT 0 COMMENT '经营类目ID',
    `cover_url`      VARCHAR(512)            DEFAULT NULL COMMENT '店铺封面图',
    `description`    VARCHAR(500)            DEFAULT NULL COMMENT '店铺简介',
    `notice`         VARCHAR(300)            DEFAULT NULL COMMENT '店铺公告',
    -- 位置
    `longitude`      DECIMAL(10, 7)          DEFAULT NULL COMMENT '经度',
    `latitude`       DECIMAL(10, 7)          DEFAULT NULL COMMENT '纬度',
    `address`        VARCHAR(200)            DEFAULT NULL COMMENT '详细地址',
    -- 运营信息
    `business_hours` VARCHAR(100)            DEFAULT NULL COMMENT '营业时间（如 09:00-22:00）',
    `mobile`         VARCHAR(20)             DEFAULT NULL COMMENT '客服电话',
    `status`         TINYINT        NOT NULL DEFAULT 1 COMMENT '店铺状态：1正常 2暂停营业 3违规关闭',
    -- 排名缓存（每日定时更新）
    `sales_30d`      INT            NOT NULL DEFAULT 0 COMMENT '近30天销量（缓存）',
    `avg_rating`     DECIMAL(3, 1)  NOT NULL DEFAULT 5.0 COMMENT '平均评分',
    `balance`        INT            NOT NULL DEFAULT 0 COMMENT '商户余额（分）',
    -- 公共字段
    `creator`        VARCHAR(64)    NOT NULL DEFAULT '' COMMENT '创建者',
    `create_time`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`        VARCHAR(64)             DEFAULT '' COMMENT '更新者',
    `update_time`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        BIT(1)         NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_id` (`tenant_id`),
    KEY `idx_category_sales` (`category_id`, `sales_30d`),
    KEY `idx_status` (`status`),
    -- 经纬度范围查询索引（附近商户列表）
    KEY `idx_lat_lng` (`latitude`, `longitude`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '店铺详情（附近商户展示、排名所用）';

-- ----------------------------
-- 4. shop_brokerage_config  商户返佣与积分配置
-- 说明：每个商户一条，控制返佣开关、佣金比例、积分规则、推N返1
-- ----------------------------
DROP TABLE IF EXISTS `shop_brokerage_config`;
CREATE TABLE `shop_brokerage_config` (
    `id`                       BIGINT         NOT NULL AUTO_INCREMENT COMMENT '编号',
    `tenant_id`                BIGINT         NOT NULL COMMENT '所属租户ID（唯一）',
    -- 返佣配置
    `brokerage_enabled`        BIT(1)         NOT NULL DEFAULT 0 COMMENT '是否开启返佣',
    `first_brokerage_percent`  DECIMAL(5, 2)  NOT NULL DEFAULT 0.00 COMMENT '一级佣金比例（%，直接下级消费）',
    `second_brokerage_percent` DECIMAL(5, 2)  NOT NULL DEFAULT 0.00 COMMENT '二级佣金比例（%，间接下级消费）',
    `freeze_days`              INT            NOT NULL DEFAULT 7 COMMENT '佣金冻结天数（防退款纠纷）',
    -- 推N返1配置（商户级，针对推广大使）
    `push_return_enabled`      BIT(1)         NOT NULL DEFAULT 0 COMMENT '是否开启推N返1活动',
    `push_n`                   INT            NOT NULL DEFAULT 3 COMMENT '推广大使需带来的有效订单数',
    `return_amount`            INT            NOT NULL DEFAULT 0 COMMENT '达标奖励金额（分）',
    -- 积分规则
    `point_per_yuan`           INT            NOT NULL DEFAULT 0 COMMENT '消费1元赠积分数（0=关闭积分）',
    -- 提现配置
    `min_withdraw_amount`      INT            NOT NULL DEFAULT 100 COMMENT '最低提现金额（分，默认1元）',
    -- 公共字段
    `creator`                  VARCHAR(64)    NOT NULL DEFAULT '' COMMENT '创建者',
    `create_time`              DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                  VARCHAR(64)             DEFAULT '' COMMENT '更新者',
    `update_time`              DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                  BIT(1)         NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_id` (`tenant_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '商户返佣与积分配置';

-- ----------------------------
-- 5. trade_brokerage_user 追加 share_code 字段
-- 说明：推广大使在该商户的专属分享码，6位字母数字，创建时自动生成
-- 注意：share_code 在同一租户内唯一（(tenant_id, share_code) 联合唯一）
-- ----------------------------
ALTER TABLE `trade_brokerage_user`
    ADD COLUMN `share_code` VARCHAR(10) DEFAULT NULL COMMENT '推广大使专属分享码（6位字母数字）' AFTER `frozen_price`;

-- 同一租户内分享码唯一
ALTER TABLE `trade_brokerage_user`
    ADD UNIQUE KEY `uk_tenant_share_code` (`tenant_id`, `share_code`);

-- ----------------------------
-- 6. merchant_referral  平台商户推荐裂变记录
-- 说明：记录"哪个商户推荐了哪个新商户"，用于平台级推N返1（推N个商户付费返1年）
-- 无 tenant_id 隔离，属于平台全局表
-- ----------------------------
DROP TABLE IF EXISTS `merchant_referral`;
CREATE TABLE `merchant_referral` (
    `id`                  BIGINT      NOT NULL AUTO_INCREMENT COMMENT '编号',
    `referrer_mobile`     VARCHAR(20) NOT NULL COMMENT '推荐人手机号（对应某商户的联系手机）',
    `referrer_tenant_id`  BIGINT               DEFAULT NULL COMMENT '推荐人租户ID（开通后关联）',
    `referee_tenant_id`   BIGINT      NOT NULL COMMENT '被推荐商户的租户ID',
    `paid_at`             DATETIME             DEFAULT NULL COMMENT '被推荐商户首次付费时间',
    `rewarded`            BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否已触发返利（推N达标后置为1）',
    `reward_time`         DATETIME             DEFAULT NULL COMMENT '返利触发时间',
    -- 公共字段
    `creator`             VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建者',
    `create_time`         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`             VARCHAR(64)          DEFAULT '' COMMENT '更新者',
    `update_time`         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_referrer_mobile` (`referrer_mobile`),
    KEY `idx_referrer_tenant` (`referrer_tenant_id`),
    KEY `idx_referee_tenant` (`referee_tenant_id`),
    KEY `idx_rewarded` (`rewarded`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '平台商户推荐裂变记录（推N返1年费）';

-- ----------------------------
-- 7. merchant_withdraw_apply  商户向平台申请提现
-- 说明：商户租户将其经营收益提现至平台，平台人工审核并上传转账凭证
-- 无 tenant_id 隔离（平台级表），继承 BaseDO
-- ----------------------------
DROP TABLE IF EXISTS `merchant_withdraw_apply`;
CREATE TABLE `merchant_withdraw_apply` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '申请编号',
    `tenant_id`     BIGINT       NOT NULL COMMENT '申请商户的租户ID',
    `shop_name`     VARCHAR(50)            DEFAULT NULL COMMENT '商户店铺名称（冗余，方便展示）',
    `amount`        INT          NOT NULL COMMENT '申请提现金额（分）',
    `withdraw_type` TINYINT      NOT NULL DEFAULT 1 COMMENT '提现方式：1微信扫码 2支付宝扫码 3银行转账',
    `account_name`  VARCHAR(50)            DEFAULT NULL COMMENT '账户姓名',
    `account_no`    VARCHAR(100)           DEFAULT NULL COMMENT '账号/收款码URL',
    `bank_name`     VARCHAR(50)            DEFAULT NULL COMMENT '银行名称（银行转账时填写）',
    `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '审核状态：0待审核 1已转账 2驳回',
    `reject_reason` VARCHAR(200)           DEFAULT NULL COMMENT '驳回原因',
    `auditor_id`    BIGINT                 DEFAULT NULL COMMENT '审核员管理员ID',
    `audit_time`    DATETIME               DEFAULT NULL COMMENT '审核时间',
    `voucher_url`   VARCHAR(512)           DEFAULT NULL COMMENT '转账凭证截图 OSS URL',
    -- 公共字段
    `creator`       VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '创建者',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`       VARCHAR(64)            DEFAULT '' COMMENT '更新者',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '商户向平台申请提现记录';

-- ----------------------------
-- 8. ai_video_task  AI成片任务
-- 说明：替代旧的 video_task，增加文案生成、配额管控等字段
-- ----------------------------
DROP TABLE IF EXISTS `ai_video_task`;
CREATE TABLE `ai_video_task` (
    `id`                 BIGINT        NOT NULL AUTO_INCREMENT COMMENT '任务编号',
    `tenant_id`          BIGINT        NOT NULL DEFAULT 0 COMMENT '租户ID',
    `user_id`            BIGINT        NOT NULL DEFAULT 0 COMMENT '操作的商户管理员用户ID',
    -- 输入素材
    `image_urls`         JSON          NOT NULL COMMENT '上传图片 OSS URL 列表（JSON数组，3-9张）',
    `user_description`   VARCHAR(500)  NOT NULL DEFAULT '' COMMENT '用户输入的简短描述（最多200字）',
    -- 文案（两阶段：AI生成 → 用户确认）
    `ai_copywriting`     JSON                   DEFAULT NULL COMMENT 'AI生成的逐句文案（JSON数组，每句≤15字）',
    `final_copywriting`  JSON                   DEFAULT NULL COMMENT '用户确认后的最终文案（可手动修改）',
    -- 成片配置
    `bgm_id`             INT                    DEFAULT NULL COMMENT '选择的背景音乐ID（NULL=纯背景音乐）',
    -- 输出
    `status`             TINYINT       NOT NULL DEFAULT 0
        COMMENT '任务状态：0待处理 1文案生成中 2等待用户确认文案 3视频合成中 4完成 5失败',
    `video_url`          VARCHAR(512)           DEFAULT NULL COMMENT '生成视频的 OSS URL',
    `cover_url`          VARCHAR(512)           DEFAULT NULL COMMENT '视频封面图 OSS URL',
    `fail_reason`        VARCHAR(200)           DEFAULT NULL COMMENT '失败原因',
    -- 配额管控
    `quota_deducted`     BIT(1)        NOT NULL DEFAULT 0 COMMENT '是否已扣减配额（成功后扣，失败不扣）',
    -- 公共字段
    `creator`            VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '创建者',
    `create_time`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`            VARCHAR(64)            DEFAULT '' COMMENT '更新者',
    `update_time`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`            BIT(1)        NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_status` (`tenant_id`, `status`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = 'AI成片任务（文案生成+视频合成+配额管控）';
