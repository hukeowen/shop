<template>
  <s-layout title="店铺管理">
    <view class="shop-container" v-if="merchantInfo">
      <!-- 店铺信息卡片 -->
      <view class="shop-card">
        <image :src="merchantInfo.logo || '/static/img/shop-default.png'" class="shop-logo" mode="aspectFill" />
        <view class="shop-info">
          <text class="shop-name">{{ merchantInfo.name }}</text>
          <text class="shop-status" :class="'status-' + merchantInfo.status">
            {{ statusText[merchantInfo.status] }}
          </text>
        </view>
      </view>

      <!-- 小程序码 -->
      <view class="qrcode-card" v-if="merchantInfo.miniAppQrCodeUrl">
        <text class="card-title">我的店铺码</text>
        <image :src="merchantInfo.miniAppQrCodeUrl" class="qrcode-img" mode="aspectFit" @tap="previewQrCode" />
        <text class="card-tip">长按保存，分享给客户扫码进店</text>
      </view>

      <!-- 功能菜单 -->
      <view class="menu-grid">
        <view class="menu-item" @tap="goPage('/pages/video/create')">
          <text class="menu-icon">🎬</text>
          <text class="menu-text">AI生成视频</text>
        </view>
        <view class="menu-item" @tap="goPage('/pages/video/list')">
          <text class="menu-icon">📹</text>
          <text class="menu-text">我的视频</text>
        </view>
        <view class="menu-item" @tap="bindDouyin">
          <text class="menu-icon">🎵</text>
          <text class="menu-text">{{ merchantInfo.douyinBound ? '抖音已绑定' : '绑定抖音' }}</text>
        </view>
        <view class="menu-item" @tap="previewQrCode">
          <text class="menu-icon">📱</text>
          <text class="menu-text">店铺码</text>
        </view>
      </view>

      <!-- 微信支付状态 -->
      <view class="pay-card">
        <text class="card-title">收款状态</text>
        <view class="pay-status">
          <text v-if="merchantInfo.wxSubMchId" style="color:#07c160;">已开通 ({{ merchantInfo.wxSubMchId }})</text>
          <text v-else style="color:#ff9900;">未开通微信支付</text>
        </view>
      </view>
    </view>
  </s-layout>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import sheep from '@/sheep';
import MerchantApi from '@/sheep/api/merchant';
import VideoApi from '@/sheep/api/video';

const merchantInfo = ref(null);
const statusText = { 0: '待审核', 1: '营业中', 2: '未通过', 3: '已禁用' };

onMounted(async () => {
  const { data } = await MerchantApi.getMy();
  merchantInfo.value = data;
});

const goPage = (url) => {
  uni.navigateTo({ url });
};

const previewQrCode = () => {
  if (merchantInfo.value?.miniAppQrCodeUrl) {
    uni.previewImage({ urls: [merchantInfo.value.miniAppQrCodeUrl] });
  }
};

const bindDouyin = async () => {
  if (merchantInfo.value?.douyinBound) {
    uni.showToast({ title: '已绑定抖音账号', icon: 'none' });
    return;
  }
  // 跳转到 H5 页面进行抖音授权
  const redirectUri = encodeURIComponent(sheep.$url.base_url + '/video/douyin/oauth/callback');
  const { data: oauthUrl } = await VideoApi.getDouyinOAuthUrl(merchantInfo.value.id, redirectUri);
  // 通过 web-view 打开 H5 授权页
  uni.navigateTo({
    url: '/pages/merchant/douyin-auth?url=' + encodeURIComponent(oauthUrl),
  });
};
</script>

<style lang="scss" scoped>
.shop-container { padding: 20rpx 30rpx; }
.shop-card {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 16rpx;
  padding: 30rpx;
  .shop-logo { width: 100rpx; height: 100rpx; border-radius: 12rpx; margin-right: 24rpx; }
  .shop-info { flex: 1; }
  .shop-name { font-size: 32rpx; font-weight: bold; display: block; }
  .shop-status { font-size: 24rpx; margin-top: 8rpx; display: block; }
  .status-1 { color: #07c160; }
  .status-3 { color: #ff4d4f; }
}
.qrcode-card, .pay-card {
  background: #fff;
  border-radius: 16rpx;
  padding: 30rpx;
  margin-top: 20rpx;
  text-align: center;
  .card-title { font-size: 30rpx; font-weight: bold; display: block; margin-bottom: 20rpx; }
  .card-tip { font-size: 24rpx; color: #999; margin-top: 16rpx; display: block; }
}
.qrcode-img { width: 300rpx; height: 300rpx; }
.menu-grid {
  display: flex;
  flex-wrap: wrap;
  background: #fff;
  border-radius: 16rpx;
  padding: 20rpx;
  margin-top: 20rpx;
  .menu-item {
    width: 25%;
    text-align: center;
    padding: 20rpx 0;
    .menu-icon { font-size: 48rpx; display: block; }
    .menu-text { font-size: 24rpx; color: #666; margin-top: 8rpx; display: block; }
  }
}
.pay-status { font-size: 28rpx; }
</style>
