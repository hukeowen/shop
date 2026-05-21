/**
 * 营销配置 API（v6 双积分 / 星级递减 / 邀请激励 / 星级积分池）
 *
 *   · GET/PUT /app-api/merchant/mini/promo/config              商户级
 *   · GET/PUT /app-api/merchant/mini/promo/product-config      商品级
 */
import { request } from './request.js';

const SHOP_BASE = '/app-api/merchant/mini/promo/config';
const PRODUCT_BASE = '/app-api/merchant/mini/promo/product-config';

// ==================== 商户级 ====================

/** 拉取本商户营销配置；未配置时后端返默认值。 */
export function getShopPromoConfig() {
  return request({ url: SHOP_BASE });
}

/** upsert 本商户营销配置。req 字段与 PromoConfigSaveReqVO 一一对应。 */
export function saveShopPromoConfig(req) {
  return request({ url: SHOP_BASE, method: 'PUT', data: req });
}

// ==================== 商品级 ====================

/** 拉取某商品的营销配置；未配置时后端返"全关"默认值。 */
export function getProductPromoConfig(spuId) {
  return request({ url: `${PRODUCT_BASE}?spuId=${spuId}` });
}

/** upsert 某商品的营销配置。 */
export function saveProductPromoConfig(req) {
  return request({ url: PRODUCT_BASE, method: 'PUT', data: req });
}

// ==================== 用户钱包（双积分） ====================

/** 当前用户星级 + 双积分余额。 */
export function getMyAccount() {
  return request({ url: '/app-api/merchant/mini/promo/account' });
}

/** 推广积分流水。 */
export function listPromoRecords({ pageNo = 1, pageSize = 20 } = {}) {
  return request({
    url: '/app-api/merchant/mini/promo/promo-records',
    data: { pageNo, pageSize },
  });
}

/** 消费积分流水。 */
export function listConsumeRecords({ pageNo = 1, pageSize = 20 } = {}) {
  return request({
    url: '/app-api/merchant/mini/promo/consume-records',
    data: { pageNo, pageSize },
  });
}

/** 推广积分 → 消费积分；idempotencyKey 防双击。 */
export function convertPromoToConsume(promoAmount, idempotencyKey) {
  return request({
    url: '/app-api/merchant/mini/promo/convert',
    method: 'POST',
    data: { promoAmount, idempotencyKey },
  });
}

/** 当前用户在所有商品队列中的位置（仅 QUEUEING）。 */
export function listMyQueues() {
  return request({ url: '/app-api/merchant/mini/promo/my-queues' });
}

// ==================== 推荐链 ====================

/** 绑定邀请人（首次有效，per-tenant 终生）。必传 tenantId，后端按上下文绑当前店。 */
export function bindReferral(inviterUserId, orderId, tenantId) {
  const q = `inviterUserId=${encodeURIComponent(inviterUserId)}` +
            (orderId ? `&orderId=${encodeURIComponent(orderId)}` : '');
  return request({
    url: `/app-api/merchant/mini/promo/referral/bind?${q}`,
    method: 'POST',
    tenantId: tenantId || undefined,  // header tenant-id → 后端 TenantContextHolder → bindParent per-tenant 操作
  });
}

/** 当前用户的直接邀请人（0 = 自然用户）。 */
export function getMyReferralParent() {
  return request({ url: '/app-api/merchant/mini/promo/referral/parent' });
}

// ==================== 积分池 ====================

export function getPoolInfo() {
  return request({ url: '/app-api/merchant/mini/promo/pool/info' });
}

export function settlePool(mode) {
  return request({
    url: '/app-api/merchant/mini/promo/pool/settle',
    method: 'POST',
    data: { mode },
  });
}

export function listPoolRounds({ pageNo = 1, pageSize = 20 } = {}) {
  return request({
    url: '/app-api/merchant/mini/promo/pool/rounds',
    data: { pageNo, pageSize },
  });
}

// ==================== v8 SPU 级奖池（按商品独立） ====================

/** 查某 SPU 当前池余额 + 历史入/出 */
export function getSpuPoolBalance(spuId) {
  return request({
    url: '/app-api/merchant/mini/promo/spu-pool/balance',
    data: { spuId },
  });
}

/** 商户触发某 SPU 立即结算 */
export function settleSpuPool(spuId, remark) {
  const q = `spuId=${encodeURIComponent(spuId)}` + (remark ? `&remark=${encodeURIComponent(remark)}` : '');
  return request({
    url: `/app-api/merchant/mini/promo/spu-pool/settle?${q}`,
    method: 'POST',
  });
}

/** 商户查某 SPU 的历次结算单 */
export function listSpuPoolRecords(spuId, { pageNo = 1, pageSize = 10 } = {}) {
  return request({
    url: '/app-api/merchant/mini/promo/spu-pool/settle-records',
    data: { spuId, pageNo, pageSize },
  });
}

/** 商户查某次结算的中奖明细 */
export function listSpuPoolPayouts(settleId) {
  return request({
    url: '/app-api/merchant/mini/promo/spu-pool/settle-record/payouts',
    data: { settleId },
  });
}

// ==================== 用户端「奖池公示」 ====================

/** 用户：查某 SPU 当前池余额（公开） */
export function getPublicPoolBalance(spuId) {
  return request({
    url: '/app-api/merchant/promo/pool/balance',
    data: { spuId },
  });
}

/** 用户：查某 SPU 最近一次结算的中奖名单（脱敏） */
export function getPublicLatestPayouts(spuId, limit = 20) {
  return request({
    url: '/app-api/merchant/promo/pool/latest-payouts',
    data: { spuId, limit },
  });
}

/** 用户：分页查某 SPU 历次结算 */
export function listPublicSettleRecords(spuId, { pageNo = 1, pageSize = 10 } = {}) {
  return request({
    url: '/app-api/merchant/promo/pool/settle-records',
    data: { spuId, pageNo, pageSize },
  });
}

/** 用户：查某次结算的中奖名单（脱敏） */
export function getPublicPayoutsBySettle(settleId) {
  return request({
    url: '/app-api/merchant/promo/pool/settle-record/payouts',
    data: { settleId },
  });
}

// ==================== 提现 ====================

/** 用户申请提现。 */
export function applyWithdraw(amount, tenantId) {
  return request({
    url: '/app-api/merchant/mini/withdraw/apply',
    method: 'POST',
    data: { amount, tenantId },
  });
}

/** 当前用户的提现申请。 */
export function listMyWithdraws() {
  return request({ url: '/app-api/merchant/mini/withdraw/my-list' });
}

/** 商户审批端：分页查申请（mini 端点，按商户号 tenant 过滤）。 */
export function pageWithdrawAdmin({ status, pageNo = 1, pageSize = 20 } = {}) {
  return request({
    url: '/app-api/merchant/mini/withdraw/page',
    data: { status, pageNo, pageSize },
  });
}

export function approveWithdraw(id, remark) {
  return request({
    url: '/app-api/merchant/mini/withdraw/approve',
    method: 'POST',
    data: { id, remark },
  });
}

export function rejectWithdraw(id, remark) {
  return request({
    url: '/app-api/merchant/mini/withdraw/reject',
    method: 'POST',
    data: { id, remark },
  });
}

export function markPaidWithdraw(id, remark) {
  return request({
    url: '/app-api/merchant/mini/withdraw/mark-paid',
    method: 'POST',
    data: { id, remark },
  });
}
