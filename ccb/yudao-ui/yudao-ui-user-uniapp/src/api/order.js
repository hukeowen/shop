import { get, post } from '@/utils/request.js';

// 结算预览
export const checkoutPreview = (body, tenantId) =>
  post('/app-api/merchant/mini/checkout/preview', body, { tenantId });

// 提交订单
export const submitOrder = (body, tenantId) =>
  post('/app-api/merchant/mini/checkout/submit', body, { tenantId });

// 订单列表
export const listOrders = (status = '', page = 1, size = 20) =>
  get(`/app-api/merchant/mini/order/page?status=${status}&pageNo=${page}&pageSize=${size}`);

// 订单详情
export const getOrderDetail = (id) =>
  get(`/app-api/merchant/mini/order/get?id=${id}`);

// 取消订单
export const cancelOrder = (id) =>
  post('/app-api/merchant/mini/order/cancel', { id });

// 收藏（C 端收藏）
export const listFavoriteSpus = (page = 1, size = 20) =>
  get(`/app-api/product/favorite/page?pageNo=${page}&pageSize=${size}`);

export const listFavoriteShops = (page = 1, size = 20) =>
  get(`/app-api/merchant/mini/shop/favorite/page?pageNo=${page}&pageSize=${size}`);
