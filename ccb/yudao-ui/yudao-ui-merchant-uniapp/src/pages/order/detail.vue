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
      <!-- 抵扣明细：商品原价 + 各路抵扣 + 实付 -->
      <view class="deduct-block">
        <view class="dr">
          <text>商品总价</text>
          <text class="dr-v">¥{{ fen2yuan(order.originalPrice ?? order.totalPrice) }}</text>
        </view>
        <view v-if="order.balanceDeductFen > 0" class="dr">
          <text>店铺余额抵扣</text>
          <text class="dr-v dr-neg">- ¥{{ fen2yuan(order.balanceDeductFen) }}</text>
        </view>
        <view v-if="order.consumePointDeductFen > 0" class="dr">
          <text>消费积分抵扣 (用 {{ order.consumePointUsed }} 积分)</text>
          <text class="dr-v dr-neg">- ¥{{ fen2yuan(order.consumePointDeductFen) }}</text>
        </view>
        <view v-if="order.promoPointRedeemFen > 0" class="dr">
          <text>推广积分抵扣 (用 {{ order.promoPointRedeemFen }} 积分)</text>
          <text class="dr-v dr-neg">- ¥{{ fen2yuan(order.promoPointRedeemFen) }}</text>
        </view>
        <view v-if="order.promoAutoDeductFen > 0" class="dr">
          <text>推广奖励抵扣 (推 N 反 1 自动抵 {{ order.promoAutoDeductCount }} 件)</text>
          <text class="dr-v dr-neg">- ¥{{ fen2yuan(order.promoAutoDeductFen) }}</text>
        </view>
        <view v-if="order.couponDeductFen > 0" class="dr">
          <text>优惠券抵扣</text>
          <text class="dr-v dr-neg">- ¥{{ fen2yuan(order.couponDeductFen) }}</text>
        </view>
        <view class="total">
          <text>实付金额</text>
          <text class="amount">¥{{ fen2yuan(order.payPrice ?? order.totalPrice) }}</text>
        </view>
      </view>
    </view>

    <view v-if="order.remark" class="card">
      <view class="section-title">买家备注</view>
      <view class="remark">{{ order.remark }}</view>
    </view>

    <view v-if="order.status !== 30 && order.status !== 40" class="actions safe-bottom">
      <!-- 未支付：取消订单 + 确认收款 -->
      <button
        v-if="order.status === 0"
        class="btn danger"
        :disabled="acting"
        @click="onCancel"
      >
        取消订单
      </button>
      <button
        v-if="(order.status === 0 || order.status === 10) && !order.payStatus"
        class="btn primary"
        :disabled="acting"
        @click="onOfflineConfirm"
      >
        {{ acting ? '处理中…' : '确认收款（线下完成）' }}
      </button>
      <button
        v-if="order.status === 10 && order.deliveryType === 'express' && order.payStatus"
        class="btn primary"
        @click="goDeliver"
      >
        填写快递单号发货
      </button>
      <!-- 自提订单：扫码核销 -->
      <button
        v-if="order.status === 20 && order.deliveryType !== 'express'"
        class="btn primary"
        @click="onVerify"
      >
        确认核销（码：{{ order.verifyCode }}）
      </button>
      <!-- 快递订单：商户主动确认送达（设计 8.4） -->
      <button
        v-if="order.status === 20 && order.deliveryType === 'express'"
        class="btn primary"
        :disabled="acting"
        @click="onConfirmDelivered"
      >
        {{ acting ? '提交中…' : '确认已送达' }}
      </button>
    </view>
  </view>

  <view v-else class="loading">加载中...</view>
</template>

<script setup>
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { getOrder, pickUpVerify, confirmDelivered, offlineConfirm, offlineCancel } from '../../api/order.js';
import { fen2yuan, ORDER_STATUS } from '../../utils/format.js';

const order = ref(null);
const orderId = ref('');
const acting = ref(false);

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

function onConfirmDelivered() {
  uni.showModal({
    title: '确认已送达',
    content: '确认商品已送达客户手中？此操作不可撤销。',
    confirmText: '确认送达',
    success: async (modal) => {
      if (!modal.confirm) return;
      acting.value = true;
      try {
        await confirmDelivered(orderId.value);
        uni.showToast({ title: '已确认送达', icon: 'success' });
        await load();
      } catch (e) {
        uni.showToast({ title: '操作失败：' + (e?.message || e), icon: 'none' });
      } finally {
        acting.value = false;
      }
    },
  });
}

async function onOfflineConfirm() {
  const amt = order.value ? (order.value.totalPrice / 100).toFixed(2) : '';
  const m = await uni.showModal({
    title: '确认收款',
    content: `确认已收到顾客线下支付 ¥${amt}？\n确认后订单将标记为已完成，会触发积分/奖池/分销结算，不可撤销。`,
  });
  if (!m.confirm) return;
  acting.value = true;
  try {
    await offlineConfirm(orderId.value);
    uni.showToast({ title: '已确认收款', icon: 'success' });
    await load();
  } catch (e) {
    uni.showToast({ title: e?.message || '操作失败', icon: 'none' });
  } finally {
    acting.value = false;
  }
}

async function onCancel() {
  const m = await uni.showModal({
    title: '取消订单',
    content: `确认取消订单 #${order.value?.no || orderId.value}？\n库存会回滚、优惠券返还。取消后无法恢复。`,
  });
  if (!m.confirm) return;
  acting.value = true;
  try {
    await offlineCancel(orderId.value);
    uni.showToast({ title: '订单已取消', icon: 'success' });
    await load();
  } catch (e) {
    uni.showToast({ title: e?.message || '取消失败', icon: 'none' });
  } finally {
    acting.value = false;
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

.deduct-block {
  margin-top: 12rpx;
  padding-top: 12rpx;
  border-top: 1rpx dashed #e5e7eb;
  .dr {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8rpx 0;
    font-size: 26rpx;
    color: $text-regular;
    .dr-v { color: $text-regular; font-weight: 500; }
    .dr-neg { color: #ef4444; }
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
  display: flex;
  gap: 16rpx;
}

.btn {
  flex: 1;
  height: 96rpx;
  line-height: 96rpx;
  font-size: 30rpx;
  border-radius: $radius-md;

  &.primary {
    background: $brand-primary;
    color: #fff;
  }

  &.danger {
    background: transparent;
    color: #e63946;
    border: 2rpx solid #e63946;
  }

  &[disabled] {
    opacity: 0.5;
  }

  &::after {
    border: none;
  }
}
</style>
