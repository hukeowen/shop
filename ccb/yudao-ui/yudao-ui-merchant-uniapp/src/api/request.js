/**
 * 请求封装 — 原型阶段所有 api/*.js 里的接口默认返回 mock。
 * 真实接入时把 mockXxx 改成 request({...}) 即可。
 */

const BASE_URL = '/admin-api';

function getHeader() {
  const token = uni.getStorageSync('token');
  const tenantId = uni.getStorageSync('tenantId') || 1;
  return {
    'tenant-id': tenantId,
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

export function request({ url, method = 'GET', data, header }) {
  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE_URL + url,
      method,
      data,
      header: { ...getHeader(), ...header },
      success: (res) => {
        const body = res.data || {};
        if (res.statusCode === 200 && body.code === 0) {
          resolve(body.data);
        } else {
          uni.showToast({ title: body.msg || '请求失败', icon: 'none' });
          reject(body);
        }
      },
      fail: (err) => {
        uni.showToast({ title: '网络异常', icon: 'none' });
        reject(err);
      },
    });
  });
}

/** 模拟异步延迟 */
export function mockDelay(data, ms = 400) {
  return new Promise((resolve) => setTimeout(() => resolve(data), ms));
}
