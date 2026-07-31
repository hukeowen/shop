/**
 * 图片上传到 OSS（simiyun s3 v2）
 *  - 前端把本地 blob/dataURL/文件路径 转 base64 POST 到 /oss/upload
 *  - Node 侧车走 aws-sdk v2 签名直传，返回 1 小时预签名 GET URL
 *  - Seedance 只认 http(s) 公网地址，必须先过这一步
 *
 * ⚠ 三端差异（原生 App / 小程序踩过的坑）：
 *  1. 网络：裸 fetch('/oss/upload') 只有 H5 同源代理能用。App / 小程序没有同源，
 *     必须走 request()（内部 uni.request + resolveUrl 拼绝对域名）。
 *  2. 本地图片读取：uni.chooseImage 在 App 返回的是 `file:///…` / `_doc/…` 原生路径，
 *     webview 的 fetch() 读不到（拿不到 blob，或被 file 域 CORS 拦），
 *     必须用 uni.getFileSystemManager().readFile 读原生文件。
 */

import { request } from './request.js';

/** App/小程序：原生文件路径 → 纯 base64（webview fetch 读不了本地文件） */
function readLocalFileAsBase64(filePath) {
  return new Promise((resolve, reject) => {
    try {
      uni.getFileSystemManager().readFile({
        filePath,
        encoding: 'base64',
        success: (r) => resolve(r.data),
        fail: (e) => reject(new Error('读取本地图片失败：' + (e && e.errMsg ? e.errMsg : ''))),
      });
    } catch (e) {
      reject(e);
    }
  });
}

/** blob URL / dataURL / 本地文件路径 → 纯 base64（不含 data: 前缀） */
export async function blobUrlToBase64(url) {
  if (!url) throw new Error('blobUrlToBase64: url 为空');
  // dataURL 直接切前缀，三端通用
  if (/^data:/.test(url)) return String(url).replace(/^data:[^,]+,/, '');
  // #ifdef APP-PLUS || MP-WEIXIN
  return await readLocalFileAsBase64(url);
  // #endif
  // eslint-disable-next-line no-unreachable
  const blob = await fetch(url).then((r) => r.blob());
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result).replace(/^data:[^,]+,/, ''));
    reader.onerror = reject;
    reader.readAsDataURL(blob);
  });
}

/**
 * 上传一张图（base64）
 *
 * @param {string} base64
 * @param {object} [opts]
 * @param {string} [opts.ext='jpg']
 * @param {'public-read'|'private'} [opts.acl='public-read']
 *   - public-read：店铺封面/视频背景图等公开数据，返永久 URL
 *   - private：KYC 证件、身份证等敏感数据，返 1h 预签名 URL（要长期访问得调 signOss(key) 重签）
 * @param {string} [opts.prefix='tanxiaoer']
 * @returns {Promise<{ url: string, key: string, acl: string }>}
 */
export async function uploadImage(base64, { ext = 'jpg', acl = 'public-read', prefix = 'tanxiaoer' } = {}) {
  // 侧车返 { ok, url, key, acl }（非 CommonResult），request() 对无 code 字段的 body 原样返回
  const body = await request({
    url: '/oss/upload',
    method: 'POST',
    data: { base64, ext, acl, prefix },
  });
  if (!body || !body.ok) throw new Error('OSS 上传失败：' + ((body && body.error) || '未知错误'));
  return { url: body.url, key: body.key, acl: body.acl };
}

/** 批量上传 (默认 public-read，返 url 数组以兼容旧调用方) */
export async function uploadImages(base64List) {
  const results = await Promise.all(base64List.map((b) => uploadImage(b)));
  return results.map((r) => r.url);
}

/**
 * 给一个已存在的 TOS key 签发临时 GET URL（私有对象访问必经此路）
 * @param {string} key
 * @param {number} [ttl=3600]  秒，默认 1h，最长 1 天
 * @returns {Promise<string>}  预签名 URL
 */
export async function signOss(key, ttl = 3600) {
  if (!key) throw new Error('key 为空');
  const body = await request({
    url: `/oss/sign?key=${encodeURIComponent(key)}&ttl=${ttl}`,
  });
  if (!body || !body.ok) throw new Error('OSS 签发失败：' + ((body && body.error) || '未知错误'));
  return body.url;
}
