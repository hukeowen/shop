/**
 * 小程序自绘顶栏「状态栏占位」样式。
 *
 * 病根：uni-app 小程序把 `--status-bar-height` 写死成 25px（默认兜底），真机常不符
 *      （安卓 24~40px / iOS 刘海屏 44px+）→ 自绘顶栏 .safe-top 顶到状态栏。
 *      小程序无 document 不能改全局 CSS 变量；全局 mixin 的 data 在 <script setup>
 *      模板里又取不到。故每个自绘顶栏页在 setup 里调本函数拿到就地覆盖用的 :style。
 *
 * 用法：<script setup> 里 `const sbhStyle = sbhStyleVar()`，模板顶栏 `:style="sbhStyle"`。
 * 返回 { '--status-bar-height': 'Npx' }（真机高度），H5/App 返回 {}（各自用 env()/mixin 兜底）。
 */
export function sbhStyleVar() {
  // #ifdef MP-WEIXIN
  try {
    const h = (uni.getSystemInfoSync && uni.getSystemInfoSync().statusBarHeight) || 0;
    if (h) return { '--status-bar-height': h + 'px' };
  } catch (e) { /* 取不到就退回 env() 兜底 */ }
  // #endif
  return {};
}
