/**
 * 小程序自绘顶栏「状态栏占位」。
 *
 * 踩坑史（三次都被不同机制吃掉）：
 *  1. :style 设 CSS 变量 --status-bar-height → 微信小程序 inline style 不支持 CSS 变量 → 无效
 *  2. :style 设 padding-top → 顶栏 class 的 `padding` 简写覆盖了它 → 无效
 *  3. :style 设 margin-top → 顶栏是页面第一个子元素，margin「外边距塌陷」被吃掉 → 无效
 *  ✅ 最终解：在顶栏**前面插一个有固定 height 的空占位 view**（sbhSpacerStyle）。它物理占据
 *     状态栏高度的空间，把后面的顶栏顶下去，不依赖 CSS 变量 / 不碰 padding / 不会塌陷。
 *     absolute 定位的沉浸式顶栏（shop-home）另用 sbhStyleVar 的 margin-top（absolute 无塌陷）。
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

/** 流内自绘顶栏：放在顶栏前的占位块样式（物理占位，最可靠）。
 *  ⚠ 临时加了红色背景，便于真机确认「是否跑的新包」——确认生效后改回 transparent。 */
export function sbhSpacerStyle() {
  // #ifdef MP-WEIXIN
  const h = sbhPx();
  // eslint-disable-next-line no-console
  try { console.log('[SBH] statusBarHeight=', h, 'build=SPACER-v3'); } catch (e) {}
  return { height: h + 'px', flexShrink: '0', width: '100%', background: '#ff3b30' };
  // #endif
  // eslint-disable-next-line no-unreachable
  return { display: 'none' };
}

/** absolute 定位顶栏（shop-home 沉浸式）：margin-top 顶下（absolute 无塌陷） */
export function sbhStyleVar() {
  // #ifdef MP-WEIXIN
  return { marginTop: sbhPx() + 'px' };
  // #endif
  // eslint-disable-next-line no-unreachable
  return {};
}
