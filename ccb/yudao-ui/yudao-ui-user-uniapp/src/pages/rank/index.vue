<template>
  <view class="page">
    <nav-bar title="榜一排名" bg="transparent" txt="#fff" />
    <view class="hero">
      <text class="t-em">🏆</text>
      <view class="hero-t">推广榜 · TOP {{ list.length }}</view>
      <view class="hero-d">按当周分销佣金排名</view>
    </view>
    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!list.length" title="暂无排名" />
    <view v-else class="board">
      <view v-for="(u, i) in list" :key="u.id || i" class="row" :class="'r' + (i + 1)">
        <view class="rank">{{ i + 1 }}</view>
        <view class="ava">
          <image v-if="u.avatar" :src="u.avatar" class="ava-img" mode="aspectFill" />
          <text v-else>{{ (u.nickname || '邀')?.[0] }}</text>
        </view>
        <view class="body">
          <view class="name">{{ u.nickname || u.userName || '匿名用户' }}</view>
          <view class="d">直推 {{ u.brokerageUserCount || 0 }} 人</view>
        </view>
        <view class="amt">¥{{ fen2yuan(u.brokeragePrice || 0, false) }}</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { rankByPrice } from '@/api/brokerage.js';
import { fen2yuan } from '@/utils/format.js';

const loading = ref(true);
const list = ref([]);
onMounted(async () => {
  loading.value = true;
  try {
    const r = await rankByPrice(1, 50);
    list.value = r?.list || [];
  } catch { list.value = []; }
  finally { loading.value = false; }
});
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background: $bg-2; }
.hero { padding: 20px 14px 24px; text-align: center; background: radial-gradient(400px 200px at 50% 0%, rgba(212,146,10,.35), transparent 70%), linear-gradient(180deg, #18130E, #2A1A0F); color: #fff; border-bottom-left-radius: 24px; border-bottom-right-radius: 24px; }
.t-em { font-size: 40px; }
.hero-t { font-size: 18px; font-weight: 900; margin-top: 4px; background: linear-gradient(135deg, #fff, $gold-l); -webkit-background-clip: text; background-clip: text; color: transparent; }
.hero-d { font-size: 11px; opacity: .6; margin-top: 2px; }
.loading { padding: 40px; text-align: center; color: $t4; }
.board { padding: 14px; }
.row { display: flex; gap: 10px; padding: 12px; background: #fff; margin-bottom: 8px; border-radius: $r-md; align-items: center; box-shadow: $sh-1; }
.row.r1 { background: linear-gradient(135deg, #FFF6E0, #FFE5C0); }
.row.r2 { background: linear-gradient(135deg, #F2F5F8, #DEE5EC); }
.row.r3 { background: linear-gradient(135deg, #FFE3D1, #FFCFB1); }
.rank { width: 28px; height: 28px; border-radius: 50%; background: $bg-2; color: $t2; font-weight: 800; display: flex; align-items: center; justify-content: center; }
.row.r1 .rank { background: linear-gradient(135deg, $gold, $gold-d); color: #fff; }
.row.r2 .rank { background: #94A3B8; color: #fff; }
.row.r3 .rank { background: #B97048; color: #fff; }
.ava { width: 36px; height: 36px; border-radius: 50%; background: linear-gradient(135deg, $o, $gold); color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 800; overflow: hidden; }
.ava-img { width: 100%; height: 100%; }
.body { flex: 1; min-width: 0; }
.name { font-size: 13px; font-weight: 700; color: $t1; }
.d { font-size: 11px; color: $t3; margin-top: 2px; }
.amt { font-size: 17px; font-weight: 900; color: $gold-d; }
</style>
