<template>
  <view class="page">
    <nav-bar title="提现申请" />
    <view class="card">
      <view class="card-row">
        <text class="l">可提现余额</text>
        <text class="hl">¥{{ withdrawable }}</text>
      </view>
    </view>
    <view class="card">
      <view class="card-title">提现金额</view>
      <view class="amt-input">
        <text class="¥">¥</text>
        <input v-model="amount" type="digit" placeholder="0.00" />
        <text class="all" @click="amount = withdrawable">全部</text>
      </view>
    </view>
    <view class="card">
      <view class="card-title">到账方式</view>
      <view v-for="m in methods" :key="m.k" class="m" :class="{ on: method === m.k }" @click="method = m.k">
        <text class="m-ic">{{ m.ic }}</text>
        <text class="m-l">{{ m.label }}</text>
        <text class="m-c">●</text>
      </view>
    </view>
    <view class="tip">说明：1 元推广积分 = 1 元现金，到账时间 1 - 3 个工作日</view>
    <view class="submit" @click="onSubmit">申请提现</view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
// import { applyWithdraw, getWallet } from '@/api/promo.js';

const withdrawable = ref('0.00');
const amount = ref('');
const method = ref('wechat');
const methods = [
  { k: 'wechat', ic: '💚', label: '微信零钱' },
  { k: 'alipay', ic: '🅰️', label: '支付宝' },
  { k: 'bank',   ic: '🏦', label: '银行卡' },
];

function onSubmit() {
  if (!amount.value || Number(amount.value) <= 0) return uni.showToast({ title: '请输入金额', icon: 'none' });
  uni.showLoading({ title: '提交中…' });
  setTimeout(() => { uni.hideLoading(); uni.showToast({ title: '已提交', icon: 'success' }); setTimeout(() => uni.navigateBack(), 1000); }, 600);
}
onMounted(async () => { /* withdrawable.value = (await getWallet()).withdrawable/100 */ });
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
.¥ { font-size: 24px; color: $o; font-weight: 800; }
.amt-input input { flex: 1; font-size: 24px; font-weight: 800; color: $t1; }
.all { color: $o; font-size: 12px; font-weight: 700; }
.m { display: flex; align-items: center; gap: 10px; padding: 10px 0; border-bottom: 1px solid $line; }
.m:last-child { border-bottom: none; }
.m-ic { font-size: 22px; }
.m-l { flex: 1; font-size: 14px; color: $t1; }
.m-c { color: $line-d; font-size: 16px; }
.m.on .m-c { color: $o; }
.tip { padding: 12px 24px; font-size: 11px; color: $t4; line-height: 1.5; }
.submit { margin: 20px 14px 0; padding: 14px; background: linear-gradient(135deg, $o, $o-d); color: #fff; text-align: center; border-radius: $r-pill; font-weight: 800; font-size: 15px; box-shadow: $sh-warm; }
</style>
