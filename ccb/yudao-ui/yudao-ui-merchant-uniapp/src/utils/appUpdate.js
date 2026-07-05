/**
 * App 自动升级（仅原生 App 生效，H5 / 小程序为空操作）。
 *
 * 启动时 checkAppUpdate() 会：
 *   1) 读当前 App 的 versionCode（plus.runtime.getProperty）
 *   2) 拉后端最新版本 /app-api/merchant/app-version/latest?platform=android
 *   3) 服务端 versionCode 更高 → 弹窗；确认后 uni.downloadFile 下载 APK → plus.runtime.install 触发系统安装器
 *
 * options.manual=true 时（用户在「关于」页手动点检查），无更新也会 toast 提示。
 */

// App 端后端域名（与 api/request.js 的 APP_API_ORIGIN 保持一致）
const API_BASE = 'https://tuo.doupaidoudian.com';

export function checkAppUpdate(options = {}) {
  // #ifdef APP-PLUS
  const manual = options && options.manual === true;
  try {
    plus.runtime.getProperty(plus.runtime.appid, (info) => {
      const curCode = parseInt(info && info.versionCode, 10) || 0;
      uni.request({
        url: API_BASE + '/app-api/merchant/app-version/latest?platform=android',
        method: 'GET',
        timeout: 10000,
        success: (res) => {
          const body = res && res.data;
          const data = body && body.code === 0 ? body.data : null;
          if (!data) {
            if (manual) uni.showToast({ title: '已是最新版本', icon: 'none' });
            return;
          }
          const srvCode = parseInt(data.versionCode, 10) || 0;
          if (srvCode <= curCode) {
            if (manual) uni.showToast({ title: '已是最新版本', icon: 'none' });
            return;
          }
          promptUpdate(data);
        },
        fail: () => {
          if (manual) uni.showToast({ title: '检查更新失败，请稍后重试', icon: 'none' });
        },
      });
    });
  } catch (e) {
    if (manual) uni.showToast({ title: '检查更新失败', icon: 'none' });
  }
  // #endif
}

// #ifdef APP-PLUS
function promptUpdate(data) {
  const force = !!data.forceUpdate;
  const title = ('发现新版本 ' + (data.versionName || '')).trim();
  const content = (data.updateLog && String(data.updateLog).trim()) || '修复若干问题，建议立即更新。';
  uni.showModal({
    title,
    content,
    showCancel: !force,
    cancelText: '暂不更新',
    confirmText: '立即更新',
    success: (res) => {
      if (res.confirm) startDownload(data);
      // 强制更新时用户点了遮罩外/返回：showCancel=false 已保证只能确认
    },
  });
}

function startDownload(data) {
  const url = data.downloadUrl;
  if (!url) {
    uni.showToast({ title: '下载地址无效', icon: 'none' });
    return;
  }
  uni.showLoading({ title: '下载中 0%', mask: true });
  const task = uni.downloadFile({
    url,
    success: (res) => {
      uni.hideLoading();
      if (res.statusCode !== 200 || !res.tempFilePath) {
        uni.showToast({ title: '下载失败（' + res.statusCode + '）', icon: 'none' });
        return;
      }
      installApk(res.tempFilePath);
    },
    fail: () => {
      uni.hideLoading();
      uni.showToast({ title: '下载失败，请检查网络', icon: 'none' });
    },
  });
  if (task && task.onProgressUpdate) {
    task.onProgressUpdate((p) => {
      uni.showLoading({ title: '下载中 ' + (p.progress || 0) + '%', mask: true });
    });
  }
}

function installApk(filePath) {
  // Android：install 一个 .apk 会拉起系统安装界面
  plus.runtime.install(
    filePath,
    { force: false },
    () => {
      uni.showModal({
        title: '升级完成',
        content: '点击确定重启应用',
        showCancel: false,
        success: () => plus.runtime.restart(),
      });
    },
    (e) => {
      uni.showToast({ title: '安装失败：' + ((e && e.message) || '未知错误'), icon: 'none' });
    }
  );
}
// #endif
