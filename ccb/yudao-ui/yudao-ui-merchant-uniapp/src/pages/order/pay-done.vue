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
        · 这单已自动进入<text class="b">邀请激励</text>队列<br/>
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
const pending = ref(false);
const payOrderId = ref(null);
const payErr = ref('');

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
