<!--
  按 activeRole 自渲染底部 tab。
  原 pages.json 全局 tabBar 只能配 1 套，跟商户/用户混用导致用户登录后点 tab 跳商户工作台。
  新方案：去掉全局 tabBar，让每个主页（商户 4 个 + 用户 3 个）自己引入这个组件。
-->
<template>
  <view class="role-tabbar safe-bottom" :style="{ background: bg }">
    <view
      v-for="t in tabs"
      :key="t.path"
      class="tab-item"
      :class="{ active: t.active }"
      @click="onTap(t)"
    >
      <text class="ic">{{ t.icon }}</text>
      <text class="lbl">{{ t.label }}</text>
    </view>
  </view>
  <!-- 占位防止内容被底栏遮住 -->
  <view class="role-tabbar-placeholder" aria-hidden="true"></view>
</template>

<script setup>
import { computed } from 'vue';
import { useUserStore } from '../store/user.js';

const props = defineProps({
  current: { type: String, required: true }, // 当前页面 path（用于高亮）
  bg: { type: String, default: '#FFFFFF' },
  // 强制角色：'member' / 'merchant'。不传则按 userStore.activeRole 自适应。
  // shop-home（C 端进店页）应传 'member' 即使商户账号登录也走用户 tab。
  forceRole: { type: String, default: '' },
});

const userStore = useUserStore();

const merchantTabs = [
  { path: '/pages/index/index',     label: '工作台', icon: '🏠' },
  { path: '/pages/ai-video/index',  label: '成片',   icon: '🎬' },
  { path: '/pages/order/list',      label: '订单',   icon: '📋' },
  { path: '/pages/me/index',        label: '我的',   icon: '👤' },
];
const userTabs = [
  { path: '/pages/user-home/index', label: '首页',   icon: '🏠' },
  { path: '/pages/user-order/list', label: '订单',   icon: '📋' },
  { path: '/pages/cart/index',      label: '购物车', icon: '🛒' },
  { path: '/pages/user-me/index',   label: '我的',   icon: '👤' },
];

const tabs = computed(() => {
  const role = props.forceRole || userStore.activeRole;
  const list = role === 'merchant' ? merchantTabs : userTabs;
  return list.map((t) => ({
    ...t,
    active: t.path.replace(/^\//, '').startsWith(props.current.replace(/^\//, '').replace(/\/$/, '')),
  }));
});

function onTap(t) {
  if (t.active) return;
  uni.reLaunch({ url: t.path });
}
</script>

<style lang="scss" scoped>
@import '../uni.scss';

.role-tabbar {
  position: fixed; left: 0; right: 0; bottom: 0; z-index: 100;
  display: flex;
  height: 100rpx;
  border-top: 1rpx solid $border-color;
  padding-bottom: env(safe-area-inset-bottom);
  box-shadow: 0 -2rpx 12rpx rgba(0,0,0,0.04);

  .tab-item {
    flex: 1;
    display: flex; flex-direction: column; align-items: center; justify-content: center;
    color: #8A8F99;
    font-size: 22rpx;
    .ic { font-size: 42rpx; line-height: 1; margin-bottom: 4rpx; }
    .lbl { font-size: 22rpx; }
    &.active { color: $brand-primary; }
  }
}

/* 占位高度 = 真实 tab 高 + 安全区，防止内容滚到底部被遮 */
.role-tabbar-placeholder {
  height: calc(100rpx + env(safe-area-inset-bottom));
  width: 100%;
}
</style>
