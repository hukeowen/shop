<template>
  <view class="page">
    <nav-bar title="我的积分兑换记录" />

    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!records.length" icon="🧾" title="暂无兑换申请记录" desc="去钱包按店申请兑换推广积分" />

    <view v-else class="list">
      <view v-for="r in records" :key="r.id" class="row" @click="goDetail(r)">
        <view class="row-l">
          <view class="logo" :class="toneOf(r.tenantId)">{{ shopFirst(r) }}</view>
          <view class="meta">
            <view class="name-line">
              <text class="name">{{ shopNameOf(r) }}</text>
              <text class="status" :class="`st-${r.status}`">{{ statusShort(r.status) }}</text>
            </view>
            <text class="time">{{ fmtTime(r.applyAt) }} · 申请 #{{ r.id }}</text>
          </view>
        </view>
        <view class="row-r">
          <text class="amt">{{ fen2yuan(r.amount, false) }}</text>
          <text class="unit">积分</text>
          <text class="arrow">›</text>
        </view>
      </view>
    </view>

    <view class="footer-tip">
      平台仅提供技术信息撮合，兑换形式与额度由<text class="b">商户独立审批</text>，
      <text class="b">平台不构成兑换承诺、不承担担保责任</text>。
    </view>
    <view class="bottom-pad"></view>
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { listMyPromoWithdraws } from '@/api/promo.js';
import { getShopInfo } from '@/api/shop.js';
import { fen2yuan, fmtTime } from '@/utils/format.js';

const loading = ref(true);
const records = ref([]);

// tenantId → 店名缓存（兑换单只带 tenantId）
const shopNameCache = ref({});
function shopNameOf(r) { return shopNameCache.value[r.tenantId] || ('店铺 #' + (r.tenantId || '')); }
function shopFirst(r) { const n = shopNameCache.value[r.tenantId] || ''; return n.charAt(0) || '店'; }
function toneOf(tid) { return ['', 't2', 't3', 't4'][(Number(tid) || 0) % 4]; }

function statusShort(s) {
  return { PENDING: '审批中', APPROVED: '待打款', PAID: '待确认', COMPLETED: '已完成', REJECTED: '已驳回' }[s] || s;
}

async function resolveShopNames(list) {
  const tids = [...new Set((list || []).map((r) => r.tenantId).filter((t) => t && !shopNameCache.value[t]))];
  if (tids.length) {
    await Promise.all(tids.map(async (tid) => {
      try { const info = await getShopInfo({ tenantId: tid }); if (info && info.shopName) shopNameCache.value[tid] = info.shopName; } catch {}
    }));
    // 触发响应式更新
    shopNameCache.value = { ...shopNameCache.value };
  }
}

async function load() {
  loading.value = true;
  try {
    const list = await listMyPromoWithdraws();
    records.value = Array.isArray(list) ? list : (list?.list || []);
    resolveShopNames(records.value);
  } catch {
    records.value = [];
  } finally {
    loading.value = false;
  }
}

function goDetail(r) {
  uni.navigateTo({ url: `/pages/withdraw/detail?id=${r.id}` });
}

onShow(load);
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.page { min-height: 100vh; background: $bg-2; padding-bottom: 30px; }
.loading { padding: 40px; text-align: center; color: $t4; }

.list { padding: 12px 14px; }
.row {
  display: flex; align-items: center; justify-content: space-between;
  background: $card; border-radius: $r-lg;
  padding: 14px 14px; margin-bottom: 10px;
  box-shadow: $sh-1;
}
.row-l { display: flex; align-items: center; gap: 11px; flex: 1; min-width: 0; }
.logo {
  width: 38px; height: 38px; border-radius: 11px; flex: none;
  background: linear-gradient(135deg, #FFD1BA, $o); color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 16px; font-weight: 800;
}
.logo.t2 { background: linear-gradient(135deg, #FFE8C9, $gold); }
.logo.t3 { background: linear-gradient(135deg, #D3F4D3, $mint); }
.logo.t4 { background: linear-gradient(135deg, #FFDDE5, #F472B6); }
.meta { flex: 1; min-width: 0; }
.name-line { display: flex; align-items: center; gap: 8px; }
.name { font-size: 15px; font-weight: 800; color: $t1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 9em; }
.status { flex: none; font-size: 10px; font-weight: 800; padding: 1px 7px; border-radius: 999px; }
.status.st-PENDING   { color: $t3; background: $bg-2; }
.status.st-APPROVED  { color: $o-d; background: $o-50; }
.status.st-PAID      { color: #2563EB; background: rgba(37,99,235,.10); }
.status.st-COMPLETED { color: #059669; background: rgba(5,150,105,.10); }
.status.st-REJECTED  { color: #DC2626; background: rgba(220,38,38,.08); }
.time { display: block; font-size: 11px; color: $t4; margin-top: 4px; }

.row-r { display: flex; align-items: baseline; gap: 3px; flex: none; }
.row-r .amt { font-size: 19px; font-weight: 900; color: $o-d; font-variant-numeric: tabular-nums; }
.row-r .unit { font-size: 11px; color: $t3; }
.row-r .arrow { font-size: 18px; color: $t4; margin-left: 4px; }

.footer-tip {
  margin: 18px 14px; padding: 12px 16px;
  background: $bg-2; border-radius: $r-md; border: 1px dashed $line;
  font-size: 11px; color: $t4; line-height: 1.7;
}
.footer-tip .b { color: $t2; font-weight: 700; }
.bottom-pad { height: 30px; }
</style>
