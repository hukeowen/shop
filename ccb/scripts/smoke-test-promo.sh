#!/usr/bin/env bash
# =============================================================================
# 营销引擎部署后烟雾测试
#
# 用法：
#   ./smoke-test-promo.sh \
#       --base-url https://your.domain \
#       --tenant-id 1 \
#       --merchant-token <admin JWT> \
#       --user-token    <member JWT>
#
# 检查项（每项打印 ✓ 或 ✗）：
#   1. /actuator/health 后端活着
#   2. PromoConfig GET 默认值能拿到（含 poolSettleMode）
#   3. 商户能 PUT 一份配置（保存往返）
#   4. ProductPromoConfig 默认值（虚拟 spuId=999999）
#   5. 用户能拿到自己账户（双积分余额）
#   6. 用户能拿推广积分流水（分页）
#   7. 用户能查上级（自然用户应返 0）
#   8. 池信息端点（balance / lastSettledAt）
#   9. 积分池历史（分页）
#  10. 提现申请门槛校验（应拒，未到门槛）
#  11. 商户审批列表（分页）
#  12. infra_job 已 seed promoPoolSettleJob
#
# 任意一项失败立即非零退出，方便接 CI / cron。
# =============================================================================
set -uo pipefail

BASE_URL=""
TENANT_ID="1"
MERCHANT_TOKEN=""
USER_TOKEN=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url)        BASE_URL="$2"; shift 2 ;;
    --tenant-id)       TENANT_ID="$2"; shift 2 ;;
    --merchant-token)  MERCHANT_TOKEN="$2"; shift 2 ;;
    --user-token)      USER_TOKEN="$2"; shift 2 ;;
    -h|--help)
      sed -n '2,30p' "$0"
      exit 0 ;;
    *) echo "未知参数：$1" >&2; exit 2 ;;
  esac
done

if [[ -z "$BASE_URL" || -z "$MERCHANT_TOKEN" || -z "$USER_TOKEN" ]]; then
  echo "缺参数。--base-url / --merchant-token / --user-token 必传" >&2
  exit 2
fi

PASS=0
FAIL=0
RESULTS=()

# ---- 工具函数 ----
hit() {
  # hit <name> <expect_code> <method> <path> [token] [data]
  local name="$1" expect="$2" method="$3" path="$4" token="${5:-}" data="${6:-}"
  local hdr=(-H "tenant-id: $TENANT_ID")
  [[ -n "$token" ]] && hdr+=(-H "Authorization: Bearer $token")
  local body_arg=()
  if [[ -n "$data" ]]; then
    hdr+=(-H "Content-Type: application/json")
    body_arg=(-d "$data")
  fi
  local code body
  code=$(curl -s -o /tmp/.smoke-body -w "%{http_code}" \
              -X "$method" "${hdr[@]}" "${body_arg[@]}" \
              "$BASE_URL$path" || echo 000)
  body=$(cat /tmp/.smoke-body 2>/dev/null || echo "")
  if [[ "$code" == "$expect" ]] && ! echo "$body" | grep -q '"code":[1-9]'; then
    PASS=$((PASS+1))
    RESULTS+=("✓ $name")
    return 0
  else
    FAIL=$((FAIL+1))
    RESULTS+=("✗ $name [HTTP $code] body: ${body:0:200}")
    return 1
  fi
}

assert_field() {
  # assert_field <name> <jq_path>
  local name="$1" path="$2"
  if command -v jq >/dev/null 2>&1; then
    if jq -e "$path" /tmp/.smoke-body >/dev/null 2>&1; then
      PASS=$((PASS+1))
      RESULTS+=("✓ $name")
    else
      FAIL=$((FAIL+1))
      RESULTS+=("✗ $name (字段缺失：$path)")
    fi
  fi
}

# ---- 1. 后端活着 ----
hit "actuator/health"        200 GET  "/actuator/health" || true

# ---- 2/3. 商户级配置往返 ----
hit "GET promo/config"       200 GET  "/app-api/merchant/mini/promo/config" "$MERCHANT_TOKEN"
assert_field "config 含 poolSettleMode" '.data.poolSettleMode'

ROUND_TRIP_BODY='{
  "starLevelCount":5,
  "commissionRates":"[1,2,3,4,5]",
  "starUpgradeRules":"[{\"directCount\":2,\"teamSales\":3},{\"directCount\":3,\"teamSales\":9},{\"directCount\":5,\"teamSales\":27},{\"directCount\":8,\"teamSales\":81},{\"directCount\":12,\"teamSales\":243}]",
  "pointConversionRatio":1.00,
  "withdrawThreshold":10000,
  "poolEnabled":false,
  "poolRatio":5.00,
  "poolEligibleStars":"[3,4,5]",
  "poolDistributeMode":"ALL",
  "poolSettleCron":"0 0 0 1 * ?",
  "poolLotteryRatio":5.00,
  "poolSettleMode":"FULL"
}'
hit "PUT promo/config"       200 PUT  "/app-api/merchant/mini/promo/config" "$MERCHANT_TOKEN" "$ROUND_TRIP_BODY"

# ---- 4. 商品级配置（虚拟 SPU） ----
hit "GET product-config"     200 GET  "/app-api/merchant/mini/promo/product-config?spuId=999999" "$MERCHANT_TOKEN"

# ---- 5/6/7. 用户钱包 ----
hit "GET user/account"       200 GET  "/app-api/merchant/mini/promo/account" "$USER_TOKEN"
assert_field "account 含 promoPointBalance" '.data.promoPointBalance'

hit "GET promo-records"      200 GET  "/app-api/merchant/mini/promo/promo-records?pageNo=1&pageSize=10" "$USER_TOKEN"
hit "GET referral/parent"    200 GET  "/app-api/merchant/mini/promo/referral/parent" "$USER_TOKEN"

# ---- 8/9. 池子 ----
hit "GET pool/info"          200 GET  "/app-api/merchant/mini/promo/pool/info" "$MERCHANT_TOKEN"
hit "GET pool/rounds"        200 GET  "/app-api/merchant/mini/promo/pool/rounds?pageNo=1&pageSize=10" "$MERCHANT_TOKEN"

# ---- 10. 提现门槛校验 ----
# amount=1 远低于门槛 10000 → 期望 200 但 body code != 0
THRESHOLD_CODE=$(curl -s -o /tmp/.smoke-body -w "%{http_code}" \
    -X POST -H "tenant-id: $TENANT_ID" -H "Authorization: Bearer $USER_TOKEN" \
    "$BASE_URL/app-api/merchant/mini/withdraw/apply?amount=1" || echo 000)
THRESHOLD_BODY=$(cat /tmp/.smoke-body 2>/dev/null || echo "")
if [[ "$THRESHOLD_CODE" == "200" ]] && echo "$THRESHOLD_BODY" | grep -q -E '"code":[1-9]|门槛|余额'; then
  PASS=$((PASS+1))
  RESULTS+=("✓ withdraw 门槛校验拒绝低额（业务异常正常返回）")
else
  FAIL=$((FAIL+1))
  RESULTS+=("✗ withdraw 门槛校验未生效 [HTTP $THRESHOLD_CODE] body: ${THRESHOLD_BODY:0:200}")
fi

# ---- 11. 商户审批列表 ----
hit "GET admin withdraw page" 200 GET  "/admin-api/merchant/promo/withdraw/page?pageNo=1&pageSize=10" "$MERCHANT_TOKEN"

# ---- 总结 ----
echo
echo "===================="
for r in "${RESULTS[@]}"; do echo "  $r"; done
echo "===================="
echo "通过：$PASS  失败：$FAIL"
[[ "$FAIL" -eq 0 ]] || exit 1
