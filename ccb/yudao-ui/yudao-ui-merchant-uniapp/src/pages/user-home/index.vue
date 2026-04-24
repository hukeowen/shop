<template>
  <view class="page">
    <!-- Custom nav bar -->
    <view class="nav-bar safe-top">
      <text class="nav-title">摊小二</text>
    </view>

    <!-- Greeting -->
    <view class="greeting-wrap">
      <text class="greeting">你好，{{ userStore.user?.nickname || '用户' }}</text>
    </view>

    <!-- Shop list -->
    <view class="section-title">附近店铺</view>
    <view v-if="loading" class="empty-tip">加载中...</view>
    <view v-else-if="!shops.length" class="empty-tip">暂无店铺</view>
    <view v-else class="shop-list">
      <view
        v-for="shop in shops"
        :key="shop.tenantId"
        class="shop-card card"
        @click="goShop(shop)"
      >
        <view class="shop-name">{{ shop.name }}</view>
        <view class="shop-addr">{{ shop.address || '暂无地址' }}</view>
        <view class="shop-arrow">›</view>
      </view>
    </view>

    <!-- Bottom tab bar -->
    <view class="bottom-bar safe-bottom">
      <view class="tab-item active">
        <text class="tab-icon">🏠</text>
        <text class="tab-label">发现</text>
      </view>
      <view class="tab-item" @click="goMe">
        <text class="tab-icon">👤</text>
        <text class="tab-label">我的</text>
      </view>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '../../store/user.js';
import { request } from '../../api/request.js';

export default {
  data() {
    return {
      userStore: null,
      shops: [],
      loading: false,
    };
  },
  onLoad() {
    this.userStore = useUserStore();
    this.loadShops();
  },
  onPullDownRefresh() {
    this.loadShops().finally(() => {
      uni.stopPullDownRefresh();
    });
  },
  methods: {
    async loadShops() {
      this.loading = true;
      try {
        const res = await request({
          url: '/app-api/merchant/shop/public/list?pageNo=1&pageSize=10',
        });
        this.shops = (res && res.list) ? res.list : (Array.isArray(res) ? res : []);
      } catch {
        this.shops = [];
      } finally {
        this.loading = false;
      }
    },
    goShop(shop) {
      uni.navigateTo({ url: `/pages/shop-home/index?tenantId=${shop.tenantId}` });
    },
    goMe() {
      uni.navigateTo({ url: '/pages/user-me/index' });
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

.nav-title {
  font-size: 36rpx;
  font-weight: 700;
  color: $brand-primary;
}

.greeting-wrap {
  padding: 32rpx 32rpx 16rpx;
}

.greeting {
  font-size: 40rpx;
  font-weight: 600;
  color: $text-primary;
}

.section-title {
  font-size: 28rpx;
  color: $text-secondary;
  padding: 16rpx 32rpx 8rpx;
}

.empty-tip {
  text-align: center;
  color: $text-placeholder;
  padding: 80rpx 0;
  font-size: 28rpx;
}

.shop-list {
  padding: 0 24rpx;
}

.shop-card {
  position: relative;
  padding: 32rpx;
  margin-bottom: 20rpx;
  border-radius: $radius-lg;
  background: $bg-card;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.06);
}

.shop-name {
  font-size: 32rpx;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 8rpx;
}

.shop-addr {
  font-size: 26rpx;
  color: $text-secondary;
}

.shop-arrow {
  position: absolute;
  right: 32rpx;
  top: 50%;
  transform: translateY(-50%);
  font-size: 40rpx;
  color: $text-placeholder;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 100rpx;
  background: #fff;
  border-top: 1rpx solid $border-color;
  display: flex;
  align-items: center;
  justify-content: space-around;
}

.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
}

.tab-icon {
  font-size: 40rpx;
}

.tab-label {
  font-size: 22rpx;
  color: $text-secondary;
}

.tab-item.active .tab-label {
  color: $brand-primary;
}
</style>
