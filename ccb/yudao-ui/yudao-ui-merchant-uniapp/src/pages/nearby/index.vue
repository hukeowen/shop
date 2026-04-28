<template>
  <view class="page">
    <view v-if="locating" class="hint-bar">📍 正在获取位置…</view>
    <view v-else-if="locateError" class="hint-bar error">
      📵 定位失败：{{ locateError }}（H5 浏览器需 https + 用户授权）
    </view>

    <!-- 上半部分：地图 -->
    <view class="map-wrap">
      <map
        v-if="lat && lng"
        class="map"
        :latitude="lat"
        :longitude="lng"
        :scale="14"
        :show-location="true"
        :markers="markers"
        @markertap="onMarkerTap"
      />
      <view v-else class="map-placeholder">
        <text>地图加载中…</text>
      </view>
    </view>

    <!-- 下半部分：店铺列表 -->
    <view class="list-wrap">
      <view class="list-header">
        <text>附近店铺（{{ shops.length }}）</text>
        <text class="refresh" @click="refreshLocation">🔄 刷新定位</text>
      </view>

      <view v-if="loading" class="empty">加载中…</view>
      <view v-else-if="!shops.length" class="empty">附近暂无营业店铺</view>

      <view
        v-for="(shop, idx) in shops"
        :key="shop.id"
        :class="['shop-item', activeId === shop.id && 'active']"
        @click="onShopTap(shop, idx)"
      >
        <image v-if="shop.coverUrl" :src="shop.coverUrl" class="shop-cover" mode="aspectFill" />
        <view v-else class="shop-cover placeholder">🏪</view>
        <view class="shop-info">
          <view class="shop-name">{{ shop.shopName }}</view>
          <view class="shop-meta">
            <text class="distance">📍 {{ formatDistance(shop._distance) }}</text>
            <text v-if="shop.avgRating" class="rating">★ {{ shop.avgRating }}</text>
            <text v-if="shop.sales30d" class="sales">月销 {{ shop.sales30d }}</text>
          </view>
          <view class="shop-address">{{ shop.address || '—' }}</view>
        </view>
        <button class="enter-btn" size="mini" @click.stop="goShop(shop)">进店</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { request } from '../../api/request.js';

const lat = ref(null);
const lng = ref(null);
const locating = ref(true);
const locateError = ref('');
const loading = ref(false);
const shops = ref([]);
const activeId = ref(null);

const markers = computed(() =>
  shops.value
    .filter((s) => s.latitude && s.longitude)
    .map((s) => ({
      id: s.id,
      latitude: Number(s.latitude),
      longitude: Number(s.longitude),
      title: s.shopName,
      iconPath: '/static/marker.png',
      width: 32,
      height: 32,
    }))
);

function formatDistance(meters) {
  if (!meters && meters !== 0) return '—';
  if (meters < 1000) return `${Math.round(meters)} m`;
  return `${(meters / 1000).toFixed(1)} km`;
}

// Haversine 距离（米）
function calcDistance(lat1, lng1, lat2, lng2) {
  const R = 6371000;
  const toRad = (d) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

function refreshLocation() {
  locating.value = true;
  locateError.value = '';
  uni.getLocation({
    type: 'gcj02',
    success: async (r) => {
      lat.value = r.latitude;
      lng.value = r.longitude;
      locating.value = false;
      await loadShops();
    },
    fail: (e) => {
      locating.value = false;
      locateError.value = e?.errMsg || '未知错误';
    },
  });
}

async function loadShops() {
  if (!lat.value || !lng.value) return;
  loading.value = true;
  try {
    const list = await request({
      url: `/app-api/merchant/mini/user-shop/nearby?latitude=${lat.value}&longitude=${lng.value}&limit=30`,
    });
    const arr = Array.isArray(list) ? list : list?.list || [];
    shops.value = arr.map((s) => ({
      ...s,
      _distance:
        s.latitude && s.longitude
          ? calcDistance(lat.value, lng.value, Number(s.latitude), Number(s.longitude))
          : null,
    }));
  } catch (e) {
    shops.value = [];
  } finally {
    loading.value = false;
  }
}

function onMarkerTap(e) {
  const id = e.markerId || e.detail?.markerId;
  if (id) {
    activeId.value = id;
    const idx = shops.value.findIndex((s) => s.id === id);
    if (idx > -1) {
      // 滚到该项（uniapp 默认 page scroll 即可）
      uni.pageScrollTo({ selector: `.shop-item:nth-child(${idx + 2})`, duration: 200 });
    }
  }
}

function onShopTap(shop) {
  activeId.value = shop.id;
}

function goShop(shop) {
  uni.navigateTo({ url: `/pages/shop-home/index?tenantId=${shop.tenantId}` });
}

onLoad(() => {
  refreshLocation();
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  min-height: 100vh;
  background: #f6f7f9;
  display: flex;
  flex-direction: column;
}

.hint-bar {
  padding: 16rpx 32rpx;
  background: #e8f4ff;
  color: #1d6fa5;
  font-size: 24rpx;

  &.error {
    background: #fff3e0;
    color: #c66c00;
  }
}

.map-wrap {
  width: 100%;
  height: 50vh;
  background: #e9ecef;
}

.map {
  width: 100%;
  height: 100%;
}

.map-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $text-placeholder;
  font-size: 26rpx;
}

.list-wrap {
  flex: 1;
  background: #fff;
  border-radius: 24rpx 24rpx 0 0;
  margin-top: -24rpx;
  padding: 24rpx 0;
  box-shadow: 0 -4rpx 12rpx rgba(0, 0, 0, 0.04);
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 32rpx 16rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: $text-primary;

  .refresh {
    font-size: 24rpx;
    color: $brand-primary;
    font-weight: normal;
  }
}

.empty {
  padding: 80rpx 0;
  text-align: center;
  color: $text-placeholder;
  font-size: 26rpx;
}

.shop-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 20rpx 32rpx;
  border-bottom: 1rpx solid $border-color;
  transition: background 0.15s;

  &.active {
    background: rgba(255, 107, 53, 0.06);
  }

  .shop-cover {
    width: 120rpx;
    height: 120rpx;
    border-radius: $radius-md;
    flex: 0 0 auto;
    background: #f5f5f5;

    &.placeholder {
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 48rpx;
      color: $text-placeholder;
    }
  }

  .shop-info {
    flex: 1;
    min-width: 0;

    .shop-name {
      font-size: 28rpx;
      font-weight: 600;
      color: $text-primary;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .shop-meta {
      margin-top: 6rpx;
      display: flex;
      gap: 16rpx;
      font-size: 22rpx;
      color: $text-secondary;

      .rating {
        color: #f59e0b;
      }
    }

    .shop-address {
      margin-top: 4rpx;
      font-size: 22rpx;
      color: $text-placeholder;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .enter-btn {
    flex: 0 0 auto;
    background: $brand-primary;
    color: #fff;
    font-size: 24rpx;
    border-radius: $radius-md;

    &::after {
      border: none;
    }
  }
}
</style>
