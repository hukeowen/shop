<template>
  <view class="page">
    <view v-if="loading" class="empty-tip">加载中...</view>
    <view v-else-if="!items.length" class="empty-tip">购物车空空如也</view>
    <view v-else class="cart-list">
      <view
        v-for="item in items"
        :key="item.id"
        class="cart-item card"
      >
        <image
          v-if="item.picUrl"
          :src="item.picUrl"
          class="item-pic"
          mode="aspectFill"
        />
        <view class="item-info">
          <view class="item-name">{{ item.spuName || item.name }}</view>
          <view class="item-sku" v-if="item.properties">{{ item.properties.map(p => p.valueName).join(' / ') }}</view>
          <view class="item-price-row">
            <text class="item-price">¥{{ fen2yuan(item.price) }}</text>
            <text class="item-count">× {{ item.count }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- Total + checkout -->
    <view v-if="items.length" class="bottom-bar safe-bottom">
      <view class="total-wrap">
        <text class="total-label">合计：</text>
        <text class="total-price">¥{{ totalPrice }}</text>
      </view>
      <view class="checkout-btn" @click="goCheckout">结算</view>
    </view>
  </view>
</template>

<script>
import { request } from '../../api/request.js';
import { fen2yuan } from '../../utils/format.js';

export default {
  data() {
    return {
      tenantId: null,
      items: [],
      loading: false,
    };
  },
  computed: {
    totalPrice() {
      const total = this.items.reduce((sum, item) => sum + (item.price || 0) * (item.count || 1), 0);
      return fen2yuan(total);
    },
  },
  onLoad(query) {
    this.tenantId = query.tenantId ? Number(query.tenantId) : null;
    this.loadCart();
  },
  methods: {
    fen2yuan,
    async loadCart() {
      this.loading = true;
      try {
        const res = await request({
          url: '/app-api/trade/cart/list',
          tenantId: this.tenantId,
        });
        this.items = Array.isArray(res) ? res : (res && res.list) || [];
      } catch {
        this.items = [];
      } finally {
        this.loading = false;
      }
    },
    goCheckout() {
      uni.navigateTo({
        url: `/pages/checkout/index?fromCart=1&tenantId=${this.tenantId}`,
      });
    },
  },
};
</script>

<style lang="scss">
@import '../../uni.scss';

.page {
  min-height: 100vh;
  background: #f6f7f9;
  padding-bottom: 140rpx;
}

.empty-tip {
  text-align: center;
  color: $text-placeholder;
  padding: 120rpx 0;
  font-size: 28rpx;
}

.cart-list {
  padding: 24rpx;
}

.cart-item {
  display: flex;
  gap: 20rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  border-radius: $radius-lg;
  background: $bg-card;
  align-items: flex-start;
}

.item-pic {
  width: 120rpx;
  height: 120rpx;
  border-radius: $radius-md;
  flex-shrink: 0;
  background: #f0f0f0;
}

.item-info {
  flex: 1;
}

.item-name {
  font-size: 30rpx;
  font-weight: 500;
  color: $text-primary;
  margin-bottom: 8rpx;
}

.item-sku {
  font-size: 24rpx;
  color: $text-placeholder;
  margin-bottom: 12rpx;
}

.item-price-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.item-price {
  font-size: 32rpx;
  font-weight: 700;
  color: $brand-primary;
}

.item-count {
  font-size: 26rpx;
  color: $text-secondary;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 16rpx 32rpx;
  background: #fff;
  border-top: 1rpx solid $border-color;
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.total-wrap {
  flex: 1;
  display: flex;
  align-items: baseline;
  gap: 4rpx;
}

.total-label {
  font-size: 26rpx;
  color: $text-secondary;
}

.total-price {
  font-size: 36rpx;
  font-weight: 700;
  color: $brand-primary;
}

.checkout-btn {
  background: $brand-primary;
  color: #fff;
  border-radius: $radius-pill;
  padding: 20rpx 60rpx;
  font-size: 30rpx;
  font-weight: 600;
}
</style>
