<template>
  <view class="page" v-if="order">
    <view class="status-banner" :style="{ background: statusBg(order.status) }">
      <view class="status-text">{{ statusText(order.status) }}</view>
      <view class="status-sub">订单号 {{ order.id }}</view>
    </view>

    <view class="card">
      <view class="section-title">收货 / 自提信息</view>
      <view class="info-row">
        <text class="k">买家</text>
        <text class="v">{{ order.userNickname }} · {{ order.userMobile }}</text>
      </view>
      <view class="info-row">
        <text class="k">方式</text>
        <text class="v">{{ order.deliveryType === 'pickup' ? '到店自提' : '快递配送' }}</text>
      </view>
      <view v-if="order.deliveryType === 'express'" class="info-row">
        <text class="k">地址</text>
        <text class="v">{{ order.address }}</text>
      </view>
      <view v-if="order.deliveryType === 'pickup'" class="info-row">
        <text class="k">核销码</text>
        <text class="v code">{{ order.verifyCode }}</text>
      </view>
      <view v-if="order.expressNo" class="info-row">
        <text class="k">快递单号</text>
        <text class="v">{{ order.expressCompany }} · {{ order.expressNo }}</text>
      </view>
    </view>

    <view class="card">
      <view class="section-title">商品明细</view>
      <view class="item" v-for="(it, i) in order.items" :key="i">
        <view class="item-name">{{ it.spuName }}</view>
        <view class="item-sku">{{ it.skuName }}</view>
        <view class="item-right">
          <text>x{{ it.count }}</text>
          <text class="item-sub">¥{{ fen2yuan(it.price) }}</text>
        </view>
      </view>
      <view class="total">
        <text>订单金额</text>
        <text class="amount">¥{{ fen2yuan(order.totalPrice) }}</text>
      </view>
    </view>

    <view v-if="order.remark" class="card">
      <view class="section-title">买家备注</view>
      <view class="remark">{{ order.remark }}</view>
    </view>

    <view v-if="order.status !== 30" class="actions safe-bottom">
      <button
        v-if="order.status === 10 && order.deliveryType === 'express'"
        class="btn primary"
        @click="goDeliver"
      >
        填写快递单号发货
      </button>
      <button
        v-if="order.status === 20"
        class="btn primary"
        @click="onVerify"
      >
        确认核销（码：{{ order.verifyCode }}）
      </button>
    </view>
  </view>

  <view v-else class="loading">加载中...</view>
</template>

<script setup>
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { getOrder, pickUpVerify } from '../../api/order.js';
import { fen2yuan, ORDER_STATUS } from '../../utils/format.js';

const order = ref(null);
const orderId = ref('');

function statusText(s) {
  return ORDER_STATUS[s]?.text || s;
}
function statusBg(s) {
  const c = ORDER_STATUS[s]?.color || '#999';
  return `linear-gradient(135deg, ${c}, ${c}cc)`;
}

async function load() {
  order.value = await getOrder(orderId.value);
}

function goDeliver() {
  uni.navigateTo({ url: `/pages/order/deliver?id=${orderId.value}` });
}

async function onVerify() {
  const r = await pickUpVerify(order.value.verifyCode);
  if (r.ok) {
    uni.showToast({ title: '核销成功', icon: 'success' });
    load();
  } else {
    uni.showToast({ title: r.msg, icon: 'none' });
  }
}

onLoad((q) => {
  orderId.value = q.id;
  load();
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  padding: 0 24rpx 200rpx;
  min-height: 100vh;
}

.loading {
  padding: 200rpx 0;
  text-align: center;
  color: $text-placeholder;
}

.status-banner {
  padding: 40rpx 32rpx;
  border-radius: $radius-lg;
  color: #fff;
  margin: 24rpx 0;

  .status-text {
    font-size: 40rpx;
    font-weight: 700;
  }

  .status-sub {
    margin-top: 8rpx;
    font-size: 24rpx;
    opacity: 0.9;
  }
}

.card {
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 28rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.section-title {
  font-size: 28rpx;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 20rpx;
}

.info-row {
  display: flex;
  padding: 12rpx 0;
  font-size: 26rpx;

  .k {
    width: 140rpx;
    color: $text-secondary;
    flex-shrink: 0;
  }

  .v {
    flex: 1;
    color: $text-primary;

    &.code {
      color: $brand-primary;
      font-weight: 700;
      letter-spacing: 4rpx;
    }
  }
}

.item {
  display: flex;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 1rpx dashed $border-color;

  &:last-of-type {
    border-bottom: none;
  }

  .item-name {
    flex: 1;
    font-size: 28rpx;
    color: $text-primary;
  }

  .item-sku {
    width: 160rpx;
    font-size: 24rpx;
    color: $text-secondary;
  }

  .item-right {
    text-align: right;
    font-size: 24rpx;
    color: $text-secondary;

    .item-sub {
      display: block;
      color: $text-primary;
      font-weight: 600;
      font-size: 26rpx;
    }
  }
}

.total {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx 0 4rpx;
  margin-top: 12rpx;
  border-top: 2rpx solid $border-color;
  font-size: 26rpx;
  color: $text-regular;

  .amount {
    font-size: 36rpx;
    font-weight: 700;
    color: $brand-primary;
  }
}

.remark {
  padding: 20rpx;
  background: #fff8ef;
  color: $text-regular;
  font-size: 26rpx;
  line-height: 1.6;
  border-radius: $radius-md;
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
  font-size: 30rpx;
  border-radius: $radius-md;

  &.primary {
    background: $brand-primary;
    color: #fff;
  }

  &::after {
    border: none;
  }
}
</style>
