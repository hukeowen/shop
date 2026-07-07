/**
 * 小程序自绘顶栏「状态栏占位」样式。
 *
 * 病根：uni-app 小程序把 `--status-bar-height` 写死成 25px（默认兜底），真机常不符
 *      （安卓 24~40px / iOS 刘海屏 44px+）→ 自绘顶栏顶到状态栏。
 *
 * ⚠ 关键教训：**微信小程序 inline style 不支持 CSS 变量**，`:style="{'--status-bar-height':'44px'}"`
 *   会被忽略，等于没设。所以这里**直接返回具体 px 的 padding-top**（inline style 100% 支持），
 *   覆盖 .safe-top 的 calc(...)。+10px 是状态栏与内容的呼吸间距（约等于原设计 +16~24rpx，
 *   也让顶栏内容与右上角胶囊大致对齐）。
 *
 * 用法：<script setup> 里 `const sbhStyle = sbhStyleVar()`，模板顶栏 `:style="sbhStyle"`。
 * H5/App 返回 {}（各自用 env() / App 端 mixin 兜底）。
 */
export function sbhStyleVar() {
  // #ifdef MP-WEIXIN
  try {
    const h = (uni.getSystemInfoSync && uni.getSystemInfoSync().statusBarHeight) || 0;
    // 真机状态栏高度取不到时兜底 20px；正常 44/30 等
    return { paddingTop: ((h || 20) + 10) + 'px' };
  } catch (e) {
    return { paddingTop: '30px' };
  }
  // #endif
  // eslint-disable-next-line no-unreachable
  return {};
}
