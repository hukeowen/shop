<template>
  <view class="page">
    <view class="bg-deco"></view>
    <view class="bg-mark">🏆</view>

    <!-- ━━━━━━━━━━ 大数字 hero ━━━━━━━━━━ -->
    <view class="stat-hero">
      <view class="stat-tag">今日全网派奖</view>
      <view class="stat-num">¥{{ stat.amount }}<text class="small">.{{ stat.cents }}</text></view>
      <view class="stat-d"><text class="b">{{ stat.count }}</text> 人到账 · 实时滚动 · 1:1 现金提现</view>
    </view>

    <!-- ━━━━━━━━━━ Brand ━━━━━━━━━━ -->
    <view class="brand-row">
      <view class="brand-logo">🏆</view>
      <view class="brand">客小二</view>
    </view>
    <view class="brand-tag">买东西也能赚钱 · 推 N 反 1 玩法</view>

    <!-- ━━━━━━━━━━ Mini ticker ━━━━━━━━━━ -->
    <view v-if="tickerLine" class="ticker-mini">
      <text class="em">🔥</text>
      <view class="ticker-roll">
        <view class="ticker-track">{{ tickerLine }}</view>
      </view>
    </view>

    <!-- ━━━━━━━━━━ 4 紧凑卖点 ━━━━━━━━━━ -->
    <view class="sells">
      <view class="sell"><view class="em">💰</view><view class="t">下单返积分</view></view>
      <view class="sell"><view class="em">🎁</view><view class="t">派奖池</view></view>
      <view class="sell"><view class="em">🔥</view><view class="t">推 N 反 1</view></view>
      <view class="sell"><view class="em">💸</view><view class="t">1:1 提现</view></view>
    </view>

    <!-- ━━━━━━━━━━ 白卡 Form ━━━━━━━━━━ -->
    <view class="form">
      <view class="form-row">
        <text class="l">+86</text>
        <input v-model="mobile" type="number" maxlength="11" placeholder="手机号" />
      </view>
      <view class="form-row">
        <text class="l">🔒</text>
        <input v-model="code" type="number" maxlength="6" placeholder="验证码" />
        <view class="send" :class="{ disabled: cd > 0 }" @click="onSend">{{ cd > 0 ? `${cd}s` : '获取' }}</view>
      </view>
      <view class="submit" :class="{ loading: submitting }" @click="onLogin">
        {{ submitting ? '登录中…' : '登录 / 注册' }}
      </view>
      <view class="other">
        <view class="other-btn" @click="onThird('wechat')">💚</view>
        <view class="other-btn" @click="onThird('alipay')">🅰</view>
        <view class="other-btn" @click="onThird('mobile')">📱</view>
      </view>
      <view class="agree">未注册手机号验证后自动创建账号 <text class="link">《协议》</text></view>
    </view>

    <view class="bottom-pad"></view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { sendSmsCode, smsLogin } from '@/api/auth.js';
import { listWinnersTicker, getTodayStat } from '@/api/promo.js';
import { useUserStore } from '@/store/user.js';
import { fen2yuan } from '@/utils/format.js';

const user = useUserStore();
const mobile = ref('');
const code = ref('');
const cd = ref(0);
const submitting = ref(false);

const rawStat = ref({ promo: 0, count: 0 });
const stat = computed(() => {
  const fen = rawStat.value.promo || 0;
  const y = Math.floor(fen / 100);
  const c = String(fen % 100).padStart(2, '0');
  return { amount: y.toLocaleString('zh-CN'), cents: c, count: rawStat.value.count };
});

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
function onThird(k) {
  uni.showToast({ title: { wechat: '微信登录待接', alipay: '支付宝待接', mobile: '本机号一键待接' }[k] || '待加', icon: 'none' });
}

onMounted(async () => {
  try {
    const s = await getTodayStat();
    if (s) rawStat.value = { promo: s.promoAmountToday || 0, count: s.awardCountToday || 0 };
  } catch {}
  try {
    const list = await listWinnersTicker(1);
    if (list && list[0]) {
      const w = list[0];
      tickerLine.value = `刚刚 ${w.userMask || '****'} 在 ${w.shopName || '某店'} ${w.sourceLabel || '派奖'} +¥${fen2yuan(w.amount, false)}`;
    }
  } catch {}
});
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.page {
  position: relative;
  min-height: 100vh;
  padding: 26px 18px 24px;
  background: linear-gradient(180deg, #FFB174 0%, $o 35%, $o-d 100%);
  color: #fff;
  overflow: hidden;
}
.bg-deco {
  position: absolute; inset: 0;
  background-image: radial-gradient(rgba(255,255,255,.06) 1px, transparent 1px);
  background-size: 22px 22px;
  pointer-events: none;
}
.bg-mark {
  position: absolute;
  top: -40px; right: -20px;
  font-size: 200px; opacity: .12;
  transform: rotate(-12deg);
  pointer-events: none;
  line-height: 1;
}

/* ━━━━━━━━━━ 大数字 hero ━━━━━━━━━━ */
.stat-hero { position: relative; text-align: center; padding: 18px 0 8px; }
.stat-tag {
  display: inline-block;
  padding: 4px 12px;
  background: rgba(0,0,0,.25);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255,255,255,.2);
  border-radius: 99px;
  font-size: 11px; font-weight: 700; letter-spacing: 1.5px;
}
.stat-tag::before { content: '💎 '; }
.stat-num {
  margin-top: 14px;
  font-size: 64px; font-weight: 900; line-height: 1;
  letter-spacing: -3px;
  background: linear-gradient(135deg, #fff 30%, $gold-50);
  -webkit-background-clip: text; background-clip: text; color: transparent;
  text-shadow: 0 8px 32px rgba(254,243,199,.4);
}
.stat-num .small { font-size: 22px; opacity: .8; margin-left: 4px; -webkit-text-fill-color: rgba(255,234,216,.9); }
.stat-d { margin-top: 8px; font-size: 12px; color: rgba(255,255,255,.85); }
.stat-d .b { color: $gold-50; font-weight: 800; }

/* ━━━━━━━━━━ Brand ━━━━━━━━━━ */
.brand-row {
  margin-top: 22px;
  display: flex; align-items: center; gap: 10px;
  justify-content: center;
  position: relative;
}
.brand-logo {
  width: 38px; height: 38px; border-radius: 12px;
  background: rgba(255,255,255,.95); color: $o-d;
  display: flex; align-items: center; justify-content: center;
  font-size: 22px;
}
.brand { font-size: 22px; font-weight: 900; letter-spacing: -.5px; }
.brand-tag { text-align: center; font-size: 12px; color: rgba(255,255,255,.7); margin-top: 4px; }

/* ━━━━━━━━━━ Mini ticker ━━━━━━━━━━ */
.ticker-mini {
  margin-top: 16px;
  padding: 8px 12px;
  background: rgba(0,0,0,.2); backdrop-filter: blur(10px);
  border-radius: 99px;
  border: 1px solid rgba(255,255,255,.15);
  font-size: 11px; color: rgba(255,255,255,.85);
  display: flex; align-items: center; gap: 8px;
  overflow: hidden; position: relative;
}
.ticker-mini .em { font-size: 12px; flex-shrink: 0; }
.ticker-roll { flex: 1; overflow: hidden; height: 16px; position: relative; }
.ticker-track {
  position: absolute; white-space: nowrap;
  animation: rollx 22s linear infinite;
}
@keyframes rollx { 0% { transform: translateX(100%); } 100% { transform: translateX(-100%); } }

/* ━━━━━━━━━━ 4 卖点 ━━━━━━━━━━ */
.sells {
  margin: 18px 0;
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px;
  position: relative;
}
.sell {
  padding: 10px 4px;
  background: rgba(0,0,0,.18); backdrop-filter: blur(10px);
  border: 1px solid rgba(255,255,255,.15);
  border-radius: 10px;
  text-align: center;
}
.sell .em { font-size: 20px; line-height: 1; }
.sell .t { font-size: 10.5px; font-weight: 800; margin-top: 4px; line-height: 1.2; }

/* ━━━━━━━━━━ Form ━━━━━━━━━━ */
.form {
  position: relative;
  background: rgba(255,255,255,.96); color: $t1;
  border-radius: 22px;
  padding: 20px 18px 16px;
  box-shadow: 0 20px 50px rgba(0,0,0,.3);
}
.form-row {
  display: flex; align-items: center; gap: 6px;
  padding: 11px 12px;
  background: $bg-2;
  border-radius: 12px;
  margin-top: 8px;
}
.form-row .l { width: 28px; color: $t2; font-size: 13px; font-weight: 600; }
.form-row input { flex: 1; font-size: 14px; color: $t1; }
.send { padding: 5px 12px; background: #fff; color: $o; border-radius: 99px; font-size: 11px; font-weight: 700; border: 1px solid $o-100; flex-shrink: 0; }
.send.disabled { background: $bg-2; color: $t4; border-color: $line; }

.submit {
  margin-top: 14px;
  padding: 14px;
  background: linear-gradient(135deg, $o, $o-d);
  color: #fff; text-align: center;
  border-radius: 99px;
  font-weight: 900; font-size: 16px; letter-spacing: 2px;
  box-shadow: $sh-warm;
}
.submit.loading { opacity: .7; }

.other { margin-top: 14px; display: flex; gap: 20px; justify-content: center; }
.other-btn {
  width: 38px; height: 38px; border-radius: 50%;
  background: $bg-2;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px;
}

.agree { margin-top: 10px; text-align: center; font-size: 10.5px; color: $t4; }
.agree .link { color: $o; }

.bottom-pad { height: 12px; }
</style>
