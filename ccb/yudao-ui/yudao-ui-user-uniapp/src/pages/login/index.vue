<template>
  <view class="page">
    <view class="hero">
      <view class="hero-em">🏆</view>
      <view class="hero-t">客小二</view>
      <view class="hero-d">商户派奖 · 推 N 反 1 · 1:1 提现</view>
    </view>

    <view class="card">
      <view class="card-title">手机号登录</view>

      <view class="field">
        <text class="f-l">手机号</text>
        <input v-model="mobile" type="number" maxlength="11" placeholder="请输入手机号" />
      </view>
      <view class="field">
        <text class="f-l">验证码</text>
        <input v-model="code" type="number" maxlength="6" placeholder="6 位短信验证码" />
        <view class="send" :class="{ disabled: cd > 0 }" @click="onSend">{{ cd > 0 ? `${cd}s` : '获取' }}</view>
      </view>

      <view class="submit" @click="onLogin">登录</view>

      <view class="agree">
        登录即同意 <text class="link">《用户协议》</text>《隐私政策》
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { sendSmsCode, smsLogin } from '@/api/auth.js';
import { useUserStore } from '@/store/user.js';

const user = useUserStore();
const mobile = ref('');
const code = ref('');
const cd = ref(0);

async function onSend() {
  if (cd.value > 0) return;
  if (!/^1[3-9]\d{9}$/.test(mobile.value)) return uni.showToast({ title: '手机号格式错', icon: 'none' });
  try {
    await sendSmsCode(mobile.value, 21);
    uni.showToast({ title: '验证码已发送', icon: 'success' });
    cd.value = 60;
    const t = setInterval(() => { cd.value--; if (cd.value <= 0) clearInterval(t); }, 1000);
  } catch {}
}

async function onLogin() {
  if (!/^1[3-9]\d{9}$/.test(mobile.value)) return uni.showToast({ title: '手机号格式错', icon: 'none' });
  if (!/^\d{4,6}$/.test(code.value)) return uni.showToast({ title: '验证码错', icon: 'none' });
  try {
    const r = await smsLogin(mobile.value, code.value);
    user.setLogin(r);
    uni.showToast({ title: '登录成功', icon: 'success' });
    const redirect = (typeof localStorage !== 'undefined' && localStorage.getItem('redirect:after-login')) || '/pages/index/index';
    try { if (typeof localStorage !== 'undefined') localStorage.removeItem('redirect:after-login'); } catch {}
    setTimeout(() => uni.reLaunch({ url: redirect }), 600);
  } catch {}
}
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background:
  radial-gradient(500px 250px at 50% 0%, rgba(255,107,53,.25), transparent 60%),
  linear-gradient(180deg, #18130E, #2A1A0F);
  padding: 60px 14px 30px;
}
.hero { text-align: center; color: #fff; padding: 30px 0 40px; }
.hero-em { font-size: 60px; }
.hero-t { font-size: 30px; font-weight: 900; margin-top: 6px; background: linear-gradient(135deg, #fff, $gold-l); -webkit-background-clip: text; background-clip: text; color: transparent; }
.hero-d { font-size: 12px; opacity: .6; margin-top: 6px; }
.card { background: #fff; border-radius: $r-xl; padding: 24px 20px; box-shadow: $sh-3; }
.card-title { font-size: 16px; font-weight: 800; color: $t1; margin-bottom: 20px; text-align: center; }
.field { display: flex; align-items: center; gap: 10px; padding: 14px 0; border-bottom: 1px solid $line; }
.f-l { width: 60px; font-size: 13px; color: $t2; font-weight: 600; }
.field input { flex: 1; font-size: 15px; color: $t1; }
.send { padding: 6px 14px; background: $o-50; color: $o; border-radius: $r-pill; font-size: 12px; font-weight: 700; }
.send.disabled { background: $bg-2; color: $t4; }
.submit { margin-top: 24px; padding: 14px; background: linear-gradient(135deg, $o, $o-d); color: #fff; text-align: center; border-radius: $r-pill; font-weight: 800; font-size: 15px; box-shadow: $sh-warm; }
.agree { margin-top: 16px; text-align: center; font-size: 11px; color: $t4; }
.link { color: $o; }
</style>
