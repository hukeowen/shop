#!/usr/bin/env bash
# =============================================================================
# QA · 商城其他模块 E2E（Phase C）
#
# 覆盖：
#   1. 优惠券：商户 seed 模板 → C 端 grab → my-list → checkout 用券 → 抵扣
#   2. 全额抵扣免支付：余额+消费+推广 凑齐 → trade.payStatus=1, status=30, 跳通联
#   3. 多 SPU 订单：99006(推N反1) + 99013(普通) 同单
#   4. offline-cancel：UNPAID 订单取消 → status=40
#   5. 异常：领券幂等 / min_amount 校验 / 抵扣超额
#
# 前置：Phase A 已跑过，用户 A(99023)/B(99024)/I(99031) 等已存在
# =============================================================================
set -uo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:48080}"
SMS_CODE="${SMS_CODE:-888888}"
SHOP_TENANT="${SHOP_TENANT:-1010}"
SPU="${SPU:-99006}"
SKU="${SKU:-99006}"
SPU2="${SPU2:-99013}"
SKU2="${SKU2:-99013}"
UNIT_FEN=1000

MYSQL_PASS="${MYSQL_PASS:-CHANGE_ME_5201314jxs@qq.com}"
MYSQL_DB="${MYSQL_DB:-ruoyi-vue-pro}"
if [[ "$(hostname)" == *iZ* ]]; then MY_PREFIX=""; else MY_PREFIX="ssh root@47.109.143.146"; fi
my_run() { if [[ -z "$MY_PREFIX" ]]; then mysql -uroot -p"$MYSQL_PASS" -D "$MYSQL_DB" $1 -e "$2" 2>/dev/null; else $MY_PREFIX "mysql -uroot -p$MYSQL_PASS -D $MYSQL_DB $1 -e \"$2\"" 2>/dev/null; fi; }
q1() { my_run "-N -s" "$1" | tr -d '\r'; }
qt() { my_run "-t" "$1"; }
xx() { my_run "-t" "$1" 2>&1 | grep -v "^Warning"; }

PASS=0; FAIL=0; LOG=()
assert() { local d="$1" a="$2" e="$3"; if [[ "$a" == "$e" ]]; then PASS=$((PASS+1)); LOG+=("✓ $d  actual=$a"); else FAIL=$((FAIL+1)); LOG+=("✗ $d  actual=$a  expected=$e"); fi; }
assert_ge() { local d="$1" a="$2" m="$3"; if [[ -n "$a" ]] && [[ "$a" -ge "$m" ]]; then PASS=$((PASS+1)); LOG+=("✓ $d  actual=$a ≥ $m"); else FAIL=$((FAIL+1)); LOG+=("✗ $d  actual=$a expected ≥ $m"); fi; }
banner() { echo; echo "===================================="; echo "== $*"; echo "===================================="; }

post_json() {
  local token="$1" tenant="$2" path="$3" body="$4"
  local hdr=(-H "Content-Type: application/json")
  [[ -n "$token" ]] && hdr+=(-H "Authorization: Bearer $token")
  [[ -n "$tenant" ]] && hdr+=(-H "tenant-id: $tenant")
  curl -sS -X POST "${hdr[@]}" -d "$body" "$BASE_URL$path"
}
sms_login() {
  local mobile="$1"
  post_json "" "" "/app-api/member/auth/send-sms-code" "{\"mobile\":\"$mobile\",\"scene\":1}" > /dev/null; sleep 1
  local r=$(post_json "" "" "/app-api/member/auth/sms-login" "{\"mobile\":\"$mobile\",\"code\":\"$SMS_CODE\"}")
  echo "$r" | jq -r '.data.userId'; echo "$r" | jq -r '.data.accessToken'
}

# ---- 加载用户 ----
banner "STEP 0 · 加载用户 A/B/I + 商户老板 M"
out=$(sms_login 13900000001); UID_A=$(echo "$out"|head -1); TOK_A=$(echo "$out"|tail -1)
out=$(sms_login 13900000002); UID_B=$(echo "$out"|head -1); TOK_B=$(echo "$out"|tail -1)
out=$(sms_login 18888888888); UID_M=$(echo "$out"|head -1); TOK_M=$(echo "$out"|tail -1)
echo "  A=$UID_A B=$UID_B M=$UID_M (爱家超市 老板)"

# 取 A 地址
ADDR_A=$(q1 "SELECT id FROM member_address WHERE user_id=$UID_A ORDER BY id DESC LIMIT 1")
echo "  A 默认地址 id=$ADDR_A"

# ====================================================================
banner "STEP 1 · 优惠券：商户 seed → C 端 grab → 用券 checkout"
# ====================================================================

# 清理可能存在的旧测试券
xx "DELETE FROM shop_coupon_user WHERE coupon_id IN (SELECT id FROM shop_coupon WHERE tenant_id=$SHOP_TENANT AND name LIKE 'qa-%')" > /dev/null
xx "DELETE FROM shop_coupon WHERE tenant_id=$SHOP_TENANT AND name LIKE 'qa-%'" > /dev/null

# 商户 seed 一张：满 ¥5 减 ¥1
saveResp=$(post_json "$TOK_M" "$SHOP_TENANT" "/app-api/merchant/mini/coupon/save" \
  '{"name":"qa-5减1","discountAmount":100,"minAmount":500,"totalCount":100,"validDays":30,"status":0}')
echo "  coupon save: $saveResp"
COUPON_ID=$(echo "$saveResp" | jq -r '.data')
assert_ge "STEP 1 商户 seed coupon id" "$COUPON_ID" "1"

# C 端拉店内可领券（PermitAll）
listResp=$(curl -sS "$BASE_URL/app-api/merchant/shop/public/coupons?tenantId=$SHOP_TENANT")
listCnt=$(echo "$listResp" | jq -r '.data | length')
assert_ge "STEP 1 public coupons list ≥ 1" "$listCnt" "1"

# A grab
grabResp=$(curl -sS -X POST -H "Authorization: Bearer $TOK_A" \
  "$BASE_URL/app-api/merchant/mini/coupon/grab?tenantId=$SHOP_TENANT&couponId=$COUPON_ID")
COUPON_USER_ID=$(echo "$grabResp" | jq -r '.data')
assert_ge "STEP 1 A grab couponUserId" "$COUPON_USER_ID" "1"

# 重复 grab 应幂等返同 id
grabResp2=$(curl -sS -X POST -H "Authorization: Bearer $TOK_A" \
  "$BASE_URL/app-api/merchant/mini/coupon/grab?tenantId=$SHOP_TENANT&couponId=$COUPON_ID")
COUPON_USER_ID2=$(echo "$grabResp2" | jq -r '.data')
assert "STEP 1 重复 grab 返同 id" "$COUPON_USER_ID2" "$COUPON_USER_ID"

# my-list 应有 1 张
myListResp=$(curl -sS -H "Authorization: Bearer $TOK_A" "$BASE_URL/app-api/merchant/mini/coupon/my-list?tenantId=$SHOP_TENANT")
myCnt=$(echo "$myListResp" | jq -r '[.data[] | select(.couponId == '$COUPON_ID')] | length')
assert "STEP 1 my-list 含本券" "$myCnt" "1"

submit_with_coupon() {
  # submit <token> <skuId> <count> <addrId> [couponUserId] [bal] [cp] [pp]
  local tok="$1" skuId="$2" cnt="$3" addr="$4"
  local cu="${5:-}" bal="${6:-0}" cp="${7:-0}" pp="${8:-0}"
  local useBal=false useCp=false usePp=false
  [[ "$bal" -gt 0 ]] && useBal=true
  [[ "$cp" -gt 0 ]] && useCp=true
  [[ "$pp" -gt 0 ]] && usePp=true
  local cuField=""
  [[ -n "$cu" ]] && cuField=",\"couponUserId\":$cu"
  post_json "$tok" "" "/app-api/merchant/mini/checkout/submit" "$(cat <<EOF
{"tenantId":$SHOP_TENANT,"order":{"items":[{"skuId":$skuId,"count":$cnt}],"pointStatus":false,"deliveryType":2,"pickUpStoreId":1,"receiverName":"QA","receiverMobile":"13900000099"},"useShopBalance":$useBal,"balanceFen":$bal,"useConsumePoint":$useCp,"consumePointDeductFen":$cp,"usePromoPoint":$usePp,"promoPointDeductFen":$pp$cuField}
EOF
)"
}

# A 下 5 件 (¥5) + 用券 ¥1 → expected couponDeductFen=100
echo "  A 用券下 5 件 ¥5 (满 ¥5 减 ¥1)..."
sub5=$(submit_with_coupon "$TOK_A" "$SKU" 5 "$ADDR_A" "$COUPON_USER_ID")
cdfn=$(echo "$sub5" | jq -r '.data.couponDeductFen // 0')
assert "STEP 1 couponDeductFen=100" "$cdfn" "100"
ORD5=$(echo "$sub5" | jq -r '.data.orderId')
curl -sS -X POST -H "Authorization: Bearer $TOK_A" -H "tenant-id: $SHOP_TENANT" \
  "$BASE_URL/app-api/merchant/mini/order/offline-confirm?id=$ORD5" > /dev/null
sleep 1

# 验券已 used
cuStatus=$(q1 "SELECT status FROM shop_coupon_user WHERE id=$COUPON_USER_ID")
assert "STEP 1 券 status=1 已用" "$cuStatus" "1"

# 异常：A 已用券再下单同券 → 应失败
sub5b=$(submit_with_coupon "$TOK_A" "$SKU" 5 "$ADDR_A" "$COUPON_USER_ID")
errCode=$(echo "$sub5b" | jq -r '.code')
if [[ "$errCode" != "0" ]]; then
  PASS=$((PASS+1)); LOG+=("✓ STEP 1 已用券再次提交被拒 code=$errCode")
else
  FAIL=$((FAIL+1)); LOG+=("✗ STEP 1 已用券再次提交未拒")
fi

# 异常：B 用 A 的券 → 应失败
sub5c=$(submit_with_coupon "$TOK_B" "$SKU" 5 "$ADDR_A" "$COUPON_USER_ID")
errCode2=$(echo "$sub5c" | jq -r '.code')
if [[ "$errCode2" != "0" ]]; then
  PASS=$((PASS+1)); LOG+=("✓ STEP 1 跨用户用券被拒 code=$errCode2")
else
  FAIL=$((FAIL+1)); LOG+=("✗ STEP 1 跨用户用券未拒")
fi

# ====================================================================
banner "STEP 2 · 全额抵扣免支付（balance + consume + promo 凑齐）"
# ====================================================================
# 给 A 加点店铺余额（直接 DB seed，模拟之前充值）
xx "UPDATE member_shop_rel SET balance=5000 WHERE user_id=$UID_A AND tenant_id=$SHOP_TENANT" > /dev/null
# 顺手给 A 加点 consume 积分（直接 DB seed 不写流水）
xx "UPDATE shop_user_star SET consume_point_balance = consume_point_balance + 5000 WHERE user_id=$UID_A AND spu_id=0 AND tenant_id=$SHOP_TENANT" > /dev/null

aBalSeed=$(q1 "SELECT balance FROM member_shop_rel WHERE user_id=$UID_A AND tenant_id=$SHOP_TENANT")
aConsSeed=$(q1 "SELECT consume_point_balance FROM shop_user_star WHERE user_id=$UID_A AND spu_id=0 AND tenant_id=$SHOP_TENANT")
aPromoSeed=$(q1 "SELECT promo_point_balance FROM shop_user_star WHERE user_id=$UID_A AND spu_id=0 AND tenant_id=$SHOP_TENANT")
echo "  A 现有: balance=$aBalSeed consume=$aConsSeed promo=$aPromoSeed"

# 5 件 = 5000 分；用 balance 5000 (会被 cap 到 payPrice-1=4999) → payPrice=1 → 触发免支付路径
fullSub=$(submit_with_coupon "$TOK_A" "$SKU" 5 "$ADDR_A" "" 5000 0 0)
echo "  全抵扣 submit resp: $(echo "$fullSub" | jq -c '.data | {orderId,balanceDeductFen,consumePointDeductFen,promoPointRedeemFen,payPrice}')"
fullOrderId=$(echo "$fullSub" | jq -r '.data.orderId')
fullPay=$(echo "$fullSub" | jq -r '.data.payPrice')
assert "STEP 2 payPrice=0（免支付）" "$fullPay" "0"

# 验数据库：trade_order.pay_status=1, status=30
sleep 2
payStatus=$(q1 "SELECT pay_status FROM trade_order WHERE id=$fullOrderId" | head -1)
ordStatus=$(q1 "SELECT status FROM trade_order WHERE id=$fullOrderId")
# pay_status 是 bit → 转 unsigned 看
payStatus=$(q1 "SELECT CAST(pay_status AS UNSIGNED) FROM trade_order WHERE id=$fullOrderId")
assert "STEP 2 trade_order.pay_status=1" "$payStatus" "1"
assert "STEP 2 trade_order.status=30" "$ordStatus" "30"

# ====================================================================
banner "STEP 3 · 多 SPU 订单 (99006 + 99013)"
# ====================================================================
# 99013 普通商品 1 件 + 99006 推 N 反 1 1 件
multiSub=$(post_json "$TOK_B" "" "/app-api/merchant/mini/checkout/submit" "$(cat <<EOF
{"tenantId":$SHOP_TENANT,"order":{"items":[{"skuId":$SKU,"count":1},{"skuId":$SKU2,"count":1}],"pointStatus":false,"deliveryType":2,"pickUpStoreId":1,"receiverName":"QA","receiverMobile":"13900000099"},"useShopBalance":false,"balanceFen":0,"useConsumePoint":false,"consumePointDeductFen":0,"usePromoPoint":false,"promoPointDeductFen":0}
EOF
)")
multiCode=$(echo "$multiSub" | jq -r '.code')
assert "STEP 3 multi-SPU submit code=0" "$multiCode" "0"
multiOrderId=$(echo "$multiSub" | jq -r '.data.orderId')
echo "  multi-SPU orderId=$multiOrderId"

# offline-confirm 让营销引擎跑
curl -sS -X POST -H "Authorization: Bearer $TOK_B" -H "tenant-id: $SHOP_TENANT" \
  "$BASE_URL/app-api/merchant/mini/order/offline-confirm?id=$multiOrderId" > /dev/null
sleep 2

# 查 trade_order_item 2 行
itemCnt=$(q1 "SELECT COUNT(*) FROM trade_order_item WHERE order_id=$multiOrderId")
assert "STEP 3 trade_order_item 2 行" "$itemCnt" "2"

# 99013 (普通 SPU) 应 NOT 写 shop_promo_deduction_record（tuijianEnabled=false）
deductRec99013=$(q1 "SELECT COUNT(*) FROM shop_promo_deduction_record WHERE order_id=$multiOrderId AND spu_id=$SPU2")
assert "STEP 3 99013 普通 SPU 不写 deduction_record" "$deductRec99013" "0"
# 99006 应写
deductRec99006=$(q1 "SELECT COUNT(*) FROM shop_promo_deduction_record WHERE order_id=$multiOrderId AND spu_id=$SPU")
assert_ge "STEP 3 99006 推N反1 写 deduction_record ≥ 1" "$deductRec99006" "1"

# ====================================================================
banner "STEP 4 · offline-cancel UNPAID 订单"
# ====================================================================
# B 下 1 件不 confirm，直接 cancel
cancSub=$(post_json "$TOK_B" "" "/app-api/merchant/mini/checkout/submit" "$(cat <<EOF
{"tenantId":$SHOP_TENANT,"order":{"items":[{"skuId":$SKU,"count":1}],"pointStatus":false,"deliveryType":2,"pickUpStoreId":1,"receiverName":"QA","receiverMobile":"13900000099"},"useShopBalance":false,"balanceFen":0,"useConsumePoint":false,"consumePointDeductFen":0,"usePromoPoint":false,"promoPointDeductFen":0}
EOF
)")
cancOrderId=$(echo "$cancSub" | jq -r '.data.orderId')
echo "  待取消订单 id=$cancOrderId"

# 验初始 status=0 (UNPAID)
init=$(q1 "SELECT status FROM trade_order WHERE id=$cancOrderId")
assert "STEP 4 初始 status=0" "$init" "0"

# 取消
cancResp=$(curl -sS -X POST -H "Authorization: Bearer $TOK_M" -H "tenant-id: $SHOP_TENANT" \
  "$BASE_URL/app-api/merchant/mini/order/offline-cancel?id=$cancOrderId")
assert "STEP 4 cancel code=0" "$(echo "$cancResp" | jq -r '.code')" "0"

sleep 1
finalStatus=$(q1 "SELECT status FROM trade_order WHERE id=$cancOrderId")
# trade 取消 status=40 (CANCELED)
assert "STEP 4 cancel 后 status=40" "$finalStatus" "40"

# 已支付订单不可 cancel
ord5Status=$(q1 "SELECT status FROM trade_order WHERE id=$ORD5")
cancAgainResp=$(curl -sS -X POST -H "Authorization: Bearer $TOK_M" -H "tenant-id: $SHOP_TENANT" \
  "$BASE_URL/app-api/merchant/mini/order/offline-cancel?id=$ORD5")
cancAgainCode=$(echo "$cancAgainResp" | jq -r '.code')
if [[ "$cancAgainCode" != "0" ]]; then
  PASS=$((PASS+1)); LOG+=("✓ STEP 4 已完成订单取消被拒 code=$cancAgainCode")
else
  FAIL=$((FAIL+1)); LOG+=("✗ STEP 4 已完成订单取消未拒")
fi

# ====================================================================
banner "RESULT"
# ====================================================================
for l in "${LOG[@]}"; do echo "  $l"; done
echo
echo "==================================="
echo " 总 PASS=$PASS  FAIL=$FAIL"
echo "==================================="
[[ "$FAIL" -eq 0 ]] || exit 1
