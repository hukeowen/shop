<template>
  <view class="page">
    <!-- 顶部品牌橙渐变 + 头像信息 -->
    <view class="me-top safe-top">
      <view class="me-top-row">
        <view class="me-avatar">{{ avatarLetter }}</view>
        <view class="me-info">
          <view class="me-name">{{ userStore.user?.nickname || userStore.phone || '未登录用户' }}</view>
          <view class="me-phone">{{ phoneMask }} · 已加入 {{ shopsCount }} 家店</view>
        </view>
        <view class="me-edit" @click="goSettings">⚙</view>
      </view>
    </view>

    <!-- 跨店聚合 3 列 -->
    <view class="me-summary">
      <view class="item">
        <view class="num">{{ shopsCount }}</view>
        <view class="lbl">加入店铺</view>
      </view>
      <view class="item">
        <view class="num">{{ totalOrders }}</view>
        <view class="lbl">累计订单</view>
      </view>
      <view class="item">
        <view class="num brand">{{ totalReferrals }}</view>
        <view class="lbl">推荐好友</view>
      </view>
    </view>

    <!-- 资产隔离提示 -->
    <view class="me-tip-bar">
      💡 资产按 <text class="b">店铺独立</text>：余额、推广积分、消费积分、星级都隔离在每个店铺账户，
      点开下方"我加入的店铺"查看每家店的独立资产。
    </view>

    <!-- 我加入的店铺 -->
    <view class="shops-list">
      <view class="shops-head">
        <text class="ttl">我加入的店铺（{{ shopsCount }}）</text>
        <text class="more" @click="goManage">管理 ›</text>
      </view>
      <view v-if="!myShops.length && !loading" class="empty-row">还没加入任何店铺</view>
      <view
        v-for="s in myShops"
        :key="s.tenantId"
        class="shop-row"
        @click="goShop(s.tenantId)"
      >
        <view class="pic" :style="picStyle(s)">{{ initial(s) }}</view>
        <view class="center">
          <view class="name">
            {{ s.shopName || '未命名店铺' }}
            <text v-if="s.star" class="star">⭐ {{ s.star }} 星</text>
          </view>
          <view class="stats">
            <text>余额 <text class="em">¥{{ fen2yuan(s.balance) }}</text></text>
            <text>推广积分 <text class="em">{{ s.points || 0 }}</text></text>
          </view>
        </view>
        <text class="arrow">›</text>
      </view>
    </view>

    <!-- 资产 / 推广 -->
    <view class="me-grid">
      <view class="me-grid-title">资产 · 推广</view>
      <view class="me-row" @click="goWallet">
        <view class="me-row-icon i0">💰</view>
        <text class="me-row-name">我的钱包（按店铺）</text>
        <text class="me-row-tag">{{ shopsCount }} 家店余额</text>
        <text class="me-row-arrow">›</text>
      </view>
      <view class="me-row" @click="goPromoRecords">
        <view class="me-row-icon i1">📊</view>
        <text class="me-row-name">推广积分明细</text>
        <text class="me-row-arrow">›</text>
      </view>
      <view class="me-row" @click="goQueue">
        <view class="me-row-icon i2">🎯</view>
        <text class="me-row-name">我的队列（v6 推 N 反 1）</text>
        <text v-if="queueCount" class="me-row-tag">{{ queueCount }} 个排队中</text>
        <text class="me-row-arrow">›</text>
      </view>
      <view class="me-row" @click="goStar">
        <view class="me-row-icon i3">🏆</view>
        <text class="me-row-name">店铺星级</text>
        <text class="me-row-arrow">›</text>
      </view>
      <view class="me-row" @click="goInvite">
        <view class="me-row-icon i0">🔗</view>
        <text class="me-row-name">邀请好友（先选店铺）</text>
        <text v-if="totalReferrals" class="me-row-tag">已邀 {{ totalReferrals }} 人</text>
        <text class="me-row-arrow">›</text>
      </view>
    </view>

    <!-- 收藏 / 浏览 -->
    <view class="me-grid">
      <view class="me-grid-title">收藏 · 浏览</view>
      <view class="me-row" @click="goFavoriteShops">
        <view class="me-row-icon ifav">❤</view>
        <text class="me-row-name">我收藏的店铺</text>
        <text v-if="favoriteCount" class="me-row-tag">{{ favoriteCount }} 家</text>
        <text class="me-row-arrow">›</text>
      </view>
      <view class="me-row" @click="goRecent">
        <view class="me-row-icon i2">🕒</view>
        <text class="me-row-name">最近浏览</text>
        <text class="me-row-arrow">›</text>
      </view>
    </view>

    <!-- 平台 / 设置 -->
    <view class="me-grid">
      <view class="me-grid-title">平台 · 设置</view>
      <view class="me-row" @click="goAddress">
        <view class="me-row-icon i3">📍</view>
        <text class="me-row-name">收货地址</text>
        <text class="me-row-arrow">›</text>
      </view>
      <view class="me-row" @click="goHelp">
        <view class="me-row-icon i0">❓</view>
        <text class="me-row-name">帮助与反馈</text>
        <text class="me-row-arrow">›</text>
      </view>
      <view class="me-row" @click="goAbout">
        <view class="me-row-icon i1">ℹ</view>
        <text class="me-row-name">关于摊小二</text>
        <text class="me-row-arrow">›</text>
      </view>
      <view v-if="userStore.token" class="me-row danger" @click="onLogout">
        <view class="me-row-icon idanger">⏻</view>
        <text class="me-row-name">退出登录</text>
        <text class="me-row-arrow">›</text>
      </view>
    </view>

    <view class="bottom-space"></view>

    <!-- Tab Bar -->
    <view class="tab-bar safe-bottom">
      <view class="tab-item" @click="goHome">
        <text class="ic">🏠</text>
        <text class="lbl">首页</text>
      </view>
      <view class="tab-item" @click="goOrder">
        <text class="ic">📋</text>
        <text class="lbl">订单</text>
      </view>
      <view class="tab-item active">
        <text class="ic">👤</text>
        <text class="lbl">我的</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app';
import { request } from '../../api/request.js';
import { fen2yuan } from '../../utils/format.js';
import { useUserStore } from '../../store/user.js';

const userStore = useUserStore();
const myShops = ref([]);
const loading = ref(false);
const totalOrders = ref(0);
const totalReferrals = ref(0);
const queueCount = ref(0);

const shopsCount = computed(() => myShops.value.length);
const favoriteCount = computed(() => myShops.value.filter(s => s.favorite).length);
const avatarLetter = computed(() => {
  const n = userStore.user?.nickname || userStore.phone || '客';
  return String(n).slice(0, 1).toUpperCase();
});
const phoneMask = computed(() => {
  const p = userStore.phone;
  if (!p || p.length < 11) return p || '未绑定手机';
  return p.slice(0, 3) + '****' + p.slice(7);
});

const initial = (s) => (s.shopName || s.name || '店')[0];
const picStyle = (s) => {
  const palette = ['#ffd1ba,#ff6b35', '#c9e0ff,#6196f0', '#d3f4d3,#4cb84c', '#ffd0dc,#ee5a8b'];
  const idx = (Number(s.tenantId) || 0) % palette.length;
  return `background: linear-gradient(135deg, ${palette[idx]});`;
};

async function loadMyShops() {
  if (!userStore.token) {
    myShops.value = [];
    return;
  }
  loading.value = true;
  try {
    const list = await request({ url: '/app-api/merchant/mini/member-rel/my-shops-enriched' });
    myShops.value = list || [];
  } catch {
    myShops.value = [];
  } finally {
    loading.value = false;
  }
}

async function loadAggregates() {
  if (!userStore.token) return;
  try {
    const r = await request({ url: '/app-api/trade/order/count' }).catch(() => null);
    totalOrders.value = r?.allCount ?? r?.total ?? 0;
  } catch { totalOrders.value = 0; }
  try {
    const r = await request({ url: '/app-api/merchant/mini/promo/referral/my-children-count' }).catch(() => null);
    totalReferrals.value = r?.count ?? 0;
  } catch { totalReferrals.value = 0; }
  try {
    const list = await request({ url: '/app-api/merchant/mini/promo/my-queues' }).catch(() => null);
    queueCount.value = Array.isArray(list) ? list.length : 0;
  } catch { queueCount.value = 0; }
}

function goShop(tid) {
  if (!tid) return;
  uni.navigateTo({ url: `/pages/shop-home/index?tenantId=${tid}` });
}
function goWallet() { uni.navigateTo({ url: '/pages/user-me/wallet' }); }
function goPromoRecords() { uni.navigateTo({ url: '/pages/user-me/promo-records' }); }
function goQueue() { uni.navigateTo({ url: '/pages/user-me/my-queue' }); }
function goStar() { uni.navigateTo({ url: '/pages/user-me/star' }); }
function goInvite() { uni.navigateTo({ url: '/pages/user-me/invite' }); }
function goFavoriteShops() { uni.navigateTo({ url: '/pages/user-me/favorites' }); }
function goRecent() { uni.navigateTo({ url: '/pages/user-me/favorites' }); }
function goAddress() { uni.navigateTo({ url: '/pages/user-me/address' }); }
function goHelp() { uni.navigateTo({ url: '/pages/user-me/help' }); }
function goAbout() { uni.navigateTo({ url: '/pages/user-me/about' }); }
function goManage() { goFavoriteShops(); }
function goSettings() { goAbout(); }

function goHome() { uni.switchTab?.({ url: '/pages/user-home/index' }) || uni.navigateTo({ url: '/pages/user-home/index' }); }
function goOrder() { uni.switchTab?.({ url: '/pages/user-order/list' }) || uni.navigateTo({ url: '/pages/user-order/list' }); }

function onLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确定要退出登录吗？',
    success: (r) => {
      if (r.confirm) {
        userStore.logout();
        uni.reLaunch({ url: '/pages/login/index' });
      }
    },
  });
}

onMounted(() => {
  if (!userStore.token) {
    uni.reLaunch({ url: '/pages/login/index' });
    return;
  }
  loadMyShops();
  loadAggregates();
});
onShow(() => {
  if (userStore.token) loadMyShops();
});
onPullDownRefresh(async () => {
  await Promise.all([loadMyShops(), loadAggregates()]);
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

.me-top {
  background: linear-gradient(135deg, $brand-primary, $brand-primary-dark);
  color: #fff;
  padding: 24rpx 32rpx 64rpx;
}
.me-top-row { display: flex; align-items: center; gap: 24rpx; }
.me-avatar {
  width: 112rpx; height: 112rpx; border-radius: 50%;
  background: rgba(255,255,255,.22); border: 4rpx solid rgba(255,255,255,.40);
  font-size: 48rpx; font-weight: 800; color: #fff;
  display: flex; align-items: center; justify-content: center;
}
.me-info { flex: 1; min-width: 0; }
.me-name { font-size: 34rpx; font-weight: 700; }
.me-phone { font-size: 24rpx; opacity: .85; margin-top: 4rpx; }
.me-edit {
  width: 64rpx; height: 64rpx; border-radius: 50%;
  background: rgba(255,255,255,.18);
  font-size: 28rpx; line-height: 64rpx; text-align: center;
}

.me-summary {
  margin: -32rpx 32rpx 0; padding: 32rpx 0;
  background: $bg-card; border-radius: $radius-lg;
  box-shadow: 0 4rpx 16rpx rgba(15,23,42,.04);
  display: grid; grid-template-columns: repeat(3, 1fr);
  position: relative; z-index: 2;
}
.me-summary .item { text-align: center; border-right: 1rpx solid $border-color; }
.me-summary .item:last-child { border-right: 0; }
.me-summary .num {
  font-size: 40rpx; font-weight: 800; color: $text-primary;
  font-variant-numeric: tabular-nums;
}
.me-summary .num.brand { color: $brand-primary; }
.me-summary .lbl { font-size: 22rpx; color: $text-placeholder; margin-top: 4rpx; }

.me-tip-bar {
  margin: 24rpx 32rpx; padding: 20rpx 28rpx;
  background: #FFF8EF; border-radius: $radius-md;
  border-left: 6rpx solid $warning;
  font-size: 24rpx; color: #B26A00; line-height: 1.5;
}
.me-tip-bar .b { font-weight: 700; }

.shops-list {
  margin: 0 32rpx; background: $bg-card;
  border-radius: $radius-lg; box-shadow: 0 4rpx 16rpx rgba(15,23,42,.04);
  overflow: hidden;
}
.shops-head {
  display: flex; justify-content: space-between; align-items: center;
  padding: 28rpx 32rpx; border-bottom: 1rpx solid $border-color;
}
.shops-head .ttl { font-size: 28rpx; font-weight: 700; color: $text-primary; }
.shops-head .more { font-size: 24rpx; color: $text-placeholder; }
.shop-row {
  display: flex; gap: 24rpx; padding: 28rpx 32rpx;
  border-bottom: 1rpx solid $border-color;
}
.shop-row:last-child { border-bottom: 0; }
.shop-row .pic {
  width: 80rpx; height: 80rpx; border-radius: $radius-md;
  color: #fff; font-size: 36rpx; font-weight: 800;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.shop-row .center { flex: 1; min-width: 0; }
.shop-row .name {
  font-size: 28rpx; font-weight: 600; color: $text-primary;
  display: flex; align-items: center; gap: 12rpx;
}
.shop-row .star {
  background: $brand-primary-light; color: $brand-primary;
  font-size: 20rpx; padding: 2rpx 14rpx; border-radius: 999rpx;
}
.shop-row .stats {
  margin-top: 12rpx; display: flex; gap: 24rpx;
  font-size: 22rpx; color: $text-placeholder;
}
.shop-row .stats .em {
  color: $brand-primary; font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.shop-row .arrow { color: $text-placeholder; font-size: 36rpx; align-self: center; }
.shops-list .empty-row {
  text-align: center; padding: 40rpx 0;
  font-size: 24rpx; color: $text-placeholder;
}

.me-grid {
  margin: 24rpx 32rpx; background: $bg-card;
  border-radius: $radius-lg; box-shadow: 0 4rpx 16rpx rgba(15,23,42,.04);
  overflow: hidden;
}
.me-grid-title {
  padding: 24rpx 32rpx 8rpx;
  font-size: 26rpx; font-weight: 700; color: $text-primary;
}
.me-row {
  display: flex; align-items: center; gap: 24rpx;
  padding: 28rpx 32rpx;
  border-top: 1rpx solid $border-color;
}
.me-row:first-of-type { border-top: 0; }
.me-row-icon {
  width: 64rpx; height: 64rpx; border-radius: $radius-md;
  font-size: 32rpx;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.me-row-icon.i0 { background: $brand-primary-light; color: $brand-primary; }
.me-row-icon.i1 { background: rgba(99,102,241,.12); color: #6366F1; }
.me-row-icon.i2 { background: rgba(16,185,129,.12); color: #10B981; }
.me-row-icon.i3 { background: rgba(245,158,11,.14); color: #F59E0B; }
.me-row-icon.ifav { background: rgba(238,90,139,.14); color: #ee5a8b; }
.me-row-icon.idanger { background: rgba(239,68,68,.12); color: $danger; }
.me-row-name { flex: 1; font-size: 28rpx; color: $text-primary; }
.me-row-tag {
  font-size: 22rpx; background: $brand-primary-light; color: $brand-primary;
  padding: 4rpx 16rpx; border-radius: 999rpx;
}
.me-row-arrow { font-size: 32rpx; color: $text-placeholder; }
.me-row.danger .me-row-name { color: $danger; }

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
