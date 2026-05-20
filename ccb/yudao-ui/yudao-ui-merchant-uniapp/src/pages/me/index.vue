<template>
  <view class="page">
    <view class="profile safe-top">
      <view class="avatar">{{ avatarText }}</view>
      <view class="info">
        <view class="nickname">{{ displayName }}</view>
        <view class="mobile">{{ userStore.phone || '' }}</view>
      </view>
    </view>

    <view class="shop card">
      <view class="shop-head">
        <text class="title">我的店铺</text>
        <text class="edit" @click="goShopEdit">编辑</text>
      </view>
      <view class="shop-body">
        <view class="shop-name">{{ userStore.shop?.name || '-' }}</view>
        <view class="shop-addr">{{ userStore.shop?.address || '-' }}</view>
      </view>
    </view>

    <!-- SaaS 订阅状态 -->
    <view v-if="saasStatus" :class="['saas-card', saasStatus.expired ? 'expired' : '', saasStatus.isPlatform ? 'platform' : '']">
      <view class="saas-hd">
        <text class="saas-name">{{ levelLabel(saasStatus.level) }}</text>
        <text v-if="saasStatus.expired" class="saas-tag danger">已到期</text>
        <text v-else-if="saasStatus.isPlatform" class="saas-tag info">永久</text>
        <text v-else-if="daysRemaining < 7" class="saas-tag warn">{{ daysRemaining }} 天到期</text>
        <text v-else class="saas-tag ok">{{ daysRemaining }} 天剩余</text>
      </view>
      <view class="saas-row">
        <text class="lbl">到期时间</text>
        <text class="val">{{ saasStatus.isPlatform ? '永久' : (saasStatus.expireAt ? formatTime(saasStatus.expireAt) : '未订阅') }}</text>
      </view>
      <view class="saas-row">
        <text class="lbl">AI 视频余量</text>
        <text class="val">{{ saasStatus.aiVideoQuota || 0 }} 条</text>
      </view>
      <view v-if="!saasStatus.isPlatform" class="saas-actions">
        <view class="saas-btn primary" @click="goSubscription">{{ saasStatus.expired ? '立即续费' : '续费 / 升级' }}</view>
        <view
          v-if="saasStatus.level === 'BASIC' || saasStatus.level === 'PRO' || saasStatus.isPlatform"
          class="saas-btn ghost"
          @click="goShareCode"
        >
          🚀 分享开店赚奖励
        </view>
      </view>
      <view v-else class="saas-actions">
        <view class="saas-btn ghost" @click="goShareCode">🚀 分享开店赚奖励</view>
      </view>
    </view>

    <!-- 快捷功能 4 宫格：最常用的「商品/统计/会员/优惠券」一击到位 -->
    <view class="quick-grid">
      <view class="quick-item" @click="goProducts">
        <view class="qi-ic" style="background:linear-gradient(135deg,#FFC8A8,#FF6B35);">🛒</view>
        <text class="qi-lbl">商品管理</text>
      </view>
      <view class="quick-item" @click="goSalesStats">
        <view class="qi-ic" style="background:linear-gradient(135deg,#A8D8FF,#5B95E0);">📊</view>
        <text class="qi-lbl">销售统计</text>
      </view>
      <view class="quick-item" @click="goShopMembers">
        <view class="qi-ic" style="background:linear-gradient(135deg,#C8FFC8,#4CB84C);">👥</view>
        <text class="qi-lbl">店铺会员</text>
      </view>
      <view class="quick-item" @click="goCoupon">
        <view class="qi-ic" style="background:linear-gradient(135deg,#FFD0DC,#EE5A8B);">🎁</view>
        <text class="qi-lbl">优惠券</text>
      </view>
    </view>

    <!-- 营销 -->
    <view class="section-title">营销</view>
    <view class="menu card">
      <view class="menu-item" @click="goPromoConfig">
        <view class="mi-l"><text class="mi-ic">🎯</text><text>营销配置（推 N 反 1 / 星级 / 自然队列 / 积分池）</text></view>
        <text class="arrow">›</text>
      </view>
    </view>

    <!-- 经营数据 -->
    <view class="section-title">经营数据</view>
    <view class="menu card">
      <view class="menu-item" @click="goProductRank">
        <view class="mi-l"><text class="mi-ic">🛍</text><text>商品销售排行</text></view>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @click="goMembers">
        <view class="mi-l"><text class="mi-ic">🏆</text><text>会员消费排行</text></view>
        <text class="arrow">›</text>
      </view>
    </view>

    <!-- 财务管理 -->
    <view class="section-title">财务管理</view>
    <view class="menu card">
      <view class="menu-item" @click="goWithdrawApply">
        <view class="mi-l"><text class="mi-ic">💸</text><text>商户提现</text></view>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @click="goWithdrawApprove">
        <view class="mi-l"><text class="mi-ic">✅</text><text>提现审批</text></view>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @click="goUserWithdraw">
        <view class="mi-l"><text class="mi-ic">🪙</text><text>用户提现审核</text></view>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @click="goMemberWithdraw">
        <view class="mi-l"><text class="mi-ic">💼</text><text>用户余额提现审核</text></view>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @click="goPayApply">
        <view class="mi-l"><text class="mi-ic">💳</text><text>在线支付开通</text></view>
        <text class="arrow">›</text>
      </view>
    </view>

    <!-- AI 视频工具 -->
    <view class="section-title">AI 工具</view>
    <view class="menu card">
      <view class="menu-item" @click="goQuota">
        <view class="mi-l"><text class="mi-ic">🎬</text><text>AI 视频配额</text></view>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @click="goPackageOrders">
        <view class="mi-l"><text class="mi-ic">📦</text><text>配额订单</text></view>
        <text class="arrow">›</text>
      </view>
    </view>

    <!-- 设置与帮助 -->
    <view class="section-title">设置</view>
    <view class="menu card">
      <view class="menu-item" @click="goQrcode">
        <view class="mi-l"><text class="mi-ic">📲</text><text>店铺二维码</text></view>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @click="goHelp">
        <view class="mi-l"><text class="mi-ic">❓</text><text>帮助与反馈</text></view>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @click="goAbout">
        <view class="mi-l"><text class="mi-ic">ℹ️</text><text>关于拓小二</text></view>
        <text class="arrow">›</text>
      </view>
    </view>

    <button class="logout" @click="onLogout">退出登录</button>

    <RoleTabBar current="/pages/me/index" />
  </view>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { useUserStore } from '../../store/user.js';
import { request } from '../../api/request.js';

const userStore = useUserStore();

// SaaS 订阅状态
const saasStatus = ref(null);
const daysRemaining = computed(() => {
  if (!saasStatus.value || !saasStatus.value.expireAt) return 0;
  const ms = new Date(saasStatus.value.expireAt).getTime() - Date.now();
  return Math.max(0, Math.floor(ms / 86400000));
});
function levelLabel(level) {
  return ({
    PLATFORM: '平台商户',
    PRO: '全功能包',
    BASIC: '基础包',
    TRIAL: '试用版（30 天 PRO 体验）',
    EXPIRED: '已过期',
  })[level] || level;
}
function formatTime(ts) {
  if (!ts) return '-';
  const d = new Date(ts);
  if (isNaN(d.getTime())) return ts;
  const pad = (n) => (n < 10 ? '0' + n : n);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}
function goSubscription() {
  uni.navigateTo({ url: '/pages/me/subscription' });
}
function goShareCode() {
  uni.navigateTo({ url: '/pages/me/share-code' });
}
async function loadSaasStatus() {
  try {
    saasStatus.value = await request({ url: '/app-api/merchant/mini/saas/my-status' });
  } catch {}
}
onMounted(() => loadSaasStatus());
onShow(() => loadSaasStatus());

// 显示名优先级：nickname（店铺名）→ phone 末四位 → 「未登录」
const displayName = computed(() => {
  if (userStore.user?.nickname) return userStore.user.nickname;
  if (userStore.phone) {
    const p = userStore.phone;
    return p.length === 11 ? `${p.slice(0, 3)}****${p.slice(7)}` : p;
  }
  if (!userStore.token) return '未登录';
  return '商户用户';
});

const avatarText = computed(() => {
  const n = userStore.user?.nickname || userStore.phone || '摊';
  return n.slice(0, 1);
});

function toast() {
  uni.showToast({ title: '原型阶段，功能待开发', icon: 'none' });
}

function goShopEdit() {
  uni.navigateTo({ url: '/pages/me/shop-edit' });
}

function goPromoConfig() {
  uni.navigateTo({ url: '/pages/me/promo-config' });
}

function goQrcode() {
  uni.navigateTo({ url: '/pages/me/qrcode' });
}

function goHelp() {
  uni.navigateTo({ url: '/pages/me/help' });
}

function goAbout() {
  uni.navigateTo({ url: '/pages/me/about' });
}

function goPackageOrders() {
  uni.navigateTo({ url: '/pages/ai-video/package-orders' });
}

function goQuota() {
  uni.navigateTo({ url: '/pages/ai-video/quota' });
}

function goProducts() {
  uni.navigateTo({ url: '/pages/product/list' });
}

function goCoupon() {
  uni.navigateTo({ url: '/pages/me/coupon' });
}

function goWithdrawApprove() {
  uni.navigateTo({ url: '/pages/me/withdraw-approve' });
}

function goMembers() {
  uni.navigateTo({ url: '/pages/member/list' });
}

function goSalesStats() {
  uni.navigateTo({ url: '/pages/me/sales-stats' });
}
function goProductRank() {
  uni.navigateTo({ url: '/pages/me/product-rank' });
}
function goShopMembers() {
  uni.navigateTo({ url: '/pages/me/members' });
}

function goWithdrawApply() {
  uni.navigateTo({ url: '/pages/withdraw/merchant-apply' });
}

function goUserWithdraw() {
  uni.navigateTo({ url: '/pages/withdraw/user-list' });
}

function goMemberWithdraw() {
  uni.navigateTo({ url: '/pages/withdraw/member-list' });
}

function goPayApply() {
  uni.navigateTo({ url: '/pages/me/pay-apply' });
}

async function onLogout() {
  const r = await uni.showModal({ title: '提示', content: '确认退出登录？' });
  if (r.confirm) {
    await userStore.logout();
    // 商户个人页退出，跳商户登录
    uni.reLaunch({ url: '/pages/merchant-login/index' });
  }
}
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  // 底部 220rpx 留给固定 RoleTabBar 高度（约 120rpx）+ 安全区，避免内容被 tabbar 遮挡
  padding: 0 24rpx 220rpx;
}

.safe-top {
  padding-top: calc(env(safe-area-inset-top) + 48rpx);
}

.profile {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 24rpx 12rpx 48rpx;

  .avatar {
    width: 120rpx;
    height: 120rpx;
    line-height: 120rpx;
    text-align: center;
    border-radius: 50%;
    background: $brand-primary;
    color: #fff;
    font-size: 56rpx;
    font-weight: 600;
  }

  .nickname {
    font-size: 36rpx;
    font-weight: 700;
    color: $text-primary;
  }

  .mobile {
    font-size: 26rpx;
    color: $text-secondary;
    margin-top: 4rpx;
  }
}

.card {
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 32rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.shop {
  .shop-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20rpx;

    .title {
      font-size: 28rpx;
      color: $text-secondary;
    }

    .edit {
      font-size: 26rpx;
      color: $brand-primary;
    }
  }

  .shop-body {
    .shop-name {
      font-size: 32rpx;
      font-weight: 600;
      color: $text-primary;
    }

    .shop-addr {
      margin-top: 12rpx;
      font-size: 26rpx;
      color: $text-regular;
    }
  }
}

.menu {
  padding: 0 32rpx;

  .menu-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    height: 100rpx;
    font-size: 30rpx;
    color: $text-primary;
    border-bottom: 1rpx solid $border-color;

    &:last-child {
      border-bottom: none;
    }

    .arrow {
      color: $text-placeholder;
      font-size: 40rpx;
    }

    .mi-l {
      display: flex;
      align-items: center;
      gap: 18rpx;
    }
    .mi-ic {
      font-size: 32rpx;
      width: 44rpx;
      text-align: center;
      line-height: 1;
    }
  }
}

/* 4 宫格快捷功能 — 最高频操作一击直达 */
.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24rpx;
  background: $bg-card;
  padding: 32rpx 24rpx;
  border-radius: $radius-lg;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, .03);
}
.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}
.quick-item .qi-ic {
  width: 96rpx; height: 96rpx;
  display: flex; align-items: center; justify-content: center;
  border-radius: 24rpx;
  font-size: 44rpx;
  color: #fff;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, .08);
}
.quick-item .qi-lbl {
  font-size: 24rpx;
  color: $text-primary;
  font-weight: 500;
}

/* 区块标题 — 视觉分组锚点 */
.section-title {
  padding: 0 12rpx;
  margin: 8rpx 0 16rpx;
  font-size: 26rpx;
  font-weight: 600;
  color: $text-secondary;
  letter-spacing: 1rpx;
}

.logout {
  margin: 48rpx 0;
  height: 96rpx;
  line-height: 96rpx;
  background: #fff;
  color: $danger;
  font-size: 30rpx;
  border-radius: $radius-md;
  border: 1rpx solid $border-color;

  &::after {
    border: none;
  }
}

.saas-card {
  background: linear-gradient(135deg, #fff5ef, #ffe5d5);
  border-radius: $radius-lg;
  padding: 28rpx 32rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(255, 107, 53, 0.12);

  &.expired {
    background: linear-gradient(135deg, #ffeaea, #ffd1d1);
    box-shadow: 0 2rpx 12rpx rgba(220, 60, 60, 0.15);
  }

  &.platform {
    background: linear-gradient(135deg, #e8f5ff, #d0e5ff);
    box-shadow: 0 2rpx 12rpx rgba(60, 130, 220, 0.12);
  }

  .saas-hd {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 20rpx;

    .saas-name {
      font-size: 32rpx;
      font-weight: 700;
      color: $text-primary;
    }

    .saas-tag {
      font-size: 22rpx;
      padding: 4rpx 14rpx;
      border-radius: 999rpx;
      background: rgba(255, 255, 255, 0.7);
      color: $text-regular;

      &.danger { color: #d72e2e; background: rgba(255, 220, 220, 0.9); }
      &.warn { color: #d97706; background: rgba(255, 244, 220, 0.9); }
      &.ok { color: #16a34a; background: rgba(220, 244, 220, 0.9); }
      &.info { color: #2266cc; background: rgba(220, 230, 244, 0.9); }
    }
  }

  .saas-row {
    display: flex;
    justify-content: space-between;
    font-size: 26rpx;
    color: $text-regular;
    margin-top: 8rpx;

    .lbl { color: $text-secondary; }
    .val { color: $text-primary; }
  }

  .saas-actions {
    margin-top: 20rpx;
    display: flex;
    gap: 16rpx;

    .saas-btn {
      flex: 1;
      height: 70rpx;
      line-height: 70rpx;
      text-align: center;
      border-radius: $radius-md;
      font-size: 28rpx;
      font-weight: 600;

      &.primary {
        background: $brand-primary;
        color: #fff;
      }
    }
  }
}
</style>
