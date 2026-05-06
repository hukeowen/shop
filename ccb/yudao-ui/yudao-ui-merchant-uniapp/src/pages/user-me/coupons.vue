<template>
  <view class="page">
    <view class="topbar safe-top">
      <text class="back" @click="goBack">‹</text>
      <text class="title">我的优惠券</text>
      <view style="width:60rpx"></view>
    </view>

    <view class="filter">
      <text class="it" :class="{ active: tab === 0 }" @click="tab = 0">全部 {{ list.length }}</text>
      <text class="it" :class="{ active: tab === 1 }" @click="tab = 1">未使用 {{ countByStatus(0) }}</text>
      <text class="it" :class="{ active: tab === 2 }" @click="tab = 2">已使用 {{ countByStatus(1) }}</text>
      <text class="it" :class="{ active: tab === 3 }" @click="tab = 3">已过期 {{ countByStatus(2) }}</text>
    </view>

    <view v-if="loading" class="empty-tip">加载中...</view>
    <view v-else-if="!filteredList.length" class="empty-state">
      <view class="empty-emoji">🎁</view>
      <view class="empty-title">这里还没有券</view>
      <view class="empty-sub">去店铺详情页领取吧</view>
    </view>
    <view v-else class="list">
      <view
        v-for="c in filteredList"
        :key="c.id"
        class="coupon-row"
        :class="{ used: c.status === 1, expired: c.status === 2 }"
      >
        <view class="amt-block">
          <view class="amt"><text class="cny">¥</text>{{ fen2yuan(c.discountAmount) }}</view>
        </view>
        <view class="info">
          <view class="cond">{{ c.minAmount > 0 ? `满 ${fen2yuan(c.minAmount)} 可用` : '无门槛' }}</view>
          <view class="meta">{{ formatTime(c.expireTime) }} 过期</view>
        </view>
        <view class="state">
          <text v-if="c.status === 0">未使用</text>
          <text v-else-if="c.status === 1">已使用</text>
          <text v-else>已过期</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { request } from '../../api/request.js';
import { fen2yuan } from '../../utils/format.js';

const list = ref([]);
const tab = ref(0); // 0=全部 1=未使用 2=已使用 3=已过期
const loading = ref(true);

const filteredList = computed(() => {
  if (tab.value === 0) return list.value;
  const s = tab.value - 1; // tab 1→status 0; tab 2→status 1; tab 3→status 2
  return list.value.filter((c) => c.status === s);
});
function countByStatus(s) {
  return list.value.filter((c) => c.status === s).length;
}

function formatTime(t) {
  if (!t) return '';
  const d = new Date(typeof t === 'string' ? t.replace(' ', 'T') : t);
  if (isNaN(d.getTime())) return '';
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

async function load() {
  loading.value = true;
  try {
    // 跨租户拉所有领过的券
    const r = await request({ url: '/app-api/merchant/mini/coupon/my-list' });
    list.value = Array.isArray(r) ? r : [];
  } catch { list.value = []; }
  loading.value = false;
}

function goBack() { uni.navigateBack({ fail: () => uni.reLaunch({ url: '/pages/user-home/index' }) }); }

onMounted(load);
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page { min-height: 100vh; background: $bg-page; padding-bottom: 80rpx; }
.safe-top { padding-top: env(safe-area-inset-top); }

.topbar {
  display: flex; align-items: center;
  padding: 24rpx 32rpx;
  background: $bg-card;
  border-bottom: 2rpx solid $border-color;
  .back { font-size: 44rpx; color: $text-primary; padding-right: 16rpx; }
  .title { flex: 1; font-size: 32rpx; font-weight: 700; color: $text-primary; text-align: center; }
}

.filter {
  display: flex; padding: 24rpx 32rpx; gap: 24rpx; overflow-x: auto;
  background: $bg-card;
  .it {
    font-size: 26rpx; color: $text-secondary;
    padding: 8rpx 24rpx; border-radius: 999rpx;
    background: $bg-page;
    flex-shrink: 0;
    &.active { background: $brand-primary; color: #fff; font-weight: 700; }
  }
}

.empty-tip { text-align: center; color: $text-secondary; padding: 120rpx 0; font-size: 26rpx; }
.empty-state {
  text-align: center; padding: 160rpx 0;
  .empty-emoji { font-size: 96rpx; margin-bottom: 16rpx; }
  .empty-title { font-size: 30rpx; color: $text-primary; font-weight: 700; }
  .empty-sub { margin-top: 8rpx; font-size: 24rpx; color: $text-secondary; }
}

.list { padding: 24rpx 32rpx; display: flex; flex-direction: column; gap: 16rpx; }

.coupon-row {
  display: flex; align-items: center; gap: 24rpx;
  padding: 24rpx;
  background: linear-gradient(135deg, #fff 0%, #fff5ef 100%);
  border-radius: $radius-md;
  border: 2rpx dashed $brand-primary;
  &.used, &.expired { background: #fafafa; border-color: $border-color; opacity: 0.6; }
}
.amt-block { text-align: center; flex-shrink: 0; min-width: 140rpx; }
.amt {
  font-size: 44rpx; font-weight: 800; color: $brand-primary;
  font-variant-numeric: tabular-nums; line-height: 1;
  .cny { font-size: 22rpx; }
}
.info { flex: 1; min-width: 0; }
.cond { font-size: 26rpx; color: $text-primary; font-weight: 600; }
.meta { margin-top: 8rpx; font-size: 22rpx; color: $text-secondary; }
.state {
  font-size: 22rpx; color: $brand-primary; font-weight: 700;
  flex-shrink: 0;
}
.coupon-row.used .state, .coupon-row.expired .state { color: $text-placeholder; }
</style>
