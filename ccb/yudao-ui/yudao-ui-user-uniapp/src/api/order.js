import { get, post, put, del } from '@/utils/request.js';
import { toQuery } from '@/utils/qs.js';

// 结算页（含优惠 / 运费）
//   params: { shopId, items: [{skuId, count}], couponId? }
export const settlement = (params) =>
  get(`/app-api/trade/order/settlement?${toQuery(params)}`);

// 商品结算价格（参与活动后）
export const settlementProduct = (spuIds) =>
  get(`/app-api/trade/order/settlement-product?spuIds=${spuIds.join(',')}`);

// 创建订单
export const createOrder = (body) =>
  post('/app-api/trade/order/create', body);

// 一体化结账（订单创建 + 余额/积分/优惠抵扣 + 改价）— C 端推荐
export const checkoutSubmit = (body, tenantId) =>
  post('/app-api/merchant/mini/checkout/submit', body, tenantId ? { tenantId } : {});

// 订单列表分页
export const pageOrders = (status, pageNo = 1, pageSize = 20) =>
  get(`/app-api/trade/order/page?${status ? `status=${status}&` : ''}pageNo=${pageNo}&pageSize=${pageSize}`);

// 各状态订单数量
export const getOrderCount = () => get('/app-api/trade/order/get-count');

// 订单详情
export const getOrderDetail = (id, sync = false) =>
  get(`/app-api/trade/order/get-detail?id=${id}${sync ? '&sync=true' : ''}`);

// 物流轨迹
export const getExpressTrack = (id) =>
  get(`/app-api/trade/order/get-express-track-list?id=${id}`);

// 确认收货
export const receiveOrder = (id) =>
  put(`/app-api/trade/order/receive?id=${id}`, null);

// 取消订单
export const cancelOrder = (id) =>
  del(`/app-api/trade/order/cancel?id=${id}`);

// 删除订单
export const deleteOrder = (id) =>
  del(`/app-api/trade/order/delete?id=${id}`);

// ===== 线下转账收款（商户未开通在线支付时） =====
// 取收款信息（商户收款码 + 应付 + 凭证状态）
export const getOfflinePayInfo = (orderId) =>
  get(`/app-api/merchant/mini/offline-pay/info?orderId=${orderId}`);

// 上传付款凭证
export const submitOfflineProof = (body) =>
  post('/app-api/merchant/mini/offline-pay/submit-proof', body);
