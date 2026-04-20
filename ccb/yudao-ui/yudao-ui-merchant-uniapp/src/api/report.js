import { mockDelay } from './request.js';

// 首页数据看板
export function getDashboard() {
  return mockDelay({
    today: {
      orderCount: 18,
      salesAmount: 36800, // 分
      newMembers: 5,
      pendingOrders: 3,
    },
    trend: {
      // 最近 7 天销售额（分）
      labels: ['4-14', '4-15', '4-16', '4-17', '4-18', '4-19', '4-20'],
      sales: [21500, 26800, 19200, 31000, 28400, 42100, 36800],
      orders: [11, 15, 9, 17, 14, 21, 18],
    },
    topProducts: [
      { name: '蜜薯（大）', count: 42, amount: 21000 },
      { name: '烤地瓜（大）', count: 35, amount: 21000 },
      { name: '甜玉米', count: 28, amount: 8400 },
    ],
  });
}
