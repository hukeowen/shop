import { get, post, del } from '@/utils/request.js';

// 购物车列表
export const listCart = (tenantId) =>
  get('/app-api/trade/cart/list', { tenantId });

// 加入购物车
export const addCart = (skuId, count, tenantId) =>
  post('/app-api/trade/cart/add', { skuId, count }, { tenantId });

// 修改数量
export const updateCartCount = (id, count, tenantId) =>
  post('/app-api/trade/cart/update-count', { id, count }, { tenantId });

// 删除
export const removeCart = (ids, tenantId) =>
  post('/app-api/trade/cart/delete', { ids }, { tenantId });
