import { get, post } from '@/utils/request.js';

// 店铺信息（按 tenant-id header 命中）
export const getShopInfo = (tenantId) =>
  get('/app-api/merchant/mini/shop/info', { tenantId });

// 附近店铺
export const listNearbyShops = (params = {}) =>
  get(`/app-api/merchant/mini/shop/nearby?${new URLSearchParams(params).toString()}`);

// 店铺商品列表
export const listShopSpus = (tenantId, params = {}) =>
  get(`/app-api/product/spu/list?${new URLSearchParams(params).toString()}`, { tenantId });
