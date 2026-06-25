/**
 * 服务卡包 / 核销 API（商户端）
 *
 *   · 建商品配卡：defs/save、defs
 *   · 核销：verify-info、redeem、verify-records
 *
 * 全部走 requireMerchantTenantId（后端从 merchant_info 解析当前商户租户），
 * 前端无需传 tenantId。
 */
import { request } from './request.js';

const BASE = '/app-api/merchant/mini/card';

/** 全量保存某商品的服务卡定义。defs: [{name, validityDays, maxCount, description}] */
export function saveCardDefs(spuId, defs) {
  return request({ url: `${BASE}/defs/save?spuId=${spuId}`, method: 'POST', data: defs || [] });
}

/** 查某商品的服务卡定义（编辑回显） */
export function getCardDefs(spuId) {
  return request({ url: `${BASE}/defs?spuId=${spuId}` });
}

/** 扫码/输码后查卡信息（不改数据） */
export function cardVerifyInfo(cardNo) {
  return request({ url: `${BASE}/verify-info?cardNo=${encodeURIComponent(cardNo)}` });
}

/** 核销一次 */
export function redeemCard(cardNo, remark) {
  let url = `${BASE}/redeem?cardNo=${encodeURIComponent(cardNo)}`;
  if (remark) url += `&remark=${encodeURIComponent(remark)}`;
  return request({ url, method: 'POST' });
}

/** 本店核销记录分页 */
export function cardVerifyRecords(pageNo = 1, pageSize = 20) {
  return request({ url: `${BASE}/verify-records?pageNo=${pageNo}&pageSize=${pageSize}` });
}
