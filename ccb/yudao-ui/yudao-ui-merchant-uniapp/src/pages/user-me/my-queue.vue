<template>
  <view class="page">
    <view class="hero">
      <view class="hero-title">我的商品队列</view>
      <view class="hero-sub">每条队列累计满 N 次后自动出队；A 层优先于 B 层返奖。</view>
    </view>

    <view v-if="loading" class="empty-tip">加载中…</view>

    <template v-else>
      <view v-if="!list.length" class="empty-tip">
        暂无队列。下单"参与推 N 反 1"的商品后即进入。
      </view>

      <view v-for="row in list" :key="row.spuId" class="card row-card">
        <view class="row-head">
          <text class="spu">商品 #{{ row.spuId }}</text>
          <text :class="['layer', row.layer === 'A' ? 'layer-a' : 'layer-b']">
            {{ row.layer === 'A' ? 'A 层（主动）' : 'B 层（被动）' }}
          </text>
        </view>

        <view class="progress-row">
          <view class="progress-label">
            已返 {{ row.accumulatedCount }} / {{ row.maxN || '?' }} 次
          </view>
          <view class="progress-bar">
            <view class="progress-fill" :style="{ width: progressPct(row) + '%' }" />
          </view>
        </view>

        <view class="meta-row">
          <text class="meta-label">已得推广积分</text>
          <text class="meta-value">{{ row.accumulatedAmount }} 分</text>
        </view>
        <view class="meta-row">
          <text class="meta-label">入队时间</text>
          <text class="meta-value">{{ formatTime(row.joinedAt) }}</text>
        </view>
        <view v-if="row.promotedAt" class="meta-row">
          <text class="meta-label">升 A 层时间</text>
          <text class="meta-value">{{ formatTime(row.promotedAt) }}</text>
        </view>
      </view>
    </template>

    <view class="card hint-card">
      <view class="card-title">规则说明</view>
      <view class="hint-item">• A 层：你已自购或推下级成交过；优先返奖</view>
      <view class="hint-item">• B 层：你仅自然消费过 1 单进队；A 空了才轮到</view>
      <view class="hint-item">• 每次返奖按你当前累计次数定位置和比例</view>
      <view class="hint-item">• 累计满 N 次出队，永不再返；B 层老队头永不过期</view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { listMyQueues } from '../../api/promo.js';

const list = ref([]);
const loading = ref(true);

function progressPct(row) {
  if (!row.maxN || row.maxN <= 0) return 0;
  const pct = Math.round((row.accumulatedCount / row.maxN) * 100);
  return Math.min(100, Math.max(0, pct));
}

function formatTime(ts) {
  if (!ts) return '—';
  const d = new Date(ts);
  if (isNaN(d.getTime())) return ts;
  const pad = (n) => (n < 10 ? '0' + n : n);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

async function load() {
  loading.value = true;
  try {
    const data = await listMyQueues();
    list.value = Array.isArray(data) ? data : [];
  } catch {
    list.value = [];
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  padding: 24rpx 24rpx 60rpx;
  min-height: 100vh;
}

.hero {
  background: linear-gradient(135deg, #ff9a4a, #ff6b35);
  border-radius: $radius-lg;
  padding: 36rpx 32rpx;
  color: #fff;
  margin-bottom: 24rpx;

  .hero-title {
    font-size: 34rpx;
    font-weight: 700;
  }

  .hero-sub {
    margin-top: 10rpx;
    font-size: 24rpx;
    opacity: 0.9;
    line-height: 1.5;
  }
}

.empty-tip {
  text-align: center;
  color: $text-placeholder;
  padding: 80rpx 0;
  font-size: 26rpx;
}

.card {
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 28rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);

  .card-title {
    font-size: 28rpx;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: 16rpx;
  }
}

.row-card {
  .row-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16rpx;

    .spu {
      font-size: 30rpx;
      font-weight: 600;
      color: $text-primary;
    }

    .layer {
      font-size: 22rpx;
      padding: 4rpx 16rpx;
      border-radius: 999rpx;
    }

    .layer-a {
      background: rgba(255, 107, 53, 0.14);
      color: $brand-primary;
    }

    .layer-b {
      background: rgba(0, 0, 0, 0.06);
      color: $text-secondary;
    }
  }

  .progress-row {
    margin-bottom: 16rpx;

    .progress-label {
      font-size: 24rpx;
      color: $text-secondary;
      margin-bottom: 8rpx;
    }

    .progress-bar {
      height: 12rpx;
      background: #f0f1f3;
      border-radius: 999rpx;
      overflow: hidden;
    }

    .progress-fill {
      height: 100%;
      background: linear-gradient(90deg, #ff9a4a, #ff6b35);
      border-radius: 999rpx;
      transition: width 0.3s ease;
    }
  }

  .meta-row {
    display: flex;
    justify-content: space-between;
    font-size: 24rpx;
    padding: 6rpx 0;

    .meta-label {
      color: $text-secondary;
    }

    .meta-value {
      color: $text-primary;
    }
  }
}

.hint-card {
  .hint-item {
    font-size: 24rpx;
    color: $text-secondary;
    line-height: 1.7;
  }
}
</style>
