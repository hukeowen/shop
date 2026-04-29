#!/usr/bin/env bash
# =============================================================================
# 一键修复：CentOS 7 SELinux 拦 nginx 读 /opt/tanxiaer/m/ 静态文件 → 404
#
# 现象：HTML 加载到，但 /m/assets/*.js / *.css 全部 404，浏览器显示空白
# 根因：root 用户 cp -r 出来的文件 SELinux 标签是 user_home_t，nginx (httpd_t)
#       默认不允许读 user_home_t；setenforce 0 是当前会话临时切换，重启失效
# 修法：semanage fcontext + restorecon 把 /opt/tanxiaer 标成 httpd_sys_content_t
#       即使 SELinux=Enforcing 也能读
#
# 用法（root）：
#   bash /opt/tanxiaer/repo/ccb/scripts/fix-selinux-static.sh
# =============================================================================
set -euo pipefail

ROOT_DIR="${ROOT_DIR:-/opt/tanxiaer}"
GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log()  { echo -e "${GREEN}[✓]${NC} $*"; }
info() { echo -e "${BLUE}[i]${NC} $*"; }
warn() { echo -e "${YELLOW}[!]${NC} $*"; }
err()  { echo -e "${RED}[✗]${NC} $*" >&2; }

# 必须 root
if [[ $EUID -ne 0 ]]; then
  err "必须以 root 运行：sudo bash $0"
  exit 1
fi

# ── 1. 检测当前 SELinux 状态 ─────────────────────────────────────
info "检测 SELinux 状态..."
if ! command -v getenforce &>/dev/null; then
  warn "未装 SELinux 工具链 — 跳过"
  exit 0
fi
SELINUX_STATE="$(getenforce)"
log "当前 SELinux 状态: ${SELINUX_STATE}"

# ── 2. 装 policycoreutils-python（提供 semanage） ────────────────
if ! command -v semanage &>/dev/null; then
  info "安装 policycoreutils-python（提供 semanage 命令）..."
  yum install -y policycoreutils-python >/dev/null 2>&1 \
    || { err "policycoreutils-python 安装失败"; exit 1; }
fi
log "semanage 工具就位"

# ── 3. 给 /opt/tanxiaer 打 httpd_sys_content_t 标签 ──────────────
info "给 ${ROOT_DIR} 打 SELinux 标签 httpd_sys_content_t..."
# 先加规则（如果已存在 semanage 会报 already defined，用 -m 修改更稳）
if semanage fcontext -l 2>/dev/null | grep -q "^${ROOT_DIR}(/\.\*)?"; then
  semanage fcontext -m -t httpd_sys_content_t "${ROOT_DIR}(/.*)?"
  log "标签规则已更新"
else
  semanage fcontext -a -t httpd_sys_content_t "${ROOT_DIR}(/.*)?"
  log "标签规则已添加"
fi

# 应用到所有现有文件
info "restorecon -Rv ${ROOT_DIR}（应用到磁盘上所有文件，可能需要几秒）..."
restorecon -Rv "${ROOT_DIR}" >/tmp/restorecon.log 2>&1 \
  && log "restorecon 完成（详见 /tmp/restorecon.log）" \
  || { err "restorecon 失败 — 看 /tmp/restorecon.log"; exit 1; }

# ── 4. 验证标签生效 ─────────────────────────────────────────────
info "验证关键文件的 SELinux 标签..."
for f in "${ROOT_DIR}/m/index.html" "${ROOT_DIR}/admin-dist/index.html" "${ROOT_DIR}/website/index.html"; do
  if [[ -f "$f" ]]; then
    LABEL="$(ls -lZ "$f" 2>/dev/null | awk '{print $4}')"
    if [[ "$LABEL" == *"httpd_sys_content_t"* ]]; then
      log "$f → $LABEL"
    else
      warn "$f → $LABEL（未含 httpd_sys_content_t）"
    fi
  fi
done

# ── 5. 重载 nginx 让它重新打开文件句柄 ─────────────────────────
if systemctl is-active --quiet nginx; then
  info "重载 nginx..."
  nginx -t && nginx -s reload && log "nginx 已重载"
else
  warn "nginx 未运行，跳过 reload"
fi

# ── 6. 立即冒烟测试 ─────────────────────────────────────────────
echo ""
info "冒烟测试：curl /m/ 关键资源..."
SAMPLE_JS=$(ls "${ROOT_DIR}/m/assets/"index-*.js 2>/dev/null | head -1 | xargs -I{} basename {})
if [[ -n "$SAMPLE_JS" ]]; then
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1/m/assets/${SAMPLE_JS}")
  if [[ "$STATUS" == "200" ]]; then
    log "/m/assets/${SAMPLE_JS} → HTTP $STATUS ✓ (修复成功)"
  else
    err "/m/assets/${SAMPLE_JS} → HTTP $STATUS ✗ (问题未根治)"
    err "排查：sudo tail -30 /var/log/nginx/error.log"
    exit 1
  fi
else
  warn "未找到 ${ROOT_DIR}/m/assets/index-*.js — H5 可能未部署"
fi

echo ""
echo -e "${GREEN}═══════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  ✅ SELinux 修复完成${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════${NC}"
echo "  现在浏览器访问 http://<your-ip>/m/ 应该能正常显示 H5"
echo "  修复持久化：即使 SELinux=Enforcing 也能读 ${ROOT_DIR}/"
echo "  下次 ./deploy.sh 也会自动跑同样的标签设置（已合并入 configure_selinux）"
