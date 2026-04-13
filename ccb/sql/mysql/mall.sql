-- ============================================================
-- Mall 模块数据库建表脚本
-- 包含：product / promotion / statistics / trade 四个模块
-- 生成时间：2026-04-13
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- product 模块
-- ============================================================

-- ----------------------------
-- product_brand 商品品牌
-- ----------------------------
CREATE TABLE IF NOT EXISTS `product_brand` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '品牌编号',
    `name`        VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '品牌名称',
    `pic_url`     VARCHAR(512) NOT NULL DEFAULT '' COMMENT '品牌图片',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '品牌排序',
    `description` VARCHAR(256) NOT NULL DEFAULT '' COMMENT '品牌描述',
    `status`      INT          NOT NULL DEFAULT 0 COMMENT '状态（0=开启 1=关闭）',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '商品品牌';

-- ----------------------------
-- product_category 商品分类
-- ----------------------------
CREATE TABLE IF NOT EXISTS `product_category` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '分类编号',
    `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '父分类编号',
    `name`        VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '分类名称',
    `pic_url`     VARCHAR(512) NOT NULL DEFAULT '' COMMENT '移动端分类图',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '分类排序',
    `status`      INT          NOT NULL DEFAULT 0 COMMENT '开启状态（0=开启 1=关闭）',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '商品分类';

-- ----------------------------
-- product_comment 商品评论
-- ----------------------------
CREATE TABLE IF NOT EXISTS `product_comment` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评论编号',
    `user_id`             BIGINT       NOT NULL DEFAULT 0 COMMENT '评价人用户编号',
    `user_nickname`       VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '评价人名称',
    `user_avatar`         VARCHAR(512) NOT NULL DEFAULT '' COMMENT '评价人头像',
    `anonymous`           BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否匿名',
    `order_id`            BIGINT       NOT NULL DEFAULT 0 COMMENT '交易订单编号',
    `order_item_id`       BIGINT       NOT NULL DEFAULT 0 COMMENT '交易订单项编号',
    `spu_id`              BIGINT       NOT NULL DEFAULT 0 COMMENT '商品SPU编号',
    `spu_name`            VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '商品SPU名称',
    `sku_id`              BIGINT       NOT NULL DEFAULT 0 COMMENT '商品SKU编号',
    `sku_pic_url`         VARCHAR(512) NOT NULL DEFAULT '' COMMENT '商品SKU图片地址',
    `sku_properties`      JSON                  COMMENT '属性数组（JSON）',
    `visible`             BIT(1)       NOT NULL DEFAULT 1 COMMENT '是否可见',
    `scores`              INT          NOT NULL DEFAULT 5 COMMENT '评分星级（1-5）',
    `description_scores`  INT          NOT NULL DEFAULT 5 COMMENT '描述星级（1-5）',
    `benefit_scores`      INT          NOT NULL DEFAULT 5 COMMENT '服务星级（1-5）',
    `content`             VARCHAR(2048)         COMMENT '评论内容',
    `pic_urls`            JSON                  COMMENT '评论图片地址数组（JSON）',
    `reply_status`        BIT(1)       NOT NULL DEFAULT 0 COMMENT '商家是否回复',
    `reply_user_id`       BIGINT                COMMENT '回复管理员编号',
    `reply_content`       VARCHAR(512)          COMMENT '商家回复内容',
    `reply_time`          DATETIME              COMMENT '商家回复时间',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`             VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`             VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '商品评论';

-- ----------------------------
-- product_favorite 商品收藏
-- ----------------------------
CREATE TABLE IF NOT EXISTS `product_favorite` (
    `id`          BIGINT  NOT NULL AUTO_INCREMENT COMMENT '编号',
    `user_id`     BIGINT  NOT NULL DEFAULT 0 COMMENT '用户编号',
    `spu_id`      BIGINT  NOT NULL DEFAULT 0 COMMENT '商品SPU编号',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '商品收藏';

-- ----------------------------
-- product_browse_history 商品浏览记录
-- ----------------------------
CREATE TABLE IF NOT EXISTS `product_browse_history` (
    `id`           BIGINT  NOT NULL AUTO_INCREMENT COMMENT '记录编号',
    `spu_id`       BIGINT  NOT NULL DEFAULT 0 COMMENT '商品SPU编号',
    `user_id`      BIGINT  NOT NULL DEFAULT 0 COMMENT '用户编号',
    `user_deleted` BIT(1)  NOT NULL DEFAULT 0 COMMENT '用户是否删除',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`      VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`      VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '商品浏览记录';

-- ----------------------------
-- product_property 商品属性项
-- ----------------------------
CREATE TABLE IF NOT EXISTS `product_property` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '名称',
    `remark`      VARCHAR(256) DEFAULT '' COMMENT '备注',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '商品属性项';

-- ----------------------------
-- product_property_value 商品属性值
-- ----------------------------
CREATE TABLE IF NOT EXISTS `product_property_value` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `property_id` BIGINT       NOT NULL DEFAULT 0 COMMENT '属性项编号',
    `name`        VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '名称',
    `remark`      VARCHAR(256) DEFAULT '' COMMENT '备注',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '商品属性值';

-- ----------------------------
-- product_sku 商品SKU
-- ----------------------------
CREATE TABLE IF NOT EXISTS `product_sku` (
    `id`                      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '商品SKU编号',
    `spu_id`                  BIGINT       NOT NULL DEFAULT 0 COMMENT 'SPU编号',
    `properties`              JSON                  COMMENT '属性数组（JSON）',
    `price`                   INT          NOT NULL DEFAULT 0 COMMENT '商品价格（分）',
    `market_price`            INT                   COMMENT '市场价（分）',
    `cost_price`              INT          NOT NULL DEFAULT 0 COMMENT '成本价（分）',
    `bar_code`                VARCHAR(64)  DEFAULT '' COMMENT '商品条码',
    `pic_url`                 VARCHAR(512) NOT NULL DEFAULT '' COMMENT '图片地址',
    `stock`                   INT          NOT NULL DEFAULT 0 COMMENT '库存',
    `weight`                  DOUBLE                COMMENT '商品重量（kg）',
    `volume`                  DOUBLE                COMMENT '商品体积（m³）',
    `first_brokerage_price`   INT          NOT NULL DEFAULT 0 COMMENT '一级分销佣金（分）',
    `second_brokerage_price`  INT          NOT NULL DEFAULT 0 COMMENT '二级分销佣金（分）',
    `sales_count`             INT          NOT NULL DEFAULT 0 COMMENT '商品销量',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`                 VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                 VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                 BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '商品SKU';

-- ----------------------------
-- product_spu 商品SPU
-- ----------------------------
CREATE TABLE IF NOT EXISTS `product_spu` (
    `id`                   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '商品SPU编号',
    `name`                 VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '商品名称',
    `keyword`              VARCHAR(256)  DEFAULT '' COMMENT '关键字',
    `introduction`         VARCHAR(256)  DEFAULT '' COMMENT '商品简介',
    `description`          TEXT                   COMMENT '商品详情',
    `category_id`          BIGINT        NOT NULL DEFAULT 0 COMMENT '商品分类编号',
    `brand_id`             BIGINT        NOT NULL DEFAULT 0 COMMENT '商品品牌编号',
    `pic_url`              VARCHAR(512)  NOT NULL DEFAULT '' COMMENT '商品封面图',
    `slider_pic_urls`      JSON                   COMMENT '商品轮播图（JSON）',
    `sort`                 INT           NOT NULL DEFAULT 0 COMMENT '排序',
    `status`               INT           NOT NULL DEFAULT 0 COMMENT '商品状态',
    `spec_type`            BIT(1)        NOT NULL DEFAULT 0 COMMENT '规格类型（0=单规格 1=多规格）',
    `price`                INT           NOT NULL DEFAULT 0 COMMENT '商品价格（分）',
    `market_price`         INT                    COMMENT '市场价（分）',
    `cost_price`           INT           NOT NULL DEFAULT 0 COMMENT '成本价（分）',
    `stock`                INT           NOT NULL DEFAULT 0 COMMENT '库存',
    `delivery_types`       JSON                   COMMENT '配送方式数组（JSON）',
    `delivery_template_id` BIGINT                 COMMENT '物流配置模板编号',
    `give_integral`        INT           NOT NULL DEFAULT 0 COMMENT '赠送积分',
    `sub_commission_type`  BIT(1)        NOT NULL DEFAULT 0 COMMENT '分销类型（0=默认 1=自行设置）',
    `sales_count`          INT           NOT NULL DEFAULT 0 COMMENT '商品销量',
    `virtual_sales_count`  INT           NOT NULL DEFAULT 0 COMMENT '虚拟销量',
    `browse_count`         INT           NOT NULL DEFAULT 0 COMMENT '浏览量',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`              VARCHAR(64)   DEFAULT '' COMMENT '创建者',
    `create_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`              VARCHAR(64)   DEFAULT '' COMMENT '更新者',
    `update_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`              BIT(1)        NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '商品SPU';

-- ============================================================
-- promotion 模块
-- ============================================================

-- ----------------------------
-- promotion_article_category 文章分类
-- ----------------------------
CREATE TABLE IF NOT EXISTS `promotion_article_category` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '文章分类编号',
    `name`        VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '分类名称',
    `pic_url`     VARCHAR(512) NOT NULL DEFAULT '' COMMENT '图标地址',
    `status`      INT          NOT NULL DEFAULT 0 COMMENT '状态（0=开启 1=关闭）',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '文章分类';

-- ----------------------------
-- promotion_article 文章管理
-- ----------------------------
CREATE TABLE IF NOT EXISTS `promotion_article` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '文章编号',
    `category_id`      BIGINT        NOT NULL DEFAULT 0 COMMENT '分类编号',
    `spu_id`           BIGINT                 COMMENT '关联商品SPU编号',
    `title`            VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '文章标题',
    `author`           VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '文章作者',
    `pic_url`          VARCHAR(512)  NOT NULL DEFAULT '' COMMENT '文章封面图片地址',
    `introduction`     VARCHAR(256)  DEFAULT '' COMMENT '文章简介',
    `browse_count`     INT           NOT NULL DEFAULT 0 COMMENT '浏览次数',
    `sort`             INT           NOT NULL DEFAULT 0 COMMENT '排序',
    `status`           INT           NOT NULL DEFAULT 0 COMMENT '状态（0=开启 1=关闭）',
    `recommend_hot`    BIT(1)        NOT NULL DEFAULT 0 COMMENT '是否热门（小程序）',
    `recommend_banner` BIT(1)        NOT NULL DEFAULT 0 COMMENT '是否轮播图（小程序）',
    `content`          TEXT                   COMMENT '文章内容',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`          VARCHAR(64)   DEFAULT '' COMMENT '创建者',
    `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`          VARCHAR(64)   DEFAULT '' COMMENT '更新者',
    `update_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          BIT(1)        NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '文章管理';

-- ----------------------------
-- promotion_banner Banner
-- ----------------------------
CREATE TABLE IF NOT EXISTS `promotion_banner` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
    `title`        VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '标题',
    `url`          VARCHAR(512) NOT NULL DEFAULT '' COMMENT '跳转链接',
    `pic_url`      VARCHAR(512) NOT NULL DEFAULT '' COMMENT '图片链接',
    `sort`         INT          NOT NULL DEFAULT 0 COMMENT '排序',
    `status`       INT          NOT NULL DEFAULT 0 COMMENT '状态',
    `position`     INT          NOT NULL DEFAULT 0 COMMENT '定位',
    `memo`         VARCHAR(256) DEFAULT '' COMMENT '备注',
    `browse_count` INT          NOT NULL DEFAULT 0 COMMENT '点击次数',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`      VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`      VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = 'Banner';

-- ----------------------------
-- promotion_bargain_activity 砍价活动
-- ----------------------------
CREATE TABLE IF NOT EXISTS `promotion_bargain_activity` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '砍价活动编号',
    `name`                VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '砍价活动名称',
    `start_time`          DATETIME     NOT NULL COMMENT '活动开始时间',
    `end_time`            DATETIME     NOT NULL COMMENT '活动结束时间',
    `status`              INT          NOT NULL DEFAULT 0 COMMENT '活动状态',
    `spu_id`              BIGINT       NOT NULL DEFAULT 0 COMMENT '商品SPU编号',
    `sku_id`              BIGINT       NOT NULL DEFAULT 0 COMMENT '商品SKU编号',
    `bargain_first_price` INT          NOT NULL DEFAULT 0 COMMENT '砍价起始价格（分）',
    `bargain_min_price`   INT          NOT NULL DEFAULT 0 COMMENT '砍价底价（分）',
    `stock`               INT          NOT NULL DEFAULT 0 COMMENT '砍价库存',
    `total_stock`         INT          NOT NULL DEFAULT 0 COMMENT '砍价总库存',
    `help_max_count`      INT          NOT NULL DEFAULT 0 COMMENT '砍价人数',
    `bargain_count`       INT          NOT NULL DEFAULT 0 COMMENT '帮砍次数',
    `total_limit_count`   INT          NOT NULL DEFAULT 0 COMMENT '总限购数量',
    `random_min_price`    INT          NOT NULL DEFAULT 0 COMMENT '每次砍价最小金额（分）',
    `random_max_price`    INT          NOT NULL DEFAULT 0 COMMENT '每次砍价最大金额（分）',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`             VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`             VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '砍价活动';

-- ----------------------------
-- promotion_bargain_help 砍价助力
-- ----------------------------
CREATE TABLE IF NOT EXISTS `promotion_bargain_help` (
    `id`           BIGINT  NOT NULL AUTO_INCREMENT COMMENT '编号',
    `activity_id`  BIGINT  NOT NULL DEFAULT 0 COMMENT '砍价活动编号',
    `record_id`    BIGINT  NOT NULL DEFAULT 0 COMMENT '砍价记录编号',
    `user_id`      BIGINT  NOT NULL DEFAULT 0 COMMENT '用户编号',
    `reduce_price` INT     NOT NULL DEFAULT 0 COMMENT '减少价格（分）',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`      VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`      VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '砍价助力';

-- ----------------------------
-- promotion_bargain_record 砍价记录
-- ----------------------------
CREATE TABLE IF NOT EXISTS `promotion_bargain_record` (
    `id`                  BIGINT   NOT NULL AUTO_INCREMENT COMMENT '编号',
    `user_id`             BIGINT   NOT NULL DEFAULT 0 COMMENT '用户编号',
    `activity_id`         BIGINT   NOT NULL DEFAULT 0 COMMENT '砍价活动编号',
    `spu_id`              BIGINT   NOT NULL DEFAULT 0 COMMENT '商品SPU编号',
    `sku_id`              BIGINT   NOT NULL DEFAULT 0 COMMENT '商品SKU编号',
    `bargain_first_price` INT      NOT NULL DEFAULT 0 COMMENT '砍价起始价格（分）',
    `bargain_price`       INT      NOT NULL DEFAULT 0 COMMENT '当前砍价（分）',
    `status`              INT      NOT NULL DEFAULT 0 COMMENT '砍价状态',
    `end_time`            DATETIME NOT NULL COMMENT '结束时间',
    `order_id`            BIGINT            COMMENT '订单编号',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`             VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`             VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '砍价记录';

-- ----------------------------
-- promotion_combination_activity 拼团活动
-- ----------------------------
CREATE TABLE IF NOT EXISTS `promotion_combination_activity` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '活动编号',
    `name`                VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '拼团名称',
    `spu_id`              BIGINT       NOT NULL DEFAULT 0 COMMENT '商品SPU编号',
    `total_limit_count`   INT          NOT NULL DEFAULT 0 COMMENT '总限购数量',
    `single_limit_count`  INT          NOT NULL DEFAULT 0 COMMENT '单次限购数量',
    `start_time`          DATETIME     NOT NULL COMMENT '开始时间',
    `end_time`            DATETIME     NOT NULL COMMENT '结束时间',
    `user_size`           INT          NOT NULL DEFAULT 0 COMMENT '几人团',
    `virtual_group`       BIT(1)       NOT NULL DEFAULT 0 COMMENT '虚拟成团',
    `status`              INT          NOT NULL DEFAULT 0 COMMENT '活动状态',
    `limit_duration`      INT          NOT NULL DEFAULT 0 COMMENT '限制时长（小时）',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`             VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`             VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '拼团活动';

-- ----------------------------
-- promotion_combination_product 拼团商品
-- ----------------------------
CREATE TABLE IF NOT EXISTS `promotion_combination_product` (
    `id`                    BIGINT   NOT NULL AUTO_INCREMENT COMMENT '编号',
    `activity_id`           BIGINT   NOT NULL DEFAULT 0 COMMENT '拼团活动编号',
    `spu_id`                BIGINT   NOT NULL DEFAULT 0 COMMENT '商品SPU编号',
    `sku_id`                BIGINT   NOT NULL DEFAULT 0 COMMENT '商品SKU编号',
    `combination_price`     INT      NOT NULL DEFAULT 0 COMMENT '拼团价格（分）',
    `activity_status`       INT      NOT NULL DEFAULT 0 COMMENT '拼团商品状态',
    `activity_start_time`   DATETIME          COMMENT '活动开始时间（冗余）',
    `activity_end_time`     DATETIME          COMMENT '活动结束时间（冗余）',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`               VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '拼团商品';

-- ----------------------------
-- promotion_combination_record 拼团记录
-- ----------------------------
CREATE TABLE IF NOT EXISTS `promotion_combination_record` (
    `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '编号',
    `activity_id`       BIGINT        NOT NULL DEFAULT 0 COMMENT '拼团活动编号',
    `combination_price` INT           NOT NULL DEFAULT 0 COMMENT '拼团商品单价（分）',
    `spu_id`            BIGINT        NOT NULL DEFAULT 0 COMMENT 'SPU编号',
    `spu_name`          VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '商品名字',
    `pic_url`           VARCHAR(512)  NOT NULL DEFAULT '' COMMENT '商品图片',
    `sku_id`            BIGINT        NOT NULL DEFAULT 0 COMMENT 'SKU编号',
    `count`             INT           NOT NULL DEFAULT 0 COMMENT '购买商品数量',
    `user_id`           BIGINT        NOT NULL DEFAULT 0 COMMENT '用户编号',
    `nickname`          VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '用户昵称',
    `avatar`            VARCHAR(512)  NOT NULL DEFAULT '' COMMENT '用户头像',
    `head_id`           BIGINT        NOT NULL DEFAULT 0 COMMENT '团长编号',
    `status`            INT           NOT NULL DEFAULT 0 COMMENT '开团状态',
    `order_id`          BIGINT                 COMMENT '订单编号',
    `user_size`         INT           NOT NULL DEFAULT 0 COMMENT '开团需要人数',
    `user_count`        INT           NOT NULL DEFAULT 0 COMMENT '已加入拼团人数',
    `virtual_group`     BIT(1)        NOT NULL DEFAULT 0 COMMENT '是否虚拟成团',
    `expire_time`       DATETIME               COMMENT '过期时间',
    `start_time`        DATETIME               COMMENT '开始时间',
    `end_time`          DATETIME               COMMENT '结束时间',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`           VARCHAR(64)   DEFAULT '' COMMENT '创建者',
    `create_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`           VARCHAR(64)   DEFAULT '' COMMENT '更新者',
    `update_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           BIT(1)        NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '拼团记录';

-- ----------------------------
-- promotion_coupon 优惠劵
-- ----------------------------
CREATE TABLE IF NOT EXISTS `promotion_coupon` (
    `id`                    BIGINT   NOT NULL AUTO_INCREMENT COMMENT '优惠劵编号',
    `template_id`           BIGINT   NOT NULL DEFAULT 0 COMMENT '优惠劵模板编号',
    `name`                  VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '优惠劵名',
    `status`                INT      NOT NULL DEFAULT 0 COMMENT '优惠码状态',
    `user_id`               BIGINT   NOT NULL DEFAULT 0 COMMENT '用户编号',
    `take_type`             INT      NOT NULL DEFAULT 0 COMMENT '领取类型',
    `use_price`             INT      NOT NULL DEFAULT 0 COMMENT '满多少金额可用（分）',
    `valid_start_time`      DATETIME          COMMENT '生效开始时间',
    `valid_end_time`        DATETIME          COMMENT '生效结束时间',
    `product_scope`         INT      NOT NULL DEFAULT 0 COMMENT '商品范围',
    `product_scope_values`  JSON              COMMENT '商品范围编号数组（JSON）',
    `discount_type`         INT      NOT NULL DEFAULT 0 COMMENT '折扣类型',
    `discount_percent`      INT               COMMENT '折扣百分比',
    `discount_price`        INT               COMMENT '优惠金额（分）',
    `discount_limit_price`  INT               COMMENT '折扣上限（分）',
    `use_order_id`          BIGINT            COMMENT '使用订单号',
    `use_time`              DATETIME          COMMENT '使用时间',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`               VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '优惠劵';

-- ----------------------------
-- promotion_coupon_template 优惠劵模板
-- ----------------------------
CREATE TABLE IF NOT EXISTS `promotion_coupon_template` (
    `id`                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '模板编号',
    `name`                  VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '优惠劵名',
    `description`           VARCHAR(256)  DEFAULT '' COMMENT '优惠券说明',
    `status`                INT           NOT NULL DEFAULT 0 COMMENT '状态',
    `total_count`           INT           NOT NULL DEFAULT -1 COMMENT '发放数量（-1=不限制）',
    `take_limit_count`      INT           NOT NULL DEFAULT -1 COMMENT '每人限领个数（-1=不限制）',
    `take_type`             INT           NOT NULL DEFAULT 0 COMMENT '领取方式',
    `use_price`             INT           NOT NULL DEFAULT 0 COMMENT '满多少金额可用（分）',
    `product_scope`         INT           NOT NULL DEFAULT 0 COMMENT '商品范围',
    `product_scope_values`  JSON                   COMMENT '商品范围编号数组（JSON）',
    `validity_type`         INT           NOT NULL DEFAULT 0 COMMENT '生效日期类型',
    `valid_start_time`      DATETIME               COMMENT '固定日期-生效开始时间',
    `valid_end_time`        DATETIME               COMMENT '固定日期-生效结束时间',
    `fixed_start_term`      INT                    COMMENT '领取日期-开始天数',
    `fixed_end_term`        INT                    COMMENT '领取日期-结束天数',
    `discount_type`         INT           NOT NULL DEFAULT 0 COMMENT '折扣类型',
    `discount_percent`      INT                    COMMENT '折扣百分比',
    `discount_price`        INT                    COMMENT '优惠金额（分）',
    `discount_limit_price`  INT                    COMMENT '折扣上限（分）',
    `take_count`            INT           NOT NULL DEFAULT 0 COMMENT '领取优惠券数量',
    `use_count`             INT           NOT NULL DEFAULT 0 COMMENT '使用优惠券次数',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`               VARCHAR(64)   DEFAULT '' COMMENT '创建者',
    `create_time`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               VARCHAR(64)   DEFAULT '' COMMENT '更新者',
    `update_time`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               BIT(1)        NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '优惠劵模板';

-- ----------------------------
-- promotion_discount_activity 限时折扣活动
-- ----------------------------
CREATE TABLE IF NOT EXISTS `promotion_discount_activity` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '活动编号',
    `name`        VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '活动标题',
    `status`      INT          NOT NULL DEFAULT 0 COMMENT '状态',
    `start_time`  DATETIME     NOT NULL COMMENT '开始时间',
    `end_time`    DATETIME     NOT NULL COMMENT '结束时间',
    `remark`      VARCHAR(256) DEFAULT '' COMMENT '备注',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '限时折扣活动';

-- ----------------------------
-- promotion_discount_product 限时折扣商品
-- ----------------------------
CREATE TABLE IF NOT EXISTS `promotion_discount_product` (
    `id`                   BIGINT   NOT NULL AUTO_INCREMENT COMMENT '编号',
    `activity_id`          BIGINT   NOT NULL DEFAULT 0 COMMENT '限时折扣活动编号',
    `spu_id`               BIGINT   NOT NULL DEFAULT 0 COMMENT '商品SPU编号',
    `sku_id`               BIGINT   NOT NULL DEFAULT 0 COMMENT '商品SKU编号',
    `discount_type`        INT      NOT NULL DEFAULT 0 COMMENT '折扣类型',
    `discount_percent`     INT               COMMENT '折扣百分比',
    `discount_price`       INT               COMMENT '优惠金额（分）',
    `activity_name`        VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '活动标题（冗余）',
    `activity_status`      INT           NOT NULL DEFAULT 0 COMMENT '活动状态（冗余）',
    `activity_start_time`  DATETIME               COMMENT '活动开始时间（冗余）',
    `activity_end_time`    DATETIME               COMMENT '活动结束时间（冗余）',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`              VARCHAR(64)   DEFAULT '' COMMENT '创建者',
    `create_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`              VARCHAR(64)   DEFAULT '' COMMENT '更新者',
    `update_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`              BIT(1)        NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '限时折扣商品';

-- ----------------------------
-- promotion_diy_page 装修页面
-- ----------------------------
CREATE TABLE IF NOT EXISTS `promotion_diy_page` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '装修页面编号',
    `template_id`      BIGINT        NOT NULL DEFAULT 0 COMMENT '装修模板编号',
    `name`             VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '页面名称',
    `remark`           VARCHAR(256)  DEFAULT '' COMMENT '备注',
    `preview_pic_urls` JSON                   COMMENT '预览图（JSON）',
    `property`         TEXT                   COMMENT '页面属性（JSON格式）',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`          VARCHAR(64)   DEFAULT '' COMMENT '创建者',
    `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`          VARCHAR(64)   DEFAULT '' COMMENT '更新者',
    `update_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          BIT(1)        NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '装修页面';

-- ----------------------------
-- promotion_diy_template 装修模板
-- ----------------------------
CREATE TABLE IF NOT EXISTS `promotion_diy_template` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '装修模板编号',
    `name`             VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '模板名称',
    `used`             BIT(1)        NOT NULL DEFAULT 0 COMMENT '是否使用',
    `used_time`        DATETIME               COMMENT '使用时间',
    `remark`           VARCHAR(256)  DEFAULT '' COMMENT '备注',
    `preview_pic_urls` JSON                   COMMENT '预览图（JSON）',
    `property`         TEXT                   COMMENT 'uni-app底部导航属性（JSON格式）',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`          VARCHAR(64)   DEFAULT '' COMMENT '创建者',
    `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`          VARCHAR(64)   DEFAULT '' COMMENT '更新者',
    `update_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          BIT(1)        NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '装修模板';

-- ----------------------------
-- promotion_kefu_conversation 客服会话
-- ----------------------------
CREATE TABLE IF NOT EXISTS `promotion_kefu_conversation` (
    `id`                         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '编号',
    `user_id`                    BIGINT        NOT NULL DEFAULT 0 COMMENT '会话所属用户',
    `last_message_time`          DATETIME               COMMENT '最后聊天时间',
    `last_message_content`       VARCHAR(512)  DEFAULT '' COMMENT '最后聊天内容',
    `last_message_content_type`  INT                    COMMENT '最后发送消息类型',
    `admin_pinned`               BIT(1)        NOT NULL DEFAULT 0 COMMENT '管理端置顶',
    `user_deleted`               BIT(1)        NOT NULL DEFAULT 0 COMMENT '用户是否可见（true=不可见）',
    `admin_deleted`              BIT(1)        NOT NULL DEFAULT 0 COMMENT '管理员是否可见（true=不可见）',
    `admin_unread_message_count` INT           NOT NULL DEFAULT 0 COMMENT '管理员未读消息数',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`                    VARCHAR(64)   DEFAULT '' COMMENT '创建者',
    `create_time`                DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                    VARCHAR(64)   DEFAULT '' COMMENT '更新者',
    `update_time`                DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                    BIT(1)        NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '客服会话';

-- ----------------------------
-- promotion_kefu_message 客服消息
-- ----------------------------
CREATE TABLE IF NOT EXISTS `promotion_kefu_message` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '编号',
    `conversation_id` BIGINT       NOT NULL DEFAULT 0 COMMENT '会话编号',
    `sender_id`      BIGINT        NOT NULL DEFAULT 0 COMMENT '发送人编号',
    `sender_type`    INT           NOT NULL DEFAULT 0 COMMENT '发送人类型',
    `receiver_id`    BIGINT        NOT NULL DEFAULT 0 COMMENT '接收人编号',
    `receiver_type`  INT           NOT NULL DEFAULT 0 COMMENT '接收人类型',
    `content_type`   INT           NOT NULL DEFAULT 0 COMMENT '消息类型',
    `content`        VARCHAR(2048)          COMMENT '消息内容',
    `read_status`    BIT(1)        NOT NULL DEFAULT 0 COMMENT '是否已读',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`        VARCHAR(64)   DEFAULT '' COMMENT '创建者',
    `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`        VARCHAR(64)   DEFAULT '' COMMENT '更新者',
    `update_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        BIT(1)        NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '客服消息';

-- ----------------------------
-- promotion_point_activity 积分商城活动
-- ----------------------------
CREATE TABLE IF NOT EXISTS `promotion_point_activity` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '积分商城活动编号',
    `spu_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '积分商城活动商品',
    `status`      INT          NOT NULL DEFAULT 0 COMMENT '活动状态',
    `remark`      VARCHAR(256) DEFAULT '' COMMENT '备注',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序',
    `stock`       INT          NOT NULL DEFAULT 0 COMMENT '活动库存',
    `total_stock` INT          NOT NULL DEFAULT 0 COMMENT '活动总库存',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '积分商城活动';

-- ----------------------------
-- promotion_point_product 积分商城商品
-- ----------------------------
CREATE TABLE IF NOT EXISTS `promotion_point_product` (
    `id`              BIGINT  NOT NULL AUTO_INCREMENT COMMENT '积分商城商品编号',
    `activity_id`     BIGINT  NOT NULL DEFAULT 0 COMMENT '积分商城活动编号',
    `spu_id`          BIGINT  NOT NULL DEFAULT 0 COMMENT '商品SPU编号',
    `sku_id`          BIGINT  NOT NULL DEFAULT 0 COMMENT '商品SKU编号',
    `count`           INT     NOT NULL DEFAULT 0 COMMENT '可兑换次数',
    `point`           INT     NOT NULL DEFAULT 0 COMMENT '所需兑换积分',
    `price`           INT     NOT NULL DEFAULT 0 COMMENT '所需兑换金额（分）',
    `stock`           INT     NOT NULL DEFAULT 0 COMMENT '积分商城商品库存',
    `activity_status` INT     NOT NULL DEFAULT 0 COMMENT '积分商城商品状态',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`         VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`         VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '积分商城商品';

-- ----------------------------
-- promotion_reward_activity 满减送活动
-- ----------------------------
CREATE TABLE IF NOT EXISTS `promotion_reward_activity` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '活动编号',
    `name`                  VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '活动标题',
    `status`                INT          NOT NULL DEFAULT 0 COMMENT '状态',
    `start_time`            DATETIME     NOT NULL COMMENT '开始时间',
    `end_time`              DATETIME     NOT NULL COMMENT '结束时间',
    `remark`                VARCHAR(256) DEFAULT '' COMMENT '备注',
    `condition_type`        INT          NOT NULL DEFAULT 0 COMMENT '条件类型',
    `product_scope`         INT          NOT NULL DEFAULT 0 COMMENT '商品范围',
    `product_scope_values`  JSON                  COMMENT '商品SPU编号数组（JSON）',
    `rules`                 JSON                  COMMENT '优惠规则数组（JSON）',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`               VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '满减送活动';

-- ----------------------------
-- promotion_seckill_activity 秒杀活动
-- ----------------------------
CREATE TABLE IF NOT EXISTS `promotion_seckill_activity` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '秒杀活动编号',
    `spu_id`              BIGINT       NOT NULL DEFAULT 0 COMMENT '秒杀活动商品',
    `name`                VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '秒杀活动名称',
    `status`              INT          NOT NULL DEFAULT 0 COMMENT '活动状态',
    `remark`              VARCHAR(256) DEFAULT '' COMMENT '备注',
    `start_time`          DATETIME     NOT NULL COMMENT '活动开始时间',
    `end_time`            DATETIME     NOT NULL COMMENT '活动结束时间',
    `sort`                INT          NOT NULL DEFAULT 0 COMMENT '排序',
    `config_ids`          JSON                  COMMENT '秒杀时段ID数组（JSON）',
    `total_limit_count`   INT          NOT NULL DEFAULT 0 COMMENT '总限购数量',
    `single_limit_count`  INT          NOT NULL DEFAULT 0 COMMENT '单次限购数量',
    `stock`               INT          NOT NULL DEFAULT 0 COMMENT '秒杀库存',
    `total_stock`         INT          NOT NULL DEFAULT 0 COMMENT '秒杀总库存',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`             VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`             VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '秒杀活动';

-- ----------------------------
-- promotion_seckill_config 秒杀时段
-- ----------------------------
CREATE TABLE IF NOT EXISTS `promotion_seckill_config` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
    `name`             VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '秒杀时段名称',
    `start_time`       VARCHAR(8)   NOT NULL DEFAULT '' COMMENT '开始时间点（如 09:00）',
    `end_time`         VARCHAR(8)   NOT NULL DEFAULT '' COMMENT '结束时间点（如 10:00）',
    `slider_pic_urls`  JSON                  COMMENT '秒杀轮播图（JSON）',
    `status`           INT          NOT NULL DEFAULT 0 COMMENT '状态',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`          VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`          VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '秒杀时段';

-- ----------------------------
-- promotion_seckill_product 秒杀参与商品
-- ----------------------------
CREATE TABLE IF NOT EXISTS `promotion_seckill_product` (
    `id`                   BIGINT   NOT NULL AUTO_INCREMENT COMMENT '秒杀参与商品编号',
    `activity_id`          BIGINT   NOT NULL DEFAULT 0 COMMENT '秒杀活动编号',
    `config_ids`           JSON              COMMENT '秒杀时段ID数组（JSON）',
    `spu_id`               BIGINT   NOT NULL DEFAULT 0 COMMENT '商品SPU编号',
    `sku_id`               BIGINT   NOT NULL DEFAULT 0 COMMENT '商品SKU编号',
    `seckill_price`        INT      NOT NULL DEFAULT 0 COMMENT '秒杀金额（分）',
    `stock`                INT      NOT NULL DEFAULT 0 COMMENT '秒杀库存',
    `activity_status`      INT      NOT NULL DEFAULT 0 COMMENT '秒杀商品状态',
    `activity_start_time`  DATETIME          COMMENT '活动开始时间',
    `activity_end_time`    DATETIME          COMMENT '活动结束时间',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`              VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`              VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`              BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '秒杀参与商品';

-- ============================================================
-- statistics 模块
-- ============================================================

-- ----------------------------
-- product_statistics 商品统计
-- ----------------------------
CREATE TABLE IF NOT EXISTS `product_statistics` (
    `id`                       BIGINT  NOT NULL AUTO_INCREMENT COMMENT '编号',
    `time`                     DATE    NOT NULL COMMENT '统计日期',
    `spu_id`                   BIGINT  NOT NULL DEFAULT 0 COMMENT '商品SPU编号',
    `browse_count`             INT     NOT NULL DEFAULT 0 COMMENT '浏览量',
    `browse_user_count`        INT     NOT NULL DEFAULT 0 COMMENT '访客量',
    `favorite_count`           INT     NOT NULL DEFAULT 0 COMMENT '收藏数量',
    `cart_count`               INT     NOT NULL DEFAULT 0 COMMENT '加购数量',
    `order_count`              INT     NOT NULL DEFAULT 0 COMMENT '下单件数',
    `order_pay_count`          INT     NOT NULL DEFAULT 0 COMMENT '支付件数',
    `order_pay_price`          INT     NOT NULL DEFAULT 0 COMMENT '支付金额（分）',
    `after_sale_count`         INT     NOT NULL DEFAULT 0 COMMENT '退款件数',
    `after_sale_refund_price`  INT     NOT NULL DEFAULT 0 COMMENT '退款金额（分）',
    `browse_convert_percent`   INT     NOT NULL DEFAULT 0 COMMENT '访客支付转化率（百分比）',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`                  VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time`              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                  VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time`              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                  BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '商品统计';

-- ----------------------------
-- trade_statistics 交易统计
-- ----------------------------
CREATE TABLE IF NOT EXISTS `trade_statistics` (
    `id`                          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '编号',
    `time`                        DATETIME NOT NULL COMMENT '统计日期',
    `order_create_count`          INT      NOT NULL DEFAULT 0 COMMENT '创建订单数',
    `order_pay_count`             INT      NOT NULL DEFAULT 0 COMMENT '支付订单商品数',
    `order_pay_price`             INT      NOT NULL DEFAULT 0 COMMENT '总支付金额（分）',
    `after_sale_count`            INT      NOT NULL DEFAULT 0 COMMENT '退款订单数',
    `after_sale_refund_price`     INT      NOT NULL DEFAULT 0 COMMENT '总退款金额（分）',
    `brokerage_settlement_price`  INT      NOT NULL DEFAULT 0 COMMENT '佣金金额（已结算，分）',
    `wallet_pay_price`            INT      NOT NULL DEFAULT 0 COMMENT '总支付金额（余额，分）',
    `recharge_pay_count`          INT      NOT NULL DEFAULT 0 COMMENT '充值订单数',
    `recharge_pay_price`          INT      NOT NULL DEFAULT 0 COMMENT '充值金额（分）',
    `recharge_refund_count`       INT      NOT NULL DEFAULT 0 COMMENT '充值退款订单数',
    `recharge_refund_price`       INT      NOT NULL DEFAULT 0 COMMENT '充值退款金额（分）',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`                     VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time`                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                     VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time`                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                     BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '交易统计';

-- ============================================================
-- trade 模块
-- ============================================================

-- ----------------------------
-- trade_after_sale 售后订单
-- ----------------------------
CREATE TABLE IF NOT EXISTS `trade_after_sale` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '售后编号',
    `no`              VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '售后单号',
    `status`          INT           NOT NULL DEFAULT 0 COMMENT '退款状态',
    `way`             INT           NOT NULL DEFAULT 0 COMMENT '售后方式',
    `type`            INT           NOT NULL DEFAULT 0 COMMENT '售后类型',
    `user_id`         BIGINT        NOT NULL DEFAULT 0 COMMENT '用户编号',
    `apply_reason`    VARCHAR(256)  DEFAULT '' COMMENT '申请原因',
    `apply_description` VARCHAR(512) DEFAULT '' COMMENT '补充描述',
    `apply_pic_urls`  JSON                   COMMENT '补充凭证图片（JSON）',
    `order_id`        BIGINT        NOT NULL DEFAULT 0 COMMENT '交易订单编号',
    `order_no`        VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '订单流水号',
    `order_item_id`   BIGINT        NOT NULL DEFAULT 0 COMMENT '交易订单项编号',
    `spu_id`          BIGINT        NOT NULL DEFAULT 0 COMMENT '商品SPU编号',
    `spu_name`        VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '商品SPU名称',
    `sku_id`          BIGINT        NOT NULL DEFAULT 0 COMMENT '商品SKU编号',
    `properties`      JSON                   COMMENT '属性数组（JSON）',
    `pic_url`         VARCHAR(512)  NOT NULL DEFAULT '' COMMENT '商品图片',
    `count`           INT           NOT NULL DEFAULT 0 COMMENT '退货商品数量',
    `audit_time`      DATETIME               COMMENT '审批时间',
    `audit_user_id`   BIGINT                 COMMENT '审批人编号',
    `audit_reason`    VARCHAR(256)           COMMENT '审批备注',
    `refund_price`    INT           NOT NULL DEFAULT 0 COMMENT '退款金额（分）',
    `pay_refund_id`   BIGINT                 COMMENT '支付退款编号',
    `refund_time`     DATETIME               COMMENT '退款时间',
    `logistics_id`    BIGINT                 COMMENT '退货物流公司编号',
    `logistics_no`    VARCHAR(64)            COMMENT '退货物流单号',
    `delivery_time`   DATETIME               COMMENT '退货时间',
    `receive_time`    DATETIME               COMMENT '收货时间',
    `receive_reason`  VARCHAR(256)           COMMENT '收货备注',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`         VARCHAR(64)   DEFAULT '' COMMENT '创建者',
    `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`         VARCHAR(64)   DEFAULT '' COMMENT '更新者',
    `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         BIT(1)        NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '售后订单';

-- ----------------------------
-- trade_after_sale_log 交易售后日志
-- ----------------------------
CREATE TABLE IF NOT EXISTS `trade_after_sale_log` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '编号',
    `user_id`        BIGINT        NOT NULL DEFAULT 0 COMMENT '用户编号',
    `user_type`      INT           NOT NULL DEFAULT 0 COMMENT '用户类型',
    `after_sale_id`  BIGINT        NOT NULL DEFAULT 0 COMMENT '售后编号',
    `before_status`  INT           NOT NULL DEFAULT 0 COMMENT '操作前状态',
    `after_status`   INT           NOT NULL DEFAULT 0 COMMENT '操作后状态',
    `operate_type`   INT           NOT NULL DEFAULT 0 COMMENT '操作类型',
    `content`        VARCHAR(512)           COMMENT '操作明细',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`        VARCHAR(64)   DEFAULT '' COMMENT '创建者',
    `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`        VARCHAR(64)   DEFAULT '' COMMENT '更新者',
    `update_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        BIT(1)        NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '交易售后日志';

-- ----------------------------
-- trade_brokerage_record 佣金记录
-- ----------------------------
CREATE TABLE IF NOT EXISTS `trade_brokerage_record` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
    `user_id`           BIGINT       NOT NULL DEFAULT 0 COMMENT '用户编号',
    `biz_id`            VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '业务编号',
    `biz_type`          INT          NOT NULL DEFAULT 0 COMMENT '业务类型',
    `title`             VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '标题',
    `description`       VARCHAR(256) DEFAULT '' COMMENT '说明',
    `price`             INT          NOT NULL DEFAULT 0 COMMENT '金额（分）',
    `total_price`       INT          NOT NULL DEFAULT 0 COMMENT '当前总佣金（分）',
    `status`            INT          NOT NULL DEFAULT 0 COMMENT '状态',
    `frozen_days`       INT          NOT NULL DEFAULT 0 COMMENT '冻结时间（天）',
    `unfreeze_time`     DATETIME               COMMENT '解冻时间',
    `source_user_level` INT          NOT NULL DEFAULT 0 COMMENT '来源用户等级',
    `source_user_id`    BIGINT                 COMMENT '来源用户编号',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`           VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`           VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '佣金记录';

-- ----------------------------
-- trade_brokerage_user 分销用户
-- ----------------------------
CREATE TABLE IF NOT EXISTS `trade_brokerage_user` (
    `id`                BIGINT   NOT NULL AUTO_INCREMENT COMMENT '用户编号',
    `bind_user_id`      BIGINT            COMMENT '推广员编号',
    `bind_user_time`    DATETIME          COMMENT '推广员绑定时间',
    `brokerage_enabled` BIT(1)   NOT NULL DEFAULT 0 COMMENT '是否有分销资格',
    `brokerage_time`    DATETIME          COMMENT '成为分销员时间',
    `brokerage_price`   INT      NOT NULL DEFAULT 0 COMMENT '可用佣金（分）',
    `frozen_price`      INT      NOT NULL DEFAULT 0 COMMENT '冻结佣金（分）',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`           VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`           VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '分销用户';

-- ----------------------------
-- trade_brokerage_withdraw 佣金提现
-- ----------------------------
CREATE TABLE IF NOT EXISTS `trade_brokerage_withdraw` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
    `user_id`               BIGINT       NOT NULL DEFAULT 0 COMMENT '用户编号',
    `price`                 INT          NOT NULL DEFAULT 0 COMMENT '提现金额（分）',
    `fee_price`             INT          NOT NULL DEFAULT 0 COMMENT '提现手续费（分）',
    `total_price`           INT          NOT NULL DEFAULT 0 COMMENT '当前总佣金（分）',
    `type`                  INT          NOT NULL DEFAULT 0 COMMENT '提现类型',
    `user_name`             VARCHAR(64)  DEFAULT '' COMMENT '提现姓名',
    `user_account`          VARCHAR(128) DEFAULT '' COMMENT '提现账号',
    `qr_code_url`           VARCHAR(512) DEFAULT '' COMMENT '收款码',
    `bank_name`             VARCHAR(64)  DEFAULT '' COMMENT '银行名称',
    `bank_address`          VARCHAR(256) DEFAULT '' COMMENT '开户地址',
    `status`                INT          NOT NULL DEFAULT 0 COMMENT '状态',
    `audit_reason`          VARCHAR(256)          COMMENT '审核驳回原因',
    `audit_time`            DATETIME               COMMENT '审核时间',
    `remark`                VARCHAR(256)           COMMENT '备注',
    `pay_transfer_id`       BIGINT                 COMMENT '转账单编号',
    `transfer_channel_code` VARCHAR(64)            COMMENT '转账渠道',
    `transfer_time`         DATETIME               COMMENT '转账成功时间',
    `transfer_error_msg`    VARCHAR(256)           COMMENT '转账错误提示',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`               VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '佣金提现';

-- ----------------------------
-- trade_cart 购物车
-- ----------------------------
CREATE TABLE IF NOT EXISTS `trade_cart` (
    `id`          BIGINT  NOT NULL AUTO_INCREMENT COMMENT '编号',
    `user_id`     BIGINT  NOT NULL DEFAULT 0 COMMENT '用户编号',
    `spu_id`      BIGINT  NOT NULL DEFAULT 0 COMMENT '商品SPU编号',
    `sku_id`      BIGINT  NOT NULL DEFAULT 0 COMMENT '商品SKU编号',
    `count`       INT     NOT NULL DEFAULT 0 COMMENT '商品购买数量',
    `selected`    BIT(1)  NOT NULL DEFAULT 1 COMMENT '是否选中',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '购物车';

-- ----------------------------
-- trade_config 交易中心配置
-- ----------------------------
CREATE TABLE IF NOT EXISTS `trade_config` (
    `id`                              BIGINT   NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `after_sale_refund_reasons`       JSON              COMMENT '售后退款理由（JSON）',
    `after_sale_return_reasons`       JSON              COMMENT '售后退货理由（JSON）',
    `delivery_express_free_enabled`   BIT(1)   NOT NULL DEFAULT 0 COMMENT '是否启用全场包邮',
    `delivery_express_free_price`     INT               COMMENT '全场包邮最小金额（分）',
    `delivery_pick_up_enabled`        BIT(1)   NOT NULL DEFAULT 0 COMMENT '是否开启自提',
    `brokerage_enabled`               BIT(1)   NOT NULL DEFAULT 0 COMMENT '是否启用分佣',
    `brokerage_enabled_condition`     INT               COMMENT '分佣模式',
    `brokerage_bind_mode`             INT               COMMENT '分销关系绑定模式',
    `brokerage_poster_urls`           JSON              COMMENT '分销海报图地址数组（JSON）',
    `brokerage_first_percent`         INT      NOT NULL DEFAULT 0 COMMENT '一级返佣比例',
    `brokerage_second_percent`        INT      NOT NULL DEFAULT 0 COMMENT '二级返佣比例',
    `brokerage_withdraw_min_price`    INT      NOT NULL DEFAULT 0 COMMENT '用户提现最低金额（分）',
    `brokerage_withdraw_fee_percent`  INT      NOT NULL DEFAULT 0 COMMENT '用户提现手续费百分比',
    `brokerage_frozen_days`           INT      NOT NULL DEFAULT 0 COMMENT '佣金冻结时间（天）',
    `brokerage_withdraw_types`        JSON              COMMENT '提现方式（JSON）',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`                         VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time`                     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                         VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time`                     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                         BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '交易中心配置';

-- ----------------------------
-- trade_delivery_express 快递公司
-- ----------------------------
CREATE TABLE IF NOT EXISTS `trade_delivery_express` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
    `code`        VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '快递公司code',
    `name`        VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '快递公司名称',
    `logo`        VARCHAR(512) NOT NULL DEFAULT '' COMMENT '快递公司logo',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序',
    `status`      INT          NOT NULL DEFAULT 0 COMMENT '状态',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '快递公司';

-- ----------------------------
-- trade_delivery_express_template 快递运费模板
-- ----------------------------
CREATE TABLE IF NOT EXISTS `trade_delivery_express_template` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '编号',
    `name`        VARCHAR(64) NOT NULL DEFAULT '' COMMENT '模板名称',
    `charge_mode` INT         NOT NULL DEFAULT 0 COMMENT '配送计费方式',
    `sort`        INT         NOT NULL DEFAULT 0 COMMENT '排序',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '快递运费模板';

-- ----------------------------
-- trade_delivery_express_template_charge 快递运费模板计费配置
-- ----------------------------
CREATE TABLE IF NOT EXISTS `trade_delivery_express_template_charge` (
    `id`          BIGINT  NOT NULL AUTO_INCREMENT COMMENT '编号',
    `template_id` BIGINT  NOT NULL DEFAULT 0 COMMENT '配送模板编号',
    `area_ids`    JSON             COMMENT '配送区域编号列表（JSON）',
    `charge_mode` INT     NOT NULL DEFAULT 0 COMMENT '配送计费方式',
    `start_count` DOUBLE  NOT NULL DEFAULT 0 COMMENT '首件数量',
    `start_price` INT     NOT NULL DEFAULT 0 COMMENT '起步价（分）',
    `extra_count` DOUBLE  NOT NULL DEFAULT 0 COMMENT '续件数量',
    `extra_price` INT     NOT NULL DEFAULT 0 COMMENT '额外价（分）',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '快递运费模板计费配置';

-- ----------------------------
-- trade_delivery_express_template_free 快递运费模板包邮配置
-- ----------------------------
CREATE TABLE IF NOT EXISTS `trade_delivery_express_template_free` (
    `id`          BIGINT  NOT NULL AUTO_INCREMENT COMMENT '编号',
    `template_id` BIGINT  NOT NULL DEFAULT 0 COMMENT '配送模板编号',
    `area_ids`    JSON             COMMENT '配送区域编号列表（JSON）',
    `free_price`  INT     NOT NULL DEFAULT 0 COMMENT '包邮金额（分）',
    `free_count`  INT     NOT NULL DEFAULT 0 COMMENT '包邮件数',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`     VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '快递运费模板包邮配置';

-- ----------------------------
-- trade_delivery_pick_up_store 自提门店
-- ----------------------------
CREATE TABLE IF NOT EXISTS `trade_delivery_pick_up_store` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
    `name`             VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '门店名称',
    `introduction`     VARCHAR(256) DEFAULT '' COMMENT '门店简介',
    `phone`            VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '门店手机',
    `area_id`          INT          NOT NULL DEFAULT 0 COMMENT '区域编号',
    `detail_address`   VARCHAR(256) NOT NULL DEFAULT '' COMMENT '门店详细地址',
    `logo`             VARCHAR(512) NOT NULL DEFAULT '' COMMENT '门店logo',
    `opening_time`     TIME         NOT NULL COMMENT '营业开始时间',
    `closing_time`     TIME         NOT NULL COMMENT '营业结束时间',
    `latitude`         DOUBLE                COMMENT '纬度',
    `longitude`        DOUBLE                COMMENT '经度',
    `verify_user_ids`  JSON                  COMMENT '核销员工用户编号数组（JSON）',
    `status`           INT          NOT NULL DEFAULT 0 COMMENT '门店状态',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`          VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`          VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '自提门店';

-- ----------------------------
-- trade_order 交易订单
-- ----------------------------
CREATE TABLE IF NOT EXISTS `trade_order` (
    `id`                        BIGINT        NOT NULL AUTO_INCREMENT COMMENT '订单编号',
    `no`                        VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '订单流水号',
    `type`                      INT           NOT NULL DEFAULT 0 COMMENT '订单类型',
    `terminal`                  INT           NOT NULL DEFAULT 0 COMMENT '订单来源',
    `user_id`                   BIGINT        NOT NULL DEFAULT 0 COMMENT '用户编号',
    `user_ip`                   VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '用户IP',
    `user_remark`               VARCHAR(256)  DEFAULT '' COMMENT '用户备注',
    `status`                    INT           NOT NULL DEFAULT 0 COMMENT '订单状态',
    `product_count`             INT           NOT NULL DEFAULT 0 COMMENT '购买商品数量',
    `finish_time`               DATETIME               COMMENT '订单完成时间',
    `cancel_time`               DATETIME               COMMENT '订单取消时间',
    `cancel_type`               INT                    COMMENT '取消类型',
    `remark`                    VARCHAR(256)  DEFAULT '' COMMENT '商家备注',
    `comment_status`            BIT(1)        NOT NULL DEFAULT 0 COMMENT '是否评价',
    `brokerage_user_id`         BIGINT                 COMMENT '推广人编号',
    `pay_order_id`              BIGINT                 COMMENT '支付订单编号',
    `pay_status`                BIT(1)        NOT NULL DEFAULT 0 COMMENT '是否已支付',
    `pay_time`                  DATETIME               COMMENT '付款时间',
    `pay_channel_code`          VARCHAR(64)            COMMENT '支付渠道',
    `total_price`               INT           NOT NULL DEFAULT 0 COMMENT '商品原价（分）',
    `discount_price`            INT           NOT NULL DEFAULT 0 COMMENT '优惠金额（分）',
    `delivery_price`            INT           NOT NULL DEFAULT 0 COMMENT '运费金额（分）',
    `adjust_price`              INT           NOT NULL DEFAULT 0 COMMENT '订单调价（分）',
    `pay_price`                 INT           NOT NULL DEFAULT 0 COMMENT '应付金额（分）',
    `delivery_type`             INT                    COMMENT '配送方式',
    `logistics_id`              BIGINT                 COMMENT '发货物流公司编号',
    `logistics_no`              VARCHAR(64)            COMMENT '发货物流单号',
    `delivery_time`             DATETIME               COMMENT '发货时间',
    `receive_time`              DATETIME               COMMENT '收货时间',
    `receiver_name`             VARCHAR(64)            COMMENT '收件人名称',
    `receiver_mobile`           VARCHAR(32)            COMMENT '收件人手机',
    `receiver_area_id`          INT                    COMMENT '收件人地区编号',
    `receiver_detail_address`   VARCHAR(256)           COMMENT '收件人详细地址',
    `pick_up_store_id`          BIGINT                 COMMENT '自提门店编号',
    `pick_up_verify_code`       VARCHAR(64)            COMMENT '自提核销码',
    `refund_status`             INT           NOT NULL DEFAULT 0 COMMENT '售后状态',
    `refund_price`              INT           NOT NULL DEFAULT 0 COMMENT '退款金额（分）',
    `coupon_id`                 BIGINT                 COMMENT '优惠劵编号',
    `coupon_price`              INT           NOT NULL DEFAULT 0 COMMENT '优惠劵减免金额（分）',
    `use_point`                 INT           NOT NULL DEFAULT 0 COMMENT '使用的积分',
    `point_price`               INT           NOT NULL DEFAULT 0 COMMENT '积分抵扣金额（分）',
    `give_point`                INT           NOT NULL DEFAULT 0 COMMENT '赠送的积分',
    `refund_point`              INT           NOT NULL DEFAULT 0 COMMENT '退还的积分',
    `vip_price`                 INT           NOT NULL DEFAULT 0 COMMENT 'VIP减免金额（分）',
    `give_coupon_template_counts` JSON                 COMMENT '赠送的优惠劵（JSON）',
    `give_coupon_ids`           JSON                   COMMENT '赠送的优惠劵编号（JSON）',
    `seckill_activity_id`       BIGINT                 COMMENT '秒杀活动编号',
    `bargain_activity_id`       BIGINT                 COMMENT '砍价活动编号',
    `bargain_record_id`         BIGINT                 COMMENT '砍价记录编号',
    `combination_activity_id`   BIGINT                 COMMENT '拼团活动编号',
    `combination_head_id`       BIGINT                 COMMENT '拼团团长编号',
    `combination_record_id`     BIGINT                 COMMENT '拼团记录编号',
    `point_activity_id`         BIGINT                 COMMENT '积分商城活动编号',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`                   VARCHAR(64)   DEFAULT '' COMMENT '创建者',
    `create_time`               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                   VARCHAR(64)   DEFAULT '' COMMENT '更新者',
    `update_time`               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                   BIT(1)        NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '交易订单';

-- ----------------------------
-- trade_order_item 交易订单项
-- ----------------------------
CREATE TABLE IF NOT EXISTS `trade_order_item` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '编号',
    `user_id`          BIGINT        NOT NULL DEFAULT 0 COMMENT '用户编号',
    `order_id`         BIGINT        NOT NULL DEFAULT 0 COMMENT '订单编号',
    `cart_id`          BIGINT                 COMMENT '购物车项编号',
    `spu_id`           BIGINT        NOT NULL DEFAULT 0 COMMENT '商品SPU编号',
    `spu_name`         VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '商品SPU名称',
    `sku_id`           BIGINT        NOT NULL DEFAULT 0 COMMENT '商品SKU编号',
    `properties`       JSON                   COMMENT '属性数组（JSON）',
    `pic_url`          VARCHAR(512)  NOT NULL DEFAULT '' COMMENT '商品图片',
    `count`            INT           NOT NULL DEFAULT 0 COMMENT '购买数量',
    `comment_status`   BIT(1)        NOT NULL DEFAULT 0 COMMENT '是否评价',
    `price`            INT           NOT NULL DEFAULT 0 COMMENT '商品原价（单，分）',
    `discount_price`   INT           NOT NULL DEFAULT 0 COMMENT '优惠金额（总，分）',
    `delivery_price`   INT           NOT NULL DEFAULT 0 COMMENT '运费金额（总，分）',
    `adjust_price`     INT           NOT NULL DEFAULT 0 COMMENT '订单调价（总，分）',
    `pay_price`        INT           NOT NULL DEFAULT 0 COMMENT '应付金额（总，分）',
    `coupon_price`     INT           NOT NULL DEFAULT 0 COMMENT '优惠劵减免金额（分）',
    `point_price`      INT           NOT NULL DEFAULT 0 COMMENT '积分抵扣金额（分）',
    `use_point`        INT           NOT NULL DEFAULT 0 COMMENT '使用的积分',
    `give_point`       INT           NOT NULL DEFAULT 0 COMMENT '赠送的积分',
    `vip_price`        INT           NOT NULL DEFAULT 0 COMMENT 'VIP减免金额（分）',
    `after_sale_id`    BIGINT                 COMMENT '售后单编号',
    `after_sale_status` INT          NOT NULL DEFAULT 0 COMMENT '售后状态',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`          VARCHAR(64)   DEFAULT '' COMMENT '创建者',
    `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`          VARCHAR(64)   DEFAULT '' COMMENT '更新者',
    `update_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          BIT(1)        NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '交易订单项';

-- ----------------------------
-- trade_order_log 订单日志
-- ----------------------------
CREATE TABLE IF NOT EXISTS `trade_order_log` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '编号',
    `user_id`        BIGINT       NOT NULL DEFAULT 0 COMMENT '用户编号',
    `user_type`      INT          NOT NULL DEFAULT 0 COMMENT '用户类型',
    `order_id`       BIGINT       NOT NULL DEFAULT 0 COMMENT '订单号',
    `before_status`  INT          NOT NULL DEFAULT 0 COMMENT '操作前状态',
    `after_status`   INT          NOT NULL DEFAULT 0 COMMENT '操作后状态',
    `operate_type`   INT          NOT NULL DEFAULT 0 COMMENT '操作类型',
    `content`        VARCHAR(512)          COMMENT '订单日志信息',
    -- 基础字段
    `tenant_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator`        VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`        VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        BIT(1)       NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '订单日志';

SET FOREIGN_KEY_CHECKS = 1;
