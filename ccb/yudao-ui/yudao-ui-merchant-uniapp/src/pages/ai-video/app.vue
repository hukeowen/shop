<template>
  <!-- App 专用：AI 一键成片重度依赖浏览器 API(canvas/blob/video)，无法在原生环境跑，
       故用 web-view 内嵌 H5 版（webview 本身是浏览器环境，功能 100% 完整）。
       本页有原生标题栏，点左上返回即可退出回原生 App。token 通过 URL 传给 H5。 -->
  <web-view v-if="url" :src="url" />
</template>

<script setup>
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { useUserStore } from '../../store/user.js';

const url = ref('');

// 商户端 H5 部署在 tuo.doupaidoudian.com/m/
const H5_BASE = 'https://tuo.doupaidoudian.com/m/#/pages/ai-video/index';

onLoad(() => {
  let token = '';
  try {
    const user = useUserStore();
    token = user.token || '';
  } catch {}
  if (!token) {
    try { token = uni.getStorageSync('token') || ''; } catch {}
  }
  // embed=1 让 H5 版隐藏自己的底部 tab 栏（避免双 tab）；tk 传登录态
  url.value = `${H5_BASE}?embed=1&tk=${encodeURIComponent(token)}`;
});
</script>
