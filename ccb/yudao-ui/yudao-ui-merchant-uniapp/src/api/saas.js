/**
 * V042: SaaS 套餐 + 商户开店分享码 API
 */
import { request } from './request.js';

const BASE = '/app-api/merchant/mini/invite-share-code';

/** 取或生成「我的开店分享码」（仅 BASIC/PRO 商户可调；TRIAL 返 403） */
export function getOrCreateMyShareCode() {
  return request({ url: `${BASE}/my` });
}

/** 匿名按 code 查邀请人（入驻页可用） */
export function lookupShareCode(code) {
  return request({ url: `${BASE}/lookup`, data: { code } });
}
