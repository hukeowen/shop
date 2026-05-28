#!/bin/bash
M="mysql -uroot -pCHANGE_ME_5201314jxs@qq.com -D ruoyi-vue-pro -t"
echo "===== users by mobile (匹配 1811111111*) ====="
$M -e "SELECT id, mobile, nickname, tenant_id, create_time FROM member_user WHERE mobile LIKE '1811111111%' ORDER BY create_time"

echo
echo "===== shop_user_referral 这两人的上下级 ====="
$M -e "SELECT id, user_id, parent_user_id, tenant_id, bound_at, bound_order_id FROM shop_user_referral WHERE user_id IN (SELECT id FROM member_user WHERE mobile LIKE '1811111111%') OR parent_user_id IN (SELECT id FROM member_user WHERE mobile LIKE '1811111111%') ORDER BY id"

echo
echo "===== merchant 合规店铺 ====="
$M -e "SELECT id, name, tenant_id FROM merchant_info WHERE name LIKE '%合规%' OR name LIKE '%合%规%'"

echo
echo "===== shop_info 合规店铺 ====="
$M -e "SELECT id, tenant_id, shop_name FROM shop_info WHERE shop_name LIKE '%合规%'"

echo
echo "===== member_shop_rel 这两人的入店记录 ====="
$M -e "SELECT id, user_id, tenant_id, referrer_user_id, balance, points, create_time FROM member_shop_rel WHERE user_id IN (SELECT id FROM member_user WHERE mobile LIKE '1811111111%')"

echo
echo "===== trade_order 这两人下单情况 ====="
$M -e "SELECT id, tenant_id, user_id, status, pay_status, pay_price, create_time FROM trade_order WHERE user_id IN (SELECT id FROM member_user WHERE mobile LIKE '1811111111%') ORDER BY id"
