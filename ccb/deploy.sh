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
SERVER_NAME="${SERVER_NAME:-www.doupaidoudian.com}"
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
ARK_API_KEY="${ARK_API_KEY:-}"
JIMENG_AK="${JIMENG_AK:-}"
JIMENG_SK="${JIMENG_SK:-}"
DOUYIN_CLIENT_KEY="${DOUYIN_CLIENT_KEY:-}"
DOUYIN_CLIENT_SECRET="${DOUYIN_CLIENT_SECRET:-}"
# 通联收付通（H5 收银台 + 商户进件）
ALLINPAY_ENABLED="${ALLINPAY_ENABLED:-true}"
ALLINPAY_API_BASE_URL="${ALLINPAY_API_BASE_URL:-https://test-vsp.allinpay.com}"
ALLINPAY_APPID="${ALLINPAY_APPID:-}"
ALLINPAY_MERCHANT_NO="${ALLINPAY_MERCHANT_NO:-}"
ALLINPAY_MD5_KEY="${ALLINPAY_MD5_KEY:-}"
ALLINPAY_RSA_PRIVATE_KEY="${ALLINPAY_RSA_PRIVATE_KEY:-}"
ALLINPAY_RSA_PUBLIC_KEY="${ALLINPAY_RSA_PUBLIC_KEY:-}"
ALLINPAY_SIGN_TYPE="${ALLINPAY_SIGN_TYPE:-RSA}"
ALLINPAY_SM2_PRIVATE_KEY="${ALLINPAY_SM2_PRIVATE_KEY:-}"
ALLINPAY_SM2_PUBLIC_KEY="${ALLINPAY_SM2_PUBLIC_KEY:-}"
ALLINPAY_ORG_ID="${ALLINPAY_ORG_ID:-}"
ALLINPAY_USE_ONEPAY="${ALLINPAY_USE_ONEPAY:-false}"
ALLINPAY_DIAG_TOKEN="${ALLINPAY_DIAG_TOKEN:-}"
SERVER_NAME="${SERVER_NAME:-www.doupaidoudian.com}"
LE_EMAIL="${LE_EMAIL:-admin@${SERVER_NAME}}"
MERCHANT_PACKAGE_PAY_APP_KEY="${MERCHANT_PACKAGE_PAY_APP_KEY:-tanxiaer-package}"
MERCHANT_PACKAGE_PAY_APP_ID="${MERCHANT_PACKAGE_PAY_APP_ID:-10001}"
# 火山 TOS（对象存储）— sidecar 视频上传用；缺时默认沿用 JIMENG_AK/SK（同一套火山账号 IAM 通用）
TOS_AK="${TOS_AK:-}"
TOS_SK="${TOS_SK:-}"
TOS_BUCKET="${TOS_BUCKET:-tanxiaoer}"
TOS_REGION="${TOS_REGION:-cn-beijing}"

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
RESET_DATA=false
ASSUME_YES=false
for arg in "$@"; do
  case $arg in
    --skip-install)  SKIP_INSTALL=true ;;
    --skip-build)    SKIP_BUILD=true ;;
    --skip-backend)  SKIP_BACKEND=true ;;
    --skip-frontend) SKIP_FRONTEND=true ;;
    --reset)         RESET_DATA=true ;;
    --yes|-y)        ASSUME_YES=true ;;
    --help)
      cat << HELP
用法: sudo bash deploy.sh [选项]

  --skip-install   跳过系统软件安装（已装过时使用）
  --skip-build     跳过前后端所有编译（仅重新部署已有制品）
  --skip-backend   只跳过后端 Maven 构建（沿用已打好的 jar）
  --skip-frontend  只跳过前端 pnpm 构建（沿用已打好的 dist）
  --reset          ⚠ 危险：DROP DATABASE + Redis FLUSHALL 重头来过
                   （会删掉所有商户 / 用户 / 视频任务，仅留 OSS 上的对象）
  --yes / -y       --reset 时跳过交互确认（脚本里自动化用）

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
  step "安装基础依赖 + EPEL + 时区 + ffmpeg + 中文字体"
  yum install -y epel-release
  yum install -y \
    curl wget git unzip zip which \
    gcc gcc-c++ make \
    ca-certificates openssl \
    net-tools firewalld \
    yum-utils policycoreutils-python
  timedatectl set-timezone Asia/Shanghai || true

  # CentOS 7 glibc 2.17 不支持 ffmpeg-static 5.x（要 GLIBC_2.18+），sidecar 视频
  # 合成必须用系统 ffmpeg。这里在最早期就装好，整个部署期间所有视频/字幕逻辑都
  # 能用 /usr/bin/ffmpeg。
  if ! command -v ffmpeg &>/dev/null; then
    info "安装 ffmpeg（CentOS 7 视频合成必需，走 RPM Fusion 仓库）"
    local RH_VER
    RH_VER="$(rpm -E %rhel 2>/dev/null || echo 7)"
    yum install -y "https://download1.rpmfusion.org/free/el/rpmfusion-free-release-${RH_VER}.noarch.rpm" >/dev/null 2>&1 \
      || warn "RPM Fusion 仓库添加失败（网络问题？）"
    if yum install -y ffmpeg ffmpeg-devel >/dev/null 2>&1; then
      log "系统 ffmpeg 安装成功 → $(ffmpeg -version 2>/dev/null | head -1 | head -c 80)"
    else
      warn "ffmpeg 安装失败 — sidecar 视频合成功能将不可用；解决方案："
      warn "  yum install -y https://download1.rpmfusion.org/free/el/rpmfusion-free-release-${RH_VER}.noarch.rpm"
      warn "  yum install -y ffmpeg ffmpeg-devel"
    fi
  else
    log "ffmpeg 已存在 → $(ffmpeg -version 2>/dev/null | head -1 | head -c 80)"
  fi

  # 中文字体（sidecar 端卡 / 字幕烧录用）
  if [[ ! -f /usr/share/fonts/wqy-zenhei/wqy-zenhei.ttc ]]; then
    yum install -y wqy-zenhei-fonts >/dev/null 2>&1 \
      && log "中文字体 wqy-zenhei 安装成功" \
      || warn "中文字体安装失败（视频字幕无法烧录中文，仅是降级，不阻塞）"
  fi

  log "基础依赖安装完成"
}

configure_selinux() {
  step "配置 SELinux（标签法 — 即使 Enforcing 也能让 nginx 读 /opt/tanxiaer）"
  if ! command -v getenforce &>/dev/null; then
    log "未装 SELinux 工具链，跳过"
    return 0
  fi

  # 双保险方案：
  # 1) 给 /opt/tanxiaer 打 httpd_sys_content_t 标签 — 永久 + Enforcing 下也读
  # 2) 临时 setenforce 0 — 当前会话立即生效
  # 3) /etc/selinux/config 改 permissive — 下次重启永久（即使没装 semanage 也兜底）
  yum install -y policycoreutils-python >/dev/null 2>&1 || warn "policycoreutils-python 装失败"
  if command -v semanage &>/dev/null; then
    info "给 ${ROOT_DIR} 打 SELinux 标签 httpd_sys_content_t..."
    if semanage fcontext -l 2>/dev/null | grep -q "^${ROOT_DIR}(/\.\*)?"; then
      semanage fcontext -m -t httpd_sys_content_t "${ROOT_DIR}(/.*)?" 2>/dev/null \
        || warn "fcontext -m 失败"
    else
      semanage fcontext -a -t httpd_sys_content_t "${ROOT_DIR}(/.*)?" 2>/dev/null \
        || warn "fcontext -a 失败（可能已存在，下一步 restorecon 仍会生效）"
    fi
  else
    warn "semanage 未就位，仅靠 setenforce 0 兜底（重启后 nginx 可能 404）"
  fi

  # 关键：restorecon 应用标签到磁盘上所有文件（这一步必须每次 deploy 跑，因为
  # 新 cp 进来的文件标签默认是 user_home_t，没标签 nginx 读不到）
  if [[ -d "${ROOT_DIR}" ]] && command -v restorecon &>/dev/null; then
    restorecon -R "${ROOT_DIR}" 2>/dev/null && log "SELinux 标签已应用到 ${ROOT_DIR}/" \
      || warn "restorecon 失败"
  fi

  # 临时 + 永久兜底（即使标签法也没生效，至少 setenforce 0 让当前会话能用）
  if [[ "$(getenforce)" == "Enforcing" ]]; then
    setenforce 0 2>/dev/null || true
    sed -i 's/^SELINUX=.*/SELINUX=permissive/' /etc/selinux/config 2>/dev/null || true
    log "SELinux 临时切为 permissive（标签法已配，即使重启回 Enforcing 也能用）"
  else
    log "SELinux 状态: $(getenforce)"
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

reset_data() {
  step "⚠ 重置数据：DROP DATABASE + Redis FLUSHALL"
  init_mysql_defaults_file

  # 1. 交互确认（除非 --yes）
  if [[ "${ASSUME_YES}" != true ]]; then
    echo -e "${RED}危险操作！将永久删除：${NC}"
    echo "  - MySQL 数据库 ${DB_NAME}（所有租户/商户/订单/视频任务）"
    echo "  - Redis 全部 key（OAuth token / 验证码 / 缓存）"
    echo "  - OSS 上已上传的文件 不会删，需自行去阿里云控制台清"
    read -r -p "确认重置？输入 YES 继续，其它任意键取消：" confirm
    if [[ "${confirm}" != "YES" ]]; then
      err "已取消重置"
      exit 1
    fi
  fi

  # 2. 停服务避免重置期间被写
  info "停 tanxiaer / tanxiaer-sidecar 服务（避免写入正在重置的库）"
  systemctl stop tanxiaer 2>/dev/null || true
  systemctl stop tanxiaer-sidecar 2>/dev/null || true

  # 3. DROP + 重建库（保留用户授权）
  if systemctl is-active --quiet mysqld 2>/dev/null; then
    info "DROP DATABASE ${DB_NAME} → 重建空库"
    mysql_safe << SQL
DROP DATABASE IF EXISTS \`${DB_NAME}\`;
CREATE DATABASE \`${DB_NAME}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON \`${DB_NAME}\`.* TO '${DB_USER}'@'localhost';
FLUSH PRIVILEGES;
SQL
    log "MySQL 库 ${DB_NAME} 已重置"
  else
    warn "mysqld 未运行，跳过 DROP DATABASE（后续 setup_database 会重建）"
  fi

  # 4. 清 Redis（OAuth token、验证码、tenant 缓存全清）
  if systemctl is-active --quiet redis 2>/dev/null; then
    info "Redis FLUSHALL（清所有 db）"
    if [[ -n "${REDIS_PASS:-}" ]]; then
      redis-cli -p "${REDIS_PORT}" -a "${REDIS_PASS}" --no-auth-warning FLUSHALL >/dev/null \
        && log "Redis 已清空" \
        || warn "redis-cli FLUSHALL 失败（密码错？端口错？继续）"
    else
      redis-cli -p "${REDIS_PORT}" FLUSHALL >/dev/null \
        && log "Redis 已清空" \
        || warn "redis-cli FLUSHALL 失败（继续）"
    fi
  else
    warn "redis 未运行，跳过 FLUSHALL"
  fi

  # 5. 清 sidecar 临时文件（视频中间产物 / OSS 上传缓存）
  if [[ -d "${ROOT_DIR}/sidecar/tmp" ]]; then
    info "清 sidecar 临时目录 ${ROOT_DIR}/sidecar/tmp"
    rm -rf "${ROOT_DIR}/sidecar/tmp"/* 2>/dev/null || true
  fi

  log "数据重置完成 — 后续步骤会重新建表 + seed 演示数据"
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
    # 注意：列表里的 .sql 都是无 fix_ 前缀的"基础 / 迁移"脚本；
    # merchant_invite_code.sql / ai_video_package*.sql 必须放在依赖它们的 fix_ 脚本之前。
    local SQL_FILES=(
      "ruoyi-vue-pro.sql"
      "mall.sql"
      "mp.sql"
      "member_pay.sql"
      "merchant.sql"
      "merchant_invite_code.sql"   # phase 0.2 迁移：建 merchant_invite_code 表 + 给 merchant_info 加 open_id/union_id/invite_code_id
      "video.sql"
      "ai_video_package.sql"        # AI 视频套餐表
      "ai_video_package_menu.sql"   # AI 视频套餐菜单 seed
      "v2_business_tables.sql"
      "marketing.sql"
    )
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

  # 迁移文件：V001__/V002__/... 风格按版本号字典序执行（替代旧 fix_*.sql）
  # 每个 V 文件都必须幂等（IF NOT EXISTS / 存储过程检查），重复跑不报错。
  # 命名约定 docs/database-migrations.md 有详细说明。
  # _DANGER__*.sql 是破坏性脚本（如 _DANGER__tenant_reset.sql 会 DROP 50+ 张表），永不自动跑。
  shopt -s nullglob
  local V_FILES=("${SQL_DIR}"/V*.sql)
  shopt -u nullglob
  # 防御 set -u + bash 4.x 经典坑：empty array 用 ${arr[@]} 报 unbound variable
  if (( ${#V_FILES[@]} > 0 )); then
    IFS=$'\n' V_FILES=($(printf '%s\n' "${V_FILES[@]}" | sort)); unset IFS
    for f in "${V_FILES[@]}"; do
      local bn
      bn="$(basename "${f}")"
      info "导入 ${bn}..."
      mysql_safe "${DB_NAME}" < "${f}"
      log "${bn} 导入完成"
    done
  else
    warn "未找到任何 V*.sql 迁移文件 — 仅 base SQL 已导入；如需新增字段请补 V0XX__*.sql"
  fi

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
  # SMS 验证码：
  #   demo-mode=true 时全场景固定码（生产前请改为 false 并接入真短信网关）
  #   demo-code 仅 demo-mode=true 生效
  sms-code:
    demo-mode: ${YUDAO_SMS_DEMO_MODE:-true}
    demo-code: ${YUDAO_SMS_DEMO_CODE:-888888}
    expire-times: 600
    send-frequency: 60
    send-maximum-quantity-per-day: 10
    begin-code: 1000
    end-code: 9999
  pay:
    order-notify-url: https://${SERVER_NAME:-www.doupaidoudian.com}/admin-api/pay/notify/order
    refund-notify-url: https://${SERVER_NAME:-www.doupaidoudian.com}/admin-api/pay/notify/refund
    transfer-notify-url: https://${SERVER_NAME:-www.doupaidoudian.com}/admin-api/pay/notify/transfer
  merchant:
    package:
      # V021 seed 的 pay_app（id=10001, app_key=tanxiaer-package）
      # 修改后 deploy.sh 重启即生效
      pay-app-key: \${MERCHANT_PACKAGE_PAY_APP_KEY:tanxiaer-package}
      pay-app-id: \${MERCHANT_PACKAGE_PAY_APP_ID:10001}

# 通联收付通配置（V008 商户进件 + 套餐 H5 收银台桥接）
merchant:
  allinpay:
    enabled: \${ALLINPAY_ENABLED:true}
    api-base-url: \${ALLINPAY_API_BASE_URL:https://test-vsp.allinpay.com}
    org-id: \${ALLINPAY_ORG_ID:}
    # 收银台 H5 网关参数（appid 是通联给的 8 位数字）
    appid: \${ALLINPAY_APPID:}
    merchant-no: \${ALLINPAY_MERCHANT_NO:}
    md5-key: \${ALLINPAY_MD5_KEY:}
    # 签名类型：RSA / SM2，必须跟通联控制台一致
    sign-type: \${ALLINPAY_SIGN_TYPE:RSA}
    # RSA 私钥（PKCS8 PEM base64 内容，单行）— sign-type=RSA 时使用
    platform-rsa-private-key: \${ALLINPAY_RSA_PRIVATE_KEY:}
    allinpay-rsa-public-key: \${ALLINPAY_RSA_PUBLIC_KEY:}
    # SM2 私钥 / 公钥 — sign-type=SM2 时使用（通联收银宝商户号配 SM2 时必填）
    sm2-private-key: \${ALLINPAY_SM2_PRIVATE_KEY:}
    sm2-public-key: \${ALLINPAY_SM2_PUBLIC_KEY:}
    # 是否启用聚合收银台 onepay（true=用户主选 微信/支付宝/云闪付/快捷；
    # false=按浏览器 UA 推单一通道）。切 true 前需在通联控制台开通 onepay 产品权限
    use-onepay: \${ALLINPAY_USE_ONEPAY:false}
    # 诊断端点 token：保护 /diag + /diag-unionorder 不被公网滥用；空则禁用
    diag-token: \${ALLINPAY_DIAG_TOKEN:}
    # 回调 URL：必须公网可达
    register-notify-url: https://${SERVER_NAME:-www.doupaidoudian.com}/admin-api/merchant/allinpay/register-notify
    pay-notify-url: https://${SERVER_NAME:-www.doupaidoudian.com}/admin-api/merchant/allinpay/pay-notify
    h5-cashier-return-url: https://${SERVER_NAME:-www.doupaidoudian.com}/m/#/pages/index/index?paid=1
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
  # 临时停掉后端，把 1.5G+ 的 Java 堆让给 vite，避免 4G 机器 OOM
  systemctl stop tanxiaer 2>/dev/null || true
  local UI_DIR="${PROJECT_DIR}/yudao-ui/yudao-ui-admin-vue3"
  cd "${UI_DIR}"

  # vite --mode prod 加载 .env.prod，再加载 .env.prod.local 覆盖（约定俗成）。
  # 我们用 .env.prod.local 注入部署专属覆写（相对 API 路径 / 关闭验证码），
  # 不破坏仓库内 .env.prod 默认值；且 .env.prod.local 默认在 .gitignore 里，安全。
  cat > .env.prod.local << 'ENVEOF'
VITE_DEV=false
VITE_APP_TITLE=摊小二管理后台
# 关键：VITE_BASE_URL 必须留空，axios 真正用的是 base_url = VITE_BASE_URL + VITE_API_URL
# 写 "/" 会拼成 "//admin-api"，浏览器解析成 protocol-relative URL `http://admin-api/...`
# → 整个登录全部 404。留空则拼成 "/admin-api"（相对当前域名），nginx 反代就对了。
VITE_BASE_URL=
VITE_API_URL=/admin-api
VITE_APP_CAPTCHA_ENABLE=false
VITE_OUT_DIR=dist-prod
# nginx 把 admin-vue3 部署到 /admin/ 子路径，必须设 base 让产物里的资源 URL
# 是 /admin/assets/... 而不是 /assets/...，否则浏览器加载 JS 时 nginx 会
# 因找不到 /assets/* 走 fallback 返 index.html，触发 MIME 错（HTML 当 JS）
VITE_BASE_PATH=/admin/
ENVEOF

  # 4G 机器：swap 已扩到 6G。Node 堆 3G 足够；
  # package.json 的 build:prod 写死 1536MB → 不调它，直接 node 起 vite。
  pnpm install --registry=https://registry.npmmirror.com \
               --network-concurrency=4 --child-concurrency=2
  info "vite build (Node heap=3072MB, mode=prod)..."
  node --max_old_space_size=3072 ./node_modules/vite/bin/vite.js build --mode prod

  local DIST_DIR="${ROOT_DIR}/admin-dist"
  # vite --mode prod 输出目录由 .env.prod 的 VITE_OUT_DIR 决定（dist-prod）；
  # 写死 dist 会触发 "cp: cannot stat 'dist'" — 优先 dist-prod，回退 dist 兼容老配置。
  local SRC_DIST=""
  if [[ -d dist-prod ]]; then SRC_DIST="dist-prod";
  elif [[ -d dist ]]; then SRC_DIST="dist";
  else err "vite build 未产出 dist-prod/ 或 dist/"; exit 1; fi
  rm -rf "${DIST_DIR}"
  cp -r "${SRC_DIST}" "${DIST_DIR}"
  log "管理后台前端编译完成 → ${DIST_DIR}（源：${SRC_DIST}）"
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

  # 4G 机器 Node 堆给 2.5G（H5 比 admin 略小，留点给 esbuild）
  export NODE_OPTIONS="--max-old-space-size=2560"
  # ffmpeg-static / aws-sdk / msedge-tts 只是历史 vite-plugin sidecar 的依赖，
  # build:h5 实际产物不打包它们（生产 sidecar 已抽到 server/sidecar/ 独立服务）。
  # 国内 ECS 拉 GitHub release（ffmpeg 二进制）会 30s timeout，pnpm 报 ELIFECYCLE。
  # --ignore-scripts 跳过 postinstall 下载 ffmpeg 二进制，build:h5 不受影响。
  # 同时给 ffmpeg-static 配 GITHUB_PROXY 镜像，万一被某条路径 require 也有兜底。
  if [[ -n "${GITHUB_PROXY:-}" ]]; then
    export FFMPEG_BINARIES_URL="${GITHUB_PROXY%/}/https://github.com/eugeneware/ffmpeg-static/releases/download/b6.0"
  fi
  pnpm install --ignore-scripts \
               --registry=https://registry.npmmirror.com \
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
  local MAIN_CONF="/etc/nginx/nginx.conf"

  # 让 nginx 可以读取 /opt/tanxiaer 下的静态文件
  chmod -R a+rX "${ROOT_DIR}/website" "${ROOT_DIR}/admin-dist" "${ROOT_DIR}/m" 2>/dev/null || true

  # SELinux 关键：每次 deploy 都重新打标签 — H5/admin-dist 是 cp 出来的新文件，
  # 没继承父目录的 httpd_sys_content_t 标签 → nginx (httpd_t) 读不到 → 404
  # configure_selinux 阶段已 fcontext -a 加规则，这里只需 restorecon 应用
  if command -v restorecon &>/dev/null; then
    restorecon -R "${ROOT_DIR}" 2>/dev/null \
      && log "SELinux 标签已重新应用 (deploy 后 cp 出来的新文件需要)" \
      || warn "restorecon 失败"
  fi

  # ── 清理两处 nginx 默认 server，防止 default_server 冲突 ─────────────────
  # 1) /etc/nginx/conf.d/default.conf — 通常是 CentOS 包自带
  if [[ -f /etc/nginx/conf.d/default.conf ]]; then
    info "禁用 conf.d/default.conf → default.conf.disabled"
    mv -f /etc/nginx/conf.d/default.conf /etc/nginx/conf.d/default.conf.disabled
  fi
  # 2) /etc/nginx/nginx.conf — 默认有 server { listen 80 default_server; ... }
  #    会和 tanxiaer.conf 的 default_server 冲突，nginx 提示 "conflicting server
  #    name '_' on 0.0.0.0:80, ignored"，结果我们的 location /m/ 不生效，
  #    /m/assets/*.js 落到默认 root /usr/share/nginx/html → 404。
  #    用 awk 配大括号深度计数，把第一个 server { ... } 块整段注释掉。
  if grep -qE "^[[:space:]]*server[[:space:]]*\{" "${MAIN_CONF}" 2>/dev/null; then
    info "禁用 nginx.conf 里的默认 server 块（避免与 tanxiaer.conf 冲突）"
    # 关键修复：每次都用"当前 nginx.conf"作为 awk 输入（不复用旧 .bak）
    # 之前 bug：旧 .bak 是上次清理前的版本，yum 升级回滚 / 手工 mv 后 .bak 也含
    # server 块，第二次 deploy 用 .bak 覆盖等于没清。
    local TMP_NGINX
    TMP_NGINX="$(mktemp)"
    cp "${MAIN_CONF}" "${TMP_NGINX}"
    # 时间戳备份，永远是清理前的当前版本，不覆盖历史
    cp "${MAIN_CONF}" "${MAIN_CONF}.tanxiaer-bak.$(date +%Y%m%d-%H%M%S)"
    awk '
      BEGIN { d=0; in_s=0 }
      /^[[:space:]]*server[[:space:]]*\{/ {
        if (in_s==0) {
          in_s=1; d=1
          print "    # [tanxiaer-deploy] 默认 server 已禁用，路由由 conf.d/tanxiaer.conf 接管"
          next
        }
      }
      in_s==1 {
        n=gsub(/\{/,"{"); m=gsub(/\}/,"}")
        d += n - m
        if (d <= 0) { in_s=0 }
        next
      }
      { print }
    ' "${TMP_NGINX}" > "${MAIN_CONF}"
    rm -f "${TMP_NGINX}"
    # 验证清干净
    if grep -qE "^[[:space:]]*server[[:space:]]*\{" "${MAIN_CONF}" 2>/dev/null; then
      err "nginx.conf 仍含 server 块 — awk 清理失败"
      grep -nE "^[[:space:]]*server[[:space:]]*\{" "${MAIN_CONF}" >&2
      exit 1
    fi
    log "nginx.conf 默认 server 块已清理（备份 → ${MAIN_CONF}.tanxiaer-bak.<时间戳>）"
  else
    log "nginx.conf 里无默认 server 块（OK）"
  fi

  # ─── HTTPS 证书路径（首次部署用 Let's Encrypt 自动签）───
  local CERT_DIR="/etc/letsencrypt/live/${SERVER_NAME}"
  local CERT_FILE="${CERT_DIR}/fullchain.pem"
  local KEY_FILE="${CERT_DIR}/privkey.pem"
  local USE_HTTPS=false
  if [[ -f "${CERT_FILE}" && -f "${KEY_FILE}" ]]; then
    USE_HTTPS=true
    info "检测到 HTTPS 证书 → 启用 443 + 80→443 跳转"
  else
    warn "未检测到证书 ${CERT_FILE} → 先以 HTTP 启动；setup_https 阶段再签发"
  fi
  mkdir -p /var/www/letsencrypt 2>/dev/null || true

  # 写 80 端口块：ACME challenge 一直需要走 80
  cat > "${NGINX_CONF}" << NGINX_EOF
upstream tanxiaer_backend {
    server 127.0.0.1:${SERVER_PORT};
    keepalive 32;
}

# ── 80 端口：ACME 验证 + （证书就绪后）跳 443 ──
server {
    listen 80 default_server;
    server_name ${SERVER_NAME} _;
    charset utf-8;
    client_max_body_size 50m;

    # certbot --webroot ACME challenge 必走 80 端口；任何时候保持可达
    location /.well-known/acme-challenge/ {
        root /var/www/letsencrypt;
        default_type "text/plain";
    }
NGINX_EOF

  if [[ "${USE_HTTPS}" == "true" ]]; then
    # 证书就绪：80 块只剩 ACME + 跳转；HTTPS 块承担所有 location
    cat >> "${NGINX_CONF}" << NGINX_EOF
    location / { return 301 https://\$host\$request_uri; }
}

server {
    listen 443 ssl http2 default_server;
    server_name ${SERVER_NAME} _;
    charset utf-8;
    client_max_body_size 50m;

    ssl_certificate     ${CERT_FILE};
    ssl_certificate_key ${KEY_FILE};
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 1d;
    add_header Strict-Transport-Security "max-age=31536000" always;
NGINX_EOF
  fi
  # 不论 USE_HTTPS：location 块写入当前激活的 server（USE_HTTPS=false 写 80；true 写 443）
  cat >> "${NGINX_CONF}" << NGINX_EOF

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
    # ^~ 关键：让前缀匹配优先于上面 .js/.css 正则 location（否则 /admin/assets/*.js
    # 会落到官网 root /opt/tanxiaer/website 找不到 → 404）
    location ^~ /admin/ {
        alias ${ROOT_DIR}/admin-dist/;
        index index.html;
        try_files \$uri \$uri/ /admin/index.html;
    }
    location ^~ /admin/assets/ {
        alias ${ROOT_DIR}/admin-dist/assets/;
        expires 30d;
        add_header Cache-Control "public, immutable";
        access_log off;
    }

    # 商户/用户端 H5（uni-app 输出，hash 路由）
    # /m/assets/*.js 是 hash 文件名，可强缓存 30d；/m/index.html 必须 no-cache
    # 否则升级 dist 后浏览器拿旧 index.html → 引用新 chunk 时 404（因为旧 chunk 已被 rm -rf 删）
    location = /m/index.html {
        alias ${ROOT_DIR}/m/index.html;
        add_header Cache-Control "no-cache, no-store, must-revalidate";
        add_header Pragma "no-cache";
        add_header Expires 0;
    }
    location ^~ /m/assets/ {
        alias ${ROOT_DIR}/m/assets/;
        expires 30d;
        add_header Cache-Control "public, immutable";
        access_log off;
    }
    location ^~ /m/ {
        alias ${ROOT_DIR}/m/;
        index index.html;
        try_files \$uri \$uri/ /m/index.html;
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

    # ── Sidecar Node 服务（AI 视频后处理 / TOS / TTS / 抖音发布） ──
    # 这一组路径 dev 时由 vite plugin 接管；prod 反代到 systemd 守护的 sidecar
    location /oss/ {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host \$host;
        client_max_body_size 100m;
        proxy_connect_timeout 30s;
        proxy_read_timeout 600s;
    }
    location /tts/ {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host \$host;
        proxy_buffering off;
        proxy_read_timeout 120s;
    }
    location /video/ {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host \$host;
        client_max_body_size 200m;
        # ffmpeg 合成视频可能要几分钟
        proxy_read_timeout 900s;
        proxy_send_timeout 900s;
    }
    location /vproxy {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host \$host;
        proxy_read_timeout 300s;
    }
    location /jimeng {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host \$host;
    }
    location /douyin/ {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host \$host;
        client_max_body_size 200m;
        proxy_read_timeout 600s;
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

# Let's Encrypt 自动签发 + 续期（CentOS 7：epel + certbot + nginx 插件）
setup_https() {
  step "配置 HTTPS（Let's Encrypt 免费证书）"
  if [[ "${SERVER_NAME}" == "_" || "${SERVER_NAME}" == "localhost" || -z "${SERVER_NAME}" ]]; then
    warn "SERVER_NAME 为空 / 本地 → 跳过 HTTPS（域名未配置）"
    return 0
  fi
  local CERT_FILE="/etc/letsencrypt/live/${SERVER_NAME}/fullchain.pem"
  if [[ -f "${CERT_FILE}" ]]; then
    info "${SERVER_NAME} 证书已存在 → 跳过签发；cron 自动续期"
    install_certbot_renew_cron
    return 0
  fi

  # 1. 安装 certbot
  if ! command -v certbot >/dev/null 2>&1; then
    info "安装 certbot..."
    yum install -y epel-release >/dev/null 2>&1 || true
    yum install -y certbot python2-certbot-nginx >/dev/null 2>&1 \
      || yum install -y certbot python-certbot-nginx >/dev/null 2>&1
  fi
  if ! command -v certbot >/dev/null 2>&1; then
    err "certbot 安装失败 — 请手工：yum install -y epel-release && yum install -y certbot python2-certbot-nginx"
    return 1
  fi

  # 2. 用 webroot 模式签发（不需停 nginx；ACME challenge 落 /var/www/letsencrypt）
  mkdir -p /var/www/letsencrypt
  chmod 755 /var/www/letsencrypt
  systemctl reload nginx 2>/dev/null || true

  local LE_EMAIL="${LE_EMAIL:-admin@${SERVER_NAME}}"
  info "签发证书 domain=${SERVER_NAME} email=${LE_EMAIL}"
  certbot certonly --webroot -w /var/www/letsencrypt \
                   -d "${SERVER_NAME}" \
                   --non-interactive --agree-tos -m "${LE_EMAIL}" \
                   --no-eff-email || {
    err "certbot 签发失败 — 检查域名是否解析到本机 + 80 端口是否可达公网"
    return 1
  }

  # 3. 重新写 nginx 配置（这次 USE_HTTPS=true）+ reload
  configure_nginx
  install_certbot_renew_cron
  log "HTTPS 已启用 https://${SERVER_NAME}"
}

install_certbot_renew_cron() {
  # certbot 自带 systemd timer（如装了 certbot.timer）会接管；这里加 root crontab 兜底
  local CRON_LINE="0 3 * * * /usr/bin/certbot renew --quiet --deploy-hook 'systemctl reload nginx'"
  if ! crontab -l 2>/dev/null | grep -Fq "certbot renew"; then
    (crontab -l 2>/dev/null; echo "${CRON_LINE}") | crontab -
    info "已安装 certbot 续期 cron（每天 03:00）"
  fi
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
ARK_API_KEY=${ARK_API_KEY}
VOLCANO_APP_ID=${VOLCANO_APP_ID}
VOLCANO_ACCESS_TOKEN=${VOLCANO_ACCESS_TOKEN}
JIMENG_AK=${JIMENG_AK}
JIMENG_SK=${JIMENG_SK}
VOLCANO_AK=${VOLCANO_AK}
VOLCANO_SK=${VOLCANO_SK}
DOUYIN_CLIENT_KEY=${DOUYIN_CLIENT_KEY}
DOUYIN_CLIENT_SECRET=${DOUYIN_CLIENT_SECRET}
YUDAO_SMS_DEMO_MODE=${YUDAO_SMS_DEMO_MODE:-true}
YUDAO_SMS_DEMO_CODE=${YUDAO_SMS_DEMO_CODE:-888888}
ALLINPAY_ENABLED=${ALLINPAY_ENABLED}
ALLINPAY_API_BASE_URL=${ALLINPAY_API_BASE_URL}
ALLINPAY_APPID=${ALLINPAY_APPID}
ALLINPAY_MERCHANT_NO=${ALLINPAY_MERCHANT_NO}
ALLINPAY_MD5_KEY=${ALLINPAY_MD5_KEY}
ALLINPAY_RSA_PRIVATE_KEY=${ALLINPAY_RSA_PRIVATE_KEY}
ALLINPAY_RSA_PUBLIC_KEY=${ALLINPAY_RSA_PUBLIC_KEY}
ALLINPAY_SIGN_TYPE=${ALLINPAY_SIGN_TYPE}
ALLINPAY_SM2_PRIVATE_KEY=${ALLINPAY_SM2_PRIVATE_KEY}
ALLINPAY_SM2_PUBLIC_KEY=${ALLINPAY_SM2_PUBLIC_KEY}
ALLINPAY_ORG_ID=${ALLINPAY_ORG_ID}
ALLINPAY_USE_ONEPAY=${ALLINPAY_USE_ONEPAY}
ALLINPAY_DIAG_TOKEN=${ALLINPAY_DIAG_TOKEN}
SERVER_NAME=${SERVER_NAME}
MERCHANT_PACKAGE_PAY_APP_KEY=${MERCHANT_PACKAGE_PAY_APP_KEY}
MERCHANT_PACKAGE_PAY_APP_ID=${MERCHANT_PACKAGE_PAY_APP_ID}
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
# CentOS 7 systemd 219 不支持 append: → 走 journal（journalctl -u tanxiaer 看日志）
StandardOutput=journal
StandardError=journal
SyslogIdentifier=tanxiaer

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
    # 探活：HEAD 任意已注册 endpoint，只要不是 5xx / 连接拒绝就算"已就绪"
    # /admin-api/system/captcha/get 是 POST → HEAD 会返 405，但 405 = Spring DispatcherServlet 已起
    # 比原来 GET /admin-api/system/auth/captcha（不存在 → 404 但同样 spring 已起）更不会被误判
    local http_code
    http_code="$(curl -s -o /dev/null -w '%{http_code}' -X HEAD "http://localhost:${SERVER_PORT}/admin-api/system/captcha/get" 2>/dev/null || echo 000)"
    if [[ "${http_code}" != "000" && "${http_code:0:1}" != "5" ]]; then
      log "后端服务就绪 ✓ (HTTP ${http_code})"
      return
    fi
    i=$((i+1))
    echo -n "."
  done
  warn "启动超时，请检查: journalctl -u tanxiaer -n 80 --no-pager"
}

deploy_sidecar() {
  step "部署 Sidecar Node 服务（AI 视频后处理 / OSS / TTS / 抖音）"
  local SIDECAR_SRC="${PROJECT_DIR}/server/sidecar"
  local SIDECAR_DEST="${ROOT_DIR}/sidecar"
  if [[ ! -f "${SIDECAR_SRC}/index.js" ]]; then
    warn "未找到 ${SIDECAR_SRC}/index.js，跳过 sidecar 部署"
    return
  fi
  ensure_pnpm8

  # ffmpeg 已在 install_base 阶段装好；这里仅检测确认
  if command -v ffmpeg &>/dev/null; then
    log "ffmpeg → $(command -v ffmpeg)"
  else
    warn "未找到系统 ffmpeg；视频合成将不可用（不阻塞 sidecar 启动）"
  fi

  # 同步代码到 ${SIDECAR_DEST}（清掉旧 node_modules + lock，避免残留 ffmpeg-static
  # 在 CentOS 7 上跑出 GLIBC_2.18 错；干净 install）
  mkdir -p "${SIDECAR_DEST}"
  rm -rf "${SIDECAR_DEST}/node_modules" "${SIDECAR_DEST}/package-lock.json"
  cp -f "${SIDECAR_SRC}/package.json" "${SIDECAR_DEST}/"
  cp -f "${SIDECAR_SRC}/index.js" "${SIDECAR_DEST}/"
  cp -f "${SIDECAR_SRC}/README.md" "${SIDECAR_DEST}/" 2>/dev/null || true

  # bgm/ 子目录：只刷新脚本/README，不动已下载的 *.mp3（首次部署后用户跑
  # `cd ${SIDECAR_DEST}/bgm && bash download-bgm.sh` 拉素材；以后重部署素材保留）
  mkdir -p "${SIDECAR_DEST}/bgm"
  if [[ -f "${SIDECAR_SRC}/bgm/download-bgm.sh" ]]; then
    cp -f "${SIDECAR_SRC}/bgm/download-bgm.sh" "${SIDECAR_DEST}/bgm/"
    chmod +x "${SIDECAR_DEST}/bgm/download-bgm.sh"
  fi
  if [[ -f "${SIDECAR_SRC}/bgm/README.md" ]]; then
    cp -f "${SIDECAR_SRC}/bgm/README.md" "${SIDECAR_DEST}/bgm/"
  fi

  # 写运行环境变量（mode 600 防泄露）
  umask 077
  cat > "${SIDECAR_DEST}/.env" << SIDECAR_ENV_EOF
# 由 deploy.sh 自动生成；勿手工改，改 .env 后重跑 deploy.sh --skip-install --skip-build
SIDECAR_PORT=8081
TOS_AK=${JIMENG_AK}
TOS_SK=${JIMENG_SK}
TOS_BUCKET=${TOS_BUCKET:-tanxiaoer}
TOS_REGION=${TOS_REGION:-cn-beijing}
JIMENG_AK=${JIMENG_AK}
JIMENG_SK=${JIMENG_SK}
VOLCANO_ACCESS_TOKEN=${VOLCANO_ACCESS_TOKEN}
VOLCANO_AK=${VOLCANO_AK}
VOLCANO_SK=${VOLCANO_SK}
DOUYIN_CLIENT_KEY=${DOUYIN_CLIENT_KEY}
DOUYIN_CLIENT_SECRET=${DOUYIN_CLIENT_SECRET}
MERCHANT_INTERNAL_TOKEN=${MERCHANT_INTERNAL_TOKEN}
DEMO_MODE=${DEMO_MODE:-false}
# 强制 sidecar 用系统 ffmpeg（CentOS 7 glibc 2.17 下 ffmpeg-static 不可用）
FFMPEG_PATH=${FFMPEG_PATH:-/usr/bin/ffmpeg}
SIDECAR_ENV_EOF
  umask 022

  # 装依赖
  cd "${SIDECAR_DEST}"
  export PATH="/opt/nodejs/bin:${PATH}"
  info "sidecar npm install --omit=dev --ignore-scripts ..."
  # --ignore-scripts：跳过 ffmpeg-static / aws-sdk 等的 postinstall 脚本
  # （CentOS 7 上即使下载成功 GLIBC 2.17 也跑不了；走系统 yum 装的 ffmpeg）
  # 走淘宝镜像 + 失败兜底：第一次失败重试一次 + 加 --prefer-offline；都失败了
  # 至少 sidecar 已存在的 node_modules 仍可用（如果是首次部署则 sidecar 起不来 — warn 提示）
  if ! npm install --omit=dev --ignore-scripts \
                   --registry=https://registry.npmmirror.com \
                   --no-audit --fund=false 2>&1 | tail -10; then
    warn "首次 npm install 失败，重试 (官方源 + --prefer-offline)..."
    npm install --omit=dev --ignore-scripts \
                --prefer-offline --no-audit --fund=false 2>&1 | tail -10 \
      || warn "sidecar npm install 仍失败 — 视频/OSS 等功能不可用，但不阻塞主部署"
  fi

  # 装一份系统中文字体，sidecar 端卡 / 字幕烧录用得上
  if ! ls /usr/share/fonts/wqy-zenhei/wqy-zenhei.ttc 2>/dev/null && \
     ! ls /usr/share/fonts/truetype/wqy/wqy-zenhei.ttc 2>/dev/null; then
    info "安装中文字体 wqy-zenhei（sidecar 视频字幕用）"
    yum install -y wqy-zenhei-fonts 2>&1 | tail -3 || \
      warn "wqy-zenhei-fonts 安装失败，端卡字幕可能无法烧录中文（视频仍可生成，仅是无字幕降级）"
  fi

  chown -R "${SERVICE_USER}:${SERVICE_USER}" "${SIDECAR_DEST}"
  chmod 600 "${SIDECAR_DEST}/.env"

  # systemd unit
  local NODE_BIN
  NODE_BIN=$(readlink -f "$(command -v node)")
  cat > /etc/systemd/system/tanxiaer-sidecar.service << SIDECAR_UNIT_EOF
[Unit]
Description=摊小二 Sidecar (AI 视频 / OSS / TTS / 抖音 BFF)
After=network.target

[Service]
Type=simple
User=${SERVICE_USER}
Group=${SERVICE_USER}
WorkingDirectory=${SIDECAR_DEST}
EnvironmentFile=${SIDECAR_DEST}/.env
ExecStart=${NODE_BIN} ${SIDECAR_DEST}/index.js
Restart=always
RestartSec=5
# CentOS 7 systemd 219 不支持 append: 输出说明符（systemd 240+ 才有），
# 改用 journal — 用 journalctl -u tanxiaer-sidecar 看日志
StandardOutput=journal
StandardError=journal
SyslogIdentifier=tanxiaer-sidecar

NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
SIDECAR_UNIT_EOF

  chmod 644 /etc/systemd/system/tanxiaer-sidecar.service
  systemctl daemon-reload
  systemctl enable tanxiaer-sidecar
  systemctl restart tanxiaer-sidecar || warn "sidecar systemctl restart 失败（继续）"

  # 心跳检查
  local i=0
  while [[ $i -lt 6 ]]; do
    sleep 2
    if curl -sf "http://127.0.0.1:8081/healthz" &>/dev/null; then
      log "sidecar 服务就绪 ✓ (http://127.0.0.1:8081)"
      return 0
    fi
    i=$((i+1))
  done
  warn "sidecar 启动超时（不阻塞），AI 视频 / OSS / 抖音功能降级"
  warn "排查：journalctl -u tanxiaer-sidecar -n 80 --no-pager"
  return 0
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

  # --reset 必须在 setup_database 之前、mysql/redis 已起来之后
  # （install_mysql / install_redis 在 SKIP_INSTALL=false 路径下已起；
  #   SKIP_INSTALL=true 时假定服务已在跑）
  if [[ "${RESET_DATA}" == true ]]; then
    reset_data
  fi

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
  setup_https || warn "HTTPS 未启用，仍以 HTTP 提供服务"
  deploy_backend_service

  # sidecar 部署允许失败（视频/抖音功能可降级，不阻塞主部署完成）
  if ! deploy_sidecar; then
    warn "sidecar 部署失败 — AI 视频 / OSS / 抖音相关功能降级"
    warn "排查：journalctl -u tanxiaer-sidecar -n 80 --no-pager"
  fi

  setup_firewall

  # 跑完冒烟自检（10 幕，覆盖 P0-1 ~ P2-11 全部链路；任意失败给清单）
  if [[ -x "${PROJECT_DIR}/scripts/post-deploy-verify.sh" ]]; then
    info "跑部署后冒烟自检（10 幕）..."
    bash "${PROJECT_DIR}/scripts/post-deploy-verify.sh" \
         --base-url "http://localhost" \
         --mysql-pass "${MYSQL_ROOT_PASS}" \
         --project-dir "${PROJECT_DIR}" || warn "自检发现问题，详见上方清单"
  fi

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
  echo -e "${CYAN}═══════════════ 演示账号 / 入口 ═══════════════${NC}"
  echo "  PC 后台:        http://${PUBLIC_IP}/admin/   admin / admin123"
  echo "  H5 入口:        http://${PUBLIC_IP}/m/#/pages/login/index"
  echo "  H5 登录方式:    手机号 + 任意 ≥6 位密码（首次输入即注册，不发短信）"
  echo "  商户邀请码:     DEMO20260428（无限次，演示前请勿外泄）"
  echo "  分享链接示例:   http://${PUBLIC_IP}/m/shop-home?inviter=<userId>"
  echo "  营销手动结算:   POST /app-api/merchant/mini/promo/pool/settle?mode=FULL"
  echo ""
  echo -e "${CYAN}═══════════════ 演示链路速览 ═══════════════${NC}"
  echo "  1. 手机号+密码登录 → 默认 member 身份"
  echo "  2. 输入邀请码 DEMO20260428 → 一键开通商户身份（无审核）"
  echo "  3. 商户后台 PC 网页：营销引擎 / 商品营销 / 提现审批 三页"
  echo "  4. AI 极简发布商品 → 自动上架（无审核）"
  echo "  5. 用 me/qrcode 拿商户分享码 → 用户扫码进 shop-home → 下单"
  echo "  6. 双端订单页（商户 me/orders、用户 user-order/list）实时可见"
  echo "  7. 推广积分流水可在 user-me/wallet 看到 / 我的队列页查看排队状态"
  echo ""
  echo -e "${YELLOW}生产上线前请务必：${NC}"
  echo "  1. 阿里云 ECS 安全组放行 80/443 端口"
  echo "  2. 将 SERVER_NAME 改为你的真实域名"
  echo "  3. 执行 yum install -y certbot python2-certbot-nginx && certbot --nginx 配置 HTTPS"
  echo "  4. 在 .env 中填入真实 ARK_API_KEY / VOLCANO_* / DOUYIN_*（演示也要走 AI 视频时）"
  echo "  5. 演示完毕：禁用邀请码 DEMO20260428（system_menu 后台 -> merchant_invite_code）"
  echo "  6. 确认 .env 已加入 .gitignore，不会被提交"
}

main "$@"
