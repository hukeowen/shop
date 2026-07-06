/**
 * 微信小程序支付：拉起通联「微信小程序收银台」小程序完成微信/支付宝付款。
 *
 * 仅 MP-WEIXIN 生效（其它平台返回 false，调用方走 H5 location.href 兜底）。
 * 后端 /app-api/merchant/mp-pay/cashier 返回 { userName(原始ID), appId(wx开头), path(带签名) }。
 * 小程序用 uni.navigateToMiniProgram({ appId, path }) 跳过去；用户在收银台里选微信/支付宝付。
 * 付完手动返回，订单状态靠后端通联回调 + 轮询落地。
 *
 * 关键：小程序没有 window/location，绝不能用 location.href 跳转（会 "Cannot set property
 * href of undefined"）。所有支付跳转必须走这里或被 #ifndef MP-WEIXIN 隔离。
 *
 * @returns {Promise<boolean>} true=已拉起收银台（调用方不用再兜底）
 */
import { request } from './request.js';

// #ifdef MP-WEIXIN
async function doLaunch(apiUrl) {
  try {
    const info = await request({ url: apiUrl });
    if (info && info.appId && info.path) {
      uni.navigateToMiniProgram({
        appId: info.appId,
        path: info.path,
        fail: (e) => uni.showToast({ title: '拉起收银台失败：' + ((e && e.errMsg) || ''), icon: 'none' }),
      });
      return true;
    }
    uni.showToast({ title: '获取收银台信息失败', icon: 'none' });
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '获取收银台失败', icon: 'none' });
  }
  return false;
}
// #endif

/** 商品订单（trade_order.id）→ 拉起通联微信小程序收银台 */
export async function launchMpCashier(tradeOrderId) {
  if (!tradeOrderId) return false;
  // #ifdef MP-WEIXIN
  return doLaunch(`/app-api/merchant/mp-pay/cashier?tradeOrderId=${tradeOrderId}`);
  // #endif
  // eslint-disable-next-line no-unreachable
  return false;
}
