<template>
  <view class="login">
    <view class="brand">
      <view class="logo">摊</view>
      <view class="name">摊小二</view>
      <view class="slogan">一个人的摊，也能做出一家店</view>
    </view>

    <!-- H5（非小程序）环境：手机号+密码登录（演示用，首次输入即注册） -->
    <view v-if="isH5 && !hasToken" class="card">
      <view class="sec-title">手机号登录</view>
      <view class="sec-sub">首次登录即注册，无需短信验证</view>

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

      <view class="hint">演示环境不发短信，输入手机号 + 密码即可登录</view>
    </view>

    <!-- 正在登录 -->
    <view v-else-if="loading" class="card center">
      <view class="spinner" />
      <view class="status">{{ loadingText }}</view>
    </view>

    <!-- 双角色切换卡片 -->
    <view v-else-if="needRoleChoice" class="card">
      <view class="sec-title">选择进入的身份</view>
      <view class="sec-sub">你既是商户也是用户，先进哪边？</view>
      <view class="role-grid">
        <view class="role-card" @click="onChooseRole('merchant')">
          <view class="role-icon merchant">摊</view>
          <view class="role-name">商户端</view>
          <view class="role-desc">管订单 / 发货 / AI 成片</view>
        </view>
        <view class="role-card" @click="onChooseRole('member')">
          <view class="role-icon member">★</view>
          <view class="role-name">用户端</view>
          <view class="role-desc">逛店 / 下单 / 核销码</view>
        </view>
      </view>
    </view>

    <!-- 申请成为商户 -->
    <view v-else-if="showApplyMerchant" class="card">
      <view class="sec-title">申请成为商户</view>
      <view class="sec-sub">凭邀请码开通商户，开通后可管理店铺和商品</view>

      <view class="field">
        <text class="label">邀请码</text>
        <input
          class="input"
          placeholder="请输入 6-12 位邀请码"
          v-model="inviteCode"
          maxlength="16"
        />
      </view>

      <!-- 已绑手机号：直接申请 -->
      <button
        v-if="userStore.phone"
        class="submit"
        :disabled="!canApply || applying"
        @click="onApplyDirect"
      >
        {{ applying ? '提交中…' : '申请开通' }}
      </button>
      <!-- 未绑手机号：走 getPhoneNumber 一步到位 -->
      <button
        v-else
        class="submit"
        open-type="getPhoneNumber"
        :disabled="!canApply || applying"
        @getphonenumber="onApplyWithPhone"
      >
        {{ applying ? '提交中…' : '授权手机号并申请' }}
      </button>

      <view class="hint">
        没有邀请码？联系对接的招商经理拿一个。
      </view>
      <view class="back-link" @click="backToDefault">返回</view>
    </view>

    <!-- 默认：匿名用户首页入口 + 申请商户入口 -->
    <view v-else class="card">
      <view class="sec-title">欢迎</view>
      <view class="sec-sub">{{ welcomeHint }}</view>

      <button class="submit" @click="goUserHome">开始逛店</button>
      <button class="submit ghost" @click="showApplyMerchant = true">
        我是商户，去申请
      </button>
    </view>

    <view class="footer">
      <text>Phase 0.2 · 微信小程序登录接后端 BFF</text>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { useUserStore } from '../../store/user.js';

const userStore = useUserStore();
const loading = ref(false);
const loadingText = ref('登录中…');
const applying = ref(false);
const showApplyMerchant = ref(false);
const inviteCode = ref('');

// H5 手机号+密码登录（演示用）
const loginMobile = ref('');
const loginPassword = ref('');
const passwordLogining = ref(false);
const canPasswordLogin = computed(
  () => /^1[3-9]\d{9}$/.test(loginMobile.value) && loginPassword.value.length >= 6
);

// #ifdef H5
const isH5 = ref(true);
// #endif
// #ifndef H5
const isH5 = ref(false);
// #endif

const hasToken = computed(() => !!userStore.token);

const needRoleChoice = computed(
  () =>
    !loading.value &&
    userStore.token &&
    (userStore.roles || []).length >= 2 &&
    !userStore.activeRole
);

const canApply = computed(() => inviteCode.value.trim().length >= 4);

const welcomeHint = computed(() => {
  if (!userStore.token) return '微信登录后即可浏览店铺';
  if (!userStore.roles?.length) return '你还没有任何角色，可以先逛逛，或申请成为商户';
  return '已登录 · 你可以继续';
});

async function autoLogin() {
  loading.value = true;
  loadingText.value = '登录中…';
  try {
    await userStore.wxMiniLogin();
    loading.value = false;
    routeByRole();
  } catch (e) {
    loading.value = false;
    const msg = String(e?.message || e);
    if (/code|weixin/i.test(msg)) {
      // H5 环境 uni.login 失败走 h5-hint，什么也不做
      return;
    }
    uni.showToast({ title: '登录失败：' + msg, icon: 'none' });
  }
}

function consumeRedirect() {
  // 落地时 App.vue 暂存的"原意图 URL"，登录成功后优先回这里
  try {
    if (typeof localStorage === 'undefined') return '';
    const target = localStorage.getItem('redirect:after-login');
    if (target) {
      localStorage.removeItem('redirect:after-login');
      return target;
    }
  } catch {}
  return '';
}

function routeByRole() {
  // 登录成功后：如果落地时有原意图（如 /pages/shop-home/index?inviter=1），优先回
  const redirect = consumeRedirect();
  if (redirect) {
    uni.reLaunch({ url: redirect });
    return;
  }
  const roles = userStore.roles || [];
  const active = userStore.activeRole;
  if (active === 'merchant' || (roles.length === 1 && roles[0] === 'merchant')) {
    uni.reLaunch({ url: '/pages/index/index' });
    return;
  }
  if (active === 'member' || (roles.length === 1 && roles[0] === 'member')) {
    // 用户端首页暂未独立开发，先复用当前首页（Phase 2 会拆分）
    uni.reLaunch({ url: '/pages/index/index' });
    return;
  }
  if (roles.length >= 2 && !active) {
    // 渲染双角色切换 UI
    return;
  }
  // roles 为空：留在登录页上让用户选「逛店 / 申请商户」
}

async function onChooseRole(role) {
  loading.value = true;
  loadingText.value = '切换身份中…';
  try {
    await userStore.switchRole(role);
    loading.value = false;
    uni.reLaunch({ url: '/pages/index/index' });
  } catch (e) {
    loading.value = false;
    uni.showToast({ title: '切换失败：' + (e.message || ''), icon: 'none' });
  }
}

function goUserHome() {
  uni.reLaunch({ url: '/pages/index/index' });
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

function backToDefault() {
  showApplyMerchant.value = false;
  inviteCode.value = '';
}

async function onApplyDirect() {
  if (!canApply.value) return;
  applying.value = true;
  try {
    await userStore.applyMerchant(inviteCode.value.trim());
    uni.showToast({ title: '商户申请成功', icon: 'success' });
    uni.reLaunch({ url: '/pages/index/index' });
  } catch (e) {
    uni.showToast({ title: '申请失败：' + (e.message || ''), icon: 'none' });
  } finally {
    applying.value = false;
  }
}

async function onApplyWithPhone(e) {
  if (!canApply.value) return;
  if (!e?.detail?.encryptedData) {
    uni.showToast({ title: '未授权手机号', icon: 'none' });
    return;
  }
  applying.value = true;
  try {
    await userStore.applyMerchant(inviteCode.value.trim(), e);
    uni.showToast({ title: '商户申请成功', icon: 'success' });
    uni.reLaunch({ url: '/pages/index/index' });
  } catch (err) {
    uni.showToast({ title: '申请失败：' + (err.message || ''), icon: 'none' });
  } finally {
    applying.value = false;
  }
}

onLoad(() => {
  // 已有 token：直接刷新 me 再跳（避免每次进入都重登录）
  if (userStore.token) {
    loading.value = true;
    loadingText.value = '校验登录…';
    userStore
      .refreshMe()
      .then(() => {
        loading.value = false;
        routeByRole();
      })
      .catch(() => {
        loading.value = false;
        userStore.logout();
        autoLogin();
      });
    return;
  }
  // #ifdef MP-WEIXIN
  autoLogin();
  // #endif
  // #ifdef H5
  // H5 仅作原型预览，不自动调 uni.login（会失败）
  // #endif
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.login {
  min-height: 100vh;
  padding: 120rpx 48rpx 48rpx;
  background: linear-gradient(180deg, #fff3ec 0%, #f6f7f9 60%);
}

.brand {
  text-align: center;
  margin-bottom: 72rpx;

  .logo {
    width: 140rpx;
    height: 140rpx;
    margin: 0 auto 24rpx;
    border-radius: 36rpx;
    background: $brand-primary;
    color: #fff;
    font-size: 72rpx;
    font-weight: 700;
    line-height: 140rpx;
    box-shadow: 0 16rpx 40rpx rgba(255, 107, 53, 0.3);
  }

  .name {
    font-size: 48rpx;
    font-weight: 700;
    color: $text-primary;
    letter-spacing: 4rpx;
  }

  .slogan {
    margin-top: 12rpx;
    font-size: 26rpx;
    color: $text-secondary;
  }
}

.card {
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 40rpx 32rpx 32rpx;
  box-shadow: 0 2rpx 16rpx rgba(0, 0, 0, 0.04);

  &.center {
    text-align: center;
    padding: 64rpx 32rpx;
  }
}

.sec-title {
  font-size: 32rpx;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 8rpx;
}

.sec-sub {
  font-size: 24rpx;
  color: $text-secondary;
  margin-bottom: 24rpx;
}

.field {
  margin-bottom: 24rpx;

  .label {
    display: block;
    font-size: 26rpx;
    color: $text-secondary;
    margin-bottom: 12rpx;
  }
}

.input {
  width: 100%;
  height: 88rpx;
  padding: 0 24rpx;
  background: #f6f7f9;
  border-radius: $radius-md;
  font-size: 30rpx;
  color: $text-primary;
}

.submit {
  width: 100%;
  height: 96rpx;
  line-height: 96rpx;
  margin-top: 20rpx;
  background: $brand-primary;
  color: #fff;
  border-radius: $radius-md;
  font-size: 32rpx;
  font-weight: 600;

  &.ghost {
    background: #fff;
    color: $brand-primary;
    border: 2rpx solid $brand-primary;
  }

  &[disabled] {
    background: $text-placeholder;
    color: #fff;
  }

  &::after {
    border: none;
  }
}

.hint {
  margin-top: 20rpx;
  text-align: center;
  font-size: 24rpx;
  color: $text-secondary;
}

.back-link {
  margin-top: 32rpx;
  text-align: center;
  font-size: 26rpx;
  color: $brand-primary;
}

.footer {
  margin-top: 64rpx;
  text-align: center;
  font-size: 24rpx;
  color: $text-placeholder;
}

.h5-hint {
  text-align: center;

  .hint-title {
    font-size: 32rpx;
    font-weight: 600;
    color: $warning;
  }

  .hint-sub {
    margin-top: 16rpx;
    font-size: 24rpx;
    color: $text-regular;
    line-height: 1.8;
  }

  .mono {
    font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
    background: #f6f7f9;
    padding: 2rpx 8rpx;
    border-radius: 6rpx;
  }
}

.spinner {
  width: 72rpx;
  height: 72rpx;
  margin: 0 auto 24rpx;
  border: 6rpx solid $brand-primary-light;
  border-top-color: $brand-primary;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.status {
  font-size: 26rpx;
  color: $text-regular;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.role-grid {
  margin-top: 12rpx;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20rpx;

  .role-card {
    padding: 32rpx 20rpx 28rpx;
    background: #f6f7f9;
    border-radius: $radius-md;
    text-align: center;
    border: 2rpx solid transparent;

    &:active {
      background: $brand-primary-light;
      border-color: $brand-primary;
    }

    .role-icon {
      width: 96rpx;
      height: 96rpx;
      line-height: 96rpx;
      margin: 0 auto 12rpx;
      border-radius: 50%;
      color: #fff;
      font-size: 40rpx;
      font-weight: 700;

      &.merchant {
        background: $brand-primary;
      }
      &.member {
        background: #3b82f6;
      }
    }

    .role-name {
      font-size: 30rpx;
      font-weight: 600;
      color: $text-primary;
    }

    .role-desc {
      margin-top: 6rpx;
      font-size: 22rpx;
      color: $text-secondary;
    }
  }
}
</style>
