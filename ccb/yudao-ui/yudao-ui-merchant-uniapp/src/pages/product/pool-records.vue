<template>
  <view class="page">
    <view class="header">
      <text class="title">SPU {{ spuId }} 历次结算</text>
      <text class="sub">点条目查看本次中奖明细；金额单位：分</text>
    </view>

    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="!records.length" class="empty">暂无结算记录</view>

    <view v-for="r in records" :key="r.id" class="record-card" @click="openDetail(r)">
      <view class="row1">
        <text class="amt">¥{{ (r.totalDistributed / 100).toFixed(2) }}</text>
        <text class="time">{{ fmt(r.createTime) }}</text>
      </view>
      <view class="row2">
        <text>结算前 ¥{{ (r.poolBalanceBefore / 100).toFixed(2) }}</text>
        <text>残值 ¥{{ (r.poolBalanceAfter / 100).toFixed(2) }}</text>
      </view>
      <view class="row3" v-if="r.remark">
        <text class="remark">备注：{{ r.remark }}</text>
      </view>
    </view>

    <view v-if="hasMore" class="more" @click="loadMore">加载更多</view>
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { listSpuPoolRecords } from '../../api/promo.js';

const spuId = ref(0);
const records = ref([]);
const loading = ref(true);
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

async function fetchPage(reset = false) {
  if (reset) pageNo.value = 1;
  try {
    const r = await listSpuPoolRecords(spuId.value, { pageNo: pageNo.value, pageSize });
    const list = r?.list || [];
    if (reset) {
      records.value = list;
    } else {
      records.value = records.value.concat(list);
    }
    hasMore.value = (r?.total || 0) > records.value.length;
  } finally {
    loading.value = false;
  }
}

function loadMore() {
  pageNo.value += 1;
  fetchPage();
}

function openDetail(r) {
  uni.navigateTo({ url: `/pages/product/pool-detail?settleId=${r.id}` });
}

onLoad((q) => {
  spuId.value = q.spuId ? Number(q.spuId) : 0;
  if (spuId.value > 0) fetchPage(true);
  else loading.value = false;
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
  margin-bottom: 24rpx;
  .title {
    display: block;
    font-size: 32rpx;
    font-weight: 700;
    color: $text-primary;
  }
  .sub {
    display: block;
    margin-top: 6rpx;
    font-size: 22rpx;
    color: $text-secondary;
  }
}
.empty {
  text-align: center;
  padding: 80rpx 0;
  color: $text-placeholder;
  font-size: 26rpx;
}
.record-card {
  background: #fff;
  border-radius: $radius-md;
  padding: 24rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
  .row1 {
    display: flex;
    justify-content: space-between;
    align-items: center;
    .amt {
      font-size: 36rpx;
      font-weight: 700;
      color: $brand-primary;
    }
    .time {
      font-size: 22rpx;
      color: $text-secondary;
    }
  }
  .row2 {
    margin-top: 8rpx;
    display: flex;
    gap: 24rpx;
    font-size: 24rpx;
    color: $text-secondary;
  }
  .row3 {
    margin-top: 8rpx;
    .remark {
      font-size: 22rpx;
      color: $text-secondary;
      font-style: italic;
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
