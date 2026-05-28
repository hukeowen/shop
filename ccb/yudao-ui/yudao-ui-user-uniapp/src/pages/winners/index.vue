<template>
  <view class="page">
    <view class="hero">
      <view class="hero-title">
        <text class="trophy">🏆</text>
        <text class="t">商户让利公告</text>
        <text class="sub">商户营销让利活动 · 提现由商户审批</text>
      </view>
      <view class="tabs">
        <view class="tab" :class="{ on: tab === 'live' }" @click="switchTab('live')">🔴 实时中奖</view>
        <view class="tab" :class="{ on: tab === 'rank' }" @click="goRank">📊 榜一排名</view>
      </view>
      <view v-if="todayAmt" class="hero-stat">今日全网商户让利 <text class="hl">¥{{ todayAmt }}</text> · {{ todayCnt }} 笔</view>
    </view>

    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!winners.length" icon="🪙" title="暂无让利记录" desc="去店铺下单参与商户让利活动" />
    <view v-else class="list">
      <view v-for="(w, i) in winners" :key="w.id" class="row" :class="rankRowClass(i)" @click="goSource(w)">
        <view class="rank" :class="rankClass(i)">
          <text v-if="i === 0" class="crown">👑</text>
          <text v-else-if="i === 1" class="crown">🥈</text>
          <text v-else-if="i === 2" class="crown">🥉</text>
          <text v-else class="rank-n">{{ i + 1 }}</text>
        </view>
        <view class="row-body">
          <view class="row-t1">
            <text class="shop">{{ w.shopName || '某店铺' }}</text>
            让利给 <text class="phone">{{ w.userMask || '****' }}</text>
          </view>
          <view class="row-t2">{{ labelFor(w.sourceType) }} · {{ fmtTime(w.createTime) }}</view>
        </view>
        <view class="row-amt-block">
          <text class="row-amt">+¥{{ fen2yuan(w.amount, false) }}</text>
          <text class="row-cta">看商品 ›</text>
        </view>
      </view>
      <view class="more">— Top {{ winners.length }} ·  滑到底了 —</view>
    </view>
    <view class="bottom-pad"></view>
    <bottom-nav active="winners" />
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { listWinners, getTodayStat } from '@/api/promo.js';
import { fen2yuan, fmtTime } from '@/utils/format.js';

const tab = ref('live');
const loading = ref(false);
const winners = ref([]);
const todayAmt = ref('');
const todayCnt = ref(0);

function iconFor(t) {
  return {
    POOL: '🏆', POOL_V8: '🏆',
    QUEUE: '💰',
    SELF_BATCH: '🛒',
    REFERRAL_PROGRESS: '🎯',
    REFERRAL_COMMISSION: '👥', DIRECT: '👥',
    COMMISSION: '⭐',
  }[t] || '🎁';
}
function labelFor(t) {
  return {
    POOL: '中奖派发', POOL_V8: '中奖派发',
    QUEUE: '排队中奖',
    SELF_BATCH: '购物奖励',
    REFERRAL_PROGRESS: '分享感谢奖',
    REFERRAL_COMMISSION: '分享感谢奖', DIRECT: '邀请有礼',
    COMMISSION: '分享激励',
  }[t] || '推广奖励';
}
function rankClass(i) {
  if (i === 0) return 'r1';
  if (i === 1) return 'r2';
  if (i === 2) return 'r3';
  return '';
}
function rankRowClass(i) {
  if (i === 0) return 'row-top1';
  if (i === 1) return 'row-top2';
  if (i === 2) return 'row-top3';
  return '';
}

// 从 remark 提 spuId（"SPU 99014 ..." 或 "spu=99014"）
function spuIdOf(w) {
  if (!w?.remark) return 0;
  const m = String(w.remark).match(/spu[=\s]?(\d+)/i) || String(w.remark).match(/SPU\s+(\d+)/);
  return m ? Number(m[1]) : 0;
}
// 点中奖行 → 跳商品详情（带 tenantId + spuId）；无 spuId 则跳店铺主页
function goSource(w) {
  const sid = spuIdOf(w);
  const tid = w.tenantId;
  if (sid && tid) {
    uni.navigateTo({ url: `/pages/product/detail?id=${sid}&tenantId=${tid}` });
  } else if (tid) {
    uni.navigateTo({ url: `/pages/shop/home?id=${tid}&tenantId=${tid}` });
  }
}

function switchTab(k) { tab.value = k; load(); }
function goRank() { uni.navigateTo({ url: '/pages/rank/index' }); }

// 从路由读 tenantId — 从店铺主页 ticker 点过来会带，过滤本店派奖
const routeTenantId = (() => {
  try { const ps = getCurrentPages(); return ps[ps.length - 1]?.options?.tenantId || null; } catch { return null; }
})();

async function load() {
  loading.value = true;
  try {
    winners.value = await listWinners(routeTenantId, 100) || [];
  } catch { winners.value = []; }
  finally { loading.value = false; }
  try {
    const stat = await getTodayStat();
    if (stat) {
      todayAmt.value = fen2yuan(stat.promoAmountToday || 0, false);
      todayCnt.value = stat.awardCountToday || 0;
    }
  } catch {}
}
onMounted(load);
onShow(load);
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; padding-bottom: 90px; background: $bg; }
.hero {
  padding: 20px 14px 14px;
  background:
    radial-gradient(400px 200px at 50% 0%, rgba(212,146,10,.3), transparent 60%),
    linear-gradient(180deg, #18130E 0%, #2A1A0F 100%);
  text-align: center;
  border-bottom-left-radius: 24px; border-bottom-right-radius: 24px;
}
.hero-title { display: flex; flex-direction: column; align-items: center; gap: 2px; }
.trophy { font-size: 32px; }
.t {
  font-size: 22px; font-weight: 900;
  background: linear-gradient(135deg, #fff, #FFD8C0, $gold-l);
  -webkit-background-clip: text; background-clip: text; color: transparent;
}
.sub { color: rgba(255,255,255,.55); font-size: 11px; }
.tabs { display: flex; gap: 6px; margin-top: 14px; justify-content: center; }
.tab {
  padding: 8px 18px; border-radius: $r-pill;
  background: rgba(255,255,255,.1); color: rgba(255,255,255,.7);
  font-size: 12px; font-weight: 700;
  &.on {
    background: linear-gradient(135deg, $gold, $gold-d);
    color: #fff; box-shadow: $sh-gold;
  }
}
.hero-stat { margin-top: 12px; color: rgba(255,255,255,.7); font-size: 11px; }
.hero-stat .hl { color: $gold-l; font-weight: 800; }
.loading { padding: 40px; text-align: center; color: $t4; }
.list { padding: 12px 14px; }
.row {
  display: flex; align-items: center; gap: 10px;
  background: $card; border-radius: $r-md;
  padding: 12px; margin-bottom: 8px;
  box-shadow: $sh-1;
  transition: transform .15s ease;
}
.row:active { transform: scale(.99); }
/* Top 3 行整体高亮：暖橙渐变描边 + 浅金底 */
.row-top1 {
  background: linear-gradient(135deg, #FFF8E7, #FFEFD0) !important;
  border: 1.5px solid #F0B400;
  box-shadow: 0 4px 14px rgba(240,180,0,.25), 0 0 0 4px rgba(240,180,0,.06) !important;
}
.row-top2 {
  background: linear-gradient(135deg, #F8FAFC, #E5E7EB) !important;
  border: 1.5px solid #9CA3AF;
}
.row-top3 {
  background: linear-gradient(135deg, #FEF3C7, #FDE68A) !important;
  border: 1.5px solid #D97706;
}
.rank {
  width: 36px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 14px; font-weight: 800; color: $t3;
  font-variant-numeric: tabular-nums;
}
.rank .crown { font-size: 26px; line-height: 1; }
.rank .rank-n { font-size: 14px; }
.row-body { flex: 1; min-width: 0; }
.row-t1 { font-size: 13px; color: $t1; font-weight: 700; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.row-t1 .shop { color: $t1; }
.row-t1 .phone { color: $o; font-weight: 700; }
.row-t2 { font-size: 11px; color: $t3; margin-top: 2px; }
.row-amt-block {
  display: flex; flex-direction: column; align-items: flex-end;
  flex-shrink: 0; gap: 2px;
}
.row-amt {
  font-size: 18px; font-weight: 900;
  background: linear-gradient(135deg, $gold, $gold-d);
  -webkit-background-clip: text; background-clip: text; color: transparent;
  font-variant-numeric: tabular-nums;
}
.row-cta {
  font-size: 10px; color: $o; font-weight: 700;
  padding: 2px 6px; border-radius: 99px;
  background: $o-50;
}
.more { padding: 18px; text-align: center; font-size: 11px; color: $t4; }
.bottom-pad { height: 30px; }
</style>
