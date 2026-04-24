<template>
  <view class="page">
    <view v-for="(m, i) in list" :key="m.userId" class="item card">
      <view class="rank">{{ (pageNo - 1) * pageSize + i + 1 }}</view>
      <view class="avatar">{{ (m.nickname || '匿').slice(0, 1) }}</view>
      <view class="body">
        <view class="name">{{ m.nickname || '未知用户' }}</view>
        <view class="mobile">{{ m.mobile || '' }}</view>
      </view>
      <view class="right">
        <view class="amount">¥{{ fen2yuan(m.totalSpent) }}</view>
        <view class="count">{{ m.orderCount }} 单</view>
      </view>
    </view>

    <view v-if="!list.length && !loading" class="empty">暂无消费记录</view>

    <view class="pagination" v-if="total > pageSize">
      <button class="page-btn" :disabled="pageNo <= 1" @click="prevPage">上一页</button>
      <text class="page-info">{{ pageNo }} / {{ totalPages }}</text>
      <button class="page-btn" :disabled="pageNo >= totalPages" @click="nextPage">下一页</button>
    </view>

    <view class="bottom-space" />
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { request } from '../../api/request.js';
import { fen2yuan } from '../../utils/format.js';

const BASE = '/app-api/merchant/mini/member';

const list = ref([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = 20;
const loading = ref(false);

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)));

async function load() {
  loading.value = true;
  try {
    const data = await request({
      url: `${BASE}/page-by-consumption?pageNo=${pageNo.value}&pageSize=${pageSize}`,
    });
    list.value = data.list || [];
    total.value = data.total || 0;
  } catch {
    // toast shown by request.js
  } finally {
    loading.value = false;
  }
}

function prevPage() {
  if (pageNo.value > 1) {
    pageNo.value--;
    load();
  }
}

function nextPage() {
  if (pageNo.value < totalPages.value) {
    pageNo.value++;
    load();
  }
}

onShow(() => {
  pageNo.value = 1;
  load();
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  padding: 24rpx 24rpx 48rpx;
  min-height: 100vh;
}

.card {
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 24rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.item {
  display: flex;
  align-items: center;
  gap: 16rpx;

  .rank {
    flex-shrink: 0;
    width: 48rpx;
    text-align: center;
    font-size: 28rpx;
    font-weight: 700;
    color: $text-secondary;

    &:nth-child(1) { color: #F59E0B; }
  }

  .avatar {
    flex-shrink: 0;
    width: 80rpx;
    height: 80rpx;
    line-height: 80rpx;
    text-align: center;
    background: $brand-primary-light;
    color: $brand-primary;
    font-size: 32rpx;
    font-weight: 700;
    border-radius: 50%;
  }

  .body {
    flex: 1;
    min-width: 0;

    .name {
      font-size: 28rpx;
      color: $text-primary;
      font-weight: 600;
    }

    .mobile {
      margin-top: 4rpx;
      font-size: 24rpx;
      color: $text-secondary;
    }
  }

  .right {
    text-align: right;

    .amount {
      font-size: 32rpx;
      font-weight: 700;
      color: $brand-primary;
    }

    .count {
      margin-top: 4rpx;
      font-size: 22rpx;
      color: $text-placeholder;
    }
  }
}

.empty {
  padding: 120rpx 0;
  text-align: center;
  color: $text-placeholder;
  font-size: 26rpx;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 24rpx;
  padding: 32rpx 0;

  .page-btn {
    height: 64rpx;
    line-height: 64rpx;
    padding: 0 32rpx;
    background: $brand-primary;
    color: #fff;
    border-radius: $radius-pill;
    font-size: 26rpx;

    &[disabled] {
      background: $text-placeholder;
      opacity: 0.6;
    }

    &::after { border: none; }
  }

  .page-info {
    font-size: 26rpx;
    color: $text-secondary;
  }
}

.bottom-space {
  height: 48rpx;
}
</style>
