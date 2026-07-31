/**
 * bbox 裁切工具（客户端 canvas 版）
 *
 * - 输入：原图 URL + 归一化 bbox [x1,y1,x2,y2]
 *   ⚠ 原生 App 必须传 **dataURL**（`data:image/jpeg;base64,…`）而不是 `file:///` 本地路径：
 *     file:// 页面加载 file:// 图片会「污染」canvas，随后 toDataURL/toBlob 抛 SecurityError。
 *     dataURL 视为同源，不污染，H5 下也照常工作——所以调用方统一传 dataURL。
 * - 输出：裁切后的 dataURL（可直接当 <image src>）+ 纯 base64（方便上传 OSS）
 * - 会自动 padding 一圈，并居中贴到方形白底（符合商品封面 1:1 审美）
 */

export function cropByBbox(imageUrl, bbox, { padding = 0.08, maxSize = 1024 } = {}) {
  return new Promise((resolve, reject) => {
    if (typeof document === 'undefined') {
      return reject(new Error('cropByBbox 仅支持 H5 环境'));
    }
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      try {
        const W = img.naturalWidth;
        const H = img.naturalHeight;
        let [x1, y1, x2, y2] = bbox;
        // 归一化已经在 productDetect 里做过，这里再兜底
        const w = x2 - x1;
        const h = y2 - y1;
        x1 = Math.max(0, x1 - w * padding);
        y1 = Math.max(0, y1 - h * padding);
        x2 = Math.min(1, x2 + w * padding);
        y2 = Math.min(1, y2 + h * padding);
        const sx = Math.round(x1 * W);
        const sy = Math.round(y1 * H);
        const sw = Math.max(1, Math.round((x2 - x1) * W));
        const sh = Math.max(1, Math.round((y2 - y1) * H));
        // 居中贴到方形
        const side = Math.max(sw, sh);
        const scale = side > maxSize ? maxSize / side : 1;
        const outSide = Math.round(side * scale);
        const canvas = document.createElement('canvas');
        canvas.width = outSide;
        canvas.height = outSide;
        const ctx = canvas.getContext('2d');
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, outSide, outSide);
        const drawW = sw * scale;
        const drawH = sh * scale;
        const dx = Math.round((outSide - drawW) / 2);
        const dy = Math.round((outSide - drawH) / 2);
        ctx.drawImage(img, sx, sy, sw, sh, dx, dy, drawW, drawH);
        // 用 toDataURL 而不是 toBlob + URL.createObjectURL：
        // 原生 App 的 <image> 对 blob: 协议支持不稳，dataURL 三端都能直接渲染，
        // 而且顺手就是要上传的 base64，省一次 FileReader。
        const dataUrl = canvas.toDataURL('image/jpeg', 0.9);
        if (!dataUrl || dataUrl.length < 32) return reject(new Error('canvas 导出失败'));
        resolve({ previewUrl: dataUrl, base64: dataUrl.replace(/^data:[^,]+,/, '') });
      } catch (e) {
        reject(e);
      }
    };
    img.onerror = () => reject(new Error('原图加载失败：' + imageUrl));
    img.src = imageUrl;
  });
}
