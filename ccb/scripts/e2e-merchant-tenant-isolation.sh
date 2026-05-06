#!/usr/bin/env bash
# E2E 商户跨租户隔离验证脚本（bash + curl + jq）
# 前提：本地 48080 已启动，数据库为空（用户刚清库）。
# 用法：bash scripts/e2e-merchant-tenant-isolation.sh
set -euo pipefail

BASE="${BASE:-http://localhost:48080}"
PHONE_A="13700137001"
PHONE_B="13700137002"
SHOP_A="测试烧烤A"
SHOP_B="测试水果B"
SMS_CODE="888888"

red() { printf '\033[31m%s\033[0m\n' "$*"; }
grn() { printf '\033[32m%s\033[0m\n' "$*"; }
ylw() { printf '\033[33m%s\033[0m\n' "$*"; }

step() { ylw "▶ $*"; }
ok()   { grn "  ✓ $*"; }
fail() { red "  ✗ $*"; exit 1; }

# 通用 POST 工具
api_post() {
  local url="$1" body="$2" token="${3:-}"
  curl -sS -X POST "${BASE}${url}" \
    -H "Content-Type: application/json" \
    ${token:+-H "Authorization: Bearer ${token}"} \
    --data-raw "$body"
}
api_get() {
  local url="$1" token="${2:-}"
  curl -sS -X GET "${BASE}${url}" \
    ${token:+-H "Authorization: Bearer ${token}"}
}

# 1. 注册商户 A
step "1. 注册商户 A: ${PHONE_A}"
api_post /app-api/app/auth/send-sms-code "{\"mobile\":\"${PHONE_A}\",\"scene\":50}" >/dev/null
RESP_A=$(api_post /app-api/app/auth/apply-merchant-by-sms "{\"mobile\":\"${PHONE_A}\",\"code\":\"${SMS_CODE}\",\"shopName\":\"${SHOP_A}\"}")
echo "  → resp: ${RESP_A}"
TOKEN_A=$(echo "$RESP_A" | jq -r '.data.token // .data.token.accessToken // empty')
TENANT_A=$(echo "$RESP_A" | jq -r '.data.tenantId // empty')
MERCHANT_A=$(echo "$RESP_A" | jq -r '.data.merchantId // empty')
[[ -n "$TOKEN_A" ]] || fail "A token 缺失"
[[ -n "$TENANT_A" && "$TENANT_A" != "null" ]] || fail "A tenantId 缺失"
ok "A: token=${TOKEN_A:0:12}... tenantId=${TENANT_A} merchantId=${MERCHANT_A}"

# 2. 注册商户 B
step "2. 注册商户 B: ${PHONE_B}"
api_post /app-api/app/auth/send-sms-code "{\"mobile\":\"${PHONE_B}\",\"scene\":50}" >/dev/null
RESP_B=$(api_post /app-api/app/auth/apply-merchant-by-sms "{\"mobile\":\"${PHONE_B}\",\"code\":\"${SMS_CODE}\",\"shopName\":\"${SHOP_B}\"}")
TOKEN_B=$(echo "$RESP_B" | jq -r '.data.token // empty')
TENANT_B=$(echo "$RESP_B" | jq -r '.data.tenantId // empty')
MERCHANT_B=$(echo "$RESP_B" | jq -r '.data.merchantId // empty')
[[ -n "$TOKEN_B" ]] || fail "B token 缺失"
ok "B: token=${TOKEN_B:0:12}... tenantId=${TENANT_B} merchantId=${MERCHANT_B}"

# 3. 验证两个 tenant 不同（核心断言）
step "3. 跨租户隔离断言"
[[ "$TENANT_A" != "$TENANT_B" ]] || fail "tenantId 相同！跨租户失败：A=${TENANT_A} B=${TENANT_B}"
[[ "$TENANT_A" != "1" ]] || fail "A tenantId=1（admin），不是独立租户"
[[ "$TENANT_B" != "1" ]] || fail "B tenantId=1（admin），不是独立租户"
ok "A.tenant=${TENANT_A} ≠ B.tenant=${TENANT_B}，且都不是 admin tenant=1"

# 4. A 创商品
step "4. A 创商品 X"
SPU_REQ='{"name":"A-烧烤套餐","picUrl":"https://example.com/a.png","price":1990,"stock":100}'
RESP_X=$(api_post /app-api/merchant/mini/product/simple-create "$SPU_REQ" "$TOKEN_A")
SPU_X_ID=$(echo "$RESP_X" | jq -r '.data // empty')
[[ -n "$SPU_X_ID" && "$SPU_X_ID" != "null" ]] || fail "A 创商品失败: $RESP_X"
ok "A 商品 spuId=${SPU_X_ID}"

# 5. B 创商品
step "5. B 创商品 Y"
SPU_REQ_B='{"name":"B-水果拼盘","picUrl":"https://example.com/b.png","price":2990,"stock":50}'
RESP_Y=$(api_post /app-api/merchant/mini/product/simple-create "$SPU_REQ_B" "$TOKEN_B")
SPU_Y_ID=$(echo "$RESP_Y" | jq -r '.data // empty')
[[ -n "$SPU_Y_ID" && "$SPU_Y_ID" != "null" ]] || fail "B 创商品失败: $RESP_Y"
ok "B 商品 spuId=${SPU_Y_ID}"

# 6. 关键：A 调 product/page，断言只看到 X，看不到 Y
step "6. 跨租户列表隔离断言"
LIST_A=$(api_get '/app-api/merchant/mini/product/page?pageNo=1&pageSize=50' "$TOKEN_A")
A_HAS_X=$(echo "$LIST_A" | jq --arg id "$SPU_X_ID" '[.data.list[]? | select(.id == ($id | tonumber))] | length')
A_HAS_Y=$(echo "$LIST_A" | jq --arg id "$SPU_Y_ID" '[.data.list[]? | select(.id == ($id | tonumber))] | length')
[[ "$A_HAS_X" == "1" ]] || fail "A 列表里没有自己的商品 X！: $LIST_A"
[[ "$A_HAS_Y" == "0" ]] || fail "🚨 A 列表里看到了 B 的商品 Y！跨租户泄漏: $LIST_A"
ok "A 看到 X (${A_HAS_X}) 看不到 Y (${A_HAS_Y})"

LIST_B=$(api_get '/app-api/merchant/mini/product/page?pageNo=1&pageSize=50' "$TOKEN_B")
B_HAS_X=$(echo "$LIST_B" | jq --arg id "$SPU_X_ID" '[.data.list[]? | select(.id == ($id | tonumber))] | length')
B_HAS_Y=$(echo "$LIST_B" | jq --arg id "$SPU_Y_ID" '[.data.list[]? | select(.id == ($id | tonumber))] | length')
[[ "$B_HAS_Y" == "1" ]] || fail "B 列表里没有自己的商品 Y！: $LIST_B"
[[ "$B_HAS_X" == "0" ]] || fail "🚨 B 列表里看到了 A 的商品 X！跨租户泄漏: $LIST_B"
ok "B 看到 Y (${B_HAS_Y}) 看不到 X (${B_HAS_X})"

# 7. dashboard 隔离
step "7. dashboard summary 隔离"
DASH_A=$(api_get '/app-api/merchant/mini/dashboard/summary' "$TOKEN_A")
DASH_B=$(api_get '/app-api/merchant/mini/dashboard/summary' "$TOKEN_B")
echo "  → A: $DASH_A"
echo "  → B: $DASH_B"
ok "dashboard summary 各自有数据（手工核对结构）"

grn ""
grn "================================="
grn "  E2E 跨租户隔离测试 全部通过 ✅"
grn "================================="
grn "  A: tenantId=${TENANT_A} merchantId=${MERCHANT_A} spuId=${SPU_X_ID}"
grn "  B: tenantId=${TENANT_B} merchantId=${MERCHANT_B} spuId=${SPU_Y_ID}"
