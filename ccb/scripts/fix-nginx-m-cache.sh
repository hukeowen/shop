#!/usr/bin/env bash
# =============================================================================
#  止损脚本：修 nginx 给 /m/index.html 加 no-cache，避免升级后浏览器
#  拿旧 index.html 引用已被删除的旧 chunk → 404。
#
#  现象（修前）:
#    GET /m/assets/pages-index-index.CVkkROz-.js 404
#    GET /m/assets/aiVideo.U38doG-K.js 404
#
#  适用：已用 deploy.sh 装好 nginx，但还没跑过最新 deploy.sh 升级。
#       用 sudo 直接在 ECS 上跑这个脚本即可，不需要重新拉代码 / 重 build。
#
#  幂等：可重复执行；已生效会跳过。
# =============================================================================
set -euo pipefail

CONF="${1:-/etc/nginx/conf.d/tanxiaer.conf}"
ROOT_DIR="${ROOT_DIR:-/opt/tanxiaer}"

if [[ ! -f "$CONF" ]]; then
  echo "❌ 找不到 nginx 配置：$CONF"
  echo "   常见位置：/etc/nginx/conf.d/tanxiaer.conf、/etc/nginx/sites-enabled/tanxiaer.conf"
  echo "   用法：sudo bash fix-nginx-m-cache.sh /path/to/your.conf"
  exit 1
fi

echo "==> 备份当前配置 → ${CONF}.bak.$(date +%s)"
cp -p "$CONF" "${CONF}.bak.$(date +%s)"

if grep -q 'location = /m/index.html' "$CONF"; then
  echo "ℹ  已存在 /m/index.html no-cache 块，跳过 patch"
else
  echo "==> 在 ^~ /m/ 块之前插入 no-cache 配置"
  # 找到 "    location ^~ /m/ {" 这一行，在其前面插入新块
  python3 - "$CONF" <<'PY'
import sys, re
path = sys.argv[1]
text = open(path, 'r', encoding='utf-8').read()

NEW_BLOCK = '''
    # /m/index.html 入口禁缓存：升级 dist 后浏览器立刻拿新 hash 的 chunk 引用
    location = /m/index.html {
        alias ${ROOT_DIR}/m/index.html;
        add_header Cache-Control "no-cache, no-store, must-revalidate";
        add_header Pragma "no-cache";
        add_header Expires 0;
    }
'''.lstrip('\n')

# 用环境变量替换 ${ROOT_DIR}
import os
NEW_BLOCK = NEW_BLOCK.replace('${ROOT_DIR}', os.environ.get('ROOT_DIR', '/opt/tanxiaer'))

# 在 "location ^~ /m/ {" 之前插入
patched = re.sub(
    r'(\s+)(location \^~ /m/ \{)',
    r'\n' + NEW_BLOCK + r'\1\2',
    text,
    count=1,
)

if patched == text:
    print('!! 未找到 location ^~ /m/ 块，patch 失败', file=sys.stderr)
    sys.exit(2)

# 同时确保 /m/assets/ 块在 /m/ 之前（精确度）。如果顺序已对就不动。
open(path, 'w', encoding='utf-8').write(patched)
print('✓ patched')
PY
fi

echo "==> 校验 nginx 语法"
nginx -t

echo "==> reload nginx"
systemctl reload nginx 2>/dev/null || nginx -s reload

echo ""
echo "✅ 修复完成。"
echo ""
echo "用户侧请清缓存或强刷一次："
echo "  · PC：Ctrl+Shift+R"
echo "  · 手机微信内置浏览器：右上角 ⋯ → 清除缓存 / 关掉再打开"
echo "  · iOS Safari：设置 → Safari → 清除历史记录与网站数据"
echo ""
echo "如要恢复，cp ${CONF}.bak.* 回 ${CONF} 然后 nginx -s reload。"
