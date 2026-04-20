<template>
  <view class="page">
    <view class="header safe-top">
      <text class="title">订单管理</text>
      <view class="verify-btn" @click="showVerify = true">核销</view>
    </view>

    <view class="tabs">
      <view
        v-for="t in tabs"
        :key="t.value"
        class="tab"
        :class="{ active: currentStatus === t.value }"
        @click="switchTab(t.value)"
      >
        {{ t.label }}
        <view v-if="t.badge" class="badge">{{ t.badge }}</view>
      </view>
    </view>

    <view class="list">
      <view
        v-for="o in list"
        :key="o.id"
        class="order card"
        @click="goDetail(o.id)"
      >
        <view class="order-head">
          <view class="order-no">{{ o.id }}</view>
          <view class="order-status" :style="{ color: statusColor(o.status) }">
            {{ statusText(o.status) }}
          </view>
        </view>
        <view class="order-items">
          <view v-for="(it, i) in o.items" :key="i" class="item">
            <text class="item-name">{{ it.spuName }} · {{ it.skuName }}</text>
            <text class="item-count">x{{ it.count }}</text>
          </view>
        </view>
        <view class="order-foot">
          <view class="order-meta">
            <text>{{ o.userNickname }}</text>
            <text class="dot">·</text>
            <text>{{ o.deliveryType === 'pickup' ? '自提' : '配送' }}</text>
            <text class="dot">·</text>
            <text>{{ o.createdAt }}</text>
          </view>
          <view class="order-total">¥{{ fen2yuan(o.totalPrice) }}</view>
        </view>
        <view class="order-actions" v-if="o.status === 10 || o.status === 20">
          <button
            v-if="o.status === 10 && o.deliveryType === 'express'"
            class="btn primary"
            @click.stop="goDeliver(o.id)"
          >
            去发货
          </button>
          <button
            v-if="o.status === 20"
            class="btn primary"
            @click.stop="quickVerify(o.verifyCode)"
          >
            核销（{{ o.verifyCode }}）
          </button>
        </view>
      </view>
      <view v-if="!list.length" class="empty">当前没有订单</view>
    </view>

    <view class="bottom-space" />

    <view v-if="showVerify" class="verify-mask" @click="showVerify = false">
      <view class="verify-sheet" @click.stop>
        <view class="sheet-title">核销订单</view>
        <view class="sheet-sub">扫描自提码 / 手动输入 4 位核销码</view>
        <view class="sheet-actions">
          <button class="btn ghost" @click="onScan">扫码核销</button>
        </view>
        <view class="sheet-divider"><text>或</text></view>
        <view class="sheet-input">
          <input
            class="code-input"
            type="number"
            maxlength="4"
            placeholder="核销码"
            v-model="verifyCode"
          />
          <button class="btn primary" :disabled="!verifyCode" @click="onManualVerify">
            核销
          </button>
        </view>
        <view class="sheet-close" @click="showVerify = false">关闭</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getOrderPage, pickUpVerify } from '../../api/order.js';
import { fen2yuan, ORDER_STATUS } from '../../utils/format.js';

const tabs = computed(() => [
  { label: '全部', value: 0 },
  { label: '待发货', value: 10, badge: badgeOf(10) },
  { label: '待核销', value: 20, badge: badgeOf(20) },
  { label: '已完成', value: 30 },
]);

const currentStatus = ref(0);
const list = ref([]);
const allList = ref([]);
const showVerify = ref(false);
const verifyCode = ref('');

function statusText(s) {
  return ORDER_STATUS[s]?.text || s;
}
function statusColor(s) {
  return ORDER_STATUS[s]?.color || '#999';
}
function badgeOf(s) {
  const n = allList.value.filter((o) => o.status === s).length;
  return n || null;
}

async function load() {
  const all = await getOrderPage({ status: 0 });
  allList.value = all.list;
  applyFilter();
}

function applyFilter() {
  list.value = currentStatus.value
    ? allList.value.filter((o) => o.status === currentStatus.value)
    : [...allList.value];
}

function switchTab(v) {
  currentStatus.value = v;
  applyFilter();
}

function goDetail(id) {
  uni.navigateTo({ url: `/pages/order/detail?id=${id}` });
}
function goDeliver(id) {
  uni.navigateTo({ url: `/pages/order/deliver?id=${id}` });
}

async function quickVerify(code) {
  verifyCode.value = code;
  await onManualVerify();
}

async function onManualVerify() {
  const r = await pickUpVerify(verifyCode.value);
  if (r.ok) {
    uni.showToast({ title: '核销成功', icon: 'success' });
    showVerify.value = false;
    verifyCode.value = '';
    load();
  } else {
    uni.showToast({ title: r.msg || '核销失败', icon: 'none' });
  }
}

function onScan() {
  // #ifdef H5
  uni.showToast({ title: 'H5 原型请用手动输入', icon: 'none' });
  // #endif
  // #ifndef H5
  uni.scanCode({
    success: (r) => {
      verifyCode.value = r.result;
      onManualVerify();
    },
  });
  // #endif
}

onShow(() => {
  load();
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  padding: 0 24rpx 48rpx;
  min-height: 100vh;
}

.safe-top {
  padding-top: calc(env(safe-area-inset-top) + 24rpx);
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx 12rpx;

  .title {
    font-size: 40rpx;
    font-weight: 700;
    color: $text-primary;
  }

  .verify-btn {
    height: 64rpx;
    line-height: 64rpx;
    padding: 0 32rpx;
    background: $brand-primary;
    color: #fff;
    border-radius: $radius-pill;
    font-size: 26rpx;
  }
}

.tabs {
  display: flex;
  gap: 24rpx;
  padding: 0 12rpx 24rpx;
  overflow-x: auto;

  .tab {
    position: relative;
    padding: 12rpx 0;
    font-size: 28rpx;
    color: $text-secondary;

    &.active {
      color: $text-primary;
      font-weight: 700;

      &::after {
        content: '';
        position: absolute;
        left: 20%;
        right: 20%;
        bottom: 0;
        height: 6rpx;
        border-radius: 3rpx;
        background: $brand-primary;
      }
    }

    .badge {
      position: absolute;
      top: 0;
      right: -20rpx;
      min-width: 28rpx;
      height: 28rpx;
      line-height: 28rpx;
      text-align: center;
      background: $danger;
      color: #fff;
      font-size: 20rpx;
      border-radius: 14rpx;
      padding: 0 6rpx;
    }
  }
}

.list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.card {
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 28rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.order-head {
  display: flex;
  justify-content: space-between;
  align-items: center;

  .order-no {
    font-size: 24rpx;
    color: $text-secondary;
  }

  .order-status {
    font-size: 26rpx;
    font-weight: 600;
  }
}

.order-items {
  margin: 20rpx 0;
  padding: 20rpx;
  background: #fafbfc;
  border-radius: $radius-md;

  .item {
    display: flex;
    justify-content: space-between;
    font-size: 26rpx;
    color: $text-regular;
    padding: 4rpx 0;
  }
}

.order-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;

  .order-meta {
    font-size: 22rpx;
    color: $text-secondary;

    .dot {
      margin: 0 8rpx;
    }
  }

  .order-total {
    font-size: 32rpx;
    font-weight: 700;
    color: $brand-primary;
  }
}

.order-actions {
  display: flex;
  justify-content: flex-end;
  gap: 16rpx;
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid $border-color;
}

.btn {
  height: 64rpx;
  line-height: 64rpx;
  padding: 0 32rpx;
  border-radius: $radius-pill;
  font-size: 26rpx;

  &.primary {
    background: $brand-primary;
    color: #fff;
  }

  &.ghost {
    background: transparent;
    color: $brand-primary;
    border: 2rpx solid $brand-primary;
  }

  &[disabled] {
    background: $text-placeholder;
    color: #fff;
  }

  &::after {
    border: none;
  }
}

.empty {
  padding: 120rpx 0;
  text-align: center;
  color: $text-placeholder;
  font-size: 26rpx;
}

.bottom-space {
  height: 80rpx;
}

.verify-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 999;
  display: flex;
  align-items: flex-end;
}

.verify-sheet {
  width: 100%;
  background: #fff;
  border-radius: $radius-lg $radius-lg 0 0;
  padding: 40rpx 32rpx calc(env(safe-area-inset-bottom) + 40rpx);

  .sheet-title {
    font-size: 36rpx;
    font-weight: 700;
    color: $text-primary;
    text-align: center;
  }

  .sheet-sub {
    margin-top: 12rpx;
    text-align: center;
    font-size: 24rpx;
    color: $text-secondary;
  }

  .sheet-actions {
    margin-top: 32rpx;
    display: flex;
    justify-content: center;

    .btn {
      width: 100%;
      height: 88rpx;
      line-height: 88rpx;
    }
  }

  .sheet-divider {
    margin: 32rpx 0 24rpx;
    text-align: center;
    color: $text-placeholder;
    font-size: 24rpx;
    position: relative;

    &::before,
    &::after {
      content: '';
      position: absolute;
      top: 50%;
      width: calc(50% - 40rpx);
      height: 1rpx;
      background: $border-color;
    }
    &::before {
      left: 0;
    }
    &::after {
      right: 0;
    }
  }

  .sheet-input {
    display: flex;
    gap: 16rpx;
    align-items: center;

    .code-input {
      flex: 1;
      height: 88rpx;
      padding: 0 24rpx;
      background: #f6f7f9;
      border-radius: $radius-md;
      font-size: 32rpx;
      letter-spacing: 8rpx;
      text-align: center;
    }

    .btn {
      flex-shrink: 0;
      height: 88rpx;
      line-height: 88rpx;
    }
  }

  .sheet-close {
    margin-top: 32rpx;
    text-align: center;
    color: $text-secondary;
    font-size: 26rpx;
  }
}
</style>
