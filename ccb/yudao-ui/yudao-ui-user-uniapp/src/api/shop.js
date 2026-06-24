import { get, post, toQuery } from '@/utils/request.js';

// 店铺列表（公开，可按位置距离 / 关键词过滤）
//   params: { pageNo, pageSize, kw, businessType, userLng, userLat }
export const listShops = (params = {}) =>
  get(`/app-api/merchant/shop/public/list?${toQuery(params)}`);

// 店铺详情（含距离 / 营业状态）
//   tenantId 或 shopId 必传其一，userLng/userLat 可选
export const getShopInfo = (params = {}) =>
  get(`/app-api/merchant/shop/public/info?${toQuery(params)}`);

// 逆地理解析：坐标 → 具体地址（街道/门牌）。地图 key 在后端，前端只传坐标
//   后端返回 { address, recommend, city, district, province }
export const reverseGeo = (lng, lat) =>
  get(`/app-api/merchant/shop/public/geo-reverse?${toQuery({ lng, lat })}`);

// 店内上架商品分页
export const listShopProducts = (tenantId, pageNo = 1, pageSize = 20) =>
  get(`/app-api/merchant/shop/public/products?tenantId=${tenantId}&pageNo=${pageNo}&pageSize=${pageSize}`);

// 商家资质公示：营业执照图片（仅营业执照，后端不下发身份证）
//   返回 { licenseUrl, shopName }
export const getShopLicense = (tenantId) =>
  get(`/app-api/merchant/shop/public/license?tenantId=${tenantId}`);

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

// 切换店铺收藏（true=收藏 / false=取消）；不存在 rel 时后端自动建
export const toggleShopFavorite = (tenantId, favorite) =>
  post(`/app-api/merchant/mini/member-rel/favorite/toggle?${toQuery({ tenantId, favorite })}`);

// 我收藏的店铺（onlyFavorite=true）
export const listFavoriteShops = () =>
  get('/app-api/merchant/mini/member-rel/my-shops-enriched?onlyFavorite=true');

// 我加入的店铺（含余额 / 推广积分 / 消费积分）
//   onlyTuijianPurchased=true：仅返回「已付款购买过推N反1」的店铺（判定 shop_queue_position 存在）
export const listMyShopsEnriched = (onlyTuijianPurchased = false) =>
  get(`/app-api/merchant/mini/member-rel/my-shops-enriched${onlyTuijianPurchased ? '?onlyTuijianPurchased=true' : ''}`);

// 当前 tenant 下我的余额 / 积分
export const getMyRel = (tenantId) =>
  get('/app-api/merchant/mini/member-rel/my', { tenantId });

// 当前用户在某店"累计已赚"的推广积分（lifetime，单位分）
export const getMyPromoEarned = (tenantId) =>
  get(`/app-api/merchant/mini/member-rel/my-promo-earned?tenantId=${tenantId}`, { tenantId });
