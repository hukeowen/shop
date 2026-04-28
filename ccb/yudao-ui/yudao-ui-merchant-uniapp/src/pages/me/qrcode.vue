<template>
  <view class="page">
    <view v-if="loading" class="empty-tip">加载中...</view>
    <template v-else>
      <view class="card qr-card">
        <view class="shop-name">{{ shopName }}</view>
        <view class="qr-wrap">
          <image v-if="qrUrl" :src="qrUrl" class="qr-img" mode="aspectFit" />
          <view v-else class="qr-placeholder">
            <text class="qr-placeholder-text">二维码生成失败</text>
            <text class="qr-hint">请稍后重试</text>
          </view>
        </view>
        <view v-if="qrUrl" class="save-tip">长按图片可保存到相册</view>
        <view v-if="shareUrl" class="share-url">
          <text class="share-url-text">{{ shareUrl }}</text>
          <button class="copy-btn" size="mini" @click="onCopyUrl">复制链接</button>
        </view>
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
import QRCode from 'qrcode';
import { request } from '../../api/request.js';
import { useUserStore } from '../../store/user.js';

const userStore = useUserStore();
const loading = ref(true);
const qrUrl = ref('');
const shopName = ref('');
const shareUrl = ref('');

const FALLBACK_ORIGIN = 'https://www.doupaidoudian.com';

// 关键：tenantId 必须从 ShopInfoDO 取（商户的真实 tenant），不是 userStore.tenantId（普通 member 是 0）；
// 否则客户扫码进 shop-home 后 loadProducts 因 tenantId null 不加载，看不到商品。
function buildShareUrl(tenantId) {
  const inviter = userStore.userId || '';
  let origin = FALLBACK_ORIGIN;
  try {
    if (typeof location !== 'undefined' && location.origin) {
      origin = location.origin;
    }
  } catch {
    // 小程序无 location，用兜底
  }
  const params = [];
  if (tenantId) params.push('tenantId=' + tenantId);
  if (inviter) params.push('inviter=' + inviter);
  return `${origin}/m/shop-home${params.length ? '?' + params.join('&') : ''}`;
}

async function generateLocalQr(text) {
  try {
    return await QRCode.toDataURL(text, { width: 480, margin: 1, errorCorrectionLevel: 'M' });
  } catch {
    return '';
  }
}

function onCopyUrl() {
  if (!shareUrl.value) return;
  uni.setClipboardData({
    data: shareUrl.value,
    success: () => uni.showToast({ title: '已复制', icon: 'success' }),
    fail: () => uni.showToast({ title: '复制失败', icon: 'none' }),
  });
}

onLoad(async () => {
  try {
    const [shopRes, qrRes] = await Promise.all([
      request({ url: '/app-api/merchant/mini/shop/info' }),
      request({ url: '/app-api/merchant/mini/shop/qrcode' }).catch(() => null),
    ]);
    shopName.value = shopRes?.shopName || '';
    if (qrRes?.qrCodeUrl) {
      qrUrl.value = qrRes.qrCodeUrl;
    } else {
      // 后端未配置二维码 → 本地生成 /m/shop-home 链接兜底
      // tenantId 取 shopInfo.tenantId（商户的真实租户），用 userStore.userId 作 inviter
      const tenantId = shopRes?.tenantId;
      shareUrl.value = buildShareUrl(tenantId);
      qrUrl.value = await generateLocalQr(shareUrl.value);
    }
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

.share-url {
  margin-top: 24rpx;
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12rpx;

  .share-url-text {
    flex: 1;
    font-size: 22rpx;
    color: $text-secondary;
    word-break: break-all;
    background: #f6f7f9;
    padding: 12rpx 16rpx;
    border-radius: $radius-md;
  }

  .copy-btn {
    flex: 0 0 auto;
    background: $brand-primary;
    color: #fff;
    font-size: 22rpx;
    border-radius: $radius-md;

    &::after {
      border: none;
    }
  }
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
