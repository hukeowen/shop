<template>
  <view class="page">
    <nav-bar :title="shopName ? `${shopName} · 推广积分` : '推广积分明细'" />
    <view class="sum-card">
      <text class="l">{{ shopName ? `${shopName} · 推广积分` : '推广积分（跨店）' }}</text>
      <view class="amt">¥{{ balanceYuan }}</view>
      <text class="d">1 推广积分 = ¥0.01 · 1:1 现金提现</text>
    </view>
    <view v-if="loading && !records.length" class="loading">加载中…</view>
    <empty-state v-else-if="!records.length" title="暂无积分明细" />
    <scroll-view v-else scroll-y class="list" @scrolltolower="loadMore">
      <view v-for="r in records" :key="r.id" class="row">
        <view class="r-ic">{{ iconFor(r.sourceType) }}</view>
        <view class="body">
          <view class="t">{{ r.remark || labelFor(r.sourceType) }}</view>
          <view class="d">{{ fmtTime(r.createTime) }} · 余额 ¥{{ fen2yuan(r.balanceAfter || 0, false) }}</view>
        </view>
        <view class="amt" :class="{ neg: r.amount < 0 }">{{ r.amount > 0 ? '+' : '' }}¥{{ fen2yuan(r.amount, false) }}</view>
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
import { fen2yuan, fmtTime } from '@/utils/format.js';

// 路由参数：tenantId（按店过滤）+ shopName（标题显示）
const route = (() => {
  try { const ps = getCurrentPages(); return ps[ps.length - 1]?.options || {}; } catch { return {}; }
})();
const tenantId = route.tenantId ? Number(route.tenantId) : null;
const shopName = route.shopName ? decodeURIComponent(route.shopName) : '';

const promoBalance = ref(0);
const balanceYuan = computed(() => fen2yuan(promoBalance.value, false));
const loading = ref(false);
const loadingMore = ref(false);
const records = ref([]);
const page = ref(1);
const total = ref(0);
const hasMore = computed(() => records.value.length < total.value);

function iconFor(t) {
  if (t === 'POOL')       return '🏆';
  if (t === 'QUEUE')      return '💰';
  if (t === 'DIRECT')     return '👥';
  if (t === 'COMMISSION') return '⭐';
  if (t === 'WITHDRAW')   return '💸';
  if (t === 'CONVERT')    return '🔄';
  return '🪙';
}
function labelFor(t) {
  if (t === 'POOL')       return '派奖池中奖';
  if (t === 'QUEUE')      return '推 N 反 1 出队';
  if (t === 'DIRECT')     return '直推返现';
  if (t === 'COMMISSION') return '团队佣金';
  if (t === 'WITHDRAW')   return '提现';
  if (t === 'CONVERT')    return '积分转换';
  return '推广奖励';
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
    }
  } finally { loading.value = false; }
}
async function loadMore() {
  if (loadingMore.value || !hasMore.value) return;
  loadingMore.value = true;
  page.value++;
  try {
    const r = await listPromoRecords(page.value, 20, tenantId);
    if (r) records.value.push(...(r.list || []));
  } finally { loadingMore.value = false; }
}

onMounted(async () => {
  try {
    const acct = await getAccount(tenantId);
    promoBalance.value = acct?.promoPointBalance || 0;
  } catch {}
  await load();
});
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background: $bg-2; padding-bottom: 30px; }
.sum-card { margin: 14px; padding: 20px; background: linear-gradient(135deg, $o, $o-d); color: #fff; border-radius: $r-lg; box-shadow: $sh-warm; }
.sum-card .l { font-size: 12px; opacity: .8; }
.sum-card .amt { font-size: 34px; font-weight: 900; margin-top: 4px; }
.sum-card .d { font-size: 11px; opacity: .8; margin-top: 4px; }
.loading { padding: 40px; text-align: center; color: $t4; }
.list { padding-bottom: 20px; }
.row { display: flex; align-items: center; gap: 10px; padding: 12px; background: #fff; margin: 6px 14px; border-radius: $r-md; box-shadow: $sh-1; }
.r-ic { width: 36px; height: 36px; background: $o-50; color: $o; border-radius: 10px; display: flex; align-items: center; justify-content: center; font-size: 16px; flex-shrink: 0; }
.body { flex: 1; min-width: 0; }
.t { font-size: 13px; font-weight: 700; color: $t1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.d { font-size: 11px; color: $t3; margin-top: 2px; }
.amt { font-size: 16px; font-weight: 800; color: $o; flex-shrink: 0; }
.amt.neg { color: $t3; }
.more { padding: 14px; text-align: center; font-size: 12px; color: $t4; }
.more.click { color: $o; }
</style>
