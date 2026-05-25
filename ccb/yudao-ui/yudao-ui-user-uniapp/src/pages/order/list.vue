<template>
  <view class="page">
    <nav-bar title="我的订单" />
    <view class="tabs">
      <view v-for="t in tabs" :key="t.k" class="tab" :class="{ on: tab === t.k }" @click="switchTab(t.k)">
        {{ t.label }}<text v-if="counts[t.k]" class="badge">{{ counts[t.k] }}</text>
      </view>
    </view>
    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!orders.length" icon="📋" title="暂无订单" desc="去逛逛吧" />
    <view v-else>
      <view v-for="o in orders" :key="o.id" class="order" @click="goDetail(o)">
        <view class="o-head">
          <text class="o-shop">{{ o.shopName || o.merchantName || '订单' }}</text>
          <text class="o-status">{{ statusText(o.status) }}</text>
        </view>
        <view class="o-items">
          <view v-for="it in (o.items || []).slice(0, 3)" :key="it.id" class="o-it">
            <view class="o-it-pic">
              <image v-if="it.picUrl" :src="it.picUrl" mode="aspectFill" class="o-it-img" />
              <text v-else>🛍</text>
            </view>
            <view class="o-it-info">
              <view class="o-it-name">{{ it.spuName }}</view>
              <view class="o-it-meta">¥{{ fen2yuan(it.price, false) }} × {{ it.count }}</view>
            </view>
          </view>
        </view>
        <view class="o-foot">
          <text class="o-time">{{ fmtTime(o.createTime) }}</text>
          <text class="o-amt">合计 <text class="b">¥{{ fen2yuan(o.payPrice || 0, false) }}</text></text>
        </view>
        <view v-if="canCancel(o)" class="o-actions">
          <view class="btn ghost" @click.stop="onCancel(o)">取消订单</view>
          <view v-if="o.status === 0" class="btn warm" @click.stop="onPay(o)">去支付</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { pageOrders, getOrderCount, cancelOrder } from '@/api/order.js';
import { fen2yuan, fmtTime } from '@/utils/format.js';

// yudao trade status: 0=待付款 10=待发货 20=待收货 30=已完成 40=已取消 50=售后中
const tab = ref('all');
const tabs = [
  { k: 'all',   label: '全部', s: undefined },
  { k: 'wait',  label: '待付款', s: 0 },
  { k: 'send',  label: '待发货', s: 10 },
  { k: 'recv',  label: '待收货', s: 20 },
  { k: 'done',  label: '已完成', s: 30 },
];
const counts = reactive({ wait: 0, send: 0, recv: 0, done: 0 });
const loading = ref(false);
const orders = ref([]);

function statusText(s) {
  return { 0: '待付款', 10: '待发货', 20: '待收货', 30: '已完成', 40: '已取消', 50: '售后中' }[s] || '';
}
function canCancel(o) { return o.status === 0; }

async function switchTab(k) {
  tab.value = k; loading.value = true;
  const t = tabs.find((x) => x.k === k);
  try {
    const r = await pageOrders(t?.s, 1, 30);
    orders.value = r?.list || [];
  } catch { orders.value = []; }
  finally { loading.value = false; }
}

async function loadCounts() {
  try {
    const c = await getOrderCount();
    if (c) {
      counts.wait = c.unpaidCount || c['0'] || 0;
      counts.send = c.undeliveredCount || c['10'] || 0;
      counts.recv = c.uncommentCount || c['20'] || 0;
    }
  } catch {}
}

function goDetail(o) { uni.navigateTo({ url: `/pages/order/detail?id=${o.id}` }); }
async function onCancel(o) {
  uni.showModal({ title: '取消订单', content: '确定取消该订单？', success: async ({ confirm }) => {
    if (!confirm) return;
    try { await cancelOrder(o.id); uni.showToast({ title: '已取消', icon: 'success' }); switchTab(tab.value); }
    catch {}
  }});
}
function onPay(o) {
  uni.showToast({ title: '支付功能待接通联', icon: 'none' });
}

onMounted(() => { switchTab('all'); loadCounts(); });
onShow(() => { switchTab(tab.value); loadCounts(); });
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
.o-it { display: flex; align-items: center; gap: 10px; padding: 6px 0; }
.o-it-pic { width: 50px; height: 50px; border-radius: 8px; background: $o-50; color: $o; display: flex; align-items: center; justify-content: center; font-size: 22px; overflow: hidden; flex-shrink: 0; }
.o-it-img { width: 100%; height: 100%; }
.o-it-info { flex: 1; min-width: 0; }
.o-it-name { font-size: 13px; color: $t1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.o-it-meta { font-size: 11px; color: $t3; margin-top: 2px; }
.o-foot { display: flex; justify-content: space-between; align-items: center; padding-top: 8px; border-top: 1px dashed $line; margin-top: 8px; }
.o-time { font-size: 11px; color: $t4; }
.o-amt { font-size: 13px; color: $t2; }
.o-amt .b { color: $o; font-weight: 900; font-size: 16px; margin-left: 4px; }
.o-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 10px; padding-top: 10px; border-top: 1px solid $line; }
.btn { padding: 8px 16px; border-radius: $r-pill; font-size: 12px; font-weight: 700; }
.btn.ghost { background: $bg-2; color: $t2; }
.btn.warm { background: linear-gradient(135deg, $o, $o-d); color: #fff; box-shadow: $sh-warm; }
</style>
