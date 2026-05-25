import { get, post } from '@/utils/request.js';

// 领取优惠券
export const takeCoupon = (templateId) =>
  post('/app-api/promotion/coupon/take', { templateId });

// 我的优惠券列表（status: 1=未使用 2=已使用 3=已过期）
export const pageCoupons = (status, pageNo = 1, pageSize = 50) =>
  get(`/app-api/promotion/coupon/page?${status ? `status=${status}&` : ''}pageNo=${pageNo}&pageSize=${pageSize}`);

// 优惠券详情
export const getCoupon = (id) => get(`/app-api/promotion/coupon/get?id=${id}`);

// 未使用数量
export const getUnusedCouponCount = () => get('/app-api/promotion/coupon/get-unused-count');

// 优惠券模板（领券中心 / 商品页可领）
export const getCouponTemplate = (id) =>
  get(`/app-api/promotion/coupon-template/get?id=${id}`);

export const listCouponTemplates = (params = {}) =>
  get(`/app-api/promotion/coupon-template/list?${new URLSearchParams(params).toString()}`);
