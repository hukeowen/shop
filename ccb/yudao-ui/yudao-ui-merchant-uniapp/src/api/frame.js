/**
 * 视频末帧提取（client-side canvas 版）
 *
 * 火山 tos 视频直接取帧会 CORS 报错，因此通过 Vite 侧车的 /vproxy 转一层拿到同源响应。
 * 用途：把 Seedance 上一段视频的最后一帧作为下一段的起始帧，实现 3×10s 无缝衔接。
 */

import { uploadImage } from './oss.js';

export function extractLastFrameBase64(videoUrl) {
  return new Promise((resolve, reject) => {
    if (typeof document === 'undefined') {
      return reject(new Error('extractLastFrameBase64 仅支持 H5 环境'));
    }
    const proxied = '/vproxy?url=' + encodeURIComponent(videoUrl);
    const video = document.createElement('video');
    video.crossOrigin = 'anonymous';
    video.muted = true;
    video.playsInline = true;
    video.preload = 'auto';
    video.style.position = 'fixed';
    video.style.left = '-10000px';
    video.src = proxied;
    document.body.appendChild(video);

    let finished = false;
    const cleanup = () => {
      try { video.pause(); } catch {}
      video.removeAttribute('src');
      try { video.load(); } catch {}
      try { video.remove(); } catch {}
    };
    const bail = (msg) => {
      if (finished) return;
      finished = true;
      clearTimeout(timer);
      cleanup();
      reject(new Error(msg));
    };
    const done = (data) => {
      if (finished) return;
      finished = true;
      clearTimeout(timer);
      cleanup();
      resolve(data);
    };

    const timer = setTimeout(() => bail('视频帧提取超时 30s'), 30_000);

    video.addEventListener('loadedmetadata', () => {
      try {
        const dur = Number.isFinite(video.duration) ? video.duration : 0;
        video.currentTime = Math.max(0, dur - 0.1);
      } catch (e) {
        bail('seek 失败：' + e.message);
      }
    });

    video.addEventListener('seeked', () => {
      try {
        const w = video.videoWidth || 1080;
        const h = video.videoHeight || 1920;
        const canvas = document.createElement('canvas');
        canvas.width = w;
        canvas.height = h;
        const ctx = canvas.getContext('2d');
        ctx.drawImage(video, 0, 0, w, h);
        canvas.toBlob(
          (blob) => {
            if (!blob) return bail('canvas toBlob 返回空');
            const reader = new FileReader();
            reader.onload = () => {
              const raw = String(reader.result).replace(/^data:[^,]+,/, '');
              done(raw);
            };
            reader.onerror = () => bail('FileReader 读取失败');
            reader.readAsDataURL(blob);
          },
          'image/jpeg',
          0.92
        );
      } catch (e) {
        bail('drawImage 失败：' + e.message);
      }
    });

    video.addEventListener('error', () => {
      bail('视频加载失败（vproxy）');
    });
  });
}

/**
 * 取末帧并上传到 OSS，返回可喂给 Seedance 的公网 URL
 */
export async function lastFrameToOssUrl(videoUrl) {
  const base64 = await extractLastFrameBase64(videoUrl);
  const { url } = await uploadImage(base64, { ext: 'jpg' });
  return url;
}
