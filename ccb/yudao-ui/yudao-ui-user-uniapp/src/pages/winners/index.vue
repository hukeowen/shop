<template>
  <view class="page">
    <view class="hero">
      <view class="hero-title">
        <text class="trophy">🏆</text>
        <text class="t">中奖公榜</text>
        <text class="sub">商户实时派奖 · 1:1 现金提现</text>
      </view>
      <view class="tabs">
        <view class="tab" :class="{ on: tab === 'live' }" @click="tab = 'live'">🔴 实时中奖</view>
        <view class="tab" :class="{ on: tab === 'rank' }" @click="goRank">📊 榜一排名</view>
      </view>
    </view>

    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!winners.length" icon="🪙" title="还没有中奖记录" desc="去店铺下单参与派奖" />
    <view v-else class="list">
      <view v-for="w in winners" :key="w.id" class="row">
        <view class="row-ic">{{ w.icon || '🏆' }}</view>
        <view class="row-body">
          <view class="row-t1">{{ w.shopName }} 派奖给 <text class="phone">{{ w.userMask }}</text></view>
          <view class="row-t2">{{ w.spuName || '推广奖励' }} · {{ w.time }}</view>
        </view>
        <view class="row-amt">+¥{{ w.amount }}</view>
      </view>
    </view>
    <view class="bottom-pad"></view>
    <bottom-nav active="winners" />
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
// import { getWinnerBoard } from '@/api/promo.js';

const tab = ref('live');
const loading = ref(false);
const winners = ref([]);

function goRank() { uni.navigateTo({ url: '/pages/rank/index' }); }

onMounted(async () => {
  loading.value = true;
  try {
    // winners.value = await getWinnerBoard();
    winners.value = [];
  } finally { loading.value = false; }
});
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; padding-bottom: 90px; background: $bg; }
.hero {
  padding: 20px 14px 14px;
  background:
    radial-gradient(400px 200px at 50% 0%, rgba(212,146,10,.3), transparent 60%),
    linear-gradient(180deg, #18130E 0%, #2A1A0F 100%);
  text-align: center;
  border-bottom-left-radius: 24px; border-bottom-right-radius: 24px;
}
.hero-title { display: flex; flex-direction: column; align-items: center; gap: 2px; }
.trophy { font-size: 32px; }
.t {
  font-size: 22px; font-weight: 900;
  background: linear-gradient(135deg, #fff, #FFD8C0, $gold-l);
  -webkit-background-clip: text; background-clip: text; color: transparent;
}
.sub { color: rgba(255,255,255,.55); font-size: 11px; }

.tabs { display: flex; gap: 6px; margin-top: 14px; justify-content: center; }
.tab {
  padding: 8px 18px; border-radius: $r-pill;
  background: rgba(255,255,255,.1); color: rgba(255,255,255,.7);
  font-size: 12px; font-weight: 700;
  &.on {
    background: linear-gradient(135deg, $gold, $gold-d);
    color: #fff; box-shadow: $sh-gold;
  }
}

.loading { padding: 40px; text-align: center; color: $t4; }
.list { padding: 12px 14px; }
.row {
  display: flex; align-items: center; gap: 10px;
  background: $card; border-radius: $r-md;
  padding: 12px; margin-bottom: 8px;
  box-shadow: $sh-1;
}
.row-ic {
  width: 38px; height: 38px; border-radius: 10px;
  background: $gold-50; color: $gold-d;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px;
}
.row-body { flex: 1; min-width: 0; }
.row-t1 { font-size: 13px; color: $t1; font-weight: 700; }
.row-t1 .phone { color: $o; }
.row-t2 { font-size: 11px; color: $t3; margin-top: 2px; }
.row-amt {
  font-size: 18px; font-weight: 900;
  background: linear-gradient(135deg, $gold, $gold-d);
  -webkit-background-clip: text; background-clip: text; color: transparent;
}
.bottom-pad { height: 30px; }
</style>
