<template>
  <view class="page">
    <view v-if="loading" class="empty-tip">加载中...</view>
    <template v-else>
      <!-- 说明 -->
      <view class="intro card">
        <view class="intro-title">💰 线下收款码</view>
        <view class="intro-desc">没开通在线支付通道时用。上传微信 / 支付宝收款码后，顾客下单会看到这里的码、扫码付款并上传付款凭证，你在订单详情核对到账后点「确认收款」即可。两个可只传一个。</view>
      </view>

      <!-- 收款码上传 -->
      <view class="card">
        <view class="qr-row">
          <view class="qr-item">
            <view class="qr-label"><text class="wx">💚</text> 微信收款码</view>
            <view class="qr-box" @click="pickQr('wechat')">
              <image v-if="form.wechatPayQrUrl" :src="form.wechatPayQrUrl" mode="aspectFit" class="qr-img" />
              <view v-else class="qr-empty"><text class="qr-plus">+</text><text class="qr-tip">点击上传</text></view>
            </view>
            <text v-if="form.wechatPayQrUrl" class="qr-del" @click="form.wechatPayQrUrl = ''">删除</text>
          </view>
          <view class="qr-item">
            <view class="qr-label"><text class="ali">💙</text> 支付宝收款码</view>
            <view class="qr-box" @click="pickQr('alipay')">
              <image v-if="form.alipayPayQrUrl" :src="form.alipayPayQrUrl" mode="aspectFit" class="qr-img" />
              <view v-else class="qr-empty"><text class="qr-plus">+</text><text class="qr-tip">点击上传</text></view>
            </view>
            <text v-if="form.alipayPayQrUrl" class="qr-del" @click="form.alipayPayQrUrl = ''">删除</text>
          </view>
        </view>
        <text class="hint">在微信/支付宝「收付款 → 二维码收款 → 保存图片」，把收款码截图上传即可</text>
      </view>

      <view class="bottom-bar safe-bottom">
        <view class="save-btn" @click="save">保存</view>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { request } from '../../api/request.js';

const loading = ref(true);
const shopRaw = ref({}); // 保留店铺其它字段，保存时原样回传，避免被清空
const form = ref({ wechatPayQrUrl: '', alipayPayQrUrl: '' });

async function pickQr(type) {
  const tempPath = await new Promise((resolve) => {
    uni.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      success: (r) => resolve(r.tempFilePaths[0]),
      fail: () => resolve(null),
    });
  });
  if (!tempPath) return;
  uni.showLoading({ title: '上传中…' });
  try {
    const { blobUrlToBase64, uploadImage } = await import('../../api/oss.js');
    const base64 = await blobUrlToBase64(tempPath);
    const { url: publicUrl } = await uploadImage(base64, { ext: 'jpg' });
    if (type === 'wechat') form.value.wechatPayQrUrl = publicUrl;
    else form.value.alipayPayQrUrl = publicUrl;
    uni.hideLoading();
    uni.showToast({ title: '上传成功', icon: 'success' });
  } catch (e) {
    uni.hideLoading();
    uni.showToast({ title: '上传失败：' + (e?.message || e), icon: 'none' });
  }
}

onLoad(async () => {
  try {
    const res = await request({ url: '/app-api/merchant/mini/shop/info' });
    if (res) {
      shopRaw.value = res;
      form.value.wechatPayQrUrl = res.wechatPayQrUrl || '';
      form.value.alipayPayQrUrl = res.alipayPayQrUrl || '';
    }
  } catch {}
  loading.value = false;
});

async function save() {
  try {
    // 回传完整店铺对象 + 新收款码，后端只取可编辑字段，其它原样保留
    await request({
      url: '/app-api/merchant/mini/shop/info',
      method: 'PUT',
      data: {
        ...shopRaw.value,
        wechatPayQrUrl: form.value.wechatPayQrUrl,
        alipayPayQrUrl: form.value.alipayPayQrUrl,
      },
    });
    uni.showToast({ title: '保存成功', icon: 'success' });
    setTimeout(() => uni.navigateBack(), 800);
  } catch {}
}
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  min-height: 100vh;
  padding: 24rpx;
  padding-bottom: 160rpx;
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
  padding: 32rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.intro {
  background: linear-gradient(135deg, #fff5ef, #ffe5d5);
}
.intro-title {
  font-size: 32rpx;
  font-weight: 700;
  color: $text-primary;
}
.intro-desc {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: $text-regular;
  line-height: 1.6;
}

.qr-row {
  display: flex;
  gap: 28rpx;
}
.qr-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
}
.qr-label {
  font-size: 28rpx;
  color: $text-primary;
  font-weight: 600;
  .wx { color: #07c160; }
  .ali { color: #1677ff; }
}
.qr-box {
  width: 100%;
  aspect-ratio: 1 / 1;
  border-radius: 16rpx;
  background: $bg-page;
  border: 2rpx dashed $border-color;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}
.qr-img { width: 100%; height: 100%; }
.qr-empty {
  display: flex; flex-direction: column;
  align-items: center; gap: 10rpx;
  color: $text-placeholder;
}
.qr-plus { font-size: 64rpx; font-weight: 300; line-height: 1; }
.qr-tip { font-size: 24rpx; }
.qr-del { font-size: 24rpx; color: #e63946; }

.hint {
  display: block;
  margin-top: 24rpx;
  font-size: 22rpx;
  color: $text-secondary;
  line-height: 1.5;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 16rpx 32rpx;
  background: #fff;
  border-top: 1rpx solid $border-color;
}
.save-btn {
  background: $brand-primary;
  color: #fff;
  border-radius: $radius-pill;
  padding: 24rpx 0;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
}
</style>
