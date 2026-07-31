<template>
  <view class="page">
    <view class="topbar safe-top">
      <text class="back" @click="goBack">‹</text>
      <text class="title">店铺星级</text>
      <view style="width:60rpx"></view>
    </view>

    <view class="info-tip">
      <text class="b">星级 = 商品级会员等级。</text>
      不同店、不同商品有各自的升星条件。下方按店列出你所在的店，已满足推广条件的优先展示，点击查看店内每个商品的明细。
    </view>

    <view v-if="loading && !shopsActive.length && !shopsNormal.length" class="empty-tip">加载中…</view>
    <view v-else-if="!shopsActive.length && !shopsNormal.length" class="empty-state">
      <view class="empty-emoji">⭐</view>
      <view class="empty-title">暂未加入任何店铺</view>
      <view class="empty-sub">扫码或链接进入第一家店铺即可建立星级账户</view>
    </view>

    <template v-else>
      <view v-if="shopsActive.length" class="section-title">🎯 已满足推广条件（{{ shopsActive.length }} 家）</view>
      <view v-if="shopsActive.length" class="list">
        <view
          v-for="(s, idx) in shopsActive"
          :key="s.tenantId"
          :class="['shop-row', idx === 0 ? 'ranked' : '']"
          @click="enterShop(s)"
        >
          <text v-if="idx === 0" class="rank-badge">TOP 1</text>
          <view class="shop-pic" :style="picStyle(s)">{{ initial(s) }}</view>
          <view class="shop-info">
            <view class="shop-name">{{ s.shopName || '店铺 ' + s.tenantId }}</view>
            <view class="shop-meta">
              <text v-if="s.star > 0" class="meta-star">{{ stars(s.star) }}</text>
              <text v-else class="meta-star zero">未升星</text>
              <text class="meta-sep">·</text>
              <text class="meta-pts">推广 {{ ((s.promoPoints || 0) / 100).toFixed(2) }} 积分</text>
              <text class="meta-sep">·</text>
              <text class="meta-pts">消费 {{ ((s.points || 0) / 100).toFixed(2) }} 积分</text>
            </view>
          </view>
          <text class="arrow">›</text>
        </view>
      </view>

      <view v-if="shopsNormal.length" class="section-title">📦 普通会员（未购推 N 反 1 商品）</view>
      <view v-if="shopsNormal.length" class="list">
        <view
          v-for="s in shopsNormal"
          :key="s.tenantId"
          class="shop-row"
          @click="enterShop(s)"
        >
          <view class="shop-pic" :style="picStyle(s)">{{ initial(s) }}</view>
          <view class="shop-info">
            <view class="shop-name">{{ s.shopName || '店铺 ' + s.tenantId }}</view>
            <view class="shop-meta">
              <text class="meta-star zero">无星级</text>
              <text class="meta-sep">·</text>
              <text class="meta-pts">推广 {{ ((s.promoPoints || 0) / 100).toFixed(2) }} 积分</text>
              <text class="meta-sep">·</text>
              <text class="meta-pts">消费 {{ ((s.points || 0) / 100).toFixed(2) }} 积分</text>
            </view>
          </view>
          <text class="arrow">›</text>
        </view>
      </view>
    </template>

    <view class="bottom-space"></view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { request } from '../../api/request.js';

const allShops = ref([]);
const loading = ref(false);

// 「已满足推广条件」= 该店至少买过 1 个推 N 反 1 商品 → 有 spu_id>0 的 star 行 → star>=0 (但 promoPoints 可能=0)
// 简化判定：star > 0（已升星）OR promoPoints > 0（拿过推广奖励）→ 推广区
// 否则归普通会员
const shopsActive = computed(() => {
  return allShops.value
    .filter(s => (s.star || 0) > 0 || (s.promoPoints || 0) > 0)
    .sort((a, b) => {
      const sa = a.star || 0, sb = b.star || 0;
      if (sa !== sb) return sb - sa;
      return (b.promoPoints || 0) - (a.promoPoints || 0);
    });
});
const shopsNormal = computed(() => {
  return allShops.value.filter(s => (s.star || 0) <= 0 && (s.promoPoints || 0) <= 0);
});

function stars(n) {
  const cnt = Math.max(0, Math.min(10, parseInt(n) || 0));
  return '⭐'.repeat(cnt);
}
function initial(s) {
  const n = s?.shopName || '店';
  return n[0];
}
function picStyle(s) {
  const palette = ['ff9a4a-ff6b35', 'c9e0ff-6196f0', 'd3f4d3-4cb84c', 'ffd0dc-ee5a8b'];
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
    allShops.value = list || [];
  } catch { allShops.value = []; }
  finally { loading.value = false; }
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';
.page { min-height: 100vh; background: $bg-page; padding-bottom: 40rpx; }
.safe-top { padding-top: calc(max(env(safe-area-inset-top, 0px), var(--status-bar-height, 0px)) + 16rpx) !important; }
.topbar { display: flex; align-items: center; padding: 16rpx 32rpx; background: $bg-card; border-bottom: 1rpx solid $border-color; position: sticky; top: 0; z-index: 10; }
.topbar .back { font-size: 44rpx; color: $text-primary; padding-right: 16rpx; }
.topbar .title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; color: $text-primary; }

.info-tip {
  margin: 24rpx; padding: 28rpx 32rpx;
  background: linear-gradient(135deg, #fff5ef, #ffe1c8);
  border-left: 8rpx solid $brand-primary;
  border-radius: $radius-md;
  font-size: 24rpx; color: $text-primary; line-height: 1.55;
}
.info-tip .b { font-weight: 700; color: $brand-primary; }

.section-title {
  padding: 16rpx 40rpx 16rpx; font-size: 24rpx;
  color: $text-secondary; font-weight: 600;
}

.empty-tip { text-align: center; padding: 80rpx 0; color: $text-placeholder; font-size: 26rpx; }
.empty-state { text-align: center; padding: 160rpx 60rpx; }
.empty-state .empty-emoji { font-size: 96rpx; margin-bottom: 24rpx; opacity: .5; }
.empty-state .empty-title { font-size: 32rpx; font-weight: 700; color: $text-primary; }
.empty-state .empty-sub { margin-top: 12rpx; font-size: 24rpx; color: $text-placeholder; }

.list { padding: 0 32rpx; }
.shop-row {
  display: flex; align-items: center; gap: 24rpx;
  padding: 32rpx; margin-bottom: 20rpx;
  background: $bg-card; border-radius: $radius-md;
  box-shadow: 0 2rpx 8rpx rgba(15,23,42,.04);
  position: relative;
}
.shop-row.ranked {
  background:
    linear-gradient($bg-card, $bg-card) padding-box,
    linear-gradient(135deg, #ffd6b8, #ff6b35) border-box;
  border: 4rpx solid transparent;
}
.shop-row .rank-badge {
  position: absolute; top: -16rpx; left: 24rpx;
  padding: 4rpx 20rpx; border-radius: 999rpx;
  font-size: 22rpx; font-weight: 700; color: #fff;
  background: linear-gradient(135deg, $brand-primary, #ff9a4a);
  box-shadow: 0 8rpx 20rpx rgba(255,107,53,.35);
}
.shop-pic {
  width: 96rpx; height: 96rpx; border-radius: $radius-md;
  display: flex; align-items: center; justify-content: center;
  font-size: 44rpx; font-weight: 700;
  flex-shrink: 0;
}
.shop-info { flex: 1; min-width: 0; }
.shop-name { font-size: 32rpx; font-weight: 600; color: $text-primary; }
.shop-meta {
  margin-top: 12rpx; font-size: 24rpx;
  display: flex; align-items: center; gap: 12rpx;
  flex-wrap: wrap;
}
.meta-star {
  display: inline-flex; align-items: center;
  padding: 4rpx 20rpx;
  background: linear-gradient(135deg, #fff5ef, #ffd1ba);
  color: $brand-primary; border-radius: 999rpx;
  font-weight: 700;
  letter-spacing: 2rpx;
}
.meta-star.zero { background: #f6f7f9; color: $text-placeholder; font-weight: 400; }
.meta-sep { color: #d1d5db; }
.meta-pts { color: $text-secondary; font-variant-numeric: tabular-nums; }
.arrow { color: $text-placeholder; font-size: 44rpx; padding-left: 12rpx; }

.bottom-space { height: 80rpx; }
</style>
