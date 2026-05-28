<template>
  <view class="page">
    <nav-bar title="提现申请" />
    <view class="card">
      <view class="card-row">
        <text class="l">可申请提现积分</text>
        <text class="hl">¥{{ withdrawable }}</text>
      </view>
    </view>
    <view class="card">
      <view class="card-title">提现金额（元）</view>
      <view class="amt-input">
        <text class="symbol">¥</text>
        <input v-model="amount" type="digit" placeholder="0.00" @input="onAmountInput" />
        <text class="all" @click="amount = withdrawable">全部</text>
      </view>
      <view v-if="amtFenInt > 0 && amtFenInt > promoBalance" class="warn">超出可提现余额</view>
    </view>
    <view class="card">
      <view class="card-title">收款方式</view>
      <view v-for="m in methods" :key="m.k" class="m" :class="{ on: method === m.k }" @click="method = m.k">
        <text class="m-ic">{{ m.ic }}</text>
        <text class="m-l">{{ m.label }}</text>
        <text class="m-c">●</text>
      </view>
    </view>
    <view v-if="method === 'bank'" class="card">
      <view class="card-title">银行卡信息</view>
      <view class="field"><text class="f-l">户名</text><input v-model="bankName" placeholder="请输入持卡人姓名" /></view>
      <view class="field"><text class="f-l">卡号</text><input v-model="bankAccount" type="number" placeholder="请输入银行卡号" /></view>
      <view class="field"><text class="f-l">开户行</text><input v-model="bankBank" placeholder="如：中国工商银行" /></view>
    </view>
    <view v-else-if="method === 'alipay'" class="card">
      <view class="card-title">支付宝信息</view>
      <view class="field"><text class="f-l">姓名</text><input v-model="bankName" placeholder="请输入支付宝实名" /></view>
      <view class="field"><text class="f-l">账号</text><input v-model="bankAccount" placeholder="手机号 / 邮箱" /></view>
    </view>

    <view class="tip">说明：1 推广积分 = ¥0.01。<text class="b">提现申请由商户审批兑付</text>，到账时间以商户处理为准。平台为技术中介，<text class="b">不承担兑付保证</text>。</view>
    <view class="submit" :class="{ disabled: !canSubmit }" @click="onSubmit">{{ submitting ? '提交中…' : '申请提现' }}</view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { getAccount } from '@/api/promo.js';
import { applyWithdraw } from '@/api/brokerage.js';
import { fen2yuan } from '@/utils/format.js';

const promoBalance = ref(0);
const withdrawable = computed(() => fen2yuan(promoBalance.value, false));
const amount = ref('');
const amtFenInt = computed(() => {
  const v = Number(amount.value);
  if (Number.isNaN(v) || v <= 0) return 0;
  return Math.round(v * 100);
});
function onAmountInput(e) { /* v-model already syncs */ }

const method = ref('wechat');
const methods = [
  { k: 'wechat', ic: '💚', label: '微信零钱' },
  { k: 'alipay', ic: '🅰️', label: '支付宝' },
  { k: 'bank',   ic: '🏦', label: '银行卡' },
];

const bankName = ref('');
const bankAccount = ref('');
const bankBank = ref('');

const submitting = ref(false);
const canSubmit = computed(() => {
  if (submitting.value) return false;
  if (amtFenInt.value < 100) return false; // 最低 ¥1
  if (amtFenInt.value > promoBalance.value) return false;
  if (method.value === 'bank' && (!bankName.value || !bankAccount.value || !bankBank.value)) return false;
  if (method.value === 'alipay' && (!bankName.value || !bankAccount.value)) return false;
  return true;
});

async function onSubmit() {
  if (!canSubmit.value) return;
  submitting.value = true;
  try {
    // applyWithdraw 由 yudao 分销模块支持：price 单位分 + type(1/2/3=微信/支付宝/银行) + 收款信息
    const body = {
      price: amtFenInt.value,
      type: method.value === 'wechat' ? 1 : method.value === 'alipay' ? 2 : 3,
      userName: bankName.value || undefined,
      accountNo: bankAccount.value || undefined,
      bankName: bankBank.value || undefined,
    };
    await applyWithdraw(body);
    uni.showToast({ title: '已提交', icon: 'success' });
    setTimeout(() => uni.navigateBack(), 800);
  } catch {} finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  try {
    const acct = await getAccount();
    promoBalance.value = acct?.promoPointBalance || 0;
  } catch {}
});
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background: $bg-2; padding-bottom: 40px; }
.card { background: #fff; margin: 10px 14px; border-radius: $r-md; padding: 14px; box-shadow: $sh-1; }
.card-title { font-size: 14px; font-weight: 800; color: $t1; margin-bottom: 10px; }
.card-row { display: flex; justify-content: space-between; align-items: center; }
.l { font-size: 13px; color: $t2; }
.hl { font-size: 20px; font-weight: 900; color: $o; }
.amt-input { display: flex; align-items: center; gap: 8px; padding: 6px 0; border-bottom: 1px solid $line; }
.symbol { font-size: 24px; color: $o; font-weight: 800; }
.amt-input input { flex: 1; font-size: 24px; font-weight: 800; color: $t1; }
.all { color: $o; font-size: 12px; font-weight: 700; }
.warn { color: $danger; font-size: 11px; margin-top: 6px; }
.m { display: flex; align-items: center; gap: 10px; padding: 10px 0; border-bottom: 1px solid $line; }
.m:last-child { border-bottom: none; }
.m-ic { font-size: 22px; }
.m-l { flex: 1; font-size: 14px; color: $t1; }
.m-c { color: $line-d; font-size: 16px; }
.m.on .m-c { color: $o; }
.field { display: flex; align-items: center; gap: 8px; padding: 12px 0; border-bottom: 1px solid $line; }
.field:last-child { border-bottom: none; }
.f-l { width: 60px; font-size: 13px; color: $t2; font-weight: 600; }
.field input { flex: 1; font-size: 14px; color: $t1; }
.tip { padding: 12px 24px; font-size: 11px; color: $t4; line-height: 1.5; }
.submit { margin: 20px 14px 0; padding: 14px; background: linear-gradient(135deg, $o, $o-d); color: #fff; text-align: center; border-radius: $r-pill; font-weight: 800; font-size: 15px; box-shadow: $sh-warm; }
.submit.disabled { background: #94A3B8; box-shadow: none; opacity: .7; }
</style>
