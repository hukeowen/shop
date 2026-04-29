<template>
  <view class="page">
    <!-- 顶部品牌 -->
    <view class="hero">
      <view class="logo">摊</view>
      <view class="brand">
        <view class="brand-name">商户登录</view>
        <view class="brand-slogan">摊小二 SaaS 后台</view>
      </view>
    </view>

    <!-- 登录卡片 -->
    <view class="card">
      <view class="card-title">欢迎回来</view>
      <view class="card-sub">登录您的店铺管理后台</view>

      <view class="field">
        <text class="field-icon">📱</text>
        <input
          class="field-input"
          v-model="form.mobile"
          type="number"
          placeholder="手机号（11 位）"
          maxlength="11"
        />
      </view>

      <view class="field">
        <text class="field-icon">🔒</text>
        <input
          class="field-input"
          v-model="form.password"
          password
          placeholder="密码（≥6 位）"
          maxlength="64"
        />
      </view>

      <button
        class="submit-btn"
        :class="{ active: canSubmit, loading: submitting }"
        :disabled="submitting || !canSubmit"
        @click="submit"
      >
        <text v-if="!submitting">登录</text>
        <text v-else>登录中…</text>
      </button>

      <view class="forgot-row">
        <text class="forgot-text">·首次登录无需注册，输入即设密码</text>
      </view>
    </view>

    <!-- 底部 -->
    <view class="footer">
      <view class="footer-link">
        还没有店铺？
        <text class="link primary" @click="goApply">立即入驻 →</text>
      </view>
      <view class="footer-link">
        我是顾客？
        <text class="link" @click="goUserLogin">用户登录</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, computed } from 'vue';
import { request } from '../../api/request.js';

const form = reactive({ mobile: '', password: '' });
const submitting = ref(false);

const canSubmit = computed(
  () => /^1[3-9]\d{9}$/.test(form.mobile) && form.password.length >= 6
);

async function submit() {
  if (!canSubmit.value || submitting.value) return;
  submitting.value = true;
  try {
    const resp = await request({
      url: '/app-api/app/auth/password-login',
      method: 'POST',
      data: { mobile: form.mobile, password: form.password },
    });
    const token = resp?.token?.accessToken || resp?.accessToken;
    if (!token) throw new Error('无 token');
    const userStore = JSON.stringify({
      token,
      userId: resp.userId,
      phone: resp.phone,
      role: resp.activeRole,
    });
    try {
      if (typeof localStorage !== 'undefined') localStorage.setItem('user-store-v1', userStore);
    } catch {}
    try { uni.setStorageSync('token', token); } catch {}

    if (resp.activeRole !== 'merchant' && !(resp.roles || []).includes('merchant')) {
      uni.showModal({
        title: '尚未开通商户',
        content: '该手机号还没申请商户，是否立即入驻？',
        confirmText: '立即入驻',
        cancelText: '取消',
        success: (r) => {
          if (r.confirm) uni.redirectTo({ url: '/pages/merchant-apply/index' });
        },
      });
      return;
    }
    uni.reLaunch({ url: '/pages/me/index' });
  } catch {
    // toast 由 request.js 处理
  } finally {
    submitting.value = false;
  }
}

function goApply() {
  uni.navigateTo({ url: '/pages/merchant-apply/index' });
}
function goUserLogin() {
  uni.redirectTo({ url: '/pages/login/index' });
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: linear-gradient(180deg, #FF6B35 0%, #FF9A4A 30%, #FFE5D6 65%, #fff 100%);
  padding: 80rpx 32rpx 60rpx;
  position: relative;
  overflow: hidden;
}
.page::before, .page::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
  pointer-events: none;
}
.page::before { width: 400rpx; height: 400rpx; top: -100rpx; right: -100rpx; }
.page::after  { width: 300rpx; height: 300rpx; top: 200rpx; left: -120rpx; }

.hero {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-bottom: 60rpx;
  position: relative;
  z-index: 1;
}
.logo {
  width: 96rpx;
  height: 96rpx;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 56rpx;
  font-weight: 800;
  color: #FF6B35;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.12);
}
.brand-name {
  font-size: 44rpx;
  font-weight: 800;
  color: #fff;
}
.brand-slogan {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.9);
  margin-top: 4rpx;
}

.card {
  background: #fff;
  border-radius: 32rpx;
  padding: 48rpx 36rpx 36rpx;
  box-shadow: 0 20rpx 60rpx rgba(0, 0, 0, 0.12);
  position: relative;
  z-index: 1;
}
.card-title {
  font-size: 40rpx;
  font-weight: 700;
  color: #1a1a1a;
  text-align: center;
}
.card-sub {
  font-size: 24rpx;
  color: #909399;
  text-align: center;
  margin: 8rpx 0 36rpx;
}

.field {
  display: flex;
  align-items: center;
  background: #F7F8FA;
  border-radius: 16rpx;
  padding: 0 20rpx;
  height: 96rpx;
  margin-bottom: 20rpx;
  border: 2rpx solid transparent;
  &:focus-within { border-color: #FF6B35; background: #fff; }
}
.field-icon { font-size: 32rpx; margin-right: 12rpx; flex-shrink: 0; }
.field-input {
  flex: 1;
  font-size: 30rpx;
  color: #1a1a1a;
  background: transparent;
  border: none;
  outline: none;
}

.submit-btn {
  margin-top: 16rpx;
  width: 100%;
  height: 104rpx;
  line-height: 104rpx;
  background: linear-gradient(135deg, #C8C9CC, #E5E7EB);
  color: #fff;
  border-radius: 52rpx;
  font-size: 32rpx;
  font-weight: 700;
  letter-spacing: 4rpx;
  transition: all 0.3s;

  &.active {
    background: linear-gradient(135deg, #FF6B35, #FF9A4A);
    box-shadow: 0 8rpx 24rpx rgba(255, 107, 53, 0.35);
  }
  &.loading { opacity: 0.8; }
  &::after { border: none; }
}

.forgot-row {
  text-align: center;
  margin-top: 20rpx;
}
.forgot-text {
  font-size: 22rpx;
  color: #909399;
}

.footer {
  margin-top: 36rpx;
  text-align: center;
  position: relative;
  z-index: 1;
}
.footer-link {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.9);
  margin: 14rpx 0;

  .link {
    color: #fff;
    font-weight: 600;
    margin-left: 8rpx;
    &.primary {
      background: rgba(255, 255, 255, 0.18);
      backdrop-filter: blur(10rpx);
      padding: 8rpx 20rpx;
      border-radius: 20rpx;
      border: 1rpx solid rgba(255, 255, 255, 0.3);
    }
  }
}
</style>
