import { mockDelay } from './request.js';

const mockOrders = [
  {
    id: 'O20260420001',
    status: 10, // 10 待发货 / 20 待核销 / 30 已完成
    userNickname: '李阿姨',
    userMobile: '138****2356',
    totalPrice: 1800, // 分
    itemCount: 3,
    items: [
      { spuName: '蜜薯（大）', skuName: '1 份', price: 500, count: 3 },
      { spuName: '玉米', skuName: '甜糯', price: 300, count: 1 },
    ],
    remark: '多烤一会儿，要焦的',
    createdAt: '2026-04-20 09:12',
    deliveryType: 'express', // express / pickup
    verifyCode: null,
    address: '朝阳区三里屯 SOHO 1 号楼 502',
  },
  {
    id: 'O20260420002',
    status: 20,
    userNickname: '张先生',
    userMobile: '139****7712',
    totalPrice: 1200,
    itemCount: 2,
    items: [{ spuName: '烤地瓜（小）', skuName: '1 份', price: 600, count: 2 }],
    remark: '',
    createdAt: '2026-04-20 10:35',
    deliveryType: 'pickup',
    verifyCode: '8392',
    address: null,
  },
  {
    id: 'O20260420003',
    status: 10,
    userNickname: '小雨',
    userMobile: '136****9981',
    totalPrice: 900,
    itemCount: 1,
    items: [{ spuName: '蜜薯（中）', skuName: '1 份', price: 900, count: 1 }],
    remark: '',
    createdAt: '2026-04-20 11:02',
    deliveryType: 'express',
    verifyCode: null,
    address: '海淀区中关村大街 28 号',
  },
  {
    id: 'O20260419008',
    status: 30,
    userNickname: '周阿姨',
    userMobile: '137****4412',
    totalPrice: 2400,
    itemCount: 4,
    items: [{ spuName: '烤地瓜（大）', skuName: '1 份', price: 600, count: 4 }],
    remark: '',
    createdAt: '2026-04-19 18:22',
    deliveryType: 'pickup',
    verifyCode: '1257',
    address: null,
  },
];

// 分页查询订单
export function getOrderPage({ status }) {
  const list = status ? mockOrders.filter((o) => o.status === status) : mockOrders;
  return mockDelay({ total: list.length, list });
}

// 订单详情
export function getOrder(id) {
  return mockDelay(mockOrders.find((o) => o.id === id) || null);
}

// 发货
export function deliverOrder({ id, expressCompany, expressNo }) {
  const o = mockOrders.find((x) => x.id === id);
  if (o) {
    o.status = 30;
    o.expressCompany = expressCompany;
    o.expressNo = expressNo;
  }
  return mockDelay(true);
}

// 核销（按核销码）
export function pickUpVerify(code) {
  const o = mockOrders.find((x) => x.verifyCode === code);
  if (!o) return mockDelay({ ok: false, msg: '核销码无效' });
  if (o.status === 30) return mockDelay({ ok: false, msg: '订单已核销' });
  o.status = 30;
  return mockDelay({ ok: true, order: o });
}
