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

    <!-- ━━━━━━━━━━ 商家 1 列密集卡（美团风：左封面 + 右信息）━━━━━━━━━━ -->
    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!shops.length" icon="🏪" title="附近暂无店铺" desc="换个位置或允许定位试试" />
    <view v-else>
      <view class="result-bar">共找到 <text class="b">{{ shops.length }}</text> 家店</view>
      <view class="shop-list">
        <view v-for="s in shops" :key="s.id || s.tenantId" class="m-card" @click="goShop(s)">
          <!-- 左封面 -->
          <view class="m-cover">
            <image v-if="s.coverUrl" :src="s.coverUrl" mode="aspectFill" class="m-cover-img" />
            <text v-else class="m-cover-em">{{ (s.shopName || s.name || '店')[0] }}</text>
          </view>
          <!-- 右信息密集 -->
          <view class="m-body">
            <view class="m-row1">
              <text class="m-name">{{ s.shopName || s.name }}</text>
              <view class="m-status" :class="{ closed: !s.open }">
                <text class="dot"></text>{{ s.open ? '营业中' : '休息中' }}
              </view>
            </view>
            <view class="m-row2">
              <text v-if="s.rating" class="m-rate">★ {{ s.rating }}</text>
              <text v-if="s.monthSold != null" class="m-sales">月售 {{ s.monthSold }}</text>
              <text v-if="s.distance" class="m-dist">· {{ s.distance }}</text>
              <text v-if="s.star" class="m-star">★{{ s.star }} 星店</text>
            </view>
            <view v-if="s.promoLine || s.tuijianN" class="m-tags">
              <view class="m-tag promo">🔥 {{ s.promoLine || `推 ${s.tuijianN} 免费` }}</view>
            </view>
            <!-- 明星商品 / 销量第一 -->
            <view v-if="s.topSpu" class="m-spu" @click.stop="goSpu(s)">
              <text class="m-spu-tag">⭐ 招牌</text>
              <text class="m-spu-name">{{ s.topSpu.name }}</text>
              <text class="m-spu-price">¥{{ fmtYuan(s.topSpu.price) }}</text>
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
import { fmtDistance, fen2yuan } from '@/utils/format.js';

const fmtYuan = (fen) => fen2yuan(fen, false);

const location = ref('');
const loading = ref(false);
const filter = ref('all');
// 与系统 BUSINESS_CONTEXT_MAP（merchant 端 categories/index.vue）编码一一对应
const filters = [
  { k: 'all',        label: '全部' },
  { k: 'snack',      label: '小吃' },
  { k: 'drink',      label: '奶茶' },
  { k: 'bbq',        label: '烧烤' },
  { k: 'restaurant', label: '餐厅' },
  { k: 'tea_house',  label: '茶馆' },
  { k: 'fruit',      label: '水果' },
  { k: 'super',      label: '超市' },
  { k: 'bakery',     label: '烘焙' },
  { k: 'tea',        label: '茶叶' },
  { k: 'clothing',   label: '服装' },
  { k: 'massage',    label: '按摩' },
  { k: 'beauty',     label: '美容' },
  { k: 'other',      label: '其他' },
];
const shops = ref([]);
const userLng = ref(0);
const userLat = ref(0);

function goSearch() { uni.navigateTo({ url: '/pages/search/index' }); }
function goShop(s) { uni.navigateTo({ url: `/pages/shop/home?id=${s.id || s.tenantId}&tenantId=${s.tenantId || s.id}` }); }
function goSpu(s) {
  if (!s.topSpu?.id) return goShop(s);
  uni.navigateTo({ url: `/pages/product/detail?id=${s.topSpu.id}&tenantId=${s.tenantId || s.id}` });
}
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
      promoLine: s.tuijianN ? `推 ${s.tuijianN} 免费 进行中` : '',
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

/* ━━ Shop list（美团风：1 列密集卡，左封面 + 右信息）━━ */
.shop-list { padding: 0 12px; }
.m-card {
  display: flex; gap: 12px;
  padding: 12px;
  margin-bottom: 10px;
  background: $card;
  border-radius: $r-lg;
  border: 1px solid $line;
  box-shadow: 0 1px 2px rgba(15,23,42,.04), 0 4px 12px rgba(15,23,42,.04);
  transition: transform .15s ease;
}
.m-card:active { transform: scale(.99); }

/* 左封面：正方形 ~92px */
.m-cover {
  width: 92px; height: 92px;
  flex-shrink: 0;
  border-radius: $r-md;
  overflow: hidden;
  background: linear-gradient(135deg, #FFF5EB, #FFE9D5);
  display: flex; align-items: center; justify-content: center;
  position: relative;
}
.m-cover-img { width: 100%; height: 100%; }
.m-cover-em {
  font-size: 36px; font-weight: 800;
  color: $o-d;
  text-shadow: 0 2px 4px rgba(255,107,53,.15);
  letter-spacing: -1px;
}

/* 右信息密集 */
.m-body {
  flex: 1; min-width: 0;
  display: flex; flex-direction: column; gap: 6px;
}
.m-row1 {
  display: flex; align-items: center; gap: 8px;
}
.m-name {
  flex: 1; min-width: 0;
  font-size: 15px; font-weight: 800; color: $t1;
  letter-spacing: -.3px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.m-status {
  flex-shrink: 0;
  display: inline-flex; align-items: center; gap: 4px;
  padding: 2px 7px; border-radius: 99px;
  background: rgba(16,185,129,.95);
  color: #fff; font-size: 10px; font-weight: 800;
}
.m-status .dot {
  width: 4px; height: 4px; border-radius: 50%;
  background: #fff;
  animation: m-pulse 1.8s ease-in-out infinite;
}
.m-status.closed { background: rgba(100,116,139,.9); }
.m-status.closed .dot { animation: none; }
@keyframes m-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: .5; transform: scale(.7); }
}

.m-row2 {
  display: flex; align-items: center; flex-wrap: wrap; gap: 8px;
  font-size: 12px; color: $t3;
  font-variant-numeric: tabular-nums;
}
.m-rate { color: $gold-d; font-weight: 800; }
.m-sales { color: $t3; }
.m-dist { color: $t4; }
.m-star {
  margin-left: auto;
  font-size: 10.5px;
  padding: 2px 7px;
  background: linear-gradient(135deg, $gold-50, $gold-l);
  color: $gold-d;
  border-radius: 4px;
  font-weight: 800;
}

.m-tags { display: flex; gap: 6px; flex-wrap: wrap; }
.m-tag {
  display: inline-block;
  padding: 3px 8px;
  font-size: 11px; font-weight: 700;
  border-radius: 4px;
}
.m-tag.promo {
  background: linear-gradient(135deg, #FFF1EB, #FFE0D1);
  color: $o-d;
  border: 1px solid $o-100;
}

/* 明星商品 / 招牌 */
.m-spu {
  display: flex; align-items: center; gap: 6px;
  margin-top: 2px;
  padding: 5px 8px;
  background: linear-gradient(135deg, $gold-50, #FEF3C7);
  border-radius: 6px;
  border: 1px dashed $gold-l;
  overflow: hidden;
}
.m-spu-tag {
  flex-shrink: 0;
  font-size: 10px; font-weight: 800;
  color: $gold-d;
}
.m-spu-name {
  flex: 1; min-width: 0;
  font-size: 11.5px; font-weight: 700; color: $t1;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.m-spu-price {
  flex-shrink: 0;
  font-size: 12px; font-weight: 800; color: $o-d;
  font-variant-numeric: tabular-nums;
}

.loading { padding: 40px; text-align: center; color: $t4; }
.bottom-pad { height: 20px; }
</style>
