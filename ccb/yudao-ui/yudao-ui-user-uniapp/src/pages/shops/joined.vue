<template>
  <view class="page">
    <nav-bar title="我加入的店铺" />
    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!shops.length" icon="🏪" title="还没有加入的店铺" desc="逛逛附近好店，下单即加入" />
    <view v-else>
      <view class="tip">共 {{ shops.length }} 家 · 资产（余额 / 积分 / 星级）按店铺独立</view>
      <view v-for="s in shops" :key="s.tenantId" class="shop" @click="goShop(s)">
        <view class="cv">
          <image v-if="s.coverUrl" :src="s.coverUrl" mode="aspectFill" class="cv-img" />
          <text v-else class="cv-ph">{{ (s.shopName || '店')[0] }}</text>
        </view>
        <view class="bd">
          <view class="nm">
            <text class="t">{{ s.shopName || '店铺 #' + s.tenantId }}</text>
            <text v-if="s.star >= 1" class="star">{{ '★'.repeat(s.star) }} {{ s.star }}星</text>
          </view>
          <view class="meta">
            <text class="m">余额 <text class="b">¥{{ fen2yuan(s.balance || 0, false) }}</text></text>
            <text class="m">推广 <text class="b">{{ fen2yuan(s.promoPoints || 0, false) }}</text></text>
            <text class="m">消费 <text class="b">{{ fen2yuan(s.points || 0, false) }}</text></text>
          </view>
          <view v-if="s.lastVisitAt" class="visit">最近访问 {{ fmtTime(s.lastVisitAt) }}</view>
        </view>
        <text class="arr">›</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { listMyShopsEnriched } from '@/api/shop.js';
import { fen2yuan, fmtTime } from '@/utils/format.js';

const loading = ref(true);
const shops = ref([]);

async function load() {
  loading.value = true;
  try { shops.value = (await listMyShopsEnriched(true)) || []; }
  catch { shops.value = []; }
  finally { loading.value = false; }
}
function goShop(s) {
  uni.navigateTo({ url: `/pages/shop/home?id=${s.tenantId}&tenantId=${s.tenantId}` });
}
onShow(load);
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background: $bg-2; padding-bottom: 24px; }
.loading { padding: 40px; text-align: center; color: $t4; }
.tip { padding: 12px 16px 4px; font-size: 11px; color: $t3; }
.shop { display: flex; align-items: center; gap: 12px; padding: 12px 14px; background: #fff; margin: 8px 14px; border-radius: $r-md; box-shadow: $sh-1; }
.cv { width: 54px; height: 54px; border-radius: 12px; overflow: hidden; background: $o-50; color: $o; display: flex; align-items: center; justify-content: center; font-size: 24px; font-weight: 800; flex-shrink: 0; }
.cv-img { width: 100%; height: 100%; }
.bd { flex: 1; min-width: 0; }
.nm { display: flex; align-items: center; gap: 6px; }
.nm .t { font-size: 15px; font-weight: 700; color: $t1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 60%; }
.nm .star { font-size: 11px; color: $gold-d; font-weight: 700; flex-shrink: 0; }
.meta { display: flex; gap: 12px; margin-top: 5px; font-size: 11px; color: $t3; }
.meta .b { color: $o-d; font-weight: 700; }
.visit { font-size: 10px; color: $t4; margin-top: 4px; }
.arr { font-size: 18px; color: $t4; flex-shrink: 0; }
</style>
