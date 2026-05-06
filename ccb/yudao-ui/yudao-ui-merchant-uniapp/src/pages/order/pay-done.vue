<template>
  <view class="page">
    <view class="topbar">
      <text class="back" @click="goHome">‹</text>
      <text class="title">支付完成</text>
      <text class="right" @click="onShare">分享</text>
    </view>

    <view class="succ-page">
      <view class="icon-circle">✓</view>
      <view class="h2">支付成功</view>
      <view class="subtitle">订单已通知商家，预计 30 分钟内出餐</view>
    </view>

    <view v-if="order" class="succ-card">
      <view class="row"><text class="lbl">订单号</text><text class="val">{{ order.orderNo || order.id }}</text></view>
      <view class="row"><text class="lbl">下单店铺</text><text class="val">{{ order.shopName || shopName || '-' }}</text></view>
      <view class="row"><text class="lbl">商品数量</text><text class="val">{{ order.itemCount || 0 }} 件</text></view>
      <view class="row"><text class="lbl">支付方式</text><text class="val">{{ payMethodText }}</text></view>
      <view class="row amt"><text class="lbl">支付金额</text><text class="val">¥{{ payPriceYuan }}</text></view>
    </view>

    <view class="succ-promo">
      <view class="hdr">🎁 推广奖励已就绪</view>
      <view class="body">
        · 这单已自动进入<text class="b">推 N 反 1</text>队列<br/>
        · <text class="b">推荐 4 个朋友</text>买同款 → 累计返 <text class="b">¥5</text> 推广积分<br/>
        · 当前位置：<text class="b">B 层第 3 位</text>，等下个朋友进店触发首次返奖
      </view>
      <view class="actions">
        <view class="btn ghost-brand" @click="onCopyLink">复制邀请链接</view>
        <view class="btn primary" @click="onShare">微信分享 ›</view>
      </view>
    </view>

    <view class="succ-actions">
      <view class="btn ghost" @click="goOrder">查看订单</view>
      <view class="btn primary" @click="goHome">回首页继续逛</view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { request } from '../../api/request.js';
import { useUserStore } from '../../store/user.js';

const userStore = useUserStore();
const order = ref(null);
const shopName = ref('');
const orderId = ref('');
const tenantId = ref(null);

const payPriceYuan = computed(() => {
  const fen = order.value?.payPrice ?? order.value?.totalPrice ?? 0;
  return (fen / 100).toFixed(2);
});
const payMethodText = computed(() => {
  const t = order.value?.payChannel || order.value?.payType;
  if (!t) return '微信支付';
  const map = { wx_pub: '微信支付', wx_mp: '微信支付', alipay_qr: '支付宝', balance: '余额抵扣' };
  return map[t] || t;
});

onLoad((q) => {
  orderId.value = q?.orderId || q?.id || '';
  tenantId.value = q?.tenantId ? Number(q.tenantId) : null;
  loadOrder();
});

async function loadOrder() {
  if (!orderId.value) return;
  try {
    const res = await request({
      url: `/app-api/trade/order/get?id=${encodeURIComponent(orderId.value)}`,
      tenantId: tenantId.value || undefined,
    });
    order.value = res || {};
    shopName.value = res?.shopName || '';
  } catch {
    order.value = { id: orderId.value };
  }
}

function onCopyLink() {
  const uid = userStore.userId;
  const tid = tenantId.value || uni.getStorageSync('lastShopTenantId');
  const origin = (typeof location !== 'undefined' && location.origin) || 'https://www.doupaidoudian.com';
  const link = `${origin}/m/shop-home?tenantId=${tid || ''}&inviter=${uid || ''}`;
  uni.setClipboardData({
    data: link,
    success: () => uni.showToast({ title: '邀请链接已复制', icon: 'success' }),
    fail: () => uni.showToast({ title: '复制失败', icon: 'none' }),
  });
}
function onShare() {
  // H5 微信分享：通过 wx.share API 或简单走 navigator.share，兼容性差，提示用户长按或复制
  uni.showModal({
    title: '微信分享',
    content: '请长按上方按钮选「复制」后到微信粘贴给好友；或点击「复制邀请链接」自行分享',
    showCancel: false,
  });
}
function goOrder() {
  uni.reLaunch({ url: '/pages/user-order/list' });
}
function goHome() {
  uni.reLaunch({ url: '/pages/user-home/index' });
}
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page { min-height: 100vh; background: $bg-page; padding-bottom: 48rpx; }

.topbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: calc(env(safe-area-inset-top) + 24rpx) 32rpx 20rpx;
  background: linear-gradient(180deg, #fff5ef, transparent);
  .back { font-size: 48rpx; color: $text-primary; width: 60rpx; }
  .title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; }
  .right { width: 60rpx; text-align: right; color: $brand-primary; font-size: 28rpx; }
}

.succ-page {
  text-align: center; padding: 48rpx 32rpx 24rpx;
  .icon-circle {
    width: 120rpx; height: 120rpx; line-height: 120rpx;
    border-radius: 50%; background: $brand-primary; color: #fff;
    font-size: 72rpx; font-weight: 700; margin: 0 auto 24rpx;
  }
  .h2 { font-size: 40rpx; font-weight: 700; color: $text-primary; }
  .subtitle { color: $text-regular; margin-top: 12rpx; font-size: 26rpx; }
}

.succ-card {
  margin: 24rpx 24rpx 0;
  background: $bg-card; border-radius: $radius-lg;
  padding: 24rpx 28rpx;
  box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.04);
  .row {
    display: flex; justify-content: space-between; align-items: center;
    padding: 14rpx 0;
    border-bottom: 1rpx solid $border-color;
    .lbl { color: $text-secondary; font-size: 26rpx; }
    .val { color: $text-primary; font-size: 28rpx; font-weight: 600; }
    &:last-child { border-bottom: none; }
    &.amt .val { color: $brand-primary; font-size: 36rpx; }
  }
}

.succ-promo {
  margin: 24rpx 24rpx 0;
  background: linear-gradient(135deg, #fff5ef 0%, #ffe4d2 100%);
  border-radius: $radius-lg;
  padding: 28rpx;
  .hdr { font-size: 30rpx; font-weight: 700; color: $brand-primary-dark; margin-bottom: 16rpx; }
  .body { color: $text-primary; font-size: 26rpx; line-height: 1.8; .b { color: $brand-primary; font-weight: 700; } }
  .actions {
    display: flex; gap: 16rpx; margin-top: 24rpx;
    .btn {
      flex: 1; height: 80rpx; line-height: 80rpx; text-align: center;
      border-radius: $radius-md; font-size: 28rpx; font-weight: 600;
    }
    .ghost-brand { background: #fff; color: $brand-primary; border: 2rpx solid $brand-primary-light; }
    .primary { background: $brand-primary; color: #fff; }
  }
}

.succ-actions {
  display: flex; gap: 24rpx; padding: 32rpx 24rpx;
  .btn {
    flex: 1; height: 96rpx; line-height: 96rpx; text-align: center;
    border-radius: $radius-pill; font-size: 30rpx; font-weight: 600;
    &.ghost { background: #fff; color: $text-primary; border: 1rpx solid $border-color; }
    &.primary { background: $brand-primary; color: #fff; }
  }
}
</style>
