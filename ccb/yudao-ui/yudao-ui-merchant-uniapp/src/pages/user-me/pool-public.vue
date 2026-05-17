<template>
  <view class="page">
    <view class="banner">
      <text class="title">🎁 奖池公示</text>
      <text class="sub">SPU {{ spuId }} 的奖池累计 + 历次中奖名单（已脱敏）</text>
    </view>

    <view class="card pool">
      <view class="pool-grid">
        <view class="pool-item">
          <text class="label">当前池余额</text>
          <text class="value">¥{{ (poolBalance / 100).toFixed(2) }}</text>
        </view>
        <view class="pool-item">
          <text class="label">累计入池</text>
          <text class="value">¥{{ (totalIn / 100).toFixed(2) }}</text>
        </view>
        <view class="pool-item">
          <text class="label">累计已发</text>
          <text class="value">¥{{ (totalOut / 100).toFixed(2) }}</text>
        </view>
      </view>
    </view>

    <view class="section-title">最近一次结算</view>
    <view v-if="latestPayouts.length === 0" class="empty">暂无结算记录</view>
    <view v-else class="card payouts">
      <view v-for="p in latestPayouts" :key="p.id" class="payout-row" :class="{ self: p.isSelf }">
        <image v-if="p.avatar" :src="p.avatar" class="avatar" mode="aspectFill" />
        <view v-else class="avatar default-avatar">{{ (p.maskedNickname || '?').charAt(0) }}</view>
        <view class="main">
          <view class="row1">
            <text class="nick">{{ p.maskedNickname }}</text>
            <text v-if="p.isSelf" class="self-tag">我</text>
            <text class="star">{{ p.star }}星</text>
          </view>
          <text class="time">{{ fmt(p.createTime) }} · {{ p.mode === 'LOTTERY' ? '抽中' : '均分' }}</text>
        </view>
        <text class="amt">+¥{{ (p.amount / 100).toFixed(2) }}</text>
      </view>
    </view>

    <view class="section-title">历次结算</view>
    <view v-if="records.length === 0" class="empty">暂无</view>
    <view v-for="r in records" :key="r.id" class="record-card" @click="openSettle(r.id)">
      <view class="row1">
        <text class="amt">¥{{ (r.totalDistributed / 100).toFixed(2) }}</text>
        <text class="time">{{ fmt(r.createTime) }}</text>
      </view>
      <view class="row2">
        <text>结算前 ¥{{ (r.poolBalanceBefore / 100).toFixed(2) }} · 中奖 {{ r.winnerCount }} 人</text>
        <text class="link">查看 ›</text>
      </view>
    </view>

    <view v-if="hasMore" class="more" @click="loadMore">加载更多</view>
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import {
  getPublicPoolBalance,
  getPublicLatestPayouts,
  listPublicSettleRecords,
} from '../../api/promo.js';

const spuId = ref(0);
const poolBalance = ref(0);
const totalIn = ref(0);
const totalOut = ref(0);
const latestPayouts = ref([]);
const records = ref([]);
const pageNo = ref(1);
const pageSize = 10;
const hasMore = ref(false);

function fmt(t) {
  if (!t) return '-';
  const d = new Date(t);
  if (isNaN(d.getTime())) return t;
  const pad = (n) => (n < 10 ? '0' + n : n);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

async function loadAll(reset = false) {
  if (reset) pageNo.value = 1;
  try {
    const [bal, lp, page] = await Promise.all([
      getPublicPoolBalance(spuId.value),
      getPublicLatestPayouts(spuId.value, 20),
      listPublicSettleRecords(spuId.value, { pageNo: pageNo.value, pageSize }),
    ]);
    if (bal) {
      poolBalance.value = bal.poolBalance || 0;
      totalIn.value = bal.totalIn || 0;
      totalOut.value = bal.totalOut || 0;
    }
    latestPayouts.value = Array.isArray(lp) ? lp : [];
    const list = page?.list || [];
    if (reset) records.value = list;
    else records.value = records.value.concat(list);
    hasMore.value = (page?.total || 0) > records.value.length;
  } catch (e) {
    uni.showToast({ title: e?.msg || '加载失败', icon: 'none' });
  }
}

function loadMore() {
  pageNo.value += 1;
  loadAll();
}

function openSettle(settleId) {
  uni.navigateTo({ url: `/pages/user-me/pool-public?spuId=${spuId.value}&settleId=${settleId}` });
}

onLoad((q) => {
  spuId.value = q.spuId ? Number(q.spuId) : 0;
  if (spuId.value > 0) loadAll(true);
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  padding: 24rpx 24rpx 80rpx;
  min-height: 100vh;
  background: #f6f7f9;
}
.banner {
  padding: 16rpx 8rpx 24rpx;
  .title {
    display: block;
    font-size: 36rpx;
    font-weight: 700;
    color: $text-primary;
  }
  .sub {
    display: block;
    margin-top: 6rpx;
    font-size: 24rpx;
    color: $text-secondary;
  }
}
.card {
  background: #fff;
  border-radius: $radius-md;
  padding: 24rpx;
  margin-bottom: 16rpx;
}
.card.pool {
  background: linear-gradient(135deg, rgba(255, 107, 53, 0.08), rgba(255, 154, 74, 0.06));
  .pool-grid {
    display: grid;
    grid-template-columns: 1fr 1fr 1fr;
    gap: 12rpx;
    .pool-item {
      text-align: center;
      .label {
        display: block;
        font-size: 22rpx;
        color: $text-secondary;
      }
      .value {
        display: block;
        margin-top: 8rpx;
        font-size: 32rpx;
        font-weight: 700;
        color: $brand-primary;
      }
    }
  }
}
.section-title {
  margin: 24rpx 4rpx 12rpx;
  font-size: 26rpx;
  color: $text-secondary;
  font-weight: 600;
}
.empty {
  text-align: center;
  padding: 40rpx 0;
  color: $text-placeholder;
  font-size: 24rpx;
  background: #fff;
  border-radius: $radius-md;
  margin-bottom: 16rpx;
}
.payout-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 0;
  border-bottom: 1rpx dashed $border-color;

  &:last-child {
    border-bottom: none;
  }
  &.self {
    background: rgba(255, 107, 53, 0.04);
    margin: 0 -24rpx;
    padding: 16rpx 24rpx;
  }
  .avatar {
    width: 72rpx;
    height: 72rpx;
    border-radius: 50%;
    flex: 0 0 72rpx;
  }
  .default-avatar {
    background: #f6f7f9;
    color: $text-secondary;
    line-height: 72rpx;
    text-align: center;
    font-size: 28rpx;
    font-weight: 600;
  }
  .main {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 4rpx;
    .row1 {
      display: flex;
      align-items: center;
      gap: 8rpx;
      .nick {
        font-size: 26rpx;
        color: $text-primary;
        font-weight: 500;
      }
      .self-tag {
        font-size: 20rpx;
        background: $brand-primary;
        color: #fff;
        padding: 2rpx 8rpx;
        border-radius: 999rpx;
      }
      .star {
        font-size: 20rpx;
        background: rgba(255, 107, 53, 0.12);
        color: $brand-primary;
        padding: 2rpx 10rpx;
        border-radius: 999rpx;
      }
    }
    .time {
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
.record-card {
  background: #fff;
  border-radius: $radius-md;
  padding: 20rpx 24rpx;
  margin-bottom: 12rpx;
  .row1 {
    display: flex;
    justify-content: space-between;
    .amt {
      font-size: 32rpx;
      font-weight: 700;
      color: $brand-primary;
    }
    .time {
      font-size: 22rpx;
      color: $text-secondary;
    }
  }
  .row2 {
    margin-top: 6rpx;
    display: flex;
    justify-content: space-between;
    font-size: 22rpx;
    color: $text-secondary;
    .link {
      color: $brand-primary;
    }
  }
}
.more {
  text-align: center;
  padding: 24rpx 0;
  color: $brand-primary;
  font-size: 26rpx;
}
</style>
