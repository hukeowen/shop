<template>
  <view class="page">
    <nav-bar :title="shopName ? `${shopName} · 推广积分` : '推广积分明细'" />

    <!-- ━━━━━━━ Hero：累计已获（大字）+ 当前余额（小字） ━━━━━━━ -->
    <view class="hero">
      <view class="hero-bg"></view>
      <view class="hero-tag">{{ shopName ? `${shopName} · 推广积分` : '推广积分（跨店）' }}</view>
      <view class="hero-amt">{{ fen2yuan(lifetimeFen, false) }} <text class="unit">积分</text></view>
      <view class="hero-sub">累计已获 · 积分为商户营销凭证 · 兑付由商户独立审批</view>
      <view class="hero-row">
        <view class="hero-stat">
          <text class="v">{{ fen2yuan(promoBalance, false) }} <text class="u">积分</text></text>
          <text class="l">当前可用</text>
        </view>
        <view class="hero-divider"></view>
        <view class="hero-stat">
          <text class="v">{{ fen2yuan(usedPoints, false) }} <text class="u">积分</text></text>
          <text class="l">已抵扣 / 已兑付</text>
        </view>
      </view>
    </view>

    <!-- ━━━━━━━ 明细列表 ━━━━━━━ -->
    <view class="list-title">明细 <text class="cnt">· {{ total || 0 }} 条</text></view>
    <view v-if="loading && !records.length" class="loading">加载中…</view>
    <empty-state v-else-if="!records.length" title="暂无积分明细" desc="逛逛该店参与营销活动获积分" />
    <scroll-view v-else scroll-y class="list" @scrolltolower="loadMore">
      <view v-for="r in records" :key="r.id" class="row">
        <view class="r-ic" :class="`ic-${kindOf(r.sourceType)}`">{{ iconFor(r.sourceType) }}</view>
        <view class="body">
          <view class="t">
            <text class="tt">{{ labelFor(r.sourceType) }}</text>
            <text v-if="spuNameOf(r) && !inTitleAlready" class="spu-chip">{{ spuNameOf(r) }}</text>
          </view>
          <view class="d">
            <text>{{ fmtTime(r.createTime) }}</text>
            <text v-if="r.balanceAfter != null"> · 余额 {{ fen2yuan(r.balanceAfter, false) }} 积分</text>
            <text v-if="explainOf(r)" class="exp"> · {{ explainOf(r) }}</text>
          </view>
        </view>
        <view class="amt" :class="{ neg: r.amount < 0 }">{{ r.amount > 0 ? '+' : '' }}{{ fen2yuan(r.amount, false) }} <text class="u">积分</text></view>
      </view>
      <view v-if="loadingMore" class="more">加载中…</view>
      <view v-else-if="hasMore" class="more click" @click="loadMore">点击加载更多</view>
      <view v-else class="more">— 没有更多了 —</view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { getAccount, listPromoRecords } from '@/api/promo.js';
import { getMyPromoEarned } from '@/api/shop.js';
import { listSpuByIds } from '@/api/product.js';
import { fen2yuan, fmtTime } from '@/utils/format.js';

// 路由参数：tenantId（按店过滤）+ shopName（标题）
const route = (() => {
  try { const ps = getCurrentPages(); return ps[ps.length - 1]?.options || {}; } catch { return {}; }
})();
const tenantId = route.tenantId ? Number(route.tenantId) : null;
const shopName = route.shopName ? decodeURIComponent(route.shopName) : '';

const promoBalance = ref(0);
const lifetimeFen = ref(0);
const usedPoints = computed(() => Math.max(0, lifetimeFen.value - promoBalance.value));

const loading = ref(false);
const loadingMore = ref(false);
const records = ref([]);
const page = ref(1);
const total = ref(0);
const hasMore = computed(() => records.value.length < total.value);

// spuId → name 缓存
const spuNameMap = ref({});
const inTitleAlready = false; // 预留：当 label 已含品名时不再加 chip

// ===== icon / label / kind =====
function iconFor(t) {
  return {
    SELF_BATCH: '🛒',
    ORDER_DEDUCT: '💳',
    POOL_V8: '🏆', POOL: '🏆',
    QUEUE: '💰',
    REFERRAL_PROGRESS: '🎯',
    REFERRAL_COMMISSION: '👥', DIRECT: '👥',
    COMMISSION: '⭐',
    WITHDRAW: '💸',
    CONVERT: '🔄',
    MANUAL_PATCH: '🛠',
    REDEEM_ORDER: '🎟',
  }[t] || '🪙';
}
function labelFor(t) {
  return {
    SELF_BATCH: '购物奖励',
    ORDER_DEDUCT: '下单抵扣',
    POOL_V8: '中奖派发', POOL: '中奖派发',
    QUEUE: '排队中奖',
    REFERRAL_PROGRESS: '推荐奖励',
    REFERRAL_COMMISSION: '分享感谢奖', DIRECT: '邀请有礼',
    COMMISSION: '分享激励',
    WITHDRAW: '提现',
    CONVERT: '兑换消费积分',
    MANUAL_PATCH: '账户调整',
    REDEEM_ORDER: '优惠抵扣',
  }[t] || '推广积分';
}
// 行图标的彩色样式归类（暖橙 / 蓝 / 绿 / 紫）
function kindOf(t) {
  if (['POOL_V8', 'POOL', 'QUEUE'].includes(t)) return 'gold';
  if (['SELF_BATCH', 'REFERRAL_PROGRESS', 'REFERRAL_COMMISSION', 'DIRECT', 'COMMISSION'].includes(t)) return 'orange';
  if (['WITHDRAW', 'CONVERT'].includes(t)) return 'purple';
  if (['ORDER_DEDUCT', 'REDEEM_ORDER'].includes(t)) return 'gray';
  return 'orange';
}

// 从 remark 提取 spuId
function spuIdOf(r) {
  if (!r?.remark) return 0;
  const m = String(r.remark).match(/spu[=\s]?(\d+)/i) || String(r.remark).match(/SPU\s+(\d+)/);
  return m ? Number(m[1]) : 0;
}
function spuNameOf(r) {
  const id = spuIdOf(r);
  return id ? (spuNameMap.value[id] || `商品#${id}`) : '';
}
// 把 remark 里的技术细节翻译成用户能读的话（隐藏 v8/极差/SPU= 等内部术语）
function explainOf(r) {
  if (!r?.remark) return '';
  const s = String(r.remark);
  const m1 = s.match(/count=(\d+)/i);
  const m2 = s.match(/deduct=(\d+)/i);
  const m4 = s.match(/star=(\d+)/i);
  if (r.sourceType === 'SELF_BATCH' && m1) {
    const c = m1[1];
    const d = m2 ? m2[1] : '0';
    return Number(d) > 0 ? `购买 ${c} 件 · ${d} 件已立减` : `购买 ${c} 件`;
  }
  if (r.sourceType === 'ORDER_DEDUCT') return '下单立减';
  if (r.sourceType === 'POOL_V8' || r.sourceType === 'POOL') return '本期奖池派发';
  if (r.sourceType === 'COMMISSION' && m4) return `${m4[1]} 星会员`;
  if (r.sourceType === 'QUEUE') return '当期排队中奖';
  if (r.sourceType === 'REFERRAL_PROGRESS' || r.sourceType === 'REFERRAL_COMMISSION') return '好友下单';
  if (r.sourceType === 'CONVERT') return '兑换为消费积分';
  return '';
}

async function loadSpuNames(list) {
  const ids = new Set();
  list.forEach((r) => { const id = spuIdOf(r); if (id && !spuNameMap.value[id]) ids.add(id); });
  if (!ids.size) return;
  try {
    const arr = await listSpuByIds(Array.from(ids), tenantId);
    if (Array.isArray(arr)) {
      const next = { ...spuNameMap.value };
      arr.forEach((s) => { next[s.id] = s.name; });
      spuNameMap.value = next;
    }
  } catch {}
}

async function load(reset = true) {
  if (reset) { page.value = 1; records.value = []; }
  loading.value = true;
  try {
    const r = await listPromoRecords(page.value, 20, tenantId);
    if (r) {
      const list = r.list || [];
      records.value = reset ? list : [...records.value, ...list];
      total.value = r.total || list.length;
      loadSpuNames(list);
    }
  } finally { loading.value = false; }
}
async function loadMore() {
  if (loadingMore.value || !hasMore.value) return;
  loadingMore.value = true;
  page.value++;
  try {
    const r = await listPromoRecords(page.value, 20, tenantId);
    if (r) {
      const list = r.list || [];
      records.value.push(...list);
      loadSpuNames(list);
    }
  } finally { loadingMore.value = false; }
}

onMounted(async () => {
  // 并行：当前余额（getAccount）+ lifetime 累计（getMyPromoEarned）+ 明细列表
  try {
    const [acct, earn] = await Promise.all([
      getAccount(tenantId).catch(() => null),
      tenantId ? getMyPromoEarned(tenantId).catch(() => null) : Promise.resolve(null),
    ]);
    promoBalance.value = acct?.promoPointBalance || 0;
    lifetimeFen.value = earn?.lifetimeEarnedFen || promoBalance.value;
  } catch {}
  await load();
});
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.page { min-height: 100vh; background: $bg-2; padding-bottom: 40px; }

/* ━━ Hero ━━ */
.hero {
  position: relative;
  margin: 12px 14px 16px;
  padding: 22px 22px 18px;
  border-radius: $r-lg;
  background:
    radial-gradient(420px 220px at 100% 0%, rgba(255,255,255,.18), transparent 60%),
    linear-gradient(135deg, $o 0%, $o-d 100%);
  color: #fff;
  box-shadow: 0 10px 28px rgba(255,107,53,.28);
  overflow: hidden;
}
.hero-bg {
  position: absolute; inset: 0;
  background-image: radial-gradient(rgba(255,255,255,.07) 1px, transparent 1px);
  background-size: 18px 18px;
  pointer-events: none;
}
.hero-tag {
  position: relative;
  display: inline-block;
  padding: 4px 10px;
  border-radius: 99px;
  background: rgba(255,255,255,.18);
  font-size: 11px; font-weight: 700; letter-spacing: .5px;
  backdrop-filter: blur(8px);
}
.hero-amt {
  position: relative;
  margin-top: 10px;
  font-size: 40px; font-weight: 900;
  letter-spacing: -1px;
  font-variant-numeric: tabular-nums;
}
.hero-amt .unit { font-size: 16px; font-weight: 700; opacity: .8; }
.hero-sub {
  position: relative;
  font-size: 11px; opacity: .85; margin-top: 2px;
}
.hero-row {
  position: relative;
  margin-top: 14px;
  display: flex; align-items: center;
  background: rgba(255,255,255,.14);
  border-radius: 14px;
  padding: 10px 0;
  backdrop-filter: blur(10px);
}
.hero-stat { flex: 1; display: flex; flex-direction: column; align-items: center; }
.hero-stat .v { font-size: 16px; font-weight: 800; font-variant-numeric: tabular-nums; }
.hero-stat .v .u { font-size: 10px; font-weight: 600; opacity: .85; margin-left: 2px; }
.hero-stat .l { font-size: 10.5px; opacity: .85; margin-top: 2px; }
.hero-divider { width: 1px; height: 26px; background: rgba(255,255,255,.25); }

/* ━━ 列表 ━━ */
.list-title {
  margin: 4px 16px 8px;
  font-size: 13px; font-weight: 800; color: $t1;
}
.list-title .cnt { color: $t4; font-weight: 600; }

.loading { padding: 40px; text-align: center; color: $t4; }
.list { padding-bottom: 20px; }

.row {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 14px;
  background: #fff;
  margin: 6px 14px;
  border-radius: $r-md;
  box-shadow: 0 1px 2px rgba(15,23,42,.04), 0 4px 12px rgba(15,23,42,.04);
  transition: transform .15s ease;
}
.row:active { transform: scale(.99); }
.r-ic {
  width: 40px; height: 40px;
  border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}
.r-ic.ic-gold   { background: linear-gradient(135deg, #FFE3A0, #FFB347); color: #6B3F00; }
.r-ic.ic-orange { background: linear-gradient(135deg, $o-100, $o-50); color: $o-d; }
.r-ic.ic-purple { background: linear-gradient(135deg, #EDE9FE, #DDD6FE); color: #6D28D9; }
.r-ic.ic-gray   { background: #F1F5F9; color: $t3; }

.body { flex: 1; min-width: 0; }
.t {
  display: flex; align-items: center; gap: 6px;
  font-size: 13.5px; font-weight: 700; color: $t1;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.tt { flex-shrink: 0; }
.spu-chip {
  display: inline-block;
  padding: 1px 7px;
  background: $o-50;
  color: $o-d;
  font-size: 11px;
  font-weight: 700;
  border-radius: 999px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  max-width: 50%;
}
.d {
  margin-top: 4px;
  font-size: 11px; color: $t3;
  font-variant-numeric: tabular-nums;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.d .exp { color: $t2; }

.amt {
  font-size: 16px; font-weight: 800; color: $o;
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}
.amt.neg { color: $t3; }
.amt .u { font-size: 11px; font-weight: 600; color: $t3; margin-left: 2px; }

.more { padding: 18px; text-align: center; font-size: 12px; color: $t4; }
.more.click { color: $o; font-weight: 700; }
</style>
