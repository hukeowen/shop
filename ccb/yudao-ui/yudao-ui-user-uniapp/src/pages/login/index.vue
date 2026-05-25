<template>
  <view class="page">
    <view class="bg-deco"></view>
    <view class="bg-glow"></view>

    <!-- ━━━━━━━━━━ Brand hero ━━━━━━━━━━ -->
    <view class="hero">
      <view class="logo-row">
        <view class="logo-em">🏆</view>
        <view class="brand">客小二</view>
      </view>
      <view class="tagline">商户派奖 · 推 N 反 1 · 1:1 现金提现</view>

      <!-- 全网今日派奖大数字 -->
      <view v-if="stat" class="stat-row">
        <view class="stat-pill">
          <text class="em">💎</text>
          今日全网派奖 <text class="hl">¥{{ stat.amount }}</text>
        </view>
        <view class="stat-pill mint">
          <text class="em">👥</text>
          <text class="hl">{{ stat.count }}</text> 人到账
        </view>
      </view>
    </view>

    <!-- ━━━━━━━━━━ 实时中奖 ticker ━━━━━━━━━━ -->
    <view v-if="winners.length" class="ticker">
      <text class="em">🔥</text>
      <view class="roll">
        <view class="roll-track">
          <text v-for="(w, i) in winners" :key="i">
            <text class="phone">{{ w.userMask }}</text> 在 <text class="shop">{{ w.shopName }}</text> {{ w.sourceLabel }}
            <text class="amt">+¥{{ w.amount }}</text> ·
          </text>
        </view>
      </view>
    </view>

    <!-- ━━━━━━━━━━ 4 卖点卡片 ━━━━━━━━━━ -->
    <view class="sell-grid">
      <view class="sell-card warm">
        <view class="sc-em">💰</view>
        <view class="sc-t">买东西也赚钱</view>
        <view class="sc-d">下单即返推广积分</view>
      </view>
      <view class="sell-card gold">
        <view class="sc-em">🎁</view>
        <view class="sc-t">商户派奖池</view>
        <view class="sc-d">每天 ¥100+ 现金奖</view>
      </view>
      <view class="sell-card mint">
        <view class="sc-em">🔥</view>
        <view class="sc-t">推 N 反 1</view>
        <view class="sc-d">买 N 件免单 1 件</view>
      </view>
      <view class="sell-card purple">
        <view class="sc-em">💸</view>
        <view class="sc-t">1:1 提现</view>
        <view class="sc-d">微信秒到 · 满 1 元</view>
      </view>
    </view>

    <!-- ━━━━━━━━━━ 登录表单 ━━━━━━━━━━ -->
    <view class="form">
      <view class="form-title">手机号登录 / 注册</view>
      <view class="form-sub">登录即享首单立减 + 推广积分赠送</view>

      <view class="field">
        <text class="f-l">+86</text>
        <input v-model="mobile" type="number" maxlength="11" placeholder="手机号" />
      </view>
      <view class="field">
        <text class="f-l">🔒</text>
        <input v-model="code" type="number" maxlength="6" placeholder="6 位短信验证码" />
        <view class="send" :class="{ disabled: cd > 0 }" @click="onSend">{{ cd > 0 ? `${cd}s 后重发` : '获取验证码' }}</view>
      </view>

      <view class="submit" :class="{ loading: submitting }" @click="onLogin">
        {{ submitting ? '登录中…' : '登 录' }}
      </view>

      <view class="agree">
        登录即同意 <text class="link">《用户协议》</text> <text class="link">《隐私政策》</text>
      </view>

      <view class="trust">
        <text class="trust-item">✓ 商户实名认证</text>
        <text class="trust-item">✓ 资金流水可查</text>
        <text class="trust-item">✓ 1:1 现金提现</text>
      </view>
    </view>

    <view class="bottom-pad"></view>
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

const stat = ref(null);
const winners = ref([]);

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
    const list = await listWinnersTicker(10);
    winners.value = (list || []).map((w) => ({
      userMask: w.userMask || '****',
      shopName: w.shopName || '某店铺',
      sourceLabel: w.sourceLabel || '派奖',
      amount: fen2yuan(w.amount, false),
    }));
  } catch {}
});
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.page {
  position: relative;
  min-height: 100vh;
  padding: 40px 14px 30px;
  background: linear-gradient(180deg, #18130E 0%, #2A1A0F 60%, #1F1208 100%);
  overflow: hidden;
}
.bg-deco {
  position: absolute; inset: 0;
  background-image: radial-gradient(rgba(255,255,255,.04) 1px, transparent 1px);
  background-size: 22px 22px;
  pointer-events: none;
}
.bg-glow {
  position: absolute;
  top: -100px; left: -50px; width: 400px; height: 400px;
  background: radial-gradient(circle, rgba(255,107,53,.4), transparent 60%);
  pointer-events: none;
}

/* ━━━━━━━━━━ Hero ━━━━━━━━━━ */
.hero { text-align: center; color: #fff; padding: 20px 0 18px; position: relative; }
.logo-row { display: flex; align-items: center; justify-content: center; gap: 8px; }
.logo-em {
  width: 56px; height: 56px; border-radius: 16px;
  background: linear-gradient(135deg, $o, $o-d);
  display: flex; align-items: center; justify-content: center;
  font-size: 30px;
  box-shadow: $sh-warm, inset 0 1px 0 rgba(255,255,255,.3);
}
.brand {
  font-size: 32px; font-weight: 900; letter-spacing: -1px;
  background: linear-gradient(135deg, #fff, $gold-l);
  -webkit-background-clip: text; background-clip: text; color: transparent;
  text-shadow: 0 4px 24px rgba(255,107,53,.3);
}
.tagline {
  margin-top: 10px;
  font-size: 13px; color: rgba(255,255,255,.7);
  letter-spacing: 1px;
}
.stat-row {
  margin-top: 16px;
  display: flex; gap: 8px; justify-content: center; flex-wrap: wrap;
}
.stat-pill {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 6px 14px; border-radius: 99px;
  background: rgba(255,107,53,.2);
  border: 1px solid rgba(255,107,53,.4);
  backdrop-filter: blur(10px);
  color: rgba(255,255,255,.9);
  font-size: 12px;
}
.stat-pill.mint {
  background: rgba(16,185,129,.2);
  border-color: rgba(16,185,129,.4);
}
.stat-pill .em { font-size: 14px; }
.stat-pill .hl { color: $gold-l; font-weight: 800; margin: 0 2px; }
.stat-pill.mint .hl { color: $mint-l; }

/* ━━━━━━━━━━ Ticker ━━━━━━━━━━ */
.ticker {
  margin: 14px 0;
  padding: 10px 14px;
  background: rgba(255,255,255,.06);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255,255,255,.1);
  border-radius: 12px;
  display: flex; align-items: center; gap: 10px;
  overflow: hidden;
  position: relative;
}
.ticker .em { font-size: 14px; }
.ticker .roll { flex: 1; overflow: hidden; height: 16px; position: relative; }
.roll-track {
  position: absolute; white-space: nowrap;
  font-size: 11.5px; color: rgba(255,255,255,.75);
  animation: rollx 28s linear infinite;
}
.roll-track .phone { color: $gold-l; font-weight: 700; }
.roll-track .shop  { color: $o-l; font-weight: 700; }
.roll-track .amt   { color: $mint-l; font-weight: 800; }
@keyframes rollx { 0% { transform: translateX(100%); } 100% { transform: translateX(-100%); } }

/* ━━━━━━━━━━ 卖点 grid ━━━━━━━━━━ */
.sell-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin: 8px 0 20px;
  position: relative;
}
.sell-card {
  padding: 14px;
  border-radius: $r-lg;
  background: rgba(255,255,255,.08);
  border: 1px solid rgba(255,255,255,.12);
  backdrop-filter: blur(10px);
  color: #fff;
  position: relative; overflow: hidden;
}
.sell-card.warm   { background: linear-gradient(135deg, rgba(255,107,53,.25), rgba(255,107,53,.08)); border-color: rgba(255,107,53,.35); }
.sell-card.gold   { background: linear-gradient(135deg, rgba(212,146,10,.25), rgba(212,146,10,.08)); border-color: rgba(212,146,10,.35); }
.sell-card.mint   { background: linear-gradient(135deg, rgba(16,185,129,.25), rgba(16,185,129,.08)); border-color: rgba(16,185,129,.35); }
.sell-card.purple { background: linear-gradient(135deg, rgba(99,102,241,.25), rgba(99,102,241,.08)); border-color: rgba(99,102,241,.35); }
.sc-em { font-size: 26px; line-height: 1; }
.sc-t { font-size: 14px; font-weight: 800; margin-top: 8px; }
.sc-d { font-size: 11px; color: rgba(255,255,255,.7); margin-top: 3px; }

/* ━━━━━━━━━━ Form ━━━━━━━━━━ */
.form {
  position: relative;
  background: #fff;
  border-radius: $r-xl;
  padding: 22px 20px 18px;
  box-shadow: 0 20px 60px rgba(0,0,0,.4);
  z-index: 2;
}
.form-title { font-size: 17px; font-weight: 900; color: $t1; text-align: center; }
.form-sub { font-size: 11px; color: $o-d; text-align: center; margin-top: 4px; }
.field {
  display: flex; align-items: center; gap: 10px;
  padding: 14px 0;
  border-bottom: 1px solid $line;
  margin-top: 6px;
}
.f-l { width: 36px; font-size: 13px; color: $t2; font-weight: 600; }
.field input { flex: 1; font-size: 15px; color: $t1; }
.send {
  padding: 7px 14px; border-radius: $r-pill;
  background: $o-50; color: $o; border: 1px solid $o-100;
  font-size: 12px; font-weight: 700;
  flex-shrink: 0;
}
.send.disabled { background: $bg-2; color: $t4; border-color: $line; }

.submit {
  margin-top: 22px;
  padding: 15px;
  background: linear-gradient(135deg, $o, $o-d);
  color: #fff; text-align: center;
  border-radius: $r-pill;
  font-weight: 900; font-size: 16px;
  letter-spacing: 2px;
  box-shadow: $sh-warm;
}
.submit.loading { opacity: .7; }

.agree { margin-top: 16px; text-align: center; font-size: 11px; color: $t4; }
.agree .link { color: $o; }

.trust {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px dashed $line;
  display: flex; justify-content: space-around;
}
.trust-item { font-size: 10.5px; color: $mint; font-weight: 700; }

.bottom-pad { height: 12px; }
</style>
