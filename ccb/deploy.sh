#!/bin/bash
#
# 多商户SaaS平台 - CentOS 一键部署脚本
# 支持: CentOS 7/8/9, Rocky Linux 8/9, AlmaLinux 8/9
#
# 注意: 本项目为 jdk8 分支 (Spring Boot 2.7.x)，需要 JDK 8，不支持 JDK 17/21
#
# 运行时服务 (4个):
#   1. MySQL 8.0       - 数据库
#   2. Redis           - 缓存
#   3. yudao-server    - 后端主服务 (Fat JAR，含商城/支付/商户/公众号等模块)
#   4. Nginx           - 反向代理 + 管理后台静态文件
#
# 已包含模块: system / infra / member / pay / mp / 商城(product+promotion+trade+statistics) / merchant / video
# 已排除模块: bpm(工作流) / report / crm / erp / iot / mes / ai
#
# 构建工具 (仅构建阶段使用，不作为运行时服务):
#   - JDK 8 (OpenJDK)
#   - Maven 3.9
#   - Node.js 18 (构建管理后台前端)
#
# 使用方式:
#   chmod +x deploy.sh
#   sudo ./deploy.sh
#

set -e

# ==================== 配置区 ====================
# 数据库
DB_NAME="ruoyi-vue-pro"
DB_USER="ruoyi"
DB_PASS="Ruoyi@2024!"
DB_ROOT_PASS="Root@2024!"

# Redis
REDIS_PASS="Redis@2024!"

# 项目
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_NAME="yudao-server"
APP_PORT=48080
DEPLOY_DIR="/opt/yudao"
LOG_DIR="/var/log/yudao"

# 域名（部署后修改 nginx 配置即可）
DOMAIN="localhost"

# 火山引擎 & 抖音（部署后在配置文件中修改）
VOLCANO_APP_ID=""
VOLCANO_ACCESS_TOKEN=""
VOLCANO_AK=""
VOLCANO_SK=""
DOUYIN_CLIENT_KEY=""
DOUYIN_CLIENT_SECRET=""

# ==================== 颜色输出 ====================
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

# ==================== 前置检查 ====================
check_root() {
    if [ "$(id -u)" != "0" ]; then
        error "请使用 root 用户或 sudo 执行此脚本"
    fi
}

check_os() {
    if [ -f /etc/redhat-release ]; then
        OS_VERSION=$(rpm -q --qf '%{VERSION}' centos-release 2>/dev/null || \
                     rpm -q --qf '%{VERSION}' rocky-release 2>/dev/null || \
                     rpm -q --qf '%{VERSION}' almalinux-release 2>/dev/null || echo "8")
        OS_MAJOR=$(echo "$OS_VERSION" | cut -d. -f1)
        info "检测到系统: $(cat /etc/redhat-release), 主版本: $OS_MAJOR"
    else
        error "此脚本仅支持 CentOS/Rocky/AlmaLinux 系统"
    fi
}

# ==================== 安装基础工具 ====================
install_base() {
    info "安装基础工具..."
    yum install -y epel-release 2>/dev/null || dnf install -y epel-release 2>/dev/null
    yum install -y wget curl vim git unzip tar net-tools lsof firewalld
    systemctl start firewalld 2>/dev/null || true
    systemctl enable firewalld 2>/dev/null || true
    # 开放端口
    firewall-cmd --permanent --add-port=80/tcp 2>/dev/null || true
    firewall-cmd --permanent --add-port=443/tcp 2>/dev/null || true
    firewall-cmd --permanent --add-port=${APP_PORT}/tcp 2>/dev/null || true
    firewall-cmd --reload 2>/dev/null || true
    info "基础工具安装完成"
}

# ==================== 安装 JDK 8 ====================
install_jdk() {
    if java -version 2>&1 | grep -q '1.8'; then
        info "JDK 8 已安装，跳过"
        return
    fi
    info "安装 JDK 8..."
    yum install -y java-1.8.0-openjdk java-1.8.0-openjdk-devel
    # 设置 JAVA_HOME
    JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
    cat > /etc/profile.d/java.sh << EOF
export JAVA_HOME=${JAVA_HOME}
export PATH=\$JAVA_HOME/bin:\$PATH
EOF
    source /etc/profile.d/java.sh
    info "JDK 8 安装完成: $(java -version 2>&1 | head -1)"
}

# ==================== 安装 Maven ====================
install_maven() {
    if mvn -version 2>&1 | grep -q 'Apache Maven'; then
        info "Maven 已安装，跳过"
        return
    fi
    info "安装 Maven 3.9..."
    MVN_VERSION="3.9.9"
    cd /tmp
    wget -q "https://dlcdn.apache.org/maven/maven-3/${MVN_VERSION}/binaries/apache-maven-${MVN_VERSION}-bin.tar.gz"
    tar -xzf "apache-maven-${MVN_VERSION}-bin.tar.gz" -C /opt/
    ln -sf "/opt/apache-maven-${MVN_VERSION}/bin/mvn" /usr/local/bin/mvn
    cat > /etc/profile.d/maven.sh << EOF
export MAVEN_HOME=/opt/apache-maven-${MVN_VERSION}
export PATH=\$MAVEN_HOME/bin:\$PATH
EOF
    source /etc/profile.d/maven.sh
    # 配置阿里云镜像加速
    mkdir -p ~/.m2
    cat > ~/.m2/settings.xml << 'SETTINGS'
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <name>Aliyun Maven Mirror</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
SETTINGS
    info "Maven 安装完成: $(mvn -version 2>&1 | head -1)"
}

# ==================== 安装 MySQL 8.0 ====================
install_mysql() {
    if systemctl is-active --quiet mysqld 2>/dev/null; then
        info "MySQL 已运行，跳过安装"
        return
    fi
    info "安装 MySQL 8.0..."

    # 添加 MySQL 官方 Yum 源
    if [ "$OS_MAJOR" -ge 8 ]; then
        yum install -y mysql-server mysql
        systemctl start mysqld
        systemctl enable mysqld
    else
        rpm -Uvh https://dev.mysql.com/get/mysql80-community-release-el7-11.noarch.rpm 2>/dev/null || true
        yum install -y mysql-community-server mysql-community-client --nogpgcheck
        systemctl start mysqld
        systemctl enable mysqld
        # CentOS 7 需要获取临时密码
        TEMP_PASS=$(grep 'temporary password' /var/log/mysqld.log 2>/dev/null | tail -1 | awk '{print $NF}')
        if [ -n "$TEMP_PASS" ]; then
            mysql --connect-expired-password -uroot -p"${TEMP_PASS}" -e "
                ALTER USER 'root'@'localhost' IDENTIFIED BY '${DB_ROOT_PASS}';
                FLUSH PRIVILEGES;
            " 2>/dev/null || true
        fi
    fi

    # CentOS 8+ 默认无密码，直接设置
    mysql -uroot -e "
        ALTER USER 'root'@'localhost' IDENTIFIED BY '${DB_ROOT_PASS}';
        FLUSH PRIVILEGES;
    " 2>/dev/null || true

    # 创建数据库和用户
    mysql -uroot -p"${DB_ROOT_PASS}" -e "
        CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
        CREATE USER IF NOT EXISTS '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASS}';
        CREATE USER IF NOT EXISTS '${DB_USER}'@'%' IDENTIFIED BY '${DB_PASS}';
        GRANT ALL PRIVILEGES ON \`${DB_NAME}\`.* TO '${DB_USER}'@'localhost';
        GRANT ALL PRIVILEGES ON \`${DB_NAME}\`.* TO '${DB_USER}'@'%';
        FLUSH PRIVILEGES;
    "

    info "MySQL 8.0 安装完成, 数据库: ${DB_NAME}, 用户: ${DB_USER}"
}

# ==================== 导入数据库 ====================
import_database() {
    info "导入数据库..."
    # 主库
    if [ -f "${PROJECT_DIR}/sql/mysql/ruoyi-vue-pro.sql" ]; then
        mysql -u${DB_USER} -p"${DB_PASS}" ${DB_NAME} < "${PROJECT_DIR}/sql/mysql/ruoyi-vue-pro.sql"
        info "主库 SQL 导入完成"
    fi
    # Quartz
    if [ -f "${PROJECT_DIR}/sql/mysql/quartz.sql" ]; then
        mysql -u${DB_USER} -p"${DB_PASS}" ${DB_NAME} < "${PROJECT_DIR}/sql/mysql/quartz.sql"
        info "Quartz SQL 导入完成"
    fi
    # 商户表
    if [ -f "${PROJECT_DIR}/sql/mysql/merchant.sql" ]; then
        mysql -u${DB_USER} -p"${DB_PASS}" ${DB_NAME} < "${PROJECT_DIR}/sql/mysql/merchant.sql"
        info "商户表 SQL 导入完成"
    fi
    # 视频表
    if [ -f "${PROJECT_DIR}/sql/mysql/video.sql" ]; then
        mysql -u${DB_USER} -p"${DB_PASS}" ${DB_NAME} < "${PROJECT_DIR}/sql/mysql/video.sql"
        info "视频表 SQL 导入完成"
    fi
    info "数据库导入全部完成"
}

# ==================== 安装 Redis ====================
install_redis() {
    if systemctl is-active --quiet redis 2>/dev/null; then
        info "Redis 已运行，跳过安装"
        return
    fi
    info "安装 Redis..."
    yum install -y redis
    # 配置密码和绑定
    sed -i "s/^# requirepass.*/requirepass ${REDIS_PASS}/" /etc/redis.conf 2>/dev/null || \
    sed -i "s/^# requirepass.*/requirepass ${REDIS_PASS}/" /etc/redis/redis.conf 2>/dev/null || true
    sed -i "s/^bind 127.0.0.1.*/bind 127.0.0.1/" /etc/redis.conf 2>/dev/null || true
    systemctl start redis
    systemctl enable redis
    info "Redis 安装完成, 密码: ${REDIS_PASS}"
}

# ==================== 安装 Node.js 18 ====================
install_nodejs() {
    if node -v 2>&1 | grep -q 'v18\|v20'; then
        info "Node.js 已安装，跳过"
        return
    fi
    info "安装 Node.js 18..."
    curl -fsSL https://rpm.nodesource.com/setup_18.x | bash -
    yum install -y nodejs
    # 配置 npm 镜像
    npm config set registry https://registry.npmmirror.com
    info "Node.js 安装完成: $(node -v)"
}

# ==================== 安装 Nginx ====================
install_nginx() {
    if systemctl is-active --quiet nginx 2>/dev/null; then
        info "Nginx 已运行，跳过安装"
        return
    fi
    info "安装 Nginx..."
    yum install -y nginx
    systemctl start nginx
    systemctl enable nginx
    info "Nginx 安装完成"
}

# ==================== 编译后端 ====================
build_backend() {
    info "编译后端项目（首次编译需要较长时间下载依赖）..."
    cd "${PROJECT_DIR}"
    source /etc/profile.d/java.sh 2>/dev/null || true
    source /etc/profile.d/maven.sh 2>/dev/null || true

    mvn clean package -DskipTests -pl yudao-server -am -T 2C
    info "后端编译完成"
}

# ==================== 部署后端 ====================
deploy_backend() {
    info "部署后端..."
    mkdir -p ${DEPLOY_DIR} ${LOG_DIR}

    # 复制 jar
    cp "${PROJECT_DIR}/yudao-server/target/${APP_NAME}.jar" "${DEPLOY_DIR}/${APP_NAME}.jar"

    # 生成生产环境配置
    cat > "${DEPLOY_DIR}/application-prod.yaml" << EOF
server:
  port: ${APP_PORT}

spring:
  datasource:
    dynamic:
      primary: master
      datasource:
        master:
          url: jdbc:mysql://127.0.0.1:3306/${DB_NAME}?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true&rewriteBatchedStatements=true
          username: ${DB_USER}
          password: ${DB_PASS}
        slave:
          url: jdbc:mysql://127.0.0.1:3306/${DB_NAME}?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true&nullCatalogMeansCurrent=true
          username: ${DB_USER}
          password: ${DB_PASS}
  redis:
    host: 127.0.0.1
    port: 6379
    password: ${REDIS_PASS}
    database: 0

# AI视频模块配置
video:
  volcano-engine:
    app-id: ${VOLCANO_APP_ID}
    access-token: ${VOLCANO_ACCESS_TOKEN}
    access-key-id: ${VOLCANO_AK}
    access-key-secret: ${VOLCANO_SK}
  douyin:
    client-key: ${DOUYIN_CLIENT_KEY}
    client-secret: ${DOUYIN_CLIENT_SECRET}

# 商户配置
merchant:
  qrcode:
    page: pages/shop/index
    upload-path: ${DEPLOY_DIR}/qrcode/

# 日志
logging:
  file:
    name: ${LOG_DIR}/${APP_NAME}.log
  level:
    cn.iocoder.yudao: info
EOF

    # 创建 systemd 服务
    cat > /etc/systemd/system/yudao.service << EOF
[Unit]
Description=YuDao SaaS Platform
After=network.target mysqld.service redis.service
Wants=mysqld.service redis.service

[Service]
Type=simple
User=root
WorkingDirectory=${DEPLOY_DIR}
ExecStart=/usr/bin/java -Xms512m -Xmx1024m -jar ${DEPLOY_DIR}/${APP_NAME}.jar --spring.profiles.active=prod --spring.config.additional-location=${DEPLOY_DIR}/application-prod.yaml
ExecStop=/bin/kill -TERM \$MAINPID
Restart=on-failure
RestartSec=10
StandardOutput=append:${LOG_DIR}/${APP_NAME}.log
StandardError=append:${LOG_DIR}/${APP_NAME}-error.log

[Install]
WantedBy=multi-user.target
EOF

    systemctl daemon-reload
    systemctl enable yudao
    systemctl start yudao
    info "后端部署完成，服务端口: ${APP_PORT}"
}

# ==================== 构建前端（管理后台） ====================
build_admin_frontend() {
    info "构建管理后台前端..."
    ADMIN_DIR="${PROJECT_DIR}/yudao-ui/yudao-ui-admin-vue3"
    if [ ! -d "$ADMIN_DIR" ] || [ ! -f "$ADMIN_DIR/package.json" ]; then
        warn "管理后台前端目录不存在，跳过"
        return
    fi
    cd "$ADMIN_DIR"
    npm install
    # 修改 API 地址为当前服务器
    cat > .env.production << EOF
VITE_BASE_URL=http://${DOMAIN}:${APP_PORT}
VITE_API_URL=/admin-api
EOF
    npm run build
    # 部署到 nginx
    mkdir -p /usr/share/nginx/html/admin
    cp -rf dist/* /usr/share/nginx/html/admin/
    info "管理后台前端构建完成"
}

# ==================== 配置 Nginx ====================
configure_nginx() {
    info "配置 Nginx..."

    cat > /etc/nginx/conf.d/yudao.conf << EOF
# 管理后台
server {
    listen 80;
    server_name ${DOMAIN};

    # 管理后台前端
    location / {
        root /usr/share/nginx/html/admin;
        index index.html;
        try_files \$uri \$uri/ /index.html;
    }

    # 后端 API - 管理后台
    location /admin-api/ {
        proxy_pass http://127.0.0.1:${APP_PORT}/admin-api/;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_read_timeout 600s;
    }

    # 后端 API - 小程序/App
    location /app-api/ {
        proxy_pass http://127.0.0.1:${APP_PORT}/app-api/;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_read_timeout 600s;
    }

    # WebSocket
    location /infra/ws {
        proxy_pass http://127.0.0.1:${APP_PORT}/infra/ws;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host \$host;
    }

    # 文件上传大小限制
    client_max_body_size 100m;

    # 抖音 OAuth 回调（H5页面）
    location /video/douyin/oauth/callback {
        proxy_pass http://127.0.0.1:${APP_PORT}/app-api/video/douyin/oauth/callback;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
    }

    # 小程序码静态文件
    location /qrcode/ {
        alias ${DEPLOY_DIR}/qrcode/;
    }

    # TTS 音频静态文件
    location /audio/ {
        alias /tmp/audio/;
    }
}
EOF

    # 检查配置
    nginx -t
    systemctl reload nginx
    info "Nginx 配置完成"
}

# ==================== 健康检查 ====================
health_check() {
    info "等待服务启动..."
    MAX_WAIT=120
    WAITED=0
    while [ $WAITED -lt $MAX_WAIT ]; do
        if curl -sf http://127.0.0.1:${APP_PORT}/admin-api/system/tenant/get-id-by-name?name=芋道源码 > /dev/null 2>&1; then
            info "服务启动成功!"
            return
        fi
        sleep 3
        WAITED=$((WAITED + 3))
        echo -n "."
    done
    echo ""
    warn "服务可能还在启动中，请稍后检查: journalctl -u yudao -f"
}

# ==================== 打印部署信息 ====================
print_info() {
    echo ""
    echo "=============================================="
    echo -e "${GREEN}  部署完成!${NC}"
    echo "=============================================="
    echo ""
    echo "  管理后台:  http://${DOMAIN}"
    echo "  API 地址:  http://${DOMAIN}:${APP_PORT}"
    echo ""
    echo "  默认管理员: admin / admin123"
    echo ""
    echo "  MySQL:"
    echo "    数据库: ${DB_NAME}"
    echo "    用户名: ${DB_USER}"
    echo "    密码:   ${DB_PASS}"
    echo "    Root:   ${DB_ROOT_PASS}"
    echo ""
    echo "  Redis:"
    echo "    密码:   ${REDIS_PASS}"
    echo ""
    echo "=============================================="
    echo "  小程序配置:"
    echo "    在 HBuilderX 打开 yudao-ui/yudao-ui-mall-uniapp"
    echo "    修改 .env 中 SHOPRO_BASE_URL 为你的服务器地址"
    echo "    例: SHOPRO_BASE_URL=http://你的IP:${APP_PORT}"
    echo ""
    echo "  待配置项（在 ${DEPLOY_DIR}/application-prod.yaml 中修改）:"
    echo "    - 火山引擎 AppID / AccessKey（AI视频生成）"
    echo "    - 抖音开放平台 ClientKey / ClientSecret"
    echo "    - 微信支付服务商 mchId / 证书"
    echo "    - 微信小程序 AppID / Secret"
    echo "=============================================="
    echo ""
    echo "  常用命令:"
    echo "    查看日志:   tail -f ${LOG_DIR}/${APP_NAME}.log"
    echo "    重启服务:   systemctl restart yudao"
    echo "    停止服务:   systemctl stop yudao"
    echo "    服务状态:   systemctl status yudao"
    echo ""
}

# ==================== 主流程 ====================
main() {
    echo ""
    echo "=============================================="
    echo "  多商户SaaS平台 - CentOS 一键部署"
    echo "=============================================="
    echo ""

    check_root
    check_os

    install_base
    install_jdk
    install_maven
    install_mysql
    import_database
    install_redis
    install_nodejs
    install_nginx

    build_backend
    deploy_backend
    build_admin_frontend
    configure_nginx

    health_check
    print_info
}

main "$@"
