-- ===== V016: 用户反馈（C 端「帮助与反馈」） =====
-- 幂等创建：用户提交问题/建议，附问题分类、联系方式、可选 tenantId（如来自某店铺）。
-- 后台「系统管理」可查看处理。

CREATE TABLE IF NOT EXISTS `user_feedback` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`  BIGINT       NOT NULL DEFAULT 0 COMMENT '所属租户（来自店铺则为店铺 tenantId；C 端通用反馈 = 0）',
    `user_id`    BIGINT       NOT NULL COMMENT '提交用户 ID',
    `category`   VARCHAR(32)  NOT NULL DEFAULT 'OTHER'
                  COMMENT '反馈分类：BUG / FEATURE / PAYMENT / ACCOUNT / SHOP / OTHER',
    `content`    VARCHAR(2000) NOT NULL COMMENT '反馈内容',
    `contact`    VARCHAR(64)  NULL COMMENT '联系方式（手机号/微信，可选）',
    `images`     VARCHAR(2000) NULL COMMENT '附图 URL JSON 数组（可选，最多 6 张）',
    `status`     TINYINT      NOT NULL DEFAULT 0 COMMENT '0 = 待处理 / 1 = 处理中 / 2 = 已解决 / 3 = 已关闭',
    `reply`      VARCHAR(2000) NULL COMMENT '客服回复内容',
    `replied_at` DATETIME     NULL COMMENT '回复时间',

    `creator`     VARCHAR(64)  NULL,
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`     VARCHAR(64)  NULL,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     BIT(1)       NOT NULL DEFAULT b'0',

    PRIMARY KEY (`id`),
    KEY `idx_user_id`     (`user_id`),
    KEY `idx_status`      (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '用户反馈（C 端「帮助与反馈」入口）';
