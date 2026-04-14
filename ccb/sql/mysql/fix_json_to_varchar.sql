-- 修复：将使用 IntegerListTypeHandler/LongListTypeHandler 的字段从 JSON 改为 VARCHAR
-- 这些字段存储逗号分隔的字符串（如 "1,2,3"），不是合法 JSON

-- product_spu.delivery_types
ALTER TABLE `product_spu` MODIFY COLUMN `delivery_types` VARCHAR(256) DEFAULT '' COMMENT '配送方式数组';

-- trade_delivery_express_template_charge.area_ids
ALTER TABLE `trade_delivery_express_template_charge` MODIFY COLUMN `area_ids` VARCHAR(10240) DEFAULT '' COMMENT '配送区域编号列表';

-- trade_delivery_express_template_free.area_ids
ALTER TABLE `trade_delivery_express_template_free` MODIFY COLUMN `area_ids` VARCHAR(10240) DEFAULT '' COMMENT '配送区域编号列表';

-- trade_delivery_pick_up_store.verify_user_ids
ALTER TABLE `trade_delivery_pick_up_store` MODIFY COLUMN `verify_user_ids` VARCHAR(10240) DEFAULT '' COMMENT '核销管理员编号数组';

-- trade_config.brokerage_withdraw_types
ALTER TABLE `trade_config` MODIFY COLUMN `brokerage_withdraw_types` VARCHAR(256) DEFAULT '' COMMENT '佣金提现方式';

-- trade_order.give_coupon_ids
ALTER TABLE `trade_order` MODIFY COLUMN `give_coupon_ids` VARCHAR(2048) DEFAULT '' COMMENT '赠送优惠券编号数组';

-- promotion_reward_activity.product_scope_values
ALTER TABLE `promotion_reward_activity` MODIFY COLUMN `product_scope_values` VARCHAR(10240) DEFAULT '' COMMENT '商品范围编号数组';

-- promotion_seckill_activity.config_ids
ALTER TABLE `promotion_seckill_activity` MODIFY COLUMN `config_ids` VARCHAR(512) DEFAULT '' COMMENT '秒杀时段id';

-- promotion_seckill_product.config_ids
ALTER TABLE `promotion_seckill_product` MODIFY COLUMN `config_ids` VARCHAR(512) DEFAULT '' COMMENT '秒杀时段id';

-- promotion_coupon_template.product_scope_values
ALTER TABLE `promotion_coupon_template` MODIFY COLUMN `product_scope_values` VARCHAR(10240) DEFAULT '' COMMENT '商品范围编号数组';

-- promotion_coupon.product_scope_values
ALTER TABLE `promotion_coupon` MODIFY COLUMN `product_scope_values` VARCHAR(10240) DEFAULT '' COMMENT '商品范围编号数组';

-- member_user.tag_ids
ALTER TABLE `member_user` MODIFY COLUMN `tag_ids` VARCHAR(512) DEFAULT '' COMMENT '会员标签列表';

-- mp_user.tag_ids
ALTER TABLE `mp_user` MODIFY COLUMN `tag_ids` VARCHAR(512) DEFAULT '[]' COMMENT '标签编号数组';
