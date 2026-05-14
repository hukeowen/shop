<template>
  <view class="redir">
    <view class="spinner" />
    <view class="hint">{{ hint }}</view>
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { useUserStore } from '../../store/user.js';

const userStore = useUserStore();
const hint = ref('正在进入…');

// 立即按 token + activeRole 决定目标页 —— 不再展示任何"选择角色 / 欢迎"中转 UI。
//   有 token：消费 redirect 或按 role 跳；activeRole=merchant → 商户工作台
//   无 token：把 redirect 留在 localStorage（用户登录后再回），直接跳用户首页避免 401 死循环
function decide(query) {
  // 收集 redirect（query 优先于已暂存的）
  let target = '';
  if (query?.redirect) {
    try { target = decodeURIComponent(query.redirect); } catch {}
  } else {
    try {
      if (typeof localStorage !== 'undefined') {
        target = localStorage.getItem('redirect:after-login') || '';
      }
    } catch {}
  }

  // 已登录：消费 redirect 或按 role 跳
  if (userStore.token) {
    try {
      if (target && typeof localStorage !== 'undefined') {
        localStorage.removeItem('redirect:after-login');
      }
    } catch {}
    if (target) {
      uni.reLaunch({ url: target });
      return;
    }
    if (userStore.activeRole === 'merchant') {
      uni.reLaunch({ url: '/pages/index/index' });
      return;
    }
    uni.reLaunch({ url: '/pages/user-home/index' });
    return;
  }

  // 未登录：保存 redirect 但直接进用户首页（user-home 允许未登录浏览，下单等需登录的动作各自处理）
  if (target) {
    try {
      if (typeof localStorage !== 'undefined') {
        localStorage.setItem('redirect:after-login', target);
      }
    } catch {}
  }
  uni.reLaunch({ url: '/pages/user-home/index' });
}

onLoad((query) => {
  // 给 reLaunch 一个微小延迟让用户在弱网下也能看到 spinner，强网几乎瞬间跳走
  setTimeout(() => decide(query), 30);
});
</script>

<style lang="scss" scoped>
.redir {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, #fff3ec 0%, #f6f7f9 60%);
}
.spinner {
  width: 80rpx;
  height: 80rpx;
  border: 6rpx solid rgba(255, 107, 53, 0.18);
  border-top-color: #ff6b35;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}
.hint {
  margin-top: 24rpx;
  font-size: 26rpx;
  color: #6b7280;
}
@keyframes spin { to { transform: rotate(360deg); } }
</style>
