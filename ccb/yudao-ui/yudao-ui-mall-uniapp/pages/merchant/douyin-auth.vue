<template>
  <web-view :src="authUrl" @message="onMessage" />
</template>

<script setup>
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';

const authUrl = ref('');

onLoad((options) => {
  if (options.url) {
    authUrl.value = decodeURIComponent(options.url);
  }
});

const onMessage = (e) => {
  // H5 页面授权完成后通过 postMessage 通知小程序
  if (e.detail && e.detail.data) {
    const data = e.detail.data[e.detail.data.length - 1];
    if (data.type === 'douyin_bindback') {
      uni.showToast({ title: '抖音绑定成功', icon: 'success' });
      uni.navigateBack();
    }
  }
};
</script>
