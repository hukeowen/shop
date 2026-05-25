import { get, post, put } from '@/utils/request.js';

// 我的分销信息
export const getBrokerageUser = () => get('/app-api/trade/brokerage-user/get');

// 分销统计（总佣金 / 待结算 / ...）
export const getBrokerageSummary = () => get('/app-api/trade/brokerage-user/get-summary');

// 绑定推广员（成为下级）
export const bindPromoter = (promoterId) =>
  put(`/app-api/trade/brokerage-user/bind?promoterId=${promoterId}`, null);

// 分销记录分页
export const pageBrokerageRecords = (pageNo = 1, pageSize = 20) =>
  get(`/app-api/trade/brokerage-record/page?pageNo=${pageNo}&pageSize=${pageSize}`);

// 商品分销金额
export const getProductBrokerage = (spuId) =>
  get(`/app-api/trade/brokerage-record/get-product-brokerage-price?spuId=${spuId}`);

// 分销榜（按用户数）
export const rankByUserCount = (pageNo = 1, pageSize = 50) =>
  get(`/app-api/trade/brokerage-user/rank-page-by-user-count?pageNo=${pageNo}&pageSize=${pageSize}`);

// 分销榜（按佣金）
export const rankByPrice = (pageNo = 1, pageSize = 50) =>
  get(`/app-api/trade/brokerage-user/rank-page-by-price?pageNo=${pageNo}&pageSize=${pageSize}`);

// 下级分销统计分页
export const childSummaryPage = (pageNo = 1, pageSize = 20) =>
  get(`/app-api/trade/brokerage-user/child-summary-page?pageNo=${pageNo}&pageSize=${pageSize}`);

// 我的分销排名（佣金）
export const myRankByPrice = () => get('/app-api/trade/brokerage-user/get-rank-by-price');

// 提现 列表 / 详情 / 申请
export const pageWithdraws = (pageNo = 1, pageSize = 20) =>
  get(`/app-api/trade/brokerage-withdraw/page?pageNo=${pageNo}&pageSize=${pageSize}`);

export const getWithdraw = (id) =>
  get(`/app-api/trade/brokerage-withdraw/get?id=${id}`);

export const applyWithdraw = (body) =>
  post('/app-api/trade/brokerage-withdraw/create', body);
