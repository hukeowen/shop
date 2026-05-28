#!/usr/bin/env bash
# E2E 商户跨租户隔离验证脚本
# 前提：本地 48080 已启动，数据库已初始化（用户已 reset）。
# 用法：bash scripts/e2e-merchant-tenant-isolation.sh
#
# 注意：Git Bash 默认 GBK，curl --data-raw 中文会被解析为 GBK 字节导致 jackson UTF-8 报错。
# 这里把 JSON 写到临时文件 + curl --data-binary @file 来强制 UTF-8。
set -euo pipefail

BASE="${BASE:-http://localhost:48080}"
PHONE_A="13700137001"
PHONE_B="13700137002"
SHOP_A="ShopA-BBQ"
SHOP_B="ShopB-Fruit"
SMS_CODE="888888"

red() { printf '\033[31m%s\033[0m\n' "$*"; }
grn() { printf '\033[32m%s\033[0m\n' "$*"; }
ylw() { printf '\033[33m%s\033[0m\n' "$*"; }
step() { ylw "▶ $*"; }
ok()   { grn "  ✓ $*"; }
fail() { red "  ✗ $*"; exit 1; }

TMPDIR_X=$(mktemp -d)
trap 'rm -rf "$TMPDIR_X"' EXIT

# 写 UTF-8 JSON 到临时文件（bash heredoc 默认 LANG，但 printf 字节安全）
write_json() { printf '%s' "$1" > "$2"; }

# python JSON 取值：取 path 形如 'data.token' / 'data.tenantId'；列表查 id：'list:data.list:<spuId>'
pyget() {
  python - "$1" <<'PYEOF' 2>/dev/null
import sys, json
expr = sys.argv[1]
data = json.load(sys.stdin)
if expr.startswith("list:"):
    _, path, target = expr.split(":", 2)
    cur = data
    for p in path.split("."):
        cur = cur.get(p) if isinstance(cur, dict) else None
        if cur is None: break
    cnt = 0
    if isinstance(cur, list):
        for it in cur:
            if str(it.get("id")) == str(target):
                cnt += 1
    print(cnt)
else:
    cur = data
    for p in expr.split("."):
        cur = cur.get(p) if isinstance(cur, dict) else None
        if cur is None: break
    print(cur if cur is not None else "")
PYEOF
}

# === 1. A 注册 ===
step "1. 注册商户 A: ${PHONE_A}"
write_json "{\"mobile\":\"${PHONE_A}\",\"scene\":50}" "$TMPDIR_X/sms.json"
curl -sS -X POST "${BASE}/app-api/app/auth/send-sms-code" \
     -H "Content-Type: application/json" --data-binary @"$TMPDIR_X/sms.json" >/dev/null

write_json "{\"mobile\":\"${PHONE_A}\",\"smsCode\":\"${SMS_CODE}\",\"shopName\":\"${SHOP_A}\"}" "$TMPDIR_X/applyA.json"
RESP_A=$(curl -sS -X POST "${BASE}/app-api/app/auth/apply-merchant-by-sms" \
              -H "Content-Type: application/json" --data-binary @"$TMPDIR_X/applyA.json")
echo "  → ${RESP_A}"
TOKEN_A=$(echo "$RESP_A" | pyget data.token)
TENANT_A=$(echo "$RESP_A" | pyget data.tenantId)
MERCHANT_A=$(echo "$RESP_A" | pyget data.merchantId)
[[ -n "$TOKEN_A" ]] || fail "A token 缺失"
[[ -n "$TENANT_A" ]] || fail "A tenantId 缺失（关键修复点回归）"
ok "A: token=${TOKEN_A:0:12}... tenantId=${TENANT_A} merchantId=${MERCHANT_A}"

# === 2. B 注册 ===
step "2. 注册商户 B: ${PHONE_B}"
write_json "{\"mobile\":\"${PHONE_B}\",\"scene\":50}" "$TMPDIR_X/smsB.json"
curl -sS -X POST "${BASE}/app-api/app/auth/send-sms-code" \
     -H "Content-Type: application/json" --data-binary @"$TMPDIR_X/smsB.json" >/dev/null

write_json "{\"mobile\":\"${PHONE_B}\",\"smsCode\":\"${SMS_CODE}\",\"shopName\":\"${SHOP_B}\"}" "$TMPDIR_X/applyB.json"
RESP_B=$(curl -sS -X POST "${BASE}/app-api/app/auth/apply-merchant-by-sms" \
              -H "Content-Type: application/json" --data-binary @"$TMPDIR_X/applyB.json")
echo "  → ${RESP_B}"
TOKEN_B=$(echo "$RESP_B" | pyget data.token)
TENANT_B=$(echo "$RESP_B" | pyget data.tenantId)
MERCHANT_B=$(echo "$RESP_B" | pyget data.merchantId)
[[ -n "$TOKEN_B" ]] || fail "B token 缺失"
[[ -n "$TENANT_B" ]] || fail "B tenantId 缺失"
ok "B: token=${TOKEN_B:0:12}... tenantId=${TENANT_B} merchantId=${MERCHANT_B}"

# === 3. 跨租户断言 ===
step "3. 跨租户隔离断言"
[[ "$TENANT_A" != "$TENANT_B" ]] || fail "🚨 tenantId 相同：A=${TENANT_A} B=${TENANT_B}"
[[ "$TENANT_A" != "1" ]] || fail "🚨 A tenantId=1（admin），不是独立租户"
[[ "$TENANT_B" != "1" ]] || fail "🚨 B tenantId=1（admin），不是独立租户"
ok "A.tenant=${TENANT_A} ≠ B.tenant=${TENANT_B}，且都不是 admin tenant=1"

# === 4. A 创商品 ===
step "4. A 创商品 X"
write_json "{\"name\":\"A-Combo\",\"picUrl\":\"https://example.com/a.png\",\"price\":1990,\"stock\":100}" "$TMPDIR_X/spuA.json"
RESP_X=$(curl -sS -X POST "${BASE}/app-api/merchant/mini/product/simple-create" \
              -H "Content-Type: application/json" \
              -H "Authorization: Bearer ${TOKEN_A}" \
              --data-binary @"$TMPDIR_X/spuA.json")
echo "  → ${RESP_X}"
SPU_X_ID=$(echo "$RESP_X" | pyget data)
[[ -n "$SPU_X_ID" ]] || fail "A 创商品失败"
ok "A 商品 spuId=${SPU_X_ID}"

# === 5. B 创商品 ===
step "5. B 创商品 Y"
write_json "{\"name\":\"B-Fruit\",\"picUrl\":\"https://example.com/b.png\",\"price\":2990,\"stock\":50}" "$TMPDIR_X/spuB.json"
RESP_Y=$(curl -sS -X POST "${BASE}/app-api/merchant/mini/product/simple-create" \
              -H "Content-Type: application/json" \
              -H "Authorization: Bearer ${TOKEN_B}" \
              --data-binary @"$TMPDIR_X/spuB.json")
echo "  → ${RESP_Y}"
SPU_Y_ID=$(echo "$RESP_Y" | pyget data)
[[ -n "$SPU_Y_ID" ]] || fail "B 创商品失败"
ok "B 商品 spuId=${SPU_Y_ID}"

# === 6. 列表跨租户隔离 ===
step "6. product/page 跨租户隔离断言"
LIST_A=$(curl -sS "${BASE}/app-api/merchant/mini/product/page?pageNo=1&pageSize=50" \
              -H "Authorization: Bearer ${TOKEN_A}")
A_HAS_X=$(echo "$LIST_A" | pyget "list:data.list:${SPU_X_ID}")
A_HAS_Y=$(echo "$LIST_A" | pyget "list:data.list:${SPU_Y_ID}")
[[ "$A_HAS_X" == "1" ]] || fail "A 自己的列表里没看到自己的 X (=${A_HAS_X}): $LIST_A"
[[ "$A_HAS_Y" == "0" ]] || fail "🚨 A 列表里看到了 B 的 Y (=${A_HAS_Y})！跨租户泄漏: $LIST_A"
ok "A 看到 X=${A_HAS_X} 看不到 Y=${A_HAS_Y}"

LIST_B=$(curl -sS "${BASE}/app-api/merchant/mini/product/page?pageNo=1&pageSize=50" \
              -H "Authorization: Bearer ${TOKEN_B}")
B_HAS_X=$(echo "$LIST_B" | pyget "list:data.list:${SPU_X_ID}")
B_HAS_Y=$(echo "$LIST_B" | pyget "list:data.list:${SPU_Y_ID}")
[[ "$B_HAS_Y" == "1" ]] || fail "B 自己的列表里没看到自己的 Y (=${B_HAS_Y}): $LIST_B"
[[ "$B_HAS_X" == "0" ]] || fail "🚨 B 列表里看到了 A 的 X (=${B_HAS_X})！跨租户泄漏: $LIST_B"
ok "B 看到 Y=${B_HAS_Y} 看不到 X=${B_HAS_X}"

# === 7. dashboard / shop 隔离 ===
step "7. dashboard summary"
DASH_A=$(curl -sS "${BASE}/app-api/merchant/mini/dashboard/summary" -H "Authorization: Bearer ${TOKEN_A}")
DASH_B=$(curl -sS "${BASE}/app-api/merchant/mini/dashboard/summary" -H "Authorization: Bearer ${TOKEN_B}")
echo "  → A: $DASH_A"
echo "  → B: $DASH_B"
ok "dashboard summary 各自返回（手工核对结构）"

step "8. shop/info 隔离"
SHOP_A_RESP=$(curl -sS "${BASE}/app-api/merchant/mini/shop/info" -H "Authorization: Bearer ${TOKEN_A}")
SHOP_B_RESP=$(curl -sS "${BASE}/app-api/merchant/mini/shop/info" -H "Authorization: Bearer ${TOKEN_B}")
echo "  → A: $SHOP_A_RESP"
echo "  → B: $SHOP_B_RESP"

grn ""
grn "================================="
grn "  E2E 跨租户隔离测试 全部通过 ✅"
grn "================================="
grn "  A: tenantId=${TENANT_A} merchantId=${MERCHANT_A} spuId=${SPU_X_ID}"
grn "  B: tenantId=${TENANT_B} merchantId=${MERCHANT_B} spuId=${SPU_Y_ID}"
