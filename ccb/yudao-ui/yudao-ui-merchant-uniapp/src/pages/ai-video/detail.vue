<template>
  <view class="page">
    <!-- 任务丢失（HMR / 浏览器刷新后 in-memory store 被重置） -->
    <view v-if="loaded && !task" class="state-card error">
      <view class="state-icon">?</view>
      <view class="state-text">任务信息已丢失</view>
      <view class="state-sub">开发模式下 HMR / 刷新会清空内存任务，请重新创建</view>
      <button class="btn ghost" @click="retry">重新创建</button>
    </view>

    <view v-else-if="task">
    <!-- 还在拆脚本 / 等确认 （一般不会走到 detail，兜底兜一下）-->
    <view v-if="task.status === 1 || task.status === 2" class="state-card">
      <view class="spinner"></view>
      <view class="state-text">脚本阶段</view>
      <view class="state-sub">回确认页继续</view>
      <button class="btn ghost" @click="gotoConfirm">去确认</button>
    </view>

    <!-- 生成中 -->
    <view v-else-if="task.status === 3" class="state-card">
      <view class="spinner"></view>
      <view class="state-text">正在生成 {{ task.scenes?.length || scenes }} 幕视频 + 配音</view>
      <view class="state-sub">末帧链式衔接 · 通常 3-5 分钟</view>

      <view class="progress-bar">
        <view class="progress-fill" :style="{ width: overallProgress + '%' }"></view>
      </view>
      <view class="progress-label">{{ task.progress?.done || 0 }} / {{ task.progress?.total || scenes * 2 }} 子任务</view>

      <view class="scene-status-list">
        <view
          v-for="(s, i) in task.scenes"
          :key="i"
          class="ssl-wrap"
        >
          <view class="ssl-item" :class="sceneStatusClass(s)">
            <text class="ssl-idx">{{ i + 1 }}</text>
            <text class="ssl-narr">{{ s.narration }}</text>
            <text class="ssl-tag">{{ sceneStatusLabel(s) }}</text>
          </view>
          <view v-if="s.error" class="ssl-err">⚠ {{ s.error }}</view>
        </view>
      </view>
    </view>

    <!-- 全部就绪 -->
    <view v-else-if="task.status === 4" class="content">
      <view v-if="!playableScenes.length" class="state-card error">
        <view class="state-icon">!</view>
        <view class="state-text">没有可播放的分镜</view>
        <view class="state-sub">全部 {{ scenes }} 段都失败了，试试重新生成</view>
        <button class="btn ghost" @click="retry">重新创建</button>
      </view>

      <view v-else class="video-box">
        <video
          id="mainVideo"
          :key="'v' + currentIdx"
          :src="playableScenes[currentIdx]?.clipUrl"
          :poster="task.coverUrl"
          :autoplay="true"
          :show-center-play-btn="false"
          :show-fullscreen-btn="true"
          object-fit="contain"
          class="video"
          @play="onVideoPlay"
          @ended="onClipEnded"
          @error="onClipError"
        />
        <view class="video-overlay" @click="togglePlay">
          <view v-if="!playing" class="play-big">▶</view>
        </view>

        <view class="seg-bar">
          <view
            v-for="(s, i) in playableScenes"
            :key="i"
            class="seg"
            :class="{ active: i === currentIdx, played: i < currentIdx }"
          ></view>
        </view>

        <view class="audio-badge">
          <text>🔊 {{ voiceDisplay }} · 第 {{ currentIdx + 1 }}/{{ playableScenes.length }} 段</text>
        </view>
      </view>

      <view class="card title-card">
        <view class="title-text">{{ task.title }}</view>
        <view class="title-sub">
          {{ playableScenes.length }}/{{ task.scenes.length }} 幕可播放 · 共 {{ playableScenes.length * duration }} 秒
        </view>
      </view>

      <view class="card">
        <view class="section-title">分镜台词</view>
        <view class="rl-wrap" v-for="(s, i) in task.scenes" :key="i">
          <view class="rl" :class="{ playing: i === currentIdx && playing, failed: !s.clipUrl }">
            <text class="rl-no">{{ i + 1 }}</text>
            <text class="rl-text">{{ s.narration }}</text>
            <text v-if="!s.clipUrl" class="rl-badge">失败</text>
          </view>
          <view v-if="s.error" class="rl-err">⚠ {{ s.error }}</view>
        </view>
      </view>

      <!-- 端卡海报（即梦 CV 生成 1080×1920 海报，单独下载分享朋友圈用） -->
      <view class="card poster-card">
        <view class="section-title-row">
          <view class="section-title">🖼 店铺海报</view>
          <view
            v-if="task.posterUrl && !posterLoading"
            class="regen-btn"
            @click="onPoster(true)"
          >🔄 重新生成</view>
        </view>
        <view class="poster-sub">独立的店铺海报，可单独下载发朋友圈/微信群</view>

        <view v-if="posterLoading" class="poster-state">
          <view class="spinner small"></view>
          <text>AI 正在生成海报…通常 20-40 秒</text>
        </view>
        <view v-else-if="task.posterUrl" class="poster-box">
          <image
            class="poster-img"
            :src="task.posterUrl"
            mode="aspectFit"
            @click="previewPoster"
          />
          <view class="poster-actions">
            <button class="btn ghost" @click="downloadPoster">
              {{ savingPoster ? '保存中…' : '保存海报到相册' }}
            </button>
          </view>
        </view>
        <view v-else-if="posterError" class="poster-state error">
          <text class="poster-err-text">⚠ {{ posterError }}</text>
          <button class="btn ghost solo" @click="onPoster(true)">重试</button>
        </view>
        <view v-else class="poster-state">
          <button class="btn primary solo" @click="onPoster(false)">生成专业海报</button>
        </view>
      </view>

      <view class="actions-grid">
        <view class="act" @click="onReplay">
          <view class="act-icon rp">↺</view>
          <text>重新播放</text>
        </view>
        <view class="act" @click="onMergeDownload" :class="{ disabled: merging }">
          <view class="act-icon mg">⇩</view>
          <text>{{ merging ? '合并中…' : '合并下载 MP4' }}</text>
        </view>
        <view class="act" @click="onPublishDouyin" :class="{ disabled: publishing || task.publishedToDouyin }">
          <view class="act-icon dy">♪</view>
          <text>{{ publishLabel }}</text>
        </view>
      </view>

      <view class="card tips">
        <view class="tip-title">💡 这是无需 FFmpeg 的"前端拼接"播放</view>
        <view class="tip-item">{{ task.scenes.length }} 段 × {{ duration }}s 视频 + 同步语音，每段用对应图独立生成</view>
        <view class="tip-item">分享/下载成单一 mp4 需要 FFmpeg，后面上服务器再加</view>
      </view>
    </view>

    <!-- 失败 -->
    <view v-else-if="task.status === 5" class="state-card error">
      <view class="state-icon">!</view>
      <view class="state-text">生成失败</view>
      <view class="state-sub">{{ task.failReason || '请稍后重试' }}</view>
      <button class="btn ghost" @click="retry">重新创建</button>
    </view>
    </view>
  </view>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue';
import { onLoad, onUnload } from '@dcloudio/uni-app';
import { getTask, shareToDouyinApp, ensurePosterForTask } from '../../api/aiVideo.js';
import { findVoice } from '../../api/voice.js';

const task = ref(null);
const loaded = ref(false);
const taskId = ref(0);
const currentIdx = ref(0);
const playing = ref(false);
const merging = ref(false);
const publishing = ref(false);
const posterLoading = ref(false);
const posterError = ref('');
const savingPoster = ref(false);
const publishStage = ref('');  // 'merging' | 'uploading' | 'publishing' | 'done'
const duration = computed(() => task.value?.sceneDuration || Number(import.meta.env.VITE_VIDEO_DURATION || 10));
const scenes = Number(import.meta.env.VITE_VIDEO_SCENES || 3);

let pollTimer = null;

const playableScenes = computed(
  () => (task.value?.scenes || []).filter((s) => s.clipUrl)
);
const currentClip = computed(() => playableScenes.value[currentIdx.value] || null);

const overallProgress = computed(() => {
  const total = task.value?.progress?.total || scenes * 2;
  const done = task.value?.progress?.done || 0;
  return Math.min(100, Math.round((done / total) * 100));
});

const voiceDisplay = computed(() => {
  const v = findVoice(task.value?.voiceKey);
  return v ? v.name : '—';
});

async function load() {
  task.value = await getTask(taskId.value);
  loaded.value = true;
  const t = task.value;
  if (!t) return;
  // 只要还有幕在跑就继续轮询（status=3 初始 / status=4 但还有未完成的幕）
  const stillWorking =
    t.status === 3 ||
    (t.status === 4 &&
      t.scenes?.some((s) => !['ready', 'video_only', 'video_failed'].includes(s.status)));
  if (stillWorking) {
    pollTimer = setTimeout(load, 3000);
  }
}

function sceneStatusClass(s) {
  return {
    done: s.status === 'ready' || s.status === 'video_only',
    running: s.status === 'video_running' || s.status === 'video_creating',
    fail: s.status === 'video_failed',
  };
}
function sceneStatusLabel(s) {
  switch (s.status) {
    case 'ready':
      return '✓ 完成';
    case 'video_only':
      return '✓ 视频';
    case 'video_creating':
      return '排队';
    case 'video_running':
      return '生成中';
    case 'audio_muxing':
      return '合音中';
    case 'video_failed':
      return '失败';
    default:
      return '等待';
  }
}

function togglePlay() {
  playing.value = !playing.value;
  if (playing.value) {
    playCurrent();
  } else {
    try { uni.createVideoContext('mainVideo').pause(); } catch {}
  }
}

function onVideoPlay() {
  playing.value = true;
}

function onClipError(e) {
  console.warn('clip error', e);
  onClipEnded();
}

/** 主动播当前段 —— uni-app H5 的 <video autoplay> 在 :key 重挂后不稳，得手动开 */
function playCurrent() {
  nextTick(() => {
    try {
      const ctx = uni.createVideoContext('mainVideo');
      ctx && ctx.play && ctx.play();
    } catch {
      // noop
    }
    // Fallback for H5 when uni video ctx 不可用
    if (typeof document !== 'undefined') {
      const el = document.querySelector('#mainVideo video, uni-video video, .video-box video');
      if (el && el.play) el.play().catch(() => {});
    }
  });
}

/** 等待后台生成的下一段出现；最多 3 分钟 */
async function waitForNextClip() {
  const allLen = task.value?.scenes?.length || 0;
  const haveLen = playableScenes.value.length;
  if (haveLen >= allLen) return;
  const deadline = Date.now() + 180_000;
  while (Date.now() < deadline) {
    const fresh = await getTask(taskId.value);
    if (fresh) task.value = fresh;
    if (playableScenes.value.length > haveLen) return;
    if (task.value?.status === 5) return;
    await new Promise((r) => setTimeout(r, 1500));
  }
}

async function onClipEnded() {
  const allLen = task.value?.scenes?.length || 0;
  // 下一段还没生成好？等它。
  if (currentIdx.value >= playableScenes.value.length - 1 && playableScenes.value.length < allLen) {
    await waitForNextClip();
  }
  const total = playableScenes.value.length;
  if (currentIdx.value < total - 1) {
    currentIdx.value += 1;  // watch(currentIdx) 会接手 playCurrent
  } else {
    playing.value = false;
    currentIdx.value = 0;
  }
}

function onReplay() {
  currentIdx.value = 0;
  playing.value = true;
  playCurrent();
}

// ============ 端卡海报 ============
async function onPoster(force) {
  if (posterLoading.value) return;
  posterLoading.value = true;
  posterError.value = '';
  try {
    const url = await ensurePosterForTask(taskId.value, { force });
    // 同步到本地 task 引用，让 UI 立刻刷新
    if (task.value) task.value.posterUrl = url;
  } catch (e) {
    posterError.value = e.message || '海报生成失败';
  } finally {
    posterLoading.value = false;
  }
}

function previewPoster() {
  if (!task.value?.posterUrl) return;
  uni.previewImage({ urls: [task.value.posterUrl], current: task.value.posterUrl });
}

async function downloadPoster() {
  if (!task.value?.posterUrl || savingPoster.value) return;
  savingPoster.value = true;
  try {
    const dl = await new Promise((resolve, reject) => {
      uni.downloadFile({
        url: task.value.posterUrl,
        success: (r) => (r.statusCode === 200 ? resolve(r) : reject(new Error('下载 HTTP ' + r.statusCode))),
        fail: (e) => reject(new Error(e?.errMsg || '下载失败')),
      });
    });
    await new Promise((resolve, reject) => {
      uni.saveImageToPhotosAlbum({
        filePath: dl.tempFilePath,
        success: () => resolve(),
        fail: (e) => reject(new Error(e?.errMsg || '保存失败')),
      });
    });
    uni.showToast({ title: '已保存到相册', icon: 'success' });
  } catch (e) {
    uni.showModal({ title: '保存失败', content: e.message || '未知错误', showCancel: false });
  } finally {
    savingPoster.value = false;
  }
}

const publishLabel = computed(() => {
  if (task.value?.publishedToDouyin) return '已发布到抖音';
  if (!publishing.value) return '发布到抖音';
  switch (publishStage.value) {
    case 'merging': return '合并视频中…';
    case 'authorizing': return '等待抖音授权…';
    case 'downloading': return '下载视频…';
    case 'saving': return '保存到相册…';
    case 'launching': return '拉起抖音 App…';
    default: return '处理中…';
  }
});

async function onPublishDouyin() {
  if (publishing.value || task.value?.publishedToDouyin) return;
  if (!playableScenes.value.length) {
    uni.showToast({ title: '没有可发布的分镜', icon: 'none' });
    return;
  }
  const confirm = await new Promise((r) =>
    uni.showModal({
      title: '发布到抖音',
      content:
        '将弹出抖音授权页，授权后跳转到抖音 App 的发布页面，视频和文案已预填，你只需在抖音里点「发送」。',
      confirmText: '继续',
      success: (x) => r(x.confirm),
      fail: () => r(false),
    })
  );
  if (!confirm) return;
  publishing.value = true;
  publishStage.value = 'merging';
  uni.showLoading({ title: publishLabel.value, mask: true });
  try {
    const ret = await shareToDouyinApp(taskId.value, (stage) => {
      publishStage.value = stage;
      if (stage === 'authorizing') uni.hideLoading();
      else uni.showLoading({ title: publishLabel.value, mask: true });
    });
    uni.hideLoading();
    if (!ret?.launchedApp) {
      const tip = ret?.savedToAlbum
        ? '抖音授权完成，视频已保存到相册。\n请打开抖音 App →「+」→ 相册 → 选最新视频 → 长按粘贴文案 → 发送'
        : '抖音授权完成。视频已合成（可在播放页长按保存），请到抖音 App 手动选相册视频发布。';
      uni.showModal({ title: '下一步：去抖音点发送', content: tip, showCancel: false });
    }
  } catch (e) {
    uni.hideLoading();
    uni.showModal({ title: '操作失败', content: e.message || String(e), showCancel: false });
  } finally {
    publishing.value = false;
    publishStage.value = '';
  }
}

async function onMergeDownload() {
  if (merging.value) return;
  const urls = (task.value?.scenes || []).filter((s) => s.clipUrl).map((s) => s.clipUrl);
  if (!urls.length) {
    uni.showToast({ title: '没有可合并的分镜', icon: 'none' });
    return;
  }
  merging.value = true;
  uni.showLoading({ title: '合成中…1-2 分钟', mask: true });
  try {
    const res = await fetch('/video/merge', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ urls }),
    });
    if (!res.ok) {
      const errTxt = await res.text();
      throw new Error(errTxt.slice(0, 300));
    }
    const blob = await res.blob();
    const objectUrl = URL.createObjectURL(blob);
    // H5 触发下载
    const a = document.createElement('a');
    a.href = objectUrl;
    a.download = `${task.value?.title || 'video'}-${task.value?.id}.mp4`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
    uni.hideLoading();
    uni.showToast({ title: '已开始下载', icon: 'success' });
  } catch (e) {
    uni.hideLoading();
    uni.showModal({ title: '合并失败', content: e.message || String(e), showCancel: false });
  } finally {
    merging.value = false;
  }
}

function retry() {
  uni.redirectTo({ url: '/pages/ai-video/create' });
}

function gotoConfirm() {
  uni.redirectTo({ url: `/pages/ai-video/confirm?id=${taskId.value}` });
}

watch(
  () => task.value?.status,
  (s, prev) => {
    // 从生成中（3）首次进入可播（4），自动开播
    if (s === 4 && prev !== 4) {
      currentIdx.value = 0;
      playing.value = true;
      playCurrent();
    }
  }
);

// currentIdx 切换时主动触发新段播放（autoplay 不稳的兜底）
watch(currentIdx, (nv, ov) => {
  if (nv === ov) return;
  if (!playing.value) return;
  playCurrent();
});

onLoad((q) => {
  // B 改造 Step 4.6：兼容 'db_123' 字符串 id（来自历史页后端任务）和数字 id（本地 store 任务）
  // getTask 内部会按形态分别走本地/后端兜底
  const raw = q.id;
  taskId.value = (typeof raw === 'string' && raw.startsWith('db_')) ? raw : Number(raw);
  load();
});

onUnload(() => {
  if (pollTimer) clearTimeout(pollTimer);
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  padding: 0 24rpx 48rpx;
  min-height: 100vh;
}

.state-card {
  margin-top: 80rpx;
  text-align: center;
  padding: 48rpx 32rpx;
  background: $bg-card;
  border-radius: $radius-lg;

  .spinner {
    width: 80rpx;
    height: 80rpx;
    margin: 0 auto 32rpx;
    border: 6rpx solid $brand-primary-light;
    border-top-color: $brand-primary;
    border-radius: 50%;
    animation: spin 1s linear infinite;
  }

  .state-icon {
    width: 80rpx;
    height: 80rpx;
    line-height: 80rpx;
    margin: 0 auto 20rpx;
    background: $danger;
    color: #fff;
    font-size: 48rpx;
    font-weight: 700;
    border-radius: 50%;
  }

  .state-text {
    font-size: 32rpx;
    font-weight: 600;
    color: $text-primary;
  }

  .state-sub {
    margin-top: 12rpx;
    font-size: 24rpx;
    color: $text-secondary;
  }

  &.error .state-text {
    color: $danger;
  }

  .btn.ghost {
    margin-top: 40rpx;
    width: 60%;
    margin-left: auto;
    margin-right: auto;
    display: block;
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.progress-bar {
  margin: 32rpx auto 8rpx;
  max-width: 500rpx;
  height: 12rpx;
  background: $brand-primary-light;
  border-radius: 6rpx;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: $brand-primary;
  transition: width 0.3s;
}

.progress-label {
  font-size: 22rpx;
  color: $text-secondary;
}

.scene-status-list {
  margin-top: 32rpx;
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 8rpx;

  .ssl-item {
    display: flex;
    align-items: center;
    gap: 12rpx;
    padding: 12rpx 16rpx;
    background: #fafbfc;
    border-radius: $radius-sm;
    font-size: 24rpx;
    color: $text-regular;

    .ssl-idx {
      flex-shrink: 0;
      width: 36rpx;
      height: 36rpx;
      line-height: 36rpx;
      text-align: center;
      background: $text-placeholder;
      color: #fff;
      border-radius: 50%;
      font-size: 20rpx;
    }

    .ssl-narr {
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .ssl-tag {
      flex-shrink: 0;
      font-size: 20rpx;
      color: $text-placeholder;
    }

    &.running {
      background: #fff8ef;

      .ssl-idx {
        background: $warning;
      }
      .ssl-tag {
        color: $warning;
      }
    }

    &.done {
      .ssl-idx {
        background: $success;
      }
      .ssl-tag {
        color: $success;
      }
    }

    &.fail {
      .ssl-idx {
        background: $danger;
      }
      .ssl-tag {
        color: $danger;
      }
    }
  }

  .ssl-wrap {
    display: flex;
    flex-direction: column;
    gap: 4rpx;
  }

  .ssl-err {
    padding: 8rpx 16rpx 10rpx 64rpx;
    font-size: 22rpx;
    color: $danger;
    line-height: 1.4;
    word-break: break-all;
  }
}

.rl-wrap {
  display: flex;
  flex-direction: column;
}

.rl-err {
  padding: 4rpx 0 10rpx 56rpx;
  font-size: 22rpx;
  color: $danger;
  line-height: 1.4;
  word-break: break-all;
}

.video-box {
  position: relative;
  margin: 24rpx 0;
  background: #000;
  border-radius: $radius-lg;
  overflow: hidden;

  .video {
    width: 100%;
    height: 900rpx;
    background: #000;
  }

  .video-overlay {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    pointer-events: none;

    .play-big {
      pointer-events: auto;
      width: 140rpx;
      height: 140rpx;
      line-height: 140rpx;
      text-align: center;
      background: rgba(0, 0, 0, 0.55);
      color: #fff;
      font-size: 60rpx;
      border-radius: 50%;
    }
  }

  .seg-bar {
    position: absolute;
    top: 16rpx;
    left: 16rpx;
    right: 16rpx;
    display: flex;
    gap: 6rpx;

    .seg {
      flex: 1;
      height: 6rpx;
      background: rgba(255, 255, 255, 0.3);
      border-radius: 3rpx;

      &.played {
        background: rgba(255, 255, 255, 0.7);
      }

      &.active {
        background: $brand-primary;
      }
    }
  }

  .audio-badge {
    position: absolute;
    bottom: 16rpx;
    right: 16rpx;
    padding: 8rpx 20rpx;
    background: rgba(0, 0, 0, 0.55);
    color: #fff;
    border-radius: $radius-pill;
    font-size: 22rpx;
  }
}

.title-card {
  background: linear-gradient(135deg, $brand-primary, $brand-primary-dark);
  color: #fff;

  .title-text {
    font-size: 32rpx;
    font-weight: 700;
  }
  .title-sub {
    margin-top: 8rpx;
    font-size: 22rpx;
    opacity: 0.85;
  }
}

.card {
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 32rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 20rpx;
}

.section-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;

  .section-title {
    margin-bottom: 0;
  }

  .regen-btn {
    padding: 6rpx 18rpx;
    background: $brand-primary-light;
    color: $brand-primary;
    border-radius: 999rpx;
    font-size: 22rpx;
    font-weight: 500;
  }
}

// 全局小号 spinner（poster-card 等卡内 inline 等待用）
.spinner.small {
  width: 36rpx;
  height: 36rpx;
  margin: 0 auto 12rpx;
  border: 4rpx solid $brand-primary-light;
  border-top-color: $brand-primary;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.poster-card {
  .poster-sub {
    margin-top: 8rpx;
    font-size: 22rpx;
    color: $text-secondary;
  }
  .poster-state {
    margin-top: 24rpx;
    padding: 32rpx 0;
    text-align: center;
    color: $text-secondary;
    font-size: 24rpx;

    &.error {
      .poster-err-text {
        display: block;
        color: $danger;
        margin-bottom: 16rpx;
      }
    }
  }
  .poster-box {
    margin-top: 20rpx;
  }
  .poster-img {
    width: 100%;
    aspect-ratio: 9 / 16;
    border-radius: $radius-md;
    background: #f0f1f4;
  }
  .poster-actions {
    margin-top: 20rpx;
    display: flex;
    gap: 16rpx;

    .btn {
      flex: 1;
    }
  }
  .btn.solo {
    width: 60%;
    margin: 0 auto;
    display: block;
  }
}

.rl {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  padding: 14rpx 0;
  font-size: 28rpx;
  color: $text-regular;
  line-height: 1.6;
  border-bottom: 1rpx solid #f0f1f4;

  &:last-child {
    border-bottom: none;
  }

  .rl-no {
    flex-shrink: 0;
    width: 40rpx;
    height: 40rpx;
    line-height: 40rpx;
    text-align: center;
    background: $text-placeholder;
    color: #fff;
    font-size: 20rpx;
    border-radius: 50%;
  }

  .rl-text {
    flex: 1;
  }

  &.playing {
    .rl-no {
      background: $brand-primary;
    }
    .rl-text {
      color: $brand-primary-dark;
      font-weight: 500;
    }
  }

  &.failed {
    opacity: 0.6;
    .rl-no {
      background: $danger;
    }
  }

  .rl-badge {
    flex-shrink: 0;
    padding: 4rpx 12rpx;
    background: $danger;
    color: #fff;
    font-size: 20rpx;
    border-radius: $radius-pill;
  }
}

.actions-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 16rpx;
  margin-bottom: 20rpx;

  .act {
    background: $bg-card;
    border-radius: $radius-lg;
    padding: 28rpx 16rpx;
    text-align: center;
    font-size: 24rpx;
    color: $text-regular;
    box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);

    .act-icon {
      width: 72rpx;
      height: 72rpx;
      line-height: 72rpx;
      margin: 0 auto 12rpx;
      color: #fff;
      border-radius: 50%;
      font-size: 32rpx;

      &.rp {
        background: #10b981;
      }
      &.mg {
        background: #ff6b35;
      }
      &.dy {
        background: linear-gradient(135deg, #fe2c55 0%, #25f4ee 100%);
      }
    }

    &.disabled {
      opacity: 0.55;
      pointer-events: none;
    }
  }
}

.tips {
  background: #fff8ef;
  box-shadow: none;

  .tip-title {
    font-size: 26rpx;
    font-weight: 600;
    color: $warning;
    margin-bottom: 10rpx;
  }

  .tip-item {
    font-size: 22rpx;
    color: $text-regular;
    line-height: 1.8;
  }
}

.btn {
  height: 96rpx;
  line-height: 96rpx;
  padding: 0 40rpx;
  font-size: 30rpx;
  border-radius: $radius-md;

  &.ghost {
    background: #fff;
    color: $brand-primary;
    border: 2rpx solid $brand-primary;
  }

  &::after {
    border: none;
  }
}
</style>
