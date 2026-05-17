<template>
  <view class="page">
    <view class="header">
      <text class="title">结算单 #{{ settleId }}</text>
    </view>

    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="!payouts.length" class="empty">该结算无中奖记录</view>

    <view v-else>
      <view class="summary">
        <view class="sum-item">
          <text class="label">总中奖人数</text>
          <text class="val">{{ payouts.length }}</text>
        </view>
        <view class="sum-item">
          <text class="label">总发放金额</text>
          <text class="val brand">¥{{ (totalAmt / 100).toFixed(2) }}</text>
        </view>
      </view>

      <view class="list-title">中奖明细</view>
      <view v-for="p in payouts" :key="p.id" class="payout-row">
        <view class="star-tag">{{ p.star }}星</view>
        <view class="main">
          <text class="uid">用户 #{{ p.userId }}</text>
          <text class="mode">{{ p.mode === 'LOTTERY' ? '抽中' : '均分' }}</text>
        </view>
        <text class="amt">+¥{{ (p.amount / 100).toFixed(2) }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { listSpuPoolPayouts } from '../../api/promo.js';

const settleId = ref(0);
const payouts = ref([]);
const loading = ref(true);

const totalAmt = computed(() => payouts.value.reduce((s, p) => s + (p.amount || 0), 0));

onLoad(async (q) => {
  settleId.value = q.settleId ? Number(q.settleId) : 0;
  if (!settleId.value) {
    loading.value = false;
    return;
  }
  try {
    const r = await listSpuPoolPayouts(settleId.value);
    payouts.value = Array.isArray(r) ? r : [];
  } finally {
    loading.value = false;
  }
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  padding: 24rpx;
  min-height: 100vh;
  background: #f6f7f9;
}
.header {
  margin-bottom: 16rpx;
  .title {
    font-size: 32rpx;
    font-weight: 700;
    color: $text-primary;
  }
}
.empty {
  text-align: center;
  padding: 80rpx 0;
  color: $text-placeholder;
  font-size: 26rpx;
}
.summary {
  display: flex;
  gap: 16rpx;
  background: #fff;
  border-radius: $radius-md;
  padding: 24rpx;
  margin-bottom: 16rpx;
  .sum-item {
    flex: 1;
    .label {
      display: block;
      font-size: 22rpx;
      color: $text-secondary;
    }
    .val {
      display: block;
      margin-top: 6rpx;
      font-size: 36rpx;
      font-weight: 700;
      color: $text-primary;
      &.brand { color: $brand-primary; }
    }
  }
}
.list-title {
  font-size: 26rpx;
  color: $text-secondary;
  margin: 16rpx 0 12rpx;
}
.payout-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  background: #fff;
  border-radius: $radius-md;
  margin-bottom: 12rpx;
  .star-tag {
    flex: 0 0 60rpx;
    height: 60rpx;
    line-height: 60rpx;
    text-align: center;
    background: linear-gradient(135deg, #ffd6b8, #ff6b35);
    color: #fff;
    border-radius: $radius-md;
    font-size: 22rpx;
    font-weight: 600;
  }
  .main {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 4rpx;
    .uid {
      font-size: 26rpx;
      color: $text-primary;
    }
    .mode {
      font-size: 22rpx;
      color: $text-secondary;
    }
  }
  .amt {
    font-size: 28rpx;
    font-weight: 700;
    color: $brand-primary;
  }
}
</style>
