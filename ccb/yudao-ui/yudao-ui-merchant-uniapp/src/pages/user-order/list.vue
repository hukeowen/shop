<template>
  <view class="page">
    <!-- 顶部筛选 + 标题 -->
    <view class="order-top safe-top">
      <view class="ttl">我的订单</view>
      <view class="filter">
        <view
          v-for="tab in tabs"
          :key="tab.key"
          :class="['it', activeTab === tab.key ? 'active' : '']"
          @click="switchTab(tab.key)"
        >{{ tab.label }}</view>
      </view>
    </view>

    <view v-if="loading && !groups.length" class="empty-tip">加载中...</view>
    <view v-else-if="!groups.length" class="empty-state">
      <view class="empty-emoji">🧾</view>
      <view class="empty-title">还没有订单</view>
      <view class="empty-sub">逛逛附近店铺，下单后会出现在这里</view>
      <view class="empty-cta" @click="goHome">去逛附近店铺 ›</view>
    </view>

    <view v-else>
      <view
        v-for="grp in groups"
        :key="grp.tenantId"
        class="shop-group"
      >
        <view class="group-head" @click="goShop(grp.tenantId)">
          <view class="group-pic" :style="picStyle(grp)">{{ initial(grp) }}</view>
          <text class="group-shop-name">{{ grp.shopName }}</text>
          <text class="group-count">{{ grp.orders.length }} 单</text>
          <text class="group-arrow">›</text>
        </view>
        <view
          v-for="order in grp.orders"
          :key="order.id"
          class="order-row"
          @click="goOrder(order)"
        >
          <view class="order-row1">
            <text class="order-no">#{{ order.no || order.id }}</text>
            <text class="order-status" :class="statusClass(order.status)">{{ statusLabel(order.status) }}</text>
          </view>
          <view class="order-items">
            <image v-if="firstItemPic(order)" class="order-pic-img" :src="firstItemPic(order)" mode="aspectFill" />
            <view v-else class="order-pic">{{ pickEmoji(order) }}</view>
            <view class="order-info">
              <view class="order-pname">{{ firstItemName(order) }}</view>
              <view class="order-spec">{{ itemsSummary(order) }}</view>
            </view>
            <view class="order-amt">
              <view class="price">¥{{ fen2yuan(orderTotal(order)) }}</view>
              <view class="qty">x {{ orderQty(order) }}</view>
            </view>
          </view>
          <view class="order-foot">
            <text class="order-time">{{ formatTime(order.createTime) }}</text>
            <view class="order-actions">
              <view v-if="order.status === 0" class="btn primary" @click.stop="onPay(order)">立即付款</view>
              <view v-if="order.status === 20" class="btn primary" @click.stop="onConfirm(order)">确认收货</view>
              <view v-if="order.status === 30" class="btn" @click.stop="onRebuy(order)">再来一单</view>
              <view v-if="order.status === 30" class="btn" @click.stop="onComment(order)">评价</view>
            </view>
          </view>
        </view>
      </view>
    </view>

    <view v-if="hasMore && !loading" class="load-more" @click="loadMore">加载更多</view>
    <view class="bottom-space"></view>

    <!-- 底部 tab：force-role="member" 防商户账号下被解析成商户 tab -->
    <RoleTabBar current="/pages/user-order/list" force-role="member" />
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app';
import { request } from '../../api/request.js';
import { fen2yuan } from '../../utils/format.js';

const tabs = [
  { key: 'all', label: '全部', status: null },
  { key: 'unpaid', label: '待付款', status: 0 },
  { key: 'undelivered', label: '待发货', status: 10 },
  { key: 'unreceived', label: '待收货', status: 20 },
  { key: 'completed', label: '已完成', status: 30 },
];

const activeTab = ref('all');
const orders = ref([]);
const loading = ref(false);
const pageNo = ref(1);
const hasMore = ref(true);

// 按 tenantId 分组：trade/order/page 返的订单不含 shopName，前端用 shopNameMap 异步映射
const shopNameMap = ref({});
const groups = computed(() => {
  const map = new Map();
  for (const o of orders.value) {
    const tid = o.tenantId || 0;
    if (!map.has(tid)) {
      map.set(tid, {
        tenantId: tid,
        shopName: shopNameMap.value[tid] || o.shopName || o.merchantName || '加载中...',
        orders: [],
      });
    }
    map.get(tid).orders.push(o);
  }
  return Array.from(map.values());
});

// 按订单出现的 tenantId 去重，并发拉店铺名（去重避免重复请求）
async function loadShopNames() {
  const tids = Array.from(new Set(orders.value.map(o => o.tenantId).filter(Boolean)));
  const need = tids.filter(tid => !shopNameMap.value[tid]);
  if (!need.length) return;
  const results = await Promise.all(
    need.map(tid =>
      request({ url: `/app-api/merchant/shop/public/info?tenantId=${tid}` })
        .then(s => ({ tid, name: s?.shopName || `店铺 #${tid}` }))
        .catch(() => ({ tid, name: `店铺 #${tid}` }))
    )
  );
  const next = { ...shopNameMap.value };
  results.forEach(({ tid, name }) => { next[tid] = name; });
  shopNameMap.value = next;
}

const initial = (grp) => (grp.shopName || '店')[0];
const picStyle = (grp) => {
  const palette = ['#ffd1ba,#ff6b35', '#c9e0ff,#6196f0', '#d3f4d3,#4cb84c', '#ffd0dc,#ee5a8b'];
  const idx = (Number(grp.tenantId) || 0) % palette.length;
  return `background: linear-gradient(135deg, ${palette[idx]});`;
};

function statusLabel(s) {
  return ({ 0: '待付款', 10: '待发货', 20: '待收货', 30: '已完成', 40: '已取消' })[s] || '未知';
}
function statusClass(s) {
  if (s === 0) return 'danger';
  if (s === 30) return 'success';
  if (s === 20) return 'warn';
  return '';
}
function firstItemName(order) {
  const items = order.items || order.orderItems || [];
  return items[0]?.spuName || items[0]?.name || '商品';
}
function firstItemPic(order) {
  const items = order.items || order.orderItems || [];
  return items[0]?.picUrl || items[0]?.spu?.picUrl || '';
}
function itemsSummary(order) {
  const items = order.items || order.orderItems || [];
  if (items.length <= 1) return items[0]?.skuName || items[0]?.spec || '';
  return `共 ${items.length} 件`;
}
function orderTotal(order) {
  return order.payPrice ?? order.totalPrice ?? order.price ?? 0;
}
function orderQty(order) {
  const items = order.items || order.orderItems || [];
  return items.reduce((s, i) => s + (i.count || 0), 0) || 1;
}
function pickEmoji(order) {
  // 简单按商品名首字符判断 emoji
  const name = firstItemName(order);
  if (/(地瓜|薯|玉米|烤)/.test(name)) return '🍠';
  if (/(茶|奶茶)/.test(name)) return '🍵';
  if (/(果|莓|桃|柑|橙)/.test(name)) return '🍇';
  if (/(肉|串|烧)/.test(name)) return '🍖';
  return '🛍';
}
function formatTime(t) {
  if (!t) return '';
  const d = new Date(typeof t === 'string' ? t.replace(' ', 'T') : t);
  if (isNaN(d.getTime())) return '';
  return `${d.getMonth() + 1}-${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

function currentStatus() {
  const t = tabs.find(x => x.key === activeTab.value);
  return t?.status ?? null;
}

async function load(reset = false) {
  if (reset) { pageNo.value = 1; orders.value = []; hasMore.value = true; }
  if (!hasMore.value) return;
  loading.value = true;
  try {
    const status = currentStatus();
    const url = status == null
      ? `/app-api/trade/order/page?pageNo=${pageNo.value}&pageSize=10`
      : `/app-api/trade/order/page?pageNo=${pageNo.value}&pageSize=10&status=${status}`;
    const res = await request({ url });
    const list = res?.list || [];
    orders.value = pageNo.value === 1 ? list : orders.value.concat(list);
    hasMore.value = list.length >= 10;
    // 异步拉店铺名（不阻塞列表渲染）
    loadShopNames();
  } catch {
    if (pageNo.value === 1) orders.value = [];
  } finally {
    loading.value = false;
  }
}

function switchTab(key) {
  if (activeTab.value === key) return;
  activeTab.value = key;
  load(true);
}
function loadMore() {
  pageNo.value += 1;
  load();
}

function goShop(tid) { uni.navigateTo({ url: `/pages/shop-home/index?tenantId=${tid}` }); }
function goOrder(o) { uni.navigateTo({ url: `/pages/user-order/list` }); /* 暂用列表，详情页后续单独建 */ }
function onPay(o) { uni.showToast({ title: '支付页跳转待接入', icon: 'none' }); }
function onConfirm(o) {
  uni.showModal({ title: '确认收货', content: '确认已收到货？', success: (r) => { if (r.confirm) uni.showToast({ title: '已确认', icon: 'success' }); } });
}
function onRebuy(o) { goShop(o.tenantId); }
function onComment(o) { uni.showToast({ title: '评价页开发中', icon: 'none' }); }
// pages.json 已无全局 tabBar，统一 reLaunch（H5 hash 路由下 switchTab/navigateTo 不可靠）
function goHome() { uni.reLaunch({ url: '/pages/user-home/index' }); }
function goMe() { uni.reLaunch({ url: '/pages/user-me/index' }); }

onMounted(() => load(true));
onShow(() => load(true));
onPullDownRefresh(async () => {
  await load(true);
  uni.stopPullDownRefresh();
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page { min-height: 100vh; background: $bg-page; padding-bottom: 160rpx; }
.safe-top { padding-top: calc(env(safe-area-inset-top) + 16rpx); }
.safe-bottom { padding-bottom: env(safe-area-inset-bottom); }

.order-top {
  background: $bg-card;
  padding: 24rpx 32rpx 0;
  box-shadow: 0 1rpx 0 $border-color;
}
.order-top .ttl { font-size: 40rpx; font-weight: 800; color: $text-primary; margin-bottom: 24rpx; }
.order-top .filter {
  display: flex; gap: 32rpx;
  border-bottom: 1rpx solid $border-color; padding-bottom: 24rpx;
}
.order-top .filter .it {
  font-size: 26rpx; color: $text-secondary;
  padding-bottom: 8rpx; border-bottom: 4rpx solid transparent;
}
.order-top .filter .it.active {
  color: $brand-primary; border-bottom-color: $brand-primary; font-weight: 600;
}

.empty-tip { text-align: center; padding: 80rpx 0; color: $text-placeholder; font-size: 26rpx; }
.empty-state { text-align: center; padding: 120rpx 60rpx; }
.empty-state .empty-emoji { font-size: 96rpx; margin-bottom: 24rpx; opacity: .5; }
.empty-state .empty-title { font-size: 32rpx; font-weight: 700; color: $text-primary; }
.empty-state .empty-sub { margin-top: 12rpx; font-size: 24rpx; color: $text-placeholder; }
.empty-state .empty-cta {
  margin-top: 40rpx; display: inline-block;
  padding: 16rpx 40rpx; background: $brand-primary; color: #fff;
  border-radius: 999rpx; font-size: 26rpx; font-weight: 600;
}

.shop-group {
  margin: 24rpx 32rpx; background: $bg-card;
  border-radius: $radius-lg; overflow: hidden;
  box-shadow: 0 4rpx 16rpx rgba(15,23,42,.04);
}
.group-head {
  display: flex; align-items: center; gap: 16rpx;
  padding: 24rpx 32rpx; border-bottom: 1rpx dashed $border-color;
}
.group-pic {
  width: 56rpx; height: 56rpx; border-radius: $radius-sm;
  color: #fff; font-size: 26rpx; font-weight: 800;
  display: flex; align-items: center; justify-content: center;
}
.group-shop-name { flex: 1; font-size: 28rpx; font-weight: 700; color: $text-primary; }
.group-count { font-size: 22rpx; color: $text-placeholder; }
.group-arrow { font-size: 28rpx; color: $text-placeholder; }

.order-row {
  padding: 24rpx 28rpx;
  border-bottom: 1rpx solid $border-color;
}
.order-row:last-child { border-bottom: 0; }
.order-row1 { display: flex; align-items: center; margin-bottom: 16rpx; }
.order-no { flex: 1; font-size: 22rpx; color: $text-placeholder; }
.order-status { font-size: 24rpx; font-weight: 600; color: $brand-primary; }
.order-status.success { color: $success; }
.order-status.danger { color: $danger; }
.order-status.warn { color: $warning; }

.order-items {
  display: flex; gap: 16rpx; margin-bottom: 16rpx;
}
.order-pic {
  width: 112rpx; height: 112rpx; border-radius: $radius-sm;
  background: linear-gradient(135deg,#f3e1d4,#ffae74);
  color: #fff; font-size: 44rpx;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.order-pic-img {
  width: 96rpx; height: 96rpx; border-radius: $radius-md;
  flex-shrink: 0;
  background: $bg-page;
}
.order-info { flex: 1; min-width: 0; }
.order-pname {
  font-size: 26rpx; color: $text-primary;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.order-spec { font-size: 22rpx; color: $text-placeholder; margin-top: 4rpx; }
.order-amt { text-align: right; flex-shrink: 0; }
.order-amt .price {
  font-size: 28rpx; font-weight: 700; color: $text-primary;
  font-variant-numeric: tabular-nums;
}
.order-amt .qty { font-size: 22rpx; color: $text-placeholder; margin-top: 4rpx; }

.order-foot { display: flex; align-items: center; justify-content: space-between; }
.order-time { font-size: 22rpx; color: $text-placeholder; }
.order-actions { display: flex; gap: 12rpx; }
.order-actions .btn {
  padding: 8rpx 24rpx; border-radius: 999rpx;
  font-size: 22rpx; border: 1rpx solid $border-color;
  background: $bg-card; color: $text-secondary;
}
.order-actions .btn.primary {
  background: $brand-primary; color: #fff; border-color: $brand-primary; font-weight: 600;
}

.load-more {
  text-align: center; padding: 24rpx 0;
  font-size: 24rpx; color: $brand-primary;
}
.bottom-space { height: 24rpx; }

.tab-bar {
  position: fixed; bottom: 0; left: 0; right: 0;
  height: 144rpx; padding-bottom: 32rpx;
  background: rgba(255,255,255,.96); backdrop-filter: blur(20rpx);
  border-top: 1rpx solid $border-color;
  display: flex; align-items: stretch; z-index: 50;
}
.tab-item {
  flex: 1; display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  color: $text-placeholder; font-size: 22rpx;
}
.tab-item .ic { font-size: 44rpx; line-height: 1; margin-bottom: 6rpx; }
.tab-item.active { color: $brand-primary; font-weight: 600; }
.tab-item.active .ic { transform: translateY(-4rpx) scale(1.08); }
</style>
