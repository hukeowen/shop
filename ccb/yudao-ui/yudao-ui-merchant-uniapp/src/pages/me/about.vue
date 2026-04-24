<template>
  <view class="page">
    <view class="hero">
      <view class="logo">摊</view>
      <view class="app-name">摊小二</view>
      <view class="app-version">v1.0.0</view>
      <view class="app-slogan">让每个小摊，都有大生意</view>
    </view>

    <view class="card section">
      <view class="about-item">
        <text class="about-label">产品版本</text>
        <text class="about-val">1.0.0</text>
      </view>
      <view class="about-item">
        <text class="about-label">开发商</text>
        <text class="about-val">摊小二科技</text>
      </view>
      <view class="about-item">
        <text class="about-label">官方网站</text>
        <text class="about-val link">www.tanzxiaer.com</text>
      </view>
    </view>

    <view class="card section">
      <view class="desc">
        摊小二是专为线下摊位、小店商户打造的数字化经营工具。
        提供商品管理、订单处理、会员积分、推广返佣、AI 视频营销等一站式功能，
        帮助每一位摊主轻松开店、高效运营。
      </view>
    </view>

    <view class="copyright">© 2025 摊小二科技 版权所有</view>
  </view>
</template>

<script setup>
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  min-height: 100vh;
  padding: 24rpx;
}

.hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60rpx 0 40rpx;
}

.logo {
  width: 160rpx;
  height: 160rpx;
  line-height: 160rpx;
  text-align: center;
  border-radius: 40rpx;
  background: $brand-primary;
  color: #fff;
  font-size: 80rpx;
  font-weight: 700;
  margin-bottom: 24rpx;
}

.app-name {
  font-size: 44rpx;
  font-weight: 700;
  color: $text-primary;
  margin-bottom: 8rpx;
}

.app-version {
  font-size: 24rpx;
  color: $text-placeholder;
  margin-bottom: 16rpx;
}

.app-slogan {
  font-size: 26rpx;
  color: $text-secondary;
}

.card {
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 16rpx 32rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.about-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 1rpx solid $border-color;

  &:last-child { border-bottom: none; }
}

.about-label {
  font-size: 28rpx;
  color: $text-secondary;
}

.about-val {
  font-size: 28rpx;
  color: $text-primary;

  &.link { color: $brand-primary; }
}

.desc {
  font-size: 26rpx;
  color: $text-secondary;
  line-height: 1.8;
  padding: 12rpx 0;
}

.copyright {
  text-align: center;
  font-size: 22rpx;
  color: $text-placeholder;
  padding: 24rpx 0;
}
</style>
