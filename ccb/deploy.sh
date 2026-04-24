#!/usr/bin/env bash
# =============================================================================
#  摊小二 一键部署脚本
#  适用于：CentOS 7.9 x86_64（阿里云 ECS）
#  用法：sudo bash deploy.sh [--skip-install] [--skip-build] [--help]
#
#  首次使用：cp .env.example .env && vim .env 填写密码，再运行本脚本
#
#  注意事项：
#   - CentOS 7 glibc 2.17 仅支持 Node.js ≤16（Node 18+ 需要 glibc 2.28）
#   - 阿里云 ECS 请在安全组额外放行 80/443（firewalld 只管系统防火墙）
# =============================================================================
set -euo pipefail

# ── 错误陷阱：哪一行死的、哪个命令、退出码，一目了然 ──────────────────────────
trap 'rc=$?; echo -e "\033[0;31m[✗] 脚本在第 ${LINENO} 行异常退出（退出码=${rc}）\033[0m" >&2; echo -e "\033[0;31m    失败命令: ${BASH_COMMAND}\033[0m" >&2; exit $rc' ERR

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
MAVEN_VERSION="${MAVEN_VERSION:-3.9.9}"
NODE_VERSION="${NODE_VERSION:-16}"   # CentOS 7 glibc 限制，最多 Node 16

# ── 必填密码校验 ─────────────────────────────────────────────────────────────
: "${MYSQL_ROOT_PASS:?MYSQL_ROOT_PASS 未在 .env 中设置}"
: "${DB_PASS:?DB_PASS 未在 .env 中设置}"
: "${REDIS_PASS:?REDIS_PASS 未在 .env 中设置}"
: "${MERCHANT_INTERNAL_TOKEN:?MERCHANT_INTERNAL_TOKEN 未在 .env 中设置（≥16 字符）}"

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

# ── 环境检查 ─────────────────────────────────────────────────────────────────
if [[ $EUID -ne 0 ]]; then
  err "请以 root 权限运行：sudo bash deploy.sh"
  exit 1
fi
if [[ ! -f /etc/redhat-release ]] || ! grep -qi "centos" /etc/redhat-release 2>/dev/null; then
  warn "未检测到 CentOS，本脚本仅在 CentOS 7.9 验证过，继续执行风险自负"
fi

echo -e "${CYAN}"
echo "  ████████╗ █████╗ ███╗   ██╗██╗  ██╗██╗ █████╗  ██████╗ ███████╗██████╗ "
echo "     ██╔══╝██╔══██╗████╗  ██║╚██╗██╔╝██║██╔══██╗██╔═══██╗██╔════╝██╔══██╗"
echo "     ██║   ███████║██╔██╗ ██║ ╚███╔╝ ██║███████║██║   ██║█████╗  ██████╔╝"
echo "     ██║   ██╔══██║██║╚██╗██║ ██╔██╗ ██║██╔══██║██║   ██║██╔══╝  ██╔══██╗"
echo "     ██║   ██║  ██║██║ ╚████║██╔╝ ██╗██║██║  ██║╚██████╔╝███████╗██║  ██║"
echo "     ╚═╝   ╚═╝  ╚═╝╚═╝  ╚═══╝╚═╝  ╚═╝╚═╝╚═╝  ╚═╝ ╚═════╝ ╚══════╝╚═╝  ╚═╝"
echo "                  摊小二 · 全栈一键部署（CentOS 7.9 / 阿里云）"
echo -e "${NC}"
info "目标目录: ${ROOT_DIR}"
info "仓库地址: ${REPO_URL}"
info "分支:     ${BRANCH}"
info "配置来源: ${ENV_FILE}"
echo ""

# =============================================================================
install_base() {
  step "安装基础依赖 + EPEL + 时区"
  yum install -y epel-release
  yum install -y \
    curl wget git unzip zip which \
    gcc gcc-c++ make \
    ca-certificates openssl \
    net-tools firewalld \
    yum-utils policycoreutils-python
  timedatectl set-timezone Asia/Shanghai || true
  log "基础依赖安装完成"
}

configure_selinux() {
  step "检查 SELinux"
  if command -v getenforce &>/dev/null && [[ $(getenforce) == "Enforcing" ]]; then
    setenforce 0
    sed -i 's/^SELINUX=.*/SELINUX=permissive/' /etc/selinux/config
    warn "SELinux 已切为 permissive（避免 nginx 访问 /opt/tanxiaer 被阻），持久化生效需重启一次"
  else
    log "SELinux 未启用或已为 permissive，跳过"
  fi
}

install_java() {
  step "安装 OpenJDK 8"
  # pipefail 下 `cmd | grep -q` 可能因 SIGPIPE 报错；改成字符串匹配更稳
  local JV=""
  JV="$(java -version 2>&1 || true)"
  if [[ "${JV}" == *"1.8"* ]]; then
    log "JDK 8 已安装，跳过"
  else
    yum install -y java-1.8.0-openjdk java-1.8.0-openjdk-devel
  fi
  if ! command -v java &>/dev/null; then
    err "Java 安装失败，command -v java 找不到命令"
    exit 1
  fi
  # 动态探测 JAVA_HOME（CentOS 包含版本号后缀，需 readlink）
  local JH
  JH=$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")
  cat > /etc/profile.d/java.sh << EOF
export JAVA_HOME=${JH}
export PATH=\$JAVA_HOME/bin:\$PATH
EOF
  log "JDK 8 安装完成，JAVA_HOME=${JH}"
}

install_maven() {
  step "安装 Maven ${MAVEN_VERSION}"
  if [[ -x /opt/maven/bin/mvn ]]; then
    local MV=""
    MV="$(/opt/maven/bin/mvn -version 2>&1 || true)"
    if [[ "${MV}" == *"${MAVEN_VERSION}"* ]]; then
      log "Maven ${MAVEN_VERSION} 已安装，跳过"
      return
    fi
  fi
  # Apache 镜像只保留当前版本，旧版本要去 archive；做一组 fallback
  local MVN_MIRROR="https://mirrors.aliyun.com/apache/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
  local MVN_CDN="https://dlcdn.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
  local MVN_ARCHIVE="https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
  local OK=false
  for URL in "${MVN_MIRROR}" "${MVN_CDN}" "${MVN_ARCHIVE}"; do
    info "下载 Maven：${URL}"
    if curl -fL --connect-timeout 10 --retry 2 --retry-delay 2 \
            -o /tmp/maven.tar.gz "${URL}"; then
      OK=true; break
    fi
    warn "该源不可用，换下一个..."
  done
  if [[ "${OK}" != "true" ]]; then
    err "所有 Maven 下载源都失败了，请检查网络"; exit 1
  fi
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
  # Node 16 已 EOL，nodesource 对 EOL 版本支持不稳，直接用官方二进制 tarball
  local NODE_FULL="v16.20.2"
  local NODE_ARCH="linux-x64"
  local NODE_DIR="/opt/node-${NODE_FULL}-${NODE_ARCH}"

  local NV=""
  NV="$(/opt/nodejs/bin/node --version 2>/dev/null || node --version 2>/dev/null || true)"
  if [[ "${NV}" == "${NODE_FULL}" ]]; then
    log "Node.js ${NV} 已安装，跳过"
  else
    # 优先淘宝镜像，次选官方
    local NODE_URL_MIRROR="https://cdn.npmmirror.com/binaries/node/${NODE_FULL}/node-${NODE_FULL}-${NODE_ARCH}.tar.xz"
    local NODE_URL_OFFICIAL="https://nodejs.org/dist/${NODE_FULL}/node-${NODE_FULL}-${NODE_ARCH}.tar.xz"
    info "下载 Node.js ${NODE_FULL}（淘宝镜像）"
    if ! curl -fL --connect-timeout 10 --retry 3 --retry-delay 2 \
              -o /tmp/node.tar.xz "${NODE_URL_MIRROR}"; then
      warn "淘宝镜像失败，切换官方源"
      curl -fL --connect-timeout 10 --retry 3 --retry-delay 2 \
           -o /tmp/node.tar.xz "${NODE_URL_OFFICIAL}"
    fi
    # CentOS 7 需要 xz 解包
    command -v xz &>/dev/null || yum install -y xz
    tar -xf /tmp/node.tar.xz -C /opt/
    ln -sfn "${NODE_DIR}" /opt/nodejs
    ln -sfn /opt/nodejs/bin/node /usr/local/bin/node
    ln -sfn /opt/nodejs/bin/npm  /usr/local/bin/npm
    ln -sfn /opt/nodejs/bin/npx  /usr/local/bin/npx
    cat > /etc/profile.d/nodejs.sh << 'EOF'
export PATH=/opt/nodejs/bin:$PATH
EOF
    rm -f /tmp/node.tar.xz
  fi

  # 当前 shell 立即生效
  export PATH=/opt/nodejs/bin:${PATH}

  if ! command -v pnpm &>/dev/null; then
    /opt/nodejs/bin/npm install -g pnpm --registry=https://registry.npmmirror.com
    ln -sfn /opt/nodejs/bin/pnpm /usr/local/bin/pnpm 2>/dev/null || true
  fi
  log "Node $(node --version), pnpm $(pnpm --version)"
}

install_nginx() {
  step "安装 Nginx"
  if command -v nginx &>/dev/null; then
    log "Nginx 已安装，跳过"
  else
    yum install -y nginx
  fi
  systemctl enable nginx
  log "Nginx 安装完成"
}

install_mysql() {
  step "安装 MySQL 8"
  if rpm -q mysql-community-server &>/dev/null; then
    log "MySQL 已安装，跳过 root 密码初始化"
    systemctl enable mysqld 2>/dev/null || true
    systemctl start mysqld
    return
  fi
  # CentOS 7 默认有 mariadb-libs，会冲突，先清理
  yum remove -y mariadb-libs 2>/dev/null || true

  # MySQL 官方仓库 + 导入 2022 轮转后的 GPG key
  rpm -Uvh https://dev.mysql.com/get/mysql80-community-release-el7-7.noarch.rpm 2>/dev/null || true
  rpm --import https://repo.mysql.com/RPM-GPG-KEY-mysql-2022 2>/dev/null || true

  yum install -y mysql-community-server
  systemctl enable mysqld
  systemctl start mysqld

  # MySQL 8 首次启动在 /var/log/mysqld.log 留临时密码
  local TEMP_PASS
  TEMP_PASS=$(awk '/temporary password/ {print $NF}' /var/log/mysqld.log | tail -1)
  if [[ -z "${TEMP_PASS}" ]]; then
    err "未能从 /var/log/mysqld.log 获取 MySQL 临时密码，请手动初始化"
    exit 1
  fi

  mysql --connect-expired-password -uroot -p"${TEMP_PASS}" << SQL || true
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '${MYSQL_ROOT_PASS}';
FLUSH PRIVILEGES;
SQL
  log "MySQL 8 安装完成，root 密码已重置"
}

install_redis() {
  step "安装 Redis（EPEL）"
  if ! command -v redis-server &>/dev/null; then
    yum install -y redis
  fi
  sed -i "s/^# *requirepass .*/requirepass ${REDIS_PASS}/" /etc/redis.conf
  if ! grep -q "^requirepass" /etc/redis.conf; then
    echo "requirepass ${REDIS_PASS}" >> /etc/redis.conf
  fi
  sed -i "s/^port .*/port ${REDIS_PORT}/" /etc/redis.conf
  # 仅监听本机，不对外暴露
  sed -i 's/^bind .*/bind 127.0.0.1/' /etc/redis.conf
  chmod 640 /etc/redis.conf
  systemctl enable redis
  systemctl restart redis
  log "Redis 配置完成（端口: ${REDIS_PORT}，仅监听 127.0.0.1）"
}

create_service_user() {
  step "创建服务运行用户: ${SERVICE_USER}"
  if id -u "${SERVICE_USER}" &>/dev/null; then
    log "用户 ${SERVICE_USER} 已存在"
  else
    useradd --system --no-create-home --shell /sbin/nologin "${SERVICE_USER}"
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

  # 幂等性判断：已存在核心表则跳过
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
  source /etc/profile.d/java.sh
  export PATH="/opt/maven/bin:${PATH}"

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
  # CentOS 的 nginx 使用 /etc/nginx/conf.d/*.conf，无 sites-available/enabled
  local NGINX_CONF="/etc/nginx/conf.d/tanxiaer.conf"

  # 让 nginx 可以读取 /opt/tanxiaer 下的静态文件
  chmod -R a+rX "${ROOT_DIR}/website" "${ROOT_DIR}/admin-dist" 2>/dev/null || true

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

  # 把 CentOS 默认的 default_server 监听删掉，避免端口冲突
  if [[ -f /etc/nginx/nginx.conf ]] && grep -l 'default_server' /etc/nginx/nginx.conf &>/dev/null; then
    sed -i 's/default_server//g' /etc/nginx/nginx.conf
  fi

  nginx -t
  systemctl reload nginx 2>/dev/null || systemctl start nginx
  log "Nginx 配置完成"
}

deploy_backend_service() {
  step "部署后端服务（systemd）"
  local JAR_SRC="${ROOT_DIR}/repo/yudao-server/target/yudao-server.jar"
  local JAR_DEST="${ROOT_DIR}/app/yudao-server.jar"
  mkdir -p "${ROOT_DIR}/app" "${ROOT_DIR}/logs"

  systemctl stop tanxiaer 2>/dev/null || true
  cp "${JAR_SRC}" "${JAR_DEST}"

  chown -R "${SERVICE_USER}:${SERVICE_USER}" "${ROOT_DIR}/app" "${ROOT_DIR}/logs"

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

  # 动态获取 java 路径
  local JAVA_BIN
  JAVA_BIN=$(readlink -f "$(command -v java)")

  cat > /etc/systemd/system/tanxiaer.service << UNIT_EOF
[Unit]
Description=摊小二后端服务
After=network.target mysqld.service redis.service

[Service]
Type=simple
User=${SERVICE_USER}
Group=${SERVICE_USER}
WorkingDirectory=${ROOT_DIR}/app
EnvironmentFile=${ENV_UNIT}
ExecStart=${JAVA_BIN} \\
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
  step "配置防火墙（firewalld）"
  systemctl enable firewalld 2>/dev/null || true
  systemctl start firewalld 2>/dev/null || true
  firewall-cmd --permanent --add-service=ssh
  firewall-cmd --permanent --add-service=http
  firewall-cmd --permanent --add-service=https
  firewall-cmd --reload
  log "firewalld 已启用（开放 22/80/443）"
  warn "阿里云 ECS 安全组：请在阿里云控制台另行放行 80/443（firewalld 不管安全组）"
}

# =============================================================================
main() {
  mkdir -p "${ROOT_DIR}"

  if [[ "${SKIP_INSTALL}" == false ]]; then
    install_base
    configure_selinux
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

  PUBLIC_IP=$(curl -s --max-time 5 https://ipinfo.io/ip 2>/dev/null || curl -s --max-time 5 ifconfig.me 2>/dev/null || echo "YOUR_SERVER_IP")
  echo ""
  echo -e "${GREEN}╔════════════════════════════════════════════════════════╗${NC}"
  echo -e "${GREEN}║           🎉  摊小二部署完成！                        ║${NC}"
  echo -e "${GREEN}╠════════════════════════════════════════════════════════╣${NC}"
  echo -e "${GREEN}║  官网首页:   http://${PUBLIC_IP}/"
  echo -e "${GREEN}║  管理后台:   http://${PUBLIC_IP}/admin/"
  echo -e "${GREEN}║  日志目录:   ${ROOT_DIR}/logs/"
  echo -e "${GREEN}║  服务管理:   systemctl {start|stop|restart} tanxiaer"
  echo -e "${GREEN}║  运行用户:   ${SERVICE_USER} (非 root)"
  echo -e "${GREEN}╚════════════════════════════════════════════════════════╝${NC}"
  echo ""
  echo -e "${YELLOW}生产上线前请务必：${NC}"
  echo "  1. 阿里云 ECS 安全组放行 80/443 端口"
  echo "  2. 将 SERVER_NAME 改为你的真实域名"
  echo "  3. 执行 yum install -y certbot python2-certbot-nginx && certbot --nginx 配置 HTTPS"
  echo "  4. 在 .env 中填入真实的火山引擎 / 抖音 API Key"
  echo "  5. 确认 .env 已加入 .gitignore，不会被提交"
}

main "$@"
