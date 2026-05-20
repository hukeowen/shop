<template>
  <view class="page">
    <view class="topbar">
      <text class="back" @click="goHome">‹</text>
      <text class="title">支付完成</text>
      <text class="right" @click="onShare">分享</text>
    </view>

    <view v-if="pending" class="succ-page pending">
      <view class="icon-circle pending">⌛</view>
      <view class="h2">等待付款</view>
      <view class="subtitle">点击下方按钮唤起通联收付通完成支付</view>
      <view class="pay-btn" @click="callPay">立即付款 ¥{{ payPriceYuan }}</view>
      <view class="pay-tip" v-if="payErr">{{ payErr }}</view>
    </view>
    <view v-else class="succ-page">
      <view class="icon-circle">✓</view>
      <view class="h2">支付成功</view>
    </view>

    <view v-if="info && info.found" class="succ-card">
      <view class="row"><text class="lbl">订单号</text><text class="val">{{ info.orderNo || info.orderId }}</text></view>
      <view class="row"><text class="lbl">下单店铺</text><text class="val">{{ info.shopName || '-' }}</text></view>
      <view class="row"><text class="lbl">支付金额</text><text class="val amt">¥{{ payPriceYuan }}</text></view>
    </view>

    <!-- 商品清单 -->
    <view v-if="info && info.items && info.items.length" class="items-card">
      <view class="items-title">已购买商品</view>
      <view v-for="it in info.items" :key="it.skuId" class="item-row">
        <image v-if="it.picUrl" class="item-pic" :src="it.picUrl" mode="aspectFill" />
        <view v-else class="item-pic placeholder">🛍</view>
        <view class="item-info">
          <view class="item-name">{{ it.spuName }}</view>
          <view class="item-meta">¥{{ (it.price / 100).toFixed(2) }} × {{ it.count }}</view>
        </view>
      </view>
    </view>

    <!-- 邀请激励：仅在订单含 tuijianEnabled 商品时显示 -->
    <view v-if="info && info.anyTuijian" class="succ-promo">
      <view class="hdr">🎁 推广奖励已就绪</view>
      <view class="body">
        · 这单已进入<text class="b">邀请激励</text>队列<br/>
        · 推荐朋友买同款 → 累计返推广积分<br/>
        · 详情见「我的钱包 · 推广积分流水」
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
const pending = ref(false);
const payOrderId = ref(null);
const payErr = ref('');
// 新版统一详情（merchant 端 /pay-done-info 跨租户安全）
const info = ref(null);

const payPriceYuan = computed(() => {
  const fen = info.value?.payPrice ?? order.value?.payPrice ?? 0;
  return (fen / 100).toFixed(2);
});

onLoad((q) => {
  orderId.value = q?.orderId || q?.id || '';
  tenantId.value = q?.tenantId ? Number(q.tenantId) : null;
  pending.value = q?.pending === '1';
  payOrderId.value = q?.payOrderId ? Number(q.payOrderId) : null;
  loadOrder();
  // 微信点金计划：在微信支付完成后，本页可能被嵌在 payapp.weixin.qq.com 的 iframe 里。
  // 不发 onIframeReady → 微信只显示自己的官方结果页 + 「点金计划」广告，看不到我们这页 + 商家自定义按钮。
  // 发 SHOW_CUSTOM_PAGE → 微信切到当前页 + 隐藏官方页底部广告。详见 https://prodoc.allinpay.com/doc/1551/
  try {
    if (typeof window !== 'undefined' && window.parent && window.parent !== window) {
      const mchData = { action: 'onIframeReady', displayStyle: 'SHOW_CUSTOM_PAGE' };
      window.parent.postMessage(JSON.stringify(mchData), 'https://payapp.weixin.qq.com');
    }
  } catch {}
});

// 点金计划「跳出 iframe」专用：微信支付完成页里调用 jumpOut，让顶层从结果页跳到 jumpOutUrl
// 浏览器直接打开（不在 iframe 里）→ fallback 普通跳转
function jumpOutOrNav(targetUrl) {
  try {
    if (typeof window !== 'undefined' && window.parent && window.parent !== window) {
      const mchData = { action: 'jumpOut', jumpOutUrl: targetUrl };
      window.parent.postMessage(JSON.stringify(mchData), 'https://payapp.weixin.qq.com');
      // 兜底：postMessage 后若 200ms 内微信没接管，自己跳
      setTimeout(() => { location.href = targetUrl; }, 200);
      return;
    }
  } catch {}
  location.href = targetUrl;
}

async function callPay() {
  if (!payOrderId.value) {
    payErr.value = '订单缺支付单号';
    return;
  }
  payErr.value = '';
  try {
    const res = await request({
      url: '/app-api/pay/order/submit',
      method: 'POST',
      data: { id: payOrderId.value, channelCode: 'allinpay_qr' },
      tenantId: tenantId.value || undefined,
    });
    // 通联返支付链接 / 二维码 / JSAPI 参数；H5 直接 location.href 跳转
    const dispCnt = res?.displayContent;
    if (typeof dispCnt === 'string' && (dispCnt.startsWith('http') || dispCnt.startsWith('weixin:'))) {
      location.href = dispCnt;
    } else {
      uni.showModal({
        title: '请扫码支付',
        content: '通联收付通已生成支付链接，请用微信扫一扫\n（生产对接 wx.chooseWXPay 唤起 JSAPI）',
        showCancel: false,
      });
    }
  } catch (e) {
    payErr.value = e?.message || '支付下单失败，请稍后重试';
  }
}

async function loadOrder() {
  if (!orderId.value || !tenantId.value) return;
  try {
    // merchant 端跨租户安全 endpoint：一次返订单 + 店铺 + 商品 + tuijian 标
    const res = await request({
      url: `/app-api/merchant/mini/order/pay-done-info?orderId=${encodeURIComponent(orderId.value)}&tenantId=${tenantId.value}`,
    });
    info.value = res || { found: false };
    shopName.value = info.value?.shopName || '';
  } catch {
    info.value = { found: false };
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
  // 嵌在微信结果页 iframe 时走 jumpOut；否则普通 reLaunch
  if (typeof window !== 'undefined' && window.parent && window.parent !== window) {
    const origin = location.origin;
    jumpOutOrNav(`${origin}/m/#/pages/user-order/list`);
    return;
  }
  uni.reLaunch({ url: '/pages/user-order/list' });
}
function goHome() {
  if (typeof window !== 'undefined' && window.parent && window.parent !== window) {
    const origin = location.origin;
    jumpOutOrNav(`${origin}/m/#/pages/user-home/index`);
    return;
  }
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

.items-card {
  margin: 24rpx 24rpx 0;
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 24rpx 28rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
  .items-title {
    font-size: 28rpx;
    font-weight: 600;
    color: $text-primary;
    padding-bottom: 16rpx;
    border-bottom: 1rpx solid $border-color;
  }
  .item-row {
    display: flex;
    align-items: center;
    gap: 20rpx;
    padding: 20rpx 0;
    border-bottom: 1rpx solid $border-color;
    &:last-child { border-bottom: none; }
  }
  .item-pic {
    width: 96rpx; height: 96rpx;
    border-radius: $radius-md;
    background: #f6f7f9;
    flex-shrink: 0;
    display: flex; align-items: center; justify-content: center;
    font-size: 40rpx;
    &.placeholder { color: $text-placeholder; }
  }
  .item-info { flex: 1; min-width: 0; }
  .item-name {
    font-size: 28rpx; color: $text-primary;
    overflow: hidden; text-overflow: ellipsis;
    white-space: nowrap;
  }
  .item-meta {
    margin-top: 8rpx; font-size: 24rpx;
    color: $text-secondary;
    font-variant-numeric: tabular-nums;
  }
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
