#!/bin/bash
M="mysql -uroot -pCHANGE_ME_5201314jxs@qq.com -D ruoyi-vue-pro -t"

echo "===== shop_user_star (两人在 tenant=1010 的星级) ====="
$M -e "SELECT id, user_id, tenant_id, spu_id, current_star, direct_count, team_sales_count, team_sales_amount, promo_point_balance, consume_point_balance FROM shop_user_star WHERE user_id IN (99017, 99021) ORDER BY user_id, spu_id"

echo
echo "===== shop_queue_position (该店两人在哪些 SPU 激活了) ====="
$M -e "SELECT id, user_id, spu_id, accumulated_count, accumulated_amount, state, status, joined_at FROM shop_queue_position WHERE user_id IN (99017, 99021) ORDER BY user_id, spu_id"

echo
echo "===== shop_promo_record（推广积分流水） ====="
$M -e "SELECT id, user_id, tenant_id, source_type, source_id, amount, balance_after, remark, create_time FROM shop_promo_record WHERE user_id IN (99017, 99021) ORDER BY id"

echo
echo "===== shop_referral_contribution（首贡献奖记录） ====="
$M -e "SELECT id, parent_user_id, child_user_id, spu_id, tenant_id, awarded_at FROM shop_referral_contribution WHERE parent_user_id IN (99017, 99021) OR child_user_id IN (99017, 99021)"

echo
echo "===== 订单 10031 详情 ====="
$M -e "SELECT * FROM trade_order_item WHERE order_id = 10031 \G"

echo
echo "===== 99021 买的那个 SPU 配置 ====="
$M -e "SELECT spu_id, tenant_id, tuijian_enabled, tuijian_n, tuijian_ratios, direct_rate, star_count FROM product_promo_config WHERE spu_id IN (SELECT spu_id FROM trade_order_item WHERE order_id = 10031)"
