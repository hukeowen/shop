import { get, post, put, del } from '@/utils/request.js';

// 购物车列表（按店铺分组）
export const listCart = () =>
  get('/app-api/trade/cart/list');

// 加入购物车
export const addCart = (skuId, count) =>
  post('/app-api/trade/cart/add', { skuId, count });

// 修改数量
export const updateCartCount = (id, count) =>
  put('/app-api/trade/cart/update-count', { id, count });

// 修改选中
export const updateCartSelected = (ids, selected) =>
  put('/app-api/trade/cart/update-selected', { ids, selected });

// 重置（清空）
export const resetCart = () =>
  put('/app-api/trade/cart/reset', null);

// 删除
export const deleteCart = (ids) =>
  del(`/app-api/trade/cart/delete?ids=${ids.join(',')}`);

// 总数
export const getCartCount = () => get('/app-api/trade/cart/get-count');
