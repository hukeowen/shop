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

// 固定的收尾 CTA，必定出现在最后一幕的 narration
const FIXED_CTA = '截图微信扫描二维码在线下单';

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
    SCENES <= 2 ? `① 画面/好奇心钩子（用照片里最有画面感的细节让人停刷）② 固定收尾「${FIXED_CTA}」` :
    SCENES === 3 ? `① 画面钩子（照片里最抓眼的氛围/细节/悬念）② 感官与场景展开（香气/温度/质感/使用情境）③ 固定收尾「${FIXED_CTA}」` :
    `① 画面钩子 ② 感官与质感展开 ${SCENES > 4 ? '③④ 场景延伸 + 情绪递进' : '③ 细节特写高光'} ④ 生活化场景共鸣 ⑤ 固定收尾「${FIXED_CTA}」（共 ${SCENES} 幕，像一段完整故事）`;

  const directorPrompt = [
    `你是自家商品的店主，同时是个会讲故事的人。现在只看照片——充分发挥想象，帮这件商品写一支有画面感的抖音短片。`,
    `商品背景（你自己提供的一句话）：${userDescription}`,
    '',
    '我已经分析了每张图的特质：',
    JSON.stringify(imageAnalysis, null, 2),
    '',
    `任务：规划一支 ${totalSec} 秒（${SCENES} 幕，每幕 ${D}s）的抖音短片，必须做到：`,
    '1. 【从图出发、大胆想象】不要编参数、不要编不存在的配料/功能，但要把照片里没明说的东西想出来：烟雾/香气/温度/质感/场景（厨房/清晨/朋友聚会/下班路上…）/小情绪',
    '2. 【画面钩子】第 1 幕前 3 秒用照片里最有画面感的细节或悬念让人停刷——不要痛点喊话，不要反差促销，不要数字冲击',
    '3. 【打乱上传顺序】按 best_role 重新安排——不要按 idx 顺序，要按最强叙事冲击力排',
    '4. 【自然口语】像朋友聊天、分享好东西那样说，不要广告腔；多用感官词（香/烫/脆/绵/柔/清/润/暖）和场景词，不要念参数',
    `5. 【扫码收尾】最后一幕 narration 固定就是这 14 个字："${FIXED_CTA}"——其他幕不要提前出现扫码/二维码/下单引导，避免重复；严禁出现"老板""赔本""大减价""限时""秒杀""库存不多""小黄车""购物车""点击链接"等促销/违规词`,
    '6. 【每幕承接】narration 是一整段口播被切开，有承接/递进/转折，连起来是一个完整故事',
    '',
    `故事结构参考：${structureHint}`,
    '',
    motionRule,
    '',
    `⚠️ img_idx 填你策划的那张图的编号（0-${n-1}），允许和幕序号不同。${n} 张图每张都要用到。`,
    '严格只输出 JSON，不要解释：',
    JSON.stringify({
      title: '视频标题（8-16字，画面感/情绪感，不要吆喝不要促销词）',
      scenes: Array.from({ length: SCENES }, (_, i) => ({
        img_idx: `你策划的图编号（0-${n-1}，按最强叙事顺序，不是上传顺序）`,
        narration: `第${i+1}句台词（${D >= 10 ? '16-36' : '8-18'}字，自然口语，有承接和感官画面感）`,
        visual_prompt: '【英文】subject motion + camera movement + lighting + cinematic details',
      })),
    }, null, 2),
  ].join('\n');

  const raw = await arkChat(TEXT_MODEL, [
    { role: 'system', content: '你是会讲故事的店主+短视频编剧。基于图片发挥想象，写自然、有画面感的口播台词；禁止使用"老板""赔本""大减价""限时""秒杀""小黄车""购物车"等吆喝/违规词。只输出 JSON，不加说明文字。' },
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
      content: '你是会讲故事的店主。看图后凭画面发挥想象，用一句（15-30字）有画面感、有情绪的文案把照片里最动人的瞬间说出来——不要吆喝，不要"老板/赔本/大减价/限时/秒杀"等词，不要报参数。只输出那一句话，不要引号、不要解释、不要标点以外的任何字符。',
    },
    {
      role: 'user',
      content: [
        { type: 'text', text: '这是商品照片，请凭画面想象写一句有画面感的文案：' },
        ...imageContent,
      ],
    },
  ]);
  return raw.trim().replace(/^["""''「」【】]|["""''「」【】]$/g, '').trim();
}

async function generateScriptTextOnly({ userDescription, SCENES, D, totalSec, n }) {
  const raw = await arkChat(TEXT_MODEL, [
    { role: 'system', content: `你是会讲故事的店主+短视频编剧。narration 自然口语，基于画面发挥想象（画面/氛围/感官/场景/情绪），有钩子→展开→共鸣→扫码收尾的弧线；visual_prompt 必须英文且有真实物体运动。禁止使用"老板/赔本/大减价/限时/秒杀/小黄车/购物车"等吆喝/违规词。只输出 JSON。` },
    { role: 'user', content: `身份：你是这个商品的店主，正在用心分享一件自己喜欢的东西。\n商品背景：${userDescription}\n共 ${n} 张图，输出 ${SCENES} 幕脚本（每幕 ${D}s，共 ${totalSec}s）。\n第 1 幕要用画面感/好奇心钩子（不要痛点喊话、不要数字冲击）；最后一幕 narration 固定就是这 14 个字："${FIXED_CTA}"，前面几幕不要提前出现扫码/二维码/下单引导；全程不要说"老板/赔本/大减价/限时/秒杀/小黄车/购物车/点击链接"。\n` + JSON.stringify({
      title: '视频标题（8-16字，画面感/情绪感，不要吆喝不要促销词）',
      scenes: Array.from({ length: SCENES }, (_, i) => ({
        img_idx: i % n,
        narration: `第${i+1}句台词（${D >= 10 ? '16-36' : '8-18'}字，自然口语，基于画面）`,
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
  const out = scenes.slice(0, sceneCount);
  // 强制收尾：最后一幕 narration 固定为扫码引导文案，保证 TTS 和字幕都是这句
  if (out.length) {
    out[out.length - 1].narration = FIXED_CTA;
  }
  return {
    title: String(obj.title || '').trim() || '新品推荐',
    scenes: out,
  };
}
