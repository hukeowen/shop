<script>
import { useUserStore } from './store/user.js';
import { savePendingReferrer } from './utils/referral.js';

// H5 落地：把 ?inviter= / ?referrerUserId= 暂存到 localStorage，
// 即使后续 reLaunch 到 login 也不会丢 — 登录后由 referral.js flush 自动绑定。
function captureLandingInviter() {
  try {
    if (typeof location === 'undefined' || !location.search) return null;
    const sp = new URLSearchParams(location.search);
    const inviter = sp.get('inviter') || sp.get('referrerUserId');
    if (inviter) savePendingReferrer(inviter);
    return null;
  } catch {
    return null;
  }
}

// 把当前 H5 URL（含 query）保存为登录后的 redirect 目标，
// 并返回该目标 route（caller 用来决定要不要让页面自己加载）。
// 仅当落地是"非默认页"（不是 login / index / user-home）时返回。
function captureRedirect() {
  try {
    if (typeof location === 'undefined') return '';
    // hash 路由：location.hash 形如 "#/pages/shop-home/index?inviter=1"
    const route = location.hash && location.hash.startsWith('#/')
      ? location.hash.slice(1)
      : '';

    // 特殊场景：商户分享 URL 形如 /m/shop-home?tenantId=171&inviter=11#/pages/login/index
    //   - hash 是 /pages/login/index（被 login 白名单过滤掉）
    //   - 但 location.search 里有 tenantId — 普通用户登录后应跳到 shop-home（沉浸式店铺详情）
    // 处理：检查顶层 ?tenantId=...，有则构造 redirect 到 shop-home 而不是默认 user-home
    if (typeof location !== 'undefined' && location.search && /[?&]tenantId=/.test(location.search)) {
      const sp = new URLSearchParams(location.search);
      const tenantId = sp.get('tenantId');
      const inviter = sp.get('inviter') || sp.get('referrerUserId') || '';
      if (tenantId) {
        const params = [`tenantId=${encodeURIComponent(tenantId)}`];
        if (inviter) params.push(`inviter=${encodeURIComponent(inviter)}`);
        const shopHomeRoute = `/pages/shop-home/index?${params.join('&')}`;
        if (typeof localStorage !== 'undefined') {
          localStorage.setItem('redirect:after-login', shopHomeRoute);
        }
        return shopHomeRoute;
      }
    }

    if (!route) return '';
    if (route.startsWith('/pages/login/')) return '';
    if (route.startsWith('/pages/index/')) return '';
    if (route.startsWith('/pages/user-home/')) return '';
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem('redirect:after-login', route);
    }
    return route;
  } catch {
    return '';
  }
}

export default {
  onLaunch() {
    // 1) 落地先抓 inviter（不管有没 token，都尽早暂存）
    captureLandingInviter();
    const landingRoute = captureRedirect();

    // 2) 从 localStorage 恢复登录态
    const userStore = useUserStore();
    userStore.hydrate();
    // eslint-disable-next-line no-console
    console.log(
      '[摊小二] App Launched, role=', userStore.activeRole,
      'hasToken=', !!userStore.token, 'landing=', landingRoute || '(default)'
    );

    // ⭐ 关键：落地在非默认页（如 shop-home?inviter=1）时绝不强跳，让该页自己加载。
    // 未登录时由该页的拦截 / 首个需登录请求触发跳转 login（带 redirect）。
    if (landingRoute) {
      return;
    }

    // 默认行为（落地是 / 或 #/pages/login 或 #/pages/index 或 #/pages/user-home 时）：
    // Member role → route to user home
    if (userStore.token && userStore.activeRole === 'member') {
      try { uni.reLaunch({ url: '/pages/user-home/index' }); } catch {}
      return;
    }
    // 首次启动：无 token → 跳登录
    if (!userStore.token) {
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
