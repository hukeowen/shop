<template>
  <view class="page">
    <view class="hero">
      <view class="hero-l">本店会员 / 总会员</view>
      <view class="hero-big">{{ list.length }} / {{ list.length }}</view>
      <view class="hero-sub">含店主 + 顾客；推荐入店 {{ referralCount }} 人</view>
    </view>

    <view class="period-tabs">
      <view class="pt active">全部</view>
      <view class="pt">活跃</view>
      <view class="pt">高消费</view>
    </view>

    <view class="member-row" v-for="m in list" :key="m.userId">
      <view class="avatar" :style="avatarStyle(m)">{{ m.userId }}</view>
      <view class="m-info">
        <view class="nm">{{ m.nickname || m.mobile || '会员 ' + m.userId }}</view>
        <view class="desc">
          <text v-if="m.parentUserId">推荐人 #{{ m.parentUserId }}</text>
          <text v-else>顶级用户</text>
          ·
          <text>已购 {{ m.orderCount }} 单 ¥{{ fen2yuan(m.orderAmount) }}</text>
        </view>
      </view>
      <view class="m-stats">
        <view class="star">★ {{ m.currentStar || 0 }}</view>
        <view class="bal">余 ¥{{ fen2yuan(m.balance) }}</view>
        <view class="promo">推 ¥{{ fen2yuan(m.promoPointBalance) }}</view>
      </view>
    </view>

    <view class="empty" v-if="!list.length">暂无会员</view>
    <view class="bottom-space"></view>
  </view>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { fen2yuan } from '../../utils/format.js';
import { listMembers } from '../../api/report.js';

const list = ref([]);
const referralCount = computed(() => list.value.filter(m => m.parentUserId && m.parentUserId > 0).length);

function avatarStyle(m) {
  const palette = ['#ffd1ba,#ff6b35', '#c9e0ff,#6196f0', '#d3f4d3,#4cb84c', '#ffd0dc,#ee5a8b'];
  const idx = (m.userId || 0) % palette.length;
  return `background: linear-gradient(135deg, ${palette[idx]});`;
}

async function load() {
  try { list.value = await listMembers(1, 50) || []; }
  catch { list.value = []; }
}
onMounted(load);
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  min-height: 100vh; background: $bg-page; padding-bottom: 32rpx;
}
.hero {
  background: linear-gradient(135deg, #FF6B35 0%, #FF9A4A 100%);
  color: #fff;
  padding: 32rpx 28rpx;
  margin: 24rpx; border-radius: 24rpx;
  .hero-l { font-size: 24rpx; opacity: 0.85; }
  .hero-big { font-size: 64rpx; font-weight: 800; margin: 8rpx 0 4rpx; font-variant-numeric: tabular-nums; }
  .hero-sub { font-size: 22rpx; opacity: 0.85; }
}
.period-tabs {
  display: flex; padding: 16rpx 24rpx; gap: 12rpx; background: #fff; border-bottom: 1rpx solid $border-color;
  .pt {
    padding: 8rpx 28rpx; border-radius: 999rpx;
    background: #f0f0f3; font-size: 26rpx;
    &.active { background: $brand-primary; color: #fff; }
  }
}
.member-row {
  display: flex; align-items: center;
  margin: 0 24rpx 16rpx;
  padding: 24rpx;
  background: #fff;
  border-radius: 16rpx;
}
.avatar {
  width: 80rpx; height: 80rpx; border-radius: 50%;
  color: #fff; display: flex; align-items: center; justify-content: center;
  font-weight: 700; font-size: 32rpx; margin-right: 20rpx;
}
.m-info { flex: 1; }
.m-info .nm { font-size: 30rpx; font-weight: 600; }
.m-info .desc { font-size: 22rpx; color: $text-secondary; margin-top: 4rpx; }
.m-stats {
  text-align: right; font-size: 22rpx; color: $text-secondary;
  .star { color: $warning; font-weight: 700; font-size: 26rpx; }
  .bal { margin-top: 4rpx; }
  .promo { color: $brand-primary; font-weight: 600; }
}
.empty { text-align: center; padding: 80rpx 0; color: $text-placeholder; font-size: 24rpx; }
.bottom-space { height: 80rpx; }
</style>
