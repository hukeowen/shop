<template>
  <view class="page">
    <view class="profile safe-top">
      <view class="avatar">{{ avatarText }}</view>
      <view class="info">
        <view class="nickname">{{ userStore.user?.nickname || '未登录' }}</view>
        <view class="mobile">{{ userStore.user?.mobile || '' }}</view>
      </view>
    </view>

    <view class="shop card">
      <view class="shop-head">
        <text class="title">我的店铺</text>
        <text class="edit" @click="goShopEdit">编辑</text>
      </view>
      <view class="shop-body">
        <view class="shop-name">{{ userStore.shop?.name || '-' }}</view>
        <view class="shop-addr">{{ userStore.shop?.address || '-' }}</view>
      </view>
    </view>

    <view class="menu card">
      <view class="menu-item" @click="goProducts">
        <text>商品管理</text>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @click="goQrcode">
        <text>店铺二维码</text>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @click="goBrokerage">
        <text>返佣与积分设置</text>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @click="goWithdrawApply">
        <text>商户提现</text>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @click="goUserWithdraw">
        <text>用户提现审核</text>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @click="goMemberWithdraw">
        <text>用户余额提现审核</text>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @click="goPayApply">
        <text>在线支付开通</text>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @click="goQuota">
        <text>AI 视频配额</text>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @click="goMembers">
        <text>会员消费排行</text>
        <text class="arrow">›</text>
      </view>
    </view>

    <view class="menu card">
      <view class="menu-item" @click="toast">
        <text>帮助与反馈</text>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @click="toast">
        <text>关于摊小二</text>
        <text class="arrow">›</text>
      </view>
    </view>

    <button class="logout" @click="onLogout">退出登录</button>
  </view>
</template>

<script setup>
import { computed } from 'vue';
import { useUserStore } from '../../store/user.js';

const userStore = useUserStore();

const avatarText = computed(() => {
  const n = userStore.user?.nickname || '摊';
  return n.slice(0, 1);
});

function toast() {
  uni.showToast({ title: '原型阶段，功能待开发', icon: 'none' });
}

function goShopEdit() {
  uni.navigateTo({ url: '/pages/me/shop-edit' });
}

function goQrcode() {
  uni.navigateTo({ url: '/pages/me/qrcode' });
}

function goQuota() {
  uni.navigateTo({ url: '/pages/ai-video/quota' });
}

function goProducts() {
  uni.navigateTo({ url: '/pages/product/list' });
}

function goBrokerage() {
  uni.navigateTo({ url: '/pages/me/brokerage' });
}

function goMembers() {
  uni.navigateTo({ url: '/pages/member/list' });
}

function goWithdrawApply() {
  uni.navigateTo({ url: '/pages/withdraw/merchant-apply' });
}

function goUserWithdraw() {
  uni.navigateTo({ url: '/pages/withdraw/user-list' });
}

function goMemberWithdraw() {
  uni.navigateTo({ url: '/pages/withdraw/member-list' });
}

function goPayApply() {
  uni.navigateTo({ url: '/pages/me/pay-apply' });
}

async function onLogout() {
  const r = await uni.showModal({ title: '提示', content: '确认退出登录？' });
  if (r.confirm) {
    await userStore.logout();
    uni.reLaunch({ url: '/pages/login/index' });
  }
}
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  padding: 0 24rpx 48rpx;
}

.safe-top {
  padding-top: calc(env(safe-area-inset-top) + 48rpx);
}

.profile {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 24rpx 12rpx 48rpx;

  .avatar {
    width: 120rpx;
    height: 120rpx;
    line-height: 120rpx;
    text-align: center;
    border-radius: 50%;
    background: $brand-primary;
    color: #fff;
    font-size: 56rpx;
    font-weight: 600;
  }

  .nickname {
    font-size: 36rpx;
    font-weight: 700;
    color: $text-primary;
  }

  .mobile {
    font-size: 26rpx;
    color: $text-secondary;
    margin-top: 4rpx;
  }
}

.card {
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 32rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.shop {
  .shop-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20rpx;

    .title {
      font-size: 28rpx;
      color: $text-secondary;
    }

    .edit {
      font-size: 26rpx;
      color: $brand-primary;
    }
  }

  .shop-body {
    .shop-name {
      font-size: 32rpx;
      font-weight: 600;
      color: $text-primary;
    }

    .shop-addr {
      margin-top: 12rpx;
      font-size: 26rpx;
      color: $text-regular;
    }
  }
}

.menu {
  padding: 0 32rpx;

  .menu-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    height: 100rpx;
    font-size: 30rpx;
    color: $text-primary;
    border-bottom: 1rpx solid $border-color;

    &:last-child {
      border-bottom: none;
    }

    .arrow {
      color: $text-placeholder;
      font-size: 40rpx;
    }
  }
}

.logout {
  margin: 48rpx 0;
  height: 96rpx;
  line-height: 96rpx;
  background: #fff;
  color: $danger;
  font-size: 30rpx;
  border-radius: $radius-md;
  border: 1rpx solid $border-color;

  &::after {
    border: none;
  }
}
</style>
