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
    // 兼容两种返回结构：① 顶层 token=string ② 嵌套 token.accessToken
    const token =
      (typeof resp?.token === 'string' ? resp.token : null) ||
      resp?.token?.accessToken ||
      resp?.accessToken;
    if (!token) {
      uni.showToast({ title: '登录响应缺少 token', icon: 'none' });
      throw new Error('无 token');
    }
    const userStore = JSON.stringify({
      token,
      userId: resp.userId,
      phone: resp.phone,
      role: resp.activeRole,
      nickname: resp.nickname,
      shopName: resp.shopName,
    });
    try {
      if (typeof localStorage !== 'undefined') localStorage.setItem('user-store-v1', userStore);
    } catch {}
    try {
      uni.setStorageSync('token', token);
      if (resp.merchantId) uni.setStorageSync('merchantId', resp.merchantId);
    } catch {}

    const isMerchant = resp.activeRole === 'merchant' || (resp.roles || []).includes('merchant');
    if (!isMerchant) {
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
  } catch (e) {
    // toast 由 request.js 处理；这里仅作为最后兜底，避免 silently 卡住按钮
    if (e?.message === '无 token') {
      // 已在上面 toast 了
    }
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

/* 页面背景为「橙→米→白」渐变，footer 处于白底区域，
 * 必须用深色文字才能在手机端清晰可读 */
.footer {
  margin-top: 40rpx;
  padding: 16rpx 0 8rpx;
  text-align: center;
  position: relative;
  z-index: 1;
}
.footer-link {
  font-size: 26rpx;
  color: #5A6577;             /* 深灰，白底可读 */
  margin: 18rpx 0;
  line-height: 1.6;

  .link {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 56rpx;         /* 触控区 ≥ 44pt */
    padding: 6rpx 20rpx;
    margin-left: 8rpx;
    color: #FF6B35;
    font-weight: 700;
    border-radius: 28rpx;
    border: 1rpx solid rgba(255, 107, 53, 0.32);
    background: rgba(255, 107, 53, 0.06);

    /* 主行动：立即入驻 — 实心橙更突出 */
    &.primary {
      color: #fff;
      background: linear-gradient(135deg, #FF6B35, #FF9A4A);
      border-color: transparent;
      box-shadow: 0 6rpx 18rpx rgba(255, 107, 53, 0.32);
      padding: 8rpx 24rpx;
    }
  }
  .link:active { background: rgba(255, 107, 53, 0.16); }
  .link.primary:active {
    background: linear-gradient(135deg, #e85a23, #f08a3d);
    transform: translateY(1rpx);
  }
}
</style>
