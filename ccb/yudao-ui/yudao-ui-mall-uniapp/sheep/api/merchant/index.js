import request from '@/sheep/request';

export default {
  // 提交商户入驻申请
  apply: (data) => request({ url: '/merchant/app/apply', method: 'POST', data }),
  // 获取我的商户信息
  getMy: () => request({ url: '/merchant/app/my', method: 'GET' }),
  // 根据ID获取商户信息（公开，不需要登录为该商户）
  getById: (id) => request({ url: '/merchant/app/get', method: 'GET', params: { id } }),
};
