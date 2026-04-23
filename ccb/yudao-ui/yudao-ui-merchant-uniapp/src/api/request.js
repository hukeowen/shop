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

function clearTokenAndRedirectToLogin() {
  try {
    if (typeof localStorage !== 'undefined') {
      localStorage.removeItem(USER_STORE_STORAGE_KEY);
    }
  } catch {}
  try {
    uni.removeStorageSync('token');
  } catch {}
  // 触发全局登录流程：优先跳登录页（小程序/h5 通用）
  try {
    const pages = getCurrentPages ? getCurrentPages() : [];
    const cur = pages && pages.length ? pages[pages.length - 1] : null;
    const curRoute = cur?.route || cur?.$page?.fullPath || '';
    if (!/pages\/login\/index/.test(curRoute)) {
      uni.reLaunch({ url: '/pages/login/index' });
    }
  } catch {
    // noop
  }
}

function getHeader(urlPath) {
  const token = readToken();
  const tenantId = uni.getStorageSync('tenantId') || 1;
  const header = {};
  // /admin-api 保留 tenant-id；/app-api 不需要（按 JWT 里的商户维度解析）
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
export function request({ url, method = 'GET', data, header, responseType, raw = false }) {
  return new Promise((resolve, reject) => {
    const isArrayBuffer = responseType === 'arraybuffer';
    uni.request({
      url,
      method,
      data,
      header: { ...getHeader(url), ...header },
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
          uni.showToast({ title: msg, icon: 'none' });
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

/** 模拟异步延迟（原型阶段剩余 mock 代码复用） */
export function mockDelay(data, ms = 400) {
  return new Promise((resolve) => setTimeout(() => resolve(data), ms));
}
