<template>
  <view class="page">
    <view :style="sbhSpacer"></view>
    <view class="redirecting">正在跳转到附近店铺…</view>
  </view>
</template>

<script setup>
import { onMounted } from 'vue';
import { sbhSpacerStyle } from '@/utils/safeTop.js';
const sbhSpacer = sbhSpacerStyle();

onMounted(() => {
  // SaaS 多租户下"全平台商品分类"不可用：每家商户各自建分类、id/名重复（如多家都叫"上传"），
  // 跨店聚合无意义。统一 redirect 到附近店铺：带 businessType 的精准跳；否则跳"全部"。
  const o = (() => { try { const ps = getCurrentPages(); return ps[ps.length - 1]?.options || {}; } catch { return {}; } })();
  const BIZ_TYPES = ['food', 'tea', 'bake', 'fresh', 'beauty', 'super'];
  const target = o.k && BIZ_TYPES.includes(o.k) ? `/pages/nearby/index?bt=${o.k}` : '/pages/nearby/index';
  uni.redirectTo({ url: target });
});
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background: $bg; display: flex; align-items: center; justify-content: center; }
.redirecting { color: $t4; font-size: 14px; }
</style>
