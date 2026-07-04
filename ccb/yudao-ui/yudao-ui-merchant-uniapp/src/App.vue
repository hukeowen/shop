<script>
import { useUserStore } from './store/user.js';
import { savePendingReferrer } from './utils/referral.js';

// H5 落地：把 ?inviter= + ?tenantId= 暂存到 localStorage（per-tenant 绑定）。
// 即使后续 reLaunch 到 login 也不会丢 — 登录后由 referral.js flush 在该 tenant 内自动绑定。
function captureLandingInviter() {
  try {
    if (typeof location === 'undefined' || !location.search) return null;
    const sp = new URLSearchParams(location.search);
    const inviter = sp.get('inviter') || sp.get('referrerUserId');
    const tenantId = sp.get('tenantId');
    if (inviter) savePendingReferrer(inviter, tenantId);
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

    if (!route || route === '/') return '';
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

// 子域名分流：tuo.* → 商户端；ke.* → 用户端；其它 → 不抢，沿用默认逻辑
// 注：deep-link（hash 指向具体页）的兼容由 captureRedirect 提前 return 保证，
// 这里只看 hostname。uniapp 启动时会自动把 hash 补成默认 hash，不能依赖 hash 长度判断。
function detectBrandedHost() {
  // #ifdef APP-PLUS
  // 原生 App 没有 hostname；商户端 APK 固定商户角色
  return 'merchant';
  // #endif
  try {
    if (typeof location === 'undefined') return null;
    const host = (location.hostname || '').toLowerCase();
    if (host.startsWith('tuo.')) return 'merchant';
    if (host.startsWith('ke.')) return 'member';
  } catch {}
  return null;
}

export default {
  onLaunch() {
    // 1) 落地先抓 inviter（不管有没 token，都尽早暂存）
    captureLandingInviter();
    const landingRoute = captureRedirect();

    // 2) 从 localStorage 恢复登录态
    const userStore = useUserStore();
    userStore.hydrate();
    const brandedHost = detectBrandedHost();
    // eslint-disable-next-line no-console
    console.log(
      '[小二] App Launched, role=', userStore.activeRole,
      'hasToken=', !!userStore.token,
      'landing=', landingRoute || '(default)',
      'brand=', brandedHost || '(none)'
    );

    // ⭐ 店铺分享场景：顶层带 ?tenantId=...，访问者一定是 C 端用户。
    //   不再区分是否登录：未登录也直接进 shop-home，下单时 checkout 自然跳登录。
    //   inviter 已 captureLandingInviter 存 localStorage，登录后 flushPendingReferrer 绑定。
    //
    //   坑：onLaunch 里的 uni.reLaunch 是异步排队的 → 会被默认 entry page
    //   （pages.json[0] = /pages/index/index 商户工作台）的同步 mount 抢先：
    //   它一 mount 就拉 API → 401 → request.js 跳 user-home/login → 覆盖我们的目标。
    //   解法：先 location.hash = '#' + target 同步抢路由，再让 uniapp router 接管。
    if (landingRoute === 'shop-share') {
      const target = (typeof localStorage !== 'undefined'
        ? localStorage.getItem('redirect:after-login') : '') || '/pages/user-home/index';
      try {
        // 不 remove redirect:after-login —— shop-home 的 onLoad 不消费它，
        // 留着供 401 重定向回来用（被点"去登录"或 checkout 跳 login 时使用）
        if (typeof location !== 'undefined') {
          location.hash = '#' + target;
        }
      } catch {}
      try { uni.reLaunch({ url: target }); } catch {}
      return;
    }

    // 落地在非默认页（如 shop-home?inviter=1）时绝不强跳，让该页自己加载。
    // 未登录时由该页的拦截 / 首个需登录请求触发跳转 login（带 redirect）。
    if (landingRoute) {
      return;
    }

    // ⭐ 子域名分流（仅根路径落地时生效）
    //   tuo.doupaidoudian.com → 商户端：已登录跳工作台，未登录跳商户登录
    //   ke.doupaidoudian.com  → 用户端：已登录跳 user-home，未登录跳通用登录
    //
    // 注：uniapp H5 在 onLaunch 异步排队的 reLaunch 会被默认 entry page（pages.json
    // 第一个 page = /pages/index/index 商户工作台）的同步 mount 抢先 — 后者会触发
    // 401 → request.js 跳 /pages/login/index，覆盖我们的目标。
    // 解法：直接改 location.hash 同步抢路由，再让 uniapp router 接管。
    if (brandedHost === 'merchant' || brandedHost === 'member') {
      let target;
      if (brandedHost === 'merchant') {
        target = (userStore.token && userStore.activeRole === 'merchant')
          ? '/pages/index/index'
          : '/pages/merchant-login/index';
      } else {
        target = userStore.token ? '/pages/user-home/index' : '/pages/login/index';
      }
      try {
        if (typeof location !== 'undefined') {
          location.hash = '#' + target;
        }
      } catch {}
      try { uni.reLaunch({ url: target }); } catch {}
      return;
    }

    // 默认行为（www.* 或 IP 直接访问，落地是 / 或 #/pages/login 或 #/pages/index 或 #/pages/user-home 时）：
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
