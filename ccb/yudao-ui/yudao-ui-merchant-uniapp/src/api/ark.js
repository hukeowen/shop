/**
 * 火山方舟 API（豆包 LLM + Seedance 图生视频）
 * 通过 Vite dev proxy /ark/* → https://ark.cn-beijing.volces.com
 *
 * ⚠️ ARK_API_KEY 在前端可见，仅 dev / 内部体验用。
 */

const ARK_KEY = import.meta.env.VITE_ARK_API_KEY;
const LLM_MODEL = import.meta.env.VITE_ARK_LLM_MODEL || 'doubao-1-5-pro-32k-250115';
const VIDEO_MODEL = import.meta.env.VITE_ARK_VIDEO_MODEL || 'doubao-seedance-1-0-pro-250528';

const RESOLUTION = import.meta.env.VITE_VIDEO_RESOLUTION || '720p';
const DURATION = import.meta.env.VITE_VIDEO_DURATION || '5';
const RATIO = import.meta.env.VITE_VIDEO_RATIO || '9:16';

function authHeaders() {
  if (!ARK_KEY || ARK_KEY.startsWith('sk-xxxx')) {
    throw new Error('未配置 VITE_ARK_API_KEY，请编辑 .env.local 后重启 dev server');
  }
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${ARK_KEY}`,
  };
}

async function arkFetch(path, options) {
  const res = await fetch('/ark' + path, options);
  const text = await res.text();
  let body;
  try {
    body = JSON.parse(text);
  } catch {
    throw new Error(`方舟接口非 JSON 响应：${text.slice(0, 200)}`);
  }
  if (!res.ok || body.error) {
    const msg = body?.error?.message || body?.message || `HTTP ${res.status}`;
    throw new Error(msg);
  }
  return body;
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

  const body = await arkFetch('/api/v3/chat/completions', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({
      model: LLM_MODEL,
      temperature: 0.85,
      messages: [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: userPrompt },
      ],
    }),
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
 * @returns string task_id
 */
export async function createVideoTask({ copywriting, imageUrl }) {
  if (!imageUrl?.startsWith('http')) {
    throw new Error('图片必须是 http(s) 公网 URL（Seedance 不支持 base64 / blob）');
  }
  const text = `${copywriting.join('，')} --rs ${RESOLUTION} --dur ${DURATION} --rt ${RATIO}`;
  const body = await arkFetch('/api/v3/contents/generations/tasks', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({
      model: VIDEO_MODEL,
      content: [
        { type: 'text', text },
        { type: 'image_url', image_url: { url: imageUrl } },
      ],
    }),
  });
  if (!body.id) throw new Error('创建视频任务失败：' + JSON.stringify(body).slice(0, 200));
  return body.id;
}

/**
 * 查询 Seedance 任务状态
 * @returns { status: 'queued'|'running'|'succeeded'|'failed', videoUrl?, error? }
 */
export async function queryVideoTask(taskId) {
  const body = await arkFetch(`/api/v3/contents/generations/tasks/${taskId}`, {
    method: 'GET',
    headers: authHeaders(),
  });
  return {
    status: body.status,
    videoUrl: body.content?.video_url || null,
    error: body.error?.message || null,
    raw: body,
  };
}

/**
 * 阻塞式轮询直到 succeeded / failed
 * @param onProgress 每次轮询回调（可用于更新进度条）
 */
export async function waitForVideo(taskId, { onProgress, intervalMs = 5000, maxMs = 600000 } = {}) {
  const started = Date.now();
  let attempt = 0;
  while (Date.now() - started < maxMs) {
    attempt += 1;
    const r = await queryVideoTask(taskId);
    onProgress?.({ attempt, status: r.status, elapsedMs: Date.now() - started });
    if (r.status === 'succeeded' && r.videoUrl) return r.videoUrl;
    if (r.status === 'failed') throw new Error(r.error || 'Seedance 任务失败');
    await new Promise((res) => setTimeout(res, intervalMs));
  }
  throw new Error(`视频生成超时（>${maxMs / 1000}s），任务 id=${taskId}`);
}
