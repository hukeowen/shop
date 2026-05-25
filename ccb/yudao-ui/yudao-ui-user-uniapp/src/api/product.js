import { get, post, del } from '@/utils/request.js';

// SPU 详情（含 SKU + 规格）
export const getSpuDetail = (id, tenantId) =>
  get(`/app-api/product/spu/get-detail?id=${id}`, tenantId ? { tenantId } : {});

// SPU 分页（支持 keyword / categoryId 过滤）
export const pageSpu = (params = {}, tenantId) =>
  get(`/app-api/product/spu/page?${new URLSearchParams(params).toString()}`, tenantId ? { tenantId } : {});

// 按 ids 拉 SPU 列表
export const listSpuByIds = (ids, tenantId) =>
  get(`/app-api/product/spu/list-by-ids?ids=${ids.join(',')}`, tenantId ? { tenantId } : {});

// 分类列表（树）
export const listCategories = (tenantId) =>
  get('/app-api/product/category/list', tenantId ? { tenantId } : {});

// 商品收藏
export const favoriteCreate = (spuId, tenantId) =>
  post(`/app-api/product/favorite/create?spuId=${spuId}`, null, tenantId ? { tenantId } : {});
export const favoriteDelete = (spuId, tenantId) =>
  del(`/app-api/product/favorite/delete?spuId=${spuId}`, tenantId ? { tenantId } : {});
export const favoriteExists = (spuId, tenantId) =>
  get(`/app-api/product/favorite/exits?spuId=${spuId}`, tenantId ? { tenantId } : {});
export const favoritePage = (pageNo = 1, pageSize = 20) =>
  get(`/app-api/product/favorite/page?pageNo=${pageNo}&pageSize=${pageSize}`);
export const favoriteCount = () => get('/app-api/product/favorite/get-count');

// 商品评价
export const pageComment = (spuId, pageNo = 1, pageSize = 20) =>
  get(`/app-api/product/comment/page?spuId=${spuId}&pageNo=${pageNo}&pageSize=${pageSize}`);
