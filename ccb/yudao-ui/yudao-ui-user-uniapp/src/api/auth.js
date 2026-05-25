import { post, get } from '@/utils/request.js';

// 发短信验证码（scene=21 = 会员登录）
export const sendSmsCode = (mobile, scene = 21) =>
  post('/app-api/member/auth/send-sms-code', { mobile, scene });

// 短信登录（成功 → token / userId / nickname / ...）
export const smsLogin = (mobile, code) =>
  post('/app-api/member/auth/sms-login', { mobile, code });

// 密码登录
export const passwordLogin = (mobile, password) =>
  post('/app-api/member/auth/login', { mobile, password });

// 当前用户信息
export const getProfile = () => get('/app-api/member/user/get');

// 退出登录
export const logout = () => post('/app-api/member/auth/logout');
