#!/usr/bin/env bash
# =============================================================================
# v8 SPU 级星级奖池「手工结算」端到端验证脚本
#
# 前置：
#   1. 本机能通到 MySQL 远端 47.109.143.146:3306 / root / CHANGE_ME_*
#   2. 已 git pull + 部署最新代码（含本次 SPU 奖池结算改造）
#   3. 服务进程已重启（systemctl restart tanxiaer）
#
# 流程：
#   ① 造数据：拿 tenant_id=1002 spu_id=9（已有 pool_ratio=10、star_count=3 的 product_promo_config）
#   ② 给该 product_promo_config 写 pool_dist_rules（50% 三星均分 / 30% 二星抽奖 1 名 / 20% 一星均分）
#   ③ 造 9 个会员用户 + 写对应 shop_user_star 行（2 个 3 星 + 3 个 2 星 + 4 个 1 星，都 spu_id=9）
#   ④ 给 spu_star_pool 注入 10000 分（¥100）— 模拟历史入池
#   ⑤ 商户 admin 调 POST /admin-api/merchant/promo/pool/settle?spuId=9
#   ⑥ 校验：
#        - spu_star_pool.pool_balance = 0
#        - spu_star_pool.total_out = 10000
#        - settle_record: 1 行，distributed=10000，after=0
#        - payout_item: 2+1+4 = 7 行
#        - shop_user_point_log（推广积分流水）有 7 条 source_type=POOL_V8
#
# 用法：
#   bash scripts/v8-pool-settle-e2e.sh [--seed-only|--verify-only|--reset]
#
# 退出码：0=全部通过；非 0=有断言失败（打印细节）
# =============================================================================
set -euo pipefail

MYSQL_HOST="${MYSQL_HOST:-47.109.143.146}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-CHANGE_ME_5201314jxs@qq.com}"
MYSQL_DB="${MYSQL_DB:-ruoyi-vue-pro}"
API_BASE="${API_BASE:-http://47.109.143.146}"      # 走 nginx
ADMIN_TOKEN="${ADMIN_TOKEN:-}"                      # JWT；为空时跳过 API 调用环节

TENANT=1002
SPU=9
EXPECTED_DISTRIBUTED=10000

mysql_q() {
  mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" -p"$MYSQL_PASS" --batch -N "$MYSQL_DB" -e "$1" 2>&1 | grep -v "^mysql: \[Warning\]" || true
}

seed() {
  echo "[seed] tenant=$TENANT spu=$SPU 写入分配规则 + 用户/星级 + 池余额..."
  mysql_q "
-- 1. 写 pool_dist_rules（50% 3星均分 / 30% 2星抽奖 1 名 / 20% 1星均分）
UPDATE product_promo_config
SET pool_dist_rules='[{\"star\":3,\"ratio\":50,\"mode\":\"EQUAL\"},{\"star\":2,\"ratio\":30,\"mode\":\"LOTTERY\",\"winners\":1},{\"star\":1,\"ratio\":20,\"mode\":\"EQUAL\"}]'
WHERE tenant_id=$TENANT AND spu_id=$SPU;

-- 2. 造 9 个测试用户（id 9001..9009），tenant=$TENANT；存在则跳过
INSERT IGNORE INTO member_user (id, mobile, nickname, avatar, name, tenant_id, mark, register_ip, login_ip, password, status)
VALUES
  (9001, '13800009001', '张三星A', '', '', $TENANT, '', '127.0.0.1', '127.0.0.1', '', 0),
  (9002, '13800009002', '李三星B', '', '', $TENANT, '', '127.0.0.1', '127.0.0.1', '', 0),
  (9003, '13800009003', '王二星A', '', '', $TENANT, '', '127.0.0.1', '127.0.0.1', '', 0),
  (9004, '13800009004', '赵二星B', '', '', $TENANT, '', '127.0.0.1', '127.0.0.1', '', 0),
  (9005, '13800009005', '钱二星C', '', '', $TENANT, '', '127.0.0.1', '127.0.0.1', '', 0),
  (9006, '13800009006', '孙一星A', '', '', $TENANT, '', '127.0.0.1', '127.0.0.1', '', 0),
  (9007, '13800009007', '周一星B', '', '', $TENANT, '', '127.0.0.1', '127.0.0.1', '', 0),
  (9008, '13800009008', '吴一星C', '', '', $TENANT, '', '127.0.0.1', '127.0.0.1', '', 0),
  (9009, '13800009009', '郑一星D', '', '', $TENANT, '', '127.0.0.1', '127.0.0.1', '', 0);

-- 3. 写 shop_user_star（每个用户在 spu=$SPU 上的星级）
INSERT INTO shop_user_star (tenant_id, user_id, spu_id, current_star, direct_count, team_sales_count, team_sales_amount, promo_point_balance, consume_point_balance)
VALUES
  ($TENANT, 9001, $SPU, 3, 10, 0, 0, 0, 0),
  ($TENANT, 9002, $SPU, 3, 10, 0, 0, 0, 0),
  ($TENANT, 9003, $SPU, 2, 5, 0, 0, 0, 0),
  ($TENANT, 9004, $SPU, 2, 5, 0, 0, 0, 0),
  ($TENANT, 9005, $SPU, 2, 5, 0, 0, 0, 0),
  ($TENANT, 9006, $SPU, 1, 2, 0, 0, 0, 0),
  ($TENANT, 9007, $SPU, 1, 2, 0, 0, 0, 0),
  ($TENANT, 9008, $SPU, 1, 2, 0, 0, 0, 0),
  ($TENANT, 9009, $SPU, 1, 2, 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE current_star=VALUES(current_star);

-- 4. 注入池余额（模拟历史已入池 ¥100）
INSERT INTO spu_star_pool (tenant_id, spu_id, pool_balance, total_in, total_out)
VALUES ($TENANT, $SPU, 10000, 10000, 0)
ON DUPLICATE KEY UPDATE pool_balance=10000, total_in=10000, total_out=0;
"
  echo "[seed] OK"
}

reset() {
  echo "[reset] 清测试数据..."
  mysql_q "
DELETE FROM spu_star_pool_payout_item WHERE spu_id=$SPU;
DELETE FROM spu_star_pool_settle_record WHERE spu_id=$SPU;
DELETE FROM spu_star_pool WHERE spu_id=$SPU;
DELETE FROM shop_user_star WHERE user_id BETWEEN 9001 AND 9009 AND spu_id=$SPU;
DELETE FROM member_user WHERE id BETWEEN 9001 AND 9009;
UPDATE product_promo_config SET pool_dist_rules=NULL WHERE tenant_id=$TENANT AND spu_id=$SPU;
"
  echo "[reset] OK"
}

verify() {
  local PASS=0 FAIL=0
  step() { echo -e "\n— $1 —"; }
  assert_eq() {
    local label="$1" got="$2" want="$3"
    if [ "$got" = "$want" ]; then
      echo "  ✓ $label = $got"
      PASS=$((PASS+1))
    else
      echo "  ✗ $label: got=$got want=$want"
      FAIL=$((FAIL+1))
    fi
  }

  step "1. 池表"
  local pool=$(mysql_q "SELECT CONCAT(pool_balance,'|',total_in,'|',total_out) FROM spu_star_pool WHERE tenant_id=$TENANT AND spu_id=$SPU")
  assert_eq "spu_star_pool" "$pool" "0|10000|10000"

  step "2. 结算单"
  local rec=$(mysql_q "SELECT CONCAT(pool_balance_before,'|',pool_balance_after,'|',total_distributed) FROM spu_star_pool_settle_record WHERE tenant_id=$TENANT AND spu_id=$SPU ORDER BY id DESC LIMIT 1")
  assert_eq "结算单 before|after|distributed" "$rec" "10000|0|10000"

  step "3. 中奖明细行数"
  local payouts_cnt=$(mysql_q "SELECT COUNT(*) FROM spu_star_pool_payout_item p
    JOIN spu_star_pool_settle_record r ON p.settle_id=r.id
    WHERE r.tenant_id=$TENANT AND r.spu_id=$SPU")
  assert_eq "payout 行数" "$payouts_cnt" "7"

  step "4. 推广积分流水（按 source_type=POOL_V8 计数）"
  # shop_promo_record 是双积分账本流水表
  local promo_cnt=$(mysql_q "SELECT COUNT(*) FROM shop_promo_record
    WHERE source_type='POOL_V8' AND user_id BETWEEN 9001 AND 9009")
  assert_eq "POOL_V8 流水行数" "$promo_cnt" "7"

  step "5. 实付积分总额（应 = 10000）"
  local total=$(mysql_q "SELECT IFNULL(SUM(amount),0) FROM shop_promo_record
    WHERE source_type='POOL_V8' AND user_id BETWEEN 9001 AND 9009")
  assert_eq "POOL_V8 累计积分" "$total" "10000"

  step "6. 每星分配"
  # 3星：2 人 × 2500
  local s3=$(mysql_q "SELECT CONCAT(COUNT(*),'|',IFNULL(MIN(amount),0),'|',IFNULL(MAX(amount),0)) FROM spu_star_pool_payout_item p
    JOIN spu_star_pool_settle_record r ON p.settle_id=r.id
    WHERE r.tenant_id=$TENANT AND r.spu_id=$SPU AND p.star=3")
  assert_eq "3星: cnt|min|max" "$s3" "2|2500|2500"

  # 2星：抽奖 1 人 × 3000
  local s2=$(mysql_q "SELECT CONCAT(COUNT(*),'|',IFNULL(MIN(amount),0),'|',IFNULL(MAX(amount),0)) FROM spu_star_pool_payout_item p
    JOIN spu_star_pool_settle_record r ON p.settle_id=r.id
    WHERE r.tenant_id=$TENANT AND r.spu_id=$SPU AND p.star=2")
  assert_eq "2星: cnt|min|max" "$s2" "1|3000|3000"

  # 1星：4 人 × 500
  local s1=$(mysql_q "SELECT CONCAT(COUNT(*),'|',IFNULL(MIN(amount),0),'|',IFNULL(MAX(amount),0)) FROM spu_star_pool_payout_item p
    JOIN spu_star_pool_settle_record r ON p.settle_id=r.id
    WHERE r.tenant_id=$TENANT AND r.spu_id=$SPU AND p.star=1")
  assert_eq "1星: cnt|min|max" "$s1" "4|500|500"

  echo -e "\n=== 通过 $PASS 项 / 失败 $FAIL 项 ==="
  [ "$FAIL" = "0" ] && return 0 || return 1
}

case "${1:-}" in
  --seed-only) seed ;;
  --verify-only) verify ;;
  --reset) reset ;;
  *)
    reset
    seed
    if [ -z "$ADMIN_TOKEN" ]; then
      echo
      echo "[i] ADMIN_TOKEN 未设置 — 跳过 API 调用环节。"
      echo "    请在浏览器登录 PC 后台后从 localStorage.getItem('ACCESS_TOKEN') 拿 token，"
      echo "    然后导出 ADMIN_TOKEN=<token> 重跑本脚本。"
      echo
      echo "    或直接打开 PC 后台「商品营销配置」→ 输 SPU=9 → 拉取 → 点「立即结算」"
      echo "    然后跑：bash $0 --verify-only"
      exit 0
    fi
    echo "[settle] 调 API 触发结算..."
    curl -fsS -X POST "$API_BASE/admin-api/merchant/promo/pool/settle?spuId=$SPU" \
      -H "Authorization: Bearer $ADMIN_TOKEN" \
      -H "tenant-id: $TENANT" | head -c 500
    echo
    sleep 1
    verify
    ;;
esac
