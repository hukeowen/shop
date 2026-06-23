import { post, get, put } from '@/utils/request.js';

// 发短信验证码（scene=21 = 会员登录）
export const sendSmsCode = (mobile, scene = 21) =>
  post('/app-api/member/auth/send-sms-code', { mobile, scene });

// 短信登录（成功 → token / userId / nickname / ...）
export const smsLogin = (mobile, code) =>
  post('/app-api/member/auth/sms-login', { mobile, code });

// 密码登录
export const passwordLogin = (mobile, password) =>
  post('/app-api/member/auth/login', { mobile, password });

// 微信小程序一键登录：loginCode=wx.login 拿到的 code，phoneCode=getPhoneNumber 拿到的 code
// 后端 /weixin-mini-app-login 一次完成「登录 + 绑定微信手机号」，返回与短信登录同结构的 token
export const weixinMiniAppLogin = (loginCode, phoneCode, state) =>
  post('/app-api/member/auth/weixin-mini-app-login', { loginCode, phoneCode, state });

// 当前用户信息
export const getProfile = () => get('/app-api/member/user/get');

// 修改基本资料（昵称 / 头像）
export const updateProfile = (body) => put('/app-api/member/user/update', body);

// 退出登录
export const logout = () => post('/app-api/member/auth/logout');
