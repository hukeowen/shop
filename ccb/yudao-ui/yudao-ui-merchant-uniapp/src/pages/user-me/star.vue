<template>
  <view class="page">
    <view class="topbar safe-top">
      <text class="back" @click="goBack">‹</text>
      <text class="title">店铺星级</text>
      <view style="width:60rpx"></view>
    </view>

    <view class="info-tip">
      <text class="b">星级 = 商品级会员等级。</text>
      不同店、不同商品有各自的升星条件。下方按店展示「该店所有已购商品中的最高星级」，
      点开看店内每个商品的明细。
    </view>

    <view v-if="loading && !myShops.length" class="empty-tip">加载中…</view>
    <view v-else-if="!myShops.length" class="empty-state">
      <view class="empty-emoji">⭐</view>
      <view class="empty-title">暂未加入任何店铺</view>
      <view class="empty-sub">扫码或链接进入第一家店铺即可建立星级账户</view>
    </view>

    <view v-else class="list">
      <view v-for="s in myShops" :key="s.tenantId" class="shop-row" @click="enterShop(s)">
        <view class="shop-pic" :style="picStyle(s)">{{ initial(s) }}</view>
        <view class="shop-info">
          <view class="shop-name">{{ s.shopName || '店铺 ' + s.tenantId }}</view>
          <view class="shop-meta">
            <text v-if="s.star > 0" class="meta-star">⭐ 最高 {{ s.star }} 星</text>
            <text v-else class="meta-star zero">未升星</text>
            <text class="meta-sep">·</text>
            <text class="meta-pts">推广 ¥{{ ((s.promoPoints || 0) / 100).toFixed(2) }}</text>
            <text class="meta-sep">·</text>
            <text class="meta-pts">消费 {{ Math.floor((s.points || 0) / 100) }} 积分</text>
          </view>
        </view>
        <text class="arrow">›</text>
      </view>
    </view>

    <view class="bottom-space"></view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { request } from '../../api/request.js';

const myShops = ref([]);
const loading = ref(false);

function initial(s) {
  const n = s?.shopName || '店';
  return n[0];
}
function picStyle(s) {
  const palette = ['ffd1ba-ff6b35', 'c9e0ff-6196f0', 'd3f4d3-4cb84c', 'ffd0dc-ee5a8b'];
  const idx = (Number(s?.tenantId) || 0) % palette.length;
  const [c1, c2] = palette[idx].split('-');
  return `background: linear-gradient(135deg, #${c1}, #${c2}); color: #fff;`;
}

function enterShop(s) {
  uni.navigateTo({ url: `/pages/user-me/star-shop?tenantId=${s.tenantId}&shopName=${encodeURIComponent(s.shopName || '')}` });
}
function goBack() { uni.navigateBack({ fail: () => uni.reLaunch({ url: '/pages/user-me/index' }) }); }

onMounted(async () => {
  loading.value = true;
  try {
    const list = await request({ url: '/app-api/merchant/mini/member-rel/my-shops-enriched' });
    myShops.value = list || [];
  } catch { myShops.value = []; }
  finally { loading.value = false; }
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';
.page { min-height: 100vh; background: $bg-page; }
.safe-top { padding-top: calc(env(safe-area-inset-top) + 16rpx); }
.topbar { display: flex; align-items: center; padding: 16rpx 32rpx; background: $bg-card; border-bottom: 1rpx solid $border-color; }
.topbar .back { font-size: 44rpx; color: $text-primary; padding-right: 16rpx; }
.topbar .title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; color: $text-primary; }

.info-tip {
  margin: 24rpx 32rpx; padding: 24rpx 28rpx;
  background: linear-gradient(135deg, #fff5ef, #ffe1c8);
  border-left: 6rpx solid $brand-primary;
  border-radius: $radius-md;
  font-size: 24rpx; color: $text-primary; line-height: 1.6;
}
.info-tip .b { font-weight: 700; color: $brand-primary; }

.empty-tip { text-align: center; padding: 80rpx 0; color: $text-placeholder; font-size: 26rpx; }
.empty-state { text-align: center; padding: 120rpx 60rpx; }
.empty-state .empty-emoji { font-size: 96rpx; margin-bottom: 24rpx; opacity: .5; }
.empty-state .empty-title { font-size: 32rpx; font-weight: 700; color: $text-primary; }
.empty-state .empty-sub { margin-top: 12rpx; font-size: 24rpx; color: $text-placeholder; }

.list { margin: 0 32rpx; }
.shop-row {
  display: flex; align-items: center; gap: 20rpx;
  padding: 28rpx 24rpx; margin-bottom: 16rpx;
  background: $bg-card; border-radius: $radius-md;
  box-shadow: 0 2rpx 12rpx rgba(15,23,42,.04);
  position: relative;
}
.shop-pic {
  width: 84rpx; height: 84rpx; border-radius: $radius-md;
  display: flex; align-items: center; justify-content: center;
  font-size: 36rpx; font-weight: 700;
  flex-shrink: 0;
}
.shop-info { flex: 1; min-width: 0; }
.shop-name { font-size: 30rpx; font-weight: 600; color: $text-primary; }
.shop-meta {
  margin-top: 8rpx; font-size: 22rpx;
  display: flex; align-items: center; gap: 8rpx;
  flex-wrap: wrap;
}
.meta-star { color: $brand-primary; font-weight: 700; }
.meta-star.zero { color: $text-placeholder; font-weight: 400; }
.meta-sep { color: $text-placeholder; }
.meta-pts { color: $text-secondary; font-variant-numeric: tabular-nums; }
.arrow { color: $text-placeholder; font-size: 36rpx; padding-left: 8rpx; }

.bottom-space { height: 80rpx; }
</style>
