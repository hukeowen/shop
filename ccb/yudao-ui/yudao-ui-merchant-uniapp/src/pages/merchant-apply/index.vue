<template>
  <view class="page">
    <view class="hero">
      <view class="title">摊小二 · 商户入驻</view>
      <view class="sub">填写下方信息，1 分钟开通您的店铺</view>
    </view>

    <view class="card">
      <view class="field">
        <text class="label">店铺名称</text>
        <input class="input" v-model="form.shopName" placeholder="例如：老王烧烤摊" maxlength="32" />
      </view>
      <view class="field">
        <text class="label">手机号</text>
        <input class="input" v-model="form.mobile" type="number" placeholder="11 位手机号" maxlength="11" />
      </view>
      <view class="field sms-row">
        <text class="label">验证码</text>
        <input class="input sms-input" v-model="form.smsCode" type="number" placeholder="6 位验证码" maxlength="6" />
        <button class="sms-btn" :disabled="smsCooldown > 0" @click="sendSms">
          {{ smsCooldown > 0 ? smsCooldown + 's' : '获取验证码' }}
        </button>
      </view>

      <view class="tips">
        <text>· 演示模式验证码固定 888888</text>
        <text>· 申请成功后用同手机号 + 任意 ≥6 位密码登录</text>
      </view>

      <button class="submit-btn" :disabled="submitting || !canSubmit" @click="submit">
        {{ submitting ? '申请中…' : '立即申请入驻' }}
      </button>

      <view class="bottom-link">
        已有商户账号？<text class="link" @click="goLogin">去登录</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, computed } from 'vue';
import { request } from '../../api/request.js';

const form = reactive({ shopName: '', mobile: '', smsCode: '' });
const submitting = ref(false);
const smsCooldown = ref(0);
let cooldownTimer = null;

const canSubmit = computed(
  () =>
    form.shopName.trim().length >= 2 &&
    /^1[3-9]\d{9}$/.test(form.mobile) &&
    form.smsCode.length === 6
);

function sendSms() {
  if (!/^1[3-9]\d{9}$/.test(form.mobile)) {
    uni.showToast({ title: '请先填写正确手机号', icon: 'none' });
    return;
  }
  // 演示模式直接 toast 提示固定 888888；生产替换成调真发码 API
  uni.showToast({ title: '验证码：888888（演示）', icon: 'none', duration: 3000 });
  smsCooldown.value = 60;
  cooldownTimer = setInterval(() => {
    smsCooldown.value--;
    if (smsCooldown.value <= 0) clearInterval(cooldownTimer);
  }, 1000);
}

async function submit() {
  if (!canSubmit.value || submitting.value) return;
  submitting.value = true;
  try {
    const resp = await request({
      url: '/app-api/app/auth/apply-merchant-by-sms',
      method: 'POST',
      data: {
        shopName: form.shopName.trim(),
        mobile: form.mobile,
        smsCode: form.smsCode,
      },
    });
    // 自动登录：把 token 存好，跳转商户后台
    if (resp?.token?.accessToken || resp?.accessToken) {
      const token = resp.token?.accessToken || resp.accessToken;
      const userStore = JSON.stringify({ token, userId: resp.userId, phone: resp.phone, role: 'merchant' });
      try {
        if (typeof localStorage !== 'undefined') localStorage.setItem('user-store-v1', userStore);
      } catch {}
      try {
        uni.setStorageSync('token', token);
        if (resp.merchantId) uni.setStorageSync('merchantId', resp.merchantId);
      } catch {}
    }
    uni.showModal({
      title: '🎉 申请成功',
      content: '您的店铺已开通！现在为您跳转到商户后台',
      showCancel: false,
      success: () => uni.reLaunch({ url: '/pages/me/index' }),
    });
  } catch (e) {
    // toast 已由 request.js 处理
  } finally {
    submitting.value = false;
  }
}

function goLogin() {
  uni.navigateTo({ url: '/pages/merchant-login/index' });
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: linear-gradient(135deg, #FF6B35 0%, #FF9A4A 100%);
  padding: 80rpx 32rpx 40rpx;
}
.hero {
  text-align: center;
  color: #fff;
  margin-bottom: 48rpx;
}
.title { font-size: 56rpx; font-weight: 700; }
.sub   { font-size: 28rpx; opacity: 0.9; margin-top: 16rpx; }

.card {
  background: #fff;
  border-radius: 24rpx;
  padding: 40rpx 32rpx 32rpx;
  box-shadow: 0 4rpx 24rpx rgba(0,0,0,0.08);
}

.field {
  display: flex;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 1rpx solid #f0f1f5;

  .label { width: 160rpx; font-size: 28rpx; color: #606266; flex-shrink: 0; }
  .input { flex: 1; font-size: 30rpx; color: #303133; }
}
.field:last-of-type { border-bottom: 1rpx solid #f0f1f5; }
.sms-row .sms-input { flex: 1; }
.sms-btn {
  margin-left: 12rpx;
  height: 64rpx;
  line-height: 64rpx;
  padding: 0 20rpx;
  background: #FFF4ED;
  color: #FF6B35;
  font-size: 24rpx;
  border-radius: 32rpx;
  border: none;
  flex-shrink: 0;
  &[disabled] { background: #f5f5f5; color: #999; }
  &::after { border: none; }
}

.tips {
  margin-top: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  font-size: 22rpx;
  color: #909399;
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
