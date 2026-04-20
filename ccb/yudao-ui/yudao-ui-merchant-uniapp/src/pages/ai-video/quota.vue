<template>
  <view class="page">
    <view class="head">
      <view class="label">本月剩余</view>
      <view class="value">
        <text class="big">{{ quota.total - quota.used }}</text>
        <text class="small"> / {{ quota.total }} 次</text>
      </view>
      <view class="bar">
        <view class="fill" :style="{ width: percent + '%' }"></view>
      </view>
      <view class="sub">已使用 {{ quota.used }} 次 · 每月 1 日重置</view>
    </view>

    <view class="card">
      <view class="section-title">购买加量包</view>
      <view class="packages">
        <view
          v-for="p in packages"
          :key="p.id"
          class="package"
          :class="{ selected: selected === p.id, hot: p.hot }"
          @click="selected = p.id"
        >
          <view v-if="p.hot" class="hot-tag">热销</view>
          <view class="pkg-count">{{ p.count }} 次</view>
          <view class="pkg-price">¥{{ p.price }}</view>
          <view class="pkg-unit">约 ¥{{ (p.price / p.count).toFixed(1) }} / 次</view>
        </view>
      </view>
    </view>

    <view class="card tips">
      <view class="tip-title">说明</view>
      <view class="tip-item">· 加量包永久有效，不过期</view>
      <view class="tip-item">· 优先消耗月度赠送配额，再扣加量包</view>
      <view class="tip-item">· 生成失败不扣次数</view>
    </view>

    <view class="actions safe-bottom">
      <button class="btn primary" :disabled="!selectedPkg" @click="onBuy">
        {{ selectedPkg ? `立即支付 ¥${selectedPkg.price}` : '请选择套餐' }}
      </button>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getQuota, buyQuota } from '../../api/aiVideo.js';

const quota = ref({ total: 10, used: 0 });
const selected = ref(2);

const packages = [
  { id: 1, count: 10, price: 29 },
  { id: 2, count: 50, price: 99, hot: true },
  { id: 3, count: 200, price: 299 },
];

const percent = computed(() => {
  return quota.value.total ? (quota.value.used / quota.value.total) * 100 : 0;
});

const selectedPkg = computed(() => packages.find((p) => p.id === selected.value));

async function load() {
  quota.value = await getQuota();
}

async function onBuy() {
  if (!selectedPkg.value) return;
  const r = await buyQuota(selectedPkg.value.count);
  if (r.ok) {
    uni.showToast({ title: '购买成功', icon: 'success' });
    load();
  } else {
    uni.showToast({ title: r.msg, icon: 'none' });
  }
}

onShow(() => {
  load();
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  padding: 24rpx 24rpx 200rpx;
  min-height: 100vh;
}

.head {
  padding: 48rpx 32rpx;
  margin-bottom: 24rpx;
  background: linear-gradient(135deg, $brand-primary, #ff9b5e);
  color: #fff;
  border-radius: $radius-lg;

  .label {
    font-size: 26rpx;
    opacity: 0.85;
  }

  .value {
    margin-top: 12rpx;
  }

  .big {
    font-size: 72rpx;
    font-weight: 800;
  }

  .small {
    font-size: 28rpx;
    opacity: 0.85;
    margin-left: 8rpx;
  }

  .bar {
    margin-top: 20rpx;
    height: 12rpx;
    background: rgba(255, 255, 255, 0.3);
    border-radius: 6rpx;
    overflow: hidden;
  }

  .fill {
    height: 100%;
    background: #fff;
    border-radius: 6rpx;
  }

  .sub {
    margin-top: 16rpx;
    font-size: 22rpx;
    opacity: 0.8;
  }
}

.card {
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 32rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 24rpx;
}

.packages {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 16rpx;

  .package {
    position: relative;
    padding: 32rpx 12rpx 24rpx;
    background: #f6f7f9;
    border: 3rpx solid transparent;
    border-radius: $radius-md;
    text-align: center;

    &.selected {
      background: $brand-primary-light;
      border-color: $brand-primary;
    }

    .hot-tag {
      position: absolute;
      top: -16rpx;
      left: 50%;
      transform: translateX(-50%);
      background: $danger;
      color: #fff;
      font-size: 20rpx;
      padding: 4rpx 16rpx;
      border-radius: $radius-pill;
    }

    .pkg-count {
      font-size: 28rpx;
      font-weight: 600;
      color: $text-primary;
    }

    .pkg-price {
      margin-top: 12rpx;
      font-size: 40rpx;
      font-weight: 700;
      color: $brand-primary;
    }

    .pkg-unit {
      margin-top: 8rpx;
      font-size: 20rpx;
      color: $text-secondary;
    }
  }
}

.tips {
  background: #fff8ef;
  box-shadow: none;

  .tip-title {
    font-size: 26rpx;
    font-weight: 600;
    color: $warning;
    margin-bottom: 12rpx;
  }

  .tip-item {
    font-size: 24rpx;
    color: $text-regular;
    line-height: 1.8;
  }
}

.actions {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 24rpx 32rpx calc(env(safe-area-inset-bottom) + 24rpx);
  background: #fff;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.05);
}

.btn {
  width: 100%;
  height: 96rpx;
  line-height: 96rpx;
  font-size: 32rpx;
  font-weight: 600;
  border-radius: $radius-md;

  &.primary {
    background: $brand-primary;
    color: #fff;
  }

  &[disabled] {
    background: $text-placeholder;
    color: #fff;
  }

  &::after {
    border: none;
  }
}
</style>
