<template>
  <view class="page">
    <!-- 顶部欢迎条 + 搜索 -->
    <view class="home-top safe-top">
      <view class="home-greeting">{{ greeting }} ☀</view>
      <view class="home-name">想吃点什么？</view>
      <view class="home-search">
        <text class="icon">🔍</text>
        <input class="kw" v-model="kw" placeholder="搜店铺、找商品、看附近" confirm-type="search" @confirm="onSearch" />
        <text class="voice">🎙</text>
      </view>
    </view>

    <!-- 3 个快捷入口（去掉扫码） -->
    <view class="home-quick">
      <view class="qk" @click="goNearby">
        <view class="qk-icon i1">📍</view>
        <text class="qk-text">附近</text>
      </view>
      <view class="qk" @click="goCategories">
        <view class="qk-icon i2">🛍</view>
        <text class="qk-text">分类</text>
      </view>
      <view class="qk" @click="goCoupons">
        <view class="qk-icon i3">🎁</view>
        <text class="qk-text">优惠</text>
      </view>
    </view>

    <!-- 最近去过 -->
    <view class="section-title">
      <text class="ttl">最近去过</text>
      <text class="more" @click="goMyShops">全部 ›</text>
    </view>
    <scroll-view scroll-x class="recent-scroll" v-if="recentShops.length">
      <view
        v-for="s in recentShops"
        :key="s.tenantId"
        class="recent-card"
        @click="goShop(s.tenantId)"
      >
        <view class="recent-cover" :style="coverStyle(s)">
          <text class="recent-tag">{{ formatTime(s.lastVisitAt) }}</text>
        </view>
        <view class="recent-body">
          <view class="recent-name">{{ s.shopName || '未命名店铺' }}</view>
          <view class="recent-meta">
            <text v-if="s.points">{{ s.points }} 积分</text>
            <text v-if="s.balance" class="dot">·</text>
            <text v-if="s.balance">¥{{ fen2yuan(s.balance) }} 余额</text>
          </view>
        </view>
      </view>
    </scroll-view>
    <view v-else-if="!loading && !recentShops.length" class="empty-tip">还没去过任何店铺，去看看附近吧</view>

    <!-- 平台 banner -->
    <view class="banner" @click="goAiVideos">
      <view class="banner-icon">🎬</view>
      <view class="banner-body">
        <view class="banner-title">商家 AI 一键成片</view>
        <view class="banner-sub">看看附近商家用 AI 拍的短视频，找新店</view>
      </view>
      <text class="banner-arrow">›</text>
    </view>

    <!-- 附近商家 -->
    <view class="section-title">
      <text class="ttl">附近商家</text>
      <text class="more" @click="toggleSort">{{ sortLabel }} ↓</text>
    </view>
    <view v-if="loading && !nearbyShops.length" class="empty-tip">加载中...</view>
    <view v-else-if="!nearbyShops.length" class="empty-tip">附近暂无店铺</view>
    <view v-else>
      <view
        v-for="s in nearbyShops"
        :key="s.tenantId || s.id"
        class="shop"
        @click="goShop(s.tenantId)"
      >
        <view class="shop-pic" :style="shopPicStyle(s)">
          {{ (s.shopName || s.name || '店')[0] }}
        </view>
        <view class="shop-info">
          <view class="shop-row1">
            <text class="shop-name">{{ s.shopName || s.name }}</text>
            <text v-if="s.bizTag" class="shop-badge">{{ s.bizTag }}</text>
          </view>
          <view class="shop-tag-line">{{ s.tagLine || s.address || s.businessHours || '欢迎光临' }}</view>
          <view class="shop-meta">
            <text v-if="s.avgRating" class="rating">★ {{ formatRating(s.avgRating) }}</text>
            <text v-if="s.distance" class="distance">📍 {{ formatDistance(s.distance) }}</text>
            <text v-if="s.sales30d != null" class="sales">月售 {{ s.sales30d }}</text>
          </view>
        </view>
      </view>
    </view>

    <view class="bottom-space" />

    <RoleTabBar current="/pages/user-home/index" force-role="member" />
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app';
import { request } from '../../api/request.js';
import { fen2yuan } from '../../utils/format.js';
import { useUserStore } from '../../store/user.js';
import { flushPendingReferrer } from '../../utils/referral.js';

const userStore = useUserStore();
const kw = ref('');
const sortMode = ref('distance'); // distance | sales
const sortLabel = computed(() => sortMode.value === 'distance' ? '距离' : '销量');
const recentShops = ref([]);
const nearbyShops = ref([]);
const loading = ref(false);

const greeting = computed(() => {
  const h = new Date().getHours();
  if (h < 6) return '夜深了';
  if (h < 11) return '早上好';
  if (h < 13) return '中午好';
  if (h < 18) return '下午好';
  return '晚上好';
});

const coverStyle = (s) => {
  const palette = ['#ffe1c8,#ffae74', '#d6e9ff,#80b3ff', '#d8f5d6,#6fcf6f', '#ffd6e0,#ff8aa7'];
  const idx = (Number(s.tenantId) || 0) % palette.length;
  return `background: linear-gradient(135deg, ${palette[idx]});`;
};
const shopPicStyle = (s) => {
  const palette = ['#ffd1ba,#ff6b35', '#c9e0ff,#6196f0', '#d3f4d3,#4cb84c', '#ffd0dc,#ee5a8b'];
  const idx = (Number(s.tenantId || s.id) || 0) % palette.length;
  return `background: linear-gradient(135deg, ${palette[idx]});`;
};

function formatTime(t) {
  if (!t) return '';
  const d = new Date(typeof t === 'string' ? t.replace(' ', 'T') : t);
  if (isNaN(d.getTime())) return '';
  const diffMs = Date.now() - d.getTime();
  const min = Math.floor(diffMs / 60000);
  if (min < 60) return `${Math.max(1, min)} 分钟前`;
  const h = Math.floor(min / 60);
  if (h < 24) return `${h} 小时前`;
  const day = Math.floor(h / 24);
  if (day < 7) return `${day} 天前`;
  return `${Math.floor(day / 7)} 周前`;
}
function formatDistance(d) {
  if (d == null) return '';
  if (d < 1000) return `${Math.round(d)}m`;
  return `${(d / 1000).toFixed(1)}km`;
}
function formatRating(r) {
  if (r == null) return '';
  return Number(r).toFixed(1);
}

async function loadRecent() {
  if (!userStore.token) {
    recentShops.value = [];
    return;
  }
  try {
    const list = await request({ url: '/app-api/merchant/mini/member-rel/my-shops-enriched' });
    // 按 lastVisitAt 倒序，取前 6
    const sorted = (list || []).slice().sort((a, b) => {
      const ta = a.lastVisitAt ? new Date(a.lastVisitAt).getTime() : 0;
      const tb = b.lastVisitAt ? new Date(b.lastVisitAt).getTime() : 0;
      return tb - ta;
    });
    recentShops.value = sorted.slice(0, 6);
  } catch {
    recentShops.value = [];
  }
}

async function loadNearby() {
  loading.value = true;
  try {
    const params = { pageNo: 1, pageSize: 20 };
    if (sortMode.value === 'distance') params.sortBy = 'distance';
    else params.sortBy = 'sales';
    const res = await request({
      url: `/app-api/merchant/shop/public/list?pageNo=${params.pageNo}&pageSize=${params.pageSize}&sortBy=${params.sortBy}`,
    });
    nearbyShops.value = (res && res.list) ? res.list : (Array.isArray(res) ? res : []);
  } catch {
    nearbyShops.value = [];
  } finally {
    loading.value = false;
  }
}

function toggleSort() {
  sortMode.value = sortMode.value === 'distance' ? 'sales' : 'distance';
  loadNearby();
}

function onSearch() {
  if (!kw.value.trim()) return;
  uni.navigateTo({ url: `/pages/nearby/index?kw=${encodeURIComponent(kw.value.trim())}` });
}

function goShop(tenantId) {
  if (!tenantId) return;
  uni.navigateTo({ url: `/pages/shop-home/index?tenantId=${tenantId}` });
}
function goNearby() { uni.navigateTo({ url: '/pages/nearby/index' }); }
// 分类 = 跳到附近店铺页（暂时只看附近店铺；后续 nearby 页内可加 category 切换）
function goCategories() { uni.navigateTo({ url: '/pages/nearby/index' }); }
// 优惠 = 跳到「我的优惠券」聚合页（跨店看所有领过的券）
function goCoupons() { uni.navigateTo({ url: '/pages/user-me/coupons' }); }
function goAiVideos() { uni.showToast({ title: 'AI 短视频聚合页开发中', icon: 'none' }); }
function goMyShops() { uni.navigateTo({ url: '/pages/user-me/index' }); }
// 用户端三大 tab 切换：pages.json 已无全局 tabBar，switchTab 在 H5 静默失败
// （即使 ?. 调用也是异步返 undefined，|| 短路到 navigateTo 但 H5 hash 路由
// 某些场景下 navigateTo 也不响应）。直接用 reLaunch 重置到目标页最稳。
function goOrder() { uni.reLaunch({ url: '/pages/user-order/list' }); }
function goMe() { uni.reLaunch({ url: '/pages/user-me/index' }); }

onMounted(() => {
  if (userStore.userId) flushPendingReferrer(userStore.userId);
  loadRecent();
  loadNearby();
});
onShow(() => { loadRecent(); });
onPullDownRefresh(async () => {
  await Promise.all([loadRecent(), loadNearby()]);
  uni.stopPullDownRefresh();
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  min-height: 100vh;
  background: $bg-page;
  padding-bottom: 160rpx;
}
.safe-top { padding-top: calc(env(safe-area-inset-top) + 24rpx); }
.safe-bottom { padding-bottom: env(safe-area-inset-bottom); }

.home-top {
  background: linear-gradient(135deg, #ffd1ba 0%, #ff9a4a 60%, $brand-primary 100%);
  padding: 24rpx 32rpx 56rpx;
  color: #fff;
}
.home-greeting { font-size: 26rpx; opacity: .9; }
.home-name { font-size: 44rpx; font-weight: 800; margin-top: 4rpx; }
.home-search {
  margin-top: 28rpx; background: rgba(255,255,255,.95);
  border-radius: 999rpx; padding: 16rpx 24rpx;
  display: flex; align-items: center; gap: 12rpx;
  box-shadow: 0 8rpx 28rpx rgba(255,107,53,.18);
}
.home-search .icon { font-size: 28rpx; color: $text-placeholder; }
.home-search .kw { flex: 1; font-size: 28rpx; color: $text-primary; }
.home-search .voice {
  width: 56rpx; height: 56rpx; line-height: 56rpx; text-align: center;
  border-radius: 50%; background: $brand-primary-light;
  color: $brand-primary; font-size: 28rpx;
}

.home-quick {
  margin: -24rpx 32rpx 0;
  padding: 24rpx 16rpx;
  background: $bg-card;
  border-radius: $radius-lg;
  box-shadow: 0 4rpx 20rpx rgba(15,23,42,.04);
  display: grid; grid-template-columns: repeat(3, 1fr);
  position: relative; z-index: 2;
}
.qk { text-align: center; padding: 12rpx 0; }
.qk-icon {
  width: 80rpx; height: 80rpx; margin: 0 auto 8rpx;
  border-radius: $radius-md; line-height: 80rpx; text-align: center;
  font-size: 40rpx;
  background: $brand-primary-light; color: $brand-primary;
}
.qk-icon.i2 { background: rgba(16,185,129,.12); color: #10B981; }
.qk-icon.i3 { background: rgba(99,102,241,.12); color: #6366F1; }
.qk-icon.i4 { background: rgba(245,158,11,.14); color: #F59E0B; }
.qk-text { font-size: 24rpx; color: $text-primary; }

.section-title {
  display: flex; justify-content: space-between; align-items: center;
  margin: 32rpx 32rpx 16rpx;
}
.section-title .ttl { font-size: 32rpx; font-weight: 700; color: $text-primary; }
.section-title .more { font-size: 24rpx; color: $text-placeholder; }

.recent-scroll {
  white-space: nowrap; padding: 0 24rpx 16rpx;
}
.recent-card {
  display: inline-block; vertical-align: top;
  width: 336rpx; margin-right: 20rpx;
  background: $bg-card; border-radius: $radius-md;
  box-shadow: 0 4rpx 16rpx rgba(15,23,42,.04);
  overflow: hidden;
}
.recent-cover {
  height: 184rpx; padding: 16rpx;
  display: flex; align-items: flex-end;
}
.recent-tag {
  background: rgba(0,0,0,.28); color: #fff;
  border-radius: 999rpx; padding: 4rpx 16rpx; font-size: 22rpx;
}
.recent-body { padding: 16rpx 20rpx; }
.recent-name {
  font-size: 26rpx; font-weight: 600; color: $text-primary;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.recent-meta {
  margin-top: 8rpx; font-size: 22rpx; color: $text-placeholder;
  display: flex; align-items: center; gap: 8rpx;
}
.recent-meta .dot { color: $text-placeholder; }

.banner {
  margin: 24rpx 32rpx;
  background: linear-gradient(135deg, #ff9a4a, $brand-primary-dark);
  border-radius: $radius-lg;
  padding: 32rpx; color: #fff;
  display: flex; align-items: center; gap: 24rpx;
  box-shadow: 0 12rpx 32rpx rgba(255,107,53,.30);
}
.banner-icon {
  width: 96rpx; height: 96rpx; border-radius: $radius-md;
  background: rgba(255,255,255,.18);
  display: flex; align-items: center; justify-content: center;
  font-size: 48rpx;
}
.banner-body { flex: 1; }
.banner-title { font-size: 30rpx; font-weight: 700; }
.banner-sub { margin-top: 8rpx; font-size: 24rpx; opacity: .9; }
.banner-arrow { font-size: 36rpx; }

.shop {
  margin: 0 32rpx 20rpx; padding: 28rpx 32rpx;
  background: $bg-card; border-radius: $radius-lg;
  box-shadow: 0 4rpx 16rpx rgba(15,23,42,.04);
  display: flex; gap: 24rpx;
}
.shop-pic {
  width: 152rpx; height: 152rpx; border-radius: $radius-md;
  color: #fff; font-size: 56rpx; font-weight: 800;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.shop-info { flex: 1; min-width: 0; }
.shop-row1 { display: flex; align-items: center; gap: 12rpx; }
.shop-name {
  font-size: 30rpx; font-weight: 700; color: $text-primary; flex: 1;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.shop-badge {
  background: $brand-primary-light; color: $brand-primary;
  font-size: 20rpx; padding: 4rpx 14rpx; border-radius: 999rpx;
  flex-shrink: 0;
}
.shop-tag-line {
  margin-top: 8rpx; font-size: 24rpx; color: $text-secondary;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.shop-meta {
  margin-top: 16rpx; display: flex; gap: 20rpx;
  font-size: 22rpx; color: $text-placeholder;
}
.shop-meta .rating { color: $brand-primary; font-weight: 600; }
.shop-meta .distance { color: #10B981; font-weight: 600; }

.empty-tip {
  text-align: center; padding: 80rpx 0;
  font-size: 26rpx; color: $text-placeholder;
}

.bottom-space { height: 24rpx; }

.tab-bar {
  position: fixed; bottom: 0; left: 0; right: 0;
  height: 144rpx; padding-bottom: 32rpx;
  background: rgba(255,255,255,.96); backdrop-filter: blur(20rpx);
  border-top: 1rpx solid $border-color;
  display: flex; align-items: stretch; z-index: 50;
}
.tab-item {
  flex: 1; display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  color: $text-placeholder; font-size: 22rpx;
}
.tab-item .ic { font-size: 44rpx; line-height: 1; margin-bottom: 6rpx; }
.tab-item.active { color: $brand-primary; font-weight: 600; }
.tab-item.active .ic { transform: translateY(-4rpx) scale(1.08); }
</style>
