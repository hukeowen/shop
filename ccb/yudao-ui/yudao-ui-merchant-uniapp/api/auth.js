import { mockDelay } from './request.js';

// 发送验证码
export function sendSmsCode(mobile) {
  // return request({ url: '/merchant/mini/auth/sms-send', method: 'POST', data: { mobile } });
  return mockDelay({ sent: true, mobile });
}

// 验证码登录
export function smsLogin({ mobile, code }) {
  // return request({ url: '/merchant/mini/auth/sms-login', method: 'POST', data: { mobile, code } });
  return mockDelay({
    token: 'mock-token-' + Date.now(),
    tenantId: 1,
    user: { id: 1001, nickname: '王师傅', mobile, avatar: '' },
    shop: { id: 1, name: '王师傅烤地瓜', address: '北京市朝阳区三里屯 SOHO 1 号楼' },
  });
}

// 退出
export function logout() {
  uni.removeStorageSync('token');
  uni.removeStorageSync('tenantId');
  return Promise.resolve(true);
}
