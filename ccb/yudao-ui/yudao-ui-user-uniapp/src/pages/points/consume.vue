<template>
  <view class="page">
    <nav-bar :title="shopName ? `${shopName} · 消费积分` : '消费积分明细'" />
    <view class="sum-card">
      <text class="l">{{ shopName ? `${shopName} · 消费积分` : '消费积分（跨店）' }}</text>
      <view class="amt">{{ fen2yuan(balance, false) }} <text class="u">积分</text></view>
      <text class="d">店内消费积分 · 下单可按商户设定比例抵扣</text>
    </view>
    <view v-if="loading && !records.length" class="loading">加载中…</view>
    <empty-state v-else-if="!records.length" title="暂无积分明细" />
    <scroll-view v-else scroll-y class="list" @scrolltolower="loadMore">
      <view v-for="r in records" :key="r.id" class="row">
        <view class="r-ic">{{ iconFor(r.sourceType) }}</view>
        <view class="body">
          <view class="t">{{ labelFor(r.sourceType) }}</view>
          <view class="d">{{ fmtTime(r.createTime) }} · 余额 {{ fen2yuan(r.balanceAfter || 0, false) }} 积分</view>
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
import { getAccount, listConsumeRecords } from '@/api/promo.js';
import { fen2yuan, fmtTime } from '@/utils/format.js';

// 路由参数：tenantId 按店过滤 + shopName 标题
const route = (() => {
  try { const ps = getCurrentPages(); return ps[ps.length - 1]?.options || {}; } catch { return {}; }
})();
const tenantId = route.tenantId ? Number(route.tenantId) : null;
const shopName = route.shopName ? decodeURIComponent(route.shopName) : '';

const balance = ref(0);
const loading = ref(false);
const loadingMore = ref(false);
const records = ref([]);
const page = ref(1);
const total = ref(0);
const hasMore = computed(() => records.value.length < total.value);

function iconFor(t) {
  if (t === 'CONSUME') return '🛒';
  if (t === 'REDEEM')  return '💸';
  if (t === 'CONVERT') return '🔄';
  return '🪙';
}
function labelFor(t) {
  if (t === 'CONSUME') return '消费赠送';
  if (t === 'REDEEM')  return '下单抵扣';
  if (t === 'CONVERT') return '推广积分转入';
  return '消费积分';
}

async function load(reset = true) {
  if (reset) { page.value = 1; records.value = []; }
  loading.value = true;
  try {
    const r = await listConsumeRecords(page.value, 20, tenantId);
    if (r) {
      const list = r.list || [];
      records.value = reset ? list : [...records.value, ...list];
      total.value = r.total || list.length;
    }
  } finally { loading.value = false; }
}
async function loadMore() {
  if (loadingMore.value || !hasMore.value) return;
  loadingMore.value = true;
  page.value++;
  try {
    const r = await listConsumeRecords(page.value, 20, tenantId);
    if (r) records.value.push(...(r.list || []));
  } finally { loadingMore.value = false; }
}

onMounted(async () => {
  try {
    const acct = await getAccount(tenantId);
    balance.value = acct?.consumePointBalance || 0;
  } catch {}
  await load();
});
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background: $bg-2; padding-bottom: 30px; }
.sum-card { margin: 14px; padding: 20px; background: linear-gradient(135deg, $mint, #0E9E6D); color: #fff; border-radius: $r-lg; box-shadow: 0 8px 24px rgba(16,185,129,.3); }
.sum-card .l { font-size: 12px; opacity: .8; }
.sum-card .amt { font-size: 34px; font-weight: 900; margin-top: 4px; }
.sum-card .amt .u { font-size: 14px; font-weight: 700; opacity: .85; margin-left: 4px; }
.sum-card .d { font-size: 11px; opacity: .8; margin-top: 4px; }
.loading { padding: 40px; text-align: center; color: $t4; }
.list { padding-bottom: 20px; }
.row { display: flex; align-items: center; gap: 10px; padding: 12px; background: #fff; margin: 6px 14px; border-radius: $r-md; box-shadow: $sh-1; }
.r-ic { width: 36px; height: 36px; background: $mint-50; color: $mint; border-radius: 10px; display: flex; align-items: center; justify-content: center; font-size: 16px; flex-shrink: 0; }
.body { flex: 1; min-width: 0; }
.t { font-size: 13px; font-weight: 700; color: $t1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.d { font-size: 11px; color: $t3; margin-top: 2px; }
.amt { font-size: 16px; font-weight: 800; color: $mint; flex-shrink: 0; }
.amt.neg { color: $t3; }
.amt .u { font-size: 11px; font-weight: 600; color: $t3; margin-left: 2px; }
.more { padding: 14px; text-align: center; font-size: 12px; color: $t4; }
.more.click { color: $o; }
</style>
