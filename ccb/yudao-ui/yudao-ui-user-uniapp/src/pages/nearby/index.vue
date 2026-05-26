<template>
  <view class="page">
    <nav-bar title="附近店铺" />

    <!-- ━━━━━━━━━━ Hero：定位 + 搜索 + 筛选 ━━━━━━━━━━ -->
    <view class="hero">
      <view class="hero-bg"></view>
      <view class="loc-row">
        <text class="loc-ic">📍</text>
        <text class="loc-name">{{ location || '正在定位…' }}</text>
        <view class="loc-act" @click="onRelocate">重定位</view>
      </view>
      <view class="search" @click="goSearch">
        <text class="ic">🔍</text>
        <text class="ph">搜店铺名 / 商品名</text>
      </view>
      <scroll-view scroll-x class="filter-scroll">
        <view v-for="f in filters" :key="f.k" class="f-tag" :class="{ on: filter === f.k }" @click="setFilter(f.k)">{{ f.label }}</view>
      </scroll-view>
    </view>

    <!-- ━━━━━━━━━━ 商家 2 列 grid（与首页统一）━━━━━━━━━━ -->
    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!shops.length" icon="🏪" title="附近暂无店铺" desc="换个位置或允许定位试试" />
    <view v-else>
      <view class="result-bar">共找到 <text class="b">{{ shops.length }}</text> 家店</view>
      <view class="shop-grid">
        <view v-for="(s, i) in shops" :key="s.id || s.tenantId" class="shop-card-g" @click="goShop(s)">
          <view class="shop-cover" :class="['', 'alt-1', 'alt-2'][i % 3]">
            <image v-if="s.shopLogo" :src="s.shopLogo" mode="aspectFill" class="cover-img" />
            <text v-else class="cover-em">{{ (s.shopName || s.name || '店')[0] }}</text>
            <view class="shop-status-mini" :class="{ closed: !s.open }">
              <text class="dot"></text>{{ s.open ? '营业中' : '休息中' }}
            </view>
            <view v-if="s.star" class="cover-star">★{{ s.star }}</view>
          </view>
          <view class="card-body">
            <view class="card-name">{{ s.shopName || s.name }}</view>
            <view v-if="s.promoLine" class="card-promo">{{ s.promoLine }}</view>
            <view class="card-meta">
              <text v-if="s.rating" class="rating">★ {{ s.rating }}</text>
              <text v-if="s.monthSold != null">月售 {{ s.monthSold }}</text>
              <text v-if="s.distance">· {{ s.distance }}</text>
            </view>
          </view>
        </view>
      </view>
    </view>
    <view class="bottom-pad"></view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { listShops } from '@/api/shop.js';
import { fmtDistance } from '@/utils/format.js';

const location = ref('');
const loading = ref(false);
const filter = ref('all');
const filters = [
  { k: 'all',    label: '全部' },
  { k: 'food',   label: '餐饮' },
  { k: 'tea',    label: '茶饮' },
  { k: 'bake',   label: '烘焙' },
  { k: 'fresh',  label: '生鲜' },
  { k: 'beauty', label: '美容' },
  { k: 'super',  label: '超市' },
];
const shops = ref([]);
const userLng = ref(0);
const userLat = ref(0);

function goSearch() { uni.navigateTo({ url: '/pages/search/index' }); }
function goShop(s) { uni.navigateTo({ url: `/pages/shop/home?id=${s.id || s.tenantId}&tenantId=${s.tenantId || s.id}` }); }
function setFilter(k) { filter.value = k; load(); }

function onRelocate() {
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
    const items = r?.list || r || [];
    shops.value = items.map((s) => ({
      ...s,
      open: s.isOpenNow === true,
      star: s.starLevel || s.star,
      rating: s.avgRating,
      monthSold: s.sales30d,
      distance: s.distance != null && s.distance > 0 ? fmtDistance(s.distance) : '',
      promoLine: s.tuijianN ? `推 ${s.tuijianN} 反 1 进行中` : '',
    }));
  } catch { shops.value = []; }
  finally { loading.value = false; }
}

onMounted(() => {
  // 支持 URL bt= 参数从首页带筛选过来（首页"餐饮/茶饮…"按钮跳进来时自动选中对应 tab）
  try {
    const opts = getCurrentPages().slice(-1)[0]?.options || {};
    if (opts.bt && filters.find((f) => f.k === opts.bt)) filter.value = opts.bt;
  } catch {}
  load();
  onRelocate();
});
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.page { min-height: 100vh; padding-bottom: 30px; background: $bg-2; }

/* ━━ Hero ━━ */
.hero {
  position: relative;
  padding: 14px 14px 16px;
  background:
    radial-gradient(500px 250px at 0% 0%, rgba(212,146,10,.35), transparent 60%),
    linear-gradient(180deg, #18130E 0%, #2A1A0F 100%);
  color: #fff;
}
.hero-bg {
  position: absolute; inset: 0;
  background-image: radial-gradient(rgba(255,255,255,.05) 1px, transparent 1px);
  background-size: 22px 22px;
  pointer-events: none;
}
.loc-row {
  display: flex; align-items: center; gap: 8px; padding: 4px 4px 0;
  position: relative;
}
.loc-ic { font-size: 14px; }
.loc-name { flex: 1; font-size: 13px; font-weight: 700; color: rgba(255,255,255,.95); }
.loc-act {
  padding: 5px 12px; border-radius: 99px;
  background: rgba(255,255,255,.12);
  font-size: 11.5px; font-weight: 700;
  color: $gold-l;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255,255,255,.15);
}

.search {
  margin-top: 12px;
  display: flex; align-items: center; gap: 8px;
  background: rgba(255,255,255,.96);
  border-radius: 99px;
  padding: 10px 16px;
  position: relative;
  box-shadow: 0 4px 12px rgba(0,0,0,.15);
}
.search .ic { color: $o; font-size: 14px; }
.search .ph { flex: 1; color: $t4; font-size: 13px; }

.filter-scroll {
  margin-top: 12px;
  white-space: nowrap;
  position: relative;
}
.f-tag {
  display: inline-block;
  padding: 6px 14px; border-radius: 99px;
  background: rgba(255,255,255,.1);
  color: rgba(255,255,255,.75);
  font-size: 11.5px; font-weight: 700;
  margin-right: 6px;
  border: 1px solid rgba(255,255,255,.12);
  backdrop-filter: blur(10px);
}
.f-tag.on {
  background: linear-gradient(135deg, $o, $o-d);
  color: #fff;
  border-color: transparent;
  box-shadow: $sh-warm;
}

/* ━━ 结果条 ━━ */
.result-bar {
  margin: 14px 14px 8px;
  font-size: 12px; color: $t3;
}
.result-bar .b { color: $o-d; font-weight: 800; margin: 0 2px; }

/* ━━ Shop grid（与首页 shop-grid 一致）━━ */
.shop-grid {
  padding: 0 14px;
  display: grid; grid-template-columns: 1fr 1fr; gap: 10px;
}
.shop-card-g {
  background: $card;
  border-radius: $r-lg;
  border: 1px solid $line;
  box-shadow: 0 1px 2px rgba(15,23,42,.04), 0 4px 12px rgba(15,23,42,.05);
  overflow: hidden;
  transition: transform .15s ease;
}
.shop-card-g:active { transform: scale(.98); }
.shop-cover {
  height: 96px;
  position: relative;
  background: linear-gradient(135deg, #FFD1BA, $o);
  display: flex; align-items: center; justify-content: center;
  overflow: hidden;
}
.shop-cover.alt-1 { background: linear-gradient(135deg, #C9E0FF, #6196F0); }
.shop-cover.alt-2 { background: linear-gradient(135deg, #D3F4D3, #4CB84C); }
.cover-img { width: 100%; height: 100%; }
.cover-em {
  font-size: 42px; font-weight: 800; color: #fff;
  text-shadow: 0 2px 8px rgba(0,0,0,.15);
}
.shop-status-mini {
  position: absolute; top: 8px; right: 8px;
  display: inline-flex; align-items: center; gap: 4px;
  padding: 3px 8px; border-radius: 99px;
  background: rgba(16,185,129,.95);
  color: #fff; font-size: 9.5px; font-weight: 800;
  backdrop-filter: blur(8px);
}
.shop-status-mini .dot {
  width: 5px; height: 5px; border-radius: 50%;
  background: #fff;
  animation: shop-pulse 1.8s ease-in-out infinite;
}
.shop-status-mini.closed { background: rgba(100,116,139,.9); }
.shop-status-mini.closed .dot { animation: none; }
@keyframes shop-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: .5; transform: scale(.7); }
}
.cover-star {
  position: absolute; top: 8px; left: 8px;
  padding: 3px 8px; border-radius: 99px;
  background: linear-gradient(135deg, $gold, $gold-d);
  color: #fff; font-size: 10px; font-weight: 800;
  box-shadow: 0 2px 8px rgba(212,146,10,.4);
}
.card-body { padding: 10px 12px 12px; }
.card-name {
  font-size: 14px; font-weight: 800; color: $t1;
  letter-spacing: -.3px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.card-promo {
  margin-top: 6px;
  display: inline-block;
  padding: 2px 7px;
  background: linear-gradient(135deg, $o-50, $gold-50);
  color: $o-d;
  font-size: 10px; font-weight: 700;
  border-radius: 4px;
  border: 1px solid $o-100;
}
.card-meta {
  margin-top: 6px;
  display: flex; align-items: center; gap: 8px;
  font-size: 11px; color: $t3;
}
.card-meta .rating { color: $gold-d; font-weight: 700; }

.loading { padding: 40px; text-align: center; color: $t4; }
.bottom-pad { height: 20px; }
</style>
