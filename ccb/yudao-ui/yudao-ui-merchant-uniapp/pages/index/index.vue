<template>
  <view class="page">
    <view class="header safe-top">
      <view>
        <view class="greeting">早上好，{{ userStore.user?.nickname || '摊主' }}</view>
        <view class="shop-name">{{ userStore.shop?.name || '未关联店铺' }}</view>
      </view>
      <view class="status-pill">
        <view class="dot"></view>
        营业中
      </view>
    </view>

    <view class="quick card">
      <view class="quick-item" @click="jumpAi">
        <view class="icon ai">AI</view>
        <text>一键成片</text>
      </view>
      <view class="quick-item" @click="jumpVerify">
        <view class="icon verify">扫</view>
        <text>扫码核销</text>
      </view>
      <view class="quick-item" @click="jumpDeliver">
        <view class="icon deliver">发</view>
        <text>待发货</text>
      </view>
      <view class="quick-item" @click="jumpOrders">
        <view class="icon order">单</view>
        <text>订单列表</text>
      </view>
    </view>

    <view class="stat-title">今日概览</view>
    <view class="stats">
      <view class="stat card">
        <view class="stat-label">订单数</view>
        <view class="stat-value">{{ data?.today.orderCount ?? '-' }}</view>
      </view>
      <view class="stat card">
        <view class="stat-label">销售额</view>
        <view class="stat-value">¥{{ fen2yuan(data?.today.salesAmount || 0) }}</view>
      </view>
      <view class="stat card">
        <view class="stat-label">新会员</view>
        <view class="stat-value">{{ data?.today.newMembers ?? '-' }}</view>
      </view>
      <view class="stat card">
        <view class="stat-label">待处理</view>
        <view class="stat-value warn">{{ data?.today.pendingOrders ?? '-' }}</view>
      </view>
    </view>

    <view class="section card">
      <view class="section-head">
        <text class="title">最近 7 天销售趋势</text>
      </view>
      <view class="chart">
        <view
          class="bar"
          v-for="(v, i) in data?.trend.sales || []"
          :key="i"
          :style="{ height: barHeight(v) + '%' }"
        >
          <view class="bar-value">¥{{ (v / 100).toFixed(0) }}</view>
          <view class="bar-fill"></view>
          <text class="bar-label">{{ data?.trend.labels[i] }}</text>
        </view>
      </view>
    </view>

    <view class="section card">
      <view class="section-head">
        <text class="title">热销商品 Top 3</text>
      </view>
      <view class="rank-list">
        <view
          class="rank-item"
          v-for="(p, i) in data?.topProducts || []"
          :key="p.name"
        >
          <view class="rank-no" :class="'rank-' + (i + 1)">{{ i + 1 }}</view>
          <view class="rank-name">{{ p.name }}</view>
          <view class="rank-meta">
            <text>售出 {{ p.count }}</text>
            <text class="rank-amount">¥{{ fen2yuan(p.amount) }}</text>
          </view>
        </view>
      </view>
    </view>

    <view class="bottom-space" />
  </view>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { onPullDownRefresh } from '@dcloudio/uni-app';
import { getDashboard } from '../../api/report.js';
import { fen2yuan } from '../../utils/format.js';
import { useUserStore } from '../../store/user.js';

const userStore = useUserStore();
const data = ref(null);

async function load() {
  data.value = await getDashboard();
}

function barHeight(v) {
  const max = Math.max(...(data.value?.trend.sales || [1]));
  return max ? Math.max(8, (v / max) * 100) : 0;
}

function jumpAi() {
  uni.switchTab({ url: '/pages/ai-video/index' });
}
function jumpOrders() {
  uni.switchTab({ url: '/pages/order/list' });
}
function jumpDeliver() {
  uni.switchTab({ url: '/pages/order/list' });
}
function jumpVerify() {
  uni.switchTab({ url: '/pages/order/list' });
}

onMounted(() => {
  if (!userStore.loggedIn) {
    uni.reLaunch({ url: '/pages/login/index' });
    return;
  }
  load();
});

onPullDownRefresh(async () => {
  await load();
  uni.stopPullDownRefresh();
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  padding: 0 24rpx 48rpx;
}

.safe-top {
  padding-top: calc(env(safe-area-inset-top) + 24rpx);
}

.header {
  padding: 16rpx 12rpx 40rpx;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;

  .greeting {
    font-size: 28rpx;
    color: $text-secondary;
  }

  .shop-name {
    font-size: 40rpx;
    font-weight: 700;
    color: $text-primary;
    margin-top: 8rpx;
  }

  .status-pill {
    display: inline-flex;
    align-items: center;
    gap: 8rpx;
    height: 48rpx;
    padding: 0 20rpx;
    background: #e8f5ef;
    color: $success;
    border-radius: $radius-pill;
    font-size: 24rpx;

    .dot {
      width: 12rpx;
      height: 12rpx;
      background: $success;
      border-radius: 50%;
    }
  }
}

.card {
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 32rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.quick {
  display: flex;
  justify-content: space-between;
  margin-bottom: 32rpx;

  .quick-item {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 12rpx;
    font-size: 24rpx;
    color: $text-regular;
  }

  .icon {
    width: 88rpx;
    height: 88rpx;
    line-height: 88rpx;
    text-align: center;
    color: #fff;
    border-radius: $radius-md;
    font-size: 30rpx;
    font-weight: 600;

    &.ai {
      background: linear-gradient(135deg, #ff6b35, #ff9b5e);
    }
    &.verify {
      background: #3b82f6;
    }
    &.deliver {
      background: #10b981;
    }
    &.order {
      background: #8b5cf6;
    }
  }
}

.stat-title {
  margin: 8rpx 12rpx 20rpx;
  font-size: 28rpx;
  color: $text-secondary;
  font-weight: 500;
}

.stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
  margin-bottom: 32rpx;

  .stat {
    padding: 24rpx;
    min-height: 160rpx;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
  }

  .stat-label {
    font-size: 24rpx;
    color: $text-secondary;
  }

  .stat-value {
    font-size: 44rpx;
    font-weight: 700;
    color: $text-primary;

    &.warn {
      color: $warning;
    }
  }
}

.section {
  margin-bottom: 24rpx;

  .section-head {
    margin-bottom: 24rpx;
    .title {
      font-size: 30rpx;
      font-weight: 600;
      color: $text-primary;
    }
  }
}

.chart {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  height: 240rpx;
  padding-top: 40rpx;

  .bar {
    flex: 1;
    height: 100%;
    margin: 0 6rpx;
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: flex-end;

    .bar-value {
      font-size: 18rpx;
      color: $text-secondary;
      margin-bottom: 6rpx;
    }

    .bar-fill {
      width: 100%;
      background: linear-gradient(180deg, $brand-primary, #ffb48a);
      border-radius: 8rpx 8rpx 0 0;
      flex: 1;
      min-height: 12rpx;
    }

    .bar-label {
      font-size: 20rpx;
      color: $text-secondary;
      margin-top: 10rpx;
      position: absolute;
      bottom: -36rpx;
    }
  }
}

.rank-list {
  .rank-item {
    display: flex;
    align-items: center;
    padding: 20rpx 0;
    border-bottom: 1rpx solid $border-color;

    &:last-child {
      border-bottom: none;
    }
  }

  .rank-no {
    width: 44rpx;
    height: 44rpx;
    line-height: 44rpx;
    text-align: center;
    border-radius: 50%;
    font-size: 24rpx;
    font-weight: 700;
    color: #fff;
    background: $text-placeholder;
    margin-right: 20rpx;

    &.rank-1 {
      background: #ffcc00;
    }
    &.rank-2 {
      background: #c0c5cf;
    }
    &.rank-3 {
      background: #d6793a;
    }
  }

  .rank-name {
    flex: 1;
    font-size: 28rpx;
    color: $text-primary;
  }

  .rank-meta {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    font-size: 22rpx;
    color: $text-secondary;

    .rank-amount {
      color: $brand-primary;
      font-weight: 600;
      font-size: 26rpx;
      margin-top: 4rpx;
    }
  }
}

.bottom-space {
  height: 80rpx;
}
</style>
