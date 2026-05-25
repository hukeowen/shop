<template>
  <view class="page">
    <nav-bar :title="shop?.shopName || shop?.name || '店铺'" bg="rgba(255,255,255,.9)" />
    <view class="hero">
      <view class="shop-head">
        <view class="shop-pic">
          <image v-if="shop?.shopLogo" :src="shop.shopLogo" class="pic-img" mode="aspectFill" />
          <text v-else>{{ (shop?.shopName || shop?.name || '店')?.[0] }}</text>
        </view>
        <view class="shop-info">
          <view class="shop-name">{{ shop?.shopName || shop?.name || '加载中…' }}</view>
          <view class="shop-meta">
            <text v-if="shop?.rating" class="rating">★ {{ shop.rating }}</text>
            <text v-if="distanceText">📍 {{ distanceText }}</text>
            <text v-if="shop?.businessStatus !== undefined" :class="shop.businessStatus ? 'open' : 'close'">
              {{ shop.businessStatus ? '营业中' : '已打烊' }}
            </text>
          </view>
          <view v-if="promoLine" class="promo-line">{{ promoLine }}</view>
        </view>
        <view class="fav" @click="toggleFav">{{ isFav ? '❤️' : '🤍' }}</view>
      </view>
    </view>

    <view class="cats">
      <view class="cat" :class="{ on: activeCat === 0 }" @click="activeCat = 0">全部</view>
      <view v-for="c in cats" :key="c.id" class="cat" :class="{ on: activeCat === c.id }" @click="activeCat = c.id">
        {{ c.name }}
      </view>
    </view>

    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!filteredSpus.length" title="该店暂无商品" />
    <view v-else>
      <view v-for="p in filteredSpus" :key="p.id" class="prod" @click="goProduct(p)">
        <view class="prod-pic">
          <image v-if="p.picUrl" :src="p.picUrl" mode="aspectFill" class="pic-img" />
          <text v-else>🛍</text>
        </view>
        <view class="prod-body">
          <view class="prod-name">{{ p.name }}</view>
          <view v-if="p.introduction" class="prod-intro">{{ p.introduction }}</view>
          <view class="prod-row">
            <text class="price">¥{{ fen2yuan(p.price || p.marketPrice || 0, false) }}</text>
            <view class="add" @click.stop="onAdd(p)">+</view>
          </view>
        </view>
      </view>
    </view>
    <view class="bottom-pad"></view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { getShopInfo, listShopProducts } from '@/api/shop.js';
import { addCart } from '@/api/cart.js';
import { fen2yuan } from '@/utils/format.js';

const shop = ref(null);
const cats = ref([]);
const activeCat = ref(0);
const spus = ref([]);
const loading = ref(true);
const isFav = ref(false);

const route = (() => {
  try { const ps = getCurrentPages(); return ps[ps.length - 1]?.options || {}; } catch { return {}; }
})();

const distanceText = computed(() => {
  if (shop.value?.distance != null) {
    const m = shop.value.distance;
    return m < 1000 ? `${Math.round(m)}m` : `${(m / 1000).toFixed(1)}km`;
  }
  return '';
});

const promoLine = computed(() => {
  const s = shop.value;
  if (!s) return '';
  if (s.tuijianN) return `推 ${s.tuijianN} 反 1 进行中`;
  if (s.starCount) return `${s.starCount} 星店铺 · 派奖中`;
  return '';
});

const filteredSpus = computed(() => {
  if (activeCat.value === 0) return spus.value;
  return spus.value.filter((p) => p.categoryId === activeCat.value);
});

function goProduct(p) { uni.navigateTo({ url: `/pages/product/detail?id=${p.id}&tenantId=${route.tenantId}` }); }
async function onAdd(p) {
  if (!p.skuIds || !p.skuIds.length) {
    return uni.navigateTo({ url: `/pages/product/detail?id=${p.id}&tenantId=${route.tenantId}` });
  }
  try {
    await addCart(p.skuIds[0], 1);
    uni.showToast({ title: '已加入购物车', icon: 'success' });
  } catch {}
}
function toggleFav() { isFav.value = !isFav.value; uni.showToast({ title: isFav.value ? '已收藏' : '取消', icon: 'none' }); }

onMounted(async () => {
  loading.value = true;
  try {
    shop.value = await getShopInfo({ tenantId: route.tenantId });
  } catch {}
  try {
    const r = await listShopProducts(route.tenantId, 1, 50);
    spus.value = r?.list || [];
    // 从 spu 中聚合分类
    const catMap = new Map();
    for (const s of spus.value) {
      if (s.categoryId && !catMap.has(s.categoryId)) {
        catMap.set(s.categoryId, { id: s.categoryId, name: s.categoryName || `分类#${s.categoryId}` });
      }
    }
    cats.value = [...catMap.values()];
  } catch { spus.value = []; }
  finally { loading.value = false; }
});
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background: $bg-2; padding-bottom: 30px; }
.hero { padding: 14px; background: linear-gradient(135deg, $o-50, $bg-3); border-bottom-left-radius: 20px; border-bottom-right-radius: 20px; }
.shop-head { display: flex; gap: 12px; align-items: center; }
.shop-pic { width: 64px; height: 64px; border-radius: 16px; background: linear-gradient(135deg, $o, $o-d); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 26px; font-weight: 800; overflow: hidden; flex-shrink: 0; }
.pic-img { width: 100%; height: 100%; }
.shop-info { flex: 1; min-width: 0; }
.shop-name { font-size: 18px; font-weight: 900; color: $t1; }
.shop-meta { display: flex; gap: 8px; font-size: 11px; color: $t3; margin-top: 4px; align-items: center; }
.rating { color: $gold-d; font-weight: 700; }
.open { color: $mint; font-weight: 700; }
.close { color: $danger; font-weight: 700; }
.promo-line { margin-top: 6px; font-size: 11px; color: $o; font-weight: 700; padding: 3px 8px; background: $card; border-radius: 6px; display: inline-block; }
.fav { width: 36px; height: 36px; background: rgba(255,255,255,.7); border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 18px; }
.cats { display: flex; gap: 8px; padding: 12px 14px; overflow-x: auto; }
.cat { padding: 6px 14px; border-radius: $r-pill; background: #fff; color: $t2; font-size: 12px; font-weight: 600; white-space: nowrap; flex-shrink: 0; }
.cat.on { background: $o; color: #fff; box-shadow: $sh-warm; }
.loading { padding: 40px; text-align: center; color: $t4; }
.prod { display: flex; gap: 10px; padding: 12px; background: #fff; margin: 8px 14px; border-radius: $r-md; box-shadow: $sh-1; }
.prod-pic { width: 70px; height: 70px; border-radius: 10px; background: $o-50; color: $o; display: flex; align-items: center; justify-content: center; font-size: 32px; overflow: hidden; flex-shrink: 0; }
.prod-body { flex: 1; min-width: 0; }
.prod-name { font-size: 14px; font-weight: 700; color: $t1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.prod-intro { font-size: 11px; color: $t3; margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.prod-row { display: flex; justify-content: space-between; align-items: center; margin-top: 6px; }
.price { font-size: 16px; font-weight: 800; color: $o; }
.add { width: 26px; height: 26px; background: linear-gradient(135deg, $o, $o-d); color: #fff; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-weight: 800; box-shadow: $sh-warm; }
.bottom-pad { height: 20px; }
</style>
