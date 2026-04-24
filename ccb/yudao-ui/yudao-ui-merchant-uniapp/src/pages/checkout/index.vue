<template>
  <view class="page">
    <view v-if="loading" class="empty-tip">加载中...</view>

    <template v-else>
      <!-- 配送方式 -->
      <view class="card section">
        <view class="section-title">配送方式</view>
        <view class="pay-options">
          <view :class="['pay-option', deliveryType === 2 ? 'active' : '']" @click="deliveryType = 2">到店自提</view>
          <view :class="['pay-option', deliveryType === 1 ? 'active' : '']" @click="deliveryType = 1">快递发货</view>
        </view>
      </view>

      <!-- 商品清单 -->
      <view class="card section">
        <view class="section-title">订单商品</view>
        <view v-for="(item, i) in items" :key="i" class="order-item">
          <text class="item-name">{{ item.spuName || item.name || '商品' }}</text>
          <text class="item-count">x{{ item.count || 1 }}</text>
          <text class="item-price">¥{{ fen2yuan((item.price || 0) * (item.count || 1)) }}</text>
        </view>
        <view class="divider" />
        <view class="total-row">
          <text class="total-label">合计</text>
          <text class="total-price">¥{{ fen2yuan(totalFen) }}</text>
        </view>
      </view>

      <!-- 支付方式 -->
      <view class="card section">
        <view class="section-title">支付方式</view>
        <view class="pay-options">
          <view :class="['pay-option', payType === 1 ? 'active' : '']" @click="payType = 1">在线支付</view>
          <view :class="['pay-option', payType === 2 ? 'active' : '']" @click="payType = 2">到店付款</view>
        </view>
      </view>

      <view class="bottom-bar safe-bottom">
        <view class="submit-btn" @click="submitOrder">提交订单</view>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { request } from '../../api/request.js';
import { fen2yuan } from '../../utils/format.js';

const skuId = ref(null);
const count = ref(1);
const tenantId = ref(null);
const fromCart = ref(false);
const items = ref([]);
const loading = ref(false);
const deliveryType = ref(2); // 默认自提，无需地址
const payType = ref(1);

const totalFen = computed(() =>
  items.value.reduce((s, i) => s + (i.price || 0) * (i.count || 1), 0)
);

onLoad((query) => {
  skuId.value = query.skuId ? Number(query.skuId) : null;
  count.value = query.count ? Number(query.count) : 1;
  tenantId.value = query.tenantId ? Number(query.tenantId) : null;
  fromCart.value = query.fromCart === '1';
  loadItems();
});

async function loadItems() {
  loading.value = true;
  try {
    if (fromCart.value) {
      const res = await request({ url: '/app-api/trade/cart/list', tenantId: tenantId.value });
      items.value = Array.isArray(res) ? res : (res?.list || []);
    } else if (skuId.value) {
      // 直接购买：不调 settlement 接口（需要 address），直接展示基本信息
      const spu = await request({
        url: `/app-api/product/spu/get-detail?id=${skuId.value}`,
        tenantId: tenantId.value,
      });
      const sku = spu?.skus?.find(s => s.id === skuId.value) || spu?.skus?.[0] || {};
      items.value = [{
        spuName: spu?.name || '商品',
        price: sku.price || 0,
        count: count.value,
      }];
    }
  } catch {
    items.value = [];
  } finally {
    loading.value = false;
  }
}

async function submitOrder() {
  if (!items.value.length) {
    uni.showToast({ title: '订单为空', icon: 'none' });
    return;
  }
  try {
    const orderData = {
      deliveryType: deliveryType.value,
      payType: payType.value,
      pointStatus: false,
      couponId: null,
    };
    if (fromCart.value) {
      orderData.items = items.value.map(i => ({ cartId: i.id || i.cartId })).filter(i => i.cartId);
    } else {
      orderData.items = [{ skuId: skuId.value, count: count.value }];
    }
    await request({
      url: '/app-api/trade/order/create',
      method: 'POST',
      data: orderData,
      tenantId: tenantId.value,
    });
    uni.showToast({ title: '下单成功', icon: 'success' });
    setTimeout(() => uni.navigateBack({ delta: 3 }), 1200);
  } catch {}
}
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  min-height: 100vh;
  padding: 24rpx;
  padding-bottom: 140rpx;
}

.empty-tip {
  text-align: center;
  color: $text-placeholder;
  padding: 120rpx 0;
  font-size: 28rpx;
}

.card {
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 28rpx 32rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.section-title {
  font-size: 28rpx;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 20rpx;
}

.order-item {
  display: flex;
  align-items: center;
  padding: 12rpx 0;
  gap: 12rpx;

  .item-name { flex: 1; font-size: 28rpx; color: $text-primary; }
  .item-count { font-size: 26rpx; color: $text-secondary; }
  .item-price { font-size: 28rpx; color: $text-primary; min-width: 100rpx; text-align: right; }
}

.divider { height: 1rpx; background: $border-color; margin: 16rpx 0; }

.total-row {
  display: flex;
  justify-content: space-between;
  align-items: center;

  .total-label { font-size: 28rpx; color: $text-secondary; }
  .total-price { font-size: 36rpx; font-weight: 700; color: $brand-primary; }
}

.pay-options {
  display: flex;
  gap: 20rpx;
}

.pay-option {
  flex: 1;
  text-align: center;
  padding: 20rpx 0;
  border-radius: $radius-md;
  border: 2rpx solid $border-color;
  font-size: 28rpx;
  color: $text-secondary;

  &.active {
    border-color: $brand-primary;
    color: $brand-primary;
    background: rgba(255, 107, 53, 0.06);
    font-weight: 600;
  }
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 16rpx 32rpx;
  background: #fff;
  border-top: 1rpx solid $border-color;
}

.submit-btn {
  background: $brand-primary;
  color: #fff;
  border-radius: $radius-pill;
  padding: 24rpx 0;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
}
</style>
