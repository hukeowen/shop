<template>
  <view class="page">
    <nav-bar title="消费积分明细" />
    <view class="sum-card">
      <text class="l">消费积分余额</text>
      <view class="amt">{{ balance }}</view>
      <text class="d">100 消费积分 = ¥1，下次消费抵扣</text>
    </view>
    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!records.length" title="暂无积分明细" />
    <view v-else>
      <view v-for="r in records" :key="r.id" class="row">
        <view class="body">
          <view class="t">{{ r.title }}</view>
          <view class="d">{{ r.time }} · {{ r.shopName }}</view>
        </view>
        <view class="amt" :class="{ neg: r.amount < 0 }">{{ r.amount > 0 ? '+' : '' }}{{ r.amount }}</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
// import { listConsumePoints } from '@/api/promo.js';
const balance = ref(0);
const loading = ref(false);
const records = ref([]);
onMounted(async () => { loading.value = true; try { records.value = []; } finally { loading.value = false; } });
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background: $bg-2; padding-bottom: 30px; }
.sum-card { margin: 14px; padding: 20px; background: linear-gradient(135deg, $mint, #0E9E6D); color: #fff; border-radius: $r-lg; box-shadow: 0 8px 24px rgba(16,185,129,.3); }
.sum-card .l { font-size: 12px; opacity: .8; }
.sum-card .amt { font-size: 34px; font-weight: 900; margin-top: 4px; }
.sum-card .d { font-size: 11px; opacity: .8; margin-top: 4px; }
.loading { padding: 40px; text-align: center; color: $t4; }
.row { display: flex; align-items: center; padding: 14px; background: #fff; margin: 6px 14px; border-radius: $r-md; box-shadow: $sh-1; }
.body { flex: 1; }
.t { font-size: 13px; font-weight: 700; color: $t1; }
.d { font-size: 11px; color: $t3; margin-top: 2px; }
.amt { font-size: 17px; font-weight: 900; color: $mint; }
.amt.neg { color: $t3; }
</style>
