/**
 * 客小二 C 端统一请求封装。
 *
 * - /app-api 走业务后端（CommonResult<T>{code,data,msg}，code=0 视为成功）
 * - /admin-api 老接口保留（带 tenant-id header）
 * - 401 → 清 token + 跳 /pages/login/index（允许匿名浏览的页面不跳）
 */

const USER_STORE_STORAGE_KEY = 'kexiaoer-user-store-v1';

function readToken() {
  try {
    if (typeof localStorage !== 'undefined') {
      const raw = localStorage.getItem(USER_STORE_STORAGE_KEY);
      if (raw) {
        const obj = JSON.parse(raw);
        if (obj && typeof obj.token === 'string' && obj.token) return obj.token;
      }
    }
  } catch {}
  try { return uni.getStorageSync('token') || ''; } catch { return ''; }
}

function readTenant() {
  try {
    if (typeof localStorage !== 'undefined') {
      const raw = localStorage.getItem(USER_STORE_STORAGE_KEY);
      if (raw) {
        const obj = JSON.parse(raw);
        if (obj && obj.tenantId) return obj.tenantId;
      }
    }
  } catch {}
  try { return uni.getStorageSync('tenantId') || ''; } catch { return ''; }
}

// 这些页面 401 不跳 login（避免和 login 重定向器互踢死循环；UI 自带空数据兜底）
const ANON_BROWSE_PREFIXES = [
  'pages/index/',
  'pages/nearby/',
  'pages/winners/',
  'pages/category/',
  'pages/search/',
  'pages/product/',
  'pages/shop/',
  'pages/login/',
];

function isAnonBrowsePage(route) {
  if (!route) return true;
  const r = String(route).replace(/^\/+/, '');
  return ANON_BROWSE_PREFIXES.some((p) => r.startsWith(p));
}

function clearTokenAndRedirectToLogin() {
  try {
    if (typeof localStorage !== 'undefined') {
      localStorage.removeItem(USER_STORE_STORAGE_KEY);
    }
  } catch {}
  try { uni.removeStorageSync('token'); } catch {}
  try {
    const pages = getCurrentPages ? getCurrentPages() : [];
    const cur = pages && pages.length ? pages[pages.length - 1] : null;
    const curRoute = cur?.route || '';
    if (/pages\/login\/index/.test(curRoute)) return;
    if (isAnonBrowsePage(curRoute)) return;
    try {
      const optionsStr = cur?.options
        ? Object.entries(cur.options).map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join('&')
        : '';
      const fullRoute = '/' + curRoute.replace(/^\/+/, '') + (optionsStr ? '?' + optionsStr : '');
      if (typeof localStorage !== 'undefined' && curRoute) {
        localStorage.setItem('redirect:after-login', fullRoute);
      }
    } catch {}
    uni.reLaunch({ url: '/pages/login/index' });
  } catch {}
}

function getHeader(urlPath, extraTenantId) {
  const token = readToken();
  const tenantId = extraTenantId || readTenant();
  const header = {};
  // /admin-api 必带 tenant-id；/app-api 后端从 JWT 解析，但 C 端跨店浏览要带（shop tenant）
  if (!urlPath.startsWith('/app-api')) {
    header['tenant-id'] = tenantId || 1;
  } else if (tenantId) {
    header['tenant-id'] = tenantId;
  }
  if (token) header.Authorization = `Bearer ${token}`;
  return header;
}

export function request({ url, method = 'GET', data, header, responseType, raw = false, tenantId }) {
  return new Promise((resolve, reject) => {
    const isArrayBuffer = responseType === 'arraybuffer';
    uni.request({
      url,
      method,
      data,
      header: { ...getHeader(url, tenantId), ...header },
      ...(isArrayBuffer ? { responseType: 'arraybuffer' } : {}),
      success: (res) => {
        if (res.statusCode === 401) {
          uni.showToast({ title: '登录已失效', icon: 'none' });
          clearTokenAndRedirectToLogin();
          reject(new Error('unauthorized'));
          return;
        }
        if (isArrayBuffer) {
          if (res.statusCode >= 200 && res.statusCode < 300) resolve(res.data);
          else reject(new Error(`http ${res.statusCode}`));
          return;
        }
        if (res.statusCode < 200 || res.statusCode >= 300) {
          uni.showToast({ title: `请求失败 ${res.statusCode}`, icon: 'none' });
          reject(new Error(`http ${res.statusCode}`));
          return;
        }
        if (raw) { resolve(res.data); return; }
        const body = res.data || {};
        if (typeof body !== 'object' || body === null || !('code' in body)) {
          resolve(body);
          return;
        }
        if (body.code === 0) {
          resolve(body.data);
        } else {
          const msg = body.msg || body.message || '请求失败';
          if (body.code === 401 || /未登录/.test(msg) || /token.*失效/i.test(msg)) {
            uni.showToast({ title: '登录已失效', icon: 'none' });
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

export const get  = (url, opt = {}) => request({ ...opt, url, method: 'GET' });
export const post = (url, data, opt = {}) => request({ ...opt, url, method: 'POST', data });
export const put  = (url, data, opt = {}) => request({ ...opt, url, method: 'PUT', data });
export const del  = (url, opt = {}) => request({ ...opt, url, method: 'DELETE' });
