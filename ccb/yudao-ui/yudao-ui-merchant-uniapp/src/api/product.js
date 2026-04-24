import { request } from './request.js';

const BASE = '/app-api/merchant/mini/product';

/**
 * 分类列表（本地常量 — 后续接入 product_category 接口时替换）
 */
export const CATEGORIES = [
  { id: 1, name: '小吃' },
  { id: 2, name: '饮品' },
  { id: 3, name: '水果' },
  { id: 4, name: '零食' },
  { id: 5, name: '服装' },
  { id: 6, name: '茶叶' },
  { id: 7, name: '日用百货' },
  { id: 99, name: '其他' },
];

export const DEFAULT_CATEGORY_ID = 1;

function catName(id) {
  return (CATEGORIES.find((c) => c.id === id) || { name: '其他' }).name;
}

/** ProductSpuDO → UI 所需字段 */
function normalizeSpu(s) {
  if (!s) return null;
  return {
    id: s.id,
    name: s.name,
    picUrl: s.picUrl,
    price: s.price,
    stock: s.stock ?? 9999,
    introduction: s.introduction || '',
    categoryId: s.categoryId ?? DEFAULT_CATEGORY_ID,
    categoryName: catName(s.categoryId),
    status: s.status ?? 0,
    brokerageEnabled: !!s.subCommissionType,
    pushBackEnabled: false,
    salesCount: s.salesCount ?? 0,
    giveIntegral: s.giveIntegral ?? 0,
    deliveryTypes: s.deliveryTypes ?? [2],
  };
}

/** 分页查询商品列表，status: 0=上架 1=下架，不传=全部 */
export async function getSpuPage({ status, pageNo = 1, pageSize = 20 } = {}) {
  const params = { pageNo, pageSize };
  if (status === 0 || status === 1) params.status = status;
  const data = await request({ url: `${BASE}/page`, data: params });
  return {
    total: data.total,
    list: (data.list || []).map(normalizeSpu),
  };
}

/** 查询单个商品详情（用于编辑页回填） */
export async function getSpu(id) {
  const data = await request({ url: `${BASE}/get?id=${id}` });
  return normalizeSpu(data);
}

/** 极简创建 */
export function createSpu(form) {
  return request({
    url: `${BASE}/simple-create`,
    method: 'POST',
    data: {
      name: form.name,
      price: form.price,
      picUrl: form.picUrl,
      stock: form.stock || 9999,
      categoryId: form.categoryId || DEFAULT_CATEGORY_ID,
      introduction: form.introduction || '',
      giveIntegral: form.giveIntegral ?? 0,
      deliveryTypes: form.deliveryTypes ?? [2],
    },
  });
}

/** 更新商品（传完整 ProductSpuSaveReqVO 结构） */
export async function updateSpu(form) {
  // 先拿含 skus 的完整 RespVO，保留服务端 SKU ID / costPrice 等字段
  const raw = await request({ url: `${BASE}/get?id=${form.id}` });
  return request({
    url: `${BASE}/update`,
    method: 'PUT',
    data: {
      id: form.id,
      name: form.name,
      keyword: form.name,
      introduction: form.introduction || form.name,
      description: raw?.description || form.name,
      categoryId: form.categoryId || DEFAULT_CATEGORY_ID,
      brandId: raw?.brandId ?? null,
      picUrl: form.picUrl,
      sliderPicUrls: [form.picUrl],
      sort: raw?.sort ?? 0,
      specType: false,
      deliveryTypes: form.deliveryTypes ?? raw?.deliveryTypes ?? [2],
      deliveryTemplateId: raw?.deliveryTemplateId ?? null,
      giveIntegral: form.giveIntegral ?? 0,
      subCommissionType: !!form.brokerageEnabled,
      virtualSalesCount: raw?.virtualSalesCount ?? 0,
      salesCount: raw?.salesCount ?? 0,
      browseCount: raw?.browseCount ?? 0,
      skus: [
        {
          id: raw?.skus?.[0]?.id,           // 保留 SKU ID，避免服务端创建重复 SKU
          name: form.name,
          price: form.price,
          marketPrice: form.price,
          costPrice: raw?.skus?.[0]?.costPrice ?? 0,
          picUrl: form.picUrl,
          stock: form.stock != null ? form.stock : 9999,
        },
      ],
    },
  });
}

/** 上下架 */
export function updateStatus({ id, status }) {
  return request({
    url: `${BASE}/update-status`,
    method: 'PUT',
    data: { id, status },
  });
}

/** 删除商品 */
export function deleteSpu(id) {
  return request({ url: `${BASE}/delete?id=${id}`, method: 'DELETE' });
}
