/**
 * 豆包 LLM · 30 秒短视频脚本生成器
 *
 * 输入: 图片数 + 一句话亮点
 * 输出: 3 幕 JSON（每幕 10s，共 30s，段与段用末帧无缝衔接）
 *   [{ img_idx, narration, visual_prompt }]
 *     - img_idx: 这幕用第几张图（只有第 1 幕用，后两幕会用上一段的末帧衔接）
 *     - narration: 这幕的口播台词（16-36 字，TTS 用，配合 10s 节奏）
 *     - visual_prompt: 给 Seedance 的画面提示（带运镜/氛围）
 */

const ARK_KEY = import.meta.env.VITE_ARK_API_KEY;
const TEXT_MODEL = import.meta.env.VITE_ARK_LLM_MODEL || 'doubao-1-5-pro-32k-250115';
const VISION_MODEL = import.meta.env.VITE_ARK_VISION_MODEL || 'doubao-1-5-vision-pro-32k-250115';
const DURATION = Number(import.meta.env.VITE_VIDEO_DURATION || 10);
const DEFAULT_SCENES = Number(import.meta.env.VITE_VIDEO_SCENES || 3);
const MAX_SCENES = 6;

function authHeaders() {
  if (!ARK_KEY) throw new Error('未配置 VITE_ARK_API_KEY');
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${ARK_KEY}`,
  };
}

async function arkChat(model, messages) {
  const res = await fetch('/ark/api/v3/chat/completions', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({
      model,
      temperature: 0.85,
      messages,
    }),
  });
  const text = await res.text();
  let body;
  try {
    body = JSON.parse(text);
  } catch {
    throw new Error(`LLM 非 JSON: ${text.slice(0, 200)}`);
  }
  if (!res.ok || body.error) {
    throw new Error(body?.error?.message || `HTTP ${res.status}`);
  }
  return body?.choices?.[0]?.message?.content || '';
}

/**
 * 生成视频脚本 —— 三步走：
 *   Step 1: 视觉分析（视觉模型逐图分析元素 + 故事适配角色）
 *   Step 2: 导演策划（文本模型根据分析结果，打乱顺序，规划最佳叙事）
 *   Step 3: 写脚本（img_idx 按导演策划的顺序，不按上传顺序）
 */
export async function generateScript({ imageCount, imageUrls, userDescription, sceneCount, sceneDuration }) {
  const n = Math.max(1, Math.min(MAX_SCENES, imageCount));
  const SCENES = Math.max(1, Math.min(MAX_SCENES, sceneCount || n || DEFAULT_SCENES));
  const D = Number(sceneDuration) || DURATION;
  const totalSec = SCENES * D;
  const urls = Array.isArray(imageUrls) ? imageUrls.slice(0, n).filter(Boolean) : [];

  if (!urls.length) {
    // 无图退回纯文本生成
    return generateScriptTextOnly({ userDescription, SCENES, D, totalSec, n });
  }

  // ── Step 1：视觉分析 ───────────────────────────────────────────────
  const analysisPrompt = [
    `你是顶级商业广告导演的视觉助理。我上传了 ${n} 张商品实拍图（编号 0 到 ${n-1}）。`,
    '请逐张仔细分析，输出 JSON 数组（不要任何解释）：',
    JSON.stringify(
      Array.from({ length: n }, (_, i) => ({
        idx: i,
        elements: '画面中的核心元素（食材/颜色/质感/状态/场景氛围，5-8个词）',
        sensory: '这张图能触发的感官体验（香气/热度/声音/口感/视觉冲击）',
        emotion: '情绪标签：诱惑感/新鲜感/满足感/仪式感/爽感/期待感',
        best_role: '最适合的叙事角色：opening_hook（开场抓眼）/ process（过程/变化）/ money_shot（高潮特写）/ lifestyle（场景融合）/ cta（行动号召）',
      })),
      null, 2
    ),
  ].join('\n');

  const analysisContent = [
    { type: 'text', text: analysisPrompt },
    ...urls.map((url) => ({ type: 'image_url', image_url: { url, detail: 'high' } })),
  ];

  const analysisRaw = await arkChat(VISION_MODEL, [
    { role: 'system', content: '你是专业商业广告视觉分析师，只输出 JSON，不加任何说明文字。' },
    { role: 'user', content: analysisContent },
  ]);

  let imageAnalysis = [];
  try {
    const m = analysisRaw.match(/\[[\s\S]*\]/);
    imageAnalysis = JSON.parse(m ? m[0] : analysisRaw);
  } catch {
    // 解析失败降级：默认顺序
    imageAnalysis = urls.map((_, i) => ({ idx: i, elements: '', sensory: '', emotion: '', best_role: 'process' }));
  }

  // ── Step 2 + 3：导演策划 + 写脚本 ─────────────────────────────────
  const motionRule = `
visual_prompt 技术要求（每条必须满足，否则视频是死图）：
· 【物体运动】必须有真实物理动作：rotating / pouring / sizzling on grill / smoke rising / liquid dripping / rippling / sparkling / falling 等
· 【镜头运动】选一种：slow push-in / pull-back / orbiting low angle / rising tilt / handheld follow / extreme close-up reveal
· 【光效】选一种：warm ember glow / dramatic backlight with smoke / golden rim lighting / tyndall beam through steam / macro bokeh
· 【语言】必须英文
· 示例（烧烤）：Marinated beef slowly rotating on charcoal grill, fat dripping and sizzling, camera pushing in low angle through rising smoke, warm amber backlight, extreme close-up of char marks forming in slow motion
`.trim();

  const structureHint =
    SCENES <= 2 ? '① 强钩子冲击 ② 高潮+行动号召' :
    SCENES === 3 ? '① 开场钩子（停住手指）② 过程/细节/欲望（让人心动）③ 成品高潮+行动号召（让人下单）' :
    `① 开场钩子 ② 变化过程 ${SCENES > 4 ? '③④ 多角度欲望激发' : '③ 细节高潮'} ④ 情感共鸣 ⑤ 行动号召（共 ${SCENES} 幕，每幕递进）`;

  const directorPrompt = [
    `你是拍过百亿播放量抖音广告的导演。商家亮点：${userDescription}`,
    '',
    '我已经分析了每张图的特质：',
    JSON.stringify(imageAnalysis, null, 2),
    '',
    `任务：规划一支 ${totalSec} 秒（${SCENES} 幕，每幕 ${D}s）的种草广告片，必须：`,
    '1. 【打乱上传顺序】根据每张图的 best_role，重新安排哪张图放哪幕——不要按 idx 顺序！要按最强叙事冲击力排',
    '2. 每幕的 narration 像一整段文案被切开，有承接/递进/转折，读出来是一个完整故事',
    '3. 想象图里没有拍到的：烟雾/声音/香气/温度变化/食材到成品的转变过程',
    '4. 台词要有感官冲击和欲望感，不要念参数',
    '',
    `故事结构参考：${structureHint}`,
    '',
    motionRule,
    '',
    `⚠️ img_idx 填你策划的那张图的编号（0-${n-1}），允许和幕序号不同。${n} 张图每张都要用到。`,
    '严格只输出 JSON，不要解释：',
    JSON.stringify({
      title: '视频标题（8-16字，有钩子，适合抖音）',
      scenes: Array.from({ length: SCENES }, (_, i) => ({
        img_idx: `你策划的图编号（0-${n-1}，按最强叙事顺序，不是上传顺序）`,
        narration: `第${i+1}句台词（${D >= 10 ? '16-36' : '8-18'}字，与上下句有承接，有感官画面感）`,
        visual_prompt: '【英文】subject motion + camera movement + lighting + cinematic details',
      })),
    }, null, 2),
  ].join('\n');

  const raw = await arkChat(TEXT_MODEL, [
    { role: 'system', content: '你是顶级抖音商业广告导演，只输出 JSON，不加说明文字。' },
    { role: 'user', content: directorPrompt },
  ]);

  return parseScript(raw, n, SCENES, false);
}

/**
 * 上传图片后自动生成"一句话亮点"，供用户确认/修改
 * @param {string[]} imageBase64s 纯 base64（不含 data: 前缀）
 */
export async function generateHighlight(imageBase64s) {
  const imgs = (imageBase64s || []).slice(0, 3).filter(Boolean);
  if (!imgs.length) throw new Error('无图片');
  const imageContent = imgs.map((b64) => ({
    type: 'image_url',
    image_url: {
      url: b64.startsWith('data:') ? b64 : `data:image/jpeg;base64,${b64}`,
      detail: 'low',
    },
  }));
  const raw = await arkChat(VISION_MODEL, [
    {
      role: 'system',
      content: '你是抖音带货文案专家。看图后用一句话（15-30字）写出最吸引人的核心卖点，要口语化、有欲望感、能让人停住刷屏的手指。只输出那句话，不要引号、不要解释、不要标点以外的任何字符。',
    },
    {
      role: 'user',
      content: [
        { type: 'text', text: '这是商品实拍图，帮我提炼一句话亮点文案：' },
        ...imageContent,
      ],
    },
  ]);
  return raw.trim().replace(/^["""''「」【】]|["""''「」【】]$/g, '').trim();
}

async function generateScriptTextOnly({ userDescription, SCENES, D, totalSec, n }) {
  const raw = await arkChat(TEXT_MODEL, [
    { role: 'system', content: `你是拍过百亿播放量抖音广告的导演级脚本策划。写完整的种草商业片脚本，台词有感官冲击，visual_prompt 必须英文且有真实物体运动，故事有钩子→欲望→高潮→行动号召弧线。只输出 JSON。` },
    { role: 'user', content: `商家亮点：${userDescription}\n共 ${n} 张图，输出 ${SCENES} 幕脚本（每幕 ${D}s，共 ${totalSec}s）。\n` + JSON.stringify({
      title: '视频标题',
      scenes: Array.from({ length: SCENES }, (_, i) => ({
        img_idx: i % n,
        narration: `第${i+1}句台词（${D >= 10 ? '16-36' : '8-18'}字）`,
        visual_prompt: '【英文】motion + camera + lighting',
      })),
    }, null, 2) },
  ]);
  return parseScript(raw, n, SCENES, false);
}

function parseScript(raw, imageCount, sceneCount) {
  let txt = raw.trim().replace(/^```(?:json)?\s*/i, '').replace(/```\s*$/i, '');
  const m = txt.match(/\{[\s\S]*\}/);
  if (m) txt = m[0];
  let obj;
  try {
    obj = JSON.parse(txt);
  } catch (e) {
    throw new Error('LLM 脚本解析失败：' + raw.slice(0, 150));
  }
  const scenes = Array.isArray(obj.scenes) ? obj.scenes : [];
  if (!scenes.length) throw new Error('LLM 未返回 scenes');

  // 收集 LLM 选的 img_idx，若有重复或越界则自动修正确保每张图都用上
  const usedIdx = new Set();
  scenes.forEach((s, i) => {
    let idx = Math.max(0, Math.min(imageCount - 1, Number(s.img_idx) || 0));
    // 若该 idx 已被用过，找一个未用的
    if (usedIdx.has(idx) && usedIdx.size < imageCount) {
      for (let j = 0; j < imageCount; j++) {
        if (!usedIdx.has(j)) { idx = j; break; }
      }
    }
    usedIdx.add(idx);
    s.img_idx = idx;
    s.narration = String(s.narration || '').trim();
    s.visual_prompt = String(s.visual_prompt || s.narration).trim();
    s.image_summary = String(s.image_summary || '').trim();
  });
  return {
    title: String(obj.title || '').trim() || '新品推荐',
    scenes: scenes.slice(0, sceneCount),
  };
}
