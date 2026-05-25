<template>
  <view class="page">
    <nav-bar title="优惠券" />
    <view class="tabs">
      <view v-for="t in tabs" :key="t.k" class="tab" :class="{ on: tab === t.k }" @click="switchTab(t.k)">{{ t.label }}</view>
    </view>
    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!coupons.length" icon="🎟" :title="`暂无${tabLabel}优惠券`" />
    <view v-else class="list">
      <view v-for="c in coupons" :key="c.id" class="cp" :class="tab">
        <view class="cp-l">
          <view class="cp-amt">¥<text class="big">{{ c.value }}</text></view>
          <view class="cp-cond">满 ¥{{ c.minSpend }} 用</view>
        </view>
        <view class="cp-r">
          <view class="cp-shop">{{ c.shopName }}</view>
          <view class="cp-name">{{ c.name }}</view>
          <view class="cp-exp">{{ c.expireText }}</view>
        </view>
        <view v-if="tab === 'unused'" class="cp-cta">去使用</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
// import { listCoupons } from '@/api/promo.js';
const tab = ref('unused');
const tabs = [
  { k: 'unused', label: '未使用' },
  { k: 'used',   label: '已使用' },
  { k: 'expired',label: '已过期' },
];
const tabLabel = computed(() => tabs.find((x) => x.k === tab.value)?.label || '');
const loading = ref(false);
const coupons = ref([]);
async function switchTab(k) {
  tab.value = k; loading.value = true;
  try { coupons.value = []; }
  finally { loading.value = false; }
}
onMounted(() => switchTab('unused'));
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background: $bg-2; padding-bottom: 30px; }
.tabs { display: flex; background: #fff; }
.tab { flex: 1; padding: 14px 0; text-align: center; font-size: 13px; color: $t2; font-weight: 600; position: relative; }
.tab.on { color: $o; }
.tab.on::after { content: ''; position: absolute; left: 30%; right: 30%; bottom: 6px; height: 3px; border-radius: 2px; background: linear-gradient(90deg, $o, $gold); }
.loading { padding: 40px; text-align: center; color: $t4; }
.list { padding: 14px; }
.cp { display: flex; align-items: stretch; background: #fff; border-radius: $r-md; margin-bottom: 10px; overflow: hidden; box-shadow: $sh-1; position: relative; }
.cp-l { width: 110px; padding: 16px 10px; background: linear-gradient(135deg, $o, $o-d); color: #fff; display: flex; flex-direction: column; align-items: center; justify-content: center; }
.cp.used .cp-l, .cp.expired .cp-l { background: linear-gradient(135deg, #94A3B8, #64748B); }
.cp-amt { font-size: 14px; font-weight: 700; }
.cp-amt .big { font-size: 28px; font-weight: 900; }
.cp-cond { font-size: 10px; margin-top: 2px; opacity: .9; }
.cp-r { flex: 1; padding: 14px 12px; min-width: 0; }
.cp-shop { font-size: 11px; color: $t3; }
.cp-name { font-size: 14px; font-weight: 700; color: $t1; margin-top: 4px; }
.cp-exp { font-size: 11px; color: $t4; margin-top: 6px; }
.cp-cta { position: absolute; right: 12px; bottom: 14px; padding: 6px 14px; border: 1px solid $o; color: $o; border-radius: $r-pill; font-size: 11px; font-weight: 700; }
</style>
