-- 删除所有 mall/member/pay/mp 模块的表，然后重新导入

-- member 模块
DROP TABLE IF EXISTS `member_address`;
DROP TABLE IF EXISTS `member_config`;
DROP TABLE IF EXISTS `member_experience_record`;
DROP TABLE IF EXISTS `member_group`;
DROP TABLE IF EXISTS `member_level`;
DROP TABLE IF EXISTS `member_level_record`;
DROP TABLE IF EXISTS `member_point_record`;
DROP TABLE IF EXISTS `member_sign_in_config`;
DROP TABLE IF EXISTS `member_sign_in_record`;
DROP TABLE IF EXISTS `member_tag`;
DROP TABLE IF EXISTS `member_user`;

-- mp 模块
DROP TABLE IF EXISTS `mp_account`;
DROP TABLE IF EXISTS `mp_auto_reply`;
DROP TABLE IF EXISTS `mp_material`;
DROP TABLE IF EXISTS `mp_menu`;
DROP TABLE IF EXISTS `mp_message`;
DROP TABLE IF EXISTS `mp_message_template`;
DROP TABLE IF EXISTS `mp_tag`;
DROP TABLE IF EXISTS `mp_user`;

-- pay 模块
DROP TABLE IF EXISTS `pay_app`;
DROP TABLE IF EXISTS `pay_channel`;
DROP TABLE IF EXISTS `pay_demo_order`;
DROP TABLE IF EXISTS `pay_demo_withdraw`;
DROP TABLE IF EXISTS `pay_notify_log`;
DROP TABLE IF EXISTS `pay_notify_task`;
DROP TABLE IF EXISTS `pay_order`;
DROP TABLE IF EXISTS `pay_order_extension`;
DROP TABLE IF EXISTS `pay_refund`;
DROP TABLE IF EXISTS `pay_transfer`;
DROP TABLE IF EXISTS `pay_wallet`;
DROP TABLE IF EXISTS `pay_wallet_recharge`;
DROP TABLE IF EXISTS `pay_wallet_recharge_package`;
DROP TABLE IF EXISTS `pay_wallet_transaction`;

-- product 模块
DROP TABLE IF EXISTS `product_brand`;
DROP TABLE IF EXISTS `product_browse_history`;
DROP TABLE IF EXISTS `product_category`;
DROP TABLE IF EXISTS `product_comment`;
DROP TABLE IF EXISTS `product_favorite`;
DROP TABLE IF EXISTS `product_property`;
DROP TABLE IF EXISTS `product_property_value`;
DROP TABLE IF EXISTS `product_sku`;
DROP TABLE IF EXISTS `product_spu`;
DROP TABLE IF EXISTS `product_statistics`;

-- promotion 模块
DROP TABLE IF EXISTS `promotion_article`;
DROP TABLE IF EXISTS `promotion_article_category`;
DROP TABLE IF EXISTS `promotion_banner`;
DROP TABLE IF EXISTS `promotion_bargain_activity`;
DROP TABLE IF EXISTS `promotion_bargain_help`;
DROP TABLE IF EXISTS `promotion_bargain_record`;
DROP TABLE IF EXISTS `promotion_combination_activity`;
DROP TABLE IF EXISTS `promotion_combination_product`;
DROP TABLE IF EXISTS `promotion_combination_record`;
DROP TABLE IF EXISTS `promotion_coupon`;
DROP TABLE IF EXISTS `promotion_coupon_template`;
DROP TABLE IF EXISTS `promotion_discount_activity`;
DROP TABLE IF EXISTS `promotion_discount_product`;
DROP TABLE IF EXISTS `promotion_diy_page`;
DROP TABLE IF EXISTS `promotion_diy_template`;
DROP TABLE IF EXISTS `promotion_kefu_conversation`;
DROP TABLE IF EXISTS `promotion_kefu_message`;
DROP TABLE IF EXISTS `promotion_point_activity`;
DROP TABLE IF EXISTS `promotion_point_product`;
DROP TABLE IF EXISTS `promotion_reward_activity`;
DROP TABLE IF EXISTS `promotion_seckill_activity`;
DROP TABLE IF EXISTS `promotion_seckill_config`;
DROP TABLE IF EXISTS `promotion_seckill_product`;

-- trade 模块
DROP TABLE IF EXISTS `trade_after_sale`;
DROP TABLE IF EXISTS `trade_after_sale_log`;
DROP TABLE IF EXISTS `trade_brokerage_record`;
DROP TABLE IF EXISTS `trade_brokerage_user`;
DROP TABLE IF EXISTS `trade_brokerage_withdraw`;
DROP TABLE IF EXISTS `trade_cart`;
DROP TABLE IF EXISTS `trade_config`;
DROP TABLE IF EXISTS `trade_delivery_express`;
DROP TABLE IF EXISTS `trade_delivery_express_template`;
DROP TABLE IF EXISTS `trade_delivery_express_template_charge`;
DROP TABLE IF EXISTS `trade_delivery_express_template_free`;
DROP TABLE IF EXISTS `trade_delivery_pick_up_store`;
DROP TABLE IF EXISTS `trade_order`;
DROP TABLE IF EXISTS `trade_order_item`;
DROP TABLE IF EXISTS `trade_order_log`;
DROP TABLE IF EXISTS `trade_statistics`;
