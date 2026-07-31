<template>
  <view class="page">
    <view :style="sbhStyle" class="sbh-spacer"></view>
    <view class="hdr safe-top">
      <view class="back" @click="goBack">‹</view>
      <text class="title">全部分类</text>
      <view class="right"></view>
    </view>
    <view class="hero">
      <view class="hero-t">想吃点儿什么？</view>
      <view class="hero-s">13 个分类 · {{ totalShops }} 家小店在身边</view>
    </view>

    <view class="grid">
      <view
        v-for="b in BIZ_TYPES"
        :key="b.key"
        :class="['c', b.key]"
        @click="goShops(b)"
      >
        <view class="c-emoji">{{ b.emoji }}</view>
        <view class="c-name">{{ b.label }}</view>
        <view class="c-sub">{{ b.sub }}</view>
      </view>
    </view>

    <view class="bottom-space"></view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { request } from '../../api/request.js';
import { sbhSpacerStyle } from '../../utils/safeTop.js';
const sbhStyle = sbhSpacerStyle(); // 小程序状态栏占位（真机高度覆盖 --status-bar-height）

// V041: 13 个分类 — key 与后端 BUSINESS_CONTEXT_MAP 一一对应
const BIZ_TYPES = [
  { key: 'bbq', label: '烧烤夜市', emoji: '🍢', sub: '炭火 · 现烤现卖' },
  { key: 'snack', label: '小吃快餐', emoji: '🥟', sub: '街头 · 现做现做' },
  { key: 'drink', label: '奶茶咖啡', emoji: '🧋', sub: '解暑 · 续命' },
  { key: 'restaurant', label: '正餐餐厅', emoji: '🍽', sub: '聚餐 · 商务' },
  { key: 'fruit', label: '水果生鲜', emoji: '🍓', sub: '现切 · 当日鲜' },
  { key: 'super', label: '超市便利', emoji: '🛒', sub: '日用 · 24h' },
  { key: 'tea', label: '茶叶酒水', emoji: '🍵', sub: '老茶 · 好酒' },
  { key: 'tea_house', label: '茶楼茶馆', emoji: '🏯', sub: '静坐 · 时光' },
  { key: 'bakery', label: '烘焙甜品', emoji: '🥐', sub: '现烤 · 手作' },
  { key: 'clothing', label: '服装鞋帽', emoji: '👕', sub: '潮流 · 经典' },
  { key: 'massage', label: '按摩 SPA', emoji: '💆', sub: '放松 · 养生' },
  { key: 'beauty', label: '美容美发', emoji: '💄', sub: '焕新 · 时尚' },
  { key: 'other', label: '其他', emoji: '🏪', sub: '更多惊喜' },
];

const totalShops = ref(0);

async function loadTotal() {
  // 拉总店铺数（仅头部展示用，不必精确）
  try {
    const r = await request({ url: '/app-api/merchant/shop/public/list?pageNo=1&pageSize=1' });
    totalShops.value = r?.total || 0;
  } catch {}
}

function goShops(b) {
  uni.navigateTo({
    url: `/pages/categories/shops?type=${b.key}&label=${encodeURIComponent(b.label)}&emoji=${encodeURIComponent(b.emoji)}`,
  });
}
function goBack() { uni.navigateBack({ fail: () => uni.reLaunch({ url: '/pages/user-home/index' }) }); }

onMounted(loadTotal);
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
  padding: 48rpx 32rpx 32rpx;
  background: linear-gradient(135deg, #fff5ef 0%, #ffe1c8 100%);
}
.hero-t {
  font-size: 44rpx;
  font-weight: 800;
  color: #1f2937;
  letter-spacing: -1rpx;
}
.hero-s {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #6b7280;
}

.grid {
  margin: 24rpx 24rpx 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
}
.c {
  padding: 32rpx 24rpx;
  border-radius: 24rpx;
  background: #fff;
  border: 2rpx solid transparent;
  position: relative;
  overflow: hidden;
  transition: transform 0.15s;
  min-height: 180rpx;
  &:active { transform: scale(0.97); }
}
.c::before {
  content: '';
  position: absolute;
  top: -40rpx; right: -40rpx;
  width: 140rpx; height: 140rpx;
  border-radius: 50%;
  opacity: 0.5;
}
.c.bbq        { background: linear-gradient(135deg, #fff5ef, #ffe1c8); &::before { background: #ff9b5e; } }
.c.snack      { background: linear-gradient(135deg, #fef3c7, #fde68a); &::before { background: #f59e0b; } }
.c.drink      { background: linear-gradient(135deg, #ecfdf5, #a7f3d0); &::before { background: #10b981; } }
.c.restaurant { background: linear-gradient(135deg, #fee2e2, #fecaca); &::before { background: #ef4444; } }
.c.fruit      { background: linear-gradient(135deg, #fce7f3, #fbcfe8); &::before { background: #ec4899; } }
.c.super      { background: linear-gradient(135deg, #dbeafe, #bfdbfe); &::before { background: #3b82f6; } }
.c.tea        { background: linear-gradient(135deg, #fef3c7, #fed7aa); &::before { background: #d97706; } }
.c.tea_house  { background: linear-gradient(135deg, #f3e8ff, #e9d5ff); &::before { background: #7c3aed; } }
.c.bakery     { background: linear-gradient(135deg, #fef3c7, #fbbf24); &::before { background: #f59e0b; } }
.c.clothing   { background: linear-gradient(135deg, #ddd6fe, #c7d2fe); &::before { background: #6366f1; } }
.c.massage    { background: linear-gradient(135deg, #ccfbf1, #99f6e4); &::before { background: #14b8a6; } }
.c.beauty     { background: linear-gradient(135deg, #fce7f3, #f9a8d4); &::before { background: #db2777; } }
.c.other      { background: linear-gradient(135deg, #f3f4f6, #e5e7eb); &::before { background: #6b7280; } }
.c-emoji {
  font-size: 56rpx;
  line-height: 1;
  position: relative;
  z-index: 1;
}
.c-name {
  margin-top: 12rpx;
  font-size: 30rpx;
  font-weight: 700;
  color: #1f2937;
  position: relative;
  z-index: 1;
}
.c-sub {
  margin-top: 4rpx;
  font-size: 20rpx;
  color: #6b7280;
  position: relative;
  z-index: 1;
}
.bottom-space { height: 32rpx; }
</style>
