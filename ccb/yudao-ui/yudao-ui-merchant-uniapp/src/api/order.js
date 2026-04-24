import { request } from './request.js';

const BASE = '/app-api/merchant/mini/order';

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
    totalPrice: o.payPrice ?? o.totalPrice ?? 0,
    productCount: o.productCount ?? 0,
    remark: o.userRemark || '',
    deliveryType: o.deliveryType === 1 ? 'express' : 'pickup',
    verifyCode: o.pickUpVerifyCode || '',
    createdAt: o.createTime ? o.createTime.replace('T', ' ').substring(0, 16) : '',
    payStatus: o.payStatus,
    items: (o.items || []).map((it) => ({
      spuName: it.spuName,
      skuName: it.skuName || it.spuName,
      price: it.price,
      count: it.count,
      picUrl: it.picUrl,
    })),
  };
}

/** 分页查询订单（status=0 表示全部） */
export async function getOrderPage({ status = 0, pageNo = 1, pageSize = 50 } = {}) {
  const params = { pageNo, pageSize };
  if (status) params.status = status;
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
