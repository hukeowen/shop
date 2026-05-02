<template>
  <view class="page">
    <view class="topbar safe-top">
      <text class="back" @click="goBack">‹</text>
      <text class="title">我收藏的店铺</text>
      <text class="right">{{ shops.length }} 家</text>
    </view>
    <view v-if="loading && !shops.length" class="empty-tip">加载中...</view>
    <view v-else-if="!shops.length" class="empty-state">
      <view class="empty-emoji">❤</view>
      <view class="empty-title">还没收藏任何店铺</view>
      <view class="empty-sub">在店铺详情页右上角点 ♥ 即可收藏</view>
      <view class="empty-cta" @click="goHome">去首页逛逛 ›</view>
    </view>
    <view v-else>
      <view
        v-for="s in shops"
        :key="s.tenantId"
        class="shop-card"
        @click="goShop(s.tenantId)"
      >
        <view class="pic" :style="picStyle(s)">{{ initial(s) }}</view>
        <view class="info">
          <view class="name">
            <text>{{ s.shopName || '未命名店铺' }}</text>
            <text v-if="s.star" class="star">⭐ {{ s.star }} 星</text>
          </view>
          <view class="addr" v-if="s.address">{{ s.address }}</view>
          <view class="stats">
            <text>余额 <text class="em">¥{{ fen2yuan(s.balance) }}</text></text>
            <text>积分 <text class="em">{{ s.points || 0 }}</text></text>
          </view>
        </view>
        <view class="fav-on" @click.stop="toggleFavorite(s)">❤</view>
      </view>
    </view>
    <view class="bottom-space"></view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { request } from '../../api/request.js';
import { fen2yuan } from '../../utils/format.js';

const shops = ref([]);
const loading = ref(false);

const initial = (s) => (s.shopName || '店')[0];
const picStyle = (s) => {
  const palette = ['#ffd1ba,#ff6b35', '#c9e0ff,#6196f0', '#d3f4d3,#4cb84c', '#ffd0dc,#ee5a8b'];
  const idx = (Number(s.tenantId) || 0) % palette.length;
  return `background: linear-gradient(135deg, ${palette[idx]});`;
};

async function load() {
  loading.value = true;
  try {
    const list = await request({ url: '/app-api/merchant/mini/member-rel/my-shops-enriched?onlyFavorite=true' });
    shops.value = list || [];
  } catch {
    shops.value = [];
  } finally {
    loading.value = false;
  }
}

async function toggleFavorite(s) {
  uni.showModal({
    title: '取消收藏',
    content: `确定从收藏中移除 ${s.shopName} 吗？`,
    success: async (r) => {
      if (!r.confirm) return;
      try {
        await request({
          url: `/app-api/merchant/mini/member-rel/favorite/toggle?tenantId=${s.tenantId}&favorite=false`,
          method: 'POST',
        });
        load();
      } catch (e) {
        uni.showToast({ title: '操作失败', icon: 'none' });
      }
    },
  });
}

function goShop(tid) { uni.navigateTo({ url: `/pages/shop-home/index?tenantId=${tid}` }); }
function goBack() { uni.navigateBack({ fail: () => goHome() }); }
function goHome() { uni.switchTab?.({ url: '/pages/user-home/index' }) || uni.reLaunch({ url: '/pages/user-home/index' }); }

onMounted(load);
</script>

<style lang="scss" scoped>
@import '../../uni.scss';
.page { min-height: 100vh; background: $bg-page; }
.safe-top { padding-top: calc(env(safe-area-inset-top) + 16rpx); }
.topbar {
  display: flex; align-items: center; padding: 16rpx 32rpx;
  background: $bg-card; border-bottom: 1rpx solid $border-color;
}
.topbar .back { font-size: 44rpx; color: $text-primary; padding-right: 16rpx; }
.topbar .title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; color: $text-primary; }
.topbar .right { font-size: 24rpx; color: $text-placeholder; }
.empty-tip { text-align: center; padding: 80rpx 0; color: $text-placeholder; font-size: 26rpx; }
.empty-state {
  text-align: center; padding: 120rpx 60rpx;
}
.empty-state .empty-emoji { font-size: 96rpx; color: #ee5a8b; margin-bottom: 24rpx; }
.empty-state .empty-title { font-size: 32rpx; font-weight: 700; color: $text-primary; }
.empty-state .empty-sub { margin-top: 12rpx; font-size: 24rpx; color: $text-placeholder; line-height: 1.6; }
.empty-state .empty-cta {
  margin-top: 40rpx; display: inline-block;
  padding: 16rpx 40rpx;
  background: $brand-primary; color: #fff;
  border-radius: 999rpx; font-size: 26rpx; font-weight: 600;
}
.shop-card {
  margin: 24rpx 32rpx; padding: 28rpx 32rpx;
  background: $bg-card; border-radius: $radius-lg;
  box-shadow: 0 4rpx 16rpx rgba(15,23,42,.04);
  display: flex; gap: 24rpx; align-items: center;
}
.shop-card .pic {
  width: 96rpx; height: 96rpx; border-radius: $radius-md;
  color: #fff; font-size: 40rpx; font-weight: 800;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.shop-card .info { flex: 1; min-width: 0; }
.shop-card .name {
  font-size: 30rpx; font-weight: 700; color: $text-primary;
  display: flex; align-items: center; gap: 12rpx;
}
.shop-card .star {
  background: $brand-primary-light; color: $brand-primary;
  font-size: 20rpx; padding: 2rpx 14rpx; border-radius: 999rpx;
}
.shop-card .addr {
  margin-top: 8rpx; font-size: 22rpx; color: $text-placeholder;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.shop-card .stats {
  margin-top: 12rpx; display: flex; gap: 24rpx;
  font-size: 22rpx; color: $text-placeholder;
}
.shop-card .stats .em {
  color: $brand-primary; font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.shop-card .fav-on {
  width: 64rpx; height: 64rpx; border-radius: 50%;
  background: rgba(238,90,139,.12); color: #ee5a8b;
  display: flex; align-items: center; justify-content: center;
  font-size: 30rpx;
  flex-shrink: 0;
}
.bottom-space { height: 80rpx; }
</style>
