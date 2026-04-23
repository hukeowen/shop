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

import { uploadImage, uploadImages } from './oss.js';
import { generateScript } from './scriptLlm.js';
import { createClipTask, waitClip } from './jimeng.js';
import { findVoice } from './voice.js';

const MAX_SCENES = 6;
const MAX_TOTAL_SEC = 30;

// 幕数 = 图片数（cap 6），每幕独占一张图
function sceneCountFor(imageCount) {
  return Math.max(1, Math.min(MAX_SCENES, imageCount || 1));
}
// 每幕时长：≤3 图用 10s，≥4 图降到 5s，保证总长 ≤ 30s
function sceneDurationFor(imageCount) {
  return imageCount <= 3 ? 10 : 5;
}
function totalSecFor(imageCount) {
  return Math.min(MAX_TOTAL_SEC, sceneCountFor(imageCount) * sceneDurationFor(imageCount));
}

const STORAGE_KEY = 'ai-video-tasks-v1';

const store = {
  nextId: 100001,
  quota: { total: 999, used: 0 },
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
        return;
      }
      try {
        scene.status = 'video_running';
        const rawUrl = await waitClip(scene.clipTaskId, { intervalMs: 4000 });
        if (scene.narration?.trim()) {
          scene.status = 'audio_muxing';
          try {
            const v = findVoice(t.voiceKey);
            const muxRes = await fetch('/video/mux', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ videoUrl: rawUrl, text: scene.narration, voice: v.ark, duration: scene.duration || t.sceneDuration || 10 }),
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
      } catch (e) {
        scene.error = 'video: ' + e.message;
        scene.status = 'video_failed';
        if (scene.index === 0 && t.status === 3) t.status = 4;
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
    scenes: [],            // [{ img_idx, narration, visual_prompt, clipTaskId, clipUrl, audioUrl, status }]
    progress: { total: 0, done: 0 },  // confirmTask 时根据实际幕数再设
    coverUrl: null,
    publishedToDouyin: false,
    failReason: null,
    ...init,
  };
  store.tasks.unshift(t);
  persist();
  return t;
}

/**
 * 步骤①②：上传 + LLM 拆幕（阻塞，通常 10-20 秒）
 * @returns taskId
 */
export async function createTask({ imageBase64s, userDescription, voiceKey, ratio, shopName }) {
  const imgs = (imageBase64s || []).filter(Boolean);
  if (!imgs.length) throw new Error('请至少上传 1 张图片');

  const task = newTask({ userDescription, voiceKey, ratio: ratio || '9:16', shopName: shopName || '' });

  try {
    // ① 上传到 OSS
    task.imageUrls = await uploadImages(imgs);
    task.coverUrl = task.imageUrls[0];

    // ② LLM 拆 N 幕（视觉模型，真的看到每张图；幕数 = 图数，每幕 1:1 用对应图）
    const sceneCount = sceneCountFor(imgs.length);
    const perSceneDuration = sceneDurationFor(imgs.length);
    task.sceneDuration = perSceneDuration;
    task.totalDuration = sceneCount * perSceneDuration;
    const { title, scenes } = await generateScript({
      imageCount: imgs.length,
      imageUrls: task.imageUrls,
      userDescription,
      sceneCount,
      sceneDuration: perSceneDuration,
    });
    task.title = title;
    task.scenes = scenes.map((s, i) => {
      const isEndCard = i === scenes.length - 1;
      return {
        index: i,
        img_idx: s.img_idx,
        image_summary: s.image_summary || '',
        narration: s.narration,
        visual_prompt: s.visual_prompt,
        // 最后一幕是端卡：3s 静止图 + 大二维码 + 店名，跳过即梦AI 生成
        duration: isEndCard ? 3 : perSceneDuration,
        isEndCard,
        status: 'pending',
        clipTaskId: null,
        clipUrl: null,
        audioUrl: null,
        audioSource: null,
        error: null,
      };
    });
    task.status = 2;
  } catch (e) {
    task.status = 5;
    task.failReason = e.message;
  }
  persist();
  return task.id;
}

export function getTask(id) {
  const t = store.tasks.find((x) => x.id === id);
  return Promise.resolve(t ? { ...t, scenes: t.scenes.map((s) => ({ ...s })) } : null);
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
  const newUrl = await uploadImage(base64);
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
    t.scenes = scenes.map((s, i) => {
      const isEndCard = i === scenes.length - 1;
      return {
        index: i,
        img_idx: s.img_idx,
        image_summary: s.image_summary || '',
        narration: s.narration,
        visual_prompt: s.visual_prompt,
        duration: isEndCard ? 3 : perSceneDuration,
        isEndCard,
        status: 'pending',
        clipTaskId: null,
        clipUrl: null,
        audioUrl: null,
        audioSource: null,
        error: null,
      };
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
export async function confirmTask({ taskId, scenes }) {
  const t = store.tasks.find((x) => x.id === taskId);
  if (!t) throw new Error('任务不存在');

  if (Array.isArray(scenes) && scenes.length) {
    t.scenes = t.scenes.map((s, i) => ({
      ...s,
      narration: scenes[i]?.narration || s.narration,
      visual_prompt: scenes[i]?.visual_prompt || s.visual_prompt,
    }));
  }

  t.status = 3;
  t.progress = { total: t.scenes.length, done: 0 };
  persist();

  // 后台跑：每幕独立生成（幕 i 起始帧 = 图 i），所有幕并行
  // TTS 在 runClip 内部与视频生成串行，视频就绪后立即合流
  (async () => {
    // 每幕错开 4 秒提交，避免同时撞即梦AI 并发限制；runClip 内部也有退避重试兜底
    const clipTasks = t.scenes.map((scene, i) => {
      const selfImg = t.imageUrls[scene.img_idx] || t.imageUrls[i] || t.imageUrls[0];
      return new Promise((r) => setTimeout(r, i * 4000)).then(() => runClip(t, scene, selfImg));
    });
    await Promise.all(clipTasks);

    const okClips = t.scenes.filter((s) => s.clipUrl).length;
    if (okClips === 0) {
      t.status = 5;
      // 把首个分镜的错误透给顶层 failReason，便于用户看到真正原因
      const firstErr = t.scenes.find((s) => s.error)?.error;
      t.failReason = firstErr ? `全部分镜失败：${firstErr}` : '全部分镜失败，请重试';
      persist();
      return;
    }
    t.status = 4;
    store.quota.used += 1;
    persist();
  })();

  return true;
}

/** 单段视频：用指定起始图 + prompt 跑即梦AI；视频就绪后立即合入 TTS 配音 */
async function runClip(task, scene, startImageUrl) {
  try {
    scene.startImageUrl = startImageUrl;

    // 端卡不走即梦AI：静止商品图 + 正中大二维码 + 店名，直接 sidecar 合成
    if (scene.isEndCard) {
      scene.status = 'endcard_building';
      const v = findVoice(task.voiceKey);
      const endRes = await fetch('/video/endcard', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          imageUrl: startImageUrl,
          shopName: task.shopName || '',
          text: scene.narration,
          voice: v.ark,
          duration: scene.duration || 3,
        }),
      });
      const endData = endRes.ok ? await endRes.json() : { ok: false, error: `HTTP ${endRes.status}` };
      if (!endData.ok) throw new Error('endcard: ' + (endData.error || 'unknown'));
      scene.clipUrl = endData.url;
      task.progress.done += 1;
      scene.status = 'ready';
      if (scene.index === 0 && task.status === 3) task.status = 4;
      persist();
      return true;
    }

    scene.status = 'video_creating';
    scene.clipTaskId = await createClipTask({
      imageUrl: startImageUrl,
      prompt: scene.visual_prompt || scene.narration,
      ratio: task.ratio,
      duration: scene.duration || task.sceneDuration || 10,
    });
    scene.status = 'video_running';
    const rawUrl = await waitClip(scene.clipTaskId, { intervalMs: 4000 });

    // 视频就绪后合入配音（sidecar 内完成：TTS + FFmpeg mux + 上传 TOS）
    if (scene.narration?.trim()) {
      scene.status = 'audio_muxing';
      try {
        const v = findVoice(task.voiceKey);
        const muxRes = await fetch('/video/mux', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ videoUrl: rawUrl, text: scene.narration, voice: v.ark, duration: scene.duration || task.sceneDuration || 10 }),
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
    return true;
  } catch (e) {
    scene.error = 'video: ' + e.message;
    scene.status = 'video_failed';
    console.warn(`[scene ${scene.index}] 视频失败:`, e.message);
    if (scene.index === 0 && task.status === 3) task.status = 4;
    persist();
    return false;
  }
}

export function getTaskPage() {
  return Promise.resolve({ total: store.tasks.length, list: [...store.tasks] });
}

export function getQuota() {
  return Promise.resolve({ ...store.quota });
}

export function buyQuota() {
  return Promise.resolve({ ok: false, msg: '配额由火山方舟账户余额承担' });
}

export function getDouyinAuthUrl() {
  return Promise.resolve({ url: 'https://open.douyin.com/platform/oauth/connect?mock=1' });
}

/**
 * 发布到抖音：合并所有分镜 → 上传 TOS → 触发抖音发布
 * （H5 原型阶段：真正的 OAuth + PublishVideo 接后端再走 Spring Boot；此处先打通合并+拿 URL 的链路）
 * @param {number} taskId
 * @param {(stage: 'merging'|'uploading'|'publishing'|'done', data?: any) => void} [onStage]
 */
export async function publishToDouyin(taskId, onStage) {
  const t = store.tasks.find((x) => x.id === taskId);
  if (!t) throw new Error('任务不存在');
  const urls = (t.scenes || []).filter((s) => s.clipUrl).map((s) => s.clipUrl);
  if (!urls.length) throw new Error('没有可合并的分镜');

  onStage?.('merging');
  const res = await fetch('/video/merge', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ urls, uploadTos: true }),
  });
  const data = res.ok ? await res.json() : { ok: false, error: `HTTP ${res.status}` };
  if (!data.ok) throw new Error('合并失败：' + (data.error || 'unknown'));

  onStage?.('uploading', { mergedUrl: data.url, size: data.size });
  t.mergedUrl = data.url;

  // TODO: 接后端 POST /merchant/mini/ai-video/douyin/publish?taskId=xxx
  // 现在 H5 原型阶段，仅落本地标记
  onStage?.('publishing');
  await new Promise((r) => setTimeout(r, 600));
  t.publishedToDouyin = true;
  persist();

  onStage?.('done', { mergedUrl: data.url });
  return { ok: true, mergedUrl: data.url };
}

export const CONFIG = { MAX_SCENES };
