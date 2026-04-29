/**
 * 图片上传到 OSS（simiyun s3 v2）
 *  - 前端把本地 blob/dataURL 转 base64 POST 到 /oss/upload
 *  - Node 侧车走 aws-sdk v2 签名直传，返回 1 小时预签名 GET URL
 *  - Seedance 只认 http(s) 公网地址，必须先过这一步
 */

/** blob URL 或 file path → 纯 base64 */
export async function blobUrlToBase64(url) {
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
  const res = await fetch('/oss/upload', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ base64, ext, acl, prefix }),
  });
  const body = await res.json();
  if (!body.ok) throw new Error('OSS 上传失败：' + (body.error || res.status));
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
  const url = `/oss/sign?key=${encodeURIComponent(key)}&ttl=${ttl}`;
  const res = await fetch(url);
  const body = await res.json();
  if (!body.ok) throw new Error('OSS 签发失败：' + (body.error || res.status));
  return body.url;
}
