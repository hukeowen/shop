/**
 * App / H5 通用「打开外部链接」（通联收银台、支付跳转等）。
 *
 * - H5：走 window.location.href 跳转（原逻辑）。
 * - App(APP-PLUS)：没有 window.location，用 plus.runtime.openURL 调系统浏览器打开；
 *   用户在浏览器完成通联支付后返回 App（订单侧靠兜底轮询 markPaid 落地，不依赖回跳）。
 *
 * 之前多处直接 `location.href = url`，App 里 location 未定义会抛错导致支付跳转卡死。
 */
export function openExternalUrl(url) {
  if (!url) return;
  // #ifdef APP-PLUS
  try {
    plus.runtime.openURL(url);
    return;
  } catch (e) {
    // 兜底：极少数机型 openURL 失败，尝试 uni 内置 webview（需在 pages.json 注册 webview 页时启用）
    try { uni.showToast({ title: '正在打开支付…', icon: 'none' }); } catch (_) {}
    return;
  }
  // #endif
  // #ifdef MP-WEIXIN
  // 小程序无 window/location，也打不开外部链接。付款一律走 launchMpCashier
  //（navigateToMiniProgram 拉起通联收银台），不该走到这里。万一走到：复制链接 + 提示，
  // 绝不触碰 location.href —— 从源头保证小程序编译产物里没有任何 .href 赋值。
  try {
    uni.setClipboardData({
      data: url,
      success: () => uni.showToast({ title: '支付链接已复制，请在浏览器打开', icon: 'none' }),
    });
  } catch (e) { /* ignore */ }
  return;
  // #endif
  // #ifdef H5
  try {
    if (typeof window !== 'undefined' && window.location) { window.location.href = url; return; }
  } catch (e) { /* ignore */ }
  try {
    if (typeof location !== 'undefined') location.href = url;
  } catch (e) { /* ignore */ }
  // #endif
}
