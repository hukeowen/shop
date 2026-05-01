<template>
  <view class="page">
    <!-- 顶部品牌区 -->
    <view class="hero">
      <view class="logo">摊</view>
      <view class="brand">
        <view class="brand-name">摊小二</view>
        <view class="brand-slogan">让每个小摊，都有大生意</view>
      </view>
    </view>

    <!-- 优势点 -->
    <view class="benefits">
      <view class="benefit">
        <view class="benefit-icon">🚀</view>
        <view class="benefit-text">1 分钟</view>
        <view class="benefit-sub">极速开通</view>
      </view>
      <view class="benefit">
        <view class="benefit-icon">🎁</view>
        <view class="benefit-text">免费用</view>
        <view class="benefit-sub">30 天试用</view>
      </view>
      <view class="benefit">
        <view class="benefit-icon">🤖</view>
        <view class="benefit-text">AI 成片</view>
        <view class="benefit-sub">营销神器</view>
      </view>
    </view>

    <!-- 申请表单卡片 -->
    <view class="card">
      <view class="card-title">立即入驻</view>
      <view class="card-sub">填写下方信息，开通您的店铺</view>

      <view class="field">
        <text class="field-icon">🏪</text>
        <input
          class="field-input"
          v-model="form.shopName"
          placeholder="店铺名称（如：老王烧烤摊）"
          maxlength="32"
        />
      </view>

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

      <view class="field sms-field">
        <text class="field-icon">🔐</text>
        <input
          class="field-input"
          v-model="form.smsCode"
          type="number"
          placeholder="短信验证码（6 位）"
          maxlength="6"
        />
        <view class="sms-btn" :class="{ disabled: smsCooldown > 0 }" @click="sendSms">
          {{ smsCooldown > 0 ? smsCooldown + 's' : '获取验证码' }}
        </view>
      </view>


      <button
        class="submit-btn"
        :class="{ active: canSubmit, loading: submitting }"
        :disabled="submitting || !canSubmit"
        @click="submit"
      >
        <text v-if="!submitting">立即申请入驻</text>
        <text v-else>申请中…</text>
      </button>

      <view class="agreement">
        提交即同意 <text class="agreement-link">《摊小二商户服务协议》</text>
      </view>
    </view>

    <!-- 底部链接 -->
    <view class="footer">
      <view class="footer-link">已有商户账号？<text class="link" @click="goLogin">立即登录</text></view>
      <view class="footer-link">我是顾客？<text class="link" @click="goUserLogin">用户登录</text></view>
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

async function sendSms() {
  if (smsCooldown.value > 0) return;
  if (!/^1[3-9]\d{9}$/.test(form.mobile)) {
    uni.showToast({ title: '请先填写正确手机号', icon: 'none' });
    return;
  }
  try {
    await request({
      url: '/app-api/app/auth/send-sms-code',
      method: 'POST',
      data: { mobile: form.mobile },
    });
    // 当前固定 888888，提示用户直接填（接入真 SMS 后改成"已发送"）
    uni.showToast({ title: '验证码：888888', icon: 'none', duration: 3000 });
    smsCooldown.value = 60;
    cooldownTimer = setInterval(() => {
      smsCooldown.value--;
      if (smsCooldown.value <= 0) clearInterval(cooldownTimer);
    }, 1000);
  } catch (e) {
    uni.showToast({ title: e?.message || '发送失败，请稍后再试', icon: 'none' });
  }
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
    const token = resp?.token?.accessToken || resp?.accessToken;
    if (token) {
      const userStore = JSON.stringify({
        token,
        userId: resp.userId,
        phone: resp.phone,
        role: 'merchant',
      });
      try {
        if (typeof localStorage !== 'undefined') localStorage.setItem('user-store-v1', userStore);
      } catch {}
      try {
        uni.setStorageSync('token', token);
        if (resp.merchantId) uni.setStorageSync('merchantId', resp.merchantId);
      } catch {}
    }
    uni.showModal({
      title: '🎉 入驻成功！',
      content: `店铺「${form.shopName.trim()}」已开通，现在为您跳转到商户后台`,
      showCancel: false,
      confirmText: '进入后台',
      success: () => uni.reLaunch({ url: '/pages/me/index' }),
    });
  } catch {
    // toast 由 request.js 处理
  } finally {
    submitting.value = false;
  }
}

function goLogin() {
  uni.navigateTo({ url: '/pages/merchant-login/index' });
}
function goUserLogin() {
  uni.navigateTo({ url: '/pages/login/index' });
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: linear-gradient(180deg, #FF6B35 0%, #FF9A4A 30%, #FFE5D6 65%, #fff 100%);
  padding: 60rpx 32rpx 60rpx;
  position: relative;
  overflow: hidden;
}

/* 装饰背景圈 */
.page::before, .page::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
  pointer-events: none;
}
.page::before { width: 400rpx; height: 400rpx; top: -100rpx; right: -100rpx; }
.page::after  { width: 300rpx; height: 300rpx; top: 200rpx; left: -120rpx; }

/* ── 顶部品牌区 ────────────────────────────── */
.hero {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-bottom: 40rpx;
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
  line-height: 1.2;
}
.brand-slogan {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.9);
  margin-top: 4rpx;
}

/* ── 优势点 ────────────────────────────────── */
.benefits {
  display: flex;
  gap: 16rpx;
  margin-bottom: 36rpx;
  position: relative;
  z-index: 1;
}
.benefit {
  flex: 1;
  background: rgba(255, 255, 255, 0.18);
  backdrop-filter: blur(20rpx);
  border-radius: 20rpx;
  padding: 24rpx 12rpx;
  text-align: center;
  border: 1rpx solid rgba(255, 255, 255, 0.25);
}
.benefit-icon { font-size: 40rpx; }
.benefit-text {
  font-size: 28rpx;
  font-weight: 700;
  color: #fff;
  margin-top: 8rpx;
}
.benefit-sub {
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.85);
  margin-top: 2rpx;
}

/* ── 表单卡片 ─────────────────────────────── */
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
  transition: border 0.2s;

  &:focus-within {
    border-color: #FF6B35;
    background: #fff;
  }
}
.field-icon {
  font-size: 32rpx;
  margin-right: 12rpx;
  flex-shrink: 0;
}
.field-input {
  flex: 1;
  font-size: 30rpx;
  color: #1a1a1a;
  background: transparent;
  border: none;
  outline: none;
}
.sms-field { padding-right: 8rpx; }
.sms-btn {
  margin-left: 12rpx;
  padding: 14rpx 24rpx;
  font-size: 24rpx;
  background: linear-gradient(135deg, #FF6B35, #FF9A4A);
  color: #fff;
  border-radius: 32rpx;
  font-weight: 600;
  flex-shrink: 0;
  white-space: nowrap;

  &.disabled {
    background: #E5E7EB;
    color: #909399;
  }
}

.demo-hint {
  display: flex;
  align-items: center;
  gap: 8rpx;
  font-size: 22rpx;
  color: #FF6B35;
  background: #FFF7F2;
  border-radius: 12rpx;
  padding: 16rpx 20rpx;
  margin: 8rpx 0 32rpx;
}
.demo-tag {
  background: #FF6B35;
  color: #fff;
  font-size: 18rpx;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  font-weight: 700;
}
.demo-code {
  font-weight: 700;
  font-size: 28rpx;
  color: #FF6B35;
}

.submit-btn {
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
  &.loading {
    opacity: 0.8;
  }
  &::after { border: none; }
}

.agreement {
  margin-top: 20rpx;
  text-align: center;
  font-size: 22rpx;
  color: #909399;
}
.agreement-link {
  color: #FF6B35;
}

/* ── 底部链接 ─────────────────────────────── */
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
  color: #5A6577;            /* 深灰，白底可读 */
  margin: 18rpx 0;
  line-height: 1.6;

  .link {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 56rpx;        /* 触控区 ≥ 44pt */
    padding: 6rpx 20rpx;
    margin-left: 8rpx;
    color: #FF6B35;           /* 品牌橙，可读 */
    font-weight: 700;
    border-radius: 28rpx;
    border: 1rpx solid rgba(255, 107, 53, 0.32);
    background: rgba(255, 107, 53, 0.06);
  }
  .link:active {
    background: rgba(255, 107, 53, 0.16);
  }
}
</style>
