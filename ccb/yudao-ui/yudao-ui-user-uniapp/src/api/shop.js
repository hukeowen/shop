import { get } from '@/utils/request.js';

// 店铺列表（公开，可按位置距离 / 关键词过滤）
//   params: { pageNo, pageSize, kw, businessType, userLng, userLat }
export const listShops = (params = {}) =>
  get(`/app-api/merchant/shop/public/list?${new URLSearchParams(params).toString()}`);

// 店铺详情（含距离 / 营业状态）
//   tenantId 或 shopId 必传其一，userLng/userLat 可选
export const getShopInfo = (params = {}) =>
  get(`/app-api/merchant/shop/public/info?${new URLSearchParams(params).toString()}`);

// 店内上架商品分页
export const listShopProducts = (tenantId, pageNo = 1, pageSize = 20) =>
  get(`/app-api/merchant/shop/public/products?tenantId=${tenantId}&pageNo=${pageNo}&pageSize=${pageSize}`);

// 店铺访客数（近 30 天）
export const getShopVisitorCount = (tenantId) =>
  get(`/app-api/merchant/shop/public/info/visitor?tenantId=${tenantId}`);

// 店铺积分配置（pointPerYuan 等）
export const getShopPointConfig = (tenantId) =>
  get(`/app-api/merchant/shop/public/config?tenantId=${tenantId}`);

// 我访问过的店铺列表（按访问时间倒序）
//   实际控制器 AppMemberShopRelController @RequestMapping("/merchant/mini/member-rel")
export const listMyShops = () =>
  get('/app-api/merchant/mini/member-rel/my-shops');

// 我加入的店铺（含余额 / 推广积分 / 消费积分）
export const listMyShopsEnriched = () =>
  get('/app-api/merchant/mini/member-rel/my-shops-enriched');

// 当前 tenant 下我的余额 / 积分
export const getMyRel = (tenantId) =>
  get('/app-api/merchant/mini/member-rel/my', { tenantId });
