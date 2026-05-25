<template>
  <view class="empty">
    <view class="empty-ic">{{ icon }}</view>
    <view class="empty-title">{{ title }}</view>
    <view v-if="desc" class="empty-desc">{{ desc }}</view>
    <slot />
  </view>
</template>

<script setup>
defineProps({
  icon: { type: String, default: '🪶' },
  title: { type: String, default: '暂无数据' },
  desc:  { type: String, default: '' },
});
</script>

<style lang="scss" scoped>
.empty {
  padding: 60px 24px;
  display: flex; flex-direction: column; align-items: center;
}
.empty-ic   { font-size: 56px; opacity: .6; margin-bottom: 12px; }
.empty-title{ font-size: 15px; font-weight: 700; color: #5A6577; }
.empty-desc { font-size: 12px; color: #94A3B8; margin-top: 6px; }
</style>
