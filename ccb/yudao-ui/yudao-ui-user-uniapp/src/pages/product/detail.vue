<template>
  <view class="page">
    <nav-bar title="商品详情" bg="rgba(255,255,255,.95)" />
    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!spu" title="商品不存在" />
    <view v-else>
      <view class="cover">
        <view class="cover-em">{{ spu.em || '🛍' }}</view>
        <view v-if="spu.promoTag" class="promo-tag">{{ spu.promoTag }}</view>
      </view>
      <view class="info">
        <view class="price-row">
          <text class="price">¥{{ spu.price }}</text>
          <text v-if="spu.originalPrice" class="original">¥{{ spu.originalPrice }}</text>
        </view>
        <view class="name">{{ spu.name }}</view>
        <view v-if="spu.intro" class="intro">{{ spu.intro }}</view>
      </view>
      <view v-if="spu.starInfo" class="card star-card">
        <view class="star-head">
          <text class="ic">🏆</text>
          <view class="star-body">
            <view class="star-t">{{ spu.starInfo.title }}</view>
            <view class="star-d">{{ spu.starInfo.desc }}</view>
          </view>
        </view>
      </view>
      <view class="card">
        <view class="card-title">店铺</view>
        <view class="shop-link" @click="goShop">
          <view class="shop-pic">{{ spu.shopName?.[0] || '店' }}</view>
          <view class="shop-body">
            <view class="shop-name">{{ spu.shopName }}</view>
            <view class="shop-meta">★ {{ spu.shopRating || '4.8' }} · 月售 {{ spu.shopMonthSold || '—' }}</view>
          </view>
          <text class="enter">进店 ›</text>
        </view>
      </view>
    </view>

    <view v-if="spu" class="actions">
      <view class="act-side">
        <view class="act-ic" @click="goCart">🛒<text v-if="cartCount" class="badge">{{ cartCount }}</text></view>
        <view class="act-ic" @click="toggleFav">{{ isFav ? '❤️' : '🤍' }}</view>
      </view>
      <view class="act-add" @click="addCart">加入购物车</view>
      <view class="act-buy" @click="buyNow">立即购买</view>
    </view>

    <award-modal :visible="showAward" :amount="awardAmount" source="加入购物车赠送" @close="showAward = false" />
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
// import { getSpuDetail } from '@/api/product.js';
// import { addCart as apiAddCart } from '@/api/cart.js';

const loading = ref(true);
const spu = ref(null);
const cartCount = ref(0);
const isFav = ref(false);
const showAward = ref(false);
const awardAmount = ref(0);

const route = (() => {
  try {
    const pages = getCurrentPages();
    return pages[pages.length - 1]?.options || {};
  } catch { return {}; }
})();

function goShop() {
  if (spu.value?.shopId) uni.navigateTo({ url: `/pages/shop/home?id=${spu.value.shopId}&tenantId=${spu.value.tenantId}` });
}
function goCart() { uni.navigateTo({ url: '/pages/cart/index' }); }
function toggleFav() { isFav.value = !isFav.value; uni.showToast({ title: isFav.value ? '已收藏' : '取消收藏', icon: 'none' }); }
function addCart() {
  uni.showToast({ title: '已加入购物车', icon: 'success' });
  cartCount.value++;
}
function buyNow() { uni.navigateTo({ url: '/pages/checkout/index' }); }

onMounted(async () => {
  loading.value = true;
  try {
    // spu.value = await getSpuDetail(route.id, route.tenantId);
    spu.value = null;
  } finally { loading.value = false; }
});
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background: $bg-2; padding-bottom: 80px; }
.loading { padding: 40px; text-align: center; color: $t4; }
.cover {
  height: 280px; background: linear-gradient(135deg, $o-50, #fff);
  display: flex; align-items: center; justify-content: center;
  position: relative;
}
.cover-em { font-size: 96px; opacity: .9; }
.promo-tag {
  position: absolute; top: 16px; left: 16px;
  background: linear-gradient(135deg, $o, $o-d); color: #fff;
  padding: 6px 12px; border-radius: $r-pill;
  font-size: 12px; font-weight: 800;
  box-shadow: $sh-warm;
}
.info { background: #fff; padding: 16px; }
.price-row { display: flex; align-items: baseline; gap: 8px; }
.price { font-size: 24px; font-weight: 900; color: $o; }
.original { font-size: 13px; color: $t4; text-decoration: line-through; }
.name { font-size: 16px; font-weight: 800; color: $t1; margin-top: 8px; line-height: 1.4; }
.intro { font-size: 12px; color: $t3; margin-top: 6px; }

.card { background: #fff; margin: 8px 0 0; padding: 14px 16px; }
.card-title { font-size: 14px; font-weight: 800; color: $t1; margin-bottom: 10px; }
.star-card { background: linear-gradient(135deg, $gold-50, #FFF); }
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

.actions {
  position: fixed; left: 0; right: 0; bottom: 0;
  display: flex; align-items: center; gap: 8px;
  padding: 10px 14px;
  padding-bottom: calc(10px + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1px solid $line;
}
.act-side { display: flex; gap: 8px; }
.act-ic {
  width: 44px; height: 44px; border-radius: 50%;
  background: $bg-2; display: flex; align-items: center; justify-content: center;
  font-size: 20px; position: relative;
}
.act-ic .badge {
  position: absolute; top: -2px; right: -2px;
  background: $danger; color: #fff;
  font-size: 10px; padding: 1px 5px; border-radius: 8px;
}
.act-add, .act-buy {
  flex: 1; padding: 12px 0; text-align: center;
  font-size: 14px; font-weight: 800; border-radius: $r-pill;
}
.act-add { background: $gold-50; color: $gold-d; }
.act-buy { background: linear-gradient(135deg, $o, $o-d); color: #fff; box-shadow: $sh-warm; }
</style>
