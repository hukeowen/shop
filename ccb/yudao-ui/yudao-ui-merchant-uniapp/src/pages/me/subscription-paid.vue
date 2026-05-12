<template>
  <view class="page">
    <view class="topbar safe-top">
      <text class="title">支付完成</text>
    </view>

    <view class="hero">
      <view class="check-circle">
        <text class="check">✓</text>
      </view>
      <view class="h1">套餐购买成功</view>
      <view class="sub">SaaS 服务已激活，{{ countdown }} 秒后自动返回工作台</view>
    </view>

    <view v-if="status" class="status-card">
      <view class="row">
        <text class="lbl">当前套餐</text>
        <text class="val pkg">{{ levelText }}</text>
      </view>
      <view class="row">
        <text class="lbl">到期时间</text>
        <text class="val">{{ formatDate(status.serviceExpireAt) }}</text>
      </view>
      <view class="row">
        <text class="lbl">AI 视频余量</text>
        <text class="val">{{ status.videoQuotaRemaining || 0 }} 条</text>
      </view>
    </view>

    <view v-else-if="loading" class="loading">加载状态中...</view>

    <view class="actions">
      <view class="btn primary" @click="goNow">立即进入工作台</view>
      <view class="btn ghost" @click="goSubscription">查看套餐详情</view>
    </view>

    <view class="footer-tip">感谢支持 摊小二 SaaS 服务</view>
  </view>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { request } from '../../api/request.js';

const countdown = ref(3);
const status = ref(null);
const loading = ref(true);
let timer = null;

const levelText = computed(() => {
  const lv = status.value?.servicePackageLevel || 'TRIAL';
  return { TRIAL: '试用版', BASIC: 'BASIC（298 年费）', PRO: 'PRO（1688 年费）', PLATFORM: '平台版' }[lv] || lv;
});

function formatDate(s) {
  if (!s) return '-';
  const d = new Date(typeof s === 'string' && !s.includes('T') ? s.replace(' ', 'T') : s);
  if (isNaN(d.getTime())) return s;
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

async function loadStatus() {
  try {
    const data = await request({ url: '/app-api/merchant/mini/saas/my-status' });
    status.value = data || null;
  } catch {} finally {
    loading.value = false;
  }
}

function startCountdown() {
  if (timer) return;
  timer = setInterval(() => {
    countdown.value--;
    if (countdown.value <= 0) {
      clearInterval(timer);
      timer = null;
      goNow();
    }
  }, 1000);
}

function goNow() {
  if (timer) { clearInterval(timer); timer = null; }
  uni.reLaunch({ url: '/pages/index/index' });
}

function goSubscription() {
  if (timer) { clearInterval(timer); timer = null; }
  uni.redirectTo({ url: '/pages/me/subscription' });
}

onShow(() => {
  countdown.value = 3;
  loadStatus();
  startCountdown();
});

onUnmounted(() => {
  if (timer) { clearInterval(timer); timer = null; }
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  min-height: 100vh;
  background: linear-gradient(180deg, #fff6ee 0%, $bg-page 40%);
  padding-bottom: 80rpx;
}
.safe-top { padding-top: calc(env(safe-area-inset-top) + 16rpx); }

.topbar {
  padding: 16rpx 32rpx;
  text-align: center;
  background: transparent;
}
.topbar .title { font-size: 32rpx; font-weight: 600; color: $text-primary; }

.hero {
  text-align: center;
  padding: 80rpx 32rpx 40rpx;
}
.check-circle {
  width: 160rpx; height: 160rpx;
  margin: 0 auto 32rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #4ade80, #22c55e);
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 12rpx 32rpx rgba(34, 197, 94, .35);
  animation: pop 0.5s cubic-bezier(0.34, 1.56, 0.64, 1) both;
}
.check-circle .check {
  color: #fff;
  font-size: 96rpx;
  font-weight: 800;
  line-height: 1;
}
@keyframes pop {
  0% { transform: scale(0); }
  100% { transform: scale(1); }
}
.hero .h1 {
  font-size: 40rpx;
  font-weight: 800;
  color: $text-primary;
  margin-bottom: 16rpx;
}
.hero .sub {
  font-size: 26rpx;
  color: $text-secondary;
}

.status-card {
  margin: 24rpx 32rpx;
  padding: 32rpx;
  background: $bg-card;
  border-radius: $radius-lg;
  box-shadow: 0 4rpx 16rpx rgba(15, 23, 42, .04);
}
.status-card .row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16rpx 0;
  border-bottom: 1rpx solid $border-color;
}
.status-card .row:last-child { border-bottom: 0; }
.status-card .lbl {
  font-size: 26rpx;
  color: $text-secondary;
}
.status-card .val {
  font-size: 28rpx;
  font-weight: 600;
  color: $text-primary;
}
.status-card .val.pkg {
  color: $brand-primary;
  font-weight: 700;
}

.loading {
  text-align: center;
  padding: 40rpx;
  color: $text-placeholder;
  font-size: 24rpx;
}

.actions {
  margin: 48rpx 32rpx 0;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.btn {
  padding: 26rpx;
  border-radius: 999rpx;
  text-align: center;
  font-size: 30rpx;
  font-weight: 700;
}
.btn.primary {
  background: $brand-primary;
  color: #fff;
  box-shadow: 0 8rpx 20rpx rgba(255, 107, 53, .25);
}
.btn.ghost {
  background: $bg-card;
  color: $brand-primary;
  border: 2rpx solid $brand-primary-light;
}

.footer-tip {
  margin-top: 40rpx;
  text-align: center;
  font-size: 22rpx;
  color: $text-placeholder;
}
</style>
