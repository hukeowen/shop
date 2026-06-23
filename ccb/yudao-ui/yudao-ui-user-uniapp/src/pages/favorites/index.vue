<template>
  <view class="page">
    <nav-bar title="我的收藏" />
    <view class="tabs">
      <view class="tab" :class="{ on: tab === 'spu' }" @click="switchTab('spu')">商品</view>
      <view class="tab" :class="{ on: tab === 'shop' }" @click="switchTab('shop')">店铺</view>
    </view>
    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!items.length" icon="❤️" title="还没有收藏" desc="商品/店铺右上角 ♡ 即可加入" />
    <view v-else>
      <view v-for="it in items" :key="it.id" class="row" @click="go(it)">
        <view class="pic">
          <image v-if="it.picUrl" :src="it.picUrl" mode="aspectFill" class="pic-img" />
          <text v-else>{{ tab === 'shop' ? '🏪' : '🛍' }}</text>
        </view>
        <view class="body">
          <view class="name">{{ it.spuName || it.shopName || it.name }}</view>
          <view class="meta">{{ it.metaText || it.intro || '' }}</view>
        </view>
        <view v-if="tab === 'spu'" class="price">¥{{ fen2yuan(it.price || it.marketPrice || 0, false) }}</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { favoritePage } from '@/api/product.js';
import { listFavoriteShops } from '@/api/shop.js';
import { fen2yuan } from '@/utils/format.js';

const tab = ref('spu');
const loading = ref(false);
const items = ref([]);

async function switchTab(k) {
  tab.value = k; loading.value = true;
  try {
    if (k === 'spu') {
      const r = await favoritePage(1, 50);
      items.value = r?.list || [];
    } else {
      const list = await listFavoriteShops();
      items.value = (list || []).map((s) => ({
        id: s.tenantId,
        tenantId: s.tenantId,
        shopName: s.shopName || `店铺 #${s.tenantId}`,
        picUrl: s.coverUrl || '',
        metaText: s.address || (s.balance ? `余额 ¥${fen2yuan(s.balance, false)}` : '已收藏'),
      }));
    }
  } catch { items.value = []; }
  finally { loading.value = false; }
}
function go(it) {
  if (tab.value === 'spu') uni.navigateTo({ url: `/pages/product/detail?id=${it.spuId || it.id}&tenantId=${it.tenantId || ''}` });
  else uni.navigateTo({ url: `/pages/shop/home?id=${it.id}&tenantId=${it.tenantId || it.id}` });
}
onMounted(() => switchTab('spu'));
onShow(() => switchTab(tab.value));
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background: $bg-2; padding-bottom: 20px; }
.tabs { display: flex; background: #fff; }
.tab { flex: 1; padding: 14px 0; text-align: center; font-size: 13px; color: $t2; font-weight: 600; position: relative; }
.tab.on { color: $o; }
.tab.on::after { content: ''; position: absolute; left: 40%; right: 40%; bottom: 6px; height: 3px; border-radius: 2px; background: linear-gradient(90deg, $o, $gold); }
.loading { padding: 40px; text-align: center; color: $t4; }
.row { display: flex; align-items: center; gap: 10px; padding: 12px; background: #fff; margin: 8px 14px; border-radius: $r-md; box-shadow: $sh-1; }
.pic { width: 56px; height: 56px; border-radius: 12px; background: $o-50; color: $o; display: flex; align-items: center; justify-content: center; font-size: 26px; overflow: hidden; flex-shrink: 0; }
.pic-img { width: 100%; height: 100%; }
.body { flex: 1; min-width: 0; }
.name { font-size: 14px; font-weight: 700; color: $t1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.meta { font-size: 11px; color: $t3; margin-top: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.price { font-size: 16px; font-weight: 800; color: $o; flex-shrink: 0; }
</style>
