<template>
  <!-- 已登录或有 redirect 落地：纯 spinner，立即重定向不渲染表单 -->
  <view v-if="redirecting" class="redir">
    <view class="spinner" />
    <view class="hint">{{ hint }}</view>
  </view>

  <!-- 未登录且无 redirect：H5 手机号 + 密码登录表单（无短信，输入即注册） -->
  <view v-else class="login">
    <view class="brand">
      <view class="logo">客</view>
      <view class="name">客小二</view>
      <view class="slogan">扫码进店 · 下单更顺手</view>
    </view>

    <view class="card">
      <view class="sec-title">手机号登录</view>
      <view class="sec-sub">首次登录即注册，无需短信验证（演示环境）</view>

      <view class="field">
        <text class="label">手机号</text>
        <input
          class="input"
          type="number"
          maxlength="11"
          placeholder="请输入手机号"
          v-model="loginMobile"
        />
      </view>
      <view class="field">
        <text class="label">密码（≥ 6 位）</text>
        <input
          class="input"
          type="password"
          maxlength="64"
          placeholder="设置一个密码"
          v-model="loginPassword"
        />
      </view>

      <button
        class="submit"
        :disabled="!canPasswordLogin || passwordLogining"
        @click="onPasswordLogin"
      >
        {{ passwordLogining ? '登录中…' : '登录 / 注册' }}
      </button>

      <view class="hint-row">
        <text class="hint-text">登录后即可下单 · 收推广积分</text>
      </view>
      <view class="bottom-link">
        <text>已是商户？</text>
        <text class="link-text" @click="goMerchantLogin">商户登录 →</text>
      </view>
      <view class="bottom-link">
        <text class="link-text" @click="skipLogin">先逛逛 →</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { useUserStore } from '../../store/user.js';

const userStore = useUserStore();

// 始终显示登录表单 —— 按产品要求 login 永不自动跳转，避免任何死循环可能
const redirecting = ref(false);
const hint = ref('正在进入…');

// 表单态
const loginMobile = ref('');
const loginPassword = ref('');
const passwordLogining = ref(false);
const canPasswordLogin = computed(
  () => /^1[3-9]\d{9}$/.test(loginMobile.value) && loginPassword.value.length >= 6
);

function readStoredRedirect() {
  try {
    if (typeof localStorage !== 'undefined') {
      return localStorage.getItem('redirect:after-login') || '';
    }
  } catch {}
  return '';
}

function consumeRedirect() {
  try {
    if (typeof localStorage !== 'undefined') localStorage.removeItem('redirect:after-login');
  } catch {}
}

function routeByRole() {
  const t = readStoredRedirect();
  if (t) {
    consumeRedirect();
    uni.reLaunch({ url: t });
    return;
  }
  if (userStore.activeRole === 'merchant') {
    uni.reLaunch({ url: '/pages/index/index' });
    return;
  }
  uni.reLaunch({ url: '/pages/user-home/index' });
}

function decide(query) {
  // 永不自动跳转 —— 进 login 就显示表单。
  // 仅保存 query.redirect 到 localStorage，供 onPasswordLogin 登录成功后消费。
  if (query?.redirect) {
    try {
      if (typeof localStorage !== 'undefined') {
        localStorage.setItem('redirect:after-login', decodeURIComponent(query.redirect));
      }
    } catch {}
  }
  redirecting.value = false;
}

function goMerchantLogin() {
  uni.redirectTo({ url: '/pages/merchant-login/index' });
}

function skipLogin() {
  uni.reLaunch({ url: '/pages/user-home/index' });
}

async function onPasswordLogin() {
  if (!canPasswordLogin.value) return;
  passwordLogining.value = true;
  try {
    await userStore.passwordLogin(loginMobile.value.trim(), loginPassword.value);
    uni.showToast({ title: '登录成功', icon: 'success' });
    routeByRole();
  } catch (e) {
    const msg = String(e?.message || e);
    let title;
    if (/手机号或密码错误/.test(msg) || /password.*invalid/i.test(msg)) {
      title = '手机号或密码错误';
    } else if (/操作过于频繁|TOO_MANY_REQUESTS/i.test(msg)) {
      title = '操作过于频繁，请稍后再试';
    } else {
      title = '登录失败：' + msg;
    }
    uni.showToast({ title, icon: 'none' });
  } finally {
    passwordLogining.value = false;
  }
}

onLoad((query) => {
  // 给浏览器一个微小延迟让 spinner 显出来再做决定（弱网/动画更顺）
  setTimeout(() => decide(query), 30);
});
</script>

<style lang="scss" scoped>
.redir {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, #fff3ec 0%, #f6f7f9 60%);
}
.spinner {
  width: 80rpx;
  height: 80rpx;
  border: 6rpx solid rgba(255, 107, 53, 0.18);
  border-top-color: #ff6b35;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}
.hint {
  margin-top: 24rpx;
  font-size: 26rpx;
  color: #6b7280;
}
@keyframes spin { to { transform: rotate(360deg); } }

.login {
  min-height: 100vh;
  padding: 120rpx 48rpx 48rpx;
  background: linear-gradient(180deg, #fff3ec 0%, #f6f7f9 60%);
}
.brand {
  text-align: center;
  margin-bottom: 72rpx;
}
.brand .logo {
  width: 140rpx;
  height: 140rpx;
  margin: 0 auto 24rpx;
  border-radius: 36rpx;
  background: #ff6b35;
  color: #fff;
  font-size: 72rpx;
  font-weight: 700;
  line-height: 140rpx;
  box-shadow: 0 16rpx 40rpx rgba(255, 107, 53, 0.3);
}
.brand .name {
  font-size: 48rpx;
  font-weight: 700;
  color: #1f2937;
  letter-spacing: 4rpx;
}
.brand .slogan {
  margin-top: 12rpx;
  font-size: 26rpx;
  color: #6b7280;
}

.card {
  background: #fff;
  border-radius: 24rpx;
  padding: 40rpx 32rpx 32rpx;
  box-shadow: 0 2rpx 16rpx rgba(0, 0, 0, 0.04);
}
.sec-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 8rpx;
}
.sec-sub {
  font-size: 24rpx;
  color: #6b7280;
  margin-bottom: 24rpx;
}
.field {
  margin-bottom: 24rpx;
}
.field .label {
  display: block;
  font-size: 26rpx;
  color: #6b7280;
  margin-bottom: 12rpx;
}
.input {
  width: 100%;
  height: 88rpx;
  padding: 0 24rpx;
  background: #f6f7f9;
  border-radius: 16rpx;
  font-size: 30rpx;
  color: #1f2937;
}
.submit {
  width: 100%;
  height: 96rpx;
  line-height: 96rpx;
  margin-top: 20rpx;
  background: #ff6b35;
  color: #fff;
  border-radius: 16rpx;
  font-size: 32rpx;
  font-weight: 600;
}
.submit[disabled] {
  background: #c5c8cf;
  color: #fff;
}
.submit::after {
  border: none;
}
.hint-row {
  margin-top: 20rpx;
  text-align: center;
}
.hint-text {
  font-size: 24rpx;
  color: #9ca3af;
}
.bottom-link {
  margin-top: 18rpx;
  padding-top: 18rpx;
  border-top: 1rpx solid #f0f0f3;
  text-align: center;
  font-size: 26rpx;
  color: #6b7280;
}
.bottom-link:first-of-type {
  margin-top: 24rpx;
}
.link-text {
  color: #ff6b35;
  margin-left: 8rpx;
}
</style>
