<template>
  <view class="login">
    <view class="brand">
      <view class="logo">摊</view>
      <view class="name">摊小二</view>
      <view class="slogan">一个人的摊，也能做出一家店</view>
    </view>

    <view class="form card">
      <view class="field">
        <text class="label">手机号</text>
        <input
          class="input"
          type="number"
          maxlength="11"
          placeholder="请输入手机号"
          v-model="mobile"
        />
      </view>
      <view class="field">
        <text class="label">验证码</text>
        <view class="code-row">
          <input
            class="input"
            type="number"
            maxlength="4"
            placeholder="4 位验证码"
            v-model="code"
          />
          <button
            class="code-btn"
            :disabled="countdown > 0 || !canSend"
            @click="onSend"
          >
            {{ countdown > 0 ? countdown + 's' : '获取验证码' }}
          </button>
        </view>
      </view>

      <button class="submit" :disabled="!canLogin" @click="onLogin">
        登录 / 注册
      </button>

      <view class="hint">未注册手机号将自动创建商户账号</view>
    </view>

    <view class="footer">
      <text>原型版本 · 任意手机号 + 任意 4 位验证码即可登录</text>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue';
import { sendSmsCode } from '../../api/auth.js';
import { useUserStore } from '../../store/user.js';

const userStore = useUserStore();
const mobile = ref('');
const code = ref('');
const countdown = ref(0);

const canSend = computed(() => /^1\d{10}$/.test(mobile.value));
const canLogin = computed(() => canSend.value && code.value.length === 4);

async function onSend() {
  if (!canSend.value) {
    uni.showToast({ title: '请输入正确手机号', icon: 'none' });
    return;
  }
  await sendSmsCode(mobile.value);
  uni.showToast({ title: '验证码已发送（任意 4 位即可）', icon: 'none' });
  countdown.value = 60;
  const timer = setInterval(() => {
    countdown.value -= 1;
    if (countdown.value <= 0) clearInterval(timer);
  }, 1000);
}

async function onLogin() {
  if (!canLogin.value) return;
  uni.showLoading({ title: '登录中' });
  try {
    await userStore.loginBySms({ mobile: mobile.value, code: code.value });
    uni.hideLoading();
    uni.reLaunch({ url: '/pages/index/index' });
  } catch (e) {
    uni.hideLoading();
  }
}
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
}

.field {
  margin-bottom: 28rpx;

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

.code-row {
  display: flex;
  gap: 16rpx;

  .input {
    flex: 1;
  }

  .code-btn {
    flex-shrink: 0;
    height: 88rpx;
    line-height: 88rpx;
    padding: 0 24rpx;
    background: transparent;
    color: $brand-primary;
    border: 2rpx solid $brand-primary;
    border-radius: $radius-md;
    font-size: 26rpx;

    &[disabled] {
      color: $text-placeholder;
      border-color: $text-placeholder;
    }

    &::after {
      border: none;
    }
  }
}

.submit {
  width: 100%;
  height: 96rpx;
  line-height: 96rpx;
  margin-top: 24rpx;
  background: $brand-primary;
  color: #fff;
  border-radius: $radius-md;
  font-size: 32rpx;
  font-weight: 600;

  &[disabled] {
    background: $text-placeholder;
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

.footer {
  margin-top: 64rpx;
  text-align: center;
  font-size: 24rpx;
  color: $text-placeholder;
}
</style>
