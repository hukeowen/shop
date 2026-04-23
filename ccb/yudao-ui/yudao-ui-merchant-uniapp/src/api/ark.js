/**
 * 火山方舟 API 封装（豆包 LLM + Seedance 图生视频）
 *
 * 所有请求经后端 BFF：POST /app-api/merchant/mini/ai-video/bff/ark/chat
 * 浏览器端不再持有 ARK_API_KEY，JWT 由 request.js 统一注入。
 *
 * 模型名称 / 视频参数仍可通过非敏感的 VITE_* 覆盖。
 */

import { request } from './request.js';

const LLM_MODEL = import.meta.env.VITE_ARK_LLM_MODEL || 'doubao-1-5-pro-32k-250115';
const VIDEO_MODEL = import.meta.env.VITE_ARK_VIDEO_MODEL || 'doubao-seedance-1-0-pro-250528';

const RESOLUTION = import.meta.env.VITE_VIDEO_RESOLUTION || '720p';
const DURATION = import.meta.env.VITE_VIDEO_DURATION || '5';
const RATIO = import.meta.env.VITE_VIDEO_RATIO || '9:16';

const BFF_CHAT = '/app-api/merchant/mini/ai-video/bff/ark/chat';

/** BFF 透传 Ark chat completion，body 即 Ark 原生 JSON */
async function arkChat(body) {
  const resp = await request({ url: BFF_CHAT, method: 'POST', data: body });
  if (!resp || resp.error) {
    const msg = resp?.error?.message || resp?.message || 'Ark 返回错误';
    throw new Error(msg);
  }
  return resp;
}

/**
 * 调用豆包 LLM 生成抖音口播文案
 * @returns string[] 4 句文案
 */
export async function generateCopywriting({ imageUrls, userDescription }) {
  const systemPrompt =
    '你是抖音爆款视频文案专家，擅长用 4 句话写出有节奏、口语化、能让人停下来看完的短视频脚本。' +
    '禁止使用书面语、长句、形容词堆砌。每句 8-15 字，朗朗上口。';

  const userPrompt =
    `商家描述：${userDescription || '（未填写）'}\n` +
    `参考图片：${imageUrls.join(' | ')}\n\n` +
    '请生成恰好 4 句口播文案，句句独立，能直接读出。\n' +
    '严格用以下 JSON 格式返回，不要任何解释、不要 ```：\n' +
    '{"lines": ["第一句", "第二句", "第三句", "第四句"]}';

  const body = await arkChat({
    model: LLM_MODEL,
    temperature: 0.85,
    messages: [
      { role: 'system', content: systemPrompt },
      { role: 'user', content: userPrompt },
    ],
  });

  const raw = body?.choices?.[0]?.message?.content || '';
  return parseLines(raw);
}

function parseLines(raw) {
  let txt = raw.trim();
  // 去掉 ``` 包裹
  txt = txt.replace(/^```(?:json)?\s*/i, '').replace(/```\s*$/i, '');
  try {
    const obj = JSON.parse(txt);
    if (Array.isArray(obj?.lines) && obj.lines.length) return obj.lines.slice(0, 6);
  } catch {
    // 降级：按行切
  }
  const lines = txt
    .split(/[\n\r]+/)
    .map((l) => l.replace(/^[\d.、\-•"\s]+/, '').replace(/[",]+$/, '').trim())
    .filter((l) => l && l.length <= 30);
  if (lines.length) return lines.slice(0, 6);
  throw new Error('LLM 文案解析失败：' + raw.slice(0, 100));
}

/**
 * 创建 Seedance 图生视频任务
 *
 * ⚠️ 本函数走 Ark 原生 /contents/generations/tasks，需要走 BFF 新增端点；
 * 当前 Phase 0.2 只接通了 /ark/chat，调用方（aiVideo.js）早已改用即梦AI /jimeng/submit。
 * 保留导出仅为向后兼容：如果真的被调到会抛友好错误。
 */
export async function createVideoTask({ copywriting, imageUrl }) {
  throw new Error('createVideoTask 已下线：请走 /app-api/merchant/mini/ai-video/bff/jimeng/submit');
}

/** 同上：queryVideoTask 已下线 */
export async function queryVideoTask(taskId) {
  throw new Error('queryVideoTask 已下线：请走 /app-api/merchant/mini/ai-video/bff/jimeng/query');
}

/** 同上：waitForVideo 已下线 */
export async function waitForVideo(taskId, opts) {
  throw new Error('waitForVideo 已下线：请改用 jimeng.js 的 waitClip');
}

// 保留导出以防被意外解构（非敏感常量）
export { LLM_MODEL, VIDEO_MODEL, RESOLUTION, DURATION, RATIO };
