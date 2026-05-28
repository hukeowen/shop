#!/bin/bash
M="mysql -uroot -pCHANGE_ME_5201314jxs@qq.com -D ruoyi-vue-pro -t"
echo "===== user 17000000002 orders ====="
$M -e "SELECT id, tenant_id, user_id, status, pay_status, total_price, pay_price, create_time, finish_time, pay_time FROM trade_order WHERE user_id IN (SELECT id FROM member_user WHERE mobile='17000000002') ORDER BY id"
echo
echo "===== order items ====="
$M -e "SELECT oi.id, oi.order_id, oi.spu_id, oi.spu_name, oi.count, oi.price, oi.pay_price FROM trade_order_item oi JOIN trade_order o ON oi.order_id=o.id WHERE o.user_id IN (SELECT id FROM member_user WHERE mobile='17000000002')"
echo
echo "===== promo config (consume_point_ratio) ====="
$M -e "SELECT spu_id, tenant_id, consume_point_ratio, tuijian_enabled, tuijian_n, tuijian_ratios FROM product_promo_config WHERE spu_id IN (SELECT spu_id FROM trade_order_item oi JOIN trade_order o ON oi.order_id=o.id WHERE o.user_id IN (SELECT id FROM member_user WHERE mobile='17000000002'))"
echo
echo "===== shop_consume_point_record ====="
$M -e "SELECT id, user_id, tenant_id, source_type, source_id, amount, balance_after, remark, create_time FROM shop_consume_point_record WHERE user_id IN (SELECT id FROM member_user WHERE mobile='17000000002') ORDER BY id"
echo
echo "===== shop_promo_record ====="
$M -e "SELECT id, user_id, tenant_id, source_type, source_id, amount, balance_after, remark, create_time FROM shop_promo_record WHERE user_id IN (SELECT id FROM member_user WHERE mobile='17000000002') ORDER BY id"
echo
echo "===== shop_user_star ====="
$M -e "SELECT id, user_id, tenant_id, spu_id, current_star, promo_point_balance, consume_point_balance FROM shop_user_star WHERE user_id IN (SELECT id FROM member_user WHERE mobile='17000000002')"
echo
echo "===== shop_promo_deduction_record ====="
$M -e "SELECT id, order_id, user_id, spu_id, unit_price, total_count, produced_amount, deduct_count, actual_paid FROM shop_promo_deduction_record WHERE user_id IN (SELECT id FROM member_user WHERE mobile='17000000002')"
