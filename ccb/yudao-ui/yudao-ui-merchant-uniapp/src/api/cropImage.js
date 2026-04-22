/**
 * bbox 裁切工具（客户端 canvas 版）
 *
 * - 输入：原图 URL（blob / http / data 都行）+ 归一化 bbox [x1,y1,x2,y2]
 * - 输出：裁切后的 blob URL + base64（方便上传 OSS）
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
        canvas.toBlob(
          (blob) => {
            if (!blob) return reject(new Error('canvas.toBlob 返回空'));
            const previewUrl = URL.createObjectURL(blob);
            const reader = new FileReader();
            reader.onload = () => {
              const base64 = String(reader.result).replace(/^data:[^,]+,/, '');
              resolve({ previewUrl, base64 });
            };
            reader.onerror = () => reject(new Error('FileReader 读取失败'));
            reader.readAsDataURL(blob);
          },
          'image/jpeg',
          0.9
        );
      } catch (e) {
        reject(e);
      }
    };
    img.onerror = () => reject(new Error('原图加载失败：' + imageUrl));
    img.src = imageUrl;
  });
}
