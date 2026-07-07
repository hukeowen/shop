/**
 * 小程序自绘顶栏「状态栏占位块」。在自绘顶栏前插一个 <view :style="sbhSpacerStyle()"></view>，
 * 物理占据状态栏高度把顶栏顶到状态栏下方（最可靠，见踩坑史）。H5/App 返回不占位。
 */
function sbhPx() {
  // #ifdef MP-WEIXIN
  try {
    const h = (uni.getSystemInfoSync && uni.getSystemInfoSync().statusBarHeight) || 0;
    return h || 20;
  } catch (e) {
    return 20;
  }
  // #endif
  // eslint-disable-next-line no-unreachable
  return 0;
}
export function sbhSpacerStyle() {
  // #ifdef MP-WEIXIN
  // ⚠ 临时红色背景：便于真机确认「是否跑的新包」，确认生效后改回 transparent。
  return { height: sbhPx() + 'px', flexShrink: '0', width: '100%', background: '#ff3b30' };
  // #endif
  // eslint-disable-next-line no-unreachable
  return { display: 'none' };
}

/**
 * 用于「absolute/fixed 定位」的悬浮顶栏按钮（占位块推不动它们）。
 * 返回一个内联 top 偏移，把按钮从状态栏下方开始排（叠加原本的 24rpx 间距）。
 * 例：product/detail 悬浮在主图上的 返回/主页/分享 三个圆钮。H5/App 返回 {}（走 CSS env 兜底）。
 */
export function sbhTopStyle(extraPx = 12) {
  // #ifdef MP-WEIXIN
  return { top: sbhPx() + extraPx + 'px' };
  // #endif
  // eslint-disable-next-line no-unreachable
  return {};
}
