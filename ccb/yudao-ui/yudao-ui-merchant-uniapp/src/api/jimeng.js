/**
 * 即梦AI 图生视频 3.0 720P（首帧）
 * 签名在 Vite sidecar（Node.js）完成，浏览器只发 JSON，避免 Secure Context 限制
 */

const REQ_KEY = 'jimeng_i2v_first_v30';

async function jimengPost(action, body) {
  const res = await fetch(`/jimeng?action=${action}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  let data;
  try { data = JSON.parse(text); } catch { throw new Error(`即梦AI 非 JSON：${text.slice(0, 200)}`); }
  if (data.code !== 10000) throw new Error(data.message || `即梦AI code=${data.code}`);
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
        const data = await jimengPost('CVSync2AsyncSubmitTask', {
          req_key: REQ_KEY,
          image_urls: [imageUrl],
          prompt: currentPrompt,
          seed: -1,
          frames,
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
  const data = await jimengPost('CVSync2AsyncGetResult', {
    req_key: REQ_KEY,
    task_id: taskId,
  });
  const d = data?.data || {};
  const status = d.status;
  if (status === 'done') {
    const videoUrl = d.video_url || null;
    return { status: videoUrl ? 'succeeded' : 'failed', videoUrl, error: videoUrl ? null : '生成失败，video_url 为空' };
  }
  if (status === 'not_found' || status === 'expired') {
    return { status: 'failed', videoUrl: null, error: `task ${status}` };
  }
  return { status: 'running', videoUrl: null, error: null };
}

export async function waitClip(taskId, { intervalMs = 5000, maxMs = 600000, onTick } = {}) {
  const started = Date.now();
  while (Date.now() - started < maxMs) {
    const r = await queryClipTask(taskId);
    onTick?.(r);
    if (r.status === 'succeeded' && r.videoUrl) return r.videoUrl;
    if (r.status === 'failed') throw new Error(r.error || '即梦AI 任务失败');
    await new Promise((res) => setTimeout(res, intervalMs));
  }
  throw new Error(`即梦AI 超时，task=${taskId}`);
}
