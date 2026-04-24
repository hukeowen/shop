<template>
  <view class="page">
    <view v-if="loading" class="empty-tip">加载中...</view>
    <template v-else>
      <view class="card qr-card">
        <view class="shop-name">{{ shopName }}</view>
        <view class="qr-wrap">
          <image v-if="qrUrl" :src="qrUrl" class="qr-img" mode="aspectFit" />
          <view v-else class="qr-placeholder">
            <text class="qr-placeholder-text">二维码尚未生成</text>
            <text class="qr-hint">请联系平台管理员生成店铺专属二维码</text>
          </view>
        </view>
        <view v-if="qrUrl" class="save-tip">长按图片可保存到相册</view>
      </view>

      <view class="card hint-card">
        <view class="hint-title">使用说明</view>
        <view class="hint-item">• 将此二维码印刷/展示在店铺门口</view>
        <view class="hint-item">• 顾客扫码后直接进入您的店铺页</view>
        <view class="hint-item">• 通过二维码进店的顾客消费，您可以获得返佣</view>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { request } from '../../api/request.js';

const loading = ref(true);
const qrUrl = ref('');
const shopName = ref('');

onLoad(async () => {
  try {
    const [shopRes, qrRes] = await Promise.all([
      request({ url: '/app-api/merchant/mini/shop/info' }),
      request({ url: '/app-api/merchant/mini/shop/qrcode' }),
    ]);
    shopName.value = shopRes?.shopName || '';
    qrUrl.value = qrRes?.qrCodeUrl || '';
  } catch {}
  loading.value = false;
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  min-height: 100vh;
  padding: 24rpx;
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
  padding: 40rpx 32rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.qr-card {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.shop-name {
  font-size: 36rpx;
  font-weight: 700;
  color: $text-primary;
  margin-bottom: 40rpx;
}

.qr-wrap {
  width: 400rpx;
  height: 400rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.qr-img {
  width: 400rpx;
  height: 400rpx;
  border-radius: $radius-md;
}

.qr-placeholder {
  width: 400rpx;
  height: 400rpx;
  background: #f5f5f5;
  border-radius: $radius-md;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
}

.qr-placeholder-text {
  font-size: 30rpx;
  color: $text-secondary;
}

.qr-hint {
  font-size: 24rpx;
  color: $text-placeholder;
  text-align: center;
  padding: 0 40rpx;
  line-height: 1.6;
}

.save-tip {
  margin-top: 24rpx;
  font-size: 24rpx;
  color: $text-placeholder;
}

.hint-card {
  .hint-title {
    font-size: 28rpx;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: 20rpx;
  }

  .hint-item {
    font-size: 26rpx;
    color: $text-secondary;
    line-height: 1.8;
    padding: 4rpx 0;
  }
}
</style>
