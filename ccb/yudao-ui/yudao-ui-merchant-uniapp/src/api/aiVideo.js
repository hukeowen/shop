/**
 * AI 一键成片 · 图片数 × 10s 独立分镜版
 *
 * 流水线：
 *  ① 上传所有图片到 OSS 得公网 URL
 *  ② 豆包视觉 LLM 看每张图 → N 幕脚本 JSON（N = 图片数，每幕 narration 描述对应那张图）
 *  ③ 视频独立生成：幕 i 用图 i 做起始帧（Seedance 1.5 Pro × N，每段 10s，互不依赖）
 *     TTS 与 Seedance 并行（每幕一句）
 *  ④ 详情页按顺序播放：N 段 video + N 段 mp3 同步
 *
 * 任务状态（task.status）：
 *   1 = 上传/拆幕中   2 = 脚本已出，等用户确认
 *   3 = 视频+配音生成中（可查进度）
 *   4 = 首段就绪可播放（继续追加后续段）
 *   5 = 失败
 */

import { request } from './request.js';
import { uploadImage, uploadImages } from './oss.js';
import { generateScript, polishDescription } from './scriptLlm.js';
import { createClipTask, waitClip } from './jimeng.js';
import { findVoice } from './voice.js';

const MAX_SCENES = 6;
const MAX_TOTAL_SEC = 30;

// ============ 后端 thin wrappers（B 改造 Step 4.1）============
// 走 yudao 后端 /app-api/video/app/task/* 接口（商户鉴权 + 商户隔离）
// 把前端 store.tasks 里的关键状态（scenes / 元数据）持久化到 video_task 表 + video_scene 表
// 失败不抛错（避免阻塞前端主链路），只 console.warn；前端 store 仍是当前会话的真相源

const VIDEO_TASK_BASE = '/app-api/video/app/task';

/**
 * 一次拉全：任务详情 + 全部分镜（detail/confirm 页冷启动时用）
 * @param {number} taskId 后端 video_task.id（注意：不是前端 store.tasks.id）
 * @returns {Promise<Object|null>} 后端返 VideoTaskWithScenesRespVO 形态；不存在/跨商户返 null
 */
export async function fetchTaskWithScenes(taskId) {
  if (!taskId || taskId <= 0) return null;
  try {
    const data = await request({ url: `${VIDEO_TASK_BASE}/get-with-scenes?id=${taskId}`, method: 'GET' });
    return data || null;
  } catch (e) {
    console.warn('[fetchTaskWithScenes]', taskId, e.message);
    return null;
  }
}

/**
 * 批量保存分镜（confirmTask 落库 N 幕脚本时调）
 * @param {number} taskId 后端 video_task.id
 * @param {Array<{sceneIndex,imgIdx,startImageUrl,narration,visualPrompt,imageSummary,duration,isEndCard,status}>} scenes
 */
export async function saveScenesToDB(taskId, scenes) {
  if (!taskId || taskId <= 0 || !Array.isArray(scenes) || !scenes.length) return false;
  try {
    await request({
      url: `${VIDEO_TASK_BASE}/scenes/save`,
      method: 'POST',
      data: { taskId, scenes },
    });
    return true;
  } catch (e) {
    console.warn('[saveScenesToDB]', taskId, e.message);
    return false;
  }
}

/**
 * 过滤 patch 对象：移除值为 undefined / null / 空字符串 / 空数组的字段，
 * 避免前端误传空值把 DB 已有值清掉（partial update 语义）。
 * Integer 0 / boolean false 仍保留（合法值）。
 */
function pickNonEmpty(patch) {
  if (!patch || typeof patch !== 'object') return null;
  const out = {};
  for (const k of Object.keys(patch)) {
    const v = patch[k];
    if (v == null) continue; // null / undefined 跳过
    if (typeof v === 'string' && v.length === 0) continue; // 空字符串跳过
    if (Array.isArray(v) && v.length === 0) continue; // 空数组跳过
    out[k] = v;
  }
  return Object.keys(out).length ? out : null;
}

/**
 * 单幕 partial 更新（runClip 各阶段同步 status/clipTaskId/clipUrl）
 * @param {number} taskId
 * @param {number} sceneIndex
 * @param {{status?,clipTaskId?,clipUrl?,audioUrl?,failReason?,startImageUrl?}} patch
 */
export async function patchSceneToDB(taskId, sceneIndex, patch) {
  if (!taskId || taskId <= 0 || sceneIndex == null || sceneIndex < 0) return false;
  const clean = pickNonEmpty(patch);
  if (!clean) return false;
  try {
    await request({
      url: `${VIDEO_TASK_BASE}/scenes/patch`,
      method: 'PUT',
      data: { taskId, sceneIndex, ...clean },
    });
    return true;
  } catch (e) {
    console.warn('[patchSceneToDB]', taskId, sceneIndex, e.message);
    return false;
  }
}

/**
 * 任务元数据 partial 更新（confirmTask 完成 / 海报生成 / 最终落库时用）
 * @param {number} id 后端 video_task.id
 * @param {{title?,description?,imageUrls?,bgmStyle?,posterUrl?,voiceKey?,ratio?,coverUrl?,videoUrl?,duration?,status?,failReason?}} patch
 */
export async function patchTaskMetaToDB(id, patch) {
  if (!id || id <= 0) return false;
  const clean = pickNonEmpty(patch);
  if (!clean) return false;
  try {
    await request({
      url: `${VIDEO_TASK_BASE}/update-meta`,
      method: 'PUT',
      data: { id, ...clean },
    });
    return true;
  } catch (e) {
    console.warn('[patchTaskMetaToDB]', id, e.message);
    return false;
  }
}

// 幕数 = 图片数（cap MAX_SCENES=6，避免老板上传太多图费流量+ 加重 enhance/seedance 配额）
function sceneCountFor(imageCount) {
  return Math.max(1, Math.min(MAX_SCENES, imageCount || 1));
}
// 每幕时长：≤3 图用 10s（保留单幕沉浸），4-6 图降到 5s 保证总长 ≤ 30s
function sceneDurationFor(imageCount) {
  return imageCount <= 3 ? 10 : 5;
}
function totalSecFor(imageCount) {
  return Math.min(MAX_TOTAL_SEC, sceneCountFor(imageCount) * sceneDurationFor(imageCount));
}

const STORAGE_KEY = 'ai-video-tasks-v1';

const store = {
  nextId: 100001,
  // quota 字段保留作为本地 cache（getQuota 接后端真值）；初始 0 避免误导
  quota: { total: 0, used: 0, remaining: 0 },
  tasks: [],
};

// HMR / 浏览器刷新都能存活 —— Seedance 的 task_id 是服务端持久的，刷新后用 task_id 重新接管轮询
(function hydrate() {
  if (typeof localStorage === 'undefined') return;
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return;
    const saved = JSON.parse(raw);
    if (!saved || !Array.isArray(saved.tasks)) return;
    store.nextId = Math.max(store.nextId, saved.nextId || 100001);
    store.quota = saved.quota || store.quota;
    store.tasks = saved.tasks.map((t) => ({ ...t }));

    // status=1（上传/拆幕中）本来就是同步阻塞的，刷新后确实丢了——只能标失败
    // status=3（生成中）每幕的 clipTaskId 仍然在火山服务端跑，可以重新轮询接管
    store.tasks.forEach((t) => {
      if (t.status === 1) {
        t.status = 5;
        t.failReason = '页面刷新中断了脚本生成，请重试';
      } else if (t.status === 3) {
        resumeTask(t);
      }
    });
  } catch (e) {
    console.warn('[aiVideo] hydrate 失败:', e.message);
  }
})();

/** 刷新后接管仍在生成中的任务：把每幕已有 clipTaskId 的继续轮询 */
function resumeTask(t) {
  console.log(`[aiVideo] 恢复任务 ${t.id}，继续轮询 Seedance 任务`);
  (async () => {
    const tasks = (t.scenes || []).map(async (scene) => {
      if (scene.clipUrl) return; // 已完成
      // 端卡：不走即梦AI，直接重新调用 sidecar 即可
      if (scene.isEndCard) {
        const startImageUrl = t.imageUrls[scene.img_idx] || t.imageUrls[t.imageUrls.length - 1] || t.imageUrls[0];
        return runClip(t, scene, startImageUrl).then(() => {});
      }
      if (!scene.clipTaskId) {
        // 没开始就丢了：这种幕我们没有 task_id 可接管，只能标失败
        scene.error = 'video: 页面刷新前该分镜尚未提交到 Seedance';
        scene.status = 'video_failed';
        pushSceneToDB(t, scene, { status: 'video_failed', failReason: scene.error });
        return;
      }
      try {
        scene.status = 'video_running';
        pushSceneToDB(t, scene, { status: 'video_running' });
        const rawUrl = await waitClip(scene.clipTaskId, { intervalMs: 4000 });
        if (scene.narration?.trim()) {
          scene.status = 'audio_muxing';
          pushSceneToDB(t, scene, { status: 'audio_muxing' });
          try {
            const v = findVoice(t.voiceKey);
            const muxRes = await fetch('/video/mux', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({
                videoUrl: rawUrl,
                text: scene.narration,
                voice: v.ark,
                duration: scene.duration || t.sceneDuration || 10,
                // resumeTask 路径同样透 task.bgmStyle
                bgmStyle: t.bgmStyle || '',
              }),
            });
            const muxData = muxRes.ok ? await muxRes.json() : { ok: false };
            scene.clipUrl = muxData.ok ? muxData.url : rawUrl;
          } catch {
            scene.clipUrl = rawUrl;
          }
        } else {
          scene.clipUrl = rawUrl;
        }
        scene.status = 'ready';
        if (scene.index === 0 && t.status === 3) t.status = 4;
        pushSceneToDB(t, scene, { status: 'ready', clipUrl: scene.clipUrl });
      } catch (e) {
        scene.error = 'video: ' + e.message;
        scene.status = 'video_failed';
        if (scene.index === 0 && t.status === 3) t.status = 4;
        pushSceneToDB(t, scene, { status: 'video_failed', failReason: scene.error });
      }
      persist();
    });
    await Promise.all(tasks);

    const okClips = t.scenes.filter((s) => s.clipUrl).length;
    if (okClips === 0) {
      t.status = 5;
      const firstErr = t.scenes.find((s) => s.error)?.error;
      t.failReason = firstErr ? `全部分镜失败：${firstErr}` : '全部分镜失败，请重试';
    } else if (t.status !== 4) {
      t.status = 4;
    }
    persist();
  })();
}

let persistTimer = null;
function persist() {
  if (typeof localStorage === 'undefined') return;
  if (persistTimer) return;
  persistTimer = setTimeout(() => {
    persistTimer = null;
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(store));
    } catch (e) {
      console.warn('[aiVideo] persist 失败:', e.message);
    }
  }, 200);
}

// 生成阶段兜底：每 2 秒落一次盘
if (typeof window !== 'undefined') {
  setInterval(() => {
    if (store.tasks.some((t) => t.status === 3)) persist();
  }, 2000);
}

/**
 * 调后端 /script/rich 拿富脚本（lines + visualPrompt + bgmStyle）。
 * 主要用 bgmStyle 给 confirm 页 BGM 选择器自动预选；visualPrompt 作为各幕基底。
 * 失败返 null，调用方自行兜底。
 */
async function fetchRichScript(shopName, userDescription) {
  if (!userDescription || !userDescription.trim()) return null;
  try {
    const data = await request({
      url: '/app-api/merchant/mini/ai-video/bff/script/rich',
      method: 'POST',
      data: {
        shopName: shopName || '',
        userDescription: userDescription.trim(),
      },
    });
    return data || null;
  } catch (e) {
    console.warn('[fetchRichScript]', e.message);
    return null;
  }
}

/**
 * 6 个 BGM 风格 key（与 sidecar/bgm/<style>_*.mp3 文件名前缀一一对应）。
 * 详细 list 见 docs/design/marketing-system 里的 ai-video 优化方案 / sidecar/bgm/README.md。
 * 这里只在前端做 UI 选项标签；空字符串 = 不混 BGM（兜底）。
 */
/**
 * 调 sidecar /jimeng/enhance 美化单张图片（即梦 CV 增强：浅景深+暖色调+电影感）。
 * 输入图片公网 URL → 输出美化后图片 URL（1080×1920）；失败 fallback 原图（sidecar 已做）
 * @returns {Promise<{url: string, enhanced: boolean}>}
 */
export async function enhanceImage(imageUrl, businessHint) {
  if (!imageUrl) throw new Error('imageUrl 为空');
  const res = await fetch('/jimeng/enhance', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ imageUrl, businessHint: businessHint || '' }),
  });
  if (!res.ok) {
    return { url: imageUrl, enhanced: false };
  }
  const data = await res.json().catch(() => null);
  if (!data || !data.url) {
    return { url: imageUrl, enhanced: false };
  }
  return { url: data.url, enhanced: !!data.enhanced };
}

/**
 * 调 sidecar /jimeng/poster 生成专业端卡海报。
 * 输入店名 + slogan → 即梦 CV 生成 1080×1920 海报（暖色渐变 + 店名艺术字 + 二维码留位）。
 * 返回 url 字符串（公网 OSS）；失败抛错让调用方决定降级策略。
 */
export async function generatePoster({ shopName, slogan }) {
  if (!shopName) throw new Error('店铺名为空，无法生成海报');
  const res = await fetch('/jimeng/poster', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ shopName, slogan: slogan || '微信扫码下单' }),
  });
  if (!res.ok) {
    const txt = await res.text().catch(() => '');
    throw new Error(`HTTP ${res.status}${txt ? ': ' + txt.slice(0, 120) : ''}`);
  }
  const data = await res.json();
  if (!data.ok) throw new Error(data.error || 'poster 生成失败');
  if (!data.url) throw new Error('poster 未返 url');
  return data.url;
}

/**
 * 给 task 生成海报并缓存到 task.posterUrl；force=true 时强制重新生成
 * （用户点"重新生成海报"按钮）。返回最新 url。
 */
export async function ensurePosterForTask(taskId, { force = false } = {}) {
  const t = store.tasks.find((x) => x.id === taskId);
  if (!t) throw new Error('任务不存在');
  if (!force && t.posterUrl) return t.posterUrl;
  const url = await generatePoster({ shopName: t.shopName || '我的店铺' });
  t.posterUrl = url;
  persist();
  // B 改造 Step 4.4：海报 URL 同步到后端 video_task.poster_url（用户换设备能看见海报）
  if (t.dbId) {
    patchTaskMetaToDB(t.dbId, { posterUrl: url }).catch(() => {});
  }
  return url;
}

export const BGM_STYLES = [
  { key: 'street_food_yelling', label: '街头美食吆喝', desc: '热闹烟火气，烧烤/夜市/小吃' },
  { key: 'cozy_explore', label: '治愈探索', desc: '舒缓安静，茶馆/书店/手作' },
  { key: 'asmr_macro', label: 'ASMR 微距', desc: '细节特写，质感/工艺/食材' },
  { key: 'elegant_tea', label: '茶楼优雅', desc: '中式古典，茶/酒/精致服务' },
  { key: 'trendy_pop', label: '潮流流行', desc: '年轻活力，奶茶/咖啡/快餐' },
  { key: 'emotional_story', label: '情绪叙事', desc: '故事感，老店/匠人/传承' },
];

function newTask(init) {
  const t = {
    id: store.nextId++,
    status: 1,
    createdAt: new Date().toLocaleString('zh-CN'),
    imageUrls: [],         // OSS 预签名公网 URL
    userDescription: '',
    voiceKey: 'cancan',
    ratio: '9:16',
    title: '',
    // BGM 风格：confirm.vue 让用户从 BGM_STYLES 6 选 1；'none' = 用户主动选"不加 BGM"
    // 默认 '' → sidecar 走它的默认 cozy_explore（保留旧行为兼容）
    // sidecar /video/mux 接到 bgmStyle 后从 sidecar/bgm/<style>_*.mp3 随机挑 1 首，
    // ffmpeg amix: TTS 1.0 + BGM 0.18(-15dB) + duration=longest
    bgmStyle: '',
    scenes: [],            // [{ img_idx, narration, visual_prompt, clipTaskId, clipUrl, audioUrl, status }]
    progress: { total: 0, done: 0 },  // confirmTask 时根据实际幕数再设
    coverUrl: null,
    // 端卡海报：detail 页生成完成后通过 sidecar /jimeng/poster 拿一张 1080×1920 海报
    // 让商户单独下载/分享朋友圈；和视频独立，不影响 mux/publish 流程
    posterUrl: null,
    publishedToDouyin: false,
    failReason: null,
    ...init,
  };
  store.tasks.unshift(t);
  persist();
  return t;
}

/**
 * 步骤①②：上传 + 可选美化 + LLM 拆幕（阻塞，通常 10-20 秒；开美化则 +20-30 秒）
 *
 * @param {Object} p
 * @param {string[]} p.imageBase64s   原图 base64
 * @param {string} p.userDescription  口语化描述
 * @param {string} p.voiceKey         TTS 音色
 * @param {string} p.ratio            画面比例
 * @param {string} p.shopName         店铺名（用于 LLM/海报）
 * @param {boolean} [p.enhance=true]  是否对每张图调 /jimeng/enhance 美化预处理；
 *                                    开 = 视觉品质提升一档；关 = 省时间省即梦配额
 * @param {(state)=>void} [p.onProgress]  阶段回调，方便 create.vue 展示
 *                                         state 形如 {phase:'uploading'|'enhancing'|'scripting',
 *                                                     enhancedCount, totalCount}
 * @returns taskId
 */
export async function createTask({ imageBase64s, imageUrls, userDescription, voiceKey, ratio, shopName, enhance = true, onProgress }) {
  // 优先复用已上传的 OSS URL（create.vue 选图时即上传），没有再走 base64 上传
  const preUploadedUrls = (imageUrls || []).filter(Boolean);
  const imgs = preUploadedUrls.length ? preUploadedUrls : (imageBase64s || []).filter(Boolean);
  if (!imgs.length) throw new Error('请至少上传 1 张图片');

  const task = newTask({ userDescription, voiceKey, ratio: ratio || '9:16', shopName: shopName || '' });
  const emit = (s) => { try { onProgress && onProgress(s); } catch {} };

  try {
    // ① 上传到 OSS（imageUrls 已就绪则跳过，省一次 OSS 调用）
    emit({ phase: 'uploading', totalCount: imgs.length });
    task.imageUrls = preUploadedUrls.length ? preUploadedUrls : await uploadImages(imgs);

    // ①.5 美化预处理（即梦 CV）— 把老板手机原图喂给 Seedance 之前提一档
    //      失败 fallback 原图（sidecar 已经做好降级），不阻塞主流程
    if (enhance) {
      emit({ phase: 'enhancing', enhancedCount: 0, totalCount: imgs.length });
      const businessHint = userDescription ? userDescription.slice(0, 24) : '';
      const enhancedUrls = [];
      let succ = 0;
      for (let i = 0; i < task.imageUrls.length; i++) {
        try {
          const r = await enhanceImage(task.imageUrls[i], businessHint);
          enhancedUrls.push(r.url);
          if (r.enhanced) succ += 1;
        } catch (e) {
          console.warn(`[enhance] 第 ${i + 1} 张失败 fallback 原图:`, e.message);
          enhancedUrls.push(task.imageUrls[i]);
        }
        emit({ phase: 'enhancing', enhancedCount: i + 1, totalCount: imgs.length });
      }
      task.imageUrls = enhancedUrls;
      task.imageEnhancedCount = succ; // detail/confirm 可展示"X/Y 张已美化"
    }
    task.coverUrl = task.imageUrls[0];

    // ②.0 文案润色：老板写的是口水话（"地瓜很甜"），先让视觉模型同时看图 + 读老板原话，
    //      扩写成 60-100 字、有画面感的短视频解说基稿（结尾"微信扫码下单"），
    //      再喂给后续视觉拆镜。失败回退原文，不阻塞主流程。
    emit({ phase: 'polishing' });
    let polishedDescription = userDescription;
    try {
      polishedDescription = await polishDescription(userDescription, shopName, task.imageUrls);
      task.userDescriptionRaw = userDescription;
      task.userDescriptionPolished = polishedDescription;
    } catch (e) {
      console.warn('[aiVideo] polishDescription 失败，沿用原文:', e?.message);
    }

    emit({ phase: 'scripting' });
    // ②a 旁路调后端 generateRichScript 拿 BGM 风格推荐 + 英文运镜基底
    //     这是即梦 API 优化第一波 ③ 的前端接入：bgmStyle 自动落到 task，confirm 页打开
    //     时该选项已被预选；visualPrompt 作为各幕 Seedance 的运镜基底，避免老板写不出
    //     "push-in macro"" "golden hour" 等词。失败不阻塞主流程（task.bgmStyle 仍空）
    let richBaseVisualPrompt = '';
    try {
      const rich = await fetchRichScript(shopName, polishedDescription);
      if (rich) {
        if (rich.bgmStyle) task.bgmStyle = rich.bgmStyle;
        if (rich.visualPrompt) richBaseVisualPrompt = rich.visualPrompt;
      }
    } catch (e) {
      console.warn('[aiVideo] generateRichScript 失败，fallback 老路径:', e.message);
    }

    // ②b LLM 拆 N 幕（视觉模型，真的看到每张图；幕数 = 图数，每幕 1:1 用对应图）
    const sceneCount = sceneCountFor(imgs.length);
    const perSceneDuration = sceneDurationFor(imgs.length);
    task.sceneDuration = perSceneDuration;
    task.totalDuration = sceneCount * perSceneDuration;
    const { title, scenes } = await generateScript({
      imageCount: imgs.length,
      imageUrls: task.imageUrls,
      userDescription: polishedDescription,
      sceneCount,
      sceneDuration: perSceneDuration,
    });
    // 用 rich.visualPrompt 当 fallback：LLM 没给某幕 visual_prompt 时填这个基底
    if (richBaseVisualPrompt) {
      scenes.forEach((s) => {
        if (!s.visual_prompt || !s.visual_prompt.trim()) {
          s.visual_prompt = richBaseVisualPrompt;
        }
      });
    }
    task.title = title;
    // 所有幕（含最后一张图）都走即梦生成动态视频；端卡作为独立尾缀片段在 N 个分镜
    // 串完后再追加，逻辑见 runClip 后面的 t.scenes.push 端卡 + merge 顺序
    task.scenes = scenes.map((s, i) => {
      const dur = (s.duration === 5 || s.duration === 10) ? s.duration : perSceneDuration;
      return {
        index: i,
        img_idx: s.img_idx,
        image_summary: s.image_summary || '',
        narration: s.narration,
        visual_prompt: s.visual_prompt,
        duration: dur,
        isEndCard: false, // 不再让最后一张图当端卡 — 端卡是独立追加的尾缀
        status: 'pending',
        clipTaskId: null,
        clipUrl: null,
        audioUrl: null,
        audioSource: null,
        error: null,
      };
    });
    // 追加独立端卡尾缀：用封面图（最后一张或 LLM 选的）+ 2s 静图 + 二维码 + CTA TTS
    // 这一幕走 sidecar /video/endcard 不走即梦，保留 2s 紧凑收尾
    task.scenes.push({
      index: task.scenes.length,
      img_idx: scenes.length - 1, // 用最后一张图当端卡背景（也可换成 0=封面图）
      image_summary: '',
      narration: '微信扫码下单',
      visual_prompt: '',
      duration: 2,
      isEndCard: true,
      status: 'pending',
      clipTaskId: null,
      clipUrl: null,
      audioUrl: null,
      audioSource: null,
      error: null,
    });
    task.status = 2;
    // 图片已上传 → 落库
    registerTaskToDB(task);
  } catch (e) {
    task.status = 5;
    task.failReason = e.message;
  }
  persist();
  return task.id;
}

/**
 * 取任务详情。
 *
 * <p>B 改造 Step 4.5：本地命中直接返；本地没有时走后端 /get-with-scenes 拉全
 * (含完整分镜)，并 hydrate 回本地 cache，让换设备 / 浏览器历史也能继续看。</p>
 *
 * <p>id 兼容三种形态：</p>
 * <ul>
 *     <li>纯数字 (本地 store.tasks.id, 如 100001) → 优先本地</li>
 *     <li>"db_123" 字符串 (历史列表用) → 直接按 dbId=123 走后端</li>
 *     <li>本地未找到的纯数字也尝试当 dbId 兜底</li>
 * </ul>
 */
export async function getTask(id) {
  // 1. 先查本地
  const local = store.tasks.find((x) => x.id === id);
  if (local) {
    return { ...local, scenes: (local.scenes || []).map((s) => ({ ...s })) };
  }
  // 2. 解析出 dbId
  let dbId = null;
  if (typeof id === 'string' && id.startsWith('db_')) {
    dbId = parseInt(id.substring(3), 10);
  } else if (typeof id === 'number' && id > 0) {
    dbId = id; // 兜底：当作 dbId 试一次
  }
  if (!dbId || isNaN(dbId)) return null;
  // 3. 后端拉详情 + 全分镜，转成本地形态后 hydrate 进 store
  const remote = await fetchTaskWithScenes(dbId);
  if (!remote) return null;
  const t = remoteTaskToLocal(remote);
  // 写入 cache（避免下次再请求；持久 localStorage）
  if (!store.tasks.find((x) => x.dbId === dbId)) {
    store.tasks.unshift(t);
    persist();
  }
  return { ...t, scenes: (t.scenes || []).map((s) => ({ ...s })) };
}

/**
 * 后端 VideoTaskWithScenesRespVO → 前端 task 形态。
 * 字段映射：
 *   imgIdx → img_idx, visualPrompt → visual_prompt, imageSummary → image_summary
 *   后端 status (0/1/2/3) → 前端 task.status (3/3/4/5) 同 dbTaskToLocal
 */
function remoteTaskToLocal(r) {
  const statusMap = { 0: 3, 1: 3, 2: 4, 3: 5 };
  const clientStatus = statusMap[r.status] ?? 3;
  const scenes = Array.isArray(r.scenes) ? r.scenes.map((s) => ({
    index: s.sceneIndex,
    img_idx: s.imgIdx,
    image_summary: s.imageSummary || '',
    narration: s.narration || '',
    visual_prompt: s.visualPrompt || '',
    duration: s.duration || null,
    isEndCard: !!s.isEndCard,
    status: s.status || 'pending',
    clipTaskId: s.clipTaskId || null,
    clipUrl: s.clipUrl || null,
    audioUrl: s.audioUrl || null,
    audioSource: null,
    error: s.failReason || null,
    startImageUrl: s.startImageUrl || null,
  })) : [];
  return {
    id: `db_${r.id}`,
    dbId: r.id,
    status: clientStatus,
    title: r.title || '',
    userDescription: r.description || '',
    imageUrls: r.imageUrls || [],
    coverUrl: r.coverUrl || (r.imageUrls && r.imageUrls[0]) || null,
    posterUrl: r.posterUrl || null,
    bgmStyle: r.bgmStyle || '',
    voiceKey: r.voiceKey || 'cancan',
    ratio: r.ratio || '9:16',
    scenes,
    sceneDuration: scenes[0]?.duration || 10,
    progress: { total: scenes.length, done: scenes.filter((s) => s.clipUrl).length },
    publishedToDouyin: r.douyinPublishStatus === 2,
    douyinItemId: r.douyinItemId || null,
    failReason: r.failReason || null,
    createdAt: r.createTime
      ? (typeof r.createTime === 'string' ? r.createTime.replace('T', ' ').substring(0, 16) : String(r.createTime))
      : '',
    shopName: '',
  };
}

/**
 * 替换某幕使用的图片（上传到 OSS 并写回 task.imageUrls[img_idx]）
 * 1:1 映射下 scene.img_idx === scene.index，只影响这一幕
 * @returns {Promise<string>} 新图的 OSS 公网 URL
 */
export async function replaceSceneImage({ taskId, sceneIndex, base64 }) {
  const t = store.tasks.find((x) => x.id === taskId);
  if (!t) throw new Error('任务不存在');
  const scene = t.scenes[sceneIndex];
  if (!scene) throw new Error(`分镜 ${sceneIndex} 不存在`);
  const { url: newUrl } = await uploadImage(base64);
  // 确保 img_idx 对齐后写回，避免多幕共用一个 slot 时相互污染
  const slot = scene.img_idx ?? sceneIndex;
  scene.img_idx = slot;
  t.imageUrls[slot] = newUrl;
  if (slot === 0) t.coverUrl = newUrl;
  persist();
  return newUrl;
}

/**
 * 重新生成文案（LLM 再写一遍 3 幕脚本，图片/OSS 复用）
 * 允许用户在确认页不满意时多来几次
 */
export async function regenerateScript(taskId) {
  const t = store.tasks.find((x) => x.id === taskId);
  if (!t) throw new Error('任务不存在');
  if (!t.imageUrls?.length) throw new Error('任务缺少图片 URL');
  t.status = 1;
  try {
    const sceneCount = sceneCountFor(t.imageUrls.length);
    const perSceneDuration = sceneDurationFor(t.imageUrls.length);
    t.sceneDuration = perSceneDuration;
    t.totalDuration = sceneCount * perSceneDuration;
    const { title, scenes } = await generateScript({
      imageCount: t.imageUrls.length,
      imageUrls: t.imageUrls,
      userDescription: t.userDescription,
      sceneCount,
      sceneDuration: perSceneDuration,
    });
    t.title = title;
    // 同 confirmTask：N 张图全走即梦，端卡作为独立尾缀片段追加
    t.scenes = scenes.map((s, i) => {
      const dur = (s.duration === 5 || s.duration === 10) ? s.duration : perSceneDuration;
      return {
        index: i,
        img_idx: s.img_idx,
        image_summary: s.image_summary || '',
        narration: s.narration,
        visual_prompt: s.visual_prompt,
        duration: dur,
        isEndCard: false,
        status: 'pending',
        clipTaskId: null,
        clipUrl: null,
        audioUrl: null,
        audioSource: null,
        error: null,
      };
    });
    t.scenes.push({
      index: t.scenes.length,
      img_idx: scenes.length - 1,
      image_summary: '',
      narration: '微信扫码下单',
      visual_prompt: '',
      duration: 2,
      isEndCard: true,
      status: 'pending',
      clipTaskId: null,
      clipUrl: null,
      audioUrl: null,
      audioSource: null,
      error: null,
    });
    t.status = 2;
  } catch (e) {
    t.status = 5;
    t.failReason = 'AI 文案生成失败：' + e.message;
    throw e;
  }
  return true;
}

/**
 * 步骤③：用户确认脚本后开始生成（视频+配音并行）
 * @param {Object} p
 * @param {number} p.taskId
 * @param {Array} [p.scenes]  可选：用户编辑过的 scenes 列表
 */
export async function confirmTask({ taskId, scenes, bgmStyle }) {
  const t = store.tasks.find((x) => x.id === taskId);
  if (!t) throw new Error('任务不存在');

  if (Array.isArray(scenes) && scenes.length) {
    t.scenes = t.scenes.map((s, i) => ({
      ...s,
      narration: scenes[i]?.narration || s.narration,
      visual_prompt: scenes[i]?.visual_prompt || s.visual_prompt,
    }));
  }
  // 用户在 confirm 页选了 BGM 风格 → 落到 task 上，runClip 调 mux 时透传
  if (typeof bgmStyle === 'string') {
    t.bgmStyle = bgmStyle;
  }

  t.status = 3;
  t.progress = { total: t.scenes.length, done: 0 };
  persist();

  // B 改造 Step 4.2：把 N 幕脚本 + 任务元数据落到后端 video_scene / video_task 表，
  // 实现"老板换设备登录能看到自己生成中的任务+分镜"。失败不抛错（thin wrapper 内部
  // catch + warn），不阻塞下面的 runClip。需要 task.dbId 已就位（registerTaskToDB
  // 在 createTask 末尾已调）；register 失败的话 dbId 为空就跳过同步。
  if (t.dbId) {
    syncScenesAndMetaToDB(t).catch((e) =>
        console.warn('[confirmTask] syncScenesAndMetaToDB 异常:', e?.message));
  }

  // 后台跑：每幕独立生成（幕 i 起始帧 = 图 i），**串行**跑
  //
  // 火山即梦免费/低配额账号同时进行的视频任务并发上限 = 1：实测
  //   5 个并发 submit → 1 OK / 3 个 429 code=50430 / 1 OK
  //   即使串行 submit，前一任务 generating 期间下一个也会被拒
  //   （这就是用户报"几张图就 401"的真因，BFF 把 429 包装成异常）
  //
  // 改成串行跑整条 runClip：每幕「submit→轮询→合配音」全跑完才下一幕，
  // 保证同时只有 1 个即梦任务。代价：N 张图 ≈ N×30s（vs 并发理论 30s），
  // 但当前并发实际 0 成功，串行肯定能成。
  // 端卡（最后一幕）不走即梦，仍可与最后一个真镜头串完之后立刻跑。
  // TTS 在 runClip 内部与视频生成串行，视频就绪后立即合流。
  (async () => {
    for (let i = 0; i < t.scenes.length; i++) {
      const scene = t.scenes[i];
      const selfImg = t.imageUrls[scene.img_idx] || t.imageUrls[i] || t.imageUrls[0];
      try {
        await runClip(t, scene, selfImg);
      } catch (e) {
        console.warn(`[runClip ${i}] 失败但继续后续幕:`, e?.message);
        // runClip 内部已经把错误写到 scene.error，这里不抛，让其他幕能继续跑
      }
    }

    const okClips = t.scenes.filter((s) => s.clipUrl).length;
    if (okClips === 0) {
      t.status = 5;
      // 把首个分镜的错误透给顶层 failReason，便于用户看到真正原因
      const firstErr = t.scenes.find((s) => s.error)?.error;
      t.failReason = firstErr ? `全部分镜失败：${firstErr}` : '全部分镜失败，请重试';
      persist();
      syncStatusToDB(t);
      return;
    }
    t.status = 4;
    // 配额扣减由后端 BFF /jimeng/submit 已经原子扣过（decreaseVideoQuota），
    // 前端不再瞎累加 store.quota.used；下次 getQuota 走后端拿真值
    persist();
    syncStatusToDB(t);
  })();

  return true;
}

/**
 * B 改造 Step 4.3：单幕状态/产出 fire-and-forget 同步到后端。
 * task.dbId 缺失（register 失败 / 老 task）时静默跳过；网络错由 patchSceneToDB
 * 内部 swallow，不阻塞 runClip 主流程。
 */
function pushSceneToDB(task, scene, patch) {
  if (!task || !task.dbId || !scene || scene.index == null) return;
  // fire-and-forget；不 await 避免拖慢生成
  patchSceneToDB(task.dbId, scene.index, patch).catch(() => {});
}

/** 单段视频：用指定起始图 + prompt 跑即梦AI；视频就绪后立即合入 TTS 配音 */
async function runClip(task, scene, startImageUrl) {
  try {
    scene.startImageUrl = startImageUrl;
    pushSceneToDB(task, scene, { startImageUrl });

    // 端卡不走即梦AI：静止商品图 + 正中大二维码 + 店名，直接 sidecar 合成
    if (scene.isEndCard) {
      scene.status = 'endcard_building';
      pushSceneToDB(task, scene, { status: 'endcard_building' });
      const v = findVoice(task.voiceKey);
      // 端卡 TTS = LLM 给最后一幕生成的完整对白（结尾已被后端 ensureCtaSuffix
      // 兜底追加「微信扫码下单」），保证既有画面对白又有 CTA 收口。
      // 前端再加一道兜底：万一 narration 没 CTA（LLM 失败 + 兜底脚本路径），
      // 末尾补上，避免老板拿到没扫码引导的端卡。
      let endText = (scene.narration || '').trim();
      if (!endText) {
        endText = '微信扫码下单';
      } else if (!endText.endsWith('微信扫码下单')) {
        endText = endText.replace(/[。！!.\s]+$/, '') + '，微信扫码下单';
      }
      const endRes = await fetch('/video/endcard', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          imageUrl: startImageUrl,
          shopName: task.shopName || '',
          text: endText,
          voice: v.ark,
          duration: scene.duration || 2,
        }),
      });
      const endData = endRes.ok ? await endRes.json() : { ok: false, error: `HTTP ${endRes.status}` };
      if (!endData.ok) throw new Error('endcard: ' + (endData.error || 'unknown'));
      scene.clipUrl = endData.url;
      task.progress.done += 1;
      scene.status = 'ready';
      if (scene.index === 0 && task.status === 3) task.status = 4;
      persist();
      pushSceneToDB(task, scene, { status: 'ready', clipUrl: endData.url });
      return true;
    }

    scene.status = 'video_creating';
    pushSceneToDB(task, scene, { status: 'video_creating' });
    scene.clipTaskId = await createClipTask({
      imageUrl: startImageUrl,
      prompt: scene.visual_prompt || scene.narration,
      ratio: task.ratio,
      duration: scene.duration || task.sceneDuration || 10,
    });
    scene.status = 'video_running';
    pushSceneToDB(task, scene, { status: 'video_running', clipTaskId: scene.clipTaskId });
    const rawUrl = await waitClip(scene.clipTaskId, { intervalMs: 4000 });

    // 视频就绪后合入配音（sidecar 内完成：TTS + FFmpeg mux + 上传 TOS）
    if (scene.narration?.trim()) {
      scene.status = 'audio_muxing';
      pushSceneToDB(task, scene, { status: 'audio_muxing' });
      try {
        const v = findVoice(task.voiceKey);
        const muxRes = await fetch('/video/mux', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            videoUrl: rawUrl,
            text: scene.narration,
            voice: v.ark,
            duration: scene.duration || task.sceneDuration || 10,
            // 透传 task.bgmStyle 给 sidecar：mux 内部按 style 从 sidecar/bgm/<style>_*.mp3 随机挑 1 首
            // 空串 = 不混 BGM，sidecar 也兼容
            bgmStyle: task.bgmStyle || '',
          }),
        });
        const muxData = muxRes.ok ? await muxRes.json() : { ok: false, error: `HTTP ${muxRes.status}` };
        if (muxData.ok) {
          scene.clipUrl = muxData.url;
        } else {
          scene.clipUrl = rawUrl;
          console.warn(`[scene ${scene.index}] mux 失败，降级原视频:`, muxData.error);
        }
      } catch (e) {
        scene.clipUrl = rawUrl;
        console.warn(`[scene ${scene.index}] mux 异常，降级原视频:`, e.message);
      }
    } else {
      scene.clipUrl = rawUrl;
    }

    task.progress.done += 1;
    scene.status = 'ready';
    if (scene.index === 0 && task.status === 3) task.status = 4;
    persist();
    pushSceneToDB(task, scene, { status: 'ready', clipUrl: scene.clipUrl });
    return true;
  } catch (e) {
    scene.error = 'video: ' + e.message;
    scene.status = 'video_failed';
    console.warn(`[scene ${scene.index}] 视频失败:`, e.message);
    if (scene.index === 0 && task.status === 3) task.status = 4;
    persist();
    pushSceneToDB(task, scene, { status: 'video_failed', failReason: scene.error });
    return false;
  }
}

const TASK_BASE = '/app-api/video/app/task';

/**
 * B 改造 Step 4.2：批量同步当前 task 的 N 幕脚本 + 元数据到后端。
 * 失败由 saveScenesToDB / patchTaskMetaToDB 内部静默吞掉（仅 warn），不抛错。
 * 字段名映射：前端用下划线（img_idx/visual_prompt/image_summary）→ 后端驼峰
 * （imgIdx/visualPrompt/imageSummary）。
 */
async function syncScenesAndMetaToDB(task) {
  if (!task || !task.dbId) return;
  // 1. 分镜：UPSERT N 幕
  const scenes = (task.scenes || []).map((s) => ({
    sceneIndex: s.index,
    imgIdx: s.img_idx,
    startImageUrl: task.imageUrls && s.img_idx != null ? (task.imageUrls[s.img_idx] || '') : '',
    narration: s.narration || '',
    visualPrompt: s.visual_prompt || '',
    imageSummary: s.image_summary || '',
    duration: s.duration || null,
    isEndCard: !!s.isEndCard,
    status: s.status || 'pending',
  }));
  await saveScenesToDB(task.dbId, scenes);
  // 2. 元数据：bgmStyle / voiceKey / ratio / coverUrl / title + status=1 (PROCESSING)
  await patchTaskMetaToDB(task.dbId, {
    title: task.title || undefined,
    bgmStyle: task.bgmStyle || undefined,
    voiceKey: task.voiceKey || undefined,
    ratio: task.ratio || undefined,
    coverUrl: task.coverUrl || undefined,
    status: 1, // VideoTaskStatusEnum.PROCESSING
  });
}

/** 注册任务到 DB（在图片上传完成后调用），返回 dbId */
async function registerTaskToDB(task) {
  try {
    const dbId = await request({
      url: `${TASK_BASE}/register`,
      method: 'POST',
      data: {
        title: task.title || task.userDescription || '未命名视频',
        description: task.userDescription || '',
        imageUrls: task.imageUrls,
      },
    });
    task.dbId = dbId;
    persist();
  } catch (e) {
    console.warn('[aiVideo] registerTaskToDB 失败（不影响本地流程）:', e.message);
  }
}

/**
 * 同步任务最终状态到 DB。
 *
 * <p>B 改造 Step 4.4：从老的 /sync-status（仅 status/videoUrl/failReason）
 * 升级走新的 /update-meta，能一次写更多字段（如 status / videoUrl / failReason
 * + 顺手把 coverUrl / posterUrl 等元数据）。</p>
 *
 * <p>映射规则：前端 task.status (1=拆幕中 2=等确认 3=生成中 4=完成 5=失败) →
 * 后端 VideoTaskStatusEnum (0=PENDING 1=PROCESSING 2=COMPLETED 3=FAILED)。</p>
 */
async function syncStatusToDB(task) {
  if (!task.dbId) return;
  const firstScene = (task.scenes || []).find((s) => s.clipUrl);
  // 前端 status → 后端 status：4=完成→2、5=失败→3、其余→1（PROCESSING）
  let dbStatus = 1;
  if (task.status === 4) dbStatus = 2;
  else if (task.status === 5) dbStatus = 3;
  await patchTaskMetaToDB(task.dbId, {
    status: dbStatus,
    videoUrl: firstScene?.clipUrl || undefined,
    failReason: task.failReason || undefined,
  });
}

/** DB 任务记录 → 本地 task 格式 */
function dbTaskToLocal(t) {
  // DB status: 0=PENDING,1=PROCESSING,2=COMPLETED,3=FAILED
  const statusMap = { 0: 3, 1: 3, 2: 4, 3: 5 };
  const clientStatus = statusMap[t.status] ?? 3;
  return {
    id: `db_${t.id}`,
    dbId: t.id,
    status: clientStatus,
    title: t.title || '',
    userDescription: t.description || '',
    imageUrls: t.imageUrls || [],
    coverUrl: t.imageUrls?.[0] || null,
    voiceKey: 'cancan',
    ratio: '9:16',
    scenes: t.videoUrl
      ? [{ index: 0, clipUrl: t.videoUrl, narration: '', status: 'ready', isEndCard: false }]
      : [],
    progress: { total: 0, done: 0 },
    publishedToDouyin: t.douyinPublishStatus === 2,
    douyinItemId: t.douyinItemId || null,
    failReason: t.failReason || null,
    createdAt: t.createTime
      ? (typeof t.createTime === 'string' ? t.createTime.replace('T', ' ').substring(0, 16) : String(t.createTime))
      : '',
  };
}

export async function getTaskPage() {
  // 本地任务（in-progress, 非DB）
  const localTasks = store.tasks.filter((t) => !t.dbId || t.status === 1 || t.status === 2 || t.status === 3);

  try {
    const resp = await request({ url: `${TASK_BASE}/my-page` });
    const dbList = Array.isArray(resp) ? resp : (resp?.list || []);
    const dbTasks = dbList.map(dbTaskToLocal);
    // 合并：local优先（local任务的dbId可能与DB中条目重叠，用dbId去重）
    const dbIds = new Set(store.tasks.filter((t) => t.dbId).map((t) => t.dbId));
    const filteredDb = dbTasks.filter((t) => !dbIds.has(t.dbId));
    const merged = [...store.tasks, ...filteredDb];
    return { total: merged.length, list: merged };
  } catch {
    // 降级：只返回本地
    return { total: store.tasks.length, list: [...store.tasks] };
  }
}

/**
 * 配额查询：以后端 video_quota 表为准；接口失败时返 0（不再 fallback 到 mock 999）
 * 后端 RespVO: {remaining, total, used}
 */
export async function getQuota() {
  try {
    const data = await request({ url: '/app-api/merchant/mini/video-quota/me' });
    const remaining = Number(data?.remaining ?? 0);
    const total = Number(data?.total ?? remaining);
    const used = Number(data?.used ?? Math.max(0, total - remaining));
    return { total, used, remaining };
  } catch (e) {
    console.warn('[getQuota] 接口失败，按 0 处理:', e?.message);
    return { total: 0, used: 0, remaining: 0 };
  }
}

/**
 * 购买配额套餐。
 * 后端 POST /merchant/mini/video-quota/packages/{packageId}/purchase
 * 返回 AppMerchantPackagePurchaseRespVO {orderId,payOrderId,...}，前端拿到 orderId
 * 跳支付页（/app-api/pay/order/submit 完成支付，回调后台扣款 + 加配额）。
 */
export async function buyQuota(packageId) {
  if (!packageId) return { ok: false, msg: '套餐 ID 为空' };
  try {
    const data = await request({
      url: `/app-api/merchant/mini/video-quota/packages/${packageId}/purchase`,
      method: 'POST',
    });
    return { ok: true, data };
  } catch (e) {
    return { ok: false, msg: e?.message || '配额购买暂未开通，请联系平台' };
  }
}

/** 配额套餐列表（quota.vue 展示） */
export async function listQuotaPackages() {
  try {
    return await request({ url: '/app-api/merchant/mini/video-quota/packages' });
  } catch (e) {
    console.warn('[listQuotaPackages]', e?.message);
    return [];
  }
}

const DOUYIN_TOKEN_KEY = 'douyin-token-v1';

function readDouyinToken() {
  if (typeof localStorage === 'undefined') return null;
  try {
    const raw = localStorage.getItem(DOUYIN_TOKEN_KEY);
    if (!raw) return null;
    const t = JSON.parse(raw);
    // expiresIn 单位秒；留 60s 安全 margin
    if (t?.grantedAt && t?.expiresIn && Date.now() > t.grantedAt + (t.expiresIn - 60) * 1000) {
      localStorage.removeItem(DOUYIN_TOKEN_KEY);
      return null;
    }
    return t;
  } catch {
    return null;
  }
}
function writeDouyinToken(t) {
  if (typeof localStorage === 'undefined') return;
  try { localStorage.setItem(DOUYIN_TOKEN_KEY, JSON.stringify(t)); } catch {}
}

/** 弹窗完成抖音 OAuth，拿到 { accessToken, openId, ... } */
async function douyinAuthorize() {
  if (typeof window === 'undefined') throw new Error('非浏览器环境无法授权');
  const r = await fetch('/douyin/auth-url');
  const d = r.ok ? await r.json() : { ok: false, error: `HTTP ${r.status}` };
  if (!d.ok || !d.url) throw new Error('无法获取抖音授权链接：' + (d.error || '未知'));

  const popup = window.open(d.url, 'douyin-oauth', 'width=720,height=820');
  if (!popup) throw new Error('浏览器阻止了授权弹窗，请允许弹窗后重试');

  return new Promise((resolve, reject) => {
    let done = false;
    const onMsg = (ev) => {
      const m = ev.data;
      if (!m || m.type !== 'douyin-oauth') return;
      done = true;
      window.removeEventListener('message', onMsg);
      clearInterval(pollClose);
      if (m.ok && m.token?.accessToken) {
        writeDouyinToken(m.token);
        resolve(m.token);
      } else {
        reject(new Error('抖音授权失败或被用户取消'));
      }
    };
    window.addEventListener('message', onMsg);
    const pollClose = setInterval(() => {
      if (done) return;
      if (popup.closed) {
        clearInterval(pollClose);
        window.removeEventListener('message', onMsg);
        reject(new Error('授权窗口被关闭'));
      }
    }, 700);
  });
}

/**
 * 发布到抖音：合并分镜 → 上传 TOS → 抖音上传 → 抖音发布
 * @param {number} taskId
 * @param {(stage: 'merging'|'authorizing'|'uploading'|'publishing'|'done', data?: any) => void} [onStage]
 */
export async function publishToDouyin(taskId, onStage) {
  const t = store.tasks.find((x) => x.id === taskId);
  if (!t) throw new Error('任务不存在');
  const urls = (t.scenes || []).filter((s) => s.clipUrl).map((s) => s.clipUrl);
  if (!urls.length) throw new Error('没有可合并的分镜');

  // 1. 合并 + 上传 TOS 拿公网 URL
  onStage?.('merging');
  let mergedUrl = t.mergedUrl;
  if (!mergedUrl) {
    const res = await fetch('/video/merge', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ urls, uploadTos: true }),
    });
    const data = res.ok ? await res.json() : { ok: false, error: `HTTP ${res.status}` };
    if (!data.ok) throw new Error('合并失败：' + (data.error || 'unknown'));
    mergedUrl = data.url;
    t.mergedUrl = mergedUrl;
    persist();
  }

  // 2. 确保有抖音 token（没有则弹窗授权）
  let tok = readDouyinToken();
  if (!tok?.accessToken || !tok?.openId) {
    onStage?.('authorizing');
    tok = await douyinAuthorize();
  }

  // 3. 上传到抖音
  onStage?.('uploading');
  const title = (t.title || t.userDescription || '').slice(0, 55);
  const pubRes = await fetch('/douyin/publish', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      accessToken: tok.accessToken,
      openId: tok.openId,
      videoUrl: mergedUrl,
      text: title,
    }),
  });
  const pubData = pubRes.ok ? await pubRes.json() : { ok: false, error: `HTTP ${pubRes.status}` };
  if (!pubData.ok) {
    // token 过期：清缓存让下次重新授权
    if (/access.?token|unauthor|登录|授权/i.test(pubData.error || '')) {
      if (typeof localStorage !== 'undefined') localStorage.removeItem(DOUYIN_TOKEN_KEY);
    }
    throw new Error(pubData.error || '抖音发布失败');
  }

  onStage?.('publishing');
  t.publishedToDouyin = true;
  t.douyinItemId = pubData.itemId;
  persist();

  onStage?.('done');
  return { ok: true, itemId: pubData.itemId };
}

/**
 * 「点按钮 → 抖音授权页 → 跳抖音 App 发布页用户手动点发送」混合流程
 *
 * 抖音开放平台要求：通过 schema 调起抖音 App 的发布页，必须先 OAuth 授权
 * （拿到 access_token 后抖音才认你这个 client_key 的导流），否则抖音 App
 * 会提示"未授权应用"。所以流程是：
 *   ① 合并分镜上传 TOS
 *   ② 弹抖音 OAuth 授权页 → 用户点同意 → 拿 access_token / openId
 *      （未配 DOUYIN_CLIENT_KEY 时 sidecar 返本地 demo 授权页，演示当天可走通）
 *   ③ 下载到本地相册（uni.downloadFile + uni.saveVideoToPhotosAlbum）
 *   ④ 复制标题+hashtag 到剪贴板
 *   ⑤ snssdk1128://aweme/share?client_key=…&access_token=…&title=…
 *      拉起抖音 App 到发布页（视频已在相册可选，文案已在剪贴板可粘贴）
 *   ⑥ 用户在抖音 App 内手动点「发送」完成发布
 *
 * 跟自动直发（publishToDouyin 调 create_video API）的区别：
 *   · 不需要 video.upload / video.create scope 严审 + 录屏 demo
 *   · 「分享到抖音」是默认开通能力，主体认证后即可用
 *   · 用户在抖音内点最后一下"发送"，符合抖音"用户主动发布"合规要求
 *
 * @param {number} taskId
 * @param {(stage: 'merging'|'authorizing'|'downloading'|'saving'|'launching'|'done', data?: any) => void} [onStage]
 * @param {string} [clientKey]  抖音移动应用的 client_key（拿到 KEY 后填 VITE_DOUYIN_CLIENT_KEY）
 */
export async function shareToDouyinApp(taskId, onStage, clientKey) {
  const t = store.tasks.find((x) => x.id === taskId);
  if (!t) throw new Error('任务不存在');
  const urls = (t.scenes || []).filter((s) => s.clipUrl).map((s) => s.clipUrl);
  if (!urls.length) throw new Error('没有可发布的分镜');

  // ① 合并 + 上传 TOS
  onStage?.('merging');
  let mergedUrl = t.mergedUrl;
  if (!mergedUrl) {
    const res = await fetch('/video/merge', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ urls, uploadTos: true }),
    });
    const data = res.ok ? await res.json() : { ok: false, error: `HTTP ${res.status}` };
    if (!data.ok) throw new Error('合并失败：' + (data.error || 'unknown'));
    mergedUrl = data.url;
    t.mergedUrl = mergedUrl;
    persist();
  }

  // ② 抖音 OAuth 授权（拿 access_token / openId，schema 跳转必需）
  //    Token 有缓存，授权过的商户下次直接跳 ③；未配 client_key 时走 demo 授权页
  let tok = (typeof readDouyinToken === 'function') ? readDouyinToken() : null;
  if (!tok?.accessToken || !tok?.openId) {
    onStage?.('authorizing');
    try {
      tok = await douyinAuthorize();
    } catch (e) {
      throw new Error('抖音授权失败：' + (e?.message || e));
    }
  }

  // ③ 下载到本地临时路径（uni-app API，H5 / 微信小程序通用）
  onStage?.('downloading');
  const tempPath = await new Promise((resolve, reject) => {
    uni.downloadFile({
      url: mergedUrl,
      success: (r) => (r.statusCode === 200 ? resolve(r.tempFilePath) : reject(new Error(`下载失败 ${r.statusCode}`))),
      fail: (err) => reject(new Error(err?.errMsg || '下载失败')),
    });
  });

  // ④ 保存到相册（小程序首次会触发 scope.writePhotosAlbum 授权弹窗）
  onStage?.('saving');
  let savedToAlbum = false;
  try {
    await new Promise((resolve, reject) => {
      uni.saveVideoToPhotosAlbum({
        filePath: tempPath,
        success: () => resolve(),
        fail: (err) => reject(new Error(err?.errMsg || '保存到相册失败')),
      });
    });
    savedToAlbum = true;
  } catch (e) {
    // H5 不支持 saveVideoToPhotosAlbum：降级用 <a download>
    if (typeof document !== 'undefined') {
      try {
        const blob = await fetch(mergedUrl).then((r) => r.blob());
        const objectUrl = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = objectUrl;
        a.download = `${t.title || 'tanxiaoer-video'}-${t.id}.mp4`;
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
        savedToAlbum = true;
      } catch (e2) {
        console.warn('[douyin/share] H5 download fallback 失败：', e2?.message || e2);
      }
    } else {
      console.warn('[douyin/share] saveVideoToPhotosAlbum 失败：', e?.message || e);
    }
  }

  // ⑤ 复制文案到剪贴板（用户切到抖音可直接粘贴标题 + hashtag）
  const title = (t.title || t.userDescription || '').slice(0, 55);
  const clipText = `${title}\n#摊小二 #探店`;
  try {
    await new Promise((resolve) => {
      uni.setClipboardData({
        data: clipText,
        showToast: false,
        success: () => resolve(),
        fail: () => resolve(),
      });
    });
  } catch {}

  // ⑥ 拉起抖音 App 到发布页（schema 带 client_key + access_token + openId）
  let launchedApp = false;
  if (typeof document !== 'undefined' && typeof window !== 'undefined') {
    onStage?.('launching');
    const ck = clientKey || (typeof import.meta !== 'undefined' && import.meta.env?.VITE_DOUYIN_CLIENT_KEY) || '';
    const params = new URLSearchParams();
    if (ck) params.set('client_key', ck);
    if (tok?.accessToken) params.set('access_token', tok.accessToken);
    if (tok?.openId) params.set('open_id', tok.openId);
    params.set('title', title);
    params.set('hashtag_list', '#摊小二 #探店');
    const schema = `snssdk1128://aweme/share?${params.toString()}`;

    const onVisibility = () => {
      if (document.hidden) launchedApp = true;
    };
    document.addEventListener('visibilitychange', onVisibility);
    try {
      const iframe = document.createElement('iframe');
      iframe.style.display = 'none';
      iframe.src = schema;
      document.body.appendChild(iframe);
      setTimeout(() => iframe.remove(), 2000);
    } catch {}
    await new Promise((r) => setTimeout(r, 1500));
    document.removeEventListener('visibilitychange', onVisibility);
  }

  onStage?.('done');
  return { ok: true, mergedUrl, savedToAlbum, launchedApp, clipText };
}

export const CONFIG = { MAX_SCENES };
