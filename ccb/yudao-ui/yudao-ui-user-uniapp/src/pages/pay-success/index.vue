<template>
  <view class="page">
    <view :style="sbhSpacer"></view>
    <view class="ok">
      <view class="ok-ic">✓</view>
      <view class="ok-t">支付成功</view>
      <view class="ok-amt">¥{{ amount }}</view>
    </view>

    <view v-if="reward" class="reward">
      <view class="r-tag">💎 你刚刚到账</view>
      <view class="r-amt">+ ¥{{ reward.amount }}</view>
      <view class="r-d">{{ reward.source }}</view>
    </view>

    <view class="actions">
      <view class="btn ghost" @click="goOrders">查看订单</view>
      <view class="btn warm" @click="goHome">返回首页</view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { fen2yuan } from '@/utils/format.js';
import { sbhSpacerStyle } from '@/utils/safeTop.js';
const sbhSpacer = sbhSpacerStyle();

const amount = ref('0.00');
const reward = ref(null);

function goOrders() { uni.reLaunch({ url: '/pages/order/list' }); }
function goHome()   { uni.reLaunch({ url: '/pages/index/index' }); }

onMounted(() => {
  try {
    const a = uni.getStorageSync('pay-success-amount');
    if (a) amount.value = fen2yuan(a, false);
    uni.removeStorageSync('pay-success-amount');
  } catch {}
  try {
    const r = uni.getStorageSync('pay-success-reward');
    if (r) reward.value = { amount: fen2yuan(r.amount || 0, false), source: r.source };
    uni.removeStorageSync('pay-success-reward');
  } catch {}
});
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background: $bg-2; padding: 40px 14px; display: flex; flex-direction: column; align-items: center; }
.ok { text-align: center; }
.ok-ic { width: 70px; height: 70px; border-radius: 50%; background: linear-gradient(135deg, $mint, $mint-l); color: #fff; font-size: 36px; display: flex; align-items: center; justify-content: center; margin: 0 auto 16px; box-shadow: 0 8px 24px rgba(16,185,129,.3); }
.ok-t { font-size: 20px; font-weight: 900; color: $t1; }
.ok-amt { font-size: 28px; font-weight: 900; color: $o; margin-top: 6px; }
.reward { margin-top: 28px; padding: 22px 30px; background: linear-gradient(135deg, $gold-50, $o-50); border-radius: $r-lg; text-align: center; min-width: 240px; box-shadow: $sh-warm; }
.r-tag { font-size: 12px; color: $gold-d; font-weight: 700; }
.r-amt { margin-top: 6px; font-size: 32px; font-weight: 900; background: linear-gradient(135deg, $o, $gold); -webkit-background-clip: text; background-clip: text; color: transparent; }
.r-d { font-size: 11px; color: $t3; margin-top: 4px; }
.actions { margin-top: 40px; display: flex; gap: 12px; width: 100%; max-width: 320px; }
.btn { flex: 1; padding: 14px 0; text-align: center; border-radius: $r-pill; font-weight: 800; font-size: 14px; }
.ghost { background: $bg-2; color: $t2; }
.warm { background: linear-gradient(135deg, $o, $o-d); color: #fff; box-shadow: $sh-warm; }
</style>
