#!/usr/bin/env bash
# =============================================================================
#  止损脚本：修管理后台登录 API 走 http://admin-api/... 错误
#
#  现象：登录页提示登录失败，浏览器 console 报：
#    GET http://admin-api/system/tenant/get-id-by-name?name=芋道源码 net::ERR_NAME_NOT_RESOLVED
#
#  根因：旧 deploy.sh 在 .env.prod.local 写了 VITE_BASE_URL=/，axios baseURL 拼成
#       "/" + "/admin-api" = "//admin-api"，浏览器把 //admin-api 当 protocol-
#       relative URL 解析成 http://admin-api → DNS 解析失败。
#       修复：VITE_BASE_URL 必须留空，base_url 拼成 "/admin-api" 相对当前域名。
#
#  本脚本仅重 build 管理后台前端 + 部署，不动后端 / DB / SQL。
#
#  用法（在 ECS 上）：
#    sudo bash fix-admin-baseurl.sh
# =============================================================================
set -euo pipefail

PROJECT_DIR="${PROJECT_DIR:-/opt/tanxiaer/repo/ccb}"
ROOT_DIR="${ROOT_DIR:-/opt/tanxiaer}"

if [[ ! -d "$PROJECT_DIR/yudao-ui/yudao-ui-admin-vue3" ]]; then
  echo "❌ 找不到管理后台源码目录：$PROJECT_DIR/yudao-ui/yudao-ui-admin-vue3"
  echo "   请先 git pull 拉到最新代码后再跑本脚本。"
  exit 1
fi

cd "$PROJECT_DIR/yudao-ui/yudao-ui-admin-vue3"

echo "==> 写 .env.prod.local（修 VITE_BASE_URL=/ 这个地雷）"
cat > .env.prod.local <<'ENVEOF'
VITE_DEV=false
VITE_APP_TITLE=摊小二管理后台
# 关键：VITE_BASE_URL 必须留空，axios 真正的 baseURL = VITE_BASE_URL + VITE_API_URL
# 写 "/" 会拼成 "//admin-api"，浏览器解析成 protocol-relative URL → http://admin-api/...
VITE_BASE_URL=
VITE_API_URL=/admin-api
VITE_APP_CAPTCHA_ENABLE=false
VITE_OUT_DIR=dist-prod
VITE_BASE_PATH=/admin/
ENVEOF

echo "==> pnpm install"
if ! command -v pnpm >/dev/null 2>&1; then
  echo "❌ 未找到 pnpm，请先安装：corepack enable && corepack prepare pnpm@8 --activate"
  exit 2
fi
pnpm install --registry=https://registry.npmmirror.com \
             --network-concurrency=4 --child-concurrency=2

# 临时停后端，让 Java 堆给 vite
echo "==> 临时停 tanxiaer 让 Node 拿够堆"
systemctl stop tanxiaer 2>/dev/null || true

echo "==> vite build (Node heap=3072MB, mode=prod)"
node --max_old_space_size=3072 ./node_modules/vite/bin/vite.js build --mode prod

SRC_DIST=""
if [[ -d dist-prod ]]; then SRC_DIST="dist-prod"
elif [[ -d dist ]]; then SRC_DIST="dist"
else
  echo "❌ vite build 未产出 dist-prod/ 或 dist/"; exit 3
fi

DIST_DIR="$ROOT_DIR/admin-dist"
echo "==> 替换 $DIST_DIR ← $SRC_DIST"
rm -rf "$DIST_DIR"
cp -r "$SRC_DIST" "$DIST_DIR"
chmod -R a+rX "$DIST_DIR" 2>/dev/null || true

# SELinux 重打标签（CentOS 7）
if command -v restorecon >/dev/null 2>&1; then
  restorecon -R "$DIST_DIR" 2>/dev/null || true
fi

echo "==> reload nginx + 启 tanxiaer"
systemctl reload nginx 2>/dev/null || nginx -s reload
systemctl start tanxiaer 2>/dev/null || true

echo ""
echo "✅ 修复完成。请打开 http://<域名>/admin/ 强刷一次（Ctrl+Shift+R）。"
echo "   登录请求应该是 http://<域名>/admin-api/...，不再是 http://admin-api/..."
