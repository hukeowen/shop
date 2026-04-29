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
    // 图片已上传 → 落库
    registerTaskToDB(task);
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
      syncStatusToDB(t);
      return;
    }
    t.status = 4;
    store.quota.used += 1;
    persist();
    syncStatusToDB(t);
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

const TASK_BASE = '/app-api/video/app/task';

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

/** 同步任务最终状态到 DB */
async function syncStatusToDB(task) {
  if (!task.dbId) return;
  try {
    const firstScene = (task.scenes || []).find((s) => s.clipUrl);
    await request({
      url: `${TASK_BASE}/sync-status`,
      method: 'PUT',
      data: {
        dbId: task.dbId,
        clientStatus: task.status,
        videoUrl: firstScene?.clipUrl || '',
        failReason: task.failReason || '',
      },
    });
  } catch (e) {
    console.warn('[aiVideo] syncStatusToDB 失败:', e.message);
  }
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

export async function getQuota() {
  try {
    const data = await request({ url: '/app-api/merchant/mini/video-quota/me' });
    const remaining = data?.remaining ?? 0;
    return { total: remaining, used: 0, remaining };
  } catch {
    return { ...store.quota };
  }
}

export function buyQuota() {
  return Promise.resolve({ ok: false, msg: '配额由火山方舟账户余额承担' });
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
