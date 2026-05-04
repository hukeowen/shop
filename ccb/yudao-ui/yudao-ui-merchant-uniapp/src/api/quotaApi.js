import { request } from './request.js';

const BASE = '/app-api/merchant/mini/video-quota';

/** 查询当前商户剩余配额 */
export function getMyQuota() {
  return request({ url: `${BASE}/me` });
}

/** 查询在架套餐列表 */
export function listPackages() {
  return request({ url: `${BASE}/packages` });
}

/**
 * 购买套餐 - 创建业务订单 + 支付单
 * @param {number} packageId
 * @param {string} channelCode  wx_lite
 * @returns {{ packageOrderId, payOrderId, channelCode, price, packageName, videoCount }}
 */
export function purchasePackage(packageId, channelCode = 'wx_lite') {
  return request({
    url: `${BASE}/packages/${packageId}/purchase`,
    method: 'POST',
    data: { channelCode },
  });
}

/**
 * 提交 pay 模块支付 - 获取 JSAPI 参数
 * @param {number} payOrderId
 * @param {string} channelCode  wx_lite
 * @returns {{ displayMode, displayContent }}  displayContent 是 JSAPI JSON 字符串
 */
export function submitPayOrder(payOrderId, channelCode = 'wx_lite') {
  return request({
    url: '/app-api/pay/order/submit',
    method: 'POST',
    data: { id: payOrderId, channelCode, returnUrl: '' },
  });
}

/**
 * 通联 H5 收银台购买（不走微信 JSAPI，由通联收银台接管）
 * @param {number} packageId
 * @returns {{ cashierUrl: string, params: Record<string,string> }}
 */
export function purchasePackageAllinpay(packageId) {
  return request({
    url: `${BASE}/packages/${packageId}/purchase-allinpay`,
    method: 'POST',
    data: {},
  });
}

/** 商户购买套餐订单列表（分页倒序） */
export function listMyPackageOrders(pageNo = 1, pageSize = 20) {
  return request({
    url: `${BASE}/package-orders?pageNo=${pageNo}&pageSize=${pageSize}`,
  });
}
