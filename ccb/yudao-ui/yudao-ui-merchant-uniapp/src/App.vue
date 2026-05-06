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
// 特殊返回 'shop-share' 表示「顶层带 tenantId 的店铺分享场景」，
// 由 onLaunch 在 hydrate 后按是否登录决定 reLaunch 目标。
function captureRedirect() {
  try {
    if (typeof location === 'undefined') return '';
    // hash 路由：location.hash 形如 "#/pages/shop-home/index?inviter=1"
    const route = location.hash && location.hash.startsWith('#/')
      ? location.hash.slice(1)
      : '';

    // 特殊场景：商户分享 URL 形如 /m/shop-home?tenantId=171&inviter=11#/pages/me/qrcode
    //   - 顶层 ?tenantId= 表示「这是分享落地」，访问者预期是 C 端用户
    //   - hash 部分（如 /pages/me/qrcode 或 /pages/login/index）一律不能直接落地，
    //     否则用户会被带到商户端 me/qrcode → 跳商户登录 → 跟期望背离
    // 处理：把 redirect:after-login 设到 shop-home，并返回 'shop-share' 标记，
    // 由 onLaunch 按登录态决定跳 shop-home（已登录）或 user-login（未登录）
    if (location.search && /[?&]tenantId=/.test(location.search)) {
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
        return 'shop-share';
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

    // ⭐ 店铺分享场景：顶层带 ?tenantId=...，访问者一定是 C 端用户
    //   - 已登录 → reLaunch shop-home（消费 redirect:after-login）
    //   - 未登录 → reLaunch user-login，把 redirect 当 query 传过去，登录页据 tenantId 拉店铺名
    if (landingRoute === 'shop-share') {
      const target = (typeof localStorage !== 'undefined'
        ? localStorage.getItem('redirect:after-login') : '') || '/pages/user-home/index';
      try {
        if (userStore.token) {
          if (typeof localStorage !== 'undefined') localStorage.removeItem('redirect:after-login');
          uni.reLaunch({ url: target });
        } else {
          uni.reLaunch({
            url: `/pages/login/index?redirect=${encodeURIComponent(target)}`,
          });
        }
      } catch {}
      return;
    }

    // 落地在非默认页（如 shop-home?inviter=1）时绝不强跳，让该页自己加载。
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
