<template>
  <view class="page">
    <view class="bg-deco"></view>

    <!-- ━━━━━━━━━━ 顶部紧凑品牌行 ━━━━━━━━━━ -->
    <view class="va-hero">
      <view class="va-logo">🏆</view>
      <view class="va-brand">客小二</view>
      <view class="va-tag">商户让利 · 推 N 反 1 · 1:1 提现</view>
      <view class="va-stats">
        <view class="va-pill"><text class="em">💎</text>今日商户让利 <text class="hl">¥{{ stat.amount }}</text></view>
        <view class="va-pill mint"><text class="em">👥</text><text class="hl">{{ stat.count }}</text> 人到账</view>
      </view>
    </view>

    <!-- ━━━━━━━━━━ 中间居中表单（flex:1） ━━━━━━━━━━ -->
    <view class="va-form-wrap">
      <view class="va-form">
        <view class="va-form-t">手机号登录 / 注册</view>
        <view class="va-form-s">登录享首单立减 + 推广积分赠送</view>
        <view class="va-field">
          <text class="l">+86</text>
          <input v-model="mobile" type="number" maxlength="11" placeholder="请输入手机号" />
        </view>
        <view class="va-field">
          <text class="l">🔒</text>
          <input v-model="code" type="number" maxlength="6" placeholder="6 位短信验证码" />
          <view class="va-send" :class="{ disabled: cd > 0 }" @click="onSend">{{ cd > 0 ? `${cd}s` : '获取' }}</view>
        </view>
        <view class="va-submit" :class="{ loading: submitting }" @click="onLogin">
          {{ submitting ? '登录中…' : '登 录' }}
        </view>
        <view class="va-agree">
          登录即同意 <text class="link">《用户协议》</text> <text class="link">《隐私政策》</text>
        </view>
      </view>
    </view>

    <!-- ━━━━━━━━━━ 底部社会证明 ━━━━━━━━━━ -->
    <view class="va-bottom">
      <view v-if="tickerLine" class="va-ticker">
        <text class="em">🔥</text>
        <view class="roll">
          <view class="track">{{ tickerLine }}</view>
        </view>
      </view>
      <view class="va-sell">
        <view class="va-card warm"><view class="em">💰</view><view class="t">下单返积分</view></view>
        <view class="va-card gold"><view class="em">🎁</view><view class="t">让利池</view></view>
        <view class="va-card mint"><view class="em">🔥</view><view class="t">推N反1</view></view>
        <view class="va-card purple"><view class="em">💸</view><view class="t">1:1提现</view></view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { sendSmsCode, smsLogin } from '@/api/auth.js';
import { listWinnersTicker, getTodayStat } from '@/api/promo.js';
import { useUserStore } from '@/store/user.js';
import { fen2yuan } from '@/utils/format.js';

const user = useUserStore();
const mobile = ref('');
const code = ref('');
const cd = ref(0);
const submitting = ref(false);

const stat = ref({ amount: '0', count: 0 });
const tickerLine = ref('');

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
  submitting.value = true;
  try {
    const r = await smsLogin(mobile.value, code.value);
    user.setLogin({ ...r, phone: mobile.value });
    uni.showToast({ title: '登录成功', icon: 'success' });
    const redirect = (typeof localStorage !== 'undefined' && localStorage.getItem('redirect:after-login')) || '/pages/index/index';
    try { if (typeof localStorage !== 'undefined') localStorage.removeItem('redirect:after-login'); } catch {}
    setTimeout(() => uni.reLaunch({ url: redirect }), 600);
  } catch {} finally { submitting.value = false; }
}

onMounted(async () => {
  try {
    const s = await getTodayStat();
    if (s) stat.value = { amount: fen2yuan(s.promoAmountToday || 0, false), count: s.awardCountToday || 0 };
  } catch {}
  try {
    const list = await listWinnersTicker(1);
    if (list && list[0]) {
      const w = list[0];
      tickerLine.value = `${w.userMask || '****'} 在 ${w.shopName || '某店'} ${w.sourceLabel || '促销让利'} +¥${fen2yuan(w.amount, false)}`;
    }
  } catch {}
});
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.page {
  position: relative;
  min-height: 100vh;
  display: flex; flex-direction: column;
  padding: 24px 22px 20px;
  padding-bottom: calc(20px + env(safe-area-inset-bottom));
  background:
    radial-gradient(500px 350px at 0% 0%, rgba(212,146,10,.4), transparent 60%),
    linear-gradient(180deg, #18130E 0%, #2A1A0F 60%, #1F1208 100%);
  color: #fff;
  overflow: hidden;
}
.bg-deco {
  position: absolute; inset: 0;
  background-image: radial-gradient(rgba(255,255,255,.05) 1px, transparent 1px);
  background-size: 22px 22px;
  pointer-events: none;
}

/* ━━ 顶部品牌行 ━━ */
.va-hero { text-align: center; padding: 4px 0 0; position: relative; z-index: 1; }
.va-logo {
  display: inline-flex; align-items: center; justify-content: center;
  width: 56px; height: 56px; border-radius: 18px;
  background: linear-gradient(135deg, $o, $o-d);
  font-size: 28px; line-height: 1;
  box-shadow: $sh-warm, inset 0 1px 0 rgba(255,255,255,.3);
}
.va-brand {
  margin-top: 10px; font-size: 26px; font-weight: 900; letter-spacing: -.5px;
  background: linear-gradient(135deg, #fff, $gold-l);
  -webkit-background-clip: text; background-clip: text; color: transparent;
  text-shadow: 0 4px 24px rgba(255,107,53,.3);
}
.va-tag { margin-top: 4px; font-size: 12px; color: rgba(255,255,255,.7); letter-spacing: .8px; }
.va-stats {
  margin-top: 12px;
  display: flex; gap: 8px; justify-content: center; flex-wrap: wrap;
}
.va-pill {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 5px 12px; border-radius: 99px;
  background: rgba(255,107,53,.22);
  border: 1px solid rgba(255,107,53,.4);
  backdrop-filter: blur(10px);
  font-size: 11.5px;
}
.va-pill.mint { background: rgba(16,185,129,.22); border-color: rgba(16,185,129,.4); }
.va-pill .em { font-size: 13px; }
.va-pill .hl { color: $gold-l; font-weight: 800; margin: 0 2px; }
.va-pill.mint .hl { color: $mint-l; }

/* ━━ 中间表单 flex:1 ━━ */
.va-form-wrap {
  flex: 1;
  display: flex; align-items: center; justify-content: center;
  padding: 18px 0;
  position: relative; z-index: 1;
}
.va-form {
  width: 100%;
  background: #fff; color: $t1;
  border-radius: 22px;
  padding: 26px 22px 22px;
  box-shadow: 0 20px 60px rgba(0,0,0,.4);
}
.va-form-t {
  font-size: 18px; font-weight: 900; text-align: center; color: $t1;
  letter-spacing: .5px;
}
.va-form-s {
  font-size: 11.5px; color: $o-d; text-align: center;
  margin-top: 6px;
}
.va-field {
  display: flex; align-items: center; gap: 10px;
  padding: 14px 14px;
  background: $bg-2;
  border-radius: 12px;
  margin-top: 12px;
  border: 1px solid transparent;
  transition: border-color .15s, background .15s;
}
.va-field:focus-within {
  border-color: $o-200;
  background: $o-50;
}
.va-field .l {
  flex-shrink: 0;
  font-size: 13px; color: $t2; font-weight: 700;
  min-width: 30px; text-align: center;
}
.va-field input {
  flex: 1; font-size: 15px; color: $t1;
  text-align: center;
  letter-spacing: 1px;
}
.va-send {
  flex-shrink: 0;
  padding: 7px 14px; border-radius: 99px;
  background: $o-50; color: $o;
  border: 1px solid $o-100;
  font-size: 12px; font-weight: 700;
}
.va-send.disabled { background: $bg-2; color: $t4; border-color: $line; }

.va-submit {
  margin-top: 18px;
  padding: 15px;
  background: linear-gradient(135deg, $o, $o-d);
  color: #fff; text-align: center;
  border-radius: 99px;
  font-weight: 900; font-size: 16px; letter-spacing: 4px;
  box-shadow: $sh-warm;
}
.va-submit.loading { opacity: .7; }
.va-agree { margin-top: 14px; text-align: center; font-size: 11px; color: $t4; }
.va-agree .link { color: $o; }

/* ━━ 底部社会证明 ━━ */
.va-bottom {
  position: relative;
  margin-top: auto;
  z-index: 1;
}
.va-ticker {
  padding: 8px 12px;
  background: rgba(255,255,255,.06);
  border: 1px solid rgba(255,255,255,.1);
  border-radius: 99px;
  display: flex; align-items: center; gap: 8px;
  overflow: hidden;
}
.va-ticker .em { font-size: 12px; flex-shrink: 0; }
.va-ticker .roll { flex: 1; overflow: hidden; height: 14px; position: relative; }
.va-ticker .track {
  position: absolute; white-space: nowrap;
  font-size: 11px; color: rgba(255,255,255,.75);
}
.va-sell {
  margin-top: 10px;
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px;
}
.va-card {
  padding: 10px 4px;
  border-radius: 12px;
  background: rgba(255,255,255,.08);
  border: 1px solid rgba(255,255,255,.12);
  backdrop-filter: blur(10px);
  text-align: center;
}
.va-card.warm   { background: linear-gradient(135deg, rgba(255,107,53,.28), rgba(255,107,53,.08)); border-color: rgba(255,107,53,.38); }
.va-card.gold   { background: linear-gradient(135deg, rgba(212,146,10,.28), rgba(212,146,10,.08)); border-color: rgba(212,146,10,.38); }
.va-card.mint   { background: linear-gradient(135deg, rgba(16,185,129,.28), rgba(16,185,129,.08)); border-color: rgba(16,185,129,.38); }
.va-card.purple { background: linear-gradient(135deg, rgba(99,102,241,.28), rgba(99,102,241,.08)); border-color: rgba(99,102,241,.38); }
.va-card .em { font-size: 18px; line-height: 1; }
.va-card .t { font-size: 11px; font-weight: 800; margin-top: 4px; line-height: 1.2; }
</style>
