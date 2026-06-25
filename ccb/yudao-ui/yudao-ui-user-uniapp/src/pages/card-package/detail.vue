<template>
  <view class="page">
    <nav-bar title="卡详情" />

    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!card" icon="🎫" title="未找到该卡" desc="可能已失效" />

    <view v-else class="wrap">
      <!-- 卡票 -->
      <view class="ticket" :class="'st-' + card.effectiveStatus">
        <view class="t-head">
          <text class="t-shop">{{ card.shopName }}</text>
          <text class="t-badge" :class="'b-' + card.effectiveStatus">{{ statusText(card.effectiveStatus) }}</text>
        </view>
        <text class="t-name">{{ card.name }}</text>
        <view class="t-sub">
          <text>{{ card.unlimited ? '不限次数' : ('剩 ' + card.remainCount + ' / ' + card.maxCount + ' 次') }}</text>
          <text class="sep">|</text>
          <text>{{ fmtDate(card.expireTime) }} 到期</text>
        </view>

        <!-- 出示码 -->
        <view class="qr-box" :class="{ dim: card.effectiveStatus !== 'ACTIVE' }">
          <canvas canvas-id="qrCanvas" id="qrCanvas" class="qr-canvas"></canvas>
          <view v-if="card.effectiveStatus !== 'ACTIVE'" class="qr-mask">{{ statusText(card.effectiveStatus) }}</view>
        </view>
        <view class="card-no">{{ groupedNo }}</view>
        <view class="qr-tip">{{ card.effectiveStatus === 'ACTIVE' ? '向商家出示此码进行核销' : '该卡已不可核销' }}</view>
      </view>

      <!-- 说明 -->
      <view v-if="card.description" class="desc-card">
        <text class="desc-h">使用说明</text>
        <text class="desc-b">{{ card.description }}</text>
      </view>

      <!-- 信息 -->
      <view class="info-card">
        <view class="row"><text class="lbl">生效时间</text><text class="val">{{ fmtDateTime(card.startTime) }}</text></view>
        <view class="row"><text class="lbl">有效期至</text><text class="val">{{ fmtDateTime(card.expireTime) }}</text></view>
        <view class="row"><text class="lbl">已核销</text><text class="val">{{ card.usedCount }} 次{{ card.unlimited ? '' : (' / 共 ' + card.maxCount + ' 次') }}</text></view>
      </view>

      <!-- 核销记录 -->
      <view class="rec-h">核销记录</view>
      <view v-if="!(card.verifyRecords && card.verifyRecords.length)" class="rec-empty">暂无核销记录</view>
      <view v-else class="rec-list">
        <view v-for="(r, i) in card.verifyRecords" :key="i" class="rec">
          <text class="rec-time">{{ fmtDateTime(r.verifyTime) }}</text>
          <text class="rec-cnt">第 {{ r.countAfter }} 次{{ r.remark ? ' · ' + r.remark : '' }}</text>
        </view>
      </view>
    </view>
    <view class="bottom-pad"></view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import qrcode from 'qrcode-generator';
import { getMyCard } from '@/api/card.js';

const loading = ref(true);
const card = ref(null);

const groupedNo = computed(() => {
  const no = card.value && card.value.cardNo ? String(card.value.cardNo) : '';
  return no.replace(/(.{4})/g, '$1 ').trim();
});

function statusText(s) {
  return { ACTIVE: '可用', USED_UP: '已用完', EXPIRED: '已过期' }[s] || s;
}
function pad(n) { return n < 10 ? '0' + n : '' + n; }
function fmtDate(ms) {
  if (!ms) return '—';
  const d = new Date(ms);
  return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())}`;
}
function fmtDateTime(ms) {
  if (!ms) return '—';
  const d = new Date(ms);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

// 跨端画二维码（uni.createCanvasContext，H5 + 小程序都可用）
function drawQr(text) {
  try {
    const qr = qrcode(0, 'M');
    qr.addData(String(text || ''));
    qr.make();
    const count = qr.getModuleCount();
    const size = 220;
    const cell = size / count;
    const ctx = uni.createCanvasContext('qrCanvas');
    ctx.setFillStyle('#ffffff');
    ctx.fillRect(0, 0, size, size);
    ctx.setFillStyle('#18130E');
    for (let r = 0; r < count; r++) {
      for (let c = 0; c < count; c++) {
        if (qr.isDark(r, c)) {
          ctx.fillRect(Math.floor(c * cell), Math.floor(r * cell), Math.ceil(cell) + 1, Math.ceil(cell) + 1);
        }
      }
    }
    ctx.draw();
  } catch (e) { /* 画码失败不影响数字码展示 */ }
}

async function load(id) {
  loading.value = true;
  try {
    card.value = await getMyCard(id);
    // 渲染后再画码（等 canvas 上屏）
    setTimeout(() => { if (card.value && card.value.cardNo) drawQr(card.value.cardNo); }, 120);
  } catch (e) {
    card.value = null;
    uni.showToast({ title: e?.message || '加载失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
}

onLoad((q) => { if (q && q.id) load(q.id); else loading.value = false; });
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.page { min-height: 100vh; background: $bg-2; padding-bottom: 30px; }
.loading { padding: 40px; text-align: center; color: $t4; }
.wrap { padding: 14px 14px 0; }

/* 卡票 */
.ticket {
  position: relative; overflow: hidden;
  background: linear-gradient(150deg, #1B1208 0%, #2C1B0E 55%, #3A2410 100%);
  border-radius: 18px; padding: 20px 18px 22px; color: #fff;
}
.ticket.st-USED_UP, .ticket.st-EXPIRED { background: linear-gradient(150deg, #3a3a3a, #565656); }
.t-head { display: flex; align-items: center; justify-content: space-between; }
.t-shop { font-size: 13px; color: rgba(255,255,255,.8); }
.t-badge { font-size: 11px; font-weight: 700; padding: 2px 9px; border-radius: 999px; }
.t-badge.b-ACTIVE { color: #18130E; background: $gold-l; }
.t-badge.b-USED_UP, .t-badge.b-EXPIRED { color: #fff; background: rgba(255,255,255,.25); }
.t-name { display: block; margin-top: 8px; font-size: 26px; font-weight: 900; color: #fff; letter-spacing: .5px; }
.t-sub { margin-top: 6px; font-size: 12.5px; color: rgba(255,255,255,.78); }
.t-sub .sep { margin: 0 8px; color: rgba(255,255,255,.35); }

.qr-box {
  position: relative; width: 236px; height: 236px; margin: 18px auto 12px;
  background: #fff; border-radius: 14px; display: flex; align-items: center; justify-content: center;
}
.qr-box.dim { opacity: .55; }
.qr-canvas { width: 220px; height: 220px; }
.qr-mask {
  position: absolute; left: 50%; top: 50%; transform: translate(-50%,-50%);
  background: rgba(0,0,0,.62); color: #fff; font-size: 15px; font-weight: 800;
  padding: 6px 18px; border-radius: 8px;
}
.card-no { text-align: center; font-size: 18px; font-weight: 800; letter-spacing: 2px; color: $gold-l; font-family: monospace; }
.qr-tip { text-align: center; margin-top: 8px; font-size: 12px; color: rgba(255,255,255,.7); }

/* 说明 / 信息 */
.desc-card, .info-card { background: #fff; border-radius: 14px; margin-top: 12px; padding: 14px 16px; box-shadow: $sh-1; }
.desc-h { display: block; font-size: 13px; font-weight: 800; color: $t1; margin-bottom: 6px; }
.desc-b { font-size: 13px; color: $t2; line-height: 1.6; }
.row { display: flex; justify-content: space-between; align-items: center; padding: 8px 0; font-size: 13px; }
.row .lbl { color: $t3; }
.row .val { color: $t1; font-weight: 600; }

.rec-h { margin: 18px 4px 8px; font-size: 14px; font-weight: 800; color: $t1; }
.rec-empty { text-align: center; color: $t4; font-size: 12px; padding: 18px; }
.rec-list { background: #fff; border-radius: 14px; padding: 4px 16px; box-shadow: $sh-1; }
.rec { display: flex; justify-content: space-between; align-items: center; padding: 11px 0; border-bottom: 1px solid $line; font-size: 12.5px; }
.rec:last-child { border-bottom: none; }
.rec-time { color: $t2; }
.rec-cnt { color: $o-d; font-weight: 700; }
.bottom-pad { height: 24px; }
</style>
