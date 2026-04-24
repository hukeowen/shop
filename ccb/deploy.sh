#!/usr/bin/env bash
# =============================================================================
#  摊小二 一键部署脚本
#  适用于 Ubuntu 20.04 / 22.04 全新服务器
#  用法: sudo bash deploy.sh [--skip-install] [--skip-build] [--help]
#
#  首次使用：cp .env.example .env && vim .env 填写密码，再运行本脚本
# =============================================================================
set -euo pipefail

# ── 加载 .env（敏感配置外置） ─────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ENV_FILE:-${SCRIPT_DIR}/.env}"
if [[ -f "${ENV_FILE}" ]]; then
  set -a; source "${ENV_FILE}"; set +a
else
  echo "[✗] 缺少配置文件：${ENV_FILE}" >&2
  echo "    请执行：cp ${SCRIPT_DIR}/.env.example ${ENV_FILE} 后填写密码再运行" >&2
  exit 1
fi

# ── 非敏感默认值 ─────────────────────────────────────────────────────────────
REPO_URL="${REPO_URL:-https://github.com/hukeowen/shop.git}"
BRANCH="${BRANCH:-main}"
ROOT_DIR="${ROOT_DIR:-/opt/tanxiaer}"
SERVER_NAME="${SERVER_NAME:-_}"
DB_NAME="${DB_NAME:-ruoyi-vue-pro}"
DB_USER="${DB_USER:-tanxiaer}"
REDIS_PORT="${REDIS_PORT:-6379}"
SERVER_PORT="${SERVER_PORT:-48080}"
SERVICE_USER="${SERVICE_USER:-tanxiaer}"
MAVEN_VERSION="${MAVEN_VERSION:-3.9.6}"
NODE_VERSION="${NODE_VERSION:-18}"

# ── 必填密码校验（从 .env 读取，缺失即退出） ──────────────────────────────────
: "${MYSQL_ROOT_PASS:?MYSQL_ROOT_PASS 未在 .env 中设置}"
: "${DB_PASS:?DB_PASS 未在 .env 中设置}"
: "${REDIS_PASS:?REDIS_PASS 未在 .env 中设置}"
: "${MERCHANT_INTERNAL_TOKEN:?MERCHANT_INTERNAL_TOKEN 未在 .env 中设置（≥16 字符）}"

# 可选第三方 API Key（未填写则服务端会在运行时报错，这里不强制）
VOLCANO_APP_ID="${VOLCANO_APP_ID:-}"
VOLCANO_ACCESS_TOKEN="${VOLCANO_ACCESS_TOKEN:-}"
VOLCANO_AK="${VOLCANO_AK:-}"
VOLCANO_SK="${VOLCANO_SK:-}"
DOUYIN_CLIENT_KEY="${DOUYIN_CLIENT_KEY:-}"
DOUYIN_CLIENT_SECRET="${DOUYIN_CLIENT_SECRET:-}"

# ── 颜色输出 ─────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; NC='\033[0m'
log()  { echo -e "${GREEN}[✓]${NC} $*"; }
info() { echo -e "${BLUE}[i]${NC} $*"; }
warn() { echo -e "${YELLOW}[!]${NC} $*"; }
err()  { echo -e "${RED}[✗]${NC} $*" >&2; }
step() { echo -e "\n${CYAN}══════ $* ══════${NC}"; }

# ── MySQL 凭据文件（避免 -p$PASS 在 ps 中暴露） ───────────────────────────────
MYSQL_DEFAULTS_FILE=""
init_mysql_defaults_file() {
  MYSQL_DEFAULTS_FILE="$(mktemp)"
  chmod 600 "${MYSQL_DEFAULTS_FILE}"
  cat > "${MYSQL_DEFAULTS_FILE}" << EOF
[client]
user=root
password=${MYSQL_ROOT_PASS}
EOF
  # 脚本退出时立即清理
  trap 'rm -f "${MYSQL_DEFAULTS_FILE}"' EXIT
}
mysql_safe() { mysql --defaults-extra-file="${MYSQL_DEFAULTS_FILE}" "$@"; }

# ── 命令行参数 ────────────────────────────────────────────────────────────────
SKIP_INSTALL=false
SKIP_BUILD=false
for arg in "$@"; do
  case $arg in
    --skip-install) SKIP_INSTALL=true ;;
    --skip-build)   SKIP_BUILD=true ;;
    --help)
      cat << HELP
用法: sudo bash deploy.sh [--skip-install] [--skip-build]
  --skip-install  跳过系统软件安装（已装过时使用）
  --skip-build    跳过编译打包（仅重新部署已有制品）

配置文件: ${ENV_FILE}
  首次使用请 cp .env.example .env 后填写密码
HELP
      exit 0 ;;
  esac
done

# ── 根权限检查 ────────────────────────────────────────────────────────────────
if [[ $EUID -ne 0 ]]; then
  err "请以 root 权限运行：sudo bash deploy.sh"
  exit 1
fi

echo -e "${CYAN}"
echo "  ████████╗ █████╗ ███╗   ██╗██╗  ██╗██╗ █████╗  ██████╗ ███████╗██████╗ "
echo "     ██╔══╝██╔══██╗████╗  ██║╚██╗██╔╝██║██╔══██╗██╔═══██╗██╔════╝██╔══██╗"
echo "     ██║   ███████║██╔██╗ ██║ ╚███╔╝ ██║███████║██║   ██║█████╗  ██████╔╝"
echo "     ██║   ██╔══██║██║╚██╗██║ ██╔██╗ ██║██╔══██║██║   ██║██╔══╝  ██╔══██╗"
echo "     ██║   ██║  ██║██║ ╚████║██╔╝ ██╗██║██║  ██║╚██████╔╝███████╗██║  ██║"
echo "     ╚═╝   ╚═╝  ╚═╝╚═╝  ╚═══╝╚═╝  ╚═╝╚═╝╚═╝  ╚═╝ ╚═════╝ ╚══════╝╚═╝  ╚═╝"
echo "                        摊小二 · 全栈一键部署脚本"
echo -e "${NC}"
info "目标目录: ${ROOT_DIR}"
info "仓库地址: ${REPO_URL}"
info "分支:     ${BRANCH}"
info "配置来源: ${ENV_FILE}"
echo ""

# =============================================================================
install_base() {
  step "安装基础依赖"
  apt-get update -y
  apt-get install -y \
    curl wget git unzip zip openssl \
    build-essential ca-certificates gnupg \
    lsb-release software-properties-common \
    net-tools ufw
  log "基础依赖安装完成"
}

install_java() {
  step "安装 OpenJDK 8"
  if java -version 2>&1 | grep -q "1\.8\|version \"8"; then
    log "JDK 8 已安装，跳过"
    return
  fi
  apt-get install -y openjdk-8-jdk
  cat > /etc/profile.d/java.sh << 'EOF'
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
EOF
  log "JDK 8 安装完成"
}

install_maven() {
  step "安装 Maven ${MAVEN_VERSION}"
  if /opt/maven/bin/mvn -version 2>/dev/null | grep -q "${MAVEN_VERSION}"; then
    log "Maven ${MAVEN_VERSION} 已安装，跳过"
    return
  fi
  local MVN_URL="https://mirrors.aliyun.com/apache/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
  wget -q -O /tmp/maven.tar.gz "${MVN_URL}"
  tar -xzf /tmp/maven.tar.gz -C /opt/
  ln -sfn "/opt/apache-maven-${MAVEN_VERSION}" /opt/maven
  cat > /etc/profile.d/maven.sh << 'EOF'
export M2_HOME=/opt/maven
export PATH=$M2_HOME/bin:$PATH
EOF

  mkdir -p /root/.m2
  cat > /root/.m2/settings.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>*</mirrorOf>
      <name>阿里云公共仓库</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
  <profiles>
    <profile>
      <id>jdk-8</id>
      <activation><jdk>1.8</jdk></activation>
      <properties>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
      </properties>
    </profile>
  </profiles>
</settings>
EOF
  log "Maven ${MAVEN_VERSION} 安装完成"
}

install_node() {
  step "安装 Node.js ${NODE_VERSION} + pnpm"
  if node --version 2>/dev/null | grep -q "v${NODE_VERSION}"; then
    log "Node.js ${NODE_VERSION} 已安装，跳过"
  else
    curl -fsSL "https://deb.nodesource.com/setup_${NODE_VERSION}.x" | bash -
    apt-get install -y nodejs
  fi
  if ! command -v pnpm &>/dev/null; then
    npm install -g pnpm --registry=https://registry.npmmirror.com
  fi
  log "Node $(node --version), pnpm $(pnpm --version)"
}

install_nginx() {
  step "安装 Nginx"
  if command -v nginx &>/dev/null; then
    log "Nginx 已安装，跳过"
    return
  fi
  apt-get install -y nginx
  systemctl enable nginx
  log "Nginx 安装完成"
}

install_mysql() {
  step "安装 MySQL 8"
  if command -v mysql &>/dev/null; then
    log "MySQL 已安装，跳过 root 密码初始化"
    return
  fi
  apt-get install -y mysql-server
  systemctl enable mysql
  systemctl start mysql
  # Ubuntu 22.04 MySQL 8 默认 auth_socket，通过 unix socket 以 root 直连设置密码
  mysql -u root << SQL
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '${MYSQL_ROOT_PASS}';
FLUSH PRIVILEGES;
SQL
  log "MySQL 安装完成，root 密码已设置"
}

install_redis() {
  step "安装 Redis"
  if ! command -v redis-server &>/dev/null; then
    apt-get install -y redis-server
  fi
  sed -i "s/^# *requirepass .*/requirepass ${REDIS_PASS}/" /etc/redis/redis.conf
  if ! grep -q "^requirepass" /etc/redis/redis.conf; then
    echo "requirepass ${REDIS_PASS}" >> /etc/redis/redis.conf
  fi
  sed -i "s/^port .*/port ${REDIS_PORT}/" /etc/redis/redis.conf
  chmod 640 /etc/redis/redis.conf
  systemctl enable redis-server
  systemctl restart redis-server
  log "Redis 配置完成（端口: ${REDIS_PORT}）"
}

create_service_user() {
  step "创建服务运行用户: ${SERVICE_USER}"
  if id -u "${SERVICE_USER}" &>/dev/null; then
    log "用户 ${SERVICE_USER} 已存在"
  else
    useradd --system --no-create-home --shell /usr/sbin/nologin "${SERVICE_USER}"
    log "已创建系统用户 ${SERVICE_USER}"
  fi
}

pull_code() {
  step "拉取代码"
  if [[ -d "${ROOT_DIR}/repo/.git" ]]; then
    info "仓库已存在，执行 git pull"
    cd "${ROOT_DIR}/repo"
    git fetch origin
    git checkout "${BRANCH}"
    git pull origin "${BRANCH}"
  else
    mkdir -p "${ROOT_DIR}/repo"
    git clone --branch "${BRANCH}" --depth=1 "${REPO_URL}" "${ROOT_DIR}/repo"
  fi
  log "代码已更新到 $(git -C ${ROOT_DIR}/repo rev-parse --short HEAD)"
}

setup_database() {
  step "初始化数据库"
  init_mysql_defaults_file

  mysql_safe << SQL
CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASS}';
GRANT ALL PRIVILEGES ON \`${DB_NAME}\`.* TO '${DB_USER}'@'localhost';
FLUSH PRIVILEGES;
SQL

  # 幂等性判断：已存在核心表则跳过 SQL 导入（避免重跑时重复 seed）
  local TABLE_COUNT
  TABLE_COUNT=$(mysql_safe -Nse "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}' AND table_name='system_users';" 2>/dev/null || echo 0)
  if [[ "${TABLE_COUNT}" -gt 0 ]]; then
    warn "检测到已初始化（system_users 存在），跳过 SQL 导入"
    log "数据库初始化完成（已有数据）"
    return
  fi

  local SQL_DIR="${ROOT_DIR}/repo/sql/mysql"
  local SQL_FILES=("ruoyi-vue-pro.sql" "mall.sql" "mp.sql" "member_pay.sql" "merchant.sql" "video.sql" "v2_business_tables.sql")

  for f in "${SQL_FILES[@]}"; do
    if [[ -f "${SQL_DIR}/${f}" ]]; then
      info "导入 ${f}..."
      mysql_safe "${DB_NAME}" < "${SQL_DIR}/${f}"
      log "${f} 导入完成"
    else
      warn "${f} 不存在，跳过"
    fi
  done

  # fix_*.sql 按文件名排序导入（避免 word splitting 问题）
  shopt -s nullglob
  local FIX_FILES=("${SQL_DIR}"/fix_*.sql)
  shopt -u nullglob
  IFS=$'\n' FIX_FILES=($(printf '%s\n' "${FIX_FILES[@]}" | sort)); unset IFS
  for f in "${FIX_FILES[@]}"; do
    info "导入 $(basename "${f}")..."
    mysql_safe "${DB_NAME}" < "${f}"
    log "$(basename "${f}") 导入完成"
  done

  log "数据库初始化完成"
}

write_prod_config() {
  step "生成生产环境配置"
  local RESOURCES="${ROOT_DIR}/repo/yudao-server/src/main/resources"
  mkdir -p "${RESOURCES}"

  # 动态生成 JWT token secret，存入 ROOT_DIR 以保证重跑时一致（避免踢掉已登录用户）
  local TOKEN_SECRET_FILE="${ROOT_DIR}/.token-secret"
  if [[ ! -s "${TOKEN_SECRET_FILE}" ]]; then
    umask 077
    openssl rand -base64 48 | tr -d '\n' > "${TOKEN_SECRET_FILE}"
  fi
  chmod 600 "${TOKEN_SECRET_FILE}"
  local TOKEN_SECRET
  TOKEN_SECRET="$(cat "${TOKEN_SECRET_FILE}")"

  umask 077
  cat > "${RESOURCES}/application-prod.yaml" << YAML_EOF
spring:
  datasource:
    dynamic:
      primary: master
      strict: false
      datasource:
        master:
          url: jdbc:mysql://127.0.0.1:3306/${DB_NAME}?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=UTF-8
          driver-class-name: com.mysql.cj.jdbc.Driver
          username: ${DB_USER}
          password: ${DB_PASS}
  data:
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
YAML_EOF
  chmod 600 "${RESOURCES}/application-prod.yaml"
  umask 022
  log "application-prod.yaml 写入完成（mode 600）"
}

build_backend() {
  step "编译后端（Maven）"
  export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
  export PATH="${JAVA_HOME}/bin:/opt/maven/bin:${PATH}"

  cd "${ROOT_DIR}/repo"
  /opt/maven/bin/mvn clean package -DskipTests -P prod \
    --batch-mode -T 1C 2>&1 | tail -40

  log "后端编译完成: $(ls yudao-server/target/yudao-server.jar)"
}

build_admin_frontend() {
  step "编译管理后台前端"
  local UI_DIR="${ROOT_DIR}/repo/yudao-ui/yudao-ui-admin-vue3"
  cd "${UI_DIR}"

  cat > .env.production << 'ENVEOF'
VITE_DEV = false
VITE_APP_TITLE = 摊小二管理后台
VITE_BASE_URL = /
VITE_API_URL = /admin-api
VITE_APP_CAPTCHA_ENABLE = false
ENVEOF

  pnpm install --registry=https://registry.npmmirror.com
  pnpm build:prod

  local DIST_DIR="${ROOT_DIR}/admin-dist"
  rm -rf "${DIST_DIR}"
  cp -r dist "${DIST_DIR}"
  log "管理后台前端编译完成 → ${DIST_DIR}"
}

deploy_website() {
  step "部署官网静态文件"
  local WEB_DIR="${ROOT_DIR}/website"
  mkdir -p "${WEB_DIR}"
  cp "${ROOT_DIR}/repo/docs/website/index.html" "${WEB_DIR}/index.html"
  [[ -d "${ROOT_DIR}/repo/docs/website/assets" ]] && \
    cp -r "${ROOT_DIR}/repo/docs/website/assets" "${WEB_DIR}/"
  log "官网部署完成 → ${WEB_DIR}"
}

configure_nginx() {
  step "配置 Nginx"
  local NGINX_CONF="/etc/nginx/sites-available/tanxiaer"

  cat > "${NGINX_CONF}" << NGINX_EOF
upstream tanxiaer_backend {
    server 127.0.0.1:${SERVER_PORT};
    keepalive 32;
}

server {
    listen 80;
    server_name ${SERVER_NAME};
    charset utf-8;
    client_max_body_size 50m;

    # 官网首页
    location = / {
        root ${ROOT_DIR}/website;
        index index.html;
    }
    location /assets/ {
        root ${ROOT_DIR}/website;
        expires 7d;
        add_header Cache-Control "public, immutable";
    }

    # 管理后台
    location /admin/ {
        alias ${ROOT_DIR}/admin-dist/;
        index index.html;
        try_files \$uri \$uri/ /admin/index.html;
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

    # WebSocket
    location /ws {
        proxy_pass http://tanxiaer_backend;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host \$host;
        proxy_read_timeout 3600s;
    }

    access_log /var/log/nginx/tanxiaer_access.log;
    error_log  /var/log/nginx/tanxiaer_error.log;
}
NGINX_EOF

  ln -sfn "${NGINX_CONF}" /etc/nginx/sites-enabled/tanxiaer
  rm -f /etc/nginx/sites-enabled/default 2>/dev/null || true
  nginx -t
  systemctl reload nginx
  log "Nginx 配置完成"
}

deploy_backend_service() {
  step "部署后端服务（systemd）"
  local JAR_SRC="${ROOT_DIR}/repo/yudao-server/target/yudao-server.jar"
  local JAR_DEST="${ROOT_DIR}/app/yudao-server.jar"
  mkdir -p "${ROOT_DIR}/app" "${ROOT_DIR}/logs"

  systemctl stop tanxiaer 2>/dev/null || true
  cp "${JAR_SRC}" "${JAR_DEST}"

  # 目录所有权交给服务用户
  chown -R "${SERVICE_USER}:${SERVICE_USER}" "${ROOT_DIR}/app" "${ROOT_DIR}/logs"

  # systemd unit（环境变量单独存入 EnvironmentFile，便于控制权限）
  local ENV_UNIT="${ROOT_DIR}/app/runtime.env"
  umask 077
  cat > "${ENV_UNIT}" << ENV_EOF
MERCHANT_INTERNAL_TOKEN=${MERCHANT_INTERNAL_TOKEN}
VOLCANO_APP_ID=${VOLCANO_APP_ID}
VOLCANO_ACCESS_TOKEN=${VOLCANO_ACCESS_TOKEN}
VOLCANO_AK=${VOLCANO_AK}
VOLCANO_SK=${VOLCANO_SK}
DOUYIN_CLIENT_KEY=${DOUYIN_CLIENT_KEY}
DOUYIN_CLIENT_SECRET=${DOUYIN_CLIENT_SECRET}
ENV_EOF
  chown "${SERVICE_USER}:${SERVICE_USER}" "${ENV_UNIT}"
  chmod 600 "${ENV_UNIT}"
  umask 022

  cat > /etc/systemd/system/tanxiaer.service << UNIT_EOF
[Unit]
Description=摊小二后端服务
After=network.target mysql.service redis-server.service

[Service]
Type=simple
User=${SERVICE_USER}
Group=${SERVICE_USER}
WorkingDirectory=${ROOT_DIR}/app
EnvironmentFile=${ENV_UNIT}
ExecStart=/usr/bin/java \\
  -server \\
  -Xms512m -Xmx1024m \\
  -XX:+UseG1GC \\
  -Dspring.profiles.active=prod \\
  -Dfile.encoding=UTF-8 \\
  -jar ${JAR_DEST}
Restart=always
RestartSec=10
StandardOutput=append:${ROOT_DIR}/logs/stdout.log
StandardError=append:${ROOT_DIR}/logs/stderr.log

# 沙箱加固
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=${ROOT_DIR}/logs ${ROOT_DIR}/app

[Install]
WantedBy=multi-user.target
UNIT_EOF

  chmod 644 /etc/systemd/system/tanxiaer.service

  systemctl daemon-reload
  systemctl enable tanxiaer
  systemctl start tanxiaer
  log "服务已启动（运行用户: ${SERVICE_USER}），等待就绪（最多 90 秒）..."

  local i=0
  while [[ $i -lt 18 ]]; do
    sleep 5
    if curl -sf "http://localhost:${SERVER_PORT}/admin-api/system/auth/captcha" &>/dev/null; then
      log "后端服务就绪 ✓"
      return
    fi
    i=$((i+1))
    echo -n "."
  done
  warn "启动超时，请检查: journalctl -u tanxiaer -n 80 --no-pager"
}

setup_firewall() {
  step "配置防火墙"
  # 必须先放行再 enable，否则 --force enable 会立即切断当前 SSH 会话
  ufw allow 22/tcp
  ufw allow 80/tcp
  ufw allow 443/tcp
  ufw --force enable
  log "防火墙已启用（对外仅开放 22/80/443）"
}

# =============================================================================
main() {
  mkdir -p "${ROOT_DIR}"

  if [[ "${SKIP_INSTALL}" == false ]]; then
    install_base
    install_java
    install_maven
    install_node
    install_nginx
    install_mysql
    install_redis
  else
    warn "跳过系统软件安装（--skip-install）"
  fi

  create_service_user
  pull_code
  setup_database
  write_prod_config

  if [[ "${SKIP_BUILD}" == false ]]; then
    build_backend
    build_admin_frontend
  else
    warn "跳过编译打包（--skip-build）"
  fi

  deploy_website
  configure_nginx
  deploy_backend_service
  setup_firewall

  PUBLIC_IP=$(curl -s --max-time 5 ifconfig.me 2>/dev/null || echo "YOUR_SERVER_IP")
  echo ""
  echo -e "${GREEN}╔══════════════════════════════════════════════════════╗${NC}"
  echo -e "${GREEN}║           🎉  摊小二部署完成！                      ║${NC}"
  echo -e "${GREEN}╠══════════════════════════════════════════════════════╣${NC}"
  echo -e "${GREEN}║  官网首页:   http://${PUBLIC_IP}/                   ${NC}"
  echo -e "${GREEN}║  管理后台:   http://${PUBLIC_IP}/admin/             ${NC}"
  echo -e "${GREEN}║  日志目录:   ${ROOT_DIR}/logs/                      ${NC}"
  echo -e "${GREEN}║  服务管理:   systemctl {start|stop|restart} tanxiaer ${NC}"
  echo -e "${GREEN}║  运行用户:   ${SERVICE_USER} (非 root)              ${NC}"
  echo -e "${GREEN}╚══════════════════════════════════════════════════════╝${NC}"
  echo ""
  echo -e "${YELLOW}生产上线前请务必：${NC}"
  echo "  1. 将 SERVER_NAME 改为你的真实域名"
  echo "  2. 执行 certbot --nginx 配置 HTTPS"
  echo "  3. 在 .env 中填入真实的火山引擎 / 抖音 API Key"
  echo "  4. 确认 .env 已加入 .gitignore，不会被提交"
}

main "$@"
