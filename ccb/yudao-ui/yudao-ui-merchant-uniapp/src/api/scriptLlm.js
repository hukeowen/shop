/**
 * 视频脚本前端 thin wrapper（业务规则全部在后端 service 兜，前端只做调用 + 字段映射）
 *
 * 输入: 图片 OSS URL 列表 + 一句话亮点
 * 输出: { title, scenes:[{img_idx, narration, visual_prompt, image_summary}] }
 *
 * 后端真实实现：
 *   POST /app-api/merchant/mini/ai-video/bff/script/multi-scene
 *   走 CopywritingService.generateMultiSceneScript（豆包 vision-pro 看图分幕）
 *   后端会强制：imgIdx 不重不漏 / 最后一幕固定 CTA / 屏蔽吆喝违规词 /
 *              字数截断 / bgmStyle 白名单 / 兜底脚本
 *
 * 与 Phase 2.5 之前的差异：
 *   - LLM prompt 工程从前端搬到后端（运维改 prompt 不再要前端发版）
 *   - ARK key 不再需要给前端，前端只调商户鉴权过的 BFF
 *   - 多幕业务规则集中在后端 postProcessMultiScene 单测覆盖（18 case）
 */

import { request } from './request.js';

const FIXED_CTA = '立即扫码下单';
const BFF_MULTI_SCENE = '/app-api/merchant/mini/ai-video/bff/script/multi-scene';
const BFF_CHAT = '/app-api/merchant/mini/ai-video/bff/ark/chat';

// generateHighlight 仍走 BFF /ark/chat（视觉模型生成"一句话亮点"），不属于
// 主 LLM 链路，保留以兼容 create.vue 的"AI 重新识别"按钮
const VISION_MODEL = import.meta.env.VITE_ARK_VISION_MODEL || 'doubao-1-5-vision-pro-32k-250115';
// 文本润色用纯文本模型（更便宜、更快），不需要看图
const TEXT_MODEL = import.meta.env.VITE_ARK_LLM_MODEL || 'doubao-1-5-pro-32k-250115';

async function arkChat(model, messages) {
  const body = await request({
    url: BFF_CHAT,
    method: 'POST',
    data: { model, temperature: 0.85, messages },
  });
  if (!body || body.error) {
    throw new Error(body?.error?.message || 'LLM 返回错误');
  }
  return body?.choices?.[0]?.message?.content || '';
}

/**
 * 多幕分镜脚本（thin wrapper）。
 *
 * @param {Object} p
 * @param {number} p.imageCount     图片数（cap 6，前端硬约束）
 * @param {string[]} p.imageUrls    图片公网 URL（必须，后端走 vision 看图）
 * @param {string} p.userDescription 商户描述
 * @param {number} p.sceneCount     期望幕数
 * @param {number} p.sceneDuration  每幕秒数
 * @returns {{title:string, scenes:Array<{img_idx,narration,visual_prompt,image_summary}>}}
 */
export async function generateScript({ imageCount, imageUrls, userDescription, sceneCount, sceneDuration }) {
  const urls = Array.isArray(imageUrls) ? imageUrls.filter(Boolean).slice(0, 3) : [];
  if (!urls.length) {
    // 没图无法走 vision；返一个最小可用的兜底，让前端流程不崩
    const n = Math.max(1, Math.min(6, sceneCount || imageCount || 1));
    const scenes = Array.from({ length: n }, (_, i) => ({
      img_idx: i,
      image_summary: '',
      narration: i === n - 1 ? FIXED_CTA : '现拍现做，新鲜出炉',
      visual_prompt: 'Cinematic vertical product shot, push-in macro lens, food photography, golden hour lighting, film grain',
    }));
    return { title: '新品推荐', scenes };
  }

  const data = await request({
    url: BFF_MULTI_SCENE,
    method: 'POST',
    data: {
      shopName: '', // 后端会用 merchant.name 兜底
      userDescription: userDescription || '',
      imageUrls: urls,
      sceneCount: sceneCount || urls.length,
      sceneDuration: sceneDuration || 10,
    },
  });

  if (!data || !Array.isArray(data.scenes)) {
    throw new Error('后端返回脚本格式不合法');
  }

  // 后端 DTO 字段：imgIdx/visualPrompt/imageSummary（驼峰）
  // 前端代码历史用 img_idx/visual_prompt/image_summary（下划线）
  // 这里做字段名映射，让 aiVideo.js 调用方零改动
  const scenes = data.scenes.map((s) => ({
    img_idx: typeof s.imgIdx === 'number' ? s.imgIdx : 0,
    image_summary: s.imageSummary || '',
    narration: s.narration || '',
    visual_prompt: s.visualPrompt || '',
  }));

  return {
    title: data.title || '新品推荐',
    scenes,
  };
}

/**
 * 上传图片后自动生成"一句话亮点"，供用户确认/修改。
 *
 * 走 /ark/chat 让视觉模型读图。入参支持两种（自动识别）：
 *   ① http:// / https:// → OSS URL（推荐：body 小，快）
 *   ② base64（含或不含 data: 前缀） → 拼 data URL 直传
 *
 * 之前 3 张几 M 图 base64 直传会撞 yudao 1021010004 "请求体过大"。
 * create.vue 已改成选图时即刻 OSS 上传，把 url 喂进来。
 *
 * @param {string[]} imageBase64sOrUrls
 */
export async function generateHighlight(imageBase64sOrUrls) {
  const imgs = (imageBase64sOrUrls || []).slice(0, 3).filter(Boolean);
  if (!imgs.length) throw new Error('无图片');
  const imageContent = imgs.map((s) => {
    const url = /^https?:\/\//i.test(s)
      ? s
      : (s.startsWith('data:') ? s : `data:image/jpeg;base64,${s}`);
    return {
      type: 'image_url',
      image_url: { url, detail: 'low' },
    };
  });
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

/**
 * 把老板写的口水话 + 商品图，润色扩写成可发布的短视频解说稿。
 *
 * 用户场景：商户多数是路边摊 / 夫妻店老板，描述往往是"地瓜很甜"、"这个超好吃我家做了20年"
 * 之类的口语；直接喂给 generateScript 拆镜头，narration 出来也很苍白。
 *
 * 流程：视觉模型 **同时看图 + 读老板原话** →
 *   1. 识别图片中能看到的具体内容（颜色、状态、容器、配料、烹饪/加工方式…）
 *   2. 围绕老板原话扩写（保留原话核心卖点，不编造没说过的产地/年份/数字）
 *   3. 60-100 字短视频解说基稿
 *   4. 结尾必须以「立即扫码下单」收口
 *
 * @param {string} rawDesc 老板口水话描述（用户在表单里写的）
 * @param {string} [shopName=''] 店铺名（让润色后稍带店家身份感）
 * @param {string[]} [imageUrls=[]] 商品图 OSS URL；走视觉模型识图后扩写
 * @returns {Promise<string>} 润色后的解说稿（一段，不分幕，结尾"立即扫码下单"）
 */
export async function polishDescription(rawDesc, shopName = '', imageUrls = []) {
  const text = (rawDesc || '').trim();
  if (!text) return text;
  // 太短（<3 字）或太长（>500 字）跳过：太短没料、太长可能本身已经写好
  if (text.length < 3 || text.length > 500) return text;

  const sysPrompt = [
    '你是为路边小店、夫妻摊、街边手艺人写短视频解说稿的资深文案。',
    '请基于「商品图片 + 老板的原始描述」扩写一段可直接念出来的短视频文案：',
    '1. 先识别图片：抽出图里能看到的具体细节（颜色、状态、容器、热气、油光、食材、纹理…）',
    '2. 围绕老板原话扩写：把口水话补成有画面感、有温度的描述',
    '3. 真实优先：原话已说的事实（年份/产地/工艺/家传）可强化；图里没看到、原话没说的不准编造',
    '4. 用第三人称叙述，不要"我家""老板"等自称',
    '5. 总长 60-100 字，能在 30 秒短视频里念完一遍',
    '6. 禁止吆喝词："秒杀/限时/全网最低/赔本/老板疯了/大减价/今日特惠/必买"等',
    '7. 结尾必须独立成句，写「立即扫码下单」六个字（一字不差），不加感叹号、不加句号',
    '8. 不要标题、不要分点、不要 emoji、不要引号、不要解释；只输出正文一段',
  ].join('\n');

  // 视觉模型 messages：先文本指令，再每张图（OSS URL；base64 也兼容但不推荐）
  const imgs = (imageUrls || []).slice(0, 3).filter(Boolean);
  const userTextHead = (shopName ? `店家：${shopName}\n` : '') +
    `老板原始描述：${text}\n\n` +
    '请先识别图片中能看到的具体内容，再围绕老板这段话扩写成短视频解说基稿（60-100 字，结尾"立即扫码下单"）：';

  const userContent = imgs.length
    ? [
        { type: 'text', text: userTextHead },
        ...imgs.map((u) => ({
          type: 'image_url',
          image_url: {
            url: /^https?:\/\//i.test(u) ? u : (u.startsWith('data:') ? u : `data:image/jpeg;base64,${u}`),
            detail: 'low',
          },
        })),
      ]
    : userTextHead;

  // 有图走视觉模型；没图回退文本模型（仅基于原话扩写）
  const model = imgs.length ? VISION_MODEL : TEXT_MODEL;

  try {
    const raw = await arkChat(model, [
      { role: 'system', content: sysPrompt },
      { role: 'user', content: userContent },
    ]);
    let out = (raw || '').trim().replace(/^["""''「」【】]|["""''「」【】]$/g, '').trim();
    // 防御：模型偶尔会带"润色后："这种前缀
    out = out.replace(/^[\s\S]*?[:：]\s*/m, (m) => (m.length < 12 ? '' : m));
    // 兜底：模型如果忘了 CTA，强制补上
    if (out && !/立即扫码下单/.test(out)) {
      out = out.replace(/[。！!.\s]+$/g, '') + '。立即扫码下单';
    }
    if (!out || out.length < 5) return text; // 润色失败回退原文
    return out;
  } catch (e) {
    console.warn('[polishDescription] 润色失败，回退原始描述:', e?.message);
    return text;
  }
}
