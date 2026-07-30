/**
 * 拉起通联「微信小程序收银台」完成微信/支付宝付款。
 *
 * 后端 /app-api/merchant/mp-pay/cashier 返回 { userName(原始ID gh_), appId(wx开头), path(带签名) }。
 * - MP-WEIXIN：uni.navigateToMiniProgram({ appId, path }) 跳过去；
 * - APP-PLUS：走微信开放平台 OpenSDK —— plus.share weixin.launchMiniProgram({ id: 原始ID, path })，
 *   注意 launchMiniProgram 的 id 是收银台小程序的「原始ID(gh_)」而非 appId。
 * 付完手动返回，订单状态靠后端通联回调 + 轮询落地。
 *
 * 关键：小程序/无 window 环境绝不能用 location.href。所有支付跳转走这里或被 #ifdef H5 隔离。
 *
 * @returns {Promise<boolean>} true=已拉起收银台（调用方不用再兜底）
 */
import { request } from './request.js';

async function doLaunch(apiUrl) {
  try {
    const info = await request({ url: apiUrl });
    if (!info || !info.path) {
      uni.showToast({ title: '获取收银台信息失败', icon: 'none' });
      return false;
    }
    // #ifdef MP-WEIXIN
    if (info.appId) {
      if (typeof wx !== 'undefined' && typeof wx.openEmbeddedMiniProgram === 'function') {
        wx.openEmbeddedMiniProgram({
          appId: info.appId,
          path: info.path,
          fail: () =>
            uni.navigateToMiniProgram({
              appId: info.appId,
              path: info.path,
              fail: (e) => uni.showToast({ title: '拉起收银台失败：' + ((e && e.errMsg) || ''), icon: 'none' }),
            }),
        });
      } else {
        uni.navigateToMiniProgram({
          appId: info.appId,
          path: info.path,
          fail: (e) => uni.showToast({ title: '拉起收银台失败：' + ((e && e.errMsg) || ''), icon: 'none' }),
        });
      }
      return true;
    }
    // #endif
    // #ifdef APP-PLUS
    return await appLaunchMiniProgram(info);
    // #endif
    // eslint-disable-next-line no-unreachable
    uni.showToast({ title: '当前环境不支持拉起收银台', icon: 'none' });
    return false;
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '获取收银台失败', icon: 'none' });
    return false;
  }
}

// #ifdef APP-PLUS
// App 端：微信开放平台移动应用 OpenSDK 拉起小程序（需 manifest 配 Share 模块 + weixin appid，
// 且该 appid 在开放平台登记了本 App 的 Android 包名+签名）。id 用收银台小程序原始ID(gh_)。
function appLaunchMiniProgram(info) {
  return new Promise((resolve) => {
    const userName = info.userName || info.originalId;
    if (!userName) {
      uni.showToast({ title: '缺少收银台原始ID', icon: 'none' });
      resolve(false);
      return;
    }
    plus.share.getServices(
      (services) => {
        const wxsvc = (services || []).find((s) => s.id === 'weixin');
        if (!wxsvc) {
          uni.showToast({ title: '未配置微信模块或未安装微信', icon: 'none' });
          resolve(false);
          return;
        }
        wxsvc.launchMiniProgram({
          id: userName,
          path: info.path,
          type: 0, // 0=正式版 1=开发版 2=体验版
        });
        resolve(true);
      },
      (e) => {
        uni.showToast({ title: '微信服务不可用：' + ((e && e.message) || ''), icon: 'none' });
        resolve(false);
      }
    );
  });
}
// #endif

/** 商品订单（trade_order.id）→ 拉起通联微信小程序收银台。H5 由调用方走 location.href 兜底。 */
export async function launchMpCashier(tradeOrderId) {
  if (!tradeOrderId) return false;
  // #ifndef H5
  return doLaunch(`/app-api/merchant/mp-pay/cashier?tradeOrderId=${tradeOrderId}`);
  // #endif
  // eslint-disable-next-line no-unreachable
  return false;
}
