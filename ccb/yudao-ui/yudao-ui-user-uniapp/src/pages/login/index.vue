<template>
  <view class="page">
    <view class="bg-deco"></view>
    <view class="bg-glow"></view>

    <!-- ━━━━━━━━━━ 顶部品牌 ━━━━━━━━━━ -->
    <view class="hero">
      <view class="logo">客</view>
      <view class="brand">客小二</view>
      <view class="tagline">发现身边好店 · 会员专享优惠</view>
    </view>

    <!-- ━━━━━━━━━━ 中间登录表单（flex:1 居中） ━━━━━━━━━━ -->
    <view class="form-wrap">
      <!-- #ifdef MP-WEIXIN -->
      <!-- 微信小程序：一键授权手机号登录（企业/个体主体方可用 getPhoneNumber） -->
      <view class="form">
        <view class="form-t">欢迎使用客小二</view>
        <view class="form-s">授权微信手机号，一键登录</view>
        <button
          class="wx-btn"
          open-type="getPhoneNumber"
          :loading="submitting"
          :disabled="submitting"
          @getphonenumber="onWxLogin"
        >{{ submitting ? '登录中…' : '微信手机号一键登录' }}</button>
        <view class="agree">
          登录即代表同意 <text class="link">《用户协议》</text> 与 <text class="link">《隐私政策》</text>
        </view>
      </view>
      <!-- #endif -->

      <!-- #ifndef MP-WEIXIN -->
      <!-- H5 / 其它端：手机号 + 短信验证码登录（保持原样） -->
      <view class="form">
        <view class="form-t">手机号登录 / 注册</view>
        <view class="form-s">登录即享会员优惠与积分好礼</view>

        <view class="field">
          <text class="cc">+86</text>
          <input v-model="mobile" type="number" maxlength="11" placeholder="请输入手机号" />
        </view>
        <view class="field">
          <input v-model="code" type="number" maxlength="6" placeholder="请输入短信验证码" />
          <view class="send" :class="{ disabled: cd > 0 }" @click="onSend">
            {{ cd > 0 ? `${cd}s 后重发` : '获取验证码' }}
          </view>
        </view>

        <view class="submit" :class="{ loading: submitting }" @click="onLogin">
          {{ submitting ? '登录中…' : '登 录' }}
        </view>

        <view class="agree">
          登录即代表同意 <text class="link">《用户协议》</text> 与 <text class="link">《隐私政策》</text>
        </view>
      </view>
      <!-- #endif -->
    </view>

    <!-- ━━━━━━━━━━ 底部权益（合规中性） ━━━━━━━━━━ -->
    <view class="benefits">
      <view class="b"><view class="b-ic warm">🛍️</view><text>好店精选</text></view>
      <view class="b"><view class="b-ic gold">🎟️</view><text>会员优惠</text></view>
      <view class="b"><view class="b-ic mint">⭐</view><text>积分好礼</text></view>
      <view class="b"><view class="b-ic purple">🛡️</view><text>安全保障</text></view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { sendSmsCode, smsLogin, weixinMiniAppLogin } from '@/api/auth.js';
import { flushPendingReferrer } from '@/utils/referral.js';
import { useUserStore } from '@/store/user.js';

const user = useUserStore();
const mobile = ref('');
const code = ref('');
const cd = ref(0);
const submitting = ref(false);

// #ifndef MP-WEIXIN
async function onSend() {
  if (cd.value > 0) return;
  if (!/^1[3-9]\d{9}$/.test(mobile.value)) return uni.showToast({ title: '请输入正确的手机号', icon: 'none' });
  try {
    await sendSmsCode(mobile.value, 21);
    uni.showToast({ title: '验证码已发送', icon: 'success' });
    cd.value = 60;
    const t = setInterval(() => { cd.value--; if (cd.value <= 0) clearInterval(t); }, 1000);
  } catch {}
}
async function onLogin() {
  if (!/^1[3-9]\d{9}$/.test(mobile.value)) return uni.showToast({ title: '请输入正确的手机号', icon: 'none' });
  if (!/^\d{4,6}$/.test(code.value)) return uni.showToast({ title: '请输入短信验证码', icon: 'none' });
  submitting.value = true;
  try {
    const r = await smsLogin(mobile.value, code.value);
    user.setLogin({ ...r, phone: mobile.value });
    uni.showToast({ title: '登录成功', icon: 'success' });
    // V044：落地时存的 inviter+tenantId pending bind，登录后立即 flush（后端仅首次绑定生效）
    try { await flushPendingReferrer(r.userId || user.userId); } catch {}
    const redirect = (typeof localStorage !== 'undefined' && localStorage.getItem('redirect:after-login')) || '/pages/index/index';
    try { if (typeof localStorage !== 'undefined') localStorage.removeItem('redirect:after-login'); } catch {}
    setTimeout(() => uni.reLaunch({ url: redirect }), 600);
  } catch {} finally { submitting.value = false; }
}
// #endif

// #ifdef MP-WEIXIN
// 微信小程序：点「手机号一键登录」按钮触发 @getphonenumber → 拿 phoneCode；
// 再 wx.login 拿 loginCode；一起调后端 weixin-mini-app-login 完成登录 + 绑微信手机号。
async function onWxLogin(e) {
  const d = e && e.detail ? e.detail : {};
  // 新版返 d.code；用户拒绝/取消时 errMsg 含 deny / cancel / fail
  if (!d.code) {
    if (/deny|cancel/i.test(d.errMsg || '')) {
      uni.showToast({ title: '已取消授权', icon: 'none' });
    } else {
      // 弹出微信返回的真实 errMsg，便于定位（如未配隐私协议 / 模拟器不支持等）
      uni.showModal({ title: '获取手机号失败', content: d.errMsg || '未知错误（请用真机预览）', showCancel: false });
    }
    return;
  }
  if (submitting.value) return;
  submitting.value = true;
  uni.showLoading({ title: '登录中…', mask: true });
  try {
    const loginCode = await new Promise((resolve, reject) => {
      uni.login({
        provider: 'weixin',
        success: (r) => (r && r.code ? resolve(r.code) : reject(new Error('微信登录失败'))),
        fail: (err) => reject(new Error(err?.errMsg || '微信登录失败')),
      });
    });
    const state = `kxe_${Date.now()}_${Math.floor(Math.random() * 1e6)}`;
    const r = await weixinMiniAppLogin(loginCode, d.code, state);
    user.setLogin({ ...r, phone: r.mobile || r.phone || '' });
    uni.hideLoading();
    uni.showToast({ title: '登录成功', icon: 'success' });
    try { await flushPendingReferrer(r.userId || user.userId); } catch {}
    setTimeout(() => uni.reLaunch({ url: '/pages/index/index' }), 600);
  } catch (err) {
    uni.hideLoading();
    uni.showToast({ title: err?.message || '登录失败，请重试', icon: 'none' });
  } finally {
    submitting.value = false;
  }
}
// #endif
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.page {
  position: relative;
  min-height: 100vh;
  display: flex; flex-direction: column;
  padding: 36px 26px 22px;
  padding-bottom: calc(24px + env(safe-area-inset-bottom));
  background:
    radial-gradient(620px 440px at 50% -10%, rgba(255,107,53,.30), transparent 62%),
    radial-gradient(440px 340px at 100% 102%, rgba(212,146,10,.16), transparent 60%),
    linear-gradient(180deg, #1A140E 0%, #241813 55%, #1B120B 100%);
  color: #fff;
  overflow: hidden;
}
.bg-deco {
  position: absolute; inset: 0;
  background-image: radial-gradient(rgba(255,255,255,.045) 1px, transparent 1px);
  background-size: 26px 26px;
  pointer-events: none;
}
.bg-glow {
  position: absolute; top: -120px; left: 50%; transform: translateX(-50%);
  width: 320px; height: 320px; border-radius: 50%;
  background: radial-gradient(circle, rgba(255,107,53,.35), transparent 70%);
  filter: blur(20px);
  pointer-events: none;
}

/* ━━ 顶部品牌 ━━ */
.hero { text-align: center; padding-top: 18px; position: relative; z-index: 1; }
.logo {
  width: 66px; height: 66px; margin: 0 auto;
  display: flex; align-items: center; justify-content: center;
  border-radius: 21px;
  background: linear-gradient(135deg, $o-l, $o-d);
  font-size: 32px; font-weight: 900; line-height: 1;
  box-shadow: 0 14px 32px rgba(255,107,53,.42), inset 0 1px 0 rgba(255,255,255,.4);
}
.brand {
  margin-top: 18px; font-size: 29px; font-weight: 800; letter-spacing: 4px;
  background: linear-gradient(135deg, #ffffff, $gold-l);
  -webkit-background-clip: text; background-clip: text; color: transparent;
}
.tagline {
  margin-top: 10px; font-size: 13px; color: rgba(255,255,255,.6);
  letter-spacing: 1.5px;
}

/* ━━ 中间表单 flex:1 ━━ */
.form-wrap {
  flex: 1;
  display: flex; align-items: center; justify-content: center;
  padding: 28px 0;
  position: relative; z-index: 1;
}
.form {
  width: 100%;
  background: #fff; color: $t1;
  border-radius: 24px;
  padding: 30px 24px 24px;
  box-shadow: 0 24px 70px rgba(0,0,0,.45);
}
.form-t {
  font-size: 20px; font-weight: 800; text-align: center; color: $t1;
  letter-spacing: .5px;
}
.form-s {
  font-size: 13px; color: $t3; text-align: center; margin-top: 8px;
}
.field {
  display: flex; align-items: center; gap: 10px;
  height: 52px; padding: 0 16px;
  background: $bg-2;
  border-radius: 14px;
  margin-top: 14px;
  border: 1.5px solid transparent;
  transition: border-color .15s, background .15s;
}
.field:focus-within { border-color: $o-200; background: $o-50; }
.field .cc {
  flex-shrink: 0; font-size: 15px; color: $t2; font-weight: 700;
  padding-right: 10px; border-right: 1px solid $line-d;
}
.field input { flex: 1; font-size: 16px; color: $t1; }
.send {
  flex-shrink: 0;
  padding: 8px 15px; border-radius: 999px;
  background: $o-50; color: $o;
  border: 1px solid $o-100;
  font-size: 13px; font-weight: 700;
}
.send.disabled { background: $bg-2; color: $t4; border-color: $line; }

.submit {
  margin-top: 22px; height: 52px; line-height: 52px;
  background: linear-gradient(135deg, $o, $o-d);
  color: #fff; text-align: center;
  border-radius: 14px;
  font-weight: 800; font-size: 17px; letter-spacing: 3px;
  box-shadow: 0 12px 28px rgba(255,107,53,.36);
}
.submit.loading { opacity: .65; }

/* 微信小程序「手机号一键登录」按钮（重置 uni button 默认样式） */
.wx-btn {
  margin-top: 10px;
  height: 52px; line-height: 52px;
  background: linear-gradient(135deg, $o, $o-d);
  color: #fff; text-align: center;
  border-radius: 14px;
  font-weight: 800; font-size: 17px; letter-spacing: 2px;
  box-shadow: 0 12px 28px rgba(255,107,53,.36);
  border: none;
}
.wx-btn::after { border: none; }
.wx-btn[disabled] { opacity: .65; background: linear-gradient(135deg, $o, $o-d); color: #fff; }
.agree { margin-top: 18px; text-align: center; font-size: 12px; color: $t4; line-height: 1.6; }
.agree .link { color: $o; }

/* ━━ 底部权益 ━━ */
.benefits {
  margin-top: auto; padding-top: 26px;
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px;
  position: relative; z-index: 1;
}
.b { text-align: center; }
.b-ic {
  width: 50px; height: 50px; margin: 0 auto 8px;
  display: flex; align-items: center; justify-content: center;
  border-radius: 16px; font-size: 23px; line-height: 1;
  background: rgba(255,255,255,.07);
  border: 1px solid rgba(255,255,255,.13);
  backdrop-filter: blur(10px);
}
.b-ic.warm   { background: linear-gradient(135deg, rgba(255,107,53,.26), rgba(255,107,53,.06)); border-color: rgba(255,107,53,.34); }
.b-ic.gold   { background: linear-gradient(135deg, rgba(212,146,10,.26), rgba(212,146,10,.06)); border-color: rgba(212,146,10,.34); }
.b-ic.mint   { background: linear-gradient(135deg, rgba(16,185,129,.26), rgba(16,185,129,.06)); border-color: rgba(16,185,129,.34); }
.b-ic.purple { background: linear-gradient(135deg, rgba(99,102,241,.26), rgba(99,102,241,.06)); border-color: rgba(99,102,241,.34); }
.b text { font-size: 12px; color: rgba(255,255,255,.76); font-weight: 600; letter-spacing: .5px; }
</style>
