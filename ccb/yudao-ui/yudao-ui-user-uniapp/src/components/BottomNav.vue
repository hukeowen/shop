<template>
  <view class="bn">
    <view class="bn-item" :class="{ active: active === 'index' }" @click="go('index')">
      <view class="ic">🏠</view><text>首页</text>
    </view>
    <view class="bn-item" :class="{ active: active === 'cart' }" @click="go('cart')">
      <view class="ic">🛒</view><text>购物车</text>
      <view v-if="cartCount > 0" class="bn-tab-badge">{{ cartCount }}</view>
    </view>
    <view class="bn-winners" :class="{ active: active === 'winners' }" @click="go('winners')">
      🏆
      <view class="bn-live-dot"></view>
    </view>
    <view class="bn-item" :class="{ active: active === 'order' }" @click="go('order')">
      <view class="ic">📋</view><text>订单</text>
    </view>
    <view class="bn-item" :class="{ active: active === 'me' }" @click="go('me')">
      <view class="ic">👤</view><text>我的</text>
    </view>
  </view>
</template>

<script setup>
const props = defineProps({
  active: { type: String, default: 'index' }, // index | cart | winners | order | me
  cartCount: { type: Number, default: 0 },
});

const ROUTE = {
  index:   '/pages/index/index',
  cart:    '/pages/cart/index',
  winners: '/pages/winners/index',
  order:   '/pages/order/list',
  me:      '/pages/me/index',
};

function go(k) {
  if (k === props.active) return;
  uni.reLaunch({ url: ROUTE[k] });
}
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.bn {
  position: fixed; bottom: 0; left: 0; right: 0;
  height: 76px;
  padding-bottom: env(safe-area-inset-bottom);
  background: rgba(255,255,255,.96);
  backdrop-filter: blur(20px) saturate(180%);
  border-top: 1px solid $line;
  display: flex; align-items: stretch;
  z-index: 50;
}
.bn-item {
  flex: 1;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  color: $t4; font-size: 10.5px; font-weight: 600; gap: 3px;
  position: relative;
}
.bn-item .ic {
  width: 26px; height: 26px;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px;
  transition: transform .25s cubic-bezier(.2,.8,.2,1);
}
.bn-item.active { color: $o-d; }
.bn-item.active .ic { transform: translateY(-2px) scale(1.1); }
.bn-item.active::after {
  content: ''; position: absolute; top: 6px;
  width: 4px; height: 4px; border-radius: 99px;
  background: $o; box-shadow: 0 0 8px $o;
}
.bn-tab-badge {
  position: absolute; top: 6px; right: 50%;
  transform: translateX(18px);
  min-width: 16px; height: 16px; padding: 0 4px;
  background: $danger; color: #fff;
  border-radius: 99px;
  font-size: 9px; font-weight: 800;
  display: flex; align-items: center; justify-content: center;
  border: 2px solid #fff;
}

/* 中奖榜浮起按钮（金渐变 + 实时红点） */
.bn-winners {
  width: 52px; height: 52px; border-radius: 50%;
  background: linear-gradient(135deg, $gold, $gold-d);
  display: flex; align-items: center; justify-content: center;
  margin-top: -14px;
  margin-left: 6px; margin-right: 6px;
  align-self: center;
  box-shadow: $sh-gold;
  color: #fff;
  font-size: 22px;
  position: relative;
}
.bn-winners.active {
  transform: scale(1.05);
  box-shadow: $sh-gold, 0 0 0 4px rgba(245,178,122,.3);
}
.bn-live-dot {
  position: absolute; top: 4px; right: 4px;
  width: 9px; height: 9px; border-radius: 50%;
  background: $danger;
  box-shadow: 0 0 6px $danger;
  border: 2px solid #fff;
  animation: live-pulse 1.5s ease-in-out infinite;
}
@keyframes live-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: .6; transform: scale(.85); }
}
</style>
