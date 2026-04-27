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
# 代码在仓库的 ccb/ 子目录下（不是仓库根）
REPO_SUBDIR="${REPO_SUBDIR:-ccb}"
ROOT_DIR="${ROOT_DIR:-/opt/tanxiaer}"
# 实际项目根（clone 目录 + 子目录），所有构建/SQL/资源路径都基于此
PROJECT_DIR="${ROOT_DIR}/repo${REPO_SUBDIR:+/${REPO_SUBDIR}}"
SERVER_NAME="${SERVER_NAME:-_}"
DB_NAME="${DB_NAME:-ruoyi-vue-pro}"
DB_USER="${DB_USER:-tanxiaer}"
REDIS_PORT="${REDIS_PORT:-6379}"
SERVER_PORT="${SERVER_PORT:-48080}"
SERVICE_USER="${SERVICE_USER:-tanxiaer}"
MAVEN_VERSION="${MAVEN_VERSION:-3.9.15}"
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
SKIP_BACKEND=false
SKIP_FRONTEND=false
for arg in "$@"; do
  case $arg in
    --skip-install)  SKIP_INSTALL=true ;;
    --skip-build)    SKIP_BUILD=true ;;
    --skip-backend)  SKIP_BACKEND=true ;;
    --skip-frontend) SKIP_FRONTEND=true ;;
    --help)
      cat << HELP
用法: sudo bash deploy.sh [选项]

  --skip-install   跳过系统软件安装（已装过时使用）
  --skip-build     跳过前后端所有编译（仅重新部署已有制品）
  --skip-backend   只跳过后端 Maven 构建（沿用已打好的 jar）
  --skip-frontend  只跳过前端 pnpm 构建（沿用已打好的 dist）

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

  # pnpm 9.x+ 要求 Node 18+，Node 16 下必须用 pnpm 8（最后兼容 Node 16 的大版本）
  local PNPM_CUR=""
  PNPM_CUR="$(/opt/nodejs/bin/pnpm --version 2>/dev/null || true)"
  if [[ "${PNPM_CUR}" != 8.* ]]; then
    warn "当前 pnpm=${PNPM_CUR:-未安装}，重新安装 pnpm@8"
    /opt/nodejs/bin/npm install -g pnpm@8 --registry=https://registry.npmmirror.com
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

  if ! rpm -q mysql-community-server &>/dev/null; then
    # CentOS 7 默认有 mariadb-libs，会冲突，先清理
    yum remove -y mariadb-libs 2>/dev/null || true

    # MySQL 官方仓库
    rpm -Uvh https://dev.mysql.com/get/mysql80-community-release-el7-7.noarch.rpm 2>/dev/null || true
    # MySQL GPG key 每隔一年轮换，导入全部已知版本避免 "Public key not installed"
    for k in RPM-GPG-KEY-mysql-2022 RPM-GPG-KEY-mysql-2023 RPM-GPG-KEY-mysql; do
      rpm --import "https://repo.mysql.com/${k}" 2>/dev/null || \
        warn "GPG key ${k} 导入失败，继续"
    done

    if ! yum install -y mysql-community-server; then
      warn "yum 安装失败，尝试跳过 GPG 校验重试"
      yum install -y --nogpgcheck mysql-community-server
    fi
  else
    log "MySQL 已安装，检查服务与密码"
  fi

  systemctl enable mysqld 2>/dev/null || true
  systemctl start mysqld
  # 给 mysqld 一点启动时间
  sleep 3

  # 幂等：如果 root 密码已是 .env 里的值，直接返回
  if mysql -uroot -p"${MYSQL_ROOT_PASS}" -e "SELECT 1" &>/dev/null; then
    log "MySQL root 密码已是预期值，跳过初始化"
    return
  fi

  # 否则从 mysqld.log 取临时密码进行首次重置
  local TEMP_PASS
  TEMP_PASS=$(awk '/temporary password/ {print $NF}' /var/log/mysqld.log | tail -1)
  if [[ -z "${TEMP_PASS}" ]]; then
    err "MySQL 已启动但密码不匹配，且 /var/log/mysqld.log 里找不到临时密码。"
    err "请手动处理：1) mysqladmin -uroot -p'旧密码' password '${MYSQL_ROOT_PASS}'"
    err "           2) 或 systemctl stop mysqld && 用 --skip-grant-tables 重置"
    exit 1
  fi

  info "使用临时密码重置 root"
  # MySQL 8 默认密码策略 MEDIUM 要求大小写+数字+特殊字符≥8位；
  # 先把策略降到 LOW（仅长度≥4）避免弱密码被拒，再设用户密码
  if ! mysql --connect-expired-password -uroot -p"${TEMP_PASS}" << SQL
SET GLOBAL validate_password.policy = LOW;
SET GLOBAL validate_password.length = 4;
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '${MYSQL_ROOT_PASS}';
FLUSH PRIVILEGES;
SQL
  then
    err "MySQL root 密码重置失败。"
    err "请检查 /var/log/mysqld.log，或确认 .env 中 MYSQL_ROOT_PASS 至少 4 位且非空"
    exit 1
  fi

  # 双保险：再校验新密码能用
  if ! mysql -uroot -p"${MYSQL_ROOT_PASS}" -e "SELECT 1" &>/dev/null; then
    err "MySQL root 密码已 ALTER 但验证失败，请人工排查"
    exit 1
  fi
  log "MySQL 8 root 密码已重置为 .env 中的 MYSQL_ROOT_PASS"
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
  # 国内访问 GitHub 常被限速/阻断，允许通过 GITHUB_PROXY 走代理
  # 例：GITHUB_PROXY=https://gh-proxy.com/ 或 https://ghfast.top/
  local CLONE_URL="${REPO_URL}"
  if [[ -n "${GITHUB_PROXY:-}" && "${REPO_URL}" == *github.com* ]]; then
    CLONE_URL="${GITHUB_PROXY%/}/${REPO_URL}"
    info "使用 GitHub 代理：${GITHUB_PROXY%/}/"
  fi

  if [[ -d "${ROOT_DIR}/repo/.git" ]]; then
    info "仓库已存在，执行 git pull"
    cd "${ROOT_DIR}/repo"
    # 如果之前用了不同的 URL（比如换了代理），更新 remote
    git remote set-url origin "${CLONE_URL}" 2>/dev/null || true
    git fetch origin --depth=1
    git checkout "${BRANCH}" 2>/dev/null || git checkout -b "${BRANCH}" "origin/${BRANCH}"
    git reset --hard "origin/${BRANCH}"
  else
    mkdir -p "${ROOT_DIR}/repo"
    git clone --branch "${BRANCH}" --depth=1 "${CLONE_URL}" "${ROOT_DIR}/repo"
  fi
  log "代码已更新到 $(cd "${ROOT_DIR}/repo" && git rev-parse --short HEAD)"
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

  # 幂等性判断：核心表已存在说明跑过 base SQL，只补跑 fix_*.sql
  # fix 脚本都是幂等的（CREATE TABLE IF NOT EXISTS / INFORMATION_SCHEMA 存储过程），可反复执行
  local SQL_DIR="${PROJECT_DIR}/sql/mysql"
  local RUN_BASE=true
  local TABLE_COUNT
  TABLE_COUNT=$(mysql_safe -Nse "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}' AND table_name='system_users';" 2>/dev/null || echo 0)
  if [[ "${TABLE_COUNT}" -gt 0 ]]; then
    warn "检测到核心表 system_users 已存在，跳过 base SQL，只跑 fix_*.sql"
    RUN_BASE=false
  fi

  if [[ "${RUN_BASE}" == true ]]; then
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
  fi

  # 排除清单：这些 fix_*.sql 是破坏性脚本，绝不能自动跑
  # fix_tenant_id.sql 会 DROP 50+ 张业务表，仅供人工定点执行
  local FIX_EXCLUDE=("fix_tenant_id.sql")
  shopt -s nullglob
  local FIX_FILES=("${SQL_DIR}"/fix_*.sql)
  shopt -u nullglob
  IFS=$'\n' FIX_FILES=($(printf '%s\n' "${FIX_FILES[@]}" | sort)); unset IFS
  for f in "${FIX_FILES[@]}"; do
    local bn="$(basename "${f}")"
    local skip=false
    for ex in "${FIX_EXCLUDE[@]}"; do
      [[ "${bn}" == "${ex}" ]] && skip=true && break
    done
    if [[ "${skip}" == true ]]; then
      warn "跳过危险脚本：${bn}（需人工定点执行）"
      continue
    fi
    info "导入 ${bn}..."
    mysql_safe "${DB_NAME}" < "${f}"
    log "${bn} 导入完成"
  done

  log "数据库初始化完成"
}

write_prod_config() {
  step "生成生产环境配置"
  local RESOURCES="${PROJECT_DIR}/yudao-server/src/main/resources"
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
  ensure_swap
  source /etc/profile.d/java.sh
  export PATH="/opt/maven/bin:${PATH}"
  # Maven 自身 JVM 堆：4G 机器给 1.5G 留余量给 OS / mysql / redis
  export MAVEN_OPTS="${MAVEN_OPTS:--Xms256m -Xmx1536m}"

  cd "${PROJECT_DIR}"
  # 完整日志落盘，控制台只保留尾部；build 失败时请查看 /tmp/maven-build.log
  info "完整构建日志：/tmp/maven-build.log"
  /opt/maven/bin/mvn clean package -DskipTests \
    --batch-mode -T 1C 2>&1 | tee /tmp/maven-build.log | tail -80

  log "后端编译完成: $(ls yudao-server/target/yudao-server.jar)"
}

ensure_pnpm8() {
  # --skip-install 模式下 install_node 不跑，这里兜底保证 pnpm 可用
  export PATH="/opt/nodejs/bin:${PATH}"
  if ! command -v node &>/dev/null; then
    err "Node 未安装，请先不带 --skip-install 跑一次完整安装"
    exit 1
  fi
  local PNPM_CUR=""
  PNPM_CUR="$(pnpm --version 2>/dev/null || true)"
  if [[ "${PNPM_CUR}" != 8.* ]]; then
    info "pnpm 当前=${PNPM_CUR:-未安装}，安装 pnpm@8"
    /opt/nodejs/bin/npm install -g pnpm@8 --registry=https://registry.npmmirror.com
    ln -sfn /opt/nodejs/bin/pnpm /usr/local/bin/pnpm 2>/dev/null || true
  fi
  info "使用 pnpm $(pnpm --version)"
}

ensure_swap() {
  # 低内存 ECS 跑 pnpm/mvn 会被 OOM killer 砍，自动加 swap 兜底
  # 目标：mem+swap ≥ 6G（2G 机器 → 4G swap；4G 机器 → 2G swap；6G+ 跳过）
  # 4G + 2G swap 足以串行跑 Maven build (~1.5G) + Node build (~1.5G) + 后端服务 (~1.5G)
  local MEM_MB SWAP_MB TARGET_MB=6144
  MEM_MB=$(free -m | awk '/^Mem:/ {print $2}')
  SWAP_MB=$(free -m | awk '/^Swap:/ {print $2}')
  if (( MEM_MB + SWAP_MB >= TARGET_MB )); then
    info "内存 ${MEM_MB}MB + swap ${SWAP_MB}MB ≥ 6G，无需扩 swap"
    return
  fi
  if [[ -f /swapfile ]]; then
    info "已存在 /swapfile（$(ls -lh /swapfile | awk '{print $5}')），跳过"
    return
  fi
  local NEED_MB=$(( TARGET_MB - MEM_MB - SWAP_MB ))
  info "内存 ${MEM_MB}MB + swap ${SWAP_MB}MB，创建 ${NEED_MB}MB swap 补足到 ${TARGET_MB}MB"
  # 检查磁盘空间
  local FREE_KB
  FREE_KB=$(df -k / | awk 'NR==2 {print $4}')
  if (( FREE_KB < NEED_MB * 1024 + 2 * 1024 * 1024 )); then
    warn "根分区剩余空间不足 ${NEED_MB}MB + 2GB 余量，跳过 swap 创建"
    return
  fi
  fallocate -l "${NEED_MB}M" /swapfile 2>/dev/null || \
    dd if=/dev/zero of=/swapfile bs=1M count="${NEED_MB}" status=none
  chmod 600 /swapfile
  mkswap /swapfile >/dev/null
  swapon /swapfile
  grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  # 降低 swappiness，优先用 RAM
  sysctl -w vm.swappiness=30 >/dev/null
  grep -q 'vm.swappiness' /etc/sysctl.conf || echo 'vm.swappiness=30' >> /etc/sysctl.conf
  log "swap 已启用：$(free -h | awk '/^Swap:/ {print $2}')"
}

build_admin_frontend() {
  step "编译管理后台前端"
  ensure_swap
  ensure_pnpm8
  local UI_DIR="${PROJECT_DIR}/yudao-ui/yudao-ui-admin-vue3"
  cd "${UI_DIR}"

  cat > .env.production << 'ENVEOF'
VITE_DEV = false
VITE_APP_TITLE = 摊小二管理后台
VITE_BASE_URL = /
VITE_API_URL = /admin-api
VITE_APP_CAPTCHA_ENABLE = false
ENVEOF

  # 限制 Node 堆内存 + 降并发，减少 OOM 概率
  export NODE_OPTIONS="--max-old-space-size=1536"
  pnpm install --registry=https://registry.npmmirror.com \
               --network-concurrency=4 --child-concurrency=2
  pnpm build:prod

  local DIST_DIR="${ROOT_DIR}/admin-dist"
  rm -rf "${DIST_DIR}"
  cp -r dist "${DIST_DIR}"
  log "管理后台前端编译完成 → ${DIST_DIR}"
}

build_merchant_h5() {
  step "编译商户/用户端 H5（uni-app）"
  ensure_swap
  ensure_pnpm8
  local UI_DIR="${PROJECT_DIR}/yudao-ui/yudao-ui-merchant-uniapp"
  cd "${UI_DIR}"

  # 把 H5 部署到 /m/ 子路径：
  # 1) router.base = /m/ → 路由前缀对齐
  # 2) publicPath = /m/  → 打包出来的 <script src="/m/assets/...">
  # 备份并 patch；末尾再恢复。上次失败留下的 .bak 在重跑时会先被恢复回去。
  local MF="src/manifest.json"
  if [[ -f "${MF}.deploy.bak" ]]; then
    info "检测到上次构建中断的 manifest 备份，先恢复"
    cp -f "${MF}.deploy.bak" "${MF}"
    rm -f "${MF}.deploy.bak"
  fi
  cp "${MF}" "${MF}.deploy.bak"

  sed -i 's|"base": "/"|"base": "/m/"|' "${MF}"
  # 在 "template": "index.html", 后插入 publicPath 行（如不存在）
  if ! grep -q '"publicPath"' "${MF}"; then
    sed -i '/"template": "index.html"/a\    "publicPath": "/m/",' "${MF}"
  fi

  # 生产 .env：只保留非敏感的 VITE_ 模型/视频/TTS 配置；敏感 KEY 全部走后端 BFF
  cat > .env.production << 'ENVEOF'
VITE_ARK_LLM_MODEL=doubao-1-5-pro-32k-250115
VITE_ARK_VISION_MODEL=doubao-1-5-vision-pro-32k-250115
VITE_ARK_VIDEO_MODEL=doubao-seedance-1-5-pro-251215
VITE_VIDEO_RESOLUTION=1080p
VITE_VIDEO_DURATION=10
VITE_VIDEO_RATIO=9:16
VITE_VIDEO_SCENES=3
VITE_TTS_PROVIDER=volc
VITE_TTS_VOICE=zh_male_beijingxiaoye_emo_v2_mars_bigtts
ENVEOF

  export NODE_OPTIONS="--max-old-space-size=1536"
  pnpm install --registry=https://registry.npmmirror.com \
               --network-concurrency=4 --child-concurrency=2
  pnpm build:h5

  # 恢复 manifest.json 原状，避免污染源码（git status 干净）
  mv -f "${MF}.deploy.bak" "${MF}"

  # uniapp 默认输出 dist/build/h5/，确认存在
  if [[ ! -f dist/build/h5/index.html ]]; then
    err "商户端 H5 构建产物缺失：${UI_DIR}/dist/build/h5/index.html"
    exit 1
  fi
  log "商户端 H5 编译完成 → ${UI_DIR}/dist/build/h5"
}

deploy_merchant_h5() {
  step "部署商户/用户端 H5（/m/）"
  local SRC="${PROJECT_DIR}/yudao-ui/yudao-ui-merchant-uniapp/dist/build/h5"
  local DST="${ROOT_DIR}/m"
  if [[ ! -f "${SRC}/index.html" ]]; then
    err "商户端 H5 产物不存在：${SRC}/index.html"
    err "解决方式：去掉 --skip-frontend 重跑，或本地 pnpm build:h5 后 scp 到 ${SRC}/"
    exit 1
  fi
  rm -rf "${DST}"
  mkdir -p "${DST}"
  cp -r "${SRC}/." "${DST}/"
  chmod -R a+rX "${DST}"
  log "商户/用户端 H5 部署完成 → ${DST}"
}

deploy_website() {
  step "部署官网静态文件"
  local WEB_DIR="${ROOT_DIR}/website"
  mkdir -p "${WEB_DIR}"
  cp "${PROJECT_DIR}/docs/website/index.html" "${WEB_DIR}/index.html"
  [[ -d "${PROJECT_DIR}/docs/website/assets" ]] && \
    cp -r "${PROJECT_DIR}/docs/website/assets" "${WEB_DIR}/"
  log "官网部署完成 → ${WEB_DIR}"
}

configure_nginx() {
  step "配置 Nginx"
  # CentOS 的 nginx 使用 /etc/nginx/conf.d/*.conf，无 sites-available/enabled
  local NGINX_CONF="/etc/nginx/conf.d/tanxiaer.conf"

  # 让 nginx 可以读取 /opt/tanxiaer 下的静态文件
  chmod -R a+rX "${ROOT_DIR}/website" "${ROOT_DIR}/admin-dist" "${ROOT_DIR}/m" 2>/dev/null || true

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

    # 官网（静态单页 + 任何后续 assets/图片 自动生效）
    location / {
        root ${ROOT_DIR}/website;
        index index.html;
        try_files \$uri \$uri/ /index.html;
    }
    location ~* \.(png|jpg|jpeg|gif|svg|webp|ico|css|js|woff2?)\$ {
        root ${ROOT_DIR}/website;
        expires 7d;
        add_header Cache-Control "public, immutable";
        access_log off;
    }

    # 管理后台
    location /admin/ {
        alias ${ROOT_DIR}/admin-dist/;
        index index.html;
        try_files \$uri \$uri/ /admin/index.html;
    }

    # 商户/用户端 H5（uni-app 输出，hash 路由）
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
  local JAR_SRC="${PROJECT_DIR}/yudao-server/target/yudao-server.jar"
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
  -Xms512m -Xmx1536m \\
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

  if [[ "${SKIP_BUILD}" == true ]]; then
    warn "跳过所有编译打包（--skip-build）"
  else
    if [[ "${SKIP_BACKEND}" == true ]]; then
      warn "跳过后端 Maven 构建（--skip-backend）"
    else
      build_backend
    fi
    if [[ "${SKIP_FRONTEND}" == true ]]; then
      warn "跳过前端 pnpm 构建（--skip-frontend）"
      # ── 管理后台 dist 兜底 ──
      local UI_DIR="${PROJECT_DIR}/yudao-ui/yudao-ui-admin-vue3"
      local DIST_DIR="${ROOT_DIR}/admin-dist"
      if [[ -f "${UI_DIR}/dist/index.html" ]]; then
        info "发现 ${UI_DIR}/dist，同步到 ${DIST_DIR}"
        rm -rf "${DIST_DIR}" && cp -r "${UI_DIR}/dist" "${DIST_DIR}"
      elif [[ -f "${DIST_DIR}/index.html" ]]; then
        info "沿用已有 ${DIST_DIR}"
      else
        err "未找到管理后台前端产物：既不在 ${UI_DIR}/dist，也不在 ${DIST_DIR}"
        err "解决方式：本地打包后 scp dist/ 到 ${DIST_DIR}/，再跑 --skip-frontend"
        exit 1
      fi
      # ── 商户/用户端 H5 dist 兜底 ──
      local M_SRC="${PROJECT_DIR}/yudao-ui/yudao-ui-merchant-uniapp/dist/build/h5"
      local M_DST="${ROOT_DIR}/m"
      if [[ -f "${M_SRC}/index.html" ]]; then
        info "发现 ${M_SRC}，同步到 ${M_DST}"
        rm -rf "${M_DST}" && mkdir -p "${M_DST}" && cp -r "${M_SRC}/." "${M_DST}/"
      elif [[ -f "${M_DST}/index.html" ]]; then
        info "沿用已有 ${M_DST}"
      else
        err "未找到商户端 H5 产物：既不在 ${M_SRC}，也不在 ${M_DST}"
        err "解决方式：本地 pnpm build:h5 后 scp dist/build/h5/ 到 ${M_SRC}/，再跑 --skip-frontend"
        exit 1
      fi
    else
      build_admin_frontend
      build_merchant_h5
    fi
  fi

  deploy_website
  if [[ "${SKIP_FRONTEND}" != true ]]; then
    deploy_merchant_h5
  fi
  configure_nginx
  deploy_backend_service
  setup_firewall

  PUBLIC_IP=$(curl -s --max-time 5 https://ipinfo.io/ip 2>/dev/null || curl -s --max-time 5 ifconfig.me 2>/dev/null || echo "YOUR_SERVER_IP")
  echo ""
  echo -e "${GREEN}╔════════════════════════════════════════════════════════╗${NC}"
  echo -e "${GREEN}║           🎉  摊小二部署完成！                        ║${NC}"
  echo -e "${GREEN}╠════════════════════════════════════════════════════════╣${NC}"
  echo -e "${GREEN}║  官网首页:    http://${PUBLIC_IP}/"
  echo -e "${GREEN}║  商户/用户端: http://${PUBLIC_IP}/m/"
  echo -e "${GREEN}║  管理后台:    http://${PUBLIC_IP}/admin/"
  echo -e "${GREEN}║  日志目录:    ${ROOT_DIR}/logs/"
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
