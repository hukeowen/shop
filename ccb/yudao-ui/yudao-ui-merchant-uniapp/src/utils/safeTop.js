/**
 * 小程序自绘顶栏「状态栏占位」样式。
 *
 * 踩坑史（别再犯）：
 *  1. uni-app 小程序把 `--status-bar-height` 写死 25px，真机不符 → 顶栏顶到状态栏。
 *  2. 用 :style 设 CSS 变量（--status-bar-height）→ 微信小程序 inline style **不支持 CSS 变量**，无效。
 *  3. 改 inline `padding-top` → 顶栏类里有 `padding: 16rpx 32rpx` **简写**，微信小程序 WXSS 引擎里
 *     inline 的 padding-top 覆盖不了 class 的 padding 简写 → 还是无效。
 *  ✅ 最终解：用 **inline `margin-top`**（外边距，跟 padding 简写完全不冲突；顶栏类都没设 margin；
 *     static 流内顶栏和 absolute 定位顶栏都能被 margin-top 顶下去）。真机高度直接给 px。
 *
 * 用法：<script setup> `const sbhStyle = sbhStyleVar()`，模板顶栏 `:style="sbhStyle"`。
 * H5/App 返回 {}（各自 env()/App mixin 兜底）。
 */
export function sbhStyleVar() {
  // #ifdef MP-WEIXIN
  try {
    const h = (uni.getSystemInfoSync && uni.getSystemInfoSync().statusBarHeight) || 0;
    return { marginTop: ((h || 20)) + 'px' };
  } catch (e) {
    return { marginTop: '30px' };
  }
  // #endif
  // eslint-disable-next-line no-unreachable
  return {};
}
