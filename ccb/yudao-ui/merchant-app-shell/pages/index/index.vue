<template>
  <view class="shell">
    <!-- 全屏 web-view 加载线上商户端 H5；H5 在 webview 内即移动浏览器环境，
         原有 fetch/FileReader/上传/AI视频 等浏览器 API 原样可用 -->
    <web-view :src="url" @message="onMessage"></web-view>
  </view>
</template>

<script>
export default {
  data() {
    return {
      // 线上商户端 H5 入口（拓小二）。换地址只改这一处。
      url: 'https://tuo.doupaidoudian.com/m/',
    };
  },
  methods: {
    onMessage() {},
  },
};
</script>

<style>
.shell { width: 100%; height: 100%; }
</style>
