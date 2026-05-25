import { get, post } from '@/utils/request.js';

// 推广积分余额（C 端可跨店聚合 + 单店）
export const getPromoAccount = (tenantId) =>
  get('/app-api/merchant/mini/promo/account', { tenantId });

// 推广积分流水
export const listPromoRecords = (tenantId, page = 1, size = 20) =>
  get(`/app-api/merchant/mini/promo/records?pageNo=${page}&pageSize=${size}`, { tenantId });

// 消费积分流水
export const listConsumePoints = (tenantId, page = 1, size = 20) =>
  get(`/app-api/merchant/mini/promo/consume-points?pageNo=${page}&pageSize=${size}`, { tenantId });

// 我的队列（推 N 反 1 当前进度）
export const getMyQueue = (tenantId) =>
  get('/app-api/merchant/mini/promo/my-queues', { tenantId });

// 中奖公榜（店内最新中奖记录）
export const getWinnerBoard = (tenantId) =>
  get('/app-api/merchant/mini/promo/winners', { tenantId });

// 榜一排名（推广榜前 N）
export const getRankTop = (tenantId, limit = 50) =>
  get(`/app-api/merchant/mini/promo/rank?limit=${limit}`, { tenantId });

// 钱包余额（balance + 可提现）
export const getWallet = (tenantId) =>
  get('/app-api/merchant/mini/promo/wallet', { tenantId });

// 提现申请
export const applyWithdraw = (body, tenantId) =>
  post('/app-api/merchant/mini/promo/withdraw', body, { tenantId });

// 优惠券列表
export const listCoupons = (status = 'unused') =>
  get(`/app-api/promotion/coupon/page?status=${status}&pageNo=1&pageSize=50`);

// 推荐关系绑定
export const bindReferral = (inviterUserId, tenantId) =>
  post(`/app-api/merchant/mini/promo/referral/bind?inviterUserId=${inviterUserId}`, null, { tenantId });
