<template>
  <view class="page">
    <nav-bar title="附近店铺" :back="false" bg="#0F0B07" txt="#fff" />
    <view class="hero">
      <view class="loc-row">
        <text class="loc-ic">📍</text>
        <text class="loc-name">{{ location || '正在定位…' }}</text>
        <text class="loc-act" @click="onRelocate">重定位</text>
      </view>
      <view class="search" @click="goSearch">
        <text class="ic">🔍</text>
        <text class="ph">搜店铺名 / 商品名</text>
      </view>
      <view class="filter">
        <view v-for="f in filters" :key="f.k" class="f-tag" :class="{ on: filter === f.k }" @click="filter = f.k">{{ f.label }}</view>
      </view>
    </view>

    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!shops.length" icon="🏪" title="附近暂无店铺" />
    <view v-else>
      <view v-for="s in shops" :key="s.id" class="shop-card" @click="goShop(s)">
        <view class="shop-head">
          <view class="shop-pic">{{ s.name?.[0] || '店' }}</view>
          <view class="shop-info">
            <view class="shop-row1"><text class="shop-name">{{ s.name }}</text></view>
            <view class="shop-meta">
              <text class="rating">★ {{ s.rating || '4.8' }}</text>
              <text class="dist">📍 {{ s.distance || '—' }}</text>
              <text>月售 {{ s.monthSold || '—' }}</text>
            </view>
          </view>
        </view>
      </view>
    </view>
    <view class="bottom-pad"></view>
    <bottom-nav active="nearby" />
  </view>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue';
// import { listNearbyShops } from '@/api/shop.js';

const location = ref('');
const loading = ref(false);
const filter = ref('all');
const filters = [
  { k: 'all',   label: '全部' },
  { k: 'food',  label: '餐饮' },
  { k: 'tea',   label: '茶饮' },
  { k: 'fresh', label: '生鲜' },
  { k: 'star',  label: '推 N 反 1' },
  { k: 'award', label: '派奖中' },
];
const shops = ref([]);

function goSearch() { uni.navigateTo({ url: '/pages/search/index' }); }
function goShop(s) { uni.navigateTo({ url: `/pages/shop/home?id=${s.id}&tenantId=${s.tenantId || s.id}` }); }
function onRelocate() {
  uni.getLocation({
    type: 'gcj02',
    success: (r) => { location.value = `${r.latitude.toFixed(4)}, ${r.longitude.toFixed(4)}`; load(); },
    fail: () => { location.value = '定位失败'; },
  });
}
async function load() {
  loading.value = true;
  try {
    // shops.value = await listNearbyShops({ filter: filter.value });
    shops.value = [];
  } finally {
    loading.value = false;
  }
}
watch(filter, load);
onMounted(() => { onRelocate(); load(); });
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; padding-bottom: 90px; background: $bg; }
.hero {
  padding: 12px 14px;
  background: linear-gradient(180deg, #18130E 0%, #2A1A0F 100%);
  border-bottom-left-radius: 24px; border-bottom-right-radius: 24px;
}
.loc-row { display: flex; align-items: center; gap: 6px; padding: 6px 4px; }
.loc-ic { color: $o; }
.loc-name { flex: 1; color: #fff; font-size: 13px; font-weight: 700; }
.loc-act { color: $o-l; font-size: 12px; }

.search {
  margin-top: 10px;
  display: flex; align-items: center; gap: 8px;
  background: rgba(255,255,255,.96); border-radius: 14px;
  padding: 10px 14px;
}
.search .ic { color: $o; }
.search .ph { flex: 1; color: $t4; font-size: 13px; }

.filter {
  display: flex; flex-wrap: wrap; gap: 6px; margin-top: 12px;
}
.f-tag {
  padding: 6px 12px; border-radius: $r-pill;
  background: rgba(255,255,255,.08); color: rgba(255,255,255,.7);
  font-size: 11px; font-weight: 600;
  &.on { background: $o; color: #fff; box-shadow: $sh-warm; }
}

.loading { padding: 30px; text-align: center; color: $t4; }

.shop-card {
  margin: 10px 14px 0; padding: 12px;
  background: $card; border-radius: $r-lg; box-shadow: $sh-1;
}
.shop-head { display: flex; gap: 12px; }
.shop-pic {
  width: 50px; height: 50px; border-radius: 12px;
  background: linear-gradient(135deg, $o, $o-d); color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-weight: 800; font-size: 18px;
}
.shop-info { flex: 1; }
.shop-name { font-size: 14px; font-weight: 800; color: $t1; }
.shop-meta { display: flex; gap: 8px; margin-top: 6px; font-size: 11px; color: $t3; }
.rating { color: $gold-d; font-weight: 700; }
.bottom-pad { height: 30px; }
</style>
