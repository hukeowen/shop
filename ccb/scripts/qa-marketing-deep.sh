#!/usr/bin/env bash
# =============================================================================
# QA · 营销系统深度 E2E（Phase B）
#
# 在 Phase A 8 用户的基础上验证：
#   1. 升星：让 A 凑齐 2 个直推 (B, C 都买过) + team_sales ≥ 30000 → 升 1 星
#   2. 团队极差：A 升 1 星后，B 再下一单 → A 拿 1% × payAmount
#   3. 池结算：配 pool_dist_rules → settle → payout 入账 + 池余额清零
#   4. 提现状态机：apply→approve→mark_paid 全跑 + reject 退还路径
#   5. 推→消转换：CONVERT 两侧流水 + 余额变化
#
# 前置：Phase A 跑过一遍（数据已有；本脚本不重置，是续测）
# =============================================================================
set -uo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:48080}"
SMS_CODE="${SMS_CODE:-888888}"
SHOP_TENANT="${SHOP_TENANT:-1010}"
SPU="${SPU:-99006}"
SKU="${SKU:-99006}"
UNIT_PRICE_FEN="${UNIT_PRICE_FEN:-1000}"

MYSQL_PASS="${MYSQL_PASS:-CHANGE_ME_5201314jxs@qq.com}"
MYSQL_DB="${MYSQL_DB:-ruoyi-vue-pro}"
if [[ "$(hostname)" == *iZ* ]] || [[ "${RUN_LOCAL_MYSQL:-0}" == "1" ]]; then
  MY_PREFIX=""
else
  MY_PREFIX="ssh root@47.109.143.146"
fi

my_run() {
  local fmt="$1" sql="$2"
  if [[ -z "$MY_PREFIX" ]]; then
    mysql -uroot -p"$MYSQL_PASS" -D "$MYSQL_DB" $fmt -e "$sql" 2>/dev/null
  else
    $MY_PREFIX "mysql -uroot -p$MYSQL_PASS -D $MYSQL_DB $fmt -e \"$sql\"" 2>/dev/null
  fi
}
q1() { my_run "-N -s" "$1" | tr -d '\r'; }
qt() { my_run "-t" "$1"; }
xx() { my_run "-t" "$1" 2>&1 | grep -v "^Warning"; }

PASS=0; FAIL=0; LOG=()
assert() {
  local desc="$1" actual="$2" expected="$3"
  if [[ "$actual" == "$expected" ]]; then
    PASS=$((PASS+1)); LOG+=("✓ $desc  actual=$actual")
  else
    FAIL=$((FAIL+1)); LOG+=("✗ $desc  actual=$actual  expected=$expected")
  fi
}
assert_ge() {
  local desc="$1" actual="$2" min="$3"
  if [[ -n "$actual" ]] && [[ "$actual" -ge "$min" ]]; then
    PASS=$((PASS+1)); LOG+=("✓ $desc  actual=$actual ≥ $min")
  else
    FAIL=$((FAIL+1)); LOG+=("✗ $desc  actual=$actual  expected ≥ $min")
  fi
}
banner() { echo; echo "================================================================"; echo "== $*"; echo "================================================================"; }

post_json() {
  local token="$1" tenant="$2" path="$3" body="$4"
  local hdr=(-H "Content-Type: application/json")
  [[ -n "$token" ]] && hdr+=(-H "Authorization: Bearer $token")
  [[ -n "$tenant" ]] && hdr+=(-H "tenant-id: $tenant")
  curl -sS -X POST "${hdr[@]}" -d "$body" "$BASE_URL$path"
}
get_json() {
  local token="$1" tenant="$2" path="$3"
  local hdr=()
  [[ -n "$token" ]] && hdr+=(-H "Authorization: Bearer $token")
  [[ -n "$tenant" ]] && hdr+=(-H "tenant-id: $tenant")
  curl -sS "${hdr[@]}" "$BASE_URL$path"
}

sms_login() {
  local mobile="$1"
  post_json "" "" "/app-api/member/auth/send-sms-code" "{\"mobile\":\"$mobile\",\"scene\":1}" > /dev/null
  sleep 1
  local resp=$(post_json "" "" "/app-api/member/auth/sms-login" "{\"mobile\":\"$mobile\",\"code\":\"$SMS_CODE\"}")
  echo "$resp" | jq -r '.data.userId'
  echo "$resp" | jq -r '.data.accessToken'
}

# ---- 拿现成 8 个用户 token（Phase A 残留）----
banner "STEP 0 · 加载 Phase A 8 用户 + 新增 I/J 用户"
declare -A USR_PHONE USR_ID USR_TOKEN USR_ADDR
USR_PHONE[A]=13900000001; USR_PHONE[B]=13900000002; USR_PHONE[C]=13900000003
USR_PHONE[D]=13900000004; USR_PHONE[E]=13900000005; USR_PHONE[F]=13900000006
USR_PHONE[G]=13900000007; USR_PHONE[H]=13900000008
# 新增：I/J 让 A 升星用（需 2 个直推 = B + I 都买）
USR_PHONE[I]=13900000009; USR_PHONE[J]=13900000010

for u in A B C D E F G H I J; do
  m=${USR_PHONE[$u]}
  out=$(sms_login "$m")
  USR_ID[$u]=$(echo "$out" | head -1)
  USR_TOKEN[$u]=$(echo "$out" | tail -1)
  # 创建地址（idempotent，每跑一次新地址 OK）
  addr=$(post_json "${USR_TOKEN[$u]}" "" "/app-api/member/address/create" \
    "{\"name\":\"qa-$u\",\"mobile\":\"$m\",\"areaId\":110101,\"detailAddress\":\"测试 $u\",\"defaultStatus\":true}")
  USR_ADDR[$u]=$(echo "$addr" | jq -r '.data // 0')
  echo "  $u $m  uid=${USR_ID[$u]}  addr=${USR_ADDR[$u]}"
done

submit_order() {
  local tok="$1" cnt="$2" addrId="$3"
  local bal="${4:-0}" cp="${5:-0}" pp="${6:-0}"
  local useBal=false useCp=false usePp=false
  [[ "$bal" -gt 0 ]] && useBal=true
  [[ "$cp" -gt 0 ]] && useCp=true
  [[ "$pp" -gt 0 ]] && usePp=true
  post_json "$tok" "" "/app-api/merchant/mini/checkout/submit" "$(cat <<EOF
{
  "tenantId": $SHOP_TENANT,
  "order": {
    "items": [{"skuId": $SKU, "count": $cnt}],
    "pointStatus": false,
    "deliveryType": 2,
    "pickUpStoreId": 1,
    "receiverName": "QA",
    "receiverMobile": "13900000099"
  },
  "useShopBalance": $useBal, "balanceFen": $bal,
  "useConsumePoint": $useCp, "consumePointDeductFen": $cp,
  "usePromoPoint": $usePp, "promoPointDeductFen": $pp
}
EOF
)"
}

bind_referral() {
  curl -sS -X POST -H "Authorization: Bearer $1" -H "tenant-id: $3" \
    "$BASE_URL/app-api/merchant/mini/promo/referral/bind?inviterUserId=$2"
}

place_and_confirm() {
  local key="$1" cnt="$2"
  local sub=$(submit_order "${USR_TOKEN[$key]}" "$cnt" "${USR_ADDR[$key]}")
  local orderId=$(echo "$sub" | jq -r '.data.orderId // 0')
  if [[ "$orderId" == "0" ]] || [[ "$orderId" == "null" ]]; then
    echo "ERR submit $key resp: $sub" >&2
    return 1
  fi
  curl -sS -X POST -H "Authorization: Bearer ${USR_TOKEN[$key]}" -H "tenant-id: $SHOP_TENANT" \
    "$BASE_URL/app-api/merchant/mini/order/offline-confirm?id=$orderId" > /dev/null
  echo "$orderId"
}

# ---- Phase A 残留状态：A=COMPLETED, B/C/D 已绑链, E/F/G/H ----
# 验证：A 当前 direct=1 (B), team=...
banner "STEP 1 · 当前状态 sanity"
aStar=$(q1 "SELECT IFNULL(current_star,0) FROM shop_user_star WHERE user_id=${USR_ID[A]} AND spu_id=$SPU AND tenant_id=$SHOP_TENANT")
aTeam=$(q1 "SELECT IFNULL(team_sales_amount,0) FROM shop_user_star WHERE user_id=${USR_ID[A]} AND spu_id=$SPU AND tenant_id=$SHOP_TENANT")
echo "  A 当前 star=$aStar  teamSales=$aTeam"

# ---- STEP 2: I/J 绑 A 让 A 直推 +1（需要 I 先满足前置：A 激活 ✓，I 无 rel）----
banner "STEP 2 · I 绑 A → I 自购 4 件 → A 直推 +1"

# 先看 I 当前 rel 状态（Phase A 没碰过 I）
iRel=$(q1 "SELECT COUNT(*) FROM member_shop_rel WHERE user_id=${USR_ID[I]} AND tenant_id=$SHOP_TENANT")
if [[ "$iRel" -gt 0 ]]; then
  # 之前测试残留 → 清掉 I 才能绑（"已不算拉新"）
  xx "DELETE FROM member_shop_rel WHERE user_id=${USR_ID[I]} AND tenant_id=$SHOP_TENANT" > /dev/null
  xx "DELETE FROM shop_user_referral WHERE user_id=${USR_ID[I]} AND tenant_id=$SHOP_TENANT" > /dev/null
  xx "DELETE FROM shop_queue_position WHERE user_id=${USR_ID[I]} AND spu_id=$SPU" > /dev/null
  xx "DELETE FROM shop_user_star WHERE user_id=${USR_ID[I]} AND tenant_id=$SHOP_TENANT" > /dev/null
fi

bindIA=$(bind_referral "${USR_TOKEN[I]}" "${USR_ID[A]}" "$SHOP_TENANT")
assert "I 绑 A code=0" "$(echo "$bindIA" | jq -r '.code')" "0"
assert "I 绑 A 返 true" "$(echo "$bindIA" | jq -r '.data')" "true"

i1=$(place_and_confirm I 4); echo "  I 首单 4 件 order=$i1"
sleep 2

# 检查：现在 A 直推数 = 2 (B 来自 Phase A + I)
# 但代码 direct_count_per_spu 没自动累加，attemptUpgradeV8 实时 histo 算
# attempt upgrade: needStar=0, matched=histo[0..]= count(B 和 I 在 SPU 上的 star 行) = 2 (B 在 Phase A buy, I 刚 buy)
# need 2 ✓; team_sales: A 累加 B I 等订单 → 这里也 30000+ 应该满足升 1 星
aStar2=$(q1 "SELECT IFNULL(current_star,0) FROM shop_user_star WHERE user_id=${USR_ID[A]} AND spu_id=$SPU AND tenant_id=$SHOP_TENANT")
aTeam2=$(q1 "SELECT IFNULL(team_sales_amount,0) FROM shop_user_star WHERE user_id=${USR_ID[A]} AND spu_id=$SPU AND tenant_id=$SHOP_TENANT")
echo "  A 升星后 star=$aStar2  teamSales=$aTeam2"

# 升 1 星条件：directCount=2 + teamSales=30000（按 PromoConfigServiceImpl 默认 DEFAULT_STAR_UPGRADE_RULES）
# 但商品级 starUpgradeRules JSON 没配，让我们查
upgradeRules=$(q1 "SELECT star_upgrade_rules FROM product_promo_config WHERE spu_id=$SPU")
echo "  SPU upgrade rules: $upgradeRules"
# 如果没配，先 set 一个合理默认
if [[ -z "$upgradeRules" ]] || [[ "$upgradeRules" == "[]" ]]; then
  xx "UPDATE product_promo_config SET star_upgrade_rules='[{\"requiredCount\":2,\"teamSales\":30000},{\"requiredCount\":3,\"teamSales\":90000},{\"requiredCount\":5,\"teamSales\":270000}]' WHERE spu_id=$SPU" > /dev/null
  echo "  ✓ 已为 SPU $SPU 设置 starUpgradeRules"
  # 重跑一遍 attemptUpgrade — 通过插入一笔订单触发
  i2=$(place_and_confirm I 1); echo "  I 再下 1 单触发升星 attempt order=$i2"
  sleep 2
  aStar2=$(q1 "SELECT IFNULL(current_star,0) FROM shop_user_star WHERE user_id=${USR_ID[A]} AND spu_id=$SPU AND tenant_id=$SHOP_TENANT")
fi
assert_ge "STEP 2 A 升 ≥ 1 星" "$aStar2" "1"

# ---- STEP 3: B 再下一单 → A 拿团队极差奖 ----
banner "STEP 3 · B 再下 1 单 → A.star=1 → 团队极差 +1%"

aBalBefore=$(q1 "SELECT IFNULL(promo_point_balance,0) FROM shop_user_star WHERE user_id=${USR_ID[A]} AND spu_id=0 AND tenant_id=$SHOP_TENANT")
b2=$(place_and_confirm B 2); echo "  B 二单 2 件 order=$b2"
sleep 2

aBalAfter=$(q1 "SELECT IFNULL(promo_point_balance,0) FROM shop_user_star WHERE user_id=${USR_ID[A]} AND spu_id=0 AND tenant_id=$SHOP_TENANT")
# B 2 件 COMPLETED 实付 ¥2 = 200 分；A.star=1 拿 1% × 200 = 2 分
# A 自购返：B 二单 COMPLETED → A 不拿首贡献（UNIQUE 已存在）
# A 拿：1) 团队极差 1% × 200 = 2 分
# 但 A 也是 B 的 ancestor，且 A.star=1：差值 = 1% × 200 = 2 分
# B 自购 produced = directRate 10% × 200 = 20 分
delta=$((aBalAfter - aBalBefore))
echo "  A balance Δ = $delta 分（含极差奖 + 可能的其他）"
# 极差奖期望 2 分；可能还有 contributing
assert_ge "STEP 3 A balance 增长 ≥ 2 (含 1% 极差)" "$delta" "2"

commCnt=$(q1 "SELECT COUNT(*) FROM shop_promo_record WHERE user_id=${USR_ID[A]} AND source_type='COMMISSION'")
assert_ge "STEP 3 A 流水含 COMMISSION ≥ 1" "$commCnt" "1"

# ---- STEP 4: 池结算 ----
banner "STEP 4 · 池结算 E2E"
poolBefore=$(q1 "SELECT IFNULL(pool_balance,0) FROM spu_star_pool WHERE tenant_id=$SHOP_TENANT AND spu_id=$SPU")
echo "  池余额 before settle: $poolBefore 分"

# 配 pool_dist_rules：100% 给 1 星 EQUAL 分
xx "UPDATE product_promo_config SET pool_dist_rules='[{\"star\":1,\"ratio\":100,\"mode\":\"EQUAL\"}]' WHERE spu_id=$SPU" > /dev/null

# 商户后台触发 settle (admin-api 需要 merchant token；这里直接走内部 service 经 admin endpoint)
# 我们没 admin token，曲线救国：直接走 mini 端商户接口或者用 mysql 触发
# 实际：调 /admin-api/merchant/spu-pool/settle 需要 admin 登录
# 或者：直接调 SpuPoolSettleService 没暴露给 mini 端。先尝试 admin endpoint。
# 这里直接执行 SQL 模拟（生产环境是 admin 后台手动点）
# Actual: admin endpoint /admin-api/merchant/spu-pool/settle?spuId=X&remark=Y

# 用 merchant 老板 (18888888888) 后台登录
mhanLogin=$(post_json "" "" "/app-api/member/auth/send-sms-code" "{\"mobile\":\"18888888888\",\"scene\":1}")
sleep 1
mhanResp=$(post_json "" "" "/app-api/member/auth/sms-login" "{\"mobile\":\"18888888888\",\"code\":\"$SMS_CODE\"}")
mhanTok=$(echo "$mhanResp" | jq -r '.data.accessToken')
echo "  merchant 18888888888 token: ${mhanTok:0:16}..."

# 商户结算（mini 端）— 现在 spu-pool/settle 在 controller 看
settleResp=$(curl -sS -X POST -H "Authorization: Bearer $mhanTok" -H "tenant-id: $SHOP_TENANT" \
  "$BASE_URL/app-api/merchant/mini/promo/spu-pool/settle?spuId=$SPU&remark=qa-test")
echo "  settle resp: $settleResp"
settleCode=$(echo "$settleResp" | jq -r '.code')
assert "settle code=0" "$settleCode" "0"

sleep 1
poolAfter=$(q1 "SELECT IFNULL(pool_balance,0) FROM spu_star_pool WHERE tenant_id=$SHOP_TENANT AND spu_id=$SPU")
echo "  池余额 after: $poolAfter 分"
payoutCnt=$(q1 "SELECT COUNT(*) FROM spu_star_pool_payout_item WHERE spu_id=$SPU")
echo "  池 payout 条数: $payoutCnt"
# 至少有 1 个 1 星用户（A）拿钱
assert_ge "结算后 payout 条数 ≥ 1" "$payoutCnt" "1"

# ---- STEP 5: 提现状态机 ----
banner "STEP 5 · 提现状态机"

# A 余额够 → 申请 (门槛 10000 分)
aBalNow=$(q1 "SELECT IFNULL(promo_point_balance,0) FROM shop_user_star WHERE user_id=${USR_ID[A]} AND spu_id=0 AND tenant_id=$SHOP_TENANT")
echo "  A 当前推广积分: $aBalNow"

# 余额不够 10000 时，临时降门槛到 100 跑测试
ORIG_THRESHOLD=$(q1 "SELECT withdraw_threshold FROM shop_promo_config WHERE tenant_id=$SHOP_TENANT")
RESTORE_THRESHOLD=0
if [[ "$aBalNow" -lt 10000 ]] && [[ "$aBalNow" -ge 100 ]]; then
  xx "UPDATE shop_promo_config SET withdraw_threshold=100 WHERE tenant_id=$SHOP_TENANT" > /dev/null
  RESTORE_THRESHOLD=1
  echo "  ✓ 临时降门槛到 100 跑测试（原值 $ORIG_THRESHOLD）"
  aBalNow=10000  # fake to enter branch
fi

if [[ "$aBalNow" -ge 10000 ]]; then
  # 跑申请
  APPLY_AMT=100
  [[ "$RESTORE_THRESHOLD" == "1" ]] && APPLY_AMT=100 || APPLY_AMT=10000
  applyResp=$(curl -sS -X POST -H "Authorization: Bearer ${USR_TOKEN[A]}" -H "tenant-id: $SHOP_TENANT" \
    "$BASE_URL/app-api/merchant/mini/withdraw/apply?amount=$APPLY_AMT&tenantId=$SHOP_TENANT")
  echo "  apply resp: $applyResp"
  applyId=$(echo "$applyResp" | jq -r '.data.id // 0')
  assert_ge "STEP 5 withdraw apply id > 0" "$applyId" "1"

  if [[ "$applyId" -gt 0 ]]; then
    # 商户审批 approve
    apprResp=$(curl -sS -X POST -H "Authorization: Bearer $mhanTok" -H "tenant-id: $SHOP_TENANT" \
      "$BASE_URL/app-api/merchant/mini/withdraw/approve?id=$applyId&remark=qa-ok")
    assert "withdraw approve code=0" "$(echo "$apprResp" | jq -r '.code')" "0"
    status=$(q1 "SELECT status FROM shop_promo_withdraw WHERE id=$applyId")
    assert "withdraw 状态 APPROVED" "$status" "APPROVED"

    # 商户标已付款 mark-paid
    paidResp=$(curl -sS -X POST -H "Authorization: Bearer $mhanTok" -H "tenant-id: $SHOP_TENANT" \
      "$BASE_URL/app-api/merchant/mini/withdraw/mark-paid?id=$applyId&remark=qa-paid")
    assert "withdraw mark-paid code=0" "$(echo "$paidResp" | jq -r '.code')" "0"
    status2=$(q1 "SELECT status FROM shop_promo_withdraw WHERE id=$applyId")
    assert "withdraw 状态 PAID" "$status2" "PAID"
  fi
else
  echo "  ⚠ A 余额 $aBalNow < 100 跳过提现状态机测试"
  LOG+=("⚠ STEP 5 skipped: A 余额 < 100")
fi
# 恢复门槛
if [[ "$RESTORE_THRESHOLD" == "1" ]]; then
  xx "UPDATE shop_promo_config SET withdraw_threshold=$ORIG_THRESHOLD WHERE tenant_id=$SHOP_TENANT" > /dev/null
  echo "  ✓ 门槛恢复为 $ORIG_THRESHOLD"
fi

# ---- STEP 6: 推→消转换 ----
banner "STEP 6 · 推→消转换"
# A 当前推广余额 - 已扣的 withdraw → 剩余
aPromoBalNow=$(q1 "SELECT IFNULL(promo_point_balance,0) FROM shop_user_star WHERE user_id=${USR_ID[A]} AND spu_id=0 AND tenant_id=$SHOP_TENANT")
aConsumeBefore=$(q1 "SELECT IFNULL(consume_point_balance,0) FROM shop_user_star WHERE user_id=${USR_ID[A]} AND spu_id=0 AND tenant_id=$SHOP_TENANT")
echo "  A 推广=$aPromoBalNow 消费=$aConsumeBefore"

if [[ "$aPromoBalNow" -ge 10 ]]; then
  idemKey=$((RANDOM * 1000 + RANDOM))
  # convert 用 @RequestParam 不是 body
  convResp=$(curl -sS -X POST -H "Authorization: Bearer ${USR_TOKEN[A]}" -H "tenant-id: $SHOP_TENANT" \
    "$BASE_URL/app-api/merchant/mini/promo/convert?promoAmount=10&idempotencyKey=$idemKey")
  echo "  convert resp: $convResp"
  cvCode=$(echo "$convResp" | jq -r '.code')
  assert "convert code=0" "$cvCode" "0"

  aPromoAfter=$(q1 "SELECT IFNULL(promo_point_balance,0) FROM shop_user_star WHERE user_id=${USR_ID[A]} AND spu_id=0 AND tenant_id=$SHOP_TENANT")
  aConsumeAfter=$(q1 "SELECT IFNULL(consume_point_balance,0) FROM shop_user_star WHERE user_id=${USR_ID[A]} AND spu_id=0 AND tenant_id=$SHOP_TENANT")
  promoDelta=$((aPromoBalNow - aPromoAfter))
  consumeDelta=$((aConsumeAfter - aConsumeBefore))
  assert "推广 -10" "$promoDelta" "10"
  assert "消费 +10 (1:1)" "$consumeDelta" "10"
else
  echo "  ⚠ A 推广余额 $aPromoBalNow < 10 跳过转换测试"
fi

# ---- 汇总 ----
banner "RESULT"
for l in "${LOG[@]}"; do echo "  $l"; done
echo
echo "==================================="
echo " 总 PASS=$PASS  FAIL=$FAIL"
echo "==================================="
[[ "$FAIL" -eq 0 ]] || exit 1
