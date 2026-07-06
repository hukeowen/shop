<template>
  <view class="page">
    <view class="hero">
      <view class="logo">拓</view>
      <view class="app-name">拓小二</view>
      <view class="app-version">v1.0.2</view>
      <view class="app-slogan">让每个店铺，都有大生意</view>
    </view>

    <view class="card section">
      <view class="about-item">
        <text class="about-label">产品版本</text>
        <text class="about-val">1.0.2</text>
      </view>
      <view class="about-item">
        <text class="about-label">开发商</text>
        <text class="about-val">拓小二科技</text>
      </view>
      <view class="about-item">
        <text class="about-label">官方网站</text>
        <text class="about-val link">www.tanzxiaer.com</text>
      </view>
      <!-- #ifdef APP-PLUS -->
      <view class="about-item tappable" @click="onCheckUpdate">
        <text class="about-label">检查更新</text>
        <text class="about-val link">当前 v{{ appVersion }} ›</text>
      </view>
      <!-- #endif -->
    </view>

    <view class="card section">
      <view class="desc">
        拓小二是专为线下店铺打造的数字化经营工具。
        提供商品管理、订单处理、会员积分、推广奖励、AI 视频营销等一站式功能，
        帮助每一位店主轻松开店、高效运营。
      </view>
    </view>

    <view class="copyright">© 2026 拓小二科技 版权所有</view>
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { checkAppUpdate } from '../../utils/appUpdate.js';

const appVersion = ref('1.0.2');

onLoad(() => {
  // #ifdef APP-PLUS
  try {
    plus.runtime.getProperty(plus.runtime.appid, (info) => {
      if (info && info.version) appVersion.value = info.version;
    });
  } catch (e) { /* ignore */ }
  // #endif
});

function onCheckUpdate() {
  checkAppUpdate({ manual: true });
}
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

  &.tappable:active { opacity: 0.6; }
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
