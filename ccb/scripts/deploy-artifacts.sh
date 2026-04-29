#!/usr/bin/env bash
# =============================================================================
# 摊小二 — 纯部署脚本（不构建，假设产物已打好）
#
# 用途：CI 把 jar / dist / sidecar 打好后，scp 到 ECS，跑这个脚本一次性部署
#       不安装系统软件、不跑 mvn / pnpm，只做：停服 → 备份 → 复制 → 配 nginx
#       → 配 systemd → 启服 → 自检
#
# 必备产物（由 deploy.sh 或 CI 提前打好）：
#   ${SRC}/yudao-server/target/yudao-server.jar
#   ${SRC}/yudao-ui/yudao-ui-admin-vue3/dist-prod/        (或 dist/)
#   ${SRC}/yudao-ui/yudao-ui-merchant-uniapp/dist/build/h5/
#   ${SRC}/server/sidecar/                                (含 index.js + package.json + node_modules)
#   ${SRC}/docs/website/index.html
#
# 必备系统组件（已装好）：
#   JDK 8 + nginx + MySQL + Redis + Node 16
#
# 用法：
#   sudo SRC=/opt/tanxiaer/repo/ccb bash deploy-artifacts.sh
#
# 选项 ENV：
#   SRC               产物源码根（默认 /opt/tanxiaer/repo/ccb）
#   ROOT_DIR          部署目标根（默认 /opt/tanxiaer）
#   SKIP_FRONTEND     true 跳过 H5 / admin-dist 部署
#   SKIP_BACKEND      true 跳过 yudao-server 部署
#   SKIP_SIDECAR      true 跳过 sidecar 部署
#   SKIP_NGINX        true 跳过 nginx 配置写入
#   KEEP_BACKUP       true 不删旧备份（默认保留最近 3 份）
#
# 退出码：0=部署成功且自检通过；非 0=自检失败（需检查清单）
# =============================================================================
set -euo pipefail

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; CYAN='\033[0;36m'; NC='\033[0m'
log()  { echo -e "${GREEN}[✓]${NC} $*"; }
info() { echo -e "${BLUE}[i]${NC} $*"; }
warn() { echo -e "${YELLOW}[!]${NC} $*"; }
err()  { echo -e "${RED}[✗]${NC} $*" >&2; }
step() { echo -e "\n${CYAN}══════ $* ══════${NC}"; }

trap 'rc=$?; echo -e "${RED}[✗] 第 ${LINENO} 行异常退出（退出码=${rc}）失败命令: ${BASH_COMMAND}${NC}" >&2; exit $rc' ERR

# ── 0. 入口检查 ────────────────────────────────────────────────────────────
if [[ $EUID -ne 0 ]]; then err "必须 root：sudo bash $0"; exit 1; fi

SRC="${SRC:-/opt/tanxiaer/repo/ccb}"
ROOT_DIR="${ROOT_DIR:-/opt/tanxiaer}"
SERVICE_USER="${SERVICE_USER:-tanxiaer}"
SERVER_PORT="${SERVER_PORT:-48080}"
SIDECAR_PORT="${SIDECAR_PORT:-8081}"
SERVER_NAME="${SERVER_NAME:-_}"
SKIP_FRONTEND="${SKIP_FRONTEND:-false}"
SKIP_BACKEND="${SKIP_BACKEND:-false}"
SKIP_SIDECAR="${SKIP_SIDECAR:-false}"
SKIP_NGINX="${SKIP_NGINX:-false}"
KEEP_BACKUP="${KEEP_BACKUP:-false}"

# ── 加载 .env（与 deploy.sh 同款，提供敏感配置）─────────────────────────
ENV_FILE="${SRC}/.env"
if [[ -f "${ENV_FILE}" ]]; then
  set -a; source "${ENV_FILE}"; set +a
  log "加载 .env: ${ENV_FILE}"
else
  warn ".env 缺失：${ENV_FILE} — sidecar/yudao-server 启动会缺关键 KEY"
fi

# ── 必填项校验 ─────────────────────────────────────────────────────────────
: "${MYSQL_ROOT_PASS:?MYSQL_ROOT_PASS 未在 .env 中设置}"
: "${DB_PASS:?DB_PASS 未在 .env 中设置}"
: "${REDIS_PASS:?REDIS_PASS 未在 .env 中设置}"
: "${MERCHANT_INTERNAL_TOKEN:?MERCHANT_INTERNAL_TOKEN 未在 .env 中设置（≥16 字符）}"

DB_NAME="${DB_NAME:-ruoyi-vue-pro}"
DB_USER="${DB_USER:-tanxiaer}"
REDIS_PORT="${REDIS_PORT:-6379}"

# ── 1. 产物存在性预检 ──────────────────────────────────────────────────────
step "[1/9] 检查产物"
JAR_SRC="${SRC}/yudao-server/target/yudao-server.jar"
ADMIN_SRC=""
for d in dist-prod dist; do
  if [[ -f "${SRC}/yudao-ui/yudao-ui-admin-vue3/${d}/index.html" ]]; then
    ADMIN_SRC="${SRC}/yudao-ui/yudao-ui-admin-vue3/${d}"
    break
  fi
done
H5_SRC="${SRC}/yudao-ui/yudao-ui-merchant-uniapp/dist/build/h5"
SIDECAR_SRC_DIR="${SRC}/server/sidecar"
WEBSITE_SRC="${SRC}/docs/website"

if [[ "${SKIP_BACKEND}" != "true" && ! -f "${JAR_SRC}" ]]; then
  err "后端 jar 缺失: ${JAR_SRC}"; exit 1
fi
if [[ "${SKIP_FRONTEND}" != "true" ]]; then
  if [[ -z "${ADMIN_SRC}" ]]; then err "PC 后台 dist 缺失: ${SRC}/yudao-ui/yudao-ui-admin-vue3/dist[-prod]/"; exit 1; fi
  if [[ ! -f "${H5_SRC}/index.html" ]]; then err "H5 dist 缺失: ${H5_SRC}/index.html"; exit 1; fi
fi
if [[ "${SKIP_SIDECAR}" != "true" && ! -f "${SIDECAR_SRC_DIR}/index.js" ]]; then
  err "sidecar 源缺失: ${SIDECAR_SRC_DIR}/index.js"; exit 1
fi

log "产物全部就绪"
[[ "${SKIP_BACKEND}"  != "true" ]] && info "  jar:    ${JAR_SRC}    ($(stat -c %s "${JAR_SRC}" | numfmt --to=iec))"
[[ "${SKIP_FRONTEND}" != "true" ]] && info "  admin:  ${ADMIN_SRC}/  ($(du -sh "${ADMIN_SRC}" 2>/dev/null | cut -f1))"
[[ "${SKIP_FRONTEND}" != "true" ]] && info "  H5:     ${H5_SRC}/  ($(du -sh "${H5_SRC}" 2>/dev/null | cut -f1))"
[[ "${SKIP_SIDECAR}"  != "true" ]] && info "  sidecar:${SIDECAR_SRC_DIR}/"

# ── 2. 创建运行用户 ────────────────────────────────────────────────────────
step "[2/9] 创建运行用户 ${SERVICE_USER}"
if ! id "${SERVICE_USER}" &>/dev/null; then
  useradd -r -s /bin/false "${SERVICE_USER}"
  log "用户已创建"
else
  log "用户已存在"
fi
mkdir -p "${ROOT_DIR}"/{app,logs,m,admin-dist,website,sidecar,backups}

# ── 3. 备份当前产物（带时间戳，方便回滚） ──────────────────────────────────
step "[3/9] 备份当前部署产物"
BACKUP_DIR="${ROOT_DIR}/backups/$(date +%Y%m%d-%H%M%S)"
mkdir -p "${BACKUP_DIR}"
[[ -f "${ROOT_DIR}/app/yudao-server.jar" ]] && cp "${ROOT_DIR}/app/yudao-server.jar" "${BACKUP_DIR}/" 2>/dev/null || true
[[ -d "${ROOT_DIR}/m"          ]] && cp -r "${ROOT_DIR}/m"          "${BACKUP_DIR}/m"          2>/dev/null || true
[[ -d "${ROOT_DIR}/admin-dist" ]] && cp -r "${ROOT_DIR}/admin-dist" "${BACKUP_DIR}/admin-dist" 2>/dev/null || true
[[ -d "${ROOT_DIR}/website"    ]] && cp -r "${ROOT_DIR}/website"    "${BACKUP_DIR}/website"    2>/dev/null || true
log "备份 → ${BACKUP_DIR}"

# 自动清理保留最近 3 份（除非 KEEP_BACKUP=true）
if [[ "${KEEP_BACKUP}" != "true" ]]; then
  ls -1dt "${ROOT_DIR}/backups"/*/ 2>/dev/null | tail -n +4 | xargs -r rm -rf
  info "旧备份已清理（保留最近 3 份）"
fi

# ── 4. 停服（让文件能干净覆盖） ────────────────────────────────────────────
step "[4/9] 停服"
for svc in tanxiaer tanxiaer-sidecar; do
  if systemctl is-active --quiet "$svc" 2>/dev/null; then
    systemctl stop "$svc" && log "已停 $svc"
  fi
done

# ── 5. 复制产物 ────────────────────────────────────────────────────────────
step "[5/9] 复制产物到 ${ROOT_DIR}"
seal_dir() {
  # 统一处理：chmod + chown + SELinux 标签恢复（即使 SELinux 关闭也无害）
  local d="$1"
  chmod -R a+rX "$d" 2>/dev/null || true
  chown -R "${SERVICE_USER}:${SERVICE_USER}" "$d" 2>/dev/null || true
  command -v restorecon &>/dev/null && restorecon -R "$d" 2>/dev/null || true
}

if [[ "${SKIP_BACKEND}" != "true" ]]; then
  cp -f "${JAR_SRC}" "${ROOT_DIR}/app/yudao-server.jar"
  log "yudao-server.jar → ${ROOT_DIR}/app/"
fi

if [[ "${SKIP_FRONTEND}" != "true" ]]; then
  rm -rf "${ROOT_DIR}/admin-dist"
  mkdir -p "${ROOT_DIR}/admin-dist"
  cp -r "${ADMIN_SRC}/." "${ROOT_DIR}/admin-dist/"
  seal_dir "${ROOT_DIR}/admin-dist"
  log "admin-dist → ${ROOT_DIR}/admin-dist/"

  rm -rf "${ROOT_DIR}/m"
  mkdir -p "${ROOT_DIR}/m"
  cp -r "${H5_SRC}/." "${ROOT_DIR}/m/"
  seal_dir "${ROOT_DIR}/m"
  log "merchant H5 → ${ROOT_DIR}/m/"

  rm -rf "${ROOT_DIR}/website"
  mkdir -p "${ROOT_DIR}/website"
  cp "${WEBSITE_SRC}/index.html" "${ROOT_DIR}/website/" 2>/dev/null || warn "官网 index.html 缺失"
  [[ -d "${WEBSITE_SRC}/assets" ]] && cp -r "${WEBSITE_SRC}/assets" "${ROOT_DIR}/website/"
  seal_dir "${ROOT_DIR}/website"
  log "website → ${ROOT_DIR}/website/"
fi

if [[ "${SKIP_SIDECAR}" != "true" ]]; then
  cp -f "${SIDECAR_SRC_DIR}/index.js"     "${ROOT_DIR}/sidecar/"
  cp -f "${SIDECAR_SRC_DIR}/package.json" "${ROOT_DIR}/sidecar/"
  # node_modules：若源码侧已 npm install 好，整体拷过来；否则在目标侧装
  if [[ -d "${SIDECAR_SRC_DIR}/node_modules" ]]; then
    rm -rf "${ROOT_DIR}/sidecar/node_modules"
    cp -r "${SIDECAR_SRC_DIR}/node_modules" "${ROOT_DIR}/sidecar/"
    log "sidecar (含 node_modules) → ${ROOT_DIR}/sidecar/"
  else
    info "sidecar 源缺 node_modules，在 ${ROOT_DIR}/sidecar/ 现场 npm install --omit=dev --ignore-scripts ..."
    (cd "${ROOT_DIR}/sidecar" && \
      npm install --omit=dev --ignore-scripts \
        --registry=https://registry.npmmirror.com --no-audit --fund=false 2>&1 | tail -8)
    log "sidecar 现场安装完成"
  fi
  # sidecar/.env（root only，含敏感 KEY）
  umask 077
  cat > "${ROOT_DIR}/sidecar/.env" << SIDECAR_ENV_EOF
SIDECAR_PORT=${SIDECAR_PORT}
TOS_AK=${TOS_AK:-${JIMENG_AK:-}}
TOS_SK=${TOS_SK:-${JIMENG_SK:-}}
TOS_BUCKET=${TOS_BUCKET:-tanxiaoer}
TOS_REGION=${TOS_REGION:-cn-beijing}
JIMENG_AK=${JIMENG_AK:-}
JIMENG_SK=${JIMENG_SK:-}
VOLCANO_ACCESS_TOKEN=${VOLCANO_ACCESS_TOKEN:-}
VOLCANO_AK=${VOLCANO_AK:-}
VOLCANO_SK=${VOLCANO_SK:-}
ARK_API_KEY=${ARK_API_KEY:-}
DOUYIN_CLIENT_KEY=${DOUYIN_CLIENT_KEY:-}
DOUYIN_CLIENT_SECRET=${DOUYIN_CLIENT_SECRET:-}
MERCHANT_INTERNAL_TOKEN=${MERCHANT_INTERNAL_TOKEN}
DEMO_MODE=${DEMO_MODE:-false}
FFMPEG_PATH=${FFMPEG_PATH:-/usr/bin/ffmpeg}
SIDECAR_ENV_EOF
  umask 022
  chown -R "${SERVICE_USER}:${SERVICE_USER}" "${ROOT_DIR}/sidecar"
fi

# ── 6. 写 nginx 配置 ───────────────────────────────────────────────────────
if [[ "${SKIP_NGINX}" != "true" ]]; then
  step "[6/9] 配置 Nginx"
  NGINX_CONF="/etc/nginx/conf.d/tanxiaer.conf"
  MAIN_CONF="/etc/nginx/nginx.conf"

  # 6a. 清掉 conf.d/default.conf（如果存在）
  if [[ -f /etc/nginx/conf.d/default.conf ]]; then
    mv -f /etc/nginx/conf.d/default.conf /etc/nginx/conf.d/default.conf.disabled
    log "禁用 conf.d/default.conf"
  fi

  # 6b. 清掉 nginx.conf 主文件里的默认 server 块（CentOS 包自带的会抢 default_server）
  if grep -qE "^[[:space:]]*server[[:space:]]*\{" "${MAIN_CONF}" 2>/dev/null; then
    [[ ! -f "${MAIN_CONF}.tanxiaer-bak" ]] && cp "${MAIN_CONF}" "${MAIN_CONF}.tanxiaer-bak"
    awk '
      BEGIN { in_s=0; d=0 }
      /^[[:space:]]*server[[:space:]]*\{/ && in_s==0 {
        in_s=1; d=1
        print "    # [tanxiaer-deploy] 默认 server 已禁用"
        next
      }
      in_s==1 {
        n=gsub(/\{/,"{"); m=gsub(/\}/,"}")
        d += n - m
        if (d <= 0) { in_s=0 }
        next
      }
      { print }
    ' "${MAIN_CONF}.tanxiaer-bak" > "${MAIN_CONF}"
    log "已清理 nginx.conf 默认 server 块（备份 → ${MAIN_CONF}.tanxiaer-bak）"
  fi

  # 6c. 写 tanxiaer.conf
  cat > "${NGINX_CONF}" << NGINX_EOF
upstream tanxiaer_backend {
    server 127.0.0.1:${SERVER_PORT};
    keepalive 32;
}

server {
    listen 80 default_server;
    server_name ${SERVER_NAME} _;
    charset utf-8;
    client_max_body_size 50m;

    # 官网（静态单页）
    location / {
        root ${ROOT_DIR}/website;
        index index.html;
        try_files \$uri \$uri/ /index.html;
    }

    # 管理后台
    location /admin/ {
        alias ${ROOT_DIR}/admin-dist/;
        index index.html;
        try_files \$uri \$uri/ /admin/index.html;
    }

    # 商户/用户端 H5
    location /m/ {
        alias ${ROOT_DIR}/m/;
        index index.html;
        try_files \$uri \$uri/ /m/index.html;
    }
    location /m/assets/ {
        alias ${ROOT_DIR}/m/assets/;
        expires 30d;
        add_header Cache-Control "public, immutable";
        access_log off;
    }

    # 后端 API
    location /admin-api/ {
        proxy_pass http://tanxiaer_backend;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_connect_timeout 60s;
        proxy_read_timeout 300s;
    }
    location /app-api/ {
        proxy_pass http://tanxiaer_backend;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_connect_timeout 60s;
        proxy_read_timeout 300s;
    }

    # Sidecar 反代
    location /oss/    { proxy_pass http://127.0.0.1:${SIDECAR_PORT}; client_max_body_size 100m; proxy_read_timeout 600s; }
    location /tts/    { proxy_pass http://127.0.0.1:${SIDECAR_PORT}; proxy_buffering off; proxy_read_timeout 120s; }
    location /video/  { proxy_pass http://127.0.0.1:${SIDECAR_PORT}; client_max_body_size 200m; proxy_read_timeout 900s; proxy_send_timeout 900s; }
    location /vproxy  { proxy_pass http://127.0.0.1:${SIDECAR_PORT}; proxy_read_timeout 300s; }
    location /jimeng  { proxy_pass http://127.0.0.1:${SIDECAR_PORT}; }
    location /douyin/ { proxy_pass http://127.0.0.1:${SIDECAR_PORT}; client_max_body_size 200m; proxy_read_timeout 600s; }

    # WebSocket
    location /ws {
        proxy_pass http://tanxiaer_backend;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 3600s;
    }
}
NGINX_EOF
  log "tanxiaer.conf 已写入"

  # 6d. 验证 + 重载
  if nginx -t 2>&1 | grep -qv "warn"; then
    nginx -s reload
    log "nginx 已重载"
  else
    err "nginx -t 检查失败"
    nginx -t
    exit 1
  fi
fi

# ── 7. 写 systemd unit ─────────────────────────────────────────────────────
step "[7/9] 写 systemd unit"

if [[ "${SKIP_BACKEND}" != "true" ]]; then
  cat > /etc/systemd/system/tanxiaer.service << UNIT_EOF
[Unit]
Description=Tanxiaer Yudao Server
After=mysqld.service redis.service
Requires=mysqld.service

[Service]
Type=simple
User=${SERVICE_USER}
EnvironmentFile=${ENV_FILE}
WorkingDirectory=${ROOT_DIR}/app
ExecStart=/usr/bin/java -Xms512m -Xmx1536m -jar ${ROOT_DIR}/app/yudao-server.jar --spring.profiles.active=prod
Restart=on-failure
RestartSec=5
StandardOutput=append:${ROOT_DIR}/logs/yudao-server.log
StandardError=append:${ROOT_DIR}/logs/yudao-server-error.log
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
UNIT_EOF
  log "tanxiaer.service 已写"
fi

if [[ "${SKIP_SIDECAR}" != "true" ]]; then
  cat > /etc/systemd/system/tanxiaer-sidecar.service << UNIT_EOF
[Unit]
Description=Tanxiaer Sidecar (OSS/TTS/Video/Douyin)
After=network.target

[Service]
Type=simple
User=${SERVICE_USER}
EnvironmentFile=${ROOT_DIR}/sidecar/.env
WorkingDirectory=${ROOT_DIR}/sidecar
ExecStart=/usr/bin/node ${ROOT_DIR}/sidecar/index.js
Restart=on-failure
RestartSec=5
StandardOutput=append:${ROOT_DIR}/logs/sidecar.log
StandardError=append:${ROOT_DIR}/logs/sidecar-error.log

[Install]
WantedBy=multi-user.target
UNIT_EOF
  log "tanxiaer-sidecar.service 已写"
fi

systemctl daemon-reload
chown -R "${SERVICE_USER}:${SERVICE_USER}" "${ROOT_DIR}/logs"

# ── 8. 启服 ────────────────────────────────────────────────────────────────
step "[8/9] 启动服务"
[[ "${SKIP_BACKEND}" != "true" ]] && { systemctl enable tanxiaer 2>/dev/null; systemctl start tanxiaer; log "tanxiaer 已启"; }
[[ "${SKIP_SIDECAR}" != "true" ]] && { systemctl enable tanxiaer-sidecar 2>/dev/null; systemctl start tanxiaer-sidecar; log "tanxiaer-sidecar 已启"; }

# ── 9. 自检（curl 真请求，看 HTTP 状态码） ───────────────────────────────
step "[9/9] 自检"
sleep 3

PASS=0; FAIL=0
check_http() {
  local label="$1" url="$2" expected="$3"
  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null || echo 000)
  if [[ "$code" == "$expected" ]] || [[ ",$expected," == *",$code,"* ]]; then
    log "$label → HTTP $code"
    PASS=$((PASS+1))
  else
    err "$label → HTTP $code (期望 $expected)"
    FAIL=$((FAIL+1))
  fi
}

# 等后端就绪（最多 60s）
if [[ "${SKIP_BACKEND}" != "true" ]]; then
  i=0
  while [[ $i -lt 12 ]]; do
    sleep 5
    code=$(curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:${SERVER_PORT}/admin-api/system/auth/captcha" 2>/dev/null || echo 000)
    if [[ "$code" == "200" ]]; then break; fi
    i=$((i+1))
    echo -n "."
  done
fi

[[ "${SKIP_BACKEND}"  != "true" ]] && check_http "后端 captcha"         "http://127.0.0.1:${SERVER_PORT}/admin-api/system/auth/captcha" "200"
[[ "${SKIP_NGINX}"    != "true" ]] && check_http "官网 /"               "http://127.0.0.1/"                                              "200"
[[ "${SKIP_FRONTEND}" != "true" ]] && check_http "PC 后台 /admin/"      "http://127.0.0.1/admin/"                                        "200"
[[ "${SKIP_FRONTEND}" != "true" ]] && check_http "H5 /m/"               "http://127.0.0.1/m/"                                            "200"
[[ "${SKIP_FRONTEND}" != "true" ]] && {
  ASSET=$(ls "${ROOT_DIR}/m/assets/"index-*.js 2>/dev/null | head -1 | xargs -I{} basename {})
  [[ -n "$ASSET" ]] && check_http "H5 资源 /m/assets/${ASSET}" "http://127.0.0.1/m/assets/${ASSET}" "200"
}
[[ "${SKIP_SIDECAR}"  != "true" ]] && check_http "sidecar /healthz"     "http://127.0.0.1:${SIDECAR_PORT}/healthz"                       "200"

echo ""
PUBLIC_IP=$(curl -s --max-time 3 ifconfig.me 2>/dev/null || echo "127.0.0.1")
echo -e "${GREEN}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  部署完成 — 通过 $PASS 项 / 失败 $FAIL 项                       ${NC}"
echo -e "${GREEN}╠══════════════════════════════════════════════════════════╣${NC}"
echo -e "${GREEN}║  官网首页:    http://${PUBLIC_IP}/                       ${NC}"
echo -e "${GREEN}║  H5 入口:    http://${PUBLIC_IP}/m/                       ${NC}"
echo -e "${GREEN}║  PC 后台:    http://${PUBLIC_IP}/admin/                   ${NC}"
echo -e "${GREEN}║  API:        http://${PUBLIC_IP}/admin-api/               ${NC}"
echo -e "${GREEN}║  备份:       ${BACKUP_DIR}                                ${NC}"
echo -e "${GREEN}║  日志:       ${ROOT_DIR}/logs/                            ${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════════╝${NC}"

if [[ $FAIL -gt 0 ]]; then
  echo ""
  err "有 $FAIL 项自检失败，回滚命令："
  echo "  systemctl stop tanxiaer tanxiaer-sidecar"
  echo "  cp -r ${BACKUP_DIR}/* ${ROOT_DIR}/    # 视具体目录调整"
  echo "  systemctl restart tanxiaer tanxiaer-sidecar"
  exit 1
fi

log "🎉 全部就绪"
exit 0
