import { createSSRApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';

export function createApp() {
  const app = createSSRApp(App);
  const pinia = createPinia();
  app.use(pinia);

  // ⭐ 原生 App：主动把真实状态栏高度写进 --status-bar-height CSS 变量。
  //   Vue3+Vite 版 uni-app(app-vue) 不保证自动注入该变量，导致各页自绘顶栏
  //   padding-top: var(--status-bar-height) 取不到值 → 标题被系统状态栏遮住。
  //   这里用 uni.getSystemInfoSync().statusBarHeight（原生必有值）在每个页面 onLoad
  //   写到该页 webview 的 documentElement 上，供全部 var(--status-bar-height) 使用。
  //   app-vue 是「一页一 webview」，故每页都要设一次；H5/小程序无需(用 env() 兜底)。
  // #ifdef APP-PLUS
  const applyStatusBarVar = () => {
    try {
      const h = (uni.getSystemInfoSync && uni.getSystemInfoSync().statusBarHeight) || 0;
      if (h && typeof document !== 'undefined' && document.documentElement) {
        document.documentElement.style.setProperty('--status-bar-height', h + 'px');
      }
    } catch (e) {
      /* 忽略：取不到就退回 env() 兜底 */
    }
  };
  app.mixin({
    onLoad() { applyStatusBarVar(); },
    onShow() { applyStatusBarVar(); },
  });
  // #endif

  return { app, pinia };
}
