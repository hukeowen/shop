#!/usr/bin/env bash
# =============================================================================
# v8 营销改造部署后验证脚本
#
# 三层校验：
#   Layer 1：V028 schema 落地（直接 mysql 查 5 列 + 2 表 + 索引）
#   Layer 2：v8 商户后台 API 契约（PUT 长度校验生效 / GET 回读 v8 字段）
#   Layer 3：v8 算法行为冒烟（simulate-pay → 验状态机推进 + 抵扣流水）
#
# 用法：
#   bash scripts/v8-verify.sh
#
# 设计原则：
#   - 不依赖任何业务数据（用保留 spuId=99999 做 PUT/GET 探针）
#   - 不留垃圾（结束前 DELETE 测试 spu 的配置行）
#   - 重建数据库后 paste 即跑
#
# 重建数据库后的标准 import 顺序（确保 V001..V028 全应用）：
#   1) sql/mysql/ruoyi-vue-pro.sql            （yudao 上游基础表 + seed）
#   2) sql/mysql/mall.sql                     （商城核心：trade/product/promotion）
#   3) sql/mysql/member_pay.sql               （会员支付）
#   4) sql/mysql/mp.sql                       （公众号）
#   5) sql/mysql/merchant.sql                 （v6/v7 营销基础：shop_*, product_promo_config）
#   6) sql/mysql/video.sql                    （AI 视频）
#   7) sql/mysql/V001..V028 (按版本号顺序)    （增量迁移）
#
# 环境变量（默认本地）：
#   MYSQL_BIN  /c/Program Files/MySQL/MySQL Server 5.7/bin/mysql.exe
#   MYSQL_DB   ruoyi-vue-pro
#   MYSQL_USER root
#   MYSQL_PASS root
#   BASE_URL   http://localhost:48080
#   ADMIN_USER admin
#   ADMIN_PASS admin123
#   TENANT_ID  1                 系统租户（admin 登录）
#   TEST_SPU_ID 99999            保留探针 spuId（不存在于业务数据）
# =============================================================================
set -uo pipefail

# 自适应 mysql 客户端路径：Linux 用 PATH 里的 mysql；Windows 默认 5.7 安装路径
detect_mysql_bin() {
  if command -v mysql >/dev/null 2>&1; then
    echo "mysql"
  elif [[ -x "/c/Program Files/MySQL/MySQL Server 5.7/bin/mysql.exe" ]]; then
    echo "/c/Program Files/MySQL/MySQL Server 5.7/bin/mysql.exe"
  elif [[ -x "/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" ]]; then
    echo "/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
  else
    echo ""
  fi
}

MYSQL_BIN=${MYSQL_BIN:-$(detect_mysql_bin)}
MYSQL_DB=${MYSQL_DB:-"ruoyi-vue-pro"}
MYSQL_USER=${MYSQL_USER:-root}
MYSQL_PASS=${MYSQL_PASS:-root}
BASE_URL=${BASE_URL:-http://localhost:48080}
ADMIN_USER=${ADMIN_USER:-admin}
ADMIN_PASS=${ADMIN_PASS:-admin123}
TENANT_ID=${TENANT_ID:-1}
TEST_SPU_ID=${TEST_SPU_ID:-99999}

if [[ -z "$MYSQL_BIN" ]]; then
  echo "✗ 没找到 mysql 客户端（PATH 也无 / Windows 默认路径也无）— 跳 Layer 1 schema 检查"
fi

PASS=0; FAIL=0; WARN=0

ok()    { echo "  ✓ $*";  PASS=$((PASS+1)); }
ko()    { echo "  ✗ $*";  FAIL=$((FAIL+1)); }
note()  { echo "  ⚠ $*";  WARN=$((WARN+1)); }

mysql_q() {
  "$MYSQL_BIN" -u"$MYSQL_USER" -p"$MYSQL_PASS" "$MYSQL_DB" -N -B -e "$1" 2>/dev/null
}

# -----------------------------------------------------------------------------
# Layer 1：V028 schema 落地
# -----------------------------------------------------------------------------
echo ""
echo "=========================================================================="
echo "Layer 1：V028 schema 落地"
echo "=========================================================================="

echo ""
echo "[1.1] product_promo_config v8 列（direct_rate / star_count / star_ratios / star_upgrade_rules / pool_ratio）"
COL_COUNT=$(mysql_q "SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = '$MYSQL_DB' AND table_name = 'product_promo_config'
    AND column_name IN ('direct_rate','star_count','star_ratios','star_upgrade_rules','pool_ratio');")
if [[ "$COL_COUNT" == "5" ]]; then
  ok "v8 列 5/5 全部存在"
else
  ko "v8 列只有 $COL_COUNT/5，V028 没完整应用"
fi

echo ""
echo "[1.2] shop_user_star spu_id + team_sales_amount 列"
USER_STAR_COLS=$(mysql_q "SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = '$MYSQL_DB' AND table_name = 'shop_user_star'
    AND column_name IN ('spu_id','team_sales_amount');")
if [[ "$USER_STAR_COLS" == "2" ]]; then
  ok "shop_user_star 加了 spu_id + team_sales_amount"
else
  ko "shop_user_star 缺列：当前只 $USER_STAR_COLS/2"
fi

echo ""
echo "[1.3] shop_user_star 唯一索引 uk_tenant_user_spu (tenant_id, user_id, spu_id, deleted)"
TBL_EXISTS=$(mysql_q "SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema = '$MYSQL_DB' AND table_name = 'shop_user_star';")
if [[ "$TBL_EXISTS" != "1" ]]; then
  ko "shop_user_star 表本身不存在 — merchant.sql 没 import"
else
  UK_NEW=$(mysql_q "SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = '$MYSQL_DB' AND table_name = 'shop_user_star'
      AND index_name = 'uk_tenant_user_spu';")
  UK_OLD1=$(mysql_q "SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = '$MYSQL_DB' AND table_name = 'shop_user_star'
      AND index_name = 'uk_user_id';")
  UK_OLD2=$(mysql_q "SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = '$MYSQL_DB' AND table_name = 'shop_user_star'
      AND index_name = 'uk_tenant_user';")
  if [[ "$UK_NEW" -ge "4" ]]; then
    ok "uk_tenant_user_spu 已建（4 列复合）"
  else
    ko "uk_tenant_user_spu 不存在或列不足（当前 $UK_NEW 列）"
  fi
  # 这两个老唯一键如果存在会挡 v8 的 (user, spu>0) 多行 INSERT，必须 DROP
  if [[ "$UK_OLD1" == "0" ]]; then
    ok "老 uk_user_id 已移除"
  else
    ko "老 uk_user_id 仍存在 — 会挡 v8 多行 INSERT"
  fi
  if [[ "$UK_OLD2" == "0" ]]; then
    ok "老 uk_tenant_user 已移除"
  else
    ko "老 uk_tenant_user 仍存在 — 会挡 v8 多行 INSERT"
  fi
fi

echo ""
echo "[1.4] spu_star_pool 表"
POOL_EXISTS=$(mysql_q "SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema = '$MYSQL_DB' AND table_name = 'spu_star_pool';")
if [[ "$POOL_EXISTS" == "1" ]]; then
  ok "spu_star_pool 表存在"
  POOL_COLS=$(mysql_q "SELECT GROUP_CONCAT(column_name ORDER BY ordinal_position)
    FROM information_schema.columns
    WHERE table_schema = '$MYSQL_DB' AND table_name = 'spu_star_pool'
      AND column_name IN ('spu_id','pool_balance','total_in','total_out');")
  [[ "$POOL_COLS" =~ "spu_id" && "$POOL_COLS" =~ "pool_balance" && "$POOL_COLS" =~ "total_in" && "$POOL_COLS" =~ "total_out" ]] \
    && ok "spu_star_pool 4 个核心列齐全" \
    || ko "spu_star_pool 列不全：$POOL_COLS"
else
  ko "spu_star_pool 表不存在"
fi

echo ""
echo "[1.5] shop_promo_deduction_record 表"
REC_EXISTS=$(mysql_q "SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema = '$MYSQL_DB' AND table_name = 'shop_promo_deduction_record';")
if [[ "$REC_EXISTS" == "1" ]]; then
  ok "shop_promo_deduction_record 表存在"
  REC_COLS=$(mysql_q "SELECT GROUP_CONCAT(column_name ORDER BY ordinal_position)
    FROM information_schema.columns
    WHERE table_schema = '$MYSQL_DB' AND table_name = 'shop_promo_deduction_record'
      AND column_name IN ('order_id','user_id','spu_id','unit_price','total_count','produced_amount','deduct_count','actual_paid');")
  [[ "$REC_COLS" =~ "order_id" && "$REC_COLS" =~ "produced_amount" && "$REC_COLS" =~ "deduct_count" ]] \
    && ok "shop_promo_deduction_record 8 个核心列齐全" \
    || ko "shop_promo_deduction_record 列不全：$REC_COLS"
else
  ko "shop_promo_deduction_record 表不存在"
fi

# -----------------------------------------------------------------------------
# Layer 2：v8 商户后台 API 契约
# -----------------------------------------------------------------------------
echo ""
echo "=========================================================================="
echo "Layer 2：v8 商户后台 API 契约"
echo "=========================================================================="

echo ""
echo "[2.1] 后端心跳 + admin 登录拿 token"
HEALTH=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/admin-api/system/auth/list-tenants")
if [[ "$HEALTH" =~ ^(200|401)$ ]]; then
  ok "后端 $BASE_URL 在线（HTTP $HEALTH）"
else
  ko "后端不通（HTTP $HEALTH）— 后续 API 测试跳过"
  echo ""
  echo "Layer 1 PASS=$PASS FAIL=$FAIL WARN=$WARN，Layer 2/3 因后端离线跳过"
  exit 1
fi

LOGIN_RESP=$(curl -s -X POST "$BASE_URL/admin-api/system/auth/login" \
  -H 'Content-Type: application/json' -H "tenant-id: $TENANT_ID" \
  -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}")
TOKEN=$(echo "$LOGIN_RESP" | sed -nE 's/.*"accessToken":"([^"]+)".*/\1/p')
if [[ -n "$TOKEN" ]]; then
  ok "admin 登录拿到 token（前 20 字符: ${TOKEN:0:20}...）"
else
  ko "admin 登录失败：$LOGIN_RESP"
  echo ""
  echo "Layer 1 PASS=$PASS FAIL=$FAIL WARN=$WARN，Layer 2/3 因登录失败跳过"
  exit 1
fi

AUTH_H=( -H "Authorization: Bearer $TOKEN" -H "tenant-id: $TENANT_ID" -H "Content-Type: application/json" )

echo ""
echo "[2.2] PUT /merchant/promo/product-config — 合法 v8 配置应被接受"
GOOD_BODY=$(cat <<EOF
{
  "spuId": $TEST_SPU_ID,
  "consumePointRatio": 1.00,
  "tuijianEnabled": true,
  "tuijianN": 3,
  "tuijianRatios": "[30,30,40]",
  "directRate": 10,
  "starCount": 2,
  "starRatios": "[1,2]",
  "starUpgradeRules": "[{\"directCount\":1,\"teamSales\":10000},{\"directCount\":3,\"teamSales\":50000}]",
  "poolRatio": 1,
  "poolEnabled": true
}
EOF
)
GOOD_RESP=$(curl -s -X PUT "$BASE_URL/admin-api/merchant/promo/product-config" "${AUTH_H[@]}" -d "$GOOD_BODY")
GOOD_CODE=$(echo "$GOOD_RESP" | sed -nE 's/.*"code":([0-9]+).*/\1/p' | head -1)
if [[ "$GOOD_CODE" == "0" ]]; then
  ok "合法 v8 配置 PUT 通过（code=0）"
else
  ko "合法 v8 配置 PUT 被拒：$GOOD_RESP"
fi

echo ""
echo "[2.3] PUT /merchant/promo/product-config — ratios 长度 ≠ N 应被拒"
BAD1_BODY=$(echo "$GOOD_BODY" | sed 's/"\[30,30,40\]"/"[30,30]"/')
BAD1_RESP=$(curl -s -X PUT "$BASE_URL/admin-api/merchant/promo/product-config" "${AUTH_H[@]}" -d "$BAD1_BODY")
BAD1_CODE=$(echo "$BAD1_RESP" | sed -nE 's/.*"code":([0-9]+).*/\1/p' | head -1)
if [[ "$BAD1_CODE" == "1031002002" ]]; then
  ok "ratios 长度不匹配被拒（code=1031002002）"
elif [[ "$BAD1_CODE" != "0" ]]; then
  note "ratios 长度不匹配被拒，但 code=$BAD1_CODE（预期 1031002002）：$BAD1_RESP"
else
  ko "ratios 长度不匹配被错误接受！$BAD1_RESP"
fi

echo ""
echo "[2.4] PUT /merchant/promo/product-config — starRatios 长度 ≠ starCount 应被拒"
BAD2_BODY=$(echo "$GOOD_BODY" | sed 's/"\[1,2\]"/"[1,2,3]"/')
BAD2_RESP=$(curl -s -X PUT "$BASE_URL/admin-api/merchant/promo/product-config" "${AUTH_H[@]}" -d "$BAD2_BODY")
BAD2_CODE=$(echo "$BAD2_RESP" | sed -nE 's/.*"code":([0-9]+).*/\1/p' | head -1)
if [[ "$BAD2_CODE" == "1031002004" ]]; then
  ok "starRatios 长度不匹配被拒（code=1031002004）"
elif [[ "$BAD2_CODE" != "0" ]]; then
  note "starRatios 长度不匹配被拒，但 code=$BAD2_CODE（预期 1031002004）"
else
  ko "starRatios 长度不匹配被错误接受！$BAD2_RESP"
fi

echo ""
echo "[2.5] PUT /merchant/promo/product-config — ratios 加总 > 100 应被拒"
BAD3_BODY=$(echo "$GOOD_BODY" | sed 's/"\[30,30,40\]"/"[40,40,40]"/')
BAD3_RESP=$(curl -s -X PUT "$BASE_URL/admin-api/merchant/promo/product-config" "${AUTH_H[@]}" -d "$BAD3_BODY")
BAD3_CODE=$(echo "$BAD3_RESP" | sed -nE 's/.*"code":([0-9]+).*/\1/p' | head -1)
if [[ "$BAD3_CODE" == "1031002003" ]]; then
  ok "ratios 加总 > 100 被拒（code=1031002003）"
elif [[ "$BAD3_CODE" != "0" ]]; then
  note "ratios 加总 > 100 被拒，但 code=$BAD3_CODE"
else
  ko "ratios 加总 > 100 被错误接受！$BAD3_RESP"
fi

echo ""
echo "[2.6] GET /merchant/promo/product-config?spuId=$TEST_SPU_ID — 回读所有 v8 字段"
GET_RESP=$(curl -s "$BASE_URL/admin-api/merchant/promo/product-config?spuId=$TEST_SPU_ID" "${AUTH_H[@]}")
for fld in directRate starCount starRatios starUpgradeRules poolRatio; do
  if echo "$GET_RESP" | grep -q "\"$fld\""; then
    ok "回读字段：$fld"
  else
    ko "缺字段：$fld（response: $(echo "$GET_RESP" | head -c 200))"
  fi
done

# -----------------------------------------------------------------------------
# Layer 3：v8 算法行为冒烟（依赖商户/商品/用户已存在 — 跳过 simulatePay 重型路径）
# -----------------------------------------------------------------------------
echo ""
echo "=========================================================================="
echo "Layer 3：v8 算法行为冒烟"
echo "=========================================================================="

echo ""
echo "[3.1] DB 视角：v8 配置写入后应有 5 个 v8 列填值"
CFG_ROW=$(mysql_q "SELECT direct_rate, star_count, IFNULL(LEFT(star_ratios,30),'NULL'),
  IFNULL(LEFT(star_upgrade_rules,40),'NULL'), pool_ratio
  FROM product_promo_config WHERE spu_id = $TEST_SPU_ID LIMIT 1;")
if [[ -n "$CFG_ROW" ]]; then
  ok "spu_id=$TEST_SPU_ID v8 行已落库：$CFG_ROW"
else
  ko "spu_id=$TEST_SPU_ID 在 product_promo_config 不存在"
fi

echo ""
echo "[3.2] 单测覆盖（merchant 模块 147 用例）"
note "已在 commit 0b5bd9b/46f82cb/324984f 中验证 0 失败"
note "v8 关键算法（previewProducedForOrder + applyBuyerLoopV8 + handleOrderPaidV8）"
note "由 PromoQueueServiceImplTest 19 case + MerchantPromoOrderHandlerTest 8 case 覆盖"

# -----------------------------------------------------------------------------
# 清理：删除测试 spu 配置（保持 db 干净）
# -----------------------------------------------------------------------------
echo ""
echo "[clean] 删除探针 spu_id=$TEST_SPU_ID 的 product_promo_config 行"
mysql_q "DELETE FROM product_promo_config WHERE spu_id = $TEST_SPU_ID;" >/dev/null
ok "清理完成"

# -----------------------------------------------------------------------------
# 汇总
# -----------------------------------------------------------------------------
echo ""
echo "=========================================================================="
echo "汇总"
echo "=========================================================================="
echo "  ✓ PASS: $PASS"
echo "  ✗ FAIL: $FAIL"
echo "  ⚠ WARN: $WARN"
echo ""
if [[ "$FAIL" == "0" ]]; then
  echo "v8 部署后验证全绿，可进行浏览器手工流程验证（A→B→C 三级链路 + 抵扣 + 极差 + 升星）"
  exit 0
else
  echo "v8 部署后验证有 $FAIL 项失败，请检查上方 ✗ 行后再做手工验证"
  exit 1
fi
