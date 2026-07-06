<template>
  <view class="page">
    <!-- #ifdef APP-PLUS -->
    <!-- 临时诊断标记：确认新代码已打包 + 读到的状态栏高度；确认后删除 -->
    <view class="sbh-debug">SBH={{ sbhDbg }}</view>
    <!-- #endif -->
    <!-- 顶部渐变 hero：问候 + 日期 + 头像 -->
    <view class="hero safe-top" :style="heroPadStyle">
      <view class="hero-glow"></view>
      <view class="hero-content">
        <view class="hero-left">
          <view class="greeting">{{ greeting }}<text class="emoji">{{ greetingEmoji }}</text></view>
          <view class="shop-name">{{ userStore.shop?.name || userStore.user?.nickname || '未关联店铺' }}</view>
          <view class="date-strip">
            <text class="date">{{ todayStr }}</text>
            <text class="dot-sep">·</text>
            <text class="weekday">{{ weekdayStr }}</text>
          </view>
        </view>
        <view class="hero-avatar">{{ avatarText }}</view>
      </view>
    </view>

    <!-- 营业打卡卡片（最核心 CTA） -->
    <view :class="['op-card', opCardClass]" @click="onOpCardTap">
      <view class="op-bg-deco"></view>
      <view class="op-content">
        <!-- 未打卡：大 CTA -->
        <template v-if="!operatingStatus?.checkedInToday">
          <view class="op-icon-big">📍</view>
          <view class="op-text">
            <view class="op-title">开始今日营业</view>
            <view class="op-sub">每日一次，让顾客知道你在</view>
          </view>
          <view class="op-cta">立即打卡</view>
        </template>
        <!-- 已打卡 + 未主动打烊：营业中 -->
        <template v-else-if="!operatingStatus?.manualClosed">
          <view class="op-icon-big pulse">🟢</view>
          <view class="op-text">
            <view class="op-title">营业中</view>
            <view class="op-sub">今日已打卡 · 顾客可下单</view>
          </view>
          <view class="op-cta minor" @click.stop="setManualClosed(true)">打烊</view>
        </template>
        <!-- 已主动打烊 -->
        <template v-else>
          <view class="op-icon-big">⏸</view>
          <view class="op-text">
            <view class="op-title">已打烊</view>
            <view class="op-sub">用户暂时无法下单 · 点击恢复营业</view>
          </view>
          <view class="op-cta primary" @click.stop="setManualClosed(false)">正常营业</view>
        </template>
      </view>
    </view>

    <!-- 8 grid 快捷功能：核心操作 4 个 + 经营管理 4 个 一并放首页 -->
    <view class="quick-grid">
      <view class="quick-item" @click="jumpAi">
        <view class="qi-ic" style="background:linear-gradient(135deg,#FFC8A8,#FF6B35);"><text>🎬</text></view>
        <text class="qi-lbl">一键成片</text>
      </view>
      <view class="quick-item" @click="jumpAddProduct">
        <view class="qi-ic" style="background:linear-gradient(135deg,#C8FFC8,#10B981);"><text>＋</text></view>
        <text class="qi-lbl">AI 上架</text>
      </view>
      <view class="quick-item" @click="jumpVerify">
        <view class="qi-ic" style="background:linear-gradient(135deg,#C8E0FF,#3B82F6);"><text>📷</text></view>
        <text class="qi-lbl">扫码核销</text>
      </view>
      <view class="quick-item" @click="jumpOrders">
        <view class="qi-ic" style="background:linear-gradient(135deg,#E5D6FF,#8B5CF6);"><text>📋</text></view>
        <text class="qi-lbl">订单</text>
      </view>
      <view class="quick-item" @click="goProducts">
        <view class="qi-ic" style="background:linear-gradient(135deg,#FFE0CC,#FF8C42);"><text>🛒</text></view>
        <text class="qi-lbl">商品管理</text>
      </view>
      <view class="quick-item" @click="jumpSalesStats">
        <view class="qi-ic" style="background:linear-gradient(135deg,#A8D8FF,#5B95E0);"><text>📊</text></view>
        <text class="qi-lbl">销售统计</text>
      </view>
      <view class="quick-item" @click="goShopMembers">
        <view class="qi-ic" style="background:linear-gradient(135deg,#D7F5D7,#4CB84C);"><text>👥</text></view>
        <text class="qi-lbl">店铺会员</text>
      </view>
      <view class="quick-item" @click="goCoupon">
        <view class="qi-ic" style="background:linear-gradient(135deg,#FFD0DC,#EE5A8B);"><text>🎁</text></view>
        <text class="qi-lbl">优惠券</text>
      </view>
    </view>

    <!-- 今日数据 2x2，每张卡专色 -->
    <view class="data-head">
      <text class="dh-title">今日数据</text>
      <text class="dh-more" @click="jumpSalesStats">查看完整 ›</text>
    </view>
    <view class="data-grid">
      <view class="data-card orange" @click="jumpOrderTab('all')">
        <view class="dc-ic">🛍</view>
        <view class="dc-body">
          <view class="dc-lbl">订单数</view>
          <view class="dc-val">{{ data?.today.orderCount ?? '-' }}</view>
        </view>
      </view>
      <view class="data-card blue" @click="jumpSalesStats">
        <view class="dc-ic">💰</view>
        <view class="dc-body">
          <view class="dc-lbl">销售额</view>
          <view class="dc-val">¥{{ smartYuan(data?.today.salesAmount || 0) }}</view>
        </view>
      </view>
      <view class="data-card green" @click="jumpMembers">
        <view class="dc-ic">👥</view>
        <view class="dc-body">
          <view class="dc-lbl">新会员</view>
          <view class="dc-val">{{ data?.today.newMembers ?? '-' }}</view>
        </view>
      </view>
      <view class="data-card purple" @click="jumpOrderTab('pending')">
        <view class="dc-ic">⏳</view>
        <view class="dc-body">
          <view class="dc-lbl">待处理</view>
          <view class="dc-val warn">{{ data?.today.pendingOrders ?? '-' }}</view>
        </view>
      </view>
    </view>

    <!-- v8 今日推广（仅有数据时显示） -->
    <view
      v-if="data?.promo && (data.promo.issued > 0 || data.promo.deducted > 0)"
      class="promo-today"
      @click="jumpSalesStats"
    >
      <view class="pt-head">
        <text class="pt-title">🎁 今日推广</text>
        <text class="pt-tag">明细 ›</text>
      </view>
      <view class="pt-row">
        <view class="pt-mini">
          <text class="l">发出推广积分</text>
          <text class="v">¥{{ fen2yuan(data.promo.issued) }}</text>
        </view>
        <view class="pt-mini">
          <text class="l">抵扣订单</text>
          <text class="v">¥{{ fen2yuan(data.promo.deducted) }}</text>
        </view>
        <view class="pt-mini">
          <text class="l">星级奖励发出</text>
          <text class="v">¥{{ fen2yuan(data.promo.commission) }}</text>
        </view>
      </view>
    </view>

    <!-- 7 天销售趋势 -->
    <view class="section card">
      <view class="section-head">
        <text class="title">最近 7 天销售趋势</text>
      </view>
      <view class="chart">
        <view
          class="bar"
          v-for="(v, i) in data?.trend.sales || []"
          :key="i"
          :style="{ height: barHeight(v) + '%' }"
        >
          <view class="bar-value">¥{{ smartYuan(v) }}</view>
          <view class="bar-fill"></view>
          <text class="bar-label">{{ data?.trend.labels[i] }}</text>
        </view>
      </view>
    </view>

    <!-- 热销 Top 3 -->
    <view class="section card">
      <view class="section-head">
        <text class="title">🏆 热销商品 Top 3</text>
        <text class="more" @click="jumpProductRank">完整排行 ›</text>
      </view>
      <view class="rank-list">
        <view
          class="rank-item"
          v-for="(p, i) in data?.topProducts || []"
          :key="p.name"
        >
          <view class="rank-no" :class="'rank-' + (i + 1)">{{ i + 1 }}</view>
          <view class="rank-name">{{ p.name }}</view>
          <view class="rank-meta">
            <text>售 {{ p.count }}</text>
            <text class="rank-amount">¥{{ fen2yuan(p.amount) }}</text>
          </view>
        </view>
        <view v-if="!data?.topProducts?.length" class="rank-empty">暂无销售数据</view>
      </view>
    </view>

    <view class="bottom-space" />

    <RoleTabBar current="/pages/index/index" />
  </view>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app';
import { getDashboard } from '../../api/report.js';
import { request } from '../../api/request.js';
import { fen2yuan, smartYuan } from '../../utils/format.js';
import { useUserStore } from '../../store/user.js';

const userStore = useUserStore();
const data = ref(null);
const operatingStatus = ref(null);

// 原生 App：直接把 padding-top 设成状态栏高度(px)，绝不依赖 CSS 变量/env/manifest
let _sbh = 0;
// #ifdef APP-PLUS
try { _sbh = uni.getSystemInfoSync().statusBarHeight || 0; } catch (e) { /* ignore */ }
if (!_sbh) { try { _sbh = plus.navigator.getStatusbarHeight() || 0; } catch (e) { /* ignore */ } }
if (!_sbh) _sbh = 30; // 多重兜底：绝不为 0，保证顶栏一定被推下来
// #endif
const heroPadStyle = _sbh ? { paddingTop: _sbh + 10 + 'px' } : {};
const sbhDbg = _sbh; // 临时诊断：确认代码生效且读到的状态栏高度值

// 时段问候 + emoji
const now = ref(new Date());
const hour = computed(() => now.value.getHours());
const greeting = computed(() => {
  const h = hour.value;
  if (h < 5) return '夜深了，';
  if (h < 11) return '早上好，';
  if (h < 14) return '中午好，';
  if (h < 18) return '下午好，';
  if (h < 23) return '晚上好，';
  return '夜深了，';
});
const greetingEmoji = computed(() => {
  const h = hour.value;
  if (h < 5) return '🌙';
  if (h < 11) return '☀';
  if (h < 14) return '🌤';
  if (h < 18) return '☕';
  if (h < 23) return '🌆';
  return '🌙';
});
const todayStr = computed(() => {
  const d = now.value;
  return `${d.getMonth() + 1}月${d.getDate()}日`;
});
const weekdayStr = computed(() => {
  const w = now.value.getDay();
  return ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][w];
});

const avatarText = computed(() => {
  const n = userStore.user?.nickname || userStore.shop?.name || userStore.phone || '摊';
  return n.slice(0, 1);
});

// 营业打卡卡片状态 class
const opCardClass = computed(() => {
  if (!operatingStatus.value?.checkedInToday) return 'pending'; // 未打卡 - 醒目橙
  if (operatingStatus.value?.manualClosed) return 'closed';     // 打烊 - 灰
  return 'open';                                                // 营业 - 绿
});

async function loadOperatingStatus() {
  try {
    operatingStatus.value = await request({ url: '/app-api/merchant/mini/shop/operating-status' });
  } catch {}
}

async function onOpCardTap() {
  // 未打卡 → 点卡片整体即打卡
  if (!operatingStatus.value?.checkedInToday) {
    try {
      await request({ url: '/app-api/merchant/mini/shop/check-in', method: 'POST' });
      uni.showToast({ title: '已打卡，营业中 ✓', icon: 'success' });
      await loadOperatingStatus();
    } catch (e) {
      uni.showToast({ title: e?.message || '打卡失败', icon: 'none' });
    }
  }
}

async function setManualClosed(closed) {
  const r = await uni.showModal({
    title: closed ? '确认打烊？' : '确认恢复营业？',
    content: closed ? '打烊后用户无法看到您的店铺，也无法下单' : '重新对用户开放下单',
  });
  if (!r.confirm) return;
  try {
    await request({
      url: `/app-api/merchant/mini/shop/manual-closed?closed=${closed}`,
      method: 'PUT',
    });
    uni.showToast({ title: closed ? '已打烊' : '已恢复营业', icon: 'success' });
    await loadOperatingStatus();
  } catch (e) {
    uni.showToast({ title: e?.message || '操作失败', icon: 'none' });
  }
}

async function load() {
  data.value = await getDashboard();
}

function barHeight(v) {
  const max = Math.max(...(data.value?.trend.sales || [1]));
  return max ? Math.max(8, (v / max) * 100) : 0;
}

function jumpAi() {
  // #ifdef APP-PLUS
  uni.navigateTo({ url: '/pages/ai-video/app' });
  return;
  // #endif
  // eslint-disable-next-line no-unreachable
  uni.reLaunch({ url: '/pages/ai-video/index' });
}
function jumpOrders() { uni.reLaunch({ url: '/pages/order/list' }); }
function jumpVerify() { uni.reLaunch({ url: '/pages/order/list' }); }
function jumpAddProduct() { uni.navigateTo({ url: '/pages/product/batch' }); }
function goProducts() { uni.navigateTo({ url: '/pages/product/list' }); }
function goShopMembers() { uni.navigateTo({ url: '/pages/me/members' }); }
function goCoupon() { uni.navigateTo({ url: '/pages/me/coupon' }); }
function jumpSalesStats() { uni.navigateTo({ url: '/pages/me/sales-stats' }); }
function jumpOrderTab(tab) { uni.reLaunch({ url: `/pages/order/list?tab=${tab}` }); }
function jumpMembers() { uni.navigateTo({ url: '/pages/me/members' }); }
function jumpProductRank() { uni.navigateTo({ url: '/pages/me/product-rank' }); }

onMounted(async () => {
  // #ifdef APP-PLUS
  // 商户端 APK：原生无 hostname，未登录直接进商户登录（不落到通用/用户登录）
  if (!userStore.loggedIn) {
    uni.reLaunch({ url: '/pages/merchant-login/index' });
    return;
  }
  // #endif
  // 子域名分流：tuo./ke. 直接定向到对应端，避免商户工作台 API 调用 → 401 → 跳通用登录
  try {
    const host = (typeof location !== 'undefined' ? location.hostname : '').toLowerCase();
    if (host.startsWith('ke.')) {
      uni.reLaunch({ url: '/pages/user-home/index' });
      return;
    }
    if (host.startsWith('tuo.') && !userStore.loggedIn) {
      uni.reLaunch({ url: '/pages/merchant-login/index' });
      return;
    }
  } catch {}
  if (!userStore.loggedIn) {
    uni.reLaunch({ url: '/pages/login/index' });
    return;
  }
  if (userStore.activeRole && userStore.activeRole !== 'merchant') {
    uni.reLaunch({ url: '/pages/user-home/index' });
    return;
  }
  try { await userStore.refreshMe(); } catch (e) { /* 不阻塞 */ }
  if (userStore.activeRole && userStore.activeRole !== 'merchant') {
    uni.reLaunch({ url: '/pages/user-home/index' });
    return;
  }
  // SaaS 订阅到期拦截
  try {
    const st = await request({ url: '/app-api/merchant/mini/saas/my-status' });
    if (st && st.expired) {
      uni.showModal({
        title: '套餐已到期',
        content: '续费后才能继续使用商户后台功能。订单 / 售后处理仍可正常进行。',
        confirmText: '立即续费',
        cancelText: '稍后处理',
        success: (r) => {
          if (r.confirm) uni.navigateTo({ url: '/pages/me/subscription' });
        },
      });
    }
  } catch {}
  load();
  loadOperatingStatus();
});

// 商户从其他页（如 shop-edit 改了营业时间）回来要刷新营业状态
onShow(() => {
  if (userStore.loggedIn && userStore.activeRole === 'merchant') {
    loadOperatingStatus();
  }
});

onPullDownRefresh(async () => {
  await Promise.all([load(), loadOperatingStatus()]);
  uni.stopPullDownRefresh();
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

/* 临时诊断标记（挪到屏幕中间、放大，确保不被状态栏遮住），确认后删除 */
.sbh-debug {
  position: fixed; top: 40%; left: 50%; transform: translate(-50%, -50%);
  z-index: 99999; background: rgba(255,0,0,.92); color: #fff;
  font-size: 56rpx; font-weight: 700; line-height: 1.4;
  padding: 24rpx 48rpx; border-radius: 20rpx; box-shadow: 0 8rpx 40rpx rgba(0,0,0,.4);
}

.page {
  // 渐变背景 — 整页底色不再纯白，让卡片浮起来
  background: linear-gradient(180deg, #fff6f0 0%, #f7f8fb 280rpx);
  min-height: 100vh;
  padding: 0 24rpx 220rpx;
}

/* ─── Hero 顶部渐变 ─── */
.safe-top { padding-top: calc(max(env(safe-area-inset-top, 0px), var(--status-bar-height, 0px)) + 24rpx); }

.hero {
  position: relative;
  padding: 8rpx 12rpx 32rpx;
  overflow: visible;
}
.hero-glow {
  position: absolute;
  top: -120rpx; left: -40rpx; right: -40rpx;
  height: 320rpx;
  background: radial-gradient(ellipse at 30% 80%, rgba(255, 167, 100, .35), transparent 70%);
  pointer-events: none;
  z-index: 0;
}
.hero-content {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.hero-left { flex: 1; min-width: 0; }
.greeting {
  font-size: 26rpx;
  color: $text-secondary;
  .emoji { margin-left: 8rpx; font-size: 26rpx; }
}
.shop-name {
  margin-top: 8rpx;
  font-size: 44rpx;
  font-weight: 800;
  color: $text-primary;
  letter-spacing: -0.5rpx;
}
.date-strip {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: $text-placeholder;
  .dot-sep { margin: 0 8rpx; }
}
.hero-avatar {
  width: 96rpx; height: 96rpx;
  line-height: 96rpx;
  text-align: center;
  border-radius: 50%;
  background: linear-gradient(135deg, $brand-primary, #ff9b5e);
  color: #fff;
  font-size: 48rpx;
  font-weight: 700;
  box-shadow: 0 8rpx 24rpx rgba(255, 107, 53, .35);
  flex-shrink: 0;
  margin-left: 24rpx;
}

/* ─── 营业打卡卡片 ─── */
.op-card {
  position: relative;
  margin-bottom: 32rpx;
  padding: 32rpx 36rpx;
  border-radius: 28rpx;
  overflow: hidden;
  transition: transform 0.15s;
  &:active { transform: scale(0.99); }
}
.op-card.pending {
  // 未打卡 — 醒目橙色，发光
  background: linear-gradient(135deg, #ff6b35 0%, #ff9b5e 100%);
  box-shadow: 0 16rpx 32rpx rgba(255, 107, 53, .35);
}
.op-card.open {
  // 营业中 — 绿色，平和
  background: linear-gradient(135deg, #10b981 0%, #22d3a3 100%);
  box-shadow: 0 12rpx 28rpx rgba(16, 185, 129, .28);
}
.op-card.closed {
  // 打烊 — 灰，朴素
  background: linear-gradient(135deg, #64748b 0%, #94a3b8 100%);
  box-shadow: 0 8rpx 20rpx rgba(100, 116, 139, .25);
}
.op-bg-deco {
  position: absolute;
  top: -80rpx; right: -80rpx;
  width: 240rpx; height: 240rpx;
  background: rgba(255, 255, 255, 0.12);
  border-radius: 50%;
  pointer-events: none;
}
.op-content {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 24rpx;
}
.op-icon-big {
  font-size: 64rpx;
  flex-shrink: 0;
  width: 96rpx; height: 96rpx;
  line-height: 96rpx;
  text-align: center;
  background: rgba(255, 255, 255, 0.22);
  border-radius: 28rpx;
  &.pulse {
    animation: pulse 1.8s ease-in-out infinite;
  }
}
@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.08); }
}
.op-text { flex: 1; min-width: 0; color: #fff; }
.op-title {
  font-size: 36rpx; font-weight: 800;
  letter-spacing: -0.5rpx;
  text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, .12);
}
.op-sub {
  margin-top: 6rpx;
  font-size: 24rpx;
  opacity: 0.9;
  line-height: 1.4;
}
.op-cta {
  flex-shrink: 0;
  padding: 16rpx 28rpx;
  background: #fff;
  color: $brand-primary;
  border-radius: 999rpx;
  font-size: 26rpx;
  font-weight: 700;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, .12);
  &.minor {
    background: rgba(255, 255, 255, 0.25);
    color: #fff;
    box-shadow: none;
  }
  &.primary {
    background: #fff;
    color: $text-primary;
  }
}

/* ─── 4 列 N 行宫格快捷（2 行 8 项）─── */
.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24rpx 16rpx;
  background: $bg-card;
  padding: 28rpx 20rpx;
  border-radius: 24rpx;
  margin-bottom: 32rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, .04);
}
.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}
.qi-ic {
  width: 96rpx; height: 96rpx;
  display: flex; align-items: center; justify-content: center;
  border-radius: 24rpx;
  font-size: 44rpx;
  color: #fff;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, .08);
}
.qi-lbl { font-size: 24rpx; color: $text-primary; font-weight: 500; }

/* ─── 今日数据 ─── */
.data-head {
  margin: 8rpx 12rpx 16rpx;
  display: flex; justify-content: space-between; align-items: center;
  .dh-title { font-size: 30rpx; font-weight: 700; color: $text-primary; }
  .dh-more { font-size: 24rpx; color: $brand-primary; }
}
.data-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
  margin-bottom: 32rpx;
}
.data-card {
  position: relative;
  padding: 24rpx;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  gap: 16rpx;
  overflow: hidden;
  transition: transform 0.15s;
  &:active { transform: scale(0.97); }
  &.orange { background: linear-gradient(135deg, #fff5ef, #ffe1c8); }
  &.blue   { background: linear-gradient(135deg, #eff6ff, #d6e9ff); }
  &.green  { background: linear-gradient(135deg, #f0fdf4, #c8f5d0); }
  &.purple { background: linear-gradient(135deg, #f8f5ff, #e5d6ff); }
}
.dc-ic {
  width: 72rpx; height: 72rpx;
  line-height: 72rpx;
  text-align: center;
  border-radius: 20rpx;
  font-size: 40rpx;
  background: rgba(255, 255, 255, 0.7);
  flex-shrink: 0;
}
.dc-body { flex: 1; min-width: 0; }
.dc-lbl { font-size: 22rpx; color: $text-secondary; }
.dc-val {
  margin-top: 6rpx;
  font-size: 40rpx;
  font-weight: 800;
  color: $text-primary;
  font-variant-numeric: tabular-nums;
  letter-spacing: -1rpx;
  &.warn { color: #ef4444; }
}

/* ─── 推广卡 ─── */
.promo-today {
  margin: 0 0 32rpx;
  padding: 24rpx;
  background: linear-gradient(135deg, #fff5e6, #ffe8d4);
  border: 2rpx solid #ffae74;
  border-radius: 20rpx;
  &:active { transform: scale(0.99); }
  .pt-head {
    display: flex; justify-content: space-between; align-items: center;
    margin-bottom: 16rpx;
    .pt-title { font-size: 28rpx; font-weight: 700; color: #d97706; }
    .pt-tag { font-size: 24rpx; color: #fff; background: $brand-primary; padding: 4rpx 16rpx; border-radius: 16rpx; }
  }
  .pt-row { display: flex; gap: 12rpx; }
  .pt-mini {
    flex: 1; padding: 12rpx;
    background: rgba(255, 255, 255, 0.7); border-radius: 12rpx;
    .l { display: block; font-size: 22rpx; color: $text-secondary; }
    .v { display: block; font-size: 30rpx; font-weight: 700; color: $text-primary; margin-top: 4rpx; }
  }
}

/* ─── 通用 card / section ─── */
.card {
  background: $bg-card;
  border-radius: 20rpx;
  padding: 28rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, .04);
}
.section { margin-bottom: 24rpx; }
.section .section-head {
  margin-bottom: 24rpx;
  display: flex; justify-content: space-between; align-items: center;
  .title { font-size: 30rpx; font-weight: 700; color: $text-primary; }
  .more { color: $brand-primary; font-size: 24rpx; }
}

/* ─── 7 天趋势 chart ─── */
.chart {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  height: 280rpx;
  padding-top: 48rpx;
}
.chart .bar {
  flex: 1;
  height: 100%;
  margin: 0 6rpx;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
}
.chart .bar-value {
  font-size: 18rpx;
  color: $text-secondary;
  margin-bottom: 6rpx;
}
.chart .bar-fill {
  width: 100%;
  background: linear-gradient(180deg, $brand-primary 0%, #ffae74 70%, #ffd1ba 100%);
  border-radius: 12rpx 12rpx 0 0;
  flex: 1;
  min-height: 16rpx;
  box-shadow: 0 -2rpx 8rpx rgba(255, 107, 53, .18);
}
.chart .bar-label {
  font-size: 20rpx;
  color: $text-secondary;
  margin-top: 12rpx;
  position: absolute;
  bottom: -36rpx;
}

/* ─── 排行 ─── */
.rank-list {
  .rank-item {
    display: flex;
    align-items: center;
    padding: 20rpx 0;
    border-bottom: 1rpx solid $border-color;
    &:last-child { border-bottom: none; }
  }
  .rank-no {
    width: 48rpx; height: 48rpx;
    line-height: 48rpx;
    text-align: center;
    border-radius: 50%;
    font-size: 26rpx;
    font-weight: 700;
    color: #fff;
    background: $text-placeholder;
    margin-right: 20rpx;
    &.rank-1 { background: linear-gradient(135deg, #ffcc00, #ffaa00); box-shadow: 0 2rpx 8rpx rgba(255, 204, 0, .4); }
    &.rank-2 { background: linear-gradient(135deg, #c0c5cf, #94a3b8); }
    &.rank-3 { background: linear-gradient(135deg, #d6793a, #b56428); }
  }
  .rank-name {
    flex: 1; font-size: 28rpx; color: $text-primary; font-weight: 500;
  }
  .rank-meta {
    display: flex; flex-direction: column;
    align-items: flex-end;
    font-size: 22rpx;
    color: $text-secondary;
    .rank-amount {
      color: $brand-primary; font-weight: 700; font-size: 26rpx; margin-top: 4rpx;
      font-variant-numeric: tabular-nums;
    }
  }
  .rank-empty {
    padding: 40rpx 0;
    text-align: center;
    color: $text-placeholder;
    font-size: 24rpx;
  }
}

.bottom-space { height: 80rpx; }
</style>
