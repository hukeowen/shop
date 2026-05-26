<template>
  <view class="page">
    <nav-bar title="附近店铺" bg="#0F0B07" txt="#fff" />
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
      <scroll-view scroll-x class="filter-scroll">
        <view v-for="f in filters" :key="f.k" class="f-tag" :class="{ on: filter === f.k }" @click="setFilter(f.k)">{{ f.label }}</view>
      </scroll-view>
    </view>

    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!shops.length" icon="🏪" title="附近暂无店铺" />
    <view v-else>
      <view v-for="s in shops" :key="s.id || s.tenantId" class="shop-card" @click="goShop(s)">
        <view class="shop-head">
          <view class="shop-pic">
            <image v-if="s.shopLogo" :src="s.shopLogo" class="pic-img" mode="aspectFill" />
            <text v-else>{{ (s.shopName || s.name || '店')[0] }}</text>
          </view>
          <view class="shop-info">
            <view class="shop-row1">
              <text class="shop-name">{{ s.shopName || s.name }}</text>
              <text v-if="s.businessStatus === 0" class="closed">已打烊</text>
            </view>
            <view class="shop-meta">
              <text v-if="s.rating" class="rating">★ {{ s.rating }}</text>
              <text v-if="s.distance != null" class="dist">📍 {{ distText(s.distance) }}</text>
              <text v-if="s.monthSold != null">月售 {{ s.monthSold }}</text>
            </view>
          </view>
        </view>
      </view>
    </view>
    <view class="bottom-pad"></view>
  </view>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue';
import { listShops } from '@/api/shop.js';

const location = ref('');
const loading = ref(false);
const filter = ref('all');
const filters = [
  { k: 'all',   label: '全部' },
  { k: 'food',  label: '餐饮' },
  { k: 'tea',   label: '茶饮' },
  { k: 'fresh', label: '生鲜' },
];
const shops = ref([]);
const userLng = ref(0);
const userLat = ref(0);

function distText(m) {
  if (m == null) return '';
  return m < 1000 ? `${Math.round(m)}m` : `${(m / 1000).toFixed(1)}km`;
}
function goSearch() { uni.navigateTo({ url: '/pages/search/index' }); }
function goShop(s) { uni.navigateTo({ url: `/pages/shop/home?id=${s.id || s.tenantId}&tenantId=${s.tenantId || s.id}` }); }
function setFilter(k) { filter.value = k; load(); }

function onRelocate() {
  // 浏览器 / 拒绝授权时 uni.getLocation 可能 success/fail 都不 fire；
  // 设个 timeout，5s 内没回话就走 fail 路径
  location.value = '正在定位…';
  let settled = false;
  const timer = setTimeout(() => {
    if (settled) return;
    settled = true;
    location.value = '定位失败 / 未授权';
    load();
  }, 5000);
  uni.getLocation({
    type: 'gcj02',
    success: (r) => {
      if (settled) return; settled = true; clearTimeout(timer);
      userLng.value = r.longitude; userLat.value = r.latitude;
      location.value = `${r.latitude.toFixed(4)}, ${r.longitude.toFixed(4)}`;
      load();
    },
    fail: () => {
      if (settled) return; settled = true; clearTimeout(timer);
      location.value = '定位失败 / 未授权';
      load();
    },
  });
}
async function load() {
  loading.value = true;
  try {
    const params = { pageNo: 1, pageSize: 50 };
    if (userLng.value && userLat.value) {
      params.userLng = userLng.value;
      params.userLat = userLat.value;
    }
    if (filter.value !== 'all') params.businessType = filter.value;
    const r = await listShops(params);
    shops.value = r?.list || r || [];
  } catch { shops.value = []; }
  finally { loading.value = false; }
}
onMounted(() => {
  // 不等定位，立即 load 一次保证不会卡住；定位回话后会再 load 一次带距离
  load();
  onRelocate();
});
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; padding-bottom: 30px; background: $bg; }
.hero { padding: 12px 14px; background: linear-gradient(180deg, #18130E 0%, #2A1A0F 100%); border-bottom-left-radius: 24px; border-bottom-right-radius: 24px; }
.loc-row { display: flex; align-items: center; gap: 6px; padding: 6px 4px; }
.loc-ic { color: $o; }
.loc-name { flex: 1; color: #fff; font-size: 13px; font-weight: 700; }
.loc-act { color: $o-l; font-size: 12px; }
.search { margin-top: 10px; display: flex; align-items: center; gap: 8px; background: rgba(255,255,255,.96); border-radius: 14px; padding: 10px 14px; }
.search .ic { color: $o; }
.search .ph { flex: 1; color: $t4; font-size: 13px; }
.filter-scroll { white-space: nowrap; margin-top: 12px; }
.f-tag { display: inline-block; padding: 6px 12px; border-radius: $r-pill; background: rgba(255,255,255,.08); color: rgba(255,255,255,.7); font-size: 11px; font-weight: 600; margin-right: 6px; }
.f-tag.on { background: $o; color: #fff; box-shadow: $sh-warm; }
.loading { padding: 30px; text-align: center; color: $t4; }
.shop-card { margin: 10px 14px 0; padding: 12px; background: $card; border-radius: $r-lg; box-shadow: $sh-1; }
.shop-head { display: flex; gap: 12px; }
.shop-pic { width: 50px; height: 50px; border-radius: 12px; background: linear-gradient(135deg, $o, $o-d); color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 18px; overflow: hidden; flex-shrink: 0; }
.pic-img { width: 100%; height: 100%; }
.shop-info { flex: 1; min-width: 0; }
.shop-row1 { display: flex; align-items: center; gap: 6px; }
.shop-name { font-size: 14px; font-weight: 800; color: $t1; flex: 1; }
.closed { font-size: 10px; color: #fff; background: $t4; padding: 1px 6px; border-radius: 4px; }
.shop-meta { display: flex; gap: 8px; margin-top: 6px; font-size: 11px; color: $t3; }
.rating { color: $gold-d; font-weight: 700; }
.bottom-pad { height: 30px; }
</style>
