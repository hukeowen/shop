<template>
  <view class="page">
    <nav-bar :title="shop?.name || '店铺'" bg="rgba(255,255,255,.9)" />
    <view class="hero">
      <view class="hero-bg"></view>
      <view class="shop-head">
        <view class="shop-pic">{{ shop?.name?.[0] || '店' }}</view>
        <view class="shop-info">
          <view class="shop-name">{{ shop?.name || '加载中…' }}</view>
          <view class="shop-meta">
            <text class="rating">★ {{ shop?.rating || '4.8' }}</text>
            <text>{{ shop?.distance || '—' }}</text>
            <text>月售 {{ shop?.monthSold || '—' }}</text>
          </view>
          <view v-if="shop?.promoLine" class="promo-line">{{ shop.promoLine }}</view>
        </view>
        <view class="fav" @click="toggleFav">{{ isFav ? '❤️' : '🤍' }}</view>
      </view>
    </view>

    <view class="cats">
      <view v-for="c in cats" :key="c.id" class="cat" :class="{ on: activeCat === c.id }" @click="activeCat = c.id">
        {{ c.name }}
      </view>
    </view>

    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!spus.length" title="该店暂无商品" />
    <view v-else>
      <view v-for="p in spus" :key="p.id" class="prod" @click="goProduct(p)">
        <view class="prod-pic">{{ p.em || '🛍' }}</view>
        <view class="prod-body">
          <view class="prod-name">{{ p.name }}</view>
          <view v-if="p.starInfo" class="prod-tags">
            <text class="tag promo">{{ p.starInfo }}</text>
          </view>
          <view class="prod-row">
            <text class="price">¥{{ p.price }}</text>
            <view class="add">+</view>
          </view>
        </view>
      </view>
    </view>
    <view class="bottom-pad"></view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
// import { getShopInfo, listShopSpus } from '@/api/shop.js';

const shop = ref(null);
const cats = ref([{ id: 0, name: '全部' }, { id: 1, name: '主推' }, { id: 2, name: '热销' }]);
const activeCat = ref(0);
const spus = ref([]);
const loading = ref(true);
const isFav = ref(false);

const route = (() => {
  try { const ps = getCurrentPages(); return ps[ps.length - 1]?.options || {}; } catch { return {}; }
})();

function goProduct(p) { uni.navigateTo({ url: `/pages/product/detail?id=${p.id}&tenantId=${route.tenantId}` }); }
function toggleFav() { isFav.value = !isFav.value; }

onMounted(async () => {
  loading.value = true;
  try {
    // shop.value = await getShopInfo(route.tenantId);
    // spus.value = await listShopSpus(route.tenantId);
    shop.value = null; spus.value = [];
  } finally { loading.value = false; }
});
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background: $bg-2; padding-bottom: 30px; }
.hero { position: relative; padding: 14px; background: linear-gradient(135deg, $o-50, $bg-3); border-bottom-left-radius: 20px; border-bottom-right-radius: 20px; }
.hero-bg { display: none; }
.shop-head { display: flex; gap: 12px; align-items: center; }
.shop-pic { width: 64px; height: 64px; border-radius: 16px; background: linear-gradient(135deg, $o, $o-d); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 26px; font-weight: 800; }
.shop-info { flex: 1; }
.shop-name { font-size: 18px; font-weight: 900; color: $t1; }
.shop-meta { display: flex; gap: 8px; font-size: 11px; color: $t3; margin-top: 4px; }
.rating { color: $gold-d; font-weight: 700; }
.promo-line { margin-top: 6px; font-size: 11px; color: $o; font-weight: 700; padding: 3px 8px; background: $card; border-radius: 6px; display: inline-block; }
.fav { width: 36px; height: 36px; background: rgba(255,255,255,.7); border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 18px; }

.cats { display: flex; gap: 8px; padding: 12px 14px; overflow-x: auto; }
.cat { padding: 6px 14px; border-radius: $r-pill; background: #fff; color: $t2; font-size: 12px; font-weight: 600; white-space: nowrap; }
.cat.on { background: $o; color: #fff; box-shadow: $sh-warm; }

.loading { padding: 40px; text-align: center; color: $t4; }
.prod { display: flex; gap: 10px; padding: 12px; background: #fff; margin: 8px 14px; border-radius: $r-md; box-shadow: $sh-1; }
.prod-pic { width: 70px; height: 70px; border-radius: 10px; background: $o-50; color: $o; display: flex; align-items: center; justify-content: center; font-size: 32px; }
.prod-body { flex: 1; }
.prod-name { font-size: 14px; font-weight: 700; color: $t1; }
.prod-tags { display: flex; gap: 4px; margin-top: 4px; }
.tag.promo { font-size: 10px; padding: 2px 6px; border-radius: 4px; background: $o-50; color: $o-d; font-weight: 700; }
.prod-row { display: flex; justify-content: space-between; align-items: center; margin-top: 6px; }
.price { font-size: 16px; font-weight: 800; color: $o; }
.add { width: 26px; height: 26px; background: linear-gradient(135deg, $o, $o-d); color: #fff; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-weight: 800; box-shadow: $sh-warm; }
.bottom-pad { height: 20px; }
</style>
