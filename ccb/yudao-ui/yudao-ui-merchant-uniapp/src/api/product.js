import { mockDelay } from './request.js';

/**
 * 商户商品（极简版）
 * 主表单只 4 项：图、名、价、分类（默认小吃）
 * 高级设置：返利、推N返一、库存、简介（折叠默认收起）
 *
 * 后端对应 /admin-api/merchant/mini/product/simple-create | page | update | update-status | delete
 * 后端默认 categoryId=1（小吃）、单规格、自提、库存 9999
 */

// 分类列表（mock — 真实接入查 product_category 表）
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

const store = {
  nextId: 5005,
  list: [
    {
      id: 5001,
      name: '蜜薯（大）',
      picUrl: 'https://images.unsplash.com/photo-1518977956812-cd3dbadaaf31?w=300',
      price: 500, // 分
      stock: 9999,
      introduction: '现烤现卖，糖心流油',
      categoryName: '小吃',
      status: 0, // 0 上架 / 1 下架
      brokerageEnabled: true,
      pushBackEnabled: true,
      salesCount: 42,
      createdAt: '2026-04-15 10:20',
    },
    {
      id: 5002,
      name: '烤地瓜（小）',
      picUrl: 'https://images.unsplash.com/photo-1623059508779-2542c6e83753?w=300',
      price: 600,
      stock: 9999,
      introduction: '',
      categoryName: '小吃',
      status: 0,
      brokerageEnabled: true,
      pushBackEnabled: false,
      salesCount: 35,
      createdAt: '2026-04-12 09:00',
    },
    {
      id: 5003,
      name: '甜玉米',
      picUrl: 'https://images.unsplash.com/photo-1601593768799-76d3a7d3d6e7?w=300',
      price: 300,
      stock: 9999,
      introduction: '春季限定',
      categoryName: '小吃',
      status: 0,
      brokerageEnabled: false,
      pushBackEnabled: false,
      salesCount: 28,
      createdAt: '2026-04-08 14:30',
    },
    {
      id: 5004,
      name: '烤红薯（试吃装）',
      picUrl: 'https://images.unsplash.com/photo-1518977956812-cd3dbadaaf31?w=300',
      price: 100,
      stock: 0,
      introduction: '只剩 0 件，已售罄',
      categoryName: '小吃',
      status: 1,
      brokerageEnabled: false,
      pushBackEnabled: false,
      salesCount: 120,
      createdAt: '2026-03-28 18:00',
    },
  ],
};

export function getSpuPage({ status } = {}) {
  let list = store.list;
  if (status === 0 || status === 1) list = list.filter((s) => s.status === status);
  return mockDelay({ total: list.length, list: [...list] });
}

export function getSpu(id) {
  return mockDelay(store.list.find((s) => s.id === id) || null);
}

export function createSpu(form) {
  const id = store.nextId++;
  const cat = CATEGORIES.find((c) => c.id === (form.categoryId || DEFAULT_CATEGORY_ID));
  store.list.unshift({
    id,
    name: form.name,
    picUrl: form.picUrl,
    price: form.price,
    stock: form.stock || 9999,
    introduction: form.introduction || '',
    categoryId: cat.id,
    categoryName: cat.name,
    status: 0,
    brokerageEnabled: !!form.brokerageEnabled,
    pushBackEnabled: !!form.pushBackEnabled,
    salesCount: 0,
    createdAt: new Date().toLocaleString('zh-CN'),
  });
  return mockDelay(id);
}

export function updateSpu(form) {
  const s = store.list.find((x) => x.id === form.id);
  if (s) {
    Object.assign(s, form);
    if (form.categoryId) {
      const cat = CATEGORIES.find((c) => c.id === form.categoryId);
      if (cat) s.categoryName = cat.name;
    }
  }
  return mockDelay(true);
}

export function updateStatus({ id, status }) {
  const s = store.list.find((x) => x.id === id);
  if (s) s.status = status;
  return mockDelay(true);
}

export function deleteSpu(id) {
  store.list = store.list.filter((x) => x.id !== id);
  return mockDelay(true);
}
