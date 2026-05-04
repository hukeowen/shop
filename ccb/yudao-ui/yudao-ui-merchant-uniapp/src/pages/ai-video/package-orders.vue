<template>
  <view class="page">
    <view class="topbar safe-top">
      <text class="back" @click="goBack">‹</text>
      <text class="title">配额订单</text>
      <view style="width:60rpx"></view>
    </view>

    <view v-if="loading && !list.length" class="empty-tip">加载中…</view>
    <view v-else-if="!list.length" class="empty-state">
      <view class="empty-emoji">📝</view>
      <view class="empty-title">还没有购买记录</view>
      <view class="empty-sub">在「AI 视频配额」页购买套餐</view>
    </view>

    <view v-else>
      <view v-for="o in list" :key="o.id" class="order-card">
        <view class="hdr">
          <text class="name">{{ o.packageName }}</text>
          <text :class="['status', 'st-' + (o.payStatus || 0)]">{{ statusLabel(o.payStatus) }}</text>
        </view>
        <view class="row"><text class="lbl">订单号</text><text class="v">{{ o.id }}</text></view>
        <view class="row"><text class="lbl">视频条数</text><text class="v">{{ o.videoCount }} 条</text></view>
        <view class="row"><text class="lbl">实付金额</text><text class="v price">¥{{ (o.price / 100).toFixed(2) }}</text></view>
        <view class="row"><text class="lbl">创建时间</text><text class="v">{{ formatTime(o.createTime) }}</text></view>
        <view v-if="o.payTime" class="row"><text class="lbl">支付时间</text><text class="v">{{ formatTime(o.payTime) }}</text></view>
      </view>
    </view>

    <view v-if="hasMore" class="load-more" @click="loadMore">{{ loadingMore ? '加载中…' : '加载更多' }}</view>
    <view v-else-if="list.length" class="end-text">— 没有更多了 —</view>
    <view class="bottom-space"></view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { listMyPackageOrders } from '../../api/quotaApi.js';

const list = ref([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = 20;
const loading = ref(false);
const loadingMore = ref(false);
const hasMore = ref(true);

function statusLabel(s) {
  return ({ 0: '待支付', 10: '已支付', 20: '已关闭', 30: '已退款' })[s] || '待支付';
}
function formatTime(t) {
  if (!t) return '';
  const d = new Date(typeof t === 'string' ? t.replace(' ', 'T') : t);
  if (isNaN(d.getTime())) return '';
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

async function load(reset = true) {
  if (reset) {
    pageNo.value = 1;
    list.value = [];
    hasMore.value = true;
    loading.value = true;
  } else {
    loadingMore.value = true;
  }
  try {
    const res = await listMyPackageOrders(pageNo.value, pageSize);
    const rows = res?.list || [];
    if (reset) list.value = rows;
    else list.value.push(...rows);
    total.value = res?.total || 0;
    hasMore.value = list.value.length < total.value;
  } catch (e) {
    if (reset) list.value = [];
  } finally {
    loading.value = false;
    loadingMore.value = false;
  }
}

function loadMore() {
  if (!hasMore.value || loadingMore.value) return;
  pageNo.value += 1;
  load(false);
}
function goBack() { uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/index/index' }) }); }

onMounted(() => load(true));
onShow(() => {
  // 从通联回跳带 ?paid=1 时刷新列表（用户刚付完，订单状态可能正在更新）
  if (typeof location !== 'undefined' && /paid=1/.test(location.hash || '')) {
    setTimeout(() => load(true), 500);
  }
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';
.page { min-height: 100vh; background: $bg-page; }
.safe-top { padding-top: calc(env(safe-area-inset-top) + 16rpx); }
.topbar { display: flex; align-items: center; padding: 16rpx 32rpx; background: $bg-card; border-bottom: 1rpx solid $border-color; }
.topbar .back { font-size: 44rpx; color: $text-primary; padding-right: 16rpx; }
.topbar .title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; color: $text-primary; }

.empty-tip { text-align: center; padding: 80rpx 0; color: $text-placeholder; font-size: 26rpx; }
.empty-state { text-align: center; padding: 120rpx 60rpx; }
.empty-state .empty-emoji { font-size: 96rpx; margin-bottom: 24rpx; opacity: .5; }
.empty-state .empty-title { font-size: 32rpx; font-weight: 700; color: $text-primary; }
.empty-state .empty-sub { margin-top: 12rpx; font-size: 24rpx; color: $text-placeholder; }

.order-card { margin: 24rpx 32rpx; padding: 28rpx 32rpx; background: $bg-card; border-radius: $radius-lg; box-shadow: 0 4rpx 12rpx rgba(15,23,42,.04); }
.hdr { display: flex; align-items: center; gap: 16rpx; margin-bottom: 16rpx; padding-bottom: 16rpx; border-bottom: 1rpx solid $border-color; }
.hdr .name { flex: 1; font-size: 30rpx; font-weight: 700; color: $text-primary; }
.hdr .status { font-size: 22rpx; padding: 4rpx 14rpx; border-radius: 8rpx; font-weight: 600; }
.hdr .status.st-0 { background: rgba(245,158,11,.15); color: #B45309; }
.hdr .status.st-10 { background: rgba(16,185,129,.15); color: $success; }
.hdr .status.st-20 { background: $bg-page; color: $text-placeholder; }
.hdr .status.st-30 { background: rgba(230,57,70,.15); color: $danger; }

.row { display: flex; align-items: center; padding: 8rpx 0; font-size: 24rpx; }
.row .lbl { width: 140rpx; color: $text-secondary; }
.row .v { flex: 1; color: $text-primary; font-variant-numeric: tabular-nums; }
.row .v.price { color: $brand-primary; font-weight: 700; font-size: 28rpx; }

.load-more { margin: 32rpx; padding: 24rpx; text-align: center; background: $bg-card; border-radius: $radius-md; color: $brand-primary; font-size: 26rpx; }
.end-text { text-align: center; margin: 32rpx; color: $text-placeholder; font-size: 22rpx; }
.bottom-space { height: 40rpx; }
</style>
