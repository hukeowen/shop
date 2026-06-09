import { request } from './request.js';

const BASE = '/app-api/merchant/mini/order';

/** createTime 兼容三种后端返回：number(timestamp) / ISO string / null */
function formatCreateTime(ct) {
  if (!ct) return '';
  if (typeof ct === 'number') {
    const d = new Date(ct);
    const pad = (n) => (n < 10 ? '0' + n : n);
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }
  if (typeof ct === 'string') {
    return ct.replace('T', ' ').substring(0, 16);
  }
  return '';
}

/** TradeOrderDO → UI所需字段 */
function normalizeOrder(o) {
  if (!o) return null;
  return {
    id: o.id,
    no: o.no,
    status: o.status,
    userNickname: o.receiverName || '',
    userMobile: o.receiverMobile || '',
    address: o.receiverDetailAddress || '',
    // totalPrice 仍以 payPrice 兜底（旧前端展示）；真正"商品原价"用 originalPrice 字段
    totalPrice: o.payPrice ?? o.totalPrice ?? 0,
    originalPrice: o.totalPrice ?? 0,        // 订单商品原价（抵扣前）
    payPrice: o.payPrice ?? 0,                // 实付（抵扣后）
    productCount: o.productCount ?? 0,
    remark: o.userRemark || '',
    deliveryType: o.deliveryType === 1 ? 'express' : 'pickup',
    verifyCode: o.pickUpVerifyCode || '',
    createdAt: formatCreateTime(o.createTime),
    payStatus: o.payStatus,
    // ===== 抵扣明细（v8 五路抵扣 + 优惠券）=====
    balanceDeductFen: o.balanceDeductFen ?? 0,
    consumePointDeductFen: o.consumePointDeductFen ?? 0,
    consumePointUsed: o.consumePointUsed ?? 0,
    promoPointRedeemFen: o.promoPointRedeemFen ?? 0,
    promoAutoDeductFen: o.promoAutoDeductFen ?? 0,
    promoAutoDeductCount: o.promoAutoDeductCount ?? 0,
    couponDeductFen: o.couponDeductFen ?? 0,
    // ===== 线下转账收款 =====
    offlinePay: !!o.offlinePay,
    offlinePayStatus: o.offlinePayStatus,            // 0待付款上传 1待确认 2已确认 3已驳回
    offlineProofUrl: o.offlineProofUrl || '',
    offlinePayChannel: o.offlinePayChannel || '',    // wechat / alipay
    offlineBuyerRemark: o.offlineBuyerRemark || '',
    offlineSubmitTime: formatCreateTime(o.offlineSubmitTime),
    items: (o.items || []).map((it) => ({
      spuName: it.spuName,
      skuName: it.skuName || it.spuName,
      price: it.price,
      count: it.count,
      picUrl: it.picUrl,
    })),
  };
}

/** 分页查询订单（status=-1 表示全部；status=0 表示真正的"待支付"） */
export async function getOrderPage({ status = -1, pageNo = 1, pageSize = 50 } = {}) {
  const params = { pageNo, pageSize };
  if (status >= 0) params.status = status;
  const data = await request({ url: `${BASE}/page`, data: params });
  return {
    total: data.total,
    list: (data.list || []).map(normalizeOrder),
  };
}

/** 获取订单详情 */
export async function getOrder(id) {
  const data = await request({ url: `${BASE}/get?id=${id}` });
  return normalizeOrder(data);
}

/** 快递发货（logisticsId=0 跳过公司校验，直接存单号） */
export function deliverOrder({ id, expressNo }) {
  return request({
    url: `${BASE}/delivery`,
    method: 'POST',
    data: { id: Number(id), logisticsId: 0, logisticsNo: expressNo || '' },
  });
}

/** 通过核销码查询订单 */
export async function getOrderByVerifyCode(code) {
  const data = await request({ url: `${BASE}/get-by-verify-code?pickUpVerifyCode=${code}` });
  return normalizeOrder(data);
}

/** 核销自提订单（按核销码） */
export async function pickUpVerify(code) {
  try {
    const order = await getOrderByVerifyCode(code);
    if (!order) return { ok: false, msg: '核销码无效' };
    if (order.status === 30) return { ok: false, msg: '订单已核销' };
    await request({
      url: `${BASE}/pick-up-verify?pickUpVerifyCode=${code}`,
      method: 'PUT',
    });
    return { ok: true, order };
  } catch (err) {
    return { ok: false, msg: err?.message || '核销失败' };
  }
}

/** 核销自提订单（按订单ID） */
export function pickUpById(id) {
  return request({ url: `${BASE}/pick-up-by-id?id=${id}`, method: 'PUT' });
}

/** 到店付款 - 商户确认收款（线下完成） */
export function offlineConfirm(id) {
  return request({ url: `${BASE}/offline-confirm?id=${id}`, method: 'POST' });
}

/** 商户取消订单（仅未支付） */
export function offlineCancel(id) {
  return request({ url: `${BASE}/offline-cancel?id=${id}`, method: 'POST' });
}

/** 驳回顾客线下付款凭证（要求重传） */
export function offlineReject(id, reason) {
  const q = reason ? `&reason=${encodeURIComponent(reason)}` : '';
  return request({ url: `${BASE}/offline-reject?id=${id}${q}`, method: 'POST' });
}

/** 商户主动确认送达（同城配送 / 自营配送 → 等价于代用户确认收货） */
export function confirmDelivered(id) {
  return request({ url: `${BASE}/confirm-delivered?id=${id}`, method: 'POST' });
}
