import { request } from './request.js';

/**
 * 商户首页数据看板（实时）。
 * 后端 AppMerchantDashboardController -> /app-api/merchant/mini/dashboard/summary。
 *
 * 后端 RespVO 字段：
 *   today*       —— todayOrderCount / todayOrderAmount(分) / todayNewMemberCount
 *   pending*     —— pendingShipmentCount / pendingVerifyCount / pendingAfterSaleCount
 *   trend*       —— trendLabels[7] / trendOrderCounts[7] / trendSalesAmount[7]
 *   topProducts  —— [{spuId,name,picUrl,salesCount,salesAmount}]
 *
 * 这里把它扁平化成首页 index.vue 现用的 today/trend/topProducts 三段结构，
 * 让模板代码不用大改。
 */
export async function getDashboard() {
  const resp = await request({
    url: '/app-api/merchant/mini/dashboard/summary',
    method: 'GET',
  });
  const r = resp || {};
  return {
    today: {
      orderCount: numOr(r.todayOrderCount, 0),
      salesAmount: numOr(r.todayOrderAmount, 0),
      newMembers: numOr(r.todayNewMemberCount, 0),
      // 首页"待处理"卡只展示一个数 → 三档相加
      pendingOrders:
        numOr(r.pendingShipmentCount, 0) +
        numOr(r.pendingVerifyCount, 0) +
        numOr(r.pendingAfterSaleCount, 0),
    },
    trend: {
      labels: Array.isArray(r.trendLabels) ? r.trendLabels : [],
      sales: Array.isArray(r.trendSalesAmount) ? r.trendSalesAmount.map((v) => numOr(v, 0)) : [],
      orders: Array.isArray(r.trendOrderCounts) ? r.trendOrderCounts.map((v) => numOr(v, 0)) : [],
    },
    topProducts: Array.isArray(r.topProducts)
      ? r.topProducts.map((p) => ({
          spuId: p.spuId,
          name: p.name || '未命名商品',
          picUrl: p.picUrl || '',
          count: numOr(p.salesCount, 0),
          amount: numOr(p.salesAmount, 0),
        }))
      : [],
    // v8: 今日推广（dashboard 加的字段）
    promo: {
      issued: numOr(r.todayPromoIssued, 0),
      deducted: numOr(r.todayPromoDeducted, 0),
      commission: numOr(r.todayPromoCommission, 0),
      poolDeposit: numOr(r.todayPromoPoolDeposit, 0),
    },
  };
}

// ========== 销售统计页 / 排行 / 漏斗 / 热力 / 会员 ==========

export async function getSalesStats(period = 'month') {
  return request({ url: '/app-api/merchant/mini/stats/sales', method: 'GET', data: { period } });
}

export async function getProductRank(period = 'month', sort = 'count', limit = 20) {
  return request({ url: '/app-api/merchant/mini/stats/product-rank', method: 'GET', data: { period, sort, limit } });
}

export async function listMembers(pageNo = 1, pageSize = 20) {
  return request({ url: '/app-api/merchant/mini/stats/members', method: 'GET', data: { pageNo, pageSize } });
}

export async function getHourlyHeatmap(period = 'month') {
  return request({ url: '/app-api/merchant/mini/stats/hourly-heatmap', method: 'GET', data: { period } });
}

export async function getReferralFunnel(spuId) {
  return request({ url: '/app-api/merchant/mini/stats/referral-funnel', method: 'GET', data: spuId ? { spuId } : {} });
}

function numOr(v, fallback) {
  if (v === null || v === undefined) return fallback;
  const n = Number(v);
  return Number.isFinite(n) ? n : fallback;
}
