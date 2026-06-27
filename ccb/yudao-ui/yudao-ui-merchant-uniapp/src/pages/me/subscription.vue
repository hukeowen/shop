<template>
  <view class="page">
    <view class="topbar safe-top">
      <text class="back" @click="goBack">‹</text>
      <text class="title">续费 / 升级</text>
    </view>

    <!-- 当前状态 -->
    <view v-if="status" :class="['cur', status.expired ? 'expired' : '']">
      <view class="cur-row">
        <text class="lbl">当前套餐</text>
        <text class="val">{{ status.packageName || levelLabel(status.level) }}</text>
      </view>
      <view class="cur-row">
        <text class="lbl">到期时间</text>
        <text class="val">{{ status.isPlatform ? '永久（平台商户）' : (status.expireAt ? formatTime(status.expireAt) : '未订阅') }}</text>
      </view>
      <view class="cur-row">
        <text class="lbl">AI 视频余量</text>
        <text class="val">{{ status.aiVideoQuota || 0 }} 条</text>
      </view>
    </view>

    <!-- 套餐选择 -->
    <view class="pkg-list">
      <view
        v-for="pkg in packages"
        :key="pkg.id"
        :class="['pkg-card', pkg.level === 'PRO' ? 'pro' : '', selectedLevel === pkg.level ? 'on' : '']"
        @click="selectedLevel = pkg.level"
      >
        <view class="pkg-tag" v-if="pkg.level === 'PRO'">推荐</view>
        <view class="pkg-name">{{ pkg.name }}</view>
        <view class="pkg-price">
          <text class="rmb">¥</text>
          <text class="num">{{ smartYuan(pkg.priceFen) }}</text>
          <text class="unit">/年</text>
        </view>
        <view class="pkg-features">
          <view class="feat" v-for="f in featuresOf(pkg)" :key="f">✓ {{ f }}</view>
          <view class="feat ai-grant">🎁 赠送 AI 视频 {{ pkg.aiVideoGrant }} 条</view>
        </view>
      </view>
    </view>

    <view class="tip" v-if="status && status.isPlatform">
      平台商户无需续费，永久全功能可用
    </view>

    <view class="bottom-bar safe-bottom" v-if="!status || !status.isPlatform">
      <view class="total">
        <text v-if="selectedPackage">¥{{ smartYuan(selectedPackage.priceFen) }} / 年</text>
        <text v-else class="placeholder">请选择套餐</text>
      </view>
      <view :class="['pay-btn', selectedPackage ? '' : 'disabled']" @click="onPurchase">
        立即支付
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { request } from '../../api/request.js';
import { smartYuan } from '../../utils/format.js';

const packages = ref([]);
const status = ref(null);
const selectedLevel = ref('PRO');

const selectedPackage = computed(() => packages.value.find(p => p.level === selectedLevel.value) || null);

function levelLabel(level) {
  return ({
    PLATFORM: '平台商户',
    PRO: '拓小二旗舰版',
    BASIC: '拓小二旺铺版',
    TRIAL: '试用版（30 天旗舰体验）',
    EXPIRED: '已过期',
  })[level] || level;
}
function featuresOf(pkg) {
  // features 后端是 JSON 数组字符串
  try {
    const arr = pkg.features ? JSON.parse(pkg.features) : [];
    const map = {
      order: '订单系统',
      tuijian: '邀请激励 推广',
      team: '店内 / 星级奖励',
      star: '星级体系',
      pool: '积分池',
      brokerage: '商户推广奖励',
    };
    return arr.map(k => map[k] || k);
  } catch {
    return [];
  }
}
function formatTime(ts) {
  if (!ts) return '-';
  const d = new Date(ts);
  if (isNaN(d.getTime())) return ts;
  const pad = (n) => (n < 10 ? '0' + n : n);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}
function goBack() { uni.navigateBack(); }

async function loadAll() {
  try {
    const [pkgs, st] = await Promise.all([
      request({ url: '/app-api/merchant/mini/saas/packages' }),
      request({ url: '/app-api/merchant/mini/saas/my-status' }),
    ]);
    packages.value = pkgs || [];
    status.value = st || null;
  } catch (e) {
    uni.showToast({ title: '加载失败', icon: 'none' });
  }
}

async function onPurchase() {
  if (!selectedPackage.value) {
    uni.showToast({ title: '请先选择套餐', icon: 'none' });
    return;
  }
  uni.showLoading({ title: '生成支付链接...' });
  try {
    const res = await request({
      url: `/app-api/merchant/mini/saas/purchase?level=${selectedLevel.value}`,
      method: 'POST',
    });
    uni.hideLoading();
    if (res && res.cashierUrl) {
      uni.showToast({ title: '跳转通联支付', icon: 'none', duration: 800 });
      setTimeout(() => { location.href = res.cashierUrl; }, 500);
    } else {
      uni.showModal({
        title: '支付链接生成失败',
        content: '通联接口暂时不可用，请稍后重试',
        showCancel: false,
      });
    }
  } catch (e) {
    uni.hideLoading();
    uni.showModal({
      title: '支付失败',
      content: e?.message || '请稍后重试',
      showCancel: false,
    });
  }
}

onMounted(() => loadAll());
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  min-height: 100vh;
  background: $bg-page;
  padding: 0 24rpx 200rpx;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: calc(env(safe-area-inset-top) + 24rpx) 0 20rpx;

  .back { font-size: 48rpx; color: $text-primary; width: 60rpx; }
  .title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; margin-right: 60rpx; }
}

.cur {
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 28rpx 32rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.04);

  &.expired {
    background: linear-gradient(135deg, #ffeaea, #ffd1d1);
  }

  .cur-row {
    display: flex;
    justify-content: space-between;
    font-size: 28rpx;
    margin: 8rpx 0;
    .lbl { color: $text-secondary; }
    .val { color: $text-primary; font-weight: 500; }
  }
}

.pkg-list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  margin-bottom: 32rpx;
}

.pkg-card {
  position: relative;
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 32rpx;
  border: 4rpx solid transparent;
  box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.04);
  transition: all 0.2s;

  &.on {
    border-color: $brand-primary;
    transform: scale(1.01);
    box-shadow: 0 4rpx 24rpx rgba(255, 107, 53, 0.18);
  }

  &.pro {
    background: linear-gradient(135deg, #fff8f0, #fff);
  }

  .pkg-tag {
    position: absolute;
    top: 0;
    right: 32rpx;
    background: $brand-primary;
    color: #fff;
    font-size: 22rpx;
    padding: 4rpx 16rpx;
    border-radius: 0 0 12rpx 12rpx;
  }

  .pkg-name {
    font-size: 34rpx;
    font-weight: 700;
    color: $text-primary;
    margin-bottom: 16rpx;
  }

  .pkg-price {
    display: flex;
    align-items: baseline;
    color: $brand-primary;
    margin-bottom: 24rpx;

    .rmb { font-size: 26rpx; }
    .num { font-size: 56rpx; font-weight: 800; margin: 0 4rpx; }
    .unit { font-size: 26rpx; color: $text-secondary; }
  }

  .pkg-features {
    .feat {
      font-size: 26rpx;
      color: $text-regular;
      margin: 8rpx 0;

      &.ai-grant {
        color: #d97706;
        font-weight: 600;
      }
    }
  }
}

.tip {
  text-align: center;
  color: $text-secondary;
  font-size: 26rpx;
  padding: 40rpx 0;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: $bg-card;
  display: flex;
  align-items: center;
  padding: 24rpx 32rpx calc(env(safe-area-inset-bottom) + 24rpx);
  box-shadow: 0 -2rpx 12rpx rgba(0,0,0,0.06);

  .total {
    flex: 1;
    font-size: 32rpx;
    color: $brand-primary;
    font-weight: 700;
    .placeholder { color: $text-placeholder; font-weight: 400; font-size: 26rpx; }
  }

  .pay-btn {
    width: 280rpx;
    height: 88rpx;
    line-height: 88rpx;
    text-align: center;
    background: $brand-primary;
    color: #fff;
    font-size: 30rpx;
    font-weight: 600;
    border-radius: $radius-md;

    &.disabled {
      background: $text-placeholder;
    }
  }
}
</style>
