/**
 * AI 拍照识别多商品
 *  - 把一张"摊位全景照"喂给豆包视觉大模型
 *  - 让它列出里面每一件可售商品：name / category / introduction / bbox
 *  - 前端拿到 bbox 后用 canvas 各自裁切成单品图
 */

import { CATEGORIES } from './product.js';
import { request } from './request.js';

const VISION_MODEL =
  import.meta.env.VITE_ARK_VISION_MODEL || 'doubao-1-5-vision-pro-32k-250115';
const BFF_CHAT = '/app-api/merchant/mini/ai-video/bff/ark/chat';

const CAT_NAMES = CATEGORIES.map((c) => c.name).join(' / ');

/**
 * @param {string} imageUrl  已经上传到 OSS 的公网 URL
 * @returns {Promise<Array<{name, categoryId, categoryName, introduction, bbox:number[]}>>}
 */
export async function detectProducts(imageUrl) {
  if (!imageUrl || !/^https?:/.test(imageUrl)) {
    throw new Error('detectProducts 需要 http(s) 公网图片 URL');
  }

  const systemPrompt = [
    '你是摊主端 AI 店员。用户给你一张摊位 / 货架 / 桌面的整体照片，你要识别每一件独立可售商品，返回结构化 JSON。',
    '规则：',
    `- 只识别可销售的独立商品（如水果、零食、饮料、熟食、服装、小吃）。忽略背景、人、手、包装袋堆、装饰物、价签。`,
    '- 同一种商品多件摆一起合并为一项（不要一个苹果列 10 次）。',
    '- 每件商品要给：name（2-8 字的中文短名）、category（从固定列表里挑一个）、introduction（14-30 字一句话卖点，要说人话）、bbox（主体外框，数组 [x1,y1,x2,y2]，相对原图归一化到 0-1，含 5% 留白）。',
    '- 最多返回 8 件；少而准胜过多而糊。实在不确定的就别写。',
    '- 严禁输出 JSON 以外的任何文字、```、注释、解释。',
  ].join('\n');

  const userText = [
    `可选分类：${CAT_NAMES}`,
    '严格按下面结构返回，不要任何前后缀：',
    JSON.stringify(
      {
        products: [
          {
            name: '示例：蜜薯',
            category: '小吃',
            introduction: '现烤流糖心，咬一口能拉丝',
            bbox: [0.12, 0.18, 0.45, 0.72],
          },
        ],
      },
      null,
      2
    ),
  ].join('\n');

  const body = await request({
    url: BFF_CHAT,
    method: 'POST',
    data: {
      model: VISION_MODEL,
      temperature: 0.2,
      messages: [
        { role: 'system', content: systemPrompt },
        {
          role: 'user',
          content: [
            { type: 'text', text: userText },
            { type: 'image_url', image_url: { url: imageUrl, detail: 'high' } },
          ],
        },
      ],
    },
  });
  if (!body || body.error) {
    throw new Error(body?.error?.message || '视觉模型返回错误');
  }
  const raw = body?.choices?.[0]?.message?.content || '';
  return parse(raw);
}

function parse(raw) {
  let txt = raw.trim().replace(/^```(?:json)?\s*/i, '').replace(/```\s*$/i, '');
  const m = txt.match(/\{[\s\S]*\}/);
  if (m) txt = m[0];
  let obj;
  try {
    obj = JSON.parse(txt);
  } catch (e) {
    throw new Error('识别结果解析失败：' + raw.slice(0, 150));
  }
  const items = Array.isArray(obj.products) ? obj.products : [];
  if (!items.length) throw new Error('没识别到商品，换一张更清楚的照片试试');
  return items
    .map((p) => {
      const bbox = normalizeBbox(p.bbox);
      if (!bbox) return null;
      const cat = findCategory(p.category);
      return {
        name: String(p.name || '').trim().slice(0, 20) || '未命名',
        categoryId: cat.id,
        categoryName: cat.name,
        introduction: String(p.introduction || '').trim().slice(0, 50),
        bbox,
      };
    })
    .filter(Boolean)
    .slice(0, 8);
}

function normalizeBbox(arr) {
  if (!Array.isArray(arr) || arr.length !== 4) return null;
  let [x1, y1, x2, y2] = arr.map(Number);
  if ([x1, y1, x2, y2].some((v) => !Number.isFinite(v))) return null;
  // 有些模型爱返回 0-1000 或 0-100 区间，做一层归一
  const max = Math.max(x1, y1, x2, y2);
  if (max > 1.01) {
    const scale = max > 100 ? 1000 : 100;
    x1 /= scale;
    y1 /= scale;
    x2 /= scale;
    y2 /= scale;
  }
  // 纠正越界
  x1 = Math.max(0, Math.min(1, x1));
  y1 = Math.max(0, Math.min(1, y1));
  x2 = Math.max(0, Math.min(1, x2));
  y2 = Math.max(0, Math.min(1, y2));
  if (x2 <= x1 || y2 <= y1) return null;
  return [x1, y1, x2, y2];
}

function findCategory(name) {
  const hit = CATEGORIES.find((c) => name && name.includes(c.name));
  return hit || CATEGORIES.find((c) => c.id === 99) || CATEGORIES[0];
}
