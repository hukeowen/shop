import { request } from './request.js';

const BASE = '/app-api/merchant/mini/product';

/**
 * 商品分类（B 改造：去 mock）
 *   - 优先调后端 GET /app-api/product/category/list 拉真分类（admin 后台维护）
 *   - 网络/接口失败时 fallback 到本地常量，保证离线/初次部署能用
 *   - splice 替换数组内容保留引用，让 Vue 模板能响应
 *
 * CATEGORIES 同步访问入口保留以兼容老代码；推荐新代码 await loadCategories()。
 */
const FALLBACK_CATEGORIES = [
  { id: 1, name: '小吃' },
  { id: 2, name: '饮品' },
  { id: 3, name: '水果' },
  { id: 4, name: '零食' },
  { id: 5, name: '服装' },
  { id: 6, name: '茶叶' },
  { id: 7, name: '日用百货' },
  { id: 99, name: '其他' },
];

export const CATEGORIES = [...FALLBACK_CATEGORIES];
export const DEFAULT_CATEGORY_ID = 1;

let _categoriesLoaded = false;
let _loadingPromise = null;

/**
 * 异步拉后端商品分类，缓存到 CATEGORIES。失败用 fallback，不抛错。
 */
export async function loadCategories(force = false) {
  if (!force && _categoriesLoaded) return CATEGORIES;
  if (_loadingPromise) return _loadingPromise;
  _loadingPromise = (async () => {
    try {
      const list = await request({ url: '/app-api/product/category/list' });
      if (Array.isArray(list) && list.length) {
        const normalized = list
            .filter((c) => c && c.id != null && c.name)
            .map((c) => ({ id: Number(c.id), name: String(c.name) }));
        if (normalized.length) {
          CATEGORIES.splice(0, CATEGORIES.length, ...normalized);
          _categoriesLoaded = true;
        }
      }
    } catch (e) {
      console.warn('[product] loadCategories 失败，用本地 fallback:', e?.message);
    } finally {
      _loadingPromise = null;
    }
    return CATEGORIES;
  })();
  return _loadingPromise;
}

// 模块加载时触发一次预热（异步），让 product/edit.vue 打开时大概率已就绪
if (typeof window !== 'undefined') {
  loadCategories().catch(() => {});
}

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
      // brand：AI 识别能给具体品牌就传具体值（可口可乐/旺仔/老干妈），否则
      // 传通用类目（小吃/水果/零食/饮品/烟酒/烘焙），后端 findOrCreate 兜底
      brand: form.brand || '',
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
