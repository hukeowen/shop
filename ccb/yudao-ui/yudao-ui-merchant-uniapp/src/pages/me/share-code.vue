<template>
  <view class="page">
    <view class="topbar safe-top">
      <text class="back" @click="goBack">‹</text>
      <text class="title">分享开店</text>
    </view>

    <view v-if="loading" class="loading">加载中…</view>

    <!-- 未付费拦截 -->
    <view v-else-if="errorCode === 403" class="paywall">
      <view class="lock-icon">🔒</view>
      <view class="paywall-title">试用商户暂无邀请权限</view>
      <view class="paywall-sub">升级到「邀三惠旺铺版 / 旗舰版」即可生成分享码邀请新商户加入</view>
      <button class="upgrade-btn" @click="goSubscription">立即升级套餐</button>
    </view>

    <!-- 加载错 -->
    <view v-else-if="errorMsg" class="empty">
      {{ errorMsg }}
      <button class="retry-btn" @click="load">重试</button>
    </view>

    <!-- 正常 -->
    <view v-else-if="code" class="content">
      <view class="hero">
        <view class="hero-title">🚀 邀请新商户加入邀三惠</view>
        <view class="hero-sub">每位通过你分享码加入并购买套餐的新商户，按规则给你返奖励</view>
      </view>

      <view class="card code-card">
        <view class="card-label">我的开店分享码</view>
        <view class="code-display">{{ code.code }}</view>
        <view class="copy-row">
          <button class="copy-btn" @click="copyCode">复制分享码</button>
          <button class="copy-btn link" @click="copyLink">复制邀请链接</button>
        </view>
      </view>

      <!-- 海报模式：带分销关系的推广二维码（扫码=带你的邀请码进入入驻页） -->
      <view class="card poster-card">
        <view class="card-label">📷 海报模式（长按二维码保存，发好友 / 朋友圈）</view>
        <view class="qr-box">
          <image v-if="qrUrl" :src="qrUrl" class="qr-img" mode="aspectFit" show-menu-by-longpress />
          <view v-else class="qr-ph">二维码生成中…</view>
        </view>
        <view class="qr-code-text">分享码 {{ code.code }}</view>
        <view class="hint">扫码直达入驻页 · 已自动带上你的邀请码（分销关系）</view>
      </view>

      <view class="card stat-card">
        <view class="stat-item">
          <text class="stat-num">{{ code.usedCount || 0 }}</text>
          <text class="stat-lbl">已邀请商户</text>
        </view>
        <view class="stat-item">
          <text class="stat-num">¥{{ (myBalance / 100).toFixed(2) }}</text>
          <text class="stat-lbl">累计邀请奖励</text>
        </view>
      </view>

      <view class="card">
        <view class="card-label">分享链接（贴朋友圈/微信）</view>
        <view class="share-url">{{ shareUrl }}</view>
        <view class="hint">扫码 / 点链接 → 直接进商户入驻页（带你的邀请码）</view>
      </view>

      <view class="rules">
        <view class="rules-title">奖励规则</view>
        <view class="rule">• 你必须自己也是付费商户（邀三惠旺铺版 / 旗舰版），才能拿邀请奖</view>
        <view class="rule">• 新商户通过你的码注册并买套餐 → 你拿首次贡献奖</view>
        <view class="rule">• 同一新商户在同一套餐上只触发一次首贡献奖</view>
        <view class="rule">• 升级套餐 / 续费 不会重复触发首贡献奖</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { getOrCreateMyShareCode } from '../../api/saas.js';
import { request } from '../../api/request.js';

const loading = ref(true);
const code = ref(null);
const errorCode = ref(0);
const errorMsg = ref('');
const myBalance = ref(0);

const shareUrl = computed(() => {
  if (!code.value) return '';
  // 「开店分享」给潜在商户用 → 落到商户端域名 tuo.（让被邀请人直接进入入驻页）
  let host = (typeof location !== 'undefined' ? location.host : 'tuo.doupaidoudian.com');
  // 当前可能在 tuo. / www. / admin. 任一域，强制改成 tuo.
  try {
    host = host.replace(/^(ke|www|admin)\./, 'tuo.');
    if (!/^tuo\./.test(host) && /doupaidoudian/.test(host)) {
      host = 'tuo.' + host.replace(/^[^.]+\./, '');
    }
  } catch {}
  const proto = (typeof location !== 'undefined' ? location.protocol : 'https:');
  return `${proto}//${host}/m/#/pages/merchant-apply/index?invite=${code.value.code}`;
});

// 海报二维码：走 sidecar /qr 出图（中心叠「邀三惠」），编码 shareUrl（含 invite 邀请码=分销关系）
const qrUrl = computed(() => {
  if (!shareUrl.value) return '';
  const base = (typeof location !== 'undefined' && location.origin) ? location.origin : '';
  return `${base}/qr?text=${encodeURIComponent(shareUrl.value)}&w=480&m=1&center=${encodeURIComponent('邀三惠')}`;
});

async function load() {
  loading.value = true;
  errorCode.value = 0;
  errorMsg.value = '';
  try {
    const r = await getOrCreateMyShareCode();
    code.value = r;
  } catch (e) {
    if (e?.code === 403) {
      errorCode.value = 403;
    } else {
      errorMsg.value = e?.message || '加载分享码失败';
    }
  } finally {
    loading.value = false;
  }
  // 顺便拉一下我的推广积分余额（A 拿的邀请奖落在 promo_point_balance）
  try {
    const acct = await request({ url: '/app-api/merchant/mini/promo/account' });
    myBalance.value = acct?.promoPointBalance || 0;
  } catch {}
}

function copyCode() {
  if (!code.value) return;
  uni.setClipboardData({ data: code.value.code, success: () => {
    uni.showToast({ title: '已复制分享码', icon: 'success' });
  }});
}

function copyLink() {
  if (!shareUrl.value) return;
  uni.setClipboardData({ data: shareUrl.value, success: () => {
    uni.showToast({ title: '已复制链接', icon: 'success' });
  }});
}

function goSubscription() {
  uni.navigateTo({ url: '/pages/me/subscription' });
}

function goBack() { uni.navigateBack(); }

onMounted(load);
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  min-height: 100vh;
  background: $bg-page;
  padding: 0 24rpx 80rpx;
}

.safe-top { padding-top: max(env(safe-area-inset-top, 0px), var(--status-bar-height, 0px)); }

.topbar {
  display: flex;
  align-items: center;
  padding: 24rpx 0 20rpx;
  .back {
    font-size: 56rpx;
    color: $text-primary;
    margin-right: 16rpx;
    line-height: 1;
  }
  .title {
    font-size: 36rpx;
    font-weight: 700;
    color: $text-primary;
  }
}

.loading {
  text-align: center;
  padding: 200rpx 0;
  color: $text-placeholder;
}

.paywall {
  margin: 80rpx 24rpx;
  padding: 60rpx 40rpx;
  background: linear-gradient(135deg, #fff7ed, #fff);
  border: 2rpx solid #fdba74;
  border-radius: $radius-lg;
  text-align: center;
  .lock-icon { font-size: 100rpx; margin-bottom: 24rpx; }
  .paywall-title { font-size: 36rpx; font-weight: 700; color: $text-primary; }
  .paywall-sub { font-size: 26rpx; color: $text-secondary; margin-top: 16rpx; line-height: 1.6; }
  .upgrade-btn {
    margin-top: 40rpx;
    height: 80rpx;
    line-height: 80rpx;
    background: $brand-primary;
    color: #fff;
    font-size: 30rpx;
    border-radius: $radius-md;
  }
}

.empty {
  text-align: center;
  padding: 200rpx 0;
  color: $text-placeholder;
  .retry-btn {
    display: inline-block;
    margin-top: 24rpx;
    padding: 12rpx 40rpx;
    background: $brand-primary;
    color: #fff;
    border-radius: $radius-md;
  }
}

.hero {
  padding: 32rpx 0 16rpx;
  text-align: center;
  .hero-title {
    font-size: 36rpx;
    font-weight: 700;
    color: $text-primary;
  }
  .hero-sub {
    margin-top: 12rpx;
    font-size: 24rpx;
    color: $text-secondary;
    line-height: 1.5;
  }
}

.card {
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 32rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
}

.card-label {
  font-size: 24rpx;
  color: $text-secondary;
}

.code-card {
  text-align: center;
}

.poster-card {
  text-align: center;
  .qr-box {
    width: 400rpx;
    height: 400rpx;
    margin: 20rpx auto 12rpx;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .qr-img { width: 400rpx; height: 400rpx; border-radius: $radius-md; }
  .qr-ph {
    width: 400rpx; height: 400rpx;
    background: #f5f5f5; border-radius: $radius-md;
    display: flex; align-items: center; justify-content: center;
    color: $text-placeholder; font-size: 26rpx;
  }
  .qr-code-text {
    font-size: 28rpx; font-weight: 700; color: $brand-primary;
    letter-spacing: 4rpx; margin-top: 4rpx;
  }
}

.code-display {
  font-size: 80rpx;
  font-weight: 800;
  letter-spacing: 12rpx;
  color: $brand-primary;
  margin: 24rpx 0;
  font-family: 'PingFang SC', monospace;
}

.copy-row {
  display: flex;
  gap: 16rpx;
  .copy-btn {
    flex: 1;
    height: 72rpx;
    line-height: 72rpx;
    background: $brand-primary;
    color: #fff;
    font-size: 26rpx;
    border-radius: $radius-md;
    border: none;
    &.link {
      background: rgba(255, 107, 53, 0.12);
      color: $brand-primary;
    }
    &::after { border: none; }
  }
}

.stat-card {
  display: flex;
  gap: 24rpx;
  .stat-item {
    flex: 1;
    text-align: center;
    .stat-num {
      display: block;
      font-size: 40rpx;
      font-weight: 700;
      color: $brand-primary;
    }
    .stat-lbl {
      display: block;
      margin-top: 8rpx;
      font-size: 22rpx;
      color: $text-secondary;
    }
  }
}

.share-url {
  margin-top: 16rpx;
  padding: 20rpx;
  background: #f6f7f9;
  border-radius: $radius-md;
  font-size: 22rpx;
  color: $text-secondary;
  word-break: break-all;
  font-family: monospace;
}

.hint {
  margin-top: 12rpx;
  font-size: 22rpx;
  color: $text-placeholder;
}

.rules {
  margin-top: 32rpx;
  .rules-title {
    font-size: 26rpx;
    color: $text-primary;
    font-weight: 600;
    margin-bottom: 12rpx;
  }
  .rule {
    margin-top: 8rpx;
    font-size: 22rpx;
    color: $text-secondary;
    line-height: 1.6;
  }
}
</style>
