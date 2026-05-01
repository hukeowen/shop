<template>
  <view class="page">
    <view class="hero">
      <view class="hero-title">邀请好友到本店购物</view>
      <view class="hero-sub">好友通过你的链接进店并下单，你可获得直推奖 / 队列返奖</view>
    </view>

    <view v-if="!tenantId" class="warn-card">
      <view class="warn-title">⚠ 未识别店铺</view>
      <view class="warn-text">请先进入一家店铺再来生成邀请链接（推荐链 + 营销奖励都按店铺隔离，没有店铺无法生成有效邀请）</view>
      <button class="warn-btn" @click="goNearby">去逛附近店铺</button>
    </view>

    <view class="card qr-card">
      <view v-if="qrLoading" class="qr-placeholder">
        <text>二维码生成中…</text>
      </view>
      <image v-else-if="qrDataUrl" :src="qrDataUrl" class="qr-img" mode="aspectFit" />
      <view v-else class="qr-placeholder">
        <text>未登录或二维码生成失败</text>
      </view>
      <view v-if="qrDataUrl" class="qr-tip">长按图片可保存到相册</view>
    </view>

    <view class="card">
      <view class="card-title">邀请链接</view>
      <view class="link-row">
        <input class="input" :value="shareUrl" disabled />
        <button class="btn primary" :disabled="!shareUrl" @click="onCopy">复制</button>
      </view>
      <view class="hint">朋友打开链接进入本店，登录后即自动绑定为你的下级（终生制，仅首次有效）。</view>
    </view>

    <view class="card hint-card">
      <view class="card-title">使用说明</view>
      <view class="hint-item">• 复制链接发给朋友 / 微信群 / 朋友圈</view>
      <view class="hint-item">• 朋友点开链接 → 进入店铺首页 → 登录</view>
      <view class="hint-item">• 朋友登录瞬间，推荐链自动落库（无须再点确认）</view>
      <view class="hint-item">• 朋友下单后，引擎按商品配置返奖到你的推广积分</view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import QRCode from 'qrcode';
import { useUserStore } from '../../store/user.js';

const userStore = useUserStore();

const FALLBACK_ORIGIN = 'https://www.doupaidoudian.com';

// 取最近访问的店铺 tenantId（shop-home 进店时已 setStorageSync('lastShopTenantId')）
// 没有则页面提示用户先去逛店再来分享 — 推荐链是按店铺隔离的，没 tenantId 链接无效
const tenantId = ref(null);
try {
  const v = uni.getStorageSync('lastShopTenantId');
  if (v) tenantId.value = Number(v);
} catch {}

function goNearby() {
  uni.switchTab({ url: '/pages/nearby/index' });
}

const shareUrl = computed(() => {
  const uid = userStore.userId;
  if (!uid || !tenantId.value) return '';
  let origin = FALLBACK_ORIGIN;
  try {
    if (typeof location !== 'undefined' && location.origin) {
      origin = location.origin;
    }
  } catch {
    // 小程序环境无 location，用兜底
  }
  // 必须带 tenantId — 否则朋友打开 shop-home 拿不到店铺信息也无法 visit 落库
  return `${origin}/m/shop-home?tenantId=${tenantId.value}&inviter=${uid}`;
});

const qrDataUrl = ref('');
const qrLoading = ref(true);

async function generateQr() {
  qrLoading.value = true;
  try {
    if (!shareUrl.value) return;
    qrDataUrl.value = await QRCode.toDataURL(shareUrl.value, {
      width: 480,
      margin: 1,
      errorCorrectionLevel: 'M',
    });
  } catch {
    qrDataUrl.value = '';
  } finally {
    qrLoading.value = false;
  }
}

function onCopy() {
  if (!shareUrl.value) {
    uni.showToast({ title: '请先登录', icon: 'none' });
    return;
  }
  uni.setClipboardData({
    data: shareUrl.value,
    success: () => uni.showToast({ title: '已复制', icon: 'success' }),
    fail: () => uni.showToast({ title: '复制失败', icon: 'none' }),
  });
}

onMounted(() => {
  generateQr();
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  padding: 24rpx 24rpx 60rpx;
  min-height: 100vh;
}

.warn-card {
  background: #fff8ef;
  border: 1rpx solid #f5c89a;
  border-radius: $radius-lg;
  padding: 32rpx;
  margin-bottom: 24rpx;
  text-align: center;

  .warn-title {
    font-size: 30rpx;
    font-weight: 700;
    color: #b34a00;
  }
  .warn-text {
    margin-top: 12rpx;
    font-size: 24rpx;
    color: $text-secondary;
    line-height: 1.6;
  }
  .warn-btn {
    margin-top: 20rpx;
    padding: 12rpx 32rpx;
    background: $brand-primary;
    color: #fff;
    border-radius: 999rpx;
    font-size: 26rpx;
    display: inline-block;
    &::after { border: none; }
  }
}

.hero {
  background: linear-gradient(135deg, #ff9a4a, #ff6b35);
  border-radius: $radius-lg;
  padding: 40rpx 32rpx;
  color: #fff;
  margin-bottom: 24rpx;

  .hero-title {
    font-size: 36rpx;
    font-weight: 700;
  }

  .hero-sub {
    margin-top: 12rpx;
    font-size: 24rpx;
    opacity: 0.9;
    line-height: 1.5;
  }
}

.card {
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 28rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);

  .card-title {
    font-size: 28rpx;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: 16rpx;
  }
}

.qr-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 36rpx;

  .qr-img {
    width: 480rpx;
    height: 480rpx;
  }

  .qr-placeholder {
    width: 480rpx;
    height: 480rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    color: $text-placeholder;
    font-size: 26rpx;
    background: #f6f7f9;
    border-radius: $radius-md;
  }

  .qr-tip {
    margin-top: 16rpx;
    font-size: 22rpx;
    color: $text-secondary;
  }
}

.link-row {
  display: flex;
  gap: 16rpx;

  .input {
    flex: 1;
    height: 80rpx;
    padding: 0 24rpx;
    background: #f6f7f9;
    border-radius: $radius-md;
    font-size: 24rpx;
    color: $text-primary;
  }

  .btn.primary {
    flex: 0 0 140rpx;
    height: 80rpx;
    background: $brand-primary;
    color: #fff;
    border: none;
    font-size: 26rpx;
    border-radius: $radius-md;

    &::after {
      border: none;
    }
  }
}

.hint {
  margin-top: 12rpx;
  font-size: 22rpx;
  color: $text-secondary;
  line-height: 1.5;
}

.hint-card {
  .hint-item {
    font-size: 24rpx;
    color: $text-secondary;
    line-height: 1.7;
  }
}
</style>
