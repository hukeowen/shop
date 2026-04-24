<script>
import { useUserStore } from './store/user.js';

export default {
  onLaunch() {
    // 从 localStorage 恢复登录态
    const userStore = useUserStore();
    userStore.hydrate();
    // eslint-disable-next-line no-console
    console.log('[摊小二] App Launched, role=', userStore.activeRole, 'hasToken=', !!userStore.token);

    // Member role → route to user home
    if (userStore.token && userStore.activeRole === 'member') {
      try { uni.reLaunch({ url: '/pages/user-home/index' }); } catch {}
      return;
    }

    // 首次启动：无 token → 跳登录；有 token 但没选身份 → 跳登录让他选
    if (!userStore.token) {
      // 启动时如果当前已经不是登录页，则跳过去
      try {
        uni.reLaunch({ url: '/pages/login/index' });
      } catch {
        // 某些生命周期下 reLaunch 会抛，忽略
      }
    }
  },
  onShow() {},
  onHide() {},
};
</script>

<style lang="scss">
@import './uni.scss';

page {
  background: #f6f7f9;
  color: #1c1f23;
  font-family: -apple-system, BlinkMacSystemFont, 'Helvetica Neue', 'PingFang SC',
    'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
  font-size: 28rpx;
}

view,
text {
  box-sizing: border-box;
}

.safe-bottom {
  padding-bottom: env(safe-area-inset-bottom);
}
</style>
