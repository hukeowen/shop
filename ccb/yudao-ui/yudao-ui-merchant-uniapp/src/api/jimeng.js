/**
 * 即梦AI 图生视频 3.0 720P（首帧）
 *
 * Phase 0.2 起：签名 + 调用通过后端 BFF 完成，浏览器不再持有 JIMENG_AK/SK。
 *   · 提交：POST /app-api/merchant/mini/ai-video/bff/jimeng/submit { imageUrl, prompt, frames, seed }
 *   · 查询：POST /app-api/merchant/mini/ai-video/bff/jimeng/query  { taskId }
 * 后端原样回包（保留 code=10000 / data.{task_id,status,video_url} 形态）。
 */

import { request } from './request.js';

const BFF_SUBMIT = '/app-api/merchant/mini/ai-video/bff/jimeng/submit';
const BFF_QUERY = '/app-api/merchant/mini/ai-video/bff/jimeng/query';

async function jimengSubmit({ imageUrl, prompt, frames, seed = -1 }) {
  const data = await request({
    url: BFF_SUBMIT,
    method: 'POST',
    data: { imageUrl, prompt, frames, seed },
  });
  if (data?.code !== 10000) {
    throw new Error(data?.message || `即梦AI code=${data?.code}`);
  }
  return data;
}

async function jimengQuery({ taskId }) {
  const data = await request({
    url: BFF_QUERY,
    method: 'POST',
    data: { taskId },
  });
  if (data?.code !== 10000) {
    throw new Error(data?.message || `即梦AI code=${data?.code}`);
  }
  return data;
}

export async function createClipTask({ imageUrl, prompt, duration }) {
  if (!imageUrl?.startsWith('http')) throw new Error('imageUrl 必须是 http(s) 公网地址');
  const frames = Number(duration) >= 10 ? 241 : 121;
  // 风控降级序列：原始 prompt → 精简版 → 空 prompt
  const promptCandidates = [prompt || '', sanitizePrompt(prompt), ''];
  let lastErr;
  for (let pi = 0; pi < promptCandidates.length; pi++) {
    const currentPrompt = promptCandidates[pi];
    for (let attempt = 0; attempt < 6; attempt++) {
      try {
        const data = await jimengSubmit({
          imageUrl,
          prompt: currentPrompt,
          frames,
          seed: -1,
        });
        const taskId = data?.data?.task_id;
        if (!taskId) throw new Error('即梦AI 未返回 task_id: ' + JSON.stringify(data).slice(0, 200));
        if (pi > 0) console.warn(`[jimeng] 风控降级第 ${pi} 级成功，prompt 已精简`);
        return taskId;
      } catch (e) {
        lastErr = e;
        if (/concurrent limit|Concurrent Limit|限流|rate limit/i.test(e.message)) {
          const wait = (attempt + 1) * 10000;
          console.warn(`[jimeng] 并发限制，${wait / 1000}s 后重试 (${attempt + 1}/6)`);
          await new Promise((r) => setTimeout(r, wait));
          continue;
        }
        if (/Text Risk|text risk|内容审核|content.*risk/i.test(e.message)) {
          console.warn(`[jimeng] 风控拦截 (prompt 第 ${pi + 1} 版)，尝试下一级`);
          break; // 换下一个 prompt 候选
        }
        throw e;
      }
    }
  }
  throw lastErr;
}

/** 去掉可能触发风控的词，只保留动作/镜头/光效关键词 */
function sanitizePrompt(prompt) {
  if (!prompt) return '';
  return prompt
    .replace(/\b(sizzling|dripping|fat|flesh|bloody|raw meat|slaughter)\b/gi, '')
    .replace(/\b(sexy|seductive|intimate|revealing)\b/gi, '')
    .replace(/\s{2,}/g, ' ')
    .trim()
    .slice(0, 200);
}

export async function queryClipTask(taskId) {
  const data = await jimengQuery({ taskId });
  const d = data?.data || {};
  const status = d.status;
  if (status === 'done') {
    const videoUrl = d.video_url || null;
    return { status: videoUrl ? 'succeeded' : 'failed', videoUrl, error: videoUrl ? null : '生成失败，video_url 为空' };
  }
  // not_found 可能是刚提交、火山侧还没写入 —— 留给调用方用 sinceSubmitMs 判断是否容忍
  if (status === 'not_found' || status === 'expired') {
    return { status: 'failed', videoUrl: null, error: `task ${status}`, transient: status === 'not_found' };
  }
  return { status: 'running', videoUrl: null, error: null };
}

export async function waitClip(taskId, { intervalMs = 5000, maxMs = 600000, onTick } = {}) {
  const started = Date.now();
  let consecutiveErrors = 0;
  const MAX_ERRORS = 6;              // 连续 6 次查询失败才真的放弃（约 30s 网络故障容忍）
  const NOT_FOUND_GRACE_MS = 60_000; // 刚提交前 60s 内 not_found 视为还没落库
  while (Date.now() - started < maxMs) {
    try {
      const r = await queryClipTask(taskId);
      onTick?.(r);
      if (r.status === 'succeeded' && r.videoUrl) return r.videoUrl;
      if (r.status === 'failed') {
        if (r.transient && Date.now() - started < NOT_FOUND_GRACE_MS) {
          // 刚提交就 not_found，给服务端一点落库时间
          consecutiveErrors = 0;
        } else {
          throw new Error(r.error || '即梦AI 任务失败');
        }
      } else {
        consecutiveErrors = 0;
      }
    } catch (e) {
      // waitClip 本身的瞬时错误（网络抖动 / sidecar 重启 / TLS reset）不应直接崩掉
      // 只在连续 MAX_ERRORS 次查询都失败时才真的放弃，其间任务其实还在火山侧运行
      if (/即梦AI 任务失败/.test(e.message)) throw e; // 服务端明确判失败，不容忍
      consecutiveErrors++;
      console.warn(`[jimeng] waitClip 查询失败 (${consecutiveErrors}/${MAX_ERRORS}): ${e.message}`);
      if (consecutiveErrors >= MAX_ERRORS) {
        throw new Error(`即梦AI 连续 ${MAX_ERRORS} 次查询失败：${e.message}`);
      }
    }
    await new Promise((res) => setTimeout(res, intervalMs));
  }
  throw new Error(`即梦AI 超时，task=${taskId}`);
}
