<template>
  <view class="page">
    <!-- Settlement preview -->
    <view v-if="settlement" class="section card">
      <view class="section-title">订单信息</view>
      <view
        v-for="(item, i) in settlementItems"
        :key="i"
        class="order-item"
      >
        <text class="item-name">{{ item.spuName || item.name || '商品' }}</text>
        <text class="item-subtotal">¥{{ fen2yuan(item.price * item.count) }}</text>
      </view>
      <view class="divider" />
      <view class="total-row">
        <text class="total-label">合计</text>
        <text class="total-price">¥{{ fen2yuan(totalFen) }}</text>
      </view>
    </view>

    <view v-else-if="loading" class="empty-tip">加载中...</view>

    <!-- Payment method -->
    <view v-if="settlement" class="section card">
      <view class="section-title">支付方式</view>
      <view class="pay-options">
        <view
          :class="['pay-option', payType === 1 ? 'pay-active' : '']"
          @click="payType = 1"
        >在线支付</view>
        <view
          :class="['pay-option', payType === 2 ? 'pay-active' : '']"
          @click="payType = 2"
        >到店付款</view>
      </view>
    </view>

    <!-- Submit -->
    <view v-if="settlement" class="bottom-bar safe-bottom">
      <view class="submit-btn" @click="submitOrder">提交订单</view>
    </view>
  </view>
</template>

<script>
import { request } from '../../api/request.js';
import { fen2yuan } from '../../utils/format.js';

export default {
  data() {
    return {
      skuId: null,
      count: 1,
      tenantId: null,
      fromCart: false,
      settlement: null,
      loading: false,
      payType: 1,
    };
  },
  computed: {
    settlementItems() {
      if (!this.settlement) return [];
      return this.settlement.items || this.settlement.orderItems || [];
    },
    totalFen() {
      if (!this.settlement) return 0;
      return this.settlement.totalPrice || this.settlement.price || 0;
    },
  },
  onLoad(query) {
    this.skuId = query.skuId ? Number(query.skuId) : null;
    this.count = query.count ? Number(query.count) : 1;
    this.tenantId = query.tenantId ? Number(query.tenantId) : null;
    this.fromCart = query.fromCart === '1';
    this.loadSettlement();
  },
  methods: {
    fen2yuan,
    async loadSettlement() {
      this.loading = true;
      try {
        if (this.fromCart) {
          // From cart: load cart items as settlement
          const cartRes = await request({
            url: '/app-api/trade/cart/list',
            tenantId: this.tenantId,
          });
          const items = Array.isArray(cartRes) ? cartRes : (cartRes && cartRes.list) || [];
          const total = items.reduce((s, i) => s + (i.price || 0) * (i.count || 1), 0);
          this.settlement = { items, totalPrice: total };
        } else if (this.skuId) {
          // Direct buy: use settlement API
          const res = await request({
            url: `/app-api/trade/order/settlement?skuIds=${this.skuId}&counts=${this.count}&addressId=0&couponId=0&payType=${this.payType}`,
            tenantId: this.tenantId,
          });
          this.settlement = res || { items: [], totalPrice: 0 };
        }
      } catch {
        // Fallback: show minimal settlement
        this.settlement = { items: [], totalPrice: 0 };
      } finally {
        this.loading = false;
      }
    },
    async submitOrder() {
      try {
        const orderData = {
          payType: this.payType,
          addressId: 0,
          couponId: 0,
        };
        if (this.fromCart) {
          orderData.fromCart = true;
        } else {
          orderData.items = [{ skuId: this.skuId, count: this.count }];
        }
        await request({
          url: '/app-api/trade/order/create',
          method: 'POST',
          data: orderData,
          tenantId: this.tenantId,
        });
        uni.showToast({ title: '下单成功', icon: 'success' });
        setTimeout(() => {
          // Navigate back to shop home
          uni.navigateBack({ delta: 3 });
        }, 1200);
      } catch {}
    },
  },
};
</script>

<style lang="scss">
@import '../../uni.scss';

.page {
  min-height: 100vh;
  background: #f6f7f9;
  padding: 24rpx;
  padding-bottom: 140rpx;
}

.empty-tip {
  text-align: center;
  color: $text-placeholder;
  padding: 120rpx 0;
  font-size: 28rpx;
}

.section {
  padding: 28rpx 32rpx;
  border-radius: $radius-lg;
  background: $bg-card;
  margin-bottom: 24rpx;
}

.section-title {
  font-size: 28rpx;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 20rpx;
}

.order-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12rpx 0;
}

.item-name {
  font-size: 28rpx;
  color: $text-primary;
  flex: 1;
}

.item-subtotal {
  font-size: 28rpx;
  color: $text-secondary;
}

.divider {
  height: 1rpx;
  background: $border-color;
  margin: 16rpx 0;
}

.total-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.total-label {
  font-size: 28rpx;
  color: $text-secondary;
}

.total-price {
  font-size: 36rpx;
  font-weight: 700;
  color: $brand-primary;
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
}

.pay-option.pay-active {
  border-color: $brand-primary;
  color: $brand-primary;
  background: rgba(255, 107, 53, 0.06);
  font-weight: 600;
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
