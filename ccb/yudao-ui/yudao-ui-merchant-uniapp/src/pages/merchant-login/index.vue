<template>
  <view class="page">
    <view class="hero">
      <view class="title">商户登录</view>
      <view class="sub">摊小二商户后台</view>
    </view>

    <view class="card">
      <view class="field">
        <text class="label">手机号</text>
        <input class="input" v-model="form.mobile" type="number" placeholder="11 位手机号" maxlength="11" />
      </view>
      <view class="field">
        <text class="label">密码</text>
        <input class="input" v-model="form.password" password placeholder="≥6 位密码" maxlength="64" />
      </view>

      <button class="submit-btn" :disabled="submitting || !canSubmit" @click="submit">
        {{ submitting ? '登录中…' : '登录' }}
      </button>

      <view class="bottom-link">
        还没有店铺？<text class="link" @click="goApply">立即入驻</text>
      </view>
      <view class="bottom-link" style="margin-top: 8rpx;">
        我是顾客？<text class="link" @click="goUserLogin">用户登录</text>
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
    const userStore = JSON.stringify({ token, userId: resp.userId, phone: resp.phone, role: resp.activeRole });
    try { if (typeof localStorage !== 'undefined') localStorage.setItem('user-store-v1', userStore); } catch {}
    try { uni.setStorageSync('token', token); } catch {}

    // 必须是商户身份才放行（不是商户提示去申请）
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
  } catch (e) {
    // toast 已由 request.js 处理
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
  background: linear-gradient(135deg, #FF6B35 0%, #FF9A4A 100%);
  padding: 80rpx 32rpx 40rpx;
}
.hero { text-align: center; color: #fff; margin-bottom: 48rpx; }
.title { font-size: 56rpx; font-weight: 700; }
.sub   { font-size: 28rpx; opacity: 0.9; margin-top: 16rpx; }

.card {
  background: #fff;
  border-radius: 24rpx;
  padding: 40rpx 32rpx 32rpx;
  box-shadow: 0 4rpx 24rpx rgba(0,0,0,0.08);
}

.field {
  display: flex; align-items: center; padding: 24rpx 0;
  border-bottom: 1rpx solid #f0f1f5;
  .label { width: 160rpx; font-size: 28rpx; color: #606266; flex-shrink: 0; }
  .input { flex: 1; font-size: 30rpx; color: #303133; }
}

.submit-btn {
  margin-top: 40rpx;
  height: 96rpx;
  line-height: 96rpx;
  background: linear-gradient(135deg, #FF6B35, #FF9A4A);
  color: #fff;
  border-radius: 48rpx;
  font-size: 32rpx;
  font-weight: 600;
  &[disabled] { opacity: 0.5; }
  &::after { border: none; }
}

.bottom-link {
  text-align: center;
  margin-top: 32rpx;
  font-size: 26rpx;
  color: #909399;
  .link { color: #FF6B35; margin-left: 8rpx; }
}
</style>
