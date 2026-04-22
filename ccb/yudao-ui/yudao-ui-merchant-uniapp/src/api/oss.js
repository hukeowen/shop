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

/** 上传一张图（base64）→ 预签名公网 URL */
export async function uploadImage(base64, { ext = 'jpg' } = {}) {
  const res = await fetch('/oss/upload', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ base64, ext }),
  });
  const body = await res.json();
  if (!body.ok) throw new Error('OSS 上传失败：' + (body.error || res.status));
  return body.url;
}

/** 批量上传 */
export async function uploadImages(base64List) {
  return Promise.all(base64List.map((b) => uploadImage(b)));
}
