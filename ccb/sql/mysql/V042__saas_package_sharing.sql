-- =============================================================================
-- V042：SaaS 套餐"商户邀商户"机制
--
-- 设计要点（详见 docs/design/marketing-system-v8.md 第十二节）：
--   1. 把平台 SaaS 套餐当作 tenant=999 的真实商品 SPU，复用 v8 推广引擎
--   2. ke 端隐藏平台店铺（shop_info.is_platform_shop）
--   3. 新建商户开店分享码表 merchant_invite_share_code
--   4. saas_package_config 关联 product_spu.id 让套餐 = 商品
--
-- 数据清理：用户决策"重头来不留历史"，清掉除 admin/平台/yudao 自带租户外的全部业务数据
-- =============================================================================
SET NAMES utf8mb4;

-- ========== 通用列幂等添加 procedure ==========
DROP PROCEDURE IF EXISTS v042_add_column;
DELIMITER //
CREATE PROCEDURE v042_add_column(
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

-- 1) shop_info 加 is_platform_shop（ke 端列表过滤平台店）
CALL v042_add_column('shop_info', 'is_platform_shop',
  '`is_platform_shop` BIT(1) NOT NULL DEFAULT b\'0\' COMMENT ''是否平台 SaaS 店铺（ke 端隐藏，仅 tuo 端商户买套餐用）''');

-- 2) saas_package_config 关联到 product_spu
CALL v042_add_column('saas_package_config', 'spu_id',
  '`spu_id` BIGINT DEFAULT NULL COMMENT ''关联的 product_spu.id（套餐 = 商品 SPU，复用 trade 流程）''');

DROP PROCEDURE IF EXISTS v042_add_column;

-- ========== 清理测试业务数据（保留 admin tenant=1/yudao 框架 tenant=121,122/平台 tenant=999） ==========

-- 营销引擎流水 / 状态机（全清）
DELETE FROM shop_referral_contribution;
DELETE FROM shop_user_referral;
DELETE FROM shop_queue_position;
DELETE FROM shop_queue_event;
DELETE FROM shop_promo_record;
DELETE FROM shop_consume_point_record;
DELETE FROM shop_promo_deduction_record;
DELETE FROM shop_user_star;
DELETE FROM spu_star_pool;
DELETE FROM spu_star_pool_settle_record;
DELETE FROM spu_star_pool_payout_item;
DELETE FROM product_promo_config;
DELETE FROM shop_promo_pool;
DELETE FROM shop_promo_pool_round;
DELETE FROM shop_promo_config;

-- 交易流水
DELETE FROM trade_order_item;
DELETE FROM trade_order;
DELETE FROM trade_cart;

-- 优惠券
DELETE FROM shop_coupon_user;
DELETE FROM shop_coupon;

-- 商品（保留分类，删 SPU/SKU）
DELETE FROM product_sku;
DELETE FROM product_spu;

-- 会员-商户关系
DELETE FROM member_shop_rel;

-- 商户主体（保留 tenant=999 平台、tenant=1 admin、tenant=121/122 yudao 自带）
DELETE FROM merchant_info        WHERE tenant_id NOT IN (1, 121, 122, 999);
DELETE FROM merchant_apply       WHERE tenant_id NOT IN (1, 121, 122, 999);
DELETE FROM merchant_invite_code WHERE id > 0;  -- BD 码清空
DELETE FROM shop_info            WHERE tenant_id NOT IN (1, 121, 122, 999);

-- 提现申请
DELETE FROM shop_promo_withdraw;
DELETE FROM member_withdraw_apply;
DELETE FROM merchant_withdraw_apply;

-- 订阅订单（老路径全清）
DELETE FROM merchant_subscription_order;
DELETE FROM merchant_package_order;
DELETE FROM merchant_referral;
DELETE FROM merchant_video_quota_log;

-- 用户（除 admin 外的 member_user 全清；admin 在 system_users 表里，不影响）
DELETE FROM member_user WHERE id > 0;

-- 测试租户清掉（除 1/121/122/999）
DELETE FROM system_tenant WHERE id NOT IN (1, 121, 122, 999);

-- ========== 平台店铺 + 套餐 SPU seed ==========

-- 标记平台店铺
UPDATE shop_info     SET is_platform_shop=b'1', shop_name='摊小二平台', status=1, balance=0 WHERE tenant_id=999;
UPDATE merchant_info SET is_platform=b'1', service_package_level='PLATFORM', status=1 WHERE tenant_id=999;

-- 平台运营 user（mobile=10000000000，专用于平台店铺 admin，不参与分销）
INSERT INTO member_user
  (id, mobile, password, nickname, name, avatar, status, register_ip, login_ip, mark, tenant_id, point, experience, creator, deleted)
VALUES
  (99000, '10000000000', '$2a$10$mRMIYLDtRHlf6.9jhfWNN.RFAo7iCMq8GbFLBwBdf57HsbZGgRf2u',
   '平台运营', '平台运营', '', 0, '127.0.0.1', '127.0.0.1', '', 0, 0, 0, 'system', b'0')
ON DUPLICATE KEY UPDATE nickname='平台运营', tenant_id=0;

-- 把平台 merchant_info 关联到这个运营 user（user_id 必填）
UPDATE merchant_info SET user_id=99000 WHERE tenant_id=999;

-- 套餐 SPU：BASIC (¥298) + PRO (¥1688)
-- spu_id 用 99001 / 99002（远离用户租户的真实 SPU 自增 id 范围）
INSERT INTO product_spu
  (id, tenant_id, name, keyword, introduction, description, category_id, brand_id,
   pic_url, sort, status, spec_type, price, market_price, cost_price, stock,
   delivery_types, sales_count, browse_count, creator, deleted)
VALUES
  (99001, 999, '基础包（BASIC）', 'SaaS 基础包', '商户开店基础版，含订单管理 + 推广基础功能',
   '基础包 ¥298/年，含订单管理、AI 视频生成 10 条、推 N 反 1 基础推广',
   0, 0, 'https://tanxiaoer.tos-s3-cn-beijing.volces.com/tanxiaoer/saas-basic.png',
   1, 0, b'0', 29800, 29800, 0, 999999,
   '[2]', 0, 0, 'system', b'0'),
  (99002, 999, '全功能包（PRO）', 'SaaS 全功能包', '商户开店全功能版，含全部营销 + 团队奖励 + 资金池',
   '全功能包 ¥1688/年，含订单管理、AI 视频 30 条、推 N 反 1、团队极差、星级奖池、分销返佣',
   0, 0, 'https://tanxiaoer.tos-s3-cn-beijing.volces.com/tanxiaoer/saas-pro.png',
   2, 0, b'0', 168800, 168800, 0, 999999,
   '[2]', 0, 0, 'system', b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name), price=VALUES(price), market_price=VALUES(market_price);

-- 对应的 SKU（单规格，price=spu price）
INSERT INTO product_sku
  (id, spu_id, properties, price, market_price, cost_price, pic_url, stock,
   sales_count, tenant_id, creator, deleted)
VALUES
  (99001, 99001, '[]', 29800, 29800, 0, 'https://tanxiaoer.tos-s3-cn-beijing.volces.com/tanxiaoer/saas-basic.png', 999999, 0, 999, 'system', b'0'),
  (99002, 99002, '[]', 168800, 168800, 0, 'https://tanxiaoer.tos-s3-cn-beijing.volces.com/tanxiaoer/saas-pro.png', 999999, 0, 999, 'system', b'0')
ON DUPLICATE KEY UPDATE price=VALUES(price);

-- saas_package_config 价格 + spu_id 关联
UPDATE saas_package_config SET price_fen=29800,  spu_id=99001 WHERE level='BASIC';
UPDATE saas_package_config SET price_fen=168800, spu_id=99002 WHERE level='PRO';

-- 套餐 SPU 的营销规则（不开 tuijian，开 directRate + star_count + pool_ratio + pool_dist_rules）
-- 续费天然由 shop_referral_contribution.uk_parent_child_spu 兜底（同 SPU 只触发一次首贡献）
INSERT INTO product_promo_config
  (tenant_id, spu_id, consume_point_ratio,
   tuijian_enabled, tuijian_n, tuijian_ratios,
   direct_rate, star_count, star_ratios, star_upgrade_rules,
   pool_ratio, pool_dist_rules, pool_enabled, creator, deleted)
VALUES
  -- BASIC：直推奖 10%、3 星极差递增、入池 5%、池按 50/30/20 三星分配
  (999, 99001, 0,
   b'0', 0, '[]',
   10.00, 3, '[1,2,3]',
   '[{"star":1,"directCount":3,"teamSales":89400},{"star":2,"directCount":10,"teamSales":298000},{"star":3,"directCount":30,"teamSales":894000}]',
   5.00,
   '[{"star":3,"ratio":50,"mode":"EQUAL"},{"star":2,"ratio":30,"mode":"EQUAL"},{"star":1,"ratio":20,"mode":"LOTTERY","winners":5}]',
   b'0', 'system', b'0'),
  -- PRO：直推奖 15%、3 星极差更陡、入池 8%
  (999, 99002, 0,
   b'0', 0, '[]',
   15.00, 3, '[2,3,5]',
   '[{"star":1,"directCount":3,"teamSales":506400},{"star":2,"directCount":10,"teamSales":1688000},{"star":3,"directCount":30,"teamSales":5064000}]',
   8.00,
   '[{"star":3,"ratio":50,"mode":"EQUAL"},{"star":2,"ratio":30,"mode":"EQUAL"},{"star":1,"ratio":20,"mode":"LOTTERY","winners":5}]',
   b'0', 'system', b'0');

-- ========== 商户开店分享码 ==========
CREATE TABLE IF NOT EXISTS `merchant_invite_share_code` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT,
  `tenant_id`         BIGINT       NOT NULL DEFAULT 0,
  `referrer_user_id`  BIGINT       NOT NULL                COMMENT '邀请人商户的 member_user.id（v8 推广引擎 parent_user_id）',
  `referrer_tenant_id` BIGINT      NOT NULL                COMMENT '邀请人商户的 tenant_id（用于反查商户身份）',
  `code`              VARCHAR(16)  NOT NULL                COMMENT '分享码（6-8 位，全局唯一）',
  `used_count`        INT          NOT NULL DEFAULT 0      COMMENT '已使用次数（成功注册的商户数）',
  `enabled`           BIT(1)       NOT NULL DEFAULT b'1'   COMMENT '是否启用',
  `remark`            VARCHAR(255) DEFAULT ''              COMMENT '备注',
  `creator`           VARCHAR(64)  DEFAULT '',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater`           VARCHAR(64)  DEFAULT '',
  `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`           BIT(1)       NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`, `deleted`),
  KEY `idx_referrer` (`referrer_user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商户开店分享码 — 商户邀请新商户入驻';

-- ========== merchant_apply 补 invite_code（绑定上线商户） ==========
DROP PROCEDURE IF EXISTS v042_add_column;
DELIMITER //
CREATE PROCEDURE v042_add_column(
  IN p_table VARCHAR(64) CHARACTER SET utf8mb4,
  IN p_column VARCHAR(64) CHARACTER SET utf8mb4,
  IN p_def TEXT CHARACTER SET utf8mb4
)
BEGIN
  DECLARE col_count INT DEFAULT 0;
  SELECT COUNT(*) INTO col_count FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column;
  IF col_count = 0 THEN
    SET @ddl := CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_def);
    PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END//
DELIMITER ;

CALL v042_add_column('merchant_apply', 'share_code',
  '`share_code` VARCHAR(16) DEFAULT NULL COMMENT ''商户入驻时使用的分享码（关联 merchant_invite_share_code.code）''');
CALL v042_add_column('merchant_apply', 'referrer_user_id',
  '`referrer_user_id` BIGINT DEFAULT NULL COMMENT ''上线商户 user_id（解析分享码后冗余落库）''');

DROP PROCEDURE IF EXISTS v042_add_column;
