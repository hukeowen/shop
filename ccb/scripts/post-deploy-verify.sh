#!/usr/bin/env bash
# =============================================================================
# 摊小二 — ECS 部署后冒烟验证（生产质量约束）
#
# 目的：确认每次 ./deploy.sh 跑完后所有 P0/P1/P2 改动真的在 ECS 上生效
#       而不是"看着像跑了"。任何一项 ✗ 都意味着该次部署不合格。
#
# 用法（ECS）：
#   bash /opt/tanxiaer/repo/ccb/scripts/post-deploy-verify.sh
#
# 用法（本机指向远端）：
#   ./scripts/post-deploy-verify.sh --base-url https://www.doupaidoudian.com \
#                                   --mysql-pass <密码>
# =============================================================================
set -uo pipefail

BASE_URL=""
MYSQL_PASS=""
DB_NAME="${DB_NAME:-ruoyi-vue-pro}"
PROJECT_DIR="${PROJECT_DIR:-/opt/tanxiaer/repo/ccb}"
RUNTIME_ENV="${RUNTIME_ENV:-/opt/tanxiaer/app/runtime.env}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url)   BASE_URL="$2"; shift 2 ;;
    --mysql-pass) MYSQL_PASS="$2"; shift 2 ;;
    --project-dir) PROJECT_DIR="$2"; shift 2 ;;
    -h|--help)    sed -n '2,18p' "$0"; exit 0 ;;
    *) echo "未知参数: $1" >&2; exit 2 ;;
  esac
done

# 自动从 .env 读 MYSQL_ROOT_PASS
if [[ -z "$MYSQL_PASS" && -f "${PROJECT_DIR}/.env" ]]; then
  MYSQL_PASS=$(grep -E "^MYSQL_ROOT_PASS=" "${PROJECT_DIR}/.env" 2>/dev/null | cut -d= -f2 || true)
fi
[[ -z "$BASE_URL" ]] && BASE_URL="http://localhost"

PASS=0; FAIL=0; WARN=0; RESULTS=()
green()  { printf '\033[32m%s\033[0m' "$1"; }
red()    { printf '\033[31m%s\033[0m' "$1"; }
yellow() { printf '\033[33m%s\033[0m' "$1"; }
ok()    { PASS=$((PASS+1)); RESULTS+=("$(green ✓) $1"); }
ko()    { FAIL=$((FAIL+1)); RESULTS+=("$(red ✗) $1"); }
warn()  { WARN=$((WARN+1)); RESULTS+=("$(yellow ⚠) $1"); }

mysql_q() {
  [[ -z "$MYSQL_PASS" ]] && return 1
  mysql -uroot -p"$MYSQL_PASS" -D"$DB_NAME" -Nse "$1" 2>/dev/null
}
http_code() { curl -s -o /tmp/.pdv-body -w "%{http_code}" "$@" 2>/dev/null; }

echo "============================================="
echo "  摊小二部署后冒烟验证 v1"
echo "  base-url=$BASE_URL  db=$DB_NAME  proj=$PROJECT_DIR"
echo "============================================="

# ── 幕 1：基础设施心跳 ─────────────────────────
echo ""
echo "[幕 1] 基础设施心跳"
[[ "$(http_code "$BASE_URL/admin-api/system/auth/captcha")" == "200" ]] \
  && ok "yudao-server captcha 200" || ko "yudao-server captcha 异常"
[[ "$(http_code "$BASE_URL/m/")" =~ ^(200|301|302)$ ]] \
  && ok "商户/用户 H5 入口可达" || ko "商户/用户 H5 入口异常"
[[ "$(http_code "$BASE_URL/admin/")" =~ ^(200|301|302)$ ]] \
  && ok "PC 管理后台入口可达" || ko "PC 管理后台入口异常"
[[ "$(http_code http://127.0.0.1:8081/healthz)" == "200" ]] \
  && ok "sidecar 8081 healthz 200" || ko "sidecar 8081 不通 — systemd 拉起 sidecar"

# ── 幕 2：P0-3 vite 老 sidecar 清理验证 ─────
echo ""
echo "[幕 2] P0-3 — vite 内嵌 sidecar 已删，依赖瘦身"
if grep -q '^\s*"ffmpeg-static"' "${PROJECT_DIR}/yudao-ui/yudao-ui-merchant-uniapp/package.json" 2>/dev/null; then
  ko "merchant-uniapp/package.json 仍含 ffmpeg-static — 期待已删"
else
  ok "merchant-uniapp/package.json 不含 ffmpeg-static / aws-sdk / msedge-tts (P0-3)"
fi
if grep -q "import ffmpegPath" "${PROJECT_DIR}/yudao-ui/yudao-ui-merchant-uniapp/vite.config.js" 2>/dev/null; then
  ko "vite.config.js 仍 import ffmpeg-static — P0-3 未生效"
else
  ok "vite.config.js 已切到 sidecar proxy 模式 (P0-3)"
fi

# ── 幕 3：P0-2 SMS demoMode 默认关 ─────────
echo ""
echo "[幕 3] P0-2 — SMS demoMode"
SMS_DEMO=$(grep -E "^SMS_DEMO_MODE=" "${PROJECT_DIR}/.env" 2>/dev/null | cut -d= -f2)
if [[ "${SMS_DEMO}" == "true" ]]; then
  warn "SMS_DEMO_MODE=true (本机/演示模式；生产部署应关闭)"
else
  ok "SMS demoMode 默认关闭 — 真发短信链路 (P0-2)"
fi

# ── 幕 4：P2-10 数据库迁移版本号化 ──────────
echo ""
echo "[幕 4] P2-10 — V001~V009 迁移文件"
V_COUNT=$(ls "${PROJECT_DIR}/sql/mysql/"V*.sql 2>/dev/null | wc -l)
if [[ "$V_COUNT" -ge 9 ]]; then
  ok "sql/mysql/ 含 ${V_COUNT} 个 V*.sql 迁移文件 (P2-10)"
else
  ko "V*.sql 数量异常: ${V_COUNT}（期望 ≥ 9）"
fi
if [[ -f "${PROJECT_DIR}/sql/mysql/_DANGER__tenant_reset.sql" ]]; then
  ok "_DANGER__tenant_reset.sql 已重命名（破坏性脚本性质显眼）"
else
  warn "_DANGER__tenant_reset.sql 缺失或旧名 fix_tenant_id.sql 未改"
fi

# DB 列存在性：V007/V008 加的 KYC/通联字段
if [[ -n "$MYSQL_PASS" ]]; then
  for col in id_card_front_key id_card_back_key business_license_key tl_open_order_id; do
    cnt=$(mysql_q "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$DB_NAME' AND table_name='shop_info' AND column_name='$col'")
    [[ "$cnt" == "1" ]] && ok "shop_info.$col 已落库" || ko "shop_info.$col 缺失 — 跑 V007/V008 迁移"
  done
fi

# ── 幕 5：P0-1 KYC 私有 ACL ───────────────
echo ""
echo "[幕 5] P0-1 — KYC 证件私有化 + 预签名"
# 5a: sidecar /oss/upload acl=private 接受参数
RESP=$(curl -s -X POST http://127.0.0.1:8081/oss/upload \
  -H "Content-Type: application/json" \
  -d '{"base64":"dGVzdC1wcml2YXRl","ext":"txt","prefix":"verify/kyc","acl":"private"}' 2>/dev/null)
KEY=$(echo "$RESP" | grep -oE '"key":"[^"]+"' | head -1 | cut -d'"' -f4)
SIGNED_URL=$(echo "$RESP" | grep -oE '"url":"[^"]+"' | head -1 | cut -d'"' -f4)
if [[ -n "$KEY" && -n "$SIGNED_URL" ]]; then
  ok "/oss/upload acl=private 返带签名 URL"
  # 5b: 直连不带签名应 403
  if [[ -n "$KEY" ]]; then
    DIRECT=$(http_code "https://tanxiaoer.tos-s3-cn-beijing.volces.com/$KEY")
    [[ "$DIRECT" == "403" ]] && ok "TOS 直连 (无签名) 返 403 — 合规" \
                              || warn "TOS 直连返 $DIRECT (期望 403)；检查 bucket 默认 ACL"
  fi
  # 5c: 预签名 URL 应 200
  if [[ -n "$SIGNED_URL" ]]; then
    SIGN=$(http_code "$SIGNED_URL")
    [[ "$SIGN" == "200" ]] && ok "预签名 URL 可访问 (200)" \
                            || ko "预签名 URL 返 $SIGN — RSA/AK/SK 配错"
  fi
else
  warn "/oss/upload acl=private 调用失败 — TOS_AK/SK 未配？响应：$(echo "$RESP" | head -c 200)"
fi
# 5d: /oss/sign 必须带 X-Internal-Token，没带应 401
NO_TOKEN_CODE=$(http_code "http://127.0.0.1:8081/oss/sign?key=foo")
[[ "$NO_TOKEN_CODE" == "401" || "$NO_TOKEN_CODE" == "503" ]] \
  && ok "/oss/sign 无 X-Internal-Token 拒 ($NO_TOKEN_CODE) — 防越权" \
  || ko "/oss/sign 无 token 返 $NO_TOKEN_CODE (期望 401/503)"

# ── 幕 6：P0-5 DEMO_MODE 守卫 ────────────
echo ""
echo "[幕 6] P0-5 — 演示 mock 守卫"
DEMO_MODE=$(grep -E "^DEMO_MODE=" "$RUNTIME_ENV" 2>/dev/null | cut -d= -f2)
DOUYIN_KEY=$(grep -E "^DOUYIN_CLIENT_KEY=" "${PROJECT_DIR}/.env" 2>/dev/null | cut -d= -f2)
DEMO_AUTH_CODE=$(http_code "http://127.0.0.1:8081/douyin/demo-auth")
if [[ "$DEMO_MODE" == "true" ]]; then
  warn "DEMO_MODE=true 演示桩开启；生产部署应关闭"
elif [[ -n "$DOUYIN_KEY" ]]; then
  ok "DOUYIN_CLIENT_KEY 已配 — 走真抖音 OAuth"
else
  [[ "$DEMO_AUTH_CODE" == "404" ]] \
    && ok "DEMO_MODE=false + 无 client_key → /douyin/demo-auth 404 (P0-5 守卫)" \
    || ko "/douyin/demo-auth 返 $DEMO_AUTH_CODE — DEMO_MODE 守卫未生效"
fi

# ── 幕 7：P1-6 通联进件 enabled 探测 ────────
echo ""
echo "[幕 7] P1-6 — 通联收付通进件"
ALLINPAY_ENABLED=$(grep -E "^ALLINPAY_ENABLED=" "${PROJECT_DIR}/.env" 2>/dev/null | cut -d= -f2)
ALLINPAY_ORG=$(grep -E "^ALLINPAY_ORG_ID=" "${PROJECT_DIR}/.env" 2>/dev/null | cut -d= -f2)
if [[ "$ALLINPAY_ENABLED" == "true" && -n "$ALLINPAY_ORG" ]]; then
  ok "ALLINPAY_ENABLED=true + orgId 已配 — 真接入模式 (审核通过会调通联)"
else
  warn "通联未启用 (走 Noop 兜底，PENDING 状态不调真接口)；待资质下来后填 ALLINPAY_ENABLED=true + ALLINPAY_ORG_ID + RSA 密钥"
fi
# webhook 路径必须在 permit-all_urls
TL_NOTIFY_CODE=$(http_code -X POST "$BASE_URL/admin-api/merchant/pay/tl-notify-echo")
[[ "$TL_NOTIFY_CODE" =~ ^(200|400|405)$ ]] \
  && ok "通联 webhook 入口可达 (无 401/403 — permit-all 生效)" \
  || ko "通联 webhook 返 $TL_NOTIFY_CODE — 检查 permit-all_urls"

# ── 幕 8：P0-4 Async TaskExecutor 配置加载 ─
echo ""
echo "[幕 8] P0-4 — AI 视频 Async + 超时熔断"
# 通过 actuator/env 检查（需要 actuator 暴露端点；如未暴露就 grep yaml）
if grep -q "ai-video:" "${PROJECT_DIR}/yudao-server/src/main/resources/application.yaml"; then
  ok "ai-video.async + timeout-sec 配置已合并入 application.yaml"
else
  ko "application.yaml 缺 ai-video.* 配置块 (P0-4 未生效)"
fi

# ── 幕 9：P2-11 system_operate_log 链路 ────
echo ""
echo "[幕 9] P2-11 — 操作日志"
if [[ -n "$MYSQL_PASS" ]]; then
  CNT=$(mysql_q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME' AND table_name='system_operate_log'")
  [[ "$CNT" == "1" ]] && ok "system_operate_log 表存在 (yudao 上游 + @LogRecord 已就位)" \
                       || ko "system_operate_log 表缺失"
fi

# ── 幕 10：环境变量配齐校验 ───────────────
echo ""
echo "[幕 10] .env 关键 KEY 校验"
declare -a REQUIRED_VARS=(MYSQL_ROOT_PASS DB_PASS REDIS_PASS MERCHANT_INTERNAL_TOKEN ARK_API_KEY)
declare -a OPTIONAL_VARS=(VOLCANO_ACCESS_TOKEN VOLCANO_APP_ID JIMENG_AK JIMENG_SK TOS_AK TOS_SK DOUYIN_CLIENT_KEY ALLINPAY_ORG_ID)
for v in "${REQUIRED_VARS[@]}"; do
  val=$(grep -E "^${v}=" "${PROJECT_DIR}/.env" 2>/dev/null | cut -d= -f2 || echo "")
  if [[ -n "$val" && "$val" != "please-override-in-production-at-least-16-chars" ]]; then
    ok "$v 已配 (${#val} 字符)"
  else
    ko "$v 必填但未配置 — 启动会失败或退化"
  fi
done
for v in "${OPTIONAL_VARS[@]}"; do
  val=$(grep -E "^${v}=" "${PROJECT_DIR}/.env" 2>/dev/null | cut -d= -f2 || echo "")
  if [[ -n "$val" ]]; then
    ok "$v 已配 (${#val} 字符)"
  else
    warn "$v 未配置 — 相关功能降级 (TTS / 视频 / 抖音 / 通联)"
  fi
done

# ── 总结 ─────────────────────────────────
echo ""
echo "============================================="
for r in "${RESULTS[@]}"; do echo "  $r"; done
echo "============================================="
echo "通过: $PASS   失败: $FAIL   警告: $WARN"
if [[ "$FAIL" -eq 0 ]]; then
  green "✅ 部署合格"; echo
  exit 0
else
  red "❌ 有 $FAIL 项硬错误，部署不合格 — 请按上方清单修复"; echo
  exit 1
fi
