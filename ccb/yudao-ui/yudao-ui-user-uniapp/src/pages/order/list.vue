<template>
  <view class="page">
    <nav-bar title="我的订单" />
    <view class="tabs">
      <view v-for="t in tabs" :key="t.k" class="tab" :class="{ on: tab === t.k }" @click="switchTab(t.k)">
        {{ t.label }}<text v-if="t.badge" class="badge">{{ t.badge }}</text>
      </view>
    </view>
    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!orders.length" icon="📋" title="暂无订单" desc="去逛逛吧" />
    <view v-else>
      <view v-for="o in orders" :key="o.id" class="order" @click="goDetail(o)">
        <view class="o-head">
          <text class="o-shop">{{ o.shopName }}</text>
          <text class="o-status">{{ o.statusText }}</text>
        </view>
        <view class="o-items">
          <view v-for="it in o.items" :key="it.id" class="o-it">
            <text class="o-it-em">{{ it.em || '🛍' }}</text>
            <text class="o-it-name">{{ it.name }}</text>
            <text class="o-it-qty">x {{ it.count }}</text>
          </view>
        </view>
        <view class="o-foot">
          <text class="o-time">{{ o.time }}</text>
          <text class="o-amt">¥{{ o.amount }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
// import { listOrders } from '@/api/order.js';

const tab = ref('all');
const tabs = [
  { k: 'all',      label: '全部' },
  { k: 'unpaid',   label: '待付款' },
  { k: 'pending',  label: '待发货' },
  { k: 'shipped',  label: '待收货' },
  { k: 'done',     label: '已完成' },
];
const loading = ref(false);
const orders = ref([]);

async function switchTab(k) {
  tab.value = k;
  loading.value = true;
  try { orders.value = []; /* orders.value = await listOrders(k === 'all' ? '' : k); */ }
  finally { loading.value = false; }
}
function goDetail(o) { uni.navigateTo({ url: `/pages/order/detail?id=${o.id}` }); }
onMounted(() => switchTab('all'));
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background: $bg-2; }
.tabs { display: flex; background: #fff; padding: 0 14px; border-bottom: 1px solid $line; overflow-x: auto; }
.tab { padding: 14px 12px; font-size: 13px; color: $t2; font-weight: 600; position: relative; white-space: nowrap; }
.tab.on { color: $o; }
.tab.on::after { content: ''; position: absolute; left: 12px; right: 12px; bottom: 6px; height: 3px; border-radius: 2px; background: linear-gradient(90deg, $o, $gold); }
.badge { background: $danger; color: #fff; font-size: 9px; padding: 1px 5px; border-radius: 6px; margin-left: 4px; vertical-align: top; }
.loading { padding: 40px; text-align: center; color: $t4; }
.order { background: #fff; margin: 10px 14px; border-radius: $r-md; padding: 14px; box-shadow: $sh-1; }
.o-head { display: flex; justify-content: space-between; margin-bottom: 10px; }
.o-shop { font-size: 14px; font-weight: 700; color: $t1; }
.o-status { font-size: 12px; color: $o; font-weight: 700; }
.o-it { display: flex; align-items: center; gap: 8px; padding: 4px 0; font-size: 13px; color: $t2; }
.o-it-em { font-size: 18px; }
.o-it-name { flex: 1; }
.o-it-qty { color: $t3; font-size: 11px; }
.o-foot { display: flex; justify-content: space-between; padding-top: 8px; border-top: 1px dashed $line; margin-top: 8px; }
.o-time { font-size: 11px; color: $t4; }
.o-amt { font-size: 16px; font-weight: 800; color: $o; }
</style>
