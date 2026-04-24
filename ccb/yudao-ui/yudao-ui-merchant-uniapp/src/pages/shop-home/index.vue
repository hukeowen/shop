<template>
  <view class="page">
    <!-- Custom nav bar -->
    <view class="nav-bar safe-top">
      <view class="nav-back" @click="goBack">‹</view>
      <text class="nav-title">{{ shopInfo ? shopInfo.name : '店铺' }}</text>
      <view class="nav-cart" @click="goCart">
        <text class="cart-icon">🛒</text>
        <view v-if="cartCount > 0" class="cart-badge">{{ cartCount }}</view>
      </view>
    </view>

    <!-- Shop info -->
    <view v-if="shopInfo" class="shop-info card">
      <view class="shop-name">{{ shopInfo.name }}</view>
      <view class="info-row" v-if="shopInfo.address">
        <text class="info-label">地址</text>
        <text class="info-val">{{ shopInfo.address }}</text>
      </view>
      <view class="info-row" v-if="shopInfo.businessHours">
        <text class="info-label">营业</text>
        <text class="info-val">{{ shopInfo.businessHours }}</text>
      </view>
      <view class="info-row" v-if="shopInfo.mobile">
        <text class="info-label">电话</text>
        <text class="info-val">{{ shopInfo.mobile }}</text>
      </view>
    </view>

    <!-- Product list -->
    <view class="section-title">商品</view>
    <view v-if="loading" class="empty-tip">加载中...</view>
    <view v-else-if="!products.length" class="empty-tip">暂无商品</view>
    <view v-else class="product-list">
      <view
        v-for="spu in products"
        :key="spu.id"
        class="product-card card"
        @click="goDetail(spu)"
      >
        <image
          v-if="spu.picUrl"
          :src="spu.picUrl"
          class="product-pic"
          mode="aspectFill"
        />
        <view class="product-info">
          <view class="product-name">{{ spu.name }}</view>
          <view class="product-price">¥{{ fen2yuan(spu.price || (spu.skus && spu.skus[0] && spu.skus[0].price) || 0) }}</view>
        </view>
      </view>
    </view>

    <!-- Bottom cart button -->
    <view class="bottom-bar safe-bottom">
      <view class="cart-btn" @click="goCart">
        <text>前往购物车</text>
        <view v-if="cartCount > 0" class="cart-count">{{ cartCount }}</view>
      </view>
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
      shopInfo: null,
      products: [],
      cartCount: 0,
      loading: false,
    };
  },
  onLoad(query) {
    this.tenantId = query.tenantId ? Number(query.tenantId) : null;
    this.loadAll();
  },
  methods: {
    fen2yuan,
    async loadAll() {
      this.loading = true;
      try {
        await Promise.all([this.loadShopInfo(), this.loadProducts(), this.loadCartCount()]);
      } finally {
        this.loading = false;
      }
    },
    async loadShopInfo() {
      if (!this.tenantId) return;
      try {
        this.shopInfo = await request({
          url: `/app-api/merchant/shop/public/info?tenantId=${this.tenantId}`,
        });
      } catch {}
    },
    async loadProducts() {
      if (!this.tenantId) return;
      try {
        const res = await request({
          url: '/app-api/product/spu/page?pageNo=1&pageSize=20',
          tenantId: this.tenantId,
        });
        this.products = (res && res.list) ? res.list : (Array.isArray(res) ? res : []);
      } catch {
        this.products = [];
      }
    },
    async loadCartCount() {
      if (!this.tenantId) return;
      try {
        const res = await request({
          url: '/app-api/trade/cart/get-count',
          tenantId: this.tenantId,
        });
        this.cartCount = typeof res === 'number' ? res : (res && res.count) || 0;
      } catch {
        this.cartCount = 0;
      }
    },
    goDetail(spu) {
      uni.navigateTo({
        url: `/pages/product/detail?spuId=${spu.id}&tenantId=${this.tenantId}`,
      });
    },
    goCart() {
      uni.navigateTo({ url: `/pages/cart/index?tenantId=${this.tenantId}` });
    },
    goBack() {
      uni.navigateBack();
    },
  },
};
</script>

<style lang="scss">
@import '../../uni.scss';

.page {
  min-height: 100vh;
  background: #f6f7f9;
  padding-bottom: 120rpx;
}

.nav-bar {
  background: #fff;
  padding: 20rpx 32rpx;
  display: flex;
  align-items: center;
}

.nav-back {
  font-size: 52rpx;
  color: $text-primary;
  margin-right: 16rpx;
  line-height: 1;
}

.nav-title {
  flex: 1;
  font-size: 32rpx;
  font-weight: 600;
  color: $text-primary;
}

.nav-cart {
  position: relative;
  padding: 8rpx;
}

.cart-icon {
  font-size: 40rpx;
}

.cart-badge {
  position: absolute;
  top: 0;
  right: 0;
  min-width: 32rpx;
  height: 32rpx;
  border-radius: 16rpx;
  background: $danger;
  color: #fff;
  font-size: 20rpx;
  text-align: center;
  line-height: 32rpx;
  padding: 0 6rpx;
}

.shop-info {
  margin: 24rpx;
  padding: 28rpx 32rpx;
  border-radius: $radius-lg;
  background: $bg-card;
}

.shop-name {
  font-size: 36rpx;
  font-weight: 700;
  color: $text-primary;
  margin-bottom: 16rpx;
}

.info-row {
  display: flex;
  gap: 16rpx;
  margin-bottom: 8rpx;
}

.info-label {
  font-size: 26rpx;
  color: $text-placeholder;
  width: 60rpx;
  flex-shrink: 0;
}

.info-val {
  font-size: 26rpx;
  color: $text-secondary;
}

.section-title {
  font-size: 28rpx;
  color: $text-secondary;
  padding: 8rpx 32rpx 12rpx;
}

.empty-tip {
  text-align: center;
  color: $text-placeholder;
  padding: 80rpx 0;
  font-size: 28rpx;
}

.product-list {
  padding: 0 24rpx;
}

.product-card {
  display: flex;
  gap: 20rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  border-radius: $radius-lg;
  background: $bg-card;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.06);
  align-items: center;
}

.product-pic {
  width: 140rpx;
  height: 140rpx;
  border-radius: $radius-md;
  flex-shrink: 0;
  background: #f0f0f0;
}

.product-info {
  flex: 1;
}

.product-name {
  font-size: 30rpx;
  font-weight: 500;
  color: $text-primary;
  margin-bottom: 12rpx;
}

.product-price {
  font-size: 32rpx;
  font-weight: 700;
  color: $brand-primary;
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

.cart-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  background: $brand-primary;
  color: #fff;
  border-radius: $radius-pill;
  padding: 24rpx 0;
  font-size: 30rpx;
  font-weight: 600;
}

.cart-count {
  min-width: 32rpx;
  height: 32rpx;
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.3);
  color: #fff;
  font-size: 22rpx;
  text-align: center;
  line-height: 32rpx;
  padding: 0 8rpx;
}
</style>
