<template>
  <view class="page">
    <nav-bar title="商品详情" bg="rgba(255,255,255,.95)" />
    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!spu" title="商品不存在" />
    <view v-else>
      <view class="cover">
        <image v-if="coverUrl" :src="coverUrl" mode="aspectFill" class="cover-img" />
        <text v-else class="cover-em">🛍</text>
        <view v-if="promoTag" class="promo-tag">{{ promoTag }}</view>
      </view>
      <view class="info">
        <view class="price-row">
          <text class="price">¥{{ fen2yuan(spu.price, false) }}</text>
          <text v-if="spu.marketPrice && spu.marketPrice > spu.price" class="original">¥{{ fen2yuan(spu.marketPrice, false) }}</text>
          <text class="sold">已售 {{ spu.salesCount || 0 }}</text>
        </view>
        <view class="name">{{ spu.name }}</view>
        <view v-if="spu.introduction" class="intro">{{ spu.introduction }}</view>
      </view>

      <view v-if="promoConfig" class="card star-card">
        <view class="star-head">
          <text class="ic">🏆</text>
          <view class="star-body">
            <view v-if="promoConfig.tuijianN" class="star-t">推 {{ promoConfig.tuijianN }} 反 1</view>
            <view class="star-t" v-else-if="promoConfig.starCount">{{ promoConfig.starCount }} 星派奖</view>
            <view class="star-d">买够即返推广积分，1:1 提现</view>
          </view>
        </view>
      </view>

      <view class="card">
        <view class="card-title">店铺</view>
        <view class="shop-link" @click="goShop">
          <view class="shop-pic">{{ shopName?.[0] || '店' }}</view>
          <view class="shop-body">
            <view class="shop-name">{{ shopName }}</view>
            <view class="shop-meta">点击进店看更多</view>
          </view>
          <text class="enter">进店 ›</text>
        </view>
      </view>

      <view v-if="spu.description" class="card desc" v-html="spu.description"></view>
    </view>

    <view v-if="spu" class="actions">
      <view class="act-side">
        <view class="act-ic" @click="goCart">🛒<text v-if="cartCount" class="badge">{{ cartCount }}</text></view>
        <view class="act-ic" @click="toggleFav">{{ isFav ? '❤️' : '🤍' }}</view>
      </view>
      <view class="act-add" @click="onAddCart">加入购物车</view>
      <view class="act-buy" @click="onBuyNow">立即购买</view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { getSpuDetail, favoriteCreate, favoriteDelete, favoriteExists } from '@/api/product.js';
import { addCart, getCartCount } from '@/api/cart.js';
import { request } from '@/utils/request.js';
import { fen2yuan } from '@/utils/format.js';
import { useUserStore } from '@/store/user.js';

const user = useUserStore();

function requireLogin(returnUrl) {
  try {
    if (typeof localStorage !== 'undefined' && returnUrl) {
      localStorage.setItem('redirect:after-login', returnUrl);
    }
  } catch {}
  uni.navigateTo({ url: '/pages/login/index' });
}

const loading = ref(true);
const spu = ref(null);
const cartCount = ref(0);
const isFav = ref(false);
const promoConfig = ref(null);

const route = (() => {
  try { const ps = getCurrentPages(); return ps[ps.length - 1]?.options || {}; } catch { return {}; }
})();

const coverUrl = computed(() => spu.value?.picUrl || (spu.value?.sliderPicUrls?.[0]) || '');
const promoTag = computed(() => {
  if (!promoConfig.value) return '';
  if (promoConfig.value.tuijianN) return `推 ${promoConfig.value.tuijianN} 反 1`;
  if (promoConfig.value.starCount) return '派奖池';
  return '';
});
const shopName = computed(() => spu.value?.shopName || spu.value?.tenantName || '本店');

function curUrl() {
  return `/pages/product/detail?id=${route.id || ''}&tenantId=${route.tenantId || ''}`;
}
function goShop()  { if (spu.value?.tenantId) uni.navigateTo({ url: `/pages/shop/home?tenantId=${spu.value.tenantId}` }); }
function goCart()  {
  if (!user.isLogin) return requireLogin(curUrl());
  uni.navigateTo({ url: '/pages/cart/index' });
}
async function toggleFav() {
  if (!spu.value) return;
  if (!user.isLogin) return requireLogin(curUrl());
  try {
    if (isFav.value) { await favoriteDelete(spu.value.id); isFav.value = false; }
    else            { await favoriteCreate(spu.value.id);  isFav.value = true; }
    uni.showToast({ title: isFav.value ? '已收藏' : '取消收藏', icon: 'success' });
  } catch {}
}
function pickSku() {
  if (!spu.value?.skus?.length) return null;
  return spu.value.skus[0];
}
async function onAddCart() {
  if (!user.isLogin) return requireLogin(curUrl());
  const sku = pickSku();
  if (!sku) return uni.showToast({ title: '该商品暂无规格', icon: 'none' });
  try {
    await addCart(sku.id, 1);
    uni.showToast({ title: '已加入购物车', icon: 'success' });
    cartCount.value++;
  } catch {}
}
function onBuyNow() {
  if (!user.isLogin) return requireLogin(curUrl());
  const sku = pickSku();
  if (!sku) return uni.showToast({ title: '该商品暂无规格', icon: 'none' });
  // 直接跳 checkout，带 sku + 数量 + shop
  uni.navigateTo({
    url: `/pages/checkout/index?type=buy_now&skuId=${sku.id}&count=1&tenantId=${route.tenantId || spu.value.tenantId || ''}`,
  });
}

onMounted(async () => {
  loading.value = true;
  try {
    spu.value = await getSpuDetail(route.id, route.tenantId);
  } catch {} finally { loading.value = false; }

  if (spu.value) {
    try {
      const cfg = await request({ url: `/app-api/merchant/mini/promo/product-config?spuId=${spu.value.id}`, tenantId: route.tenantId });
      promoConfig.value = cfg;
    } catch {}
    try {
      isFav.value = !!(await favoriteExists(spu.value.id));
    } catch {}
    try {
      cartCount.value = (await getCartCount()) || 0;
    } catch {}
  }
});
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background: $bg-2; padding-bottom: 80px; }
.loading { padding: 40px; text-align: center; color: $t4; }
.cover { height: 320px; background: linear-gradient(135deg, $o-50, #fff); display: flex; align-items: center; justify-content: center; position: relative; overflow: hidden; }
.cover-img { width: 100%; height: 100%; }
.cover-em { font-size: 96px; opacity: .9; }
.promo-tag { position: absolute; top: 16px; left: 16px; background: linear-gradient(135deg, $o, $o-d); color: #fff; padding: 6px 12px; border-radius: $r-pill; font-size: 12px; font-weight: 800; box-shadow: $sh-warm; }
.info { background: #fff; padding: 16px; }
.price-row { display: flex; align-items: baseline; gap: 8px; }
.price { font-size: 24px; font-weight: 900; color: $o; }
.original { font-size: 13px; color: $t4; text-decoration: line-through; }
.sold { font-size: 11px; color: $t3; margin-left: auto; }
.name { font-size: 16px; font-weight: 800; color: $t1; margin-top: 8px; line-height: 1.4; }
.intro { font-size: 12px; color: $t3; margin-top: 6px; }

.card { background: #fff; margin: 8px 0 0; padding: 14px 16px; }
.card-title { font-size: 14px; font-weight: 800; color: $t1; margin-bottom: 10px; }
.star-card { background: linear-gradient(135deg, $gold-50, #fff); }
.star-head { display: flex; gap: 10px; align-items: center; }
.star-head .ic { font-size: 24px; }
.star-t { font-size: 14px; font-weight: 800; color: $gold-d; }
.star-d { font-size: 11px; color: $t3; margin-top: 2px; }

.shop-link { display: flex; gap: 10px; align-items: center; padding: 8px 0; }
.shop-pic { width: 40px; height: 40px; border-radius: 10px; background: linear-gradient(135deg, $o, $o-d); color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 800; }
.shop-body { flex: 1; }
.shop-name { font-size: 14px; font-weight: 700; color: $t1; }
.shop-meta { font-size: 11px; color: $t3; margin-top: 2px; }
.enter { color: $o; font-size: 12px; font-weight: 700; }
.desc { font-size: 13px; color: $t2; line-height: 1.6; }

.actions { position: fixed; left: 0; right: 0; bottom: 0; display: flex; align-items: center; gap: 8px; padding: 10px 14px; padding-bottom: calc(10px + env(safe-area-inset-bottom)); background: #fff; border-top: 1px solid $line; }
.act-side { display: flex; gap: 8px; }
.act-ic { width: 44px; height: 44px; border-radius: 50%; background: $bg-2; display: flex; align-items: center; justify-content: center; font-size: 20px; position: relative; }
.act-ic .badge { position: absolute; top: -2px; right: -2px; background: $danger; color: #fff; font-size: 10px; padding: 1px 5px; border-radius: 8px; }
.act-add, .act-buy { flex: 1; padding: 12px 0; text-align: center; font-size: 14px; font-weight: 800; border-radius: $r-pill; }
.act-add { background: $gold-50; color: $gold-d; }
.act-buy { background: linear-gradient(135deg, $o, $o-d); color: #fff; box-shadow: $sh-warm; }
</style>
