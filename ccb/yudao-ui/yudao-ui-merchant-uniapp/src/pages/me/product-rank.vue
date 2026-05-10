<template>
  <view class="page">
    <view class="period-tabs">
      <view class="pt" :class="{ active: period === 'day' }" @click="setPeriod('day')">日</view>
      <view class="pt" :class="{ active: period === 'week' }" @click="setPeriod('week')">周</view>
      <view class="pt" :class="{ active: period === 'month' }" @click="setPeriod('month')">月</view>
      <view class="pt" :class="{ active: period === 'year' }" @click="setPeriod('year')">年</view>
      <view class="sort-tabs">
        <text :class="{ active: sort === 'count' }" @click="setSort('count')">按销量</text>
        <text>·</text>
        <text :class="{ active: sort === 'amount' }" @click="setSort('amount')">按金额</text>
      </view>
    </view>

    <view class="rank-table">
      <view class="rank-th">
        <text class="th-rank">名次</text>
        <text class="th-name">商品</text>
        <text class="th-count">销量</text>
        <text class="th-amount">销售额</text>
      </view>
      <view class="rank-tr" v-for="(p, i) in list" :key="p.spuId" @click="goSpuDetail(p)">
        <view class="td-rank">
          <view class="medal" :class="rankClass(i)">{{ p.rank || (i + 1) }}</view>
        </view>
        <view class="td-name">{{ p.name }}</view>
        <view class="td-count">{{ p.salesCount }} 件</view>
        <view class="td-amount">¥{{ fen2yuan(p.salesAmount) }}</view>
      </view>
      <view class="empty" v-if="!list.length">暂无销售数据</view>
    </view>

    <view class="bottom-space"></view>
  </view>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { fen2yuan } from '../../utils/format.js';
import { getProductRank } from '../../api/report.js';

const period = ref('month');
const sort = ref('count');
const list = ref([]);

function rankClass(i) { return ['gold', 'silver', 'bronze'][i] || 'normal'; }
async function setPeriod(p) { period.value = p; await load(); }
async function setSort(s) { sort.value = s; await load(); }
async function load() {
  try { list.value = await getProductRank(period.value, sort.value, 50) || []; }
  catch { list.value = []; }
}
function goSpuDetail(p) {
  if (!p.spuId) return;
  uni.navigateTo({ url: `/pages/product/edit?id=${p.spuId}` });
}

onMounted(load);
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  min-height: 100vh; background: $bg-page; padding-bottom: 32rpx;
}
.period-tabs {
  display: flex; align-items: center;
  background: #fff; padding: 16rpx 24rpx; gap: 12rpx;
  border-bottom: 1rpx solid $border-color;
  .pt {
    padding: 8rpx 28rpx; border-radius: 999rpx;
    background: #f0f0f3; font-size: 26rpx;
    &.active { background: $brand-primary; color: #fff; }
  }
  .sort-tabs {
    margin-left: auto; font-size: 24rpx; color: $text-secondary; display: flex; gap: 8rpx;
    text { cursor: pointer; }
    text.active { color: $brand-primary; font-weight: 600; }
  }
}
.rank-table {
  margin: 16rpx 24rpx;
  background: #fff;
  border-radius: 16rpx;
  padding: 0 16rpx;
  .rank-th, .rank-tr {
    display: flex; align-items: center;
    padding: 24rpx 8rpx; border-bottom: 1rpx solid $border-color;
  }
  .rank-tr:last-child { border-bottom: none; }
  .rank-th { font-size: 22rpx; color: $text-secondary; font-weight: 500; }
  .th-rank, .td-rank { width: 80rpx; }
  .th-name, .td-name { flex: 1; font-size: 26rpx; }
  .th-count, .td-count { width: 100rpx; text-align: center; font-size: 24rpx; color: $text-secondary; }
  .th-amount, .td-amount { width: 140rpx; text-align: right; font-size: 28rpx; font-weight: 700; color: $brand-primary; }
  .medal {
    width: 48rpx; height: 48rpx; border-radius: 50%;
    color: #fff; font-size: 24rpx; font-weight: 700;
    text-align: center; line-height: 48rpx;
    background: $text-placeholder;
    &.gold { background: #ffcc00; }
    &.silver { background: #c0c5cf; }
    &.bronze { background: #d6793a; }
  }
  .empty { text-align: center; padding: 80rpx 0; color: $text-placeholder; font-size: 24rpx; }
}
.bottom-space { height: 80rpx; }
</style>
