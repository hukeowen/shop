/**
 * 即梦 Seedance 1.5 Pro · 图生视频
 *   模型: doubao-seedance-1-5-pro-251215
 *   接口: POST /api/v3/contents/generations/tasks （content 数组形式）
 *
 * 一个 prompt + 一张图 = 一段 5s 视频。
 * 无水印：--watermark false
 */

import { request } from './request.js';

const MODEL = import.meta.env.VITE_ARK_VIDEO_MODEL || 'doubao-seedance-1-5-pro-251215';
const RESOLUTION = import.meta.env.VITE_VIDEO_RESOLUTION || '1080p';
const DEFAULT_DURATION = import.meta.env.VITE_VIDEO_DURATION || '10';
const RATIO = import.meta.env.VITE_VIDEO_RATIO || '9:16';

/**
 * ⚠️ Seedance content/generations/tasks 路径后端 BFF 暂未打通（Phase 0.2 只代理了 /ark/chat）。
 * 当前生产链路已切换到即梦AI（见 jimeng.js），这个文件仅作历史参考。
 * 这里把 arkFetch 改成抛错兜底，如被调用则能清晰提示。
 */
async function arkFetch(path) {
  throw new Error(`seedance ${path} 已下线：请使用 jimeng.js 的 createClipTask / waitClip`);
}

/**
 * 创建图生视频任务
 * @param {Object} p
 * @param {string} p.imageUrl   公网 http(s) URL（必须，base64 / blob 不行）
 * @param {string} p.prompt     场景 prompt（中文口语描述 + 镜头语言）
 * @param {string} [p.ratio]
 * @returns {Promise<string>}  task id
 */
export async function createClipTask({ imageUrl, prompt, ratio, duration }) {
  if (!imageUrl || !/^https?:\/\//.test(imageUrl)) {
    throw new Error('imageUrl 必须是 http(s) 公网地址');
  }
  const r = ratio || RATIO;
  const dur = duration || DEFAULT_DURATION;
  const text =
    `${prompt} ` +
    `--rs ${RESOLUTION} --dur ${dur} --rt ${r} ` +
    `--camerafixed false --watermark false`;
  const body = await arkFetch('/api/v3/contents/generations/tasks', {
    method: 'POST',
    body: JSON.stringify({
      model: MODEL,
      content: [
        { type: 'text', text },
        { type: 'image_url', image_url: { url: imageUrl } },
      ],
    }),
  });
  if (!body.id) throw new Error('Seedance 未返回 task id');
  return body.id;
}

/** 查询单个任务 */
export async function queryClipTask(taskId) {
  const body = await arkFetch(`/api/v3/contents/generations/tasks/${taskId}`, {
    method: 'GET',
  });
  return {
    status: body.status,
    videoUrl: body.content?.video_url || null,
    error: body.error?.message || null,
  };
}

/** 阻塞式轮询直到完成 */
export async function waitClip(taskId, { intervalMs = 5000, maxMs = 600000, onTick } = {}) {
  const started = Date.now();
  while (Date.now() - started < maxMs) {
    const r = await queryClipTask(taskId);
    onTick?.(r);
    if (r.status === 'succeeded' && r.videoUrl) return r.videoUrl;
    if (r.status === 'failed') throw new Error(r.error || 'Seedance 任务失败');
    await new Promise((res) => setTimeout(res, intervalMs));
  }
  throw new Error(`Seedance 超时，task=${taskId}`);
}
