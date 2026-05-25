<template>
  <view class="bn">
    <view
      v-for="t in tabs"
      :key="t.k"
      class="bn-item"
      :class="{ active: t.k === active }"
      @click="go(t)"
    >
      <view class="bn-ic">{{ t.ic }}</view>
      <view class="bn-tx">{{ t.tx }}</view>
      <view v-if="t.k === active" class="bn-dot"></view>
    </view>
  </view>
</template>

<script setup>
const props = defineProps({
  active: { type: String, default: 'index' }, // index | nearby | winners | me
});
const tabs = [
  { k: 'index',   tx: '首页',   ic: '🏠', url: '/pages/index/index' },
  { k: 'nearby',  tx: '附近',   ic: '📍', url: '/pages/nearby/index' },
  { k: 'winners', tx: '中奖榜', ic: '🏆', url: '/pages/winners/index' },
  { k: 'me',      tx: '我的',   ic: '👤', url: '/pages/me/index' },
];
function go(t) {
  if (t.k === props.active) return;
  uni.reLaunch({ url: t.url });
}
</script>

<style lang="scss" scoped>
.bn {
  position: fixed; left: 0; right: 0; bottom: 0;
  display: flex;
  background: #FFFFFFF2;
  backdrop-filter: blur(20px) saturate(180%);
  border-top: 1px solid #EEF0F4;
  z-index: 30;
  padding-bottom: env(safe-area-inset-bottom);
}
.bn-item {
  flex: 1;
  padding: 8px 0 10px;
  display: flex; flex-direction: column; align-items: center; gap: 2px;
  color: #94A3B8;
  font-size: 11px; font-weight: 600;
  position: relative;
  transition: color .2s;
}
.bn-item.active {
  color: #FF6B35;
}
.bn-ic { font-size: 22px; line-height: 1; }
.bn-dot {
  position: absolute; bottom: 4px;
  width: 14px; height: 3px;
  border-radius: 2px;
  background: linear-gradient(90deg, #FF6B35, #D4920A);
}
</style>
