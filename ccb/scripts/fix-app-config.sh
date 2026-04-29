#!/usr/bin/env bash
# =============================================================================
# 一键修复：yudao-server 用 root/root 默认配置连 MySQL → Access denied
#           顺便开启 MySQL 远程连接（root@'%' + tanxiaer@'%'）
#
# 现象：
#   journalctl -u tanxiaer | grep -i mysql
#   → "Access denied for user 'root'@'localhost' (using password: YES)"
#
# 根因：jar 内 application-local.yaml 默认 root/root，build 时 application-prod.yaml
#   没生成进去 / 没生效 → spring boot 启动时用了默认值。修法是在 jar 同目录写一份
#   application-prod.yaml，spring boot 会优先读它覆盖 jar 内默认。
#
# 用法（root）：
#   sudo bash /opt/tanxiaer/repo/ccb/scripts/fix-app-config.sh
# =============================================================================
set -euo pipefail

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log()  { echo -e "${GREEN}[✓]${NC} $*"; }
info() { echo -e "${BLUE}[i]${NC} $*"; }
warn() { echo -e "${YELLOW}[!]${NC} $*"; }
err()  { echo -e "${RED}[✗]${NC} $*" >&2; }

[[ $EUID -ne 0 ]] && { err "必须 root：sudo bash $0"; exit 1; }

ROOT_DIR="${ROOT_DIR:-/opt/tanxiaer}"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/repo/ccb/.env}"
APP_DIR="${ROOT_DIR}/app"
DB_NAME="${DB_NAME:-ruoyi-vue-pro}"
SERVER_PORT="${SERVER_PORT:-48080}"
REDIS_PORT="${REDIS_PORT:-6379}"
SERVICE_USER="${SERVICE_USER:-tanxiaer}"

[[ ! -f "${ENV_FILE}" ]] && { err ".env 不存在: ${ENV_FILE}"; exit 1; }
[[ ! -f "${APP_DIR}/yudao-server.jar" ]] && { err "jar 不存在: ${APP_DIR}/yudao-server.jar"; exit 1; }

# 加载 .env
set -a; source "${ENV_FILE}"; set +a
: "${MYSQL_ROOT_PASS:?MYSQL_ROOT_PASS 未在 .env 中设置}"
: "${DB_PASS:?DB_PASS 未在 .env 中设置}"
: "${REDIS_PASS:?REDIS_PASS 未在 .env 中设置}"
: "${MERCHANT_INTERNAL_TOKEN:?MERCHANT_INTERNAL_TOKEN 未在 .env 中设置}"

# ── 1. MySQL 用户 + 远程连接 ──────────────────────────────────────────────
info "[1/6] 配置 MySQL 用户 + 开启远程连接"
# 用 mktemp 写 SQL 到临时文件，避免 heredoc + grep + || 的解析坑
TMP_SQL=$(mktemp)
chmod 600 "${TMP_SQL}"
cat > "${TMP_SQL}" <<SQL
-- 1a. 远程开 root（注意：生产环境应改用 IP 白名单代替 '%'）
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' IDENTIFIED BY '${MYSQL_ROOT_PASS}' WITH GRANT OPTION;
-- 1b. 业务用户 tanxiaer（本地 + 远程都给）
CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON \`${DB_NAME}\`.* TO 'tanxiaer'@'localhost' IDENTIFIED BY '${DB_PASS}';
GRANT ALL PRIVILEGES ON \`${DB_NAME}\`.* TO 'tanxiaer'@'%'         IDENTIFIED BY '${DB_PASS}';
FLUSH PRIVILEGES;
SELECT user, host FROM mysql.user WHERE user IN ('root','tanxiaer') ORDER BY user, host;
SQL
if ! mysql -uroot -p"${MYSQL_ROOT_PASS}" < "${TMP_SQL}" 2>&1 | grep -vE "Warning|password"; then
  err "MySQL root 密码连接失败，请检查 .env 的 MYSQL_ROOT_PASS"
  rm -f "${TMP_SQL}"
  exit 1
fi
rm -f "${TMP_SQL}"
log "MySQL 用户 + 远程访问已开通"

# ── 2. MySQL bind-address 改 0.0.0.0 + firewall 放 3306 ──────────────────
info "[2/6] MySQL bind-address 0.0.0.0 + 放行 3306"
MYSQL_CONF=""
for p in /etc/my.cnf /etc/mysql/my.cnf /etc/my.cnf.d/server.cnf; do
  [[ -f "$p" ]] && grep -qE "^\s*bind-address" "$p" && MYSQL_CONF="$p" && break
done
if [[ -z "${MYSQL_CONF}" ]]; then
  # 没找到 bind-address，找 mysqld 段加一行
  for p in /etc/my.cnf /etc/my.cnf.d/server.cnf /etc/mysql/my.cnf; do
    if [[ -f "$p" ]] && grep -qE "^\[mysqld\]" "$p"; then
      MYSQL_CONF="$p"; break
    fi
  done
fi
if [[ -n "${MYSQL_CONF}" ]]; then
  if grep -qE "^\s*bind-address" "${MYSQL_CONF}"; then
    sed -i "s|^\s*bind-address.*|bind-address = 0.0.0.0|" "${MYSQL_CONF}"
  else
    sed -i "/^\[mysqld\]/a bind-address = 0.0.0.0" "${MYSQL_CONF}"
  fi
  log "${MYSQL_CONF} bind-address = 0.0.0.0"
  systemctl restart mysqld 2>/dev/null || systemctl restart mysql 2>/dev/null || warn "MySQL 重启失败 — 请手工 systemctl restart mysqld"
else
  warn "找不到 my.cnf，请手工把 bind-address 改 0.0.0.0"
fi

if command -v firewall-cmd &>/dev/null && systemctl is-active --quiet firewalld; then
  firewall-cmd --permanent --add-port=3306/tcp >/dev/null 2>&1 || true
  firewall-cmd --reload >/dev/null 2>&1 || true
  log "firewalld 已放行 3306"
fi
warn "⚠️  阿里云 ECS 安全组：去阿里云控制台另行放行 3306（firewalld 不管安全组）"

# ── 3. 写 application-prod.yaml 到 jar 同目录 ───────────────────────────
info "[3/6] 写 application-prod.yaml 到 ${APP_DIR}/"
TOKEN_SECRET_FILE="${ROOT_DIR}/.token-secret"
if [[ ! -s "${TOKEN_SECRET_FILE}" ]]; then
  umask 077
  openssl rand -base64 48 | tr -d '\n' > "${TOKEN_SECRET_FILE}"
  umask 022
fi
chmod 600 "${TOKEN_SECRET_FILE}"
TOKEN_SECRET="$(cat "${TOKEN_SECRET_FILE}")"

umask 077
cat > "${APP_DIR}/application-prod.yaml" << YAML_EOF
# 生产配置 — 由 fix-app-config.sh 生成；spring boot 启动时优先读取 jar 同目录的此文件
# 覆盖 jar 内 application-local.yaml 的默认 root/root
spring:
  datasource:
    dynamic:
      primary: master
      strict: false
      datasource:
        master:
          url: jdbc:mysql://127.0.0.1:3306/${DB_NAME}?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&nullCatalogMeansCurrent=true&rewriteBatchedStatements=true
          driver-class-name: com.mysql.cj.jdbc.Driver
          username: tanxiaer
          password: ${DB_PASS}
        slave:
          url: jdbc:mysql://127.0.0.1:3306/${DB_NAME}?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&nullCatalogMeansCurrent=true&rewriteBatchedStatements=true
          driver-class-name: com.mysql.cj.jdbc.Driver
          username: tanxiaer
          password: ${DB_PASS}
  data:
    redis:
      host: 127.0.0.1
      port: ${REDIS_PORT}
      password: ${REDIS_PASS}
      database: 0
  redis:
    host: 127.0.0.1
    port: ${REDIS_PORT}
    password: ${REDIS_PASS}
    database: 0

server:
  port: ${SERVER_PORT}

yudao:
  tenant:
    enable: true
  security:
    token-secret: ${TOKEN_SECRET}
  captcha:
    enable: false
  api-encrypt:
    enable: false

merchant:
  internal-token: ${MERCHANT_INTERNAL_TOKEN}
  field-encrypt-key: ${MERCHANT_FIELD_ENCRYPT_KEY:-dev_key_12345678}
  sidecar-url: http://127.0.0.1:8081
  allinpay:
    enabled: ${ALLINPAY_ENABLED:-false}
YAML_EOF
chmod 644 "${APP_DIR}/application-prod.yaml"
chown "${SERVICE_USER}:${SERVICE_USER}" "${APP_DIR}/application-prod.yaml" 2>/dev/null || true
umask 022
log "application-prod.yaml 写入完成 → ${APP_DIR}/application-prod.yaml"

# ── 4. 验证 systemd unit 启用了 prod profile ─────────────────────────────
info "[4/6] 校验 systemd unit"
UNIT_FILE="/etc/systemd/system/tanxiaer.service"
[[ ! -f "${UNIT_FILE}" ]] && { err "systemd unit 不存在: ${UNIT_FILE}"; exit 1; }

if grep -q "spring.profiles.active=prod" "${UNIT_FILE}"; then
  log "unit 已带 --spring.profiles.active=prod"
else
  warn "unit 缺 --spring.profiles.active=prod，自动补"
  sed -i 's|ExecStart=.*yudao-server\.jar.*|ExecStart=/usr/bin/java -Xms512m -Xmx1024m -jar '"${APP_DIR}"'/yudao-server.jar --spring.profiles.active=prod|' "${UNIT_FILE}"
  systemctl daemon-reload
  log "unit 已修正 + daemon-reload"
fi

# ── 5. 重启 tanxiaer + 等启动 ──────────────────────────────────────────
info "[5/6] 重启 tanxiaer + 等启动（最长 90s）"
systemctl restart tanxiaer
OK=false
for i in $(seq 1 18); do
  sleep 5
  code=$(curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1/admin-api/system/auth/captcha" 2>/dev/null || echo 000)
  if [[ "${code}" == "200" ]]; then
    OK=true; log "yudao-server 已就绪 (HTTP ${code})"; break
  fi
  echo -n "."
done
echo

# ── 6. 终态报告 ─────────────────────────────────────────────────────────
info "[6/6] 报告"
echo
echo "═══════════════════════════════════════════════════════"
if [[ "${OK}" == true ]]; then
  echo -e "${GREEN}  ✅ 修复成功${NC}"
  echo "═══════════════════════════════════════════════════════"
  PUBLIC_IP=$(curl -s --max-time 3 ifconfig.me 2>/dev/null || echo "47.109.143.146")
  echo "  服务可访问："
  echo "    H5:        http://${PUBLIC_IP}/m/"
  echo "    PC 后台:   http://${PUBLIC_IP}/admin/   (admin / admin123)"
  echo "    API:       http://${PUBLIC_IP}/admin-api/system/auth/captcha"
  echo
  echo "  数据库远程连接（用 Navicat 等工具）:"
  echo "    主机: ${PUBLIC_IP}    端口: 3306"
  echo "    root 用户:    密码 = .env 里的 MYSQL_ROOT_PASS"
  echo "    tanxiaer 用户: 密码 = .env 里的 DB_PASS    库 = ${DB_NAME}"
  echo
  echo -e "${YELLOW}  ⚠️  阿里云 ECS 安全组别忘了放行 3306（firewalld 不管安全组）${NC}"
  exit 0
else
  echo -e "${RED}  ✗ 修复失败 — 看下面的排查清单${NC}"
  echo "═══════════════════════════════════════════════════════"
  echo "  systemctl status tanxiaer --no-pager | head -20"
  echo "  journalctl -u tanxiaer -n 60 --no-pager | grep -E 'Caused by|Exception' | head"
  echo "  cat ${APP_DIR}/application-prod.yaml | head -25"
  exit 1
fi
