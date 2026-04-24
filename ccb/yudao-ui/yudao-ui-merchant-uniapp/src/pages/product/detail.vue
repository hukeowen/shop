<template>
  <view class="page">
    <view v-if="loading" class="empty-tip">加载中...</view>
    <view v-else-if="!product" class="empty-tip">商品不存在</view>
    <view v-else class="content">
      <!-- Product image -->
      <image
        v-if="product.picUrl"
        :src="product.picUrl"
        class="main-pic"
        mode="aspectFill"
      />
      <view class="info-card card">
        <view class="product-name">{{ product.name }}</view>
        <view class="product-price">¥{{ fen2yuan(selectedSku ? selectedSku.price : (product.price || 0)) }}</view>
        <view v-if="product.description" class="product-desc">{{ product.description }}</view>
      </view>

      <!-- SKU selector -->
      <view v-if="product.skus && product.skus.length > 1" class="sku-card card">
        <view class="sku-title">规格</view>
        <view class="sku-list">
          <view
            v-for="sku in product.skus"
            :key="sku.id"
            :class="['sku-item', selectedSkuId === sku.id ? 'sku-active' : '']"
            @click="selectSku(sku)"
          >
            <text>{{ sku.name || sku.properties?.map(p => p.valueName).join('/') || `规格${sku.id}` }}</text>
            <text class="sku-price">¥{{ fen2yuan(sku.price) }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- Bottom action bar -->
    <view v-if="product" class="bottom-bar safe-bottom">
      <view class="btn btn-cart" @click="addToCart">加入购物车</view>
      <view class="btn btn-buy" @click="buyNow">立即购买</view>
    </view>
  </view>
</template>

<script>
import { request } from '../../api/request.js';
import { fen2yuan } from '../../utils/format.js';

export default {
  data() {
    return {
      spuId: null,
      tenantId: null,
      product: null,
      selectedSkuId: null,
      loading: false,
    };
  },
  computed: {
    selectedSku() {
      if (!this.product || !this.product.skus) return null;
      return this.product.skus.find((s) => s.id === this.selectedSkuId) || null;
    },
  },
  onLoad(query) {
    this.spuId = query.spuId ? Number(query.spuId) : null;
    this.tenantId = query.tenantId ? Number(query.tenantId) : null;
    this.loadProduct();
  },
  methods: {
    fen2yuan,
    async loadProduct() {
      if (!this.spuId) return;
      this.loading = true;
      try {
        this.product = await request({
          url: `/app-api/product/spu/get-detail?id=${this.spuId}`,
          tenantId: this.tenantId,
        });
        if (this.product && this.product.skus && this.product.skus.length > 0) {
          this.selectedSkuId = this.product.skus[0].id;
        }
      } catch {
        this.product = null;
      } finally {
        this.loading = false;
      }
    },
    selectSku(sku) {
      this.selectedSkuId = sku.id;
    },
    async addToCart() {
      if (!this.selectedSkuId) {
        uni.showToast({ title: '请选择规格', icon: 'none' });
        return;
      }
      try {
        await request({
          url: '/app-api/trade/cart/add',
          method: 'POST',
          data: { skuId: this.selectedSkuId, count: 1 },
          tenantId: this.tenantId,
        });
        uni.showToast({ title: '已加入购物车', icon: 'success' });
      } catch {}
    },
    buyNow() {
      if (!this.selectedSkuId) {
        uni.showToast({ title: '请选择规格', icon: 'none' });
        return;
      }
      uni.navigateTo({
        url: `/pages/checkout/index?skuId=${this.selectedSkuId}&count=1&tenantId=${this.tenantId}`,
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

.main-pic {
  width: 100%;
  height: 600rpx;
  display: block;
  background: #f0f0f0;
}

.info-card {
  margin: 24rpx;
  padding: 28rpx 32rpx;
  border-radius: $radius-lg;
  background: $bg-card;
}

.product-name {
  font-size: 34rpx;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 12rpx;
}

.product-price {
  font-size: 40rpx;
  font-weight: 700;
  color: $brand-primary;
  margin-bottom: 16rpx;
}

.product-desc {
  font-size: 26rpx;
  color: $text-secondary;
  line-height: 1.6;
}

.sku-card {
  margin: 0 24rpx 24rpx;
  padding: 28rpx 32rpx;
  border-radius: $radius-lg;
  background: $bg-card;
}

.sku-title {
  font-size: 28rpx;
  color: $text-secondary;
  margin-bottom: 16rpx;
}

.sku-list {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.sku-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12rpx 24rpx;
  border-radius: $radius-md;
  border: 2rpx solid $border-color;
  font-size: 26rpx;
  color: $text-primary;
  gap: 4rpx;
}

.sku-item.sku-active {
  border-color: $brand-primary;
  color: $brand-primary;
  background: rgba(255, 107, 53, 0.06);
}

.sku-price {
  font-size: 22rpx;
  color: $text-secondary;
}

.sku-item.sku-active .sku-price {
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
  display: flex;
  gap: 20rpx;
}

.btn {
  flex: 1;
  text-align: center;
  padding: 24rpx 0;
  border-radius: $radius-pill;
  font-size: 30rpx;
  font-weight: 600;
}

.btn-cart {
  background: #fff3ee;
  color: $brand-primary;
  border: 2rpx solid $brand-primary;
}

.btn-buy {
  background: $brand-primary;
  color: #fff;
}
</style>
