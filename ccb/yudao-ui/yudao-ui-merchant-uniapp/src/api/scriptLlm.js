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

const FIXED_CTA = '扫码进店领优惠';
const BFF_MULTI_SCENE = '/app-api/merchant/mini/ai-video/bff/script/multi-scene';
const BFF_CHAT = '/app-api/merchant/mini/ai-video/bff/ark/chat';

// generateHighlight 仍走 BFF /ark/chat（视觉模型生成"一句话亮点"），不属于
// 主 LLM 链路，保留以兼容 create.vue 的"AI 重新识别"按钮
const VISION_MODEL = import.meta.env.VITE_ARK_VISION_MODEL || 'doubao-1-5-vision-pro-32k-250115';

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
  const urls = Array.isArray(imageUrls) ? imageUrls.filter(Boolean).slice(0, 6) : [];
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
 * 暂时仍走前端 BFF /ark/chat（看 base64 图片），后续 Phase 2.6 可整合到后端。
 *
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
