import { get } from '@/utils/request.js';

// SPU 详情
export const getSpuDetail = (id, tenantId) =>
  get(`/app-api/product/spu/get-detail?id=${id}`, { tenantId });

// 关键字搜索
export const searchSpu = (keyword, tenantId, page = 1, size = 20) =>
  get(`/app-api/product/spu/page?keyword=${encodeURIComponent(keyword)}&pageNo=${page}&pageSize=${size}`, { tenantId });

// 分类列表
export const listCategories = (tenantId) =>
  get('/app-api/product/category/list', { tenantId });

// 按分类查商品
export const listByCategory = (categoryId, tenantId, page = 1, size = 20) =>
  get(`/app-api/product/spu/page?categoryId=${categoryId}&pageNo=${page}&pageSize=${size}`, { tenantId });
