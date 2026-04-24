<template>
  <view class="page">
    <!-- Custom nav bar -->
    <view class="nav-bar safe-top">
      <text class="nav-title">我的</text>
    </view>

    <!-- User card -->
    <view class="user-card card">
      <view class="avatar">{{ avatarLetter }}</view>
      <view class="user-info">
        <view class="nickname">{{ userStore.user?.nickname || '用户' }}</view>
        <view class="phone">{{ userStore.phone || '未绑定手机' }}</view>
      </view>
    </view>

    <!-- Menu -->
    <view class="menu-section card">
      <view class="menu-item" @click="goOrders">
        <text class="menu-icon">📋</text>
        <text class="menu-label">我的订单</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="divider" />
      <view class="menu-item" @click="goShopBalance">
        <text class="menu-icon">💰</text>
        <text class="menu-label">我的余额与积分</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="divider" />
      <view v-if="userStore.isMerchant" class="menu-item" @click="switchToMerchant">
        <text class="menu-icon">🏪</text>
        <text class="menu-label">切换到商户端</text>
        <text class="menu-arrow">›</text>
      </view>
    </view>

    <!-- Logout -->
    <view class="logout-btn" @click="logout">退出登录</view>
  </view>
</template>

<script>
import { useUserStore } from '../../store/user.js';

export default {
  data() {
    return {
      userStore: null,
    };
  },
  computed: {
    avatarLetter() {
      const nick = this.userStore?.user?.nickname || this.userStore?.phone || '用';
      return nick.charAt(0).toUpperCase();
    },
  },
  onLoad() {
    this.userStore = useUserStore();
  },
  methods: {
    goOrders() {
      const tenantId = this.userStore?.tenantId || '';
      uni.navigateTo({ url: `/pages/user-order/list?tenantId=${tenantId}` });
    },
    goShopBalance() {
      const tenantId = uni.getStorageSync('lastShopTenantId') || this.userStore?.tenantId || '';
      uni.navigateTo({ url: `/pages/user-me/shop-balance?tenantId=${tenantId}` });
    },
    async switchToMerchant() {
      try {
        await this.userStore.switchRole('merchant');
        uni.reLaunch({ url: '/pages/index/index' });
      } catch {}
    },
    logout() {
      uni.showModal({
        title: '确认退出',
        content: '确定要退出登录吗？',
        success: (res) => {
          if (res.confirm) {
            this.userStore.logout();
            uni.reLaunch({ url: '/pages/login/index' });
          }
        },
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
}

.nav-bar {
  background: #fff;
  padding: 20rpx 32rpx;
}

.nav-title {
  font-size: 36rpx;
  font-weight: 700;
  color: $text-primary;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 28rpx;
  margin: 24rpx;
  padding: 32rpx;
  border-radius: $radius-lg;
  background: $bg-card;
}

.avatar {
  width: 100rpx;
  height: 100rpx;
  border-radius: 50rpx;
  background: $brand-primary;
  color: #fff;
  font-size: 40rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.user-info {
  flex: 1;
}

.nickname {
  font-size: 32rpx;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 8rpx;
}

.phone {
  font-size: 26rpx;
  color: $text-secondary;
}

.menu-section {
  margin: 0 24rpx 24rpx;
  border-radius: $radius-lg;
  background: $bg-card;
  overflow: hidden;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 32rpx;
}

.menu-icon {
  font-size: 36rpx;
  width: 50rpx;
  text-align: center;
}

.menu-label {
  flex: 1;
  font-size: 30rpx;
  color: $text-primary;
}

.menu-arrow {
  font-size: 40rpx;
  color: $text-placeholder;
}

.divider {
  height: 1rpx;
  background: $border-color;
  margin-left: 100rpx;
}

.logout-btn {
  margin: 0 24rpx;
  text-align: center;
  padding: 28rpx 0;
  background: #fff;
  border-radius: $radius-lg;
  font-size: 30rpx;
  color: $danger;
  font-weight: 500;
}
</style>
