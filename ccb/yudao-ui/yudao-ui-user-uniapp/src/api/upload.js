/**
 * 客小二 C 端图片上传。
 *
 * 走后端标准 app 文件上传：POST /app-api/infra/file/upload（@PermitAll，multipart）
 * 返回 CommonResult<String>，data 即公网可访问的文件 URL。
 * 线下转账付款凭证就用这条链路上传。
 */

const USER_STORE_STORAGE_KEY = 'kexiaoer-user-store-v1';

// #ifdef MP-WEIXIN
const API_BASE = 'https://ke.doupaidoudian.com';
// #endif
// #ifndef MP-WEIXIN
const API_BASE = '';
// #endif

function readToken() {
  try {
    if (typeof localStorage !== 'undefined') {
      const raw = localStorage.getItem(USER_STORE_STORAGE_KEY);
      if (raw) {
        const obj = JSON.parse(raw);
        if (obj && typeof obj.token === 'string' && obj.token) return obj.token;
      }
    }
  } catch {}
  try { return uni.getStorageSync('token') || ''; } catch { return ''; }
}

/**
 * 选一张图并上传，返回公网 URL。
 * @returns {Promise<string>} 文件 URL
 */
export function chooseAndUploadImage() {
  return new Promise((resolve, reject) => {
    uni.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: (r) => {
        const filePath = r.tempFilePaths && r.tempFilePaths[0];
        if (!filePath) { reject(new Error('未选择图片')); return; }
        uploadImage(filePath).then(resolve).catch(reject);
      },
      fail: (e) => reject(e),
    });
  });
}

/**
 * 上传本地临时文件，返回公网 URL。
 * @param {string} filePath uni.chooseImage 返回的临时路径
 * @returns {Promise<string>}
 */
export function uploadImage(filePath) {
  const token = readToken();
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: API_BASE + '/app-api/infra/file/upload',
      filePath,
      name: 'file',
      formData: { directory: 'offline-pay' },
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: (res) => {
        if (res.statusCode < 200 || res.statusCode >= 300) {
          reject(new Error(`上传失败 ${res.statusCode}`));
          return;
        }
        let body = res.data;
        try { body = typeof body === 'string' ? JSON.parse(body) : body; } catch {}
        if (body && typeof body === 'object' && 'code' in body) {
          if (body.code === 0) resolve(body.data);
          else reject(new Error(body.msg || '上传失败'));
        } else if (typeof body === 'string' && /^https?:\/\//.test(body)) {
          resolve(body);
        } else {
          reject(new Error('上传返回异常'));
        }
      },
      fail: (e) => reject(e),
    });
  });
}
