<template>
  <view class="login">
    <view class="brand">
      <view class="logo">摊</view>
      <view class="name">摊小二</view>
      <view class="slogan">小摊主的生意伙伴</view>
    </view>

    <!-- 被邀请落地引导：同时展示邀请人 + 店铺（onLoad 解析 redirect 中 inviter+tenantId） -->
    <view v-if="inviterNick || shopName" class="invite-welcome card">
      <view v-if="inviterNick" class="invite-row">
        <view class="invite-avatar">{{ inviterNick.slice(0, 1) }}</view>
        <view class="invite-text">
          <view class="invite-from"><text class="invite-name">{{ inviterNick }}</text> 邀请你来</view>
          <view v-if="shopName" class="invite-shop">
            <text class="invite-shop-icon">{{ shopName.slice(0, 1) }}</text>
            <text class="invite-shop-name">{{ shopName }}</text>
          </view>
        </view>
        <view class="invite-gift">登录得 ¥3 见面礼</view>
      </view>
      <!-- 没有 inviter 但有 shopName：扫码进店场景 -->
      <view v-else-if="shopName" class="shop-welcome">
        <view class="welcome-tag">欢迎光临</view>
        <view class="welcome-shop-name">{{ shopName }}</view>
        <view class="welcome-sub">登录后即可下单 · 自动成为店铺会员</view>
      </view>
    </view>

    <!-- 3 大价值主张（未登录时展示） -->
    <view v-if="!hasToken" class="value-prop">
      <view class="vp">
        <view class="vp-ic">🎯</view>
        <view class="vp-body">
          <view class="vp-ttl">边吃边赚 · 邀请最高 <text class="vp-em">返 100%</text> 商品价</view>
          <view class="vp-desc">推 N 个朋友买同款，每位都给你返推广积分</view>
        </view>
      </view>
      <view class="vp">
        <view class="vp-ic">🏪</view>
        <view class="vp-body">
          <view class="vp-ttl">本地小店 一键直达</view>
          <view class="vp-desc">附近的烧烤、水果、早点摊，扫码进店即时下单</view>
        </view>
      </view>
      <view class="vp">
        <view class="vp-ic">⭐</view>
        <view class="vp-body">
          <view class="vp-ttl">多店独立会员 · 长期复购更赚</view>
          <view class="vp-desc">每家店独立星级 + 余额 + 推广积分</view>
        </view>
      </view>
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
import { request } from '../../api/request.js';
import { useUserStore } from '../../store/user.js';

const userStore = useUserStore();
const loading = ref(false);
// 扫码进店登录时展示的店铺名（onLoad 解析 redirect 中的 tenantId 后异步拉取）
const shopName = ref('');
// 被邀请场景：邀请人昵称（拉自 /app/auth/inviter-info）
const inviterNick = ref('');
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
    // 普通用户登录 → C 端首页 user-home（不是商户工作台 /pages/index/index）
    // 注：从 shop-home 分享链接进的登录已经在上面 consumeRedirect 优先返回了，
    // 走到这里说明是直接打开 /pages/login/index 登录的纯用户场景
    uni.reLaunch({ url: '/pages/user-home/index' });
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

onLoad((query) => {
  // 接收 query.redirect — shop-home 跳过来时带的回跳路径
  // 优先级：query.redirect > localStorage redirect:after-login > 默认 /pages/index/index
  if (query?.redirect) {
    try {
      if (typeof localStorage !== 'undefined') {
        localStorage.setItem('redirect:after-login', decodeURIComponent(query.redirect));
      }
    } catch {}
    // 解析 redirect 中的 tenantId + inviter → 拉店铺名 + 邀请人昵称
    // 双展示让用户清楚"谁邀请我来哪家店"，登录后会自动绑上下级 + 入会员
    try {
      const decoded = decodeURIComponent(query.redirect);
      const tm = decoded.match(/[?&]tenantId=(\d+)/);
      const im = decoded.match(/[?&](?:inviter|referrerUserId)=(\d+)/);
      const tid = tm ? Number(tm[1]) : null;
      const inviterId = im ? Number(im[1]) : null;
      if (tid) {
        request({ url: `/app-api/merchant/shop/public/info?tenantId=${tid}` })
          .then((info) => { if (info?.shopName) shopName.value = info.shopName; })
          .catch(() => {});
      }
      if (inviterId && inviterId > 0) {
        request({ url: `/app-api/app/auth/inviter-info?inviterUserId=${inviterId}` })
          .then((info) => { if (info?.nickname) inviterNick.value = info.nickname; })
          .catch(() => {});
      }
    } catch {}
  }
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

// 被邀请落地：邀请人 + 店铺联合卡
.invite-welcome {
  margin-bottom: 24rpx;
  padding: 28rpx 28rpx 24rpx;
  background: $bg-card;
  border-radius: $radius-lg;
  border: 2rpx solid rgba(255, 107, 53, 0.20);
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.06);

  .invite-row {
    display: flex;
    align-items: center;
    gap: 20rpx;
  }
  .invite-avatar {
    width: 80rpx;
    height: 80rpx;
    border-radius: 50%;
    background: linear-gradient(135deg, #ff9a4a, #FF6B35);
    color: #fff;
    font-size: 32rpx;
    font-weight: 700;
    text-align: center;
    line-height: 80rpx;
    flex-shrink: 0;
    border: 4rpx solid rgba(255, 107, 53, 0.18);
  }
  .invite-text { flex: 1; min-width: 0; }
  .invite-from {
    font-size: 24rpx;
    color: $text-secondary;
  }
  .invite-name {
    color: $brand-primary;
    font-weight: 700;
  }
  .invite-shop {
    margin-top: 6rpx;
    font-size: 28rpx;
    font-weight: 800;
    color: $text-primary;
    display: flex;
    align-items: center;
    gap: 10rpx;
  }
  .invite-shop-icon {
    width: 40rpx;
    height: 40rpx;
    border-radius: 8rpx;
    background: linear-gradient(135deg, #ffd1ba, $brand-primary);
    color: #fff;
    font-size: 22rpx;
    font-weight: 700;
    text-align: center;
    line-height: 40rpx;
  }
  .invite-shop-name { font-size: 30rpx; }
  .invite-gift {
    background: rgba(255, 107, 53, 0.12);
    color: $brand-primary;
    font-size: 22rpx;
    font-weight: 700;
    padding: 6rpx 14rpx;
    border-radius: 999rpx;
    flex-shrink: 0;
  }
}

// 扫码进店登录引导卡（带店铺名）
.shop-welcome {
  margin-bottom: 24rpx;
  text-align: center;
  padding: 36rpx 32rpx 32rpx;
  background: linear-gradient(135deg, rgba(255, 107, 53, 0.10), rgba(255, 154, 74, 0.06));
  border: 1rpx solid rgba(255, 107, 53, 0.18);

  .welcome-tag {
    display: inline-block;
    padding: 4rpx 16rpx;
    background: $brand-primary;
    color: #fff;
    border-radius: 999rpx;
    font-size: 22rpx;
    margin-bottom: 16rpx;
  }
  .welcome-shop-name {
    font-size: 36rpx;
    font-weight: 700;
    color: $text-primary;
    line-height: 1.2;
  }
  .welcome-sub {
    margin-top: 12rpx;
    font-size: 24rpx;
    color: $text-secondary;
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

// 3 大价值主张
.value-prop {
  margin-bottom: 24rpx;
  padding: 8rpx;
  display: flex;
  flex-direction: column;
  gap: 4rpx;

  .vp {
    display: flex;
    align-items: flex-start;
    gap: 20rpx;
    padding: 22rpx 24rpx;
    background: $bg-card;
    border-radius: $radius-lg;
    box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
    margin-bottom: 12rpx;
    &:last-child { margin-bottom: 0; }
  }

  .vp-ic {
    width: 56rpx;
    height: 56rpx;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 36rpx;
    background: linear-gradient(135deg, #fff3ec, #ffd9bf);
    border-radius: 16rpx;
  }

  .vp-body { flex: 1; min-width: 0; }

  .vp-ttl {
    font-size: 28rpx;
    font-weight: 700;
    color: $text-primary;
    line-height: 1.45;
  }
  .vp-em {
    color: $brand-primary;
  }
  .vp-desc {
    margin-top: 6rpx;
    font-size: 22rpx;
    color: $text-secondary;
    line-height: 1.5;
  }
}

// 社交证明（5 个头像 + 用户数）
.social-strip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  margin-bottom: 32rpx;
  padding: 18rpx 24rpx;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 999rpx;
  border: 1rpx solid rgba(255, 107, 53, 0.12);

  .avatars {
    display: flex;
    align-items: center;
    .av {
      width: 44rpx;
      height: 44rpx;
      border-radius: 50%;
      color: #fff;
      font-size: 22rpx;
      font-weight: 700;
      display: flex;
      align-items: center;
      justify-content: center;
      border: 2rpx solid #fff;
      margin-left: -10rpx;
      &:first-child { margin-left: 0; }
    }
    .av1 { background: linear-gradient(135deg, #ff9a4a, #FF6B35); }
    .av2 { background: linear-gradient(135deg, #c9e0ff, #6196f0); }
    .av3 { background: linear-gradient(135deg, #d3f4d3, #4cb84c); }
    .av4 { background: linear-gradient(135deg, #ffd0dc, #ee5a8b); }
    .av5 { background: linear-gradient(135deg, #e0d4ff, #9170dd); }
  }

  .social-text {
    font-size: 22rpx;
    color: $text-secondary;
    .num {
      color: $text-primary;
      font-weight: 700;
    }
  }
}
</style>
