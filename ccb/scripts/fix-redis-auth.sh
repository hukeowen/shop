#!/usr/bin/env bash
# =============================================================================
# 一键修复：Redis NOAUTH 导致 yudao-server 起不来 / nginx /app-api 502
#
# 现象：
#   curl /admin-api/.../password-login → 502 Bad Gateway
#   journalctl -u tanxiaer 见 "Caused by: org.redisson.client.RedisAuthRequiredException: NOAUTH"
#
# 根因：
#   .env 里 REDIS_PASS 是 CHANGE_ME_* 占位符（或与 redis.conf 的 requirepass 不一致），
#   yudao-server 用 .env 的密码连 Redis，密码对不上 → 启动失败 exit 1 → systemd 不再
#   监听 48080 → nginx 反代到空端口 → 502。
#
# 修法（不重新编译，只改配置 + 重启）：
#   1. 检查 .env 的 REDIS_PASS，占位符则生成强密码替换
#   2. /etc/redis.conf 同步 requirepass = .env 里的 REDIS_PASS
#   3. 重启 redis、验证 PING = PONG
#   4. 重启 tanxiaer、等待启动、验证 /admin-api/captcha = 200
#
# 用法（root）：
#   sudo bash /opt/tanxiaer/repo/ccb/scripts/fix-redis-auth.sh
# =============================================================================
set -euo pipefail

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log()  { echo -e "${GREEN}[✓]${NC} $*"; }
info() { echo -e "${BLUE}[i]${NC} $*"; }
warn() { echo -e "${YELLOW}[!]${NC} $*"; }
err()  { echo -e "${RED}[✗]${NC} $*" >&2; }

[[ $EUID -ne 0 ]] && { err "必须 root：sudo bash $0"; exit 1; }

ENV_FILE="${ENV_FILE:-/opt/tanxiaer/repo/ccb/.env}"
[[ ! -f "${ENV_FILE}" ]] && { err ".env 不存在: ${ENV_FILE}"; exit 1; }

# ── 1. 取 / 生成 REDIS_PASS ───────────────────────────────────────────────
info "[1/6] 检查 .env 的 REDIS_PASS"
CURRENT_PASS=$(grep '^REDIS_PASS=' "${ENV_FILE}" | head -1 | cut -d= -f2-)

# 占位符判断（CHANGE_ME / 空 / 太短）
if [[ -z "${CURRENT_PASS}" ]] || [[ "${CURRENT_PASS}" == CHANGE_ME* ]] || [[ ${#CURRENT_PASS} -lt 8 ]]; then
  warn "REDIS_PASS 是占位符或太弱：${CURRENT_PASS:-<空>}"
  # 生成 24 字符强密码（字母+数字+@+#，无单引号反斜杠避免 sed 转义）
  NEW_PASS="Tx$(openssl rand -hex 8 2>/dev/null || head -c 16 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 16)@$(date +%s | tail -c 5)"
  # sed 替换（用 | 做分隔避免与 / 冲突）
  sed -i "s|^REDIS_PASS=.*|REDIS_PASS=${NEW_PASS}|" "${ENV_FILE}"
  log ".env REDIS_PASS 已替换为强密码"
  REDIS_PASS="${NEW_PASS}"
else
  log "REDIS_PASS 已是真实密码（${#CURRENT_PASS} 字符），保留"
  REDIS_PASS="${CURRENT_PASS}"
fi

# ── 2. 找 redis 配置文件 ────────────────────────────────────────────────
info "[2/6] 定位 redis 配置文件"
REDIS_CONF=""
for p in /etc/redis.conf /etc/redis/redis.conf /etc/redis/6379.conf; do
  [[ -f "$p" ]] && REDIS_CONF="$p" && break
done
[[ -z "${REDIS_CONF}" ]] && { err "找不到 redis 配置文件 (/etc/redis.conf 或 /etc/redis/redis.conf)"; exit 1; }
log "REDIS_CONF = ${REDIS_CONF}"

# ── 3. 同步 requirepass 到 redis.conf ──────────────────────────────────
info "[3/6] 写入 requirepass 到 redis.conf"
# 备份
[[ ! -f "${REDIS_CONF}.tanxiaer-bak" ]] && cp "${REDIS_CONF}" "${REDIS_CONF}.tanxiaer-bak"
# 删旧 requirepass（注释和未注释的都删，避免歧义）
sed -i '/^[[:space:]]*#*[[:space:]]*requirepass[[:space:]]/d' "${REDIS_CONF}"
# 追加新的
echo "requirepass ${REDIS_PASS}" >> "${REDIS_CONF}"
log "requirepass 已写入"

# 顺手把 protected-mode no 也加上（方便排查；若已为 yes 就不动）
grep -qE '^[[:space:]]*protected-mode' "${REDIS_CONF}" || echo "protected-mode no" >> "${REDIS_CONF}"

# ── 4. 重启 redis + 验证 PING ──────────────────────────────────────────
info "[4/6] 重启 redis"
# CentOS 不同发行 redis 服务名可能是 redis / redis-server
RESTARTED=false
for svc in redis redis-server redis6; do
  if systemctl list-unit-files 2>/dev/null | grep -q "^${svc}\.service"; then
    systemctl restart "${svc}" && { log "已重启 ${svc}"; RESTARTED=true; break; }
  fi
done
if [[ "${RESTARTED}" != true ]]; then
  err "找不到 redis systemd 服务，请手工重启"; exit 1
fi

sleep 2
info "[5/6] 验证 redis 密码生效（PING 应返 PONG）"
if redis-cli -a "${REDIS_PASS}" --no-auth-warning -t 3 ping 2>&1 | grep -q PONG; then
  log "redis 密码验证通过 ✓"
else
  err "redis-cli ping 未返 PONG，请手工排查："
  err "  redis-cli -a '${REDIS_PASS}' ping"
  err "  systemctl status redis"
  exit 1
fi

# ── 6. 重启 tanxiaer + 等启动 + 验证 ─────────────────────────────────────
info "[6/6] 重启 tanxiaer + 等启动（最长 90s）"
systemctl restart tanxiaer

OK=false
for i in $(seq 1 18); do
  sleep 5
  code=$(curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1/admin-api/system/auth/captcha" 2>/dev/null || echo 000)
  if [[ "${code}" == "200" ]]; then
    log "yudao-server 已就绪（HTTP ${code}）"
    OK=true
    break
  fi
  echo -n "."
done
echo

# ── 终态报告 ──────────────────────────────────────────────────────────
echo
echo "═══════════════════════════════════════════════════════"
if [[ "${OK}" == true ]]; then
  echo -e "${GREEN}  ✅ 修复成功${NC}"
  echo "═══════════════════════════════════════════════════════"
  PUBLIC_IP=$(curl -s --max-time 3 ifconfig.me 2>/dev/null || echo "47.109.143.146")
  echo "  现在可以登录："
  echo "    H5:        http://${PUBLIC_IP}/m/"
  echo "    PC 后台:   http://${PUBLIC_IP}/admin/   (admin / admin123)"
  echo "    API 自检:  curl http://${PUBLIC_IP}/admin-api/system/auth/captcha"
  echo
  echo "  redis 密码已记录在: ${ENV_FILE} 的 REDIS_PASS="
  exit 0
else
  echo -e "${RED}  ✗ 修复失败 — yudao-server 仍未起来${NC}"
  echo "═══════════════════════════════════════════════════════"
  echo "  排查："
  echo "    systemctl status tanxiaer"
  echo "    journalctl -u tanxiaer -n 50 --no-pager | grep -E 'Caused by|Exception'"
  echo
  echo "  最常见的下一个挂点："
  echo "    1. MySQL: Access denied for user 'tanxiaer'@'localhost'"
  echo "       修：mysql -uroot -p\$MYSQL_ROOT_PASS -e \"GRANT ALL ON \\\`ruoyi-vue-pro\\\`.* TO 'tanxiaer'@'localhost' IDENTIFIED BY '\$DB_PASS'; FLUSH PRIVILEGES;\""
  echo "    2. JVM OOM (4G ECS 常见)"
  echo "       修：sed -i 's/-Xmx1536m/-Xmx1024m/' /etc/systemd/system/tanxiaer.service && systemctl daemon-reload && systemctl restart tanxiaer"
  exit 1
fi
