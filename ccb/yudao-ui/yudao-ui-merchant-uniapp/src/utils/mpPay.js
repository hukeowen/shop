/**
 * 微信小程序支付：拉起通联「微信小程序收银台」小程序完成微信/支付宝付款。
 *
 * 仅 MP-WEIXIN 生效（其它平台返回 false，调用方走原有 openExternalUrl(H5收银台) 兜底）。
 * 后端 /app-api/merchant/mp-pay/cashier 返回 { userName(原始ID), appId(wx开头), path(带签名) }。
 * 小程序用 uni.navigateToMiniProgram({ appId, path }) 跳过去；用户在收银台里选微信/支付宝付。
 * 付完手动返回，订单状态靠后端通联回调 + 轮询落地（已实现）。
 *
 * @returns {Promise<boolean>} true=已拉起收银台（调用方不用再 openExternalUrl）
 */
import { request } from '../api/request.js';

// #ifdef MP-WEIXIN
async function doLaunch(apiUrl) {
  try {
    const info = await request({ url: apiUrl });
    if (info && info.appId && info.path) {
      // 全屏跳转（兜底）：完全切到通联收银台小程序
      const jumpFull = () => uni.navigateToMiniProgram({
        appId: info.appId,
        path: info.path,
        fail: (e) => uni.showToast({ title: '拉起收银台失败：' + ((e && e.errMsg) || ''), icon: 'none' }),
      });
      // ⭐ 优先「半屏拉起」（wx.openEmbeddedMiniProgram）：收银台以半屏浮层打开，
      //   用户不离开本小程序，付完自动回来，体验更好。
      //   需基础库 ≥ 2.20.1 且通联收银台支持半屏；不支持 / 调用失败时自动退回全屏跳转。
      if (typeof wx !== 'undefined' && typeof wx.openEmbeddedMiniProgram === 'function') {
        wx.openEmbeddedMiniProgram({
          appId: info.appId,
          path: info.path,
          fail: () => jumpFull(),
        });
      } else {
        jumpFull();
      }
      return true;
    }
    uni.showToast({ title: '获取收银台信息失败', icon: 'none' });
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '获取收银台失败', icon: 'none' });
  }
  return false;
}
// #endif

// #ifdef APP-PLUS
// 原生 App（商户端 Android）：用微信开放平台移动应用 SDK 拉起通联「微信小程序收银台」小程序，
// 微信原生支付 → 彻底没有 Apple Pay（银联H5收银台在安卓也会弹 Apple Pay 是通联产品行为，改不动）。
// 后端返 { userName(收银台原始ID gh_xxx), path(带签名) }；plus.share weixin.launchMiniProgram 拉起。
// ⚠ 前提：App 已在微信开放平台注册「移动应用」，manifest 配了微信 SDK(登录/分享模块 + 移动应用 AppID)。
async function doLaunchApp(apiUrl) {
  try {
    const info = await request({ url: apiUrl });
    if (info && info.userName && info.path) {
      return await new Promise((resolve) => {
        try {
          plus.share.getServices((services) => {
            const weixin = (services || []).find((s) => s.id === 'weixin');
            if (!weixin) {
              uni.showToast({ title: '未检测到微信，请先安装微信', icon: 'none' });
              resolve(false);
              return;
            }
            try {
              // id=收银台原始ID(gh_xxx)；type: 0=正式版 / 2=体验版
              weixin.launchMiniProgram({ id: info.userName, path: info.path, type: 0 });
              resolve(true);
            } catch (e2) {
              uni.showToast({ title: '拉起微信收银台失败', icon: 'none' });
              resolve(false);
            }
          }, () => {
            uni.showToast({ title: '拉起微信收银台失败', icon: 'none' });
            resolve(false);
          });
        } catch (e) { resolve(false); }
      });
    }
    uni.showToast({ title: '获取收银台信息失败', icon: 'none' });
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '获取收银台失败', icon: 'none' });
  }
  return false;
}
// #endif

/** 商品/套餐订单（trade_order.id） */
export async function launchMpCashier(tradeOrderId) {
  if (!tradeOrderId) return false;
  // #ifdef MP-WEIXIN
  return doLaunch(`/app-api/merchant/mp-pay/cashier?tradeOrderId=${tradeOrderId}`);
  // #endif
  // #ifdef APP-PLUS
  return doLaunchApp(`/app-api/merchant/mp-pay/cashier?tradeOrderId=${tradeOrderId}`);
  // #endif
  // eslint-disable-next-line no-unreachable
  return false;
}

/** AI 视频配额订单（merchant_package_order.id） */
export async function launchMpCashierPackage(packageOrderId) {
  if (!packageOrderId) return false;
  // #ifdef MP-WEIXIN
  return doLaunch(`/app-api/merchant/mp-pay/cashier-package?packageOrderId=${packageOrderId}`);
  // #endif
  // #ifdef APP-PLUS
  return doLaunchApp(`/app-api/merchant/mp-pay/cashier-package?packageOrderId=${packageOrderId}`);
  // #endif
  // eslint-disable-next-line no-unreachable
  return false;
}
