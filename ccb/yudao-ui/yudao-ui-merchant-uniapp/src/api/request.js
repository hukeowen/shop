/**
 * 统一请求封装
 *
 * 前端所有网络请求走两条线：
 *   · /admin-api   → yudao 管理域（老接口保留；tenant-id header 留给管理端）
 *   · /app-api     → 商户/用户端 BFF（登录、AI 转发等，带 JWT）
 *
 * 业务约定：后端统一返 CommonResult<T>{ code, data, msg }，code === 0 视为成功。
 * 对于二进制响应（responseType='arraybuffer' / 'blob'）不做 JSON 解包，直接返原始 data。
 */

// 不在模块加载期直接依赖 userStore，避免循环引用（userStore 内部也会 import request）
// 读 token 走 localStorage，userStore 在启动时会把 token 同步写进来
const USER_STORE_STORAGE_KEY = 'user-store-v1';

function readToken() {
  try {
    if (typeof localStorage !== 'undefined') {
      const raw = localStorage.getItem(USER_STORE_STORAGE_KEY);
      if (raw) {
        const obj = JSON.parse(raw);
        if (obj && typeof obj.token === 'string' && obj.token) return obj.token;
      }
    }
  } catch {
    // ignore
  }
  // 兼容老代码：曾经直接写在 storage 的 token 也接一下
  try {
    return uni.getStorageSync('token') || '';
  } catch {
    return '';
  }
}

/**
 * 商户端页前缀（识别后未登录跳 /pages/merchant-login/index）。
 * 不在此列表的页面（user-home / user-me / user-order / shop-home / cart /
 * checkout / login / index 等 C 端）默认跳 /pages/login/index。
 */
const MERCHANT_PAGE_PREFIXES = [
  'pages/index/',          // 商户工作台 dashboard
  'pages/me/',             // 商户个人页 / 钱包 / 设置
  'pages/order/',          // 商户订单管理
  'pages/ai-video/',       // AI 视频生成
  'pages/product/',        // 商品管理
  'pages/member/',         // 会员管理
  'pages/withdraw/',       // 商户提现
  'pages/merchant-apply/', // 商户入驻
  'pages/merchant-login/', // 商户登录（防回跳自身）
];

// 允许匿名浏览（401 不跳 login，避免和 login 重定向器互踢死循环）
const ANON_BROWSE_PREFIXES = [
  'pages/user-home/',      // 用户首页：附近 / 搜索 / 推荐都可未登录浏览
  'pages/categories/',     // 分类页：未登录可浏览
  'pages/nearby/',         // 附近店铺：未登录可浏览
  'pages/shop-home/',      // 店铺主页：未登录可浏览，下单时由 checkout 处理
  'pages/product/detail',  // 商品详情：未登录可浏览
  'pages/login/',          // 已是登录重定向器，再跳会死循环
  'pages/merchant-login/', // 同上
  'pages/merchant-apply/', // 商户入驻 H5，未登录可浏览（注册场景）
];

function isMerchantPage(route) {
  if (!route) return false;
  const r = String(route).replace(/^\/+/, '');
  return MERCHANT_PAGE_PREFIXES.some((p) => r.startsWith(p));
}

function isAnonBrowsePage(route) {
  if (!route) return true; // 不知道当前页时也别跳，避免死循环
  const r = String(route).replace(/^\/+/, '');
  return ANON_BROWSE_PREFIXES.some((p) => r.startsWith(p));
}

function clearTokenAndRedirectToLogin() {
  // 先取 role 再清 storage，否则 isMerchantContext 永远 false
  let hasMerchantRole = false;
  try {
    if (typeof localStorage !== 'undefined') {
      const raw = localStorage.getItem(USER_STORE_STORAGE_KEY);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (Array.isArray(parsed.roles) && parsed.roles.includes('merchant')) hasMerchantRole = true;
      }
    }
  } catch {}
  try {
    if (typeof localStorage !== 'undefined') {
      localStorage.removeItem(USER_STORE_STORAGE_KEY);
    }
  } catch {}
  try {
    uni.removeStorageSync('token');
  } catch {}
  // 触发全局登录流程：按当前页判定跳商户登录还是用户登录
  try {
    const pages = getCurrentPages ? getCurrentPages() : [];
    const cur = pages && pages.length ? pages[pages.length - 1] : null;
    const curRoute = cur?.route || cur?.$page?.fullPath || '';

    // 已在登录页就不重复跳，避免死循环
    if (/pages\/(login|merchant-login)\/index/.test(curRoute)) return;

    // 允许匿名浏览的页面：401 只清 token，不跳 login。
    // 避免 user-home 调需登录接口 → 401 → login → login 重定向回 user-home → 死循环。
    // 这类页面的 UI 已对空数据做了兜底（"附近暂无店铺" 等），不会白屏。
    if (isAnonBrowsePage(curRoute)) return;

    // 仅当用户已有商户身份（roles 含 merchant）才跳商户登录页；
    // 否则统一走 /pages/login/index（通用登录有"我是顾客 / 我是商户"两入口）。
    // 防止"普通用户访问商户页（如 /m/ 默认 pages/index/）被误推到商户登录"
    const isMerchantContext = isMerchantPage(curRoute) && hasMerchantRole;
    const loginPath = isMerchantContext ? '/pages/merchant-login/index' : '/pages/login/index';

    // 保存当前路由作为 redirect，登录成功后回跳
    try {
      const optionsStr = cur?.options
        ? Object.entries(cur.options).map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join('&')
        : '';
      const fullRoute = '/' + (curRoute.replace(/^\/+/, '')) + (optionsStr ? '?' + optionsStr : '');
      if (typeof localStorage !== 'undefined' && curRoute) {
        localStorage.setItem('redirect:after-login', fullRoute);
      }
    } catch {}

    uni.reLaunch({ url: loginPath });
  } catch {
    // noop
  }
}

function getHeader(urlPath) {
  const token = readToken();
  const tenantId = uni.getStorageSync('tenantId') || 1;
  const header = {};
  // /admin-api 保留 tenant-id；/app-api 后端从 JWT token 解析商户租户，无需 header
  // （yudao.tenant.ignore-urls 配置 /app-api/** 跳过 TenantInterceptor 强校验）
  if (!urlPath.startsWith('/app-api')) {
    header['tenant-id'] = tenantId;
  }
  if (token) header.Authorization = `Bearer ${token}`;
  return header;
}

/**
 * 通用请求
 * @param {Object} opt
 * @param {string} opt.url           以 /admin-api 或 /app-api 开头的完整路径
 * @param {string} [opt.method='GET']
 * @param {any}    [opt.data]
 * @param {Object} [opt.header]
 * @param {'text'|'json'|'arraybuffer'} [opt.responseType]  arraybuffer 用于 TTS MP3
 * @param {boolean} [opt.raw=false]  为 true 时不解 CommonResult，直接返回 res.data
 */
export function request({ url, method = 'GET', data, header, responseType, raw = false, tenantId }) {
  return new Promise((resolve, reject) => {
    const isArrayBuffer = responseType === 'arraybuffer';
    uni.request({
      url,
      method,
      data,
      header: { ...getHeader(url), ...(tenantId ? { 'tenant-id': tenantId } : {}), ...header },
      ...(isArrayBuffer ? { responseType: 'arraybuffer' } : {}),
      success: (res) => {
        // 401 → 登录失效
        if (res.statusCode === 401) {
          uni.showToast({ title: '登录已失效，请重新登录', icon: 'none' });
          clearTokenAndRedirectToLogin();
          reject(new Error('unauthorized'));
          return;
        }
        // 二进制：不解 CommonResult，只要 2xx 就透传
        if (isArrayBuffer) {
          if (res.statusCode >= 200 && res.statusCode < 300) {
            resolve(res.data);
          } else {
            uni.showToast({ title: `请求失败 ${res.statusCode}`, icon: 'none' });
            reject(new Error(`http ${res.statusCode}`));
          }
          return;
        }
        if (res.statusCode < 200 || res.statusCode >= 300) {
          uni.showToast({ title: `请求失败 ${res.statusCode}`, icon: 'none' });
          reject(new Error(`http ${res.statusCode}`));
          return;
        }
        // raw 模式（极少使用）：直接返 body
        if (raw) {
          resolve(res.data);
          return;
        }
        const body = res.data || {};
        // 非 CommonResult 形态（极少数裸 json）：直接返
        if (typeof body !== 'object' || body === null || !('code' in body)) {
          resolve(body);
          return;
        }
        if (body.code === 0) {
          resolve(body.data);
        } else {
          const msg = body.msg || body.message || '请求失败';
          // yudao 未登录业务码：body.code = 401（GlobalErrorCodeConstants.UNAUTHORIZED）
          // HTTP 状态可能是 200，前端必须按 code 判断，不然只 toast 不跳登录页
          if (body.code === 401 || /未登录/.test(msg) || /token.*失效/i.test(msg)) {
            uni.showToast({ title: '登录已失效，请重新登录', icon: 'none' });
            clearTokenAndRedirectToLogin();
          } else {
            uni.showToast({ title: msg, icon: 'none' });
          }
          reject(new Error(msg));
        }
      },
      fail: (err) => {
        uni.showToast({ title: '网络异常', icon: 'none' });
        reject(err);
      },
    });
  });
}

