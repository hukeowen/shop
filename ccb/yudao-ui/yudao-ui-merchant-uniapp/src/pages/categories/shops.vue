<template>
  <view class="page">
    <view class="hdr safe-top">
      <view class="back" @click="goBack">‹</view>
      <text class="title">{{ label || '分类店铺' }}</text>
      <view class="right"></view>
    </view>

    <view class="hero">
      <view class="hero-emoji">{{ emoji || '🏪' }}</view>
      <view class="hero-info">
        <view class="hero-t">{{ label || '分类' }}</view>
        <view class="hero-s">{{ total }} 家小店 · 按销量排序</view>
      </view>
    </view>

    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="!list.length" class="empty">
      <view class="empty-emoji">🌱</view>
      <view class="empty-t">这个分类还没有店铺</view>
      <view class="empty-s">换个分类看看，或回首页探店</view>
      <view class="empty-btn" @click="goBack">返回分类</view>
    </view>
    <view v-else class="list">
      <view
        v-for="s in list"
        :key="s.id"
        class="lr"
        @click="goShop(s)"
      >
        <view class="lr-c" :style="coverStyle(s)">
          {{ initialOf(s) }}
          <text v-if="s.isOpenNow" class="lr-badge">营业中</text>
          <text v-else-if="s.operatingStatus === 'OUTSIDE_HOURS'" class="lr-badge gray">营业时间外</text>
        </view>
        <view class="lr-b">
          <view class="lr-n">{{ s.shopName }}</view>
          <view class="lr-d">{{ s.description || s.address || '欢迎光临' }}</view>
          <view class="lr-f">
            <text class="lr-r">★ {{ formatRating(s.avgRating) }}</text>
            <text class="dot">·</text>
            <text>月售 {{ s.sales30d || 0 }}</text>
          </view>
        </view>
      </view>
    </view>

    <view class="bottom-space"></view>
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { request } from '../../api/request.js';

const type = ref('');
const label = ref('');
const emoji = ref('');
const list = ref([]);
const total = ref(0);
const loading = ref(true);

function coverStyle(s) {
  // 用 tenantId 哈希分配 8 套颜色，避免每张都灰
  const palette = [
    'linear-gradient(135deg,#fff1e8,#ffe1c8)',
    'linear-gradient(135deg,#fce7f3,#fbcfe8)',
    'linear-gradient(135deg,#ecfdf5,#a7f3d0)',
    'linear-gradient(135deg,#fef3c7,#fde68a)',
    'linear-gradient(135deg,#dbeafe,#bfdbfe)',
    'linear-gradient(135deg,#f3e8ff,#e9d5ff)',
    'linear-gradient(135deg,#ccfbf1,#99f6e4)',
    'linear-gradient(135deg,#fee2e2,#fecaca)',
  ];
  const idx = (Number(s.tenantId || s.id) || 0) % palette.length;
  return `background: ${palette[idx]};`;
}

function initialOf(s) {
  return (s.shopName || '店')[0];
}
function formatRating(r) {
  return r == null ? '5.0' : Number(r).toFixed(1);
}

async function load() {
  loading.value = true;
  try {
    const r = await request({
      url: `/app-api/merchant/shop/public/list?pageNo=1&pageSize=50&businessType=${type.value}`,
    });
    list.value = Array.isArray(r) ? r : (r?.list || []);
    total.value = r?.total ?? list.value.length;
  } catch {
    list.value = [];
    total.value = 0;
  }
  loading.value = false;
}

function goShop(s) {
  uni.navigateTo({ url: `/pages/shop-home/index?tenantId=${s.tenantId}` });
}
function goBack() { uni.navigateBack(); }

onLoad((q) => {
  type.value = q.type || '';
  label.value = decodeURIComponent(q.label || '分类');
  emoji.value = decodeURIComponent(q.emoji || '🏪');
  if (type.value) load();
});
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #fafafa;
  padding-bottom: 96rpx;
}
.safe-top { padding-top: calc(max(env(safe-area-inset-top, 0px), var(--status-bar-height, 0px)) + 16rpx) !important; }

.hdr {
  display: flex;
  align-items: center;
  padding: 16rpx 24rpx;
  background: #fff;
  border-bottom: 1rpx solid rgba(0, 0, 0, 0.04);
}
.back {
  width: 56rpx; height: 56rpx;
  line-height: 56rpx;
  text-align: center;
  font-size: 44rpx;
  color: #1f2937;
  margin-left: -12rpx;
}
.title {
  flex: 1;
  text-align: center;
  font-size: 32rpx;
  font-weight: 700;
  color: #1f2937;
  margin-right: 56rpx;
}

.hero {
  padding: 40rpx 32rpx 32rpx;
  background: linear-gradient(135deg, #fff5ef 0%, #ffe1c8 100%);
  display: flex;
  align-items: center;
  gap: 24rpx;
}
.hero-emoji {
  font-size: 88rpx;
  line-height: 1;
}
.hero-info { flex: 1; min-width: 0; }
.hero-t {
  font-size: 44rpx;
  font-weight: 800;
  color: #1f2937;
  letter-spacing: -1rpx;
}
.hero-s {
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #6b7280;
}

.empty {
  padding: 120rpx 48rpx;
  text-align: center;
  color: #9ca3af;
  font-size: 26rpx;
}
.empty-emoji { font-size: 96rpx; margin-bottom: 16rpx; }
.empty-t { font-size: 32rpx; font-weight: 700; color: #1f2937; margin-bottom: 8rpx; }
.empty-s { font-size: 24rpx; color: #6b7280; margin-bottom: 32rpx; }
.empty-btn {
  display: inline-block;
  padding: 20rpx 48rpx;
  background: #ff6b35;
  color: #fff;
  border-radius: 999rpx;
  font-size: 26rpx;
  font-weight: 600;
}

.list {
  padding: 24rpx 24rpx 0;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.lr {
  display: flex;
  gap: 20rpx;
  background: #fff;
  border-radius: 20rpx;
  padding: 20rpx;
  border: 1rpx solid rgba(0, 0, 0, 0.04);
}
.lr-c {
  width: 140rpx;
  height: 140rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 56rpx;
  font-weight: 700;
  color: rgba(0, 0, 0, 0.35);
  flex-shrink: 0;
  position: relative;
}
.lr-badge {
  position: absolute;
  top: 8rpx; left: 8rpx;
  padding: 4rpx 12rpx;
  background: #10b981;
  color: #fff;
  font-size: 18rpx;
  font-weight: 600;
  border-radius: 999rpx;
}
.lr-badge.gray {
  background: rgba(0, 0, 0, 0.5);
}
.lr-b {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6rpx;
}
.lr-n {
  font-size: 30rpx;
  font-weight: 700;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lr-d {
  font-size: 22rpx;
  color: #6b7280;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lr-f {
  margin-top: 8rpx;
  display: flex;
  align-items: center;
  gap: 8rpx;
  font-size: 22rpx;
  color: #9ca3af;
}
.lr-r {
  color: #ff6b35;
  font-weight: 700;
  font-size: 24rpx;
}
.dot { color: #d1d5db; }
.bottom-space { height: 32rpx; }
</style>
