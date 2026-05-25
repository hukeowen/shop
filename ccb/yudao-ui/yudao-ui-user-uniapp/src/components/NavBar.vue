<template>
  <view class="nav-bar" :style="{ paddingTop: statusH + 'px', background: bg, color: txt }">
    <view class="nav-body">
      <view v-if="back" class="nav-back" @click="onBack">
        <text class="nav-back-ic">‹</text>
      </view>
      <view class="nav-title" :class="{ 'has-back': back }">
        <slot>{{ title }}</slot>
      </view>
      <view class="nav-right"><slot name="right" /></view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';

const props = defineProps({
  title: { type: String, default: '' },
  back:  { type: Boolean, default: true },
  bg:    { type: String, default: 'transparent' },
  txt:   { type: String, default: '#1B1F23' },
});

const statusH = ref(20);
onMounted(() => {
  try {
    const info = uni.getSystemInfoSync();
    statusH.value = info.statusBarHeight || 20;
  } catch {}
});

function onBack() {
  const pages = getCurrentPages ? getCurrentPages() : [];
  if (pages.length > 1) uni.navigateBack();
  else uni.switchTab({ url: '/pages/index/index' });
}
</script>

<style lang="scss" scoped>
.nav-bar {
  position: sticky; top: 0; z-index: 50;
}
.nav-body {
  height: 44px;
  display: flex; align-items: center;
  padding: 0 12px;
  position: relative;
}
.nav-back {
  width: 32px; height: 32px;
  display: flex; align-items: center; justify-content: center;
  border-radius: 16px;
  background: rgba(0,0,0,.04);
}
.nav-back-ic {
  font-size: 24px; line-height: 1; font-weight: 700; color: inherit;
  margin-top: -3px;
}
.nav-title {
  flex: 1; text-align: center;
  font-size: 16px; font-weight: 700;
  &.has-back { margin-right: 32px; }
}
.nav-right { width: 32px; display: flex; justify-content: flex-end; }
</style>
