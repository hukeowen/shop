#!/usr/bin/env bash
# 验证「商户自建商品分类」端到端：
#   1. SMS 注册商户 (apply-merchant-by-sms)
#   2. GET  /app-api/merchant/mini/product/category/list   → 初始列表
#   3. POST /app-api/merchant/mini/product/category/create?name=xxx → 拿 id
#   4. GET  /list 再来一遍 → 包含 xxx
#   5. POST /simple-create 用 categoryName=xxx 上架 → SPU.categoryId == 新 id
set -uo pipefail

BASE="${BASE:-http://localhost:48080}"
SMS_CODE="888888"
PHONE="139$(date +%H%M%S)00"     # 临时手机号，避免冲突
SHOP="测试店-$(date +%s)"
CAT_NAME="自建分类-$(date +%s)"
TMP=$(mktemp -d); trap "rm -rf $TMP" EXIT

PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); echo "  ✓ $*"; }
bad()  { FAIL=$((FAIL+1)); echo "  ✗ $*"; }
jq_get() { python3 -c "import sys,json; d=json.load(sys.stdin)
for p in '$1'.split('.'):
  d=d.get(p) if isinstance(d,dict) else None
  if d is None: break
print(d if d is not None else '')" 2>/dev/null; }

step() { echo; echo "▶ $*"; }

# 1. send-sms + apply
step "1) 注册商户 phone=$PHONE shop=$SHOP"
printf '{"mobile":"%s","scene":50}' "$PHONE" > "$TMP/sms.json"
curl -sS -X POST "$BASE/app-api/app/auth/send-sms-code" \
  -H "Content-Type: application/json" --data-binary @"$TMP/sms.json" > /dev/null

printf '{"mobile":"%s","smsCode":"%s","shopName":"%s"}' "$PHONE" "$SMS_CODE" "$SHOP" > "$TMP/apply.json"
RESP=$(curl -sS -X POST "$BASE/app-api/app/auth/apply-merchant-by-sms" \
  -H "Content-Type: application/json" --data-binary @"$TMP/apply.json")
echo "  apply resp: $RESP"
TOKEN=$(echo "$RESP" | jq_get data.token)
TENANT=$(echo "$RESP" | jq_get data.tenantId)
[[ -n "$TOKEN" && -n "$TENANT" ]] && ok "token+tenantId OK (tenant=$TENANT)" || { bad "apply 失败"; exit 1; }

H_AUTH=(-H "Authorization: Bearer $TOKEN" -H "tenant-id: $TENANT")

# 2. GET list
step "2) GET /category/list 初始"
LIST1=$(curl -sS "${H_AUTH[@]}" "$BASE/app-api/merchant/mini/product/category/list")
echo "  list1: $LIST1"
CODE=$(echo "$LIST1" | jq_get code)
[[ "$CODE" == "0" ]] && ok "list 返回 code=0" || { bad "list code=$CODE"; exit 1; }

# 3. POST create
step "3) POST /category/create name=$CAT_NAME"
CREATE=$(curl -sS -X POST "${H_AUTH[@]}" \
  "$BASE/app-api/merchant/mini/product/category/create?name=$(python3 -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))" "$CAT_NAME")")
echo "  create: $CREATE"
CAT_ID=$(echo "$CREATE" | jq_get data)
[[ -n "$CAT_ID" && "$CAT_ID" != "None" ]] && ok "create 拿到 catId=$CAT_ID" || { bad "create 失败"; exit 1; }

# 4. GET list 再来一遍
step "4) 再 GET /category/list 验证包含新建分类"
LIST2=$(curl -sS "${H_AUTH[@]}" "$BASE/app-api/merchant/mini/product/category/list")
echo "  list2: $LIST2"
if echo "$LIST2" | grep -q "$CAT_NAME"; then
  ok "新分类已出现在列表"
else
  bad "新分类未出现在列表"
fi

# 5. simple-create with categoryName → SPU.categoryId == CAT_ID
step "5) 用新分类上架商品"
printf '{"name":"测试新建分类商品","picUrl":"https://example.com/x.png","price":999,"stock":10,"categoryName":"%s"}' "$CAT_NAME" > "$TMP/spu.json"
SPU=$(curl -sS -X POST "${H_AUTH[@]}" \
  -H "Content-Type: application/json" \
  --data-binary @"$TMP/spu.json" \
  "$BASE/app-api/merchant/mini/product/simple-create")
echo "  spu: $SPU"
SPU_ID=$(echo "$SPU" | jq_get data)
[[ -n "$SPU_ID" && "$SPU_ID" != "None" ]] && ok "SPU 创建成功 spuId=$SPU_ID" || bad "SPU 创建失败"

# 5b. 反查 SPU 的 categoryId
if [[ -n "$SPU_ID" && "$SPU_ID" != "None" ]]; then
  DETAIL=$(curl -sS "${H_AUTH[@]}" "$BASE/app-api/product/spu/get-detail?id=$SPU_ID")
  SPU_CAT=$(echo "$DETAIL" | jq_get data.categoryId)
  if [[ "$SPU_CAT" == "$CAT_ID" ]]; then
    ok "SPU.categoryId=$SPU_CAT == 新建分类 id=$CAT_ID（findOrCreateCategory 命中）"
  else
    bad "SPU.categoryId=$SPU_CAT ≠ 新建分类 id=$CAT_ID"
  fi
fi

echo
echo "================================================================"
echo "结果: $PASS PASS / $FAIL FAIL"
echo "================================================================"
exit $FAIL
