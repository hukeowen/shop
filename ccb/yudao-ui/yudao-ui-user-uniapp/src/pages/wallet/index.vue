<template>
  <view class="page">
    <nav-bar title="我的钱包" bg="transparent" txt="#fff" />
    <view class="hero">
      <view class="hero-tag">💎 余额</view>
      <view class="hero-amt">¥{{ balance }}</view>
      <view class="hero-row">
        <view class="hr-col">
          <view class="hr-l">推广积分</view>
          <view class="hr-v">{{ promoPoints }}</view>
        </view>
        <view class="hr-col">
          <view class="hr-l">消费积分</view>
          <view class="hr-v">{{ consumePoints }}</view>
        </view>
      </view>
      <view class="hero-actions">
        <view class="btn warm" @click="goWithdraw">提现</view>
        <view class="btn ghost" @click="goRecords">流水</view>
      </view>
    </view>

    <view class="section-title"><text class="h">最近到账</text></view>
    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!records.length" title="暂无到账记录" />
    <view v-else>
      <view v-for="r in records" :key="r.id" class="row">
        <view class="r-ic">{{ r.icon || '💰' }}</view>
        <view class="r-body">
          <view class="r-t">{{ r.title }}</view>
          <view class="r-d">{{ r.time }}</view>
        </view>
        <view class="r-amt">+¥{{ r.amount }}</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
// import { getWallet, listPromoRecords } from '@/api/promo.js';

const balance = ref('0.00');
const promoPoints = ref(0);
const consumePoints = ref(0);
const loading = ref(false);
const records = ref([]);

function goWithdraw() { uni.navigateTo({ url: '/pages/withdraw/index' }); }
function goRecords() { uni.navigateTo({ url: '/pages/points/promo' }); }

onMounted(async () => {
  loading.value = true;
  try { records.value = []; }
  finally { loading.value = false; }
});
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background: $bg-2; }
.hero { padding: 24px 14px 20px; background: linear-gradient(135deg, #18130E, #2A1A0F); color: #fff; border-bottom-left-radius: 28px; border-bottom-right-radius: 28px; }
.hero-tag { font-size: 12px; opacity: .7; }
.hero-amt { font-size: 38px; font-weight: 900; margin-top: 4px; background: linear-gradient(135deg, #fff, $gold-l); -webkit-background-clip: text; background-clip: text; color: transparent; }
.hero-row { display: flex; margin-top: 16px; }
.hr-col { flex: 1; }
.hr-l { font-size: 11px; opacity: .6; }
.hr-v { font-size: 18px; font-weight: 800; margin-top: 2px; }
.hero-actions { display: flex; gap: 10px; margin-top: 20px; }
.btn { flex: 1; padding: 12px 0; text-align: center; border-radius: $r-pill; font-weight: 800; font-size: 14px; }
.btn.warm { background: linear-gradient(135deg, $o, $o-d); color: #fff; box-shadow: $sh-warm; }
.btn.ghost { background: rgba(255,255,255,.12); color: #fff; }
.section-title { padding: 18px 14px 8px; }
.section-title .h { font-size: 14px; font-weight: 800; color: $t1; }
.loading { padding: 40px; text-align: center; color: $t4; }
.row { display: flex; gap: 10px; padding: 12px; background: #fff; margin: 6px 14px; border-radius: $r-md; align-items: center; box-shadow: $sh-1; }
.r-ic { width: 36px; height: 36px; background: $o-50; color: $o; border-radius: 10px; display: flex; align-items: center; justify-content: center; font-size: 16px; }
.r-body { flex: 1; }
.r-t { font-size: 13px; font-weight: 700; color: $t1; }
.r-d { font-size: 11px; color: $t3; margin-top: 2px; }
.r-amt { font-size: 16px; font-weight: 800; color: $o; }
</style>
