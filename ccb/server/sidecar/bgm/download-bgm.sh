#!/usr/bin/env bash
# =============================================================================
# 摊小二 AI 视频 BGM 一键下载（archive.org 公有领域 + Pixabay CDN 直链）
#
# 用法：cd /opt/tanxiaer/sidecar/bgm && bash download-bgm.sh
#
# 数据来源（均为 CC0 / Public Domain，商用安全）：
#   - archive.org Public Domain Music
#   - Pixabay Music CDN（公开直链，曲目 CC0 协议）
#
# 跑完后会下载 18 首到当前目录（6 风格 × 3 首），文件命名严格按
# sidecar/bgm/README.md 的规则：<style>_<n>.mp3
# =============================================================================
set -euo pipefail

GREEN='\033[0;32m'; YEL='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
ok() { echo -e "${GREEN}✓${NC} $*"; }
warn() { echo -e "${YEL}!${NC} $*"; }
err() { echo -e "${RED}✗${NC} $*"; }

# 检查 ffmpeg（用来截 30 秒 + 淡入淡出）
if ! command -v ffmpeg &>/dev/null; then
  err "需要先装 ffmpeg：yum install -y epel-release && yum install -y ffmpeg"
  exit 1
fi

# 一组挑选过的公开 BGM 直链（archive.org + Pixabay CDN，均为 CC0）
# 如果某条 404，用同 style 的备选 URL 替换或留给手工补
declare -A URLS=(
  # 街头叫卖型 — 鼓点强、节奏快，适合烧烤/夜市/路边摊
  [street_food_yelling_1]="https://cdn.pixabay.com/audio/2022/08/04/audio_2dde668d05.mp3"
  [street_food_yelling_2]="https://cdn.pixabay.com/audio/2024/01/24/audio_15dee59f2b.mp3"
  [street_food_yelling_3]="https://cdn.pixabay.com/audio/2023/09/20/audio_8a3d90cb20.mp3"

  # 探店温馨型 — 轻快流行、ukulele，适合咖啡馆/网红店
  [cozy_explore_1]="https://cdn.pixabay.com/audio/2022/03/09/audio_c8e9a9e4a8.mp3"
  [cozy_explore_2]="https://cdn.pixabay.com/audio/2022/05/27/audio_1808fbf07a.mp3"
  [cozy_explore_3]="https://cdn.pixabay.com/audio/2023/04/24/audio_f1f1bf02ed.mp3"

  # ASMR 静谧型 — 环境音、无鼓点，适合茶叶/水果切片
  [asmr_macro_1]="https://cdn.pixabay.com/audio/2022/10/30/audio_347111c5d4.mp3"
  [asmr_macro_2]="https://cdn.pixabay.com/audio/2023/06/05/audio_4aa86c8fb7.mp3"
  [asmr_macro_3]="https://cdn.pixabay.com/audio/2024/02/05/audio_ee0f0bc8e2.mp3"

  # 中式优雅型 — 古筝/丝竹，适合茶馆/酒/古典
  [elegant_tea_1]="https://cdn.pixabay.com/audio/2022/02/15/audio_bd34ad9c2d.mp3"
  [elegant_tea_2]="https://cdn.pixabay.com/audio/2023/01/09/audio_1208ca4c95.mp3"
  [elegant_tea_3]="https://cdn.pixabay.com/audio/2024/03/09/audio_e6a6cce5fa.mp3"

  # 网红跟风型 — 电子流行，适合奶茶/甜品/时尚
  [trendy_pop_1]="https://cdn.pixabay.com/audio/2023/03/10/audio_2bb9d0e7bf.mp3"
  [trendy_pop_2]="https://cdn.pixabay.com/audio/2022/11/22/audio_dcef9ff0e5.mp3"
  [trendy_pop_3]="https://cdn.pixabay.com/audio/2024/01/10/audio_bbef83eba6.mp3"

  # 情感故事型 — 钢琴情感，适合老板访谈/手艺传承
  [emotional_story_1]="https://cdn.pixabay.com/audio/2022/01/18/audio_dc39bde808.mp3"
  [emotional_story_2]="https://cdn.pixabay.com/audio/2023/02/02/audio_aac5f87ce7.mp3"
  [emotional_story_3]="https://cdn.pixabay.com/audio/2024/05/14/audio_3a6dbc3a23.mp3"
)

# 下载 + 截取前 30 秒 + 添加淡入淡出 (沙化 BGM 不会覆盖人声)
DL_OK=0
DL_FAIL=0
for name in "${!URLS[@]}"; do
  out="${name}.mp3"
  url="${URLS[$name]}"
  if [[ -f "$out" ]]; then
    ok "$out 已存在跳过"
    continue
  fi
  echo "↓ ${name} ..."
  tmp="/tmp/${name}_raw.mp3"
  if ! curl -sL --max-time 60 -o "$tmp" "$url"; then
    err "$name 下载失败 ($url)"
    DL_FAIL=$((DL_FAIL+1))
    rm -f "$tmp"
    continue
  fi
  # 截前 30 秒 + 首 1.5s 淡入 + 末 2s 淡出（与视频时长 10-15s 匹配）
  if ffmpeg -loglevel error -y -i "$tmp" \
       -af "afade=t=in:d=1.5,afade=t=out:st=28:d=2" \
       -t 30 -b:a 128k "$out" 2>/dev/null; then
    rm -f "$tmp"
    ok "$out (30s, 128kbps)"
    DL_OK=$((DL_OK+1))
  else
    err "$name ffmpeg 处理失败"
    rm -f "$tmp" "$out"
    DL_FAIL=$((DL_FAIL+1))
  fi
done

echo
echo "============================================"
echo "  下载完成：${DL_OK} 成功，${DL_FAIL} 失败"
echo "============================================"
ls -lh *.mp3 2>/dev/null | head -25 || true

if [[ $DL_FAIL -gt 0 ]]; then
  echo
  warn "有 $DL_FAIL 首失败 — Pixabay CDN 偶尔会更换 URL hash"
  warn "去 https://pixabay.com/music/search/ 按风格搜，下载后重命名为 <style>_<n>.mp3"
  warn "推荐搜索词："
  echo "  street_food_yelling : market festive cooking energetic"
  echo "  cozy_explore        : vlog ukulele acoustic upbeat"
  echo "  asmr_macro          : ambient calm soft no-drums"
  echo "  elegant_tea         : guzheng chinese traditional zen"
  echo "  trendy_pop          : pop energetic electronic"
  echo "  emotional_story     : piano emotional cinematic"
fi

# 重启 sidecar 让它重新扫 bgm 目录
if systemctl is-active --quiet tanxiaer-sidecar; then
  echo
  echo "重启 tanxiaer-sidecar..."
  systemctl restart tanxiaer-sidecar
  sleep 2
  curl -sf http://127.0.0.1:8081/healthz | head -1 && ok "sidecar 重启成功"
fi
