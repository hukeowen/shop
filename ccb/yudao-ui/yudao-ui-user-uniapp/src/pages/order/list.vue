<template>
  <view class="page">
    <view class="topbar">
      <text class="back" @click="goBack">‹</text>
      <text class="title">我的订单</text>
      <text class="right">售后</text>
    </view>

    <view class="order-tabs">
      <view v-for="t in tabs" :key="t.k" class="order-tab" :class="{ on: tab === t.k }" @click="switchTab(t.k)">
        {{ t.label }}<text v-if="counts[t.k]" class="badge">{{ counts[t.k] }}</text>
      </view>
    </view>

    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!orders.length" icon="📋" title="暂无订单" desc="去逛逛吧" />
    <view v-else>
      <view v-for="o in orders" :key="o.id" class="order-card" @click="goDetail(o)">
        <view class="order-card-head">
          <view class="pic" :class="picTone(o)">{{ shopFirstChar(o) }}</view>
          <text class="name">{{ o.shopName || o.merchantName || '订单' }}</text>
          <text class="status" :class="statusClass(o.status)">{{ statusText(o.status) }}</text>
        </view>
        <view v-if="o.rewardTag" class="order-win-tag" :class="o.rewardTag.cls">
          <text class="em">{{ o.rewardTag.em }}</text>
          <text>{{ o.rewardTag.text }} <text class="b">+¥{{ o.rewardTag.amount }}</text> · 已入推广积分</text>
        </view>
        <view v-for="(it, i) in (o.items || []).slice(0, 3)" :key="it.id || i" class="order-prod">
          <view class="order-prod-pic" :class="prodTone(i)">
            <image v-if="it.picUrl" :src="it.picUrl" mode="aspectFill" class="prod-img" />
            <text v-else>🛍</text>
          </view>
          <view class="order-prod-info">
            <view class="order-prod-name">{{ it.spuName }}</view>
            <view v-if="it.skuName" class="order-prod-spec">{{ it.skuName }}</view>
          </view>
          <view class="order-prod-amt"><view class="v">¥{{ fen2yuan(it.price, false) }}</view><view class="q">×{{ it.count }}</view></view>
        </view>
        <view class="order-foot">
          <view class="total">合计 <text class="b">¥{{ fen2yuan(o.payPrice || 0, false) }}</text>
            <text v-if="o.rewardTag"> （含返奖 ¥{{ o.rewardTag.amount }}）</text>
          </view>
          <view class="order-acts">
            <view v-for="act in actsOf(o)" :key="act.k" class="order-act" :class="{ primary: act.primary }" @click.stop="onAct(o, act.k)">{{ act.label }}</view>
          </view>
        </view>
      </view>
    </view>
    <view class="bottom-pad"></view>
    <bottom-nav active="order" />
  </view>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { pageOrders, getOrderCount, cancelOrder, receiveOrder } from '@/api/order.js';
import { fen2yuan, fmtTime } from '@/utils/format.js';

const tab = ref('all');
const tabs = [
  { k: 'all',  label: '全部',   s: undefined },
  { k: 'wait', label: '待付款', s: 0 },
  { k: 'send', label: '待发货', s: 10 },
  { k: 'recv', label: '待收货', s: 20 },
  { k: 'done', label: '已完成', s: 30 },
];
const counts = reactive({ wait: 0, send: 0, recv: 0, done: 0 });
const loading = ref(false);
const orders = ref([]);

function statusText(s) {
  return { 0: '待付款', 10: '待发货', 20: '待收货', 30: '已完成', 40: '已取消', 50: '售后中' }[s] || '';
}
function statusClass(s) {
  if (s === 0) return 'danger';
  if (s === 20) return 'warn';
  if (s === 30) return 'ok';
  return '';
}
function shopFirstChar(o) {
  const n = o.shopName || o.merchantName || '';
  return n.charAt(0) || '店';
}
function picTone(o) {
  const tid = (o.shopId || o.tenantId || 0) % 3;
  return ['', 'alt-1', 'alt-2'][tid];
}
function prodTone(i) { return ['', 'green', 'cream'][i % 3]; }

function actsOf(o) {
  if (o.status === 0)  return [{ k: 'cancel', label: '取消订单' }, { k: 'pay', label: '立即付款', primary: true }];
  if (o.status === 20) return [{ k: 'track', label: '查看物流' }, { k: 'receive', label: '确认收货', primary: true }];
  if (o.status === 30) return [{ k: 'reorder', label: '再来一单' }, { k: 'comment', label: '评价' }];
  return [];
}

function goBack() {
  const ps = getCurrentPages();
  if (ps.length > 1) uni.navigateBack();
  else uni.reLaunch({ url: '/pages/index/index' });
}
function goDetail(o) { uni.navigateTo({ url: `/pages/order/detail?id=${o.id}` }); }
function onAct(o, k) {
  if (k === 'cancel') {
    uni.showModal({ title: '取消订单', content: '确定取消该订单？', success: async ({ confirm }) => {
      if (!confirm) return;
      try { await cancelOrder(o.id); uni.showToast({ title: '已取消', icon: 'success' }); switchTab(tab.value); } catch {}
    }});
  } else if (k === 'receive') {
    uni.showModal({ title: '确认收货', content: '确认已收到货？', success: async ({ confirm }) => {
      if (!confirm) return;
      try { await receiveOrder(o.id); uni.showToast({ title: '已收货', icon: 'success' }); switchTab(tab.value); } catch {}
    }});
  } else if (k === 'pay') {
    uni.showToast({ title: '支付待接通联', icon: 'none' });
  } else if (k === 'track') {
    uni.navigateTo({ url: `/pages/order/detail?id=${o.id}&tab=express` });
  } else if (k === 'reorder' || k === 'comment') {
    uni.showToast({ title: '功能待加', icon: 'none' });
  }
}

async function switchTab(k) {
  tab.value = k; loading.value = true;
  const t = tabs.find((x) => x.k === k);
  try {
    const r = await pageOrders(t?.s, 1, 30);
    orders.value = (r?.list || []).map((o) => {
      // 计算返奖 tag（按 sourceType 推断）
      let rewardTag = null;
      if (o.rewardAmount > 0 || o.promoAmount > 0) {
        const amt = fen2yuan(o.rewardAmount || o.promoAmount || 0, false);
        if (o.rewardType === 'POOL') rewardTag = { em: '🏆', text: '本单触发商户派奖到账', amount: amt };
        else if (o.rewardType === 'QUEUE') rewardTag = { em: '🎁', text: '推 N 反 1 出队 · 全额奖', amount: amt, cls: 'gold' };
        else if (amt !== '0.00') rewardTag = { em: '💎', text: '本单推广积分到账', amount: amt };
      }
      return { ...o, rewardTag };
    });
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

onMounted(() => { switchTab('all'); loadCounts(); });
onShow(() => { switchTab(tab.value); loadCounts(); });
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.page { min-height: 100vh; background: $bg-2; padding-bottom: 90px; }

/* ━━ topbar ━━ */
.topbar {
  display: flex; align-items: center; padding: 12px 14px;
  background: $card; border-bottom: 1px solid $line;
}
.topbar .back { font-size: 22px; color: $t1; padding: 4px 10px; line-height: 1; }
.topbar .title { flex: 1; text-align: center; font-size: 16px; font-weight: 700; color: $t1; }
.topbar .right { font-size: 13px; color: $o-d; padding: 4px 10px; font-weight: 600; }

/* ━━ tabs ━━ */
.order-tabs {
  display: flex; padding: 0 14px; gap: 16px;
  background: $card;
  border-bottom: 1px solid $line;
  overflow-x: auto;
}
.order-tab {
  padding: 12px 0; font-size: 13px; font-weight: 700; color: $t3;
  position: relative; white-space: nowrap;
}
.order-tab.on { color: $t1; font-size: 15px; font-weight: 900; }
.order-tab.on::after {
  content: ''; position: absolute; bottom: 0; left: 50%; transform: translateX(-50%);
  width: 20px; height: 3px; border-radius: 2px;
  background: linear-gradient(90deg, $o, $o-d);
}
.badge {
  margin-left: 4px;
  min-width: 16px; padding: 0 4px; height: 16px;
  background: $danger; color: #fff;
  border-radius: 99px;
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 9px; font-weight: 800;
  vertical-align: top;
}

.loading { padding: 40px; text-align: center; color: $t4; }

/* ━━ order-card ━━ */
.order-card {
  margin: 12px 14px 0;
  background: $card;
  border: 1px solid $line;
  border-radius: $r-lg;
  overflow: hidden;
}
.order-card-head {
  display: flex; align-items: center; gap: 10px;
  padding: 11px 14px;
  border-bottom: 1px dashed $line;
  background: linear-gradient(90deg, $o-50, transparent);
}
.order-card-head .pic {
  width: 24px; height: 24px; border-radius: 6px;
  background: linear-gradient(135deg, #FFD1BA, $o);
  color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 800;
}
.order-card-head .pic.alt-1 { background: linear-gradient(135deg, #C9E0FF, #6196F0); }
.order-card-head .pic.alt-2 { background: linear-gradient(135deg, #D3F4D3, #4CB84C); }
.order-card-head .name { flex: 1; font-size: 13px; font-weight: 800; color: $t1; }
.order-card-head .status { font-size: 12px; font-weight: 800; padding: 2px 8px; border-radius: 99px; }
.order-card-head .status.warn   { background: rgba(245,158,11,.14);  color: $warn; }
.order-card-head .status.danger { background: rgba(230,57,70,.12);   color: $danger; }
.order-card-head .status.ok     { background: $mint-50;              color: $mint; }

/* 返奖标签 */
.order-win-tag {
  margin: 8px 14px 0;
  padding: 8px 12px;
  background: linear-gradient(135deg, $gold-50, $o-50);
  border: 1px dashed $o-100;
  border-radius: 8px;
  display: flex; align-items: center; gap: 8px;
  font-size: 11px; color: $gold-d; font-weight: 700;
}
.order-win-tag.gold { background: linear-gradient(135deg, $o-50, $gold-50); }
.order-win-tag .em { font-size: 14px; }
.order-win-tag .b { color: $o-d; font-weight: 900; }

.order-prod {
  display: flex; gap: 10px;
  padding: 12px 14px;
}
.order-prod-pic {
  width: 56px; height: 56px; border-radius: 10px;
  background: linear-gradient(135deg, $o-100, $o-l);
  display: flex; align-items: center; justify-content: center;
  font-size: 26px; flex-shrink: 0; overflow: hidden;
}
.order-prod-pic.green { background: linear-gradient(135deg, #D1FAE5, #6EE7B7); }
.order-prod-pic.cream { background: linear-gradient(135deg, #FEF3C7, #FCD34D); }
.prod-img { width: 100%; height: 100%; }
.order-prod-info { flex: 1; min-width: 0; }
.order-prod-name { font-size: 13px; font-weight: 700; color: $t1; line-height: 1.3; }
.order-prod-spec { font-size: 11px; color: $t3; margin-top: 3px; }
.order-prod-amt { text-align: right; }
.order-prod-amt .v { font-size: 14px; font-weight: 800; color: $t1; }
.order-prod-amt .q { font-size: 11px; color: $t3; margin-top: 2px; }

.order-foot {
  display: flex; justify-content: space-between; align-items: center;
  padding: 11px 14px;
  border-top: 1px solid $line;
}
.order-foot .total { font-size: 12px; color: $t2; }
.order-foot .total .b { color: $t1; font-weight: 800; font-size: 15px; margin: 0 2px; }
.order-acts { display: flex; gap: 6px; }
.order-act {
  padding: 6px 12px; border-radius: 99px;
  background: $card; color: $t2;
  font-size: 12px; font-weight: 700;
  border: 1px solid $line;
}
.order-act.primary {
  background: linear-gradient(135deg, $o, $o-d);
  color: #fff; border-color: transparent;
  box-shadow: $sh-warm;
}
.bottom-pad { height: 12px; }
</style>
