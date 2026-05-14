#!/usr/bin/env bash
# ============================================================================
#  小二（拓小二 + 客小二）子域名分流一键开通脚本
# ============================================================================
#
# 用途：在已部署 www.doupaidoudian.com 的服务器上，开通 2 个子域名：
#   tuo.doupaidoudian.com → 商户端（App.vue 嗅探 host → reLaunch /pages/merchant-login）
#   ke.doupaidoudian.com  → 用户端（嗅探 → reLaunch /pages/user-home）
#
# 前置条件（你必须先做）：
#   1. DNS 控制台已加 2 条 A 记录：
#        tuo  A  47.109.143.146  TTL 600
#        ke   A  47.109.143.146  TTL 600
#   2. 等 DNS 全国生效（一般 5-30 分钟）—— 可用 dig tuo.doupaidoudian.com 验证
#
# 本脚本会：
#   1. 验证 DNS 解析对外可见
#   2. 用 certbot 扩展现有 cert（保留 cert-name=www.doupaidoudian.com）
#   3. 改 /etc/nginx/conf.d/tanxiaer.conf 加 tuo / ke 两个 server block
#   4. nginx -t && systemctl reload nginx
#
# 使用：在服务器上跑 sudo bash setup-branded-domains.sh
# ============================================================================

set -euo pipefail

ROOT_DOMAIN="doupaidoudian.com"
HOSTS=("tuo.${ROOT_DOMAIN}" "ke.${ROOT_DOMAIN}")
PRIMARY="www.${ROOT_DOMAIN}"   # certbot --cert-name 锚点
SERVER_IP="47.109.143.146"
NGINX_CONF="/etc/nginx/conf.d/tanxiaer.conf"
ROOT_DIR="/opt/tanxiaer"

# ── 1. DNS 解析校验 ─────────────────────────────────────────────────────────
echo "==> [1/4] 检查 DNS 解析"
for h in "${HOSTS[@]}"; do
  resolved=$(dig +short "$h" @114.114.114.114 2>/dev/null | head -1 || true)
  if [[ -z "$resolved" ]]; then
    echo "  ❌ $h 解析为空。请先在 DNS 控制台加 A 记录："
    echo "       ${h%%.${ROOT_DOMAIN}} → ${SERVER_IP}"
    exit 1
  fi
  if [[ "$resolved" != "$SERVER_IP" ]]; then
    echo "  ⚠ $h 解析到 $resolved，期望 ${SERVER_IP}（可能是 DNS 缓存，要等几分钟）"
    exit 1
  fi
  echo "  ✓ $h → $resolved"
done

# ── 2. certbot 扩展证书 ──────────────────────────────────────────────────────
echo "==> [2/4] certbot 扩展证书（${PRIMARY} + ${HOSTS[*]}）"
CERT_DOMAINS=(-d "$PRIMARY")
for h in "${HOSTS[@]}"; do
  CERT_DOMAINS+=(-d "$h")
done

certbot certonly --webroot -w /var/www/letsencrypt \
  --cert-name "$PRIMARY" \
  --expand \
  --non-interactive \
  --agree-tos \
  --email admin@${ROOT_DOMAIN} \
  "${CERT_DOMAINS[@]}"

echo "  ✓ 证书已扩展，SAN 现含：${PRIMARY} ${HOSTS[*]}"

# ── 3. 改 nginx 配置 ─────────────────────────────────────────────────────────
echo "==> [3/4] 写入 nginx 子域名 server block"

# 备份
cp -a "$NGINX_CONF" "${NGINX_CONF}.bak.$(date +%Y%m%d-%H%M%S)"

# 检测是否已包含子域名 server block（幂等）
if grep -q "server_name tuo\." "$NGINX_CONF" && grep -q "server_name ke\." "$NGINX_CONF"; then
  echo "  ℹ 子域名 server block 已存在，跳过追加"
else
  cat >> "$NGINX_CONF" << NGINX_EOF

# ─────────── 拓小二（商户端）/ 客小二（用户端）子域名分流 ───────────
# H5 bundle 与 www 共用 /opt/tanxiaer/m/；App.vue 嗅探 location.hostname 区分入口。
# /admin-api/ /app-api/ /oss/ /tts/ /video/ /jimeng /douyin/ /qr 等代理路径都共享。

# 80 → 443（保留 ACME challenge 通道）
server {
    listen 80;
    server_name tuo.${ROOT_DOMAIN} ke.${ROOT_DOMAIN};
    location /.well-known/acme-challenge/ {
        root /var/www/letsencrypt;
        default_type "text/plain";
    }
    location / { return 301 https://\$host\$request_uri; }
}

server {
    listen 443 ssl http2;
    server_name tuo.${ROOT_DOMAIN} ke.${ROOT_DOMAIN};
    charset utf-8;
    client_max_body_size 50m;

    ssl_certificate     /etc/letsencrypt/live/${PRIMARY}/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/${PRIMARY}/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 1d;
    add_header Strict-Transport-Security "max-age=31536000" always;

    # 子域名根路径 → 直接渲染 H5（不再走官网）
    # uniapp 输出基础路径是 /m/，asset 引用 <script src="/m/assets/...">
    # 所以这里把 / 内部 rewrite 到 /m/index.html，让 hash 路由接管
    location = / {
        alias ${ROOT_DIR}/m/index.html;
        add_header Cache-Control "no-cache, no-store, must-revalidate";
    }

    # H5 资源 — 强缓存（hash 文件名安全）
    location ^~ /m/assets/ {
        alias ${ROOT_DIR}/m/assets/;
        expires 30d;
        add_header Cache-Control "public, immutable";
        access_log off;
    }
    location = /m/index.html {
        alias ${ROOT_DIR}/m/index.html;
        add_header Cache-Control "no-cache, no-store, must-revalidate";
    }
    location ^~ /m/ {
        alias ${ROOT_DIR}/m/;
        index index.html;
        try_files \$uri \$uri/ /m/index.html;
    }

    # 后端 API 共用 www 已配的 upstream
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

    # Sidecar 路径（OSS / TTS / 视频 / 抖音 / 二维码）
    location /oss/    { proxy_pass http://127.0.0.1:8081; proxy_set_header Host \$host; client_max_body_size 100m; proxy_read_timeout 600s; }
    location /tts/    { proxy_pass http://127.0.0.1:8081; proxy_set_header Host \$host; proxy_buffering off; proxy_read_timeout 120s; }
    location /video/  { proxy_pass http://127.0.0.1:8081; proxy_set_header Host \$host; client_max_body_size 200m; proxy_read_timeout 900s; }
    location /vproxy  { proxy_pass http://127.0.0.1:8081; proxy_set_header Host \$host; proxy_read_timeout 300s; }
    location /jimeng  { proxy_pass http://127.0.0.1:8081; proxy_set_header Host \$host; }
    location /douyin/ { proxy_pass http://127.0.0.1:8081; proxy_set_header Host \$host; client_max_body_size 200m; proxy_read_timeout 600s; }
    location /qr      { proxy_pass http://127.0.0.1:8081; proxy_set_header Host \$host; proxy_read_timeout 30s; }
}
NGINX_EOF
  echo "  ✓ 已追加 2 个 server block（http→https + https 主块）"
fi

# ── 4. 测试 + reload ────────────────────────────────────────────────────────
echo "==> [4/4] nginx -t && reload"
if ! nginx -t; then
  echo "  ❌ nginx -t 失败！请检查 ${NGINX_CONF}"
  exit 1
fi
systemctl reload nginx
echo "  ✓ nginx reload 完成"

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "✅ 子域名开通完成。验证："
echo ""
echo "   商户端：https://tuo.${ROOT_DOMAIN}/"
echo "   用户端：https://ke.${ROOT_DOMAIN}/"
echo ""
echo "   原 /m/ 链接仍可用：https://www.${ROOT_DOMAIN}/m/"
echo "═══════════════════════════════════════════════════════════════"
