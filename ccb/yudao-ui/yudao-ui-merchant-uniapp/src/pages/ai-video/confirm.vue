<template>
  <view class="page">
    <view v-if="task && task.status === 1" class="state-card">
      <view class="spinner"></view>
      <view class="state-text">AI 正在看图 + 拆幕...</view>
      <view class="state-sub">每张图 1 幕 · 总长不超 30 秒</view>
    </view>

    <view v-else-if="task && task.status === 2" class="content">
      <view class="card title-card">
        <view class="title-label">视频标题</view>
        <view class="title-text">{{ task.title || '—' }}</view>
      </view>

      <view class="card">
        <view class="section-title-row">
          <view class="section-title">{{ scenes.length }} 幕脚本（每幕 {{ effDuration }} 秒，共 {{ scenes.length * effDuration }} 秒）</view>
          <view class="regen-btn" @click="onRegen" :class="{ disabled: regenerating }">
            {{ regenerating ? '生成中…' : '🔄 重新生成' }}
          </view>
        </view>
        <view class="section-sub">每一幕都用对应的实拍图做起始帧，AI 会按图里真实产品写台词。台词和画面都能改</view>

        <view class="scenes">
          <view class="scene" v-for="(s, i) in scenes" :key="i">
            <view class="scene-head">
              <view class="scene-no">{{ i + 1 }}</view>
              <view class="scene-meta">
                <view class="scene-time">{{ i * effDuration }}s - {{ (i + 1) * effDuration }}s</view>
                <view class="scene-imgtag">图 #{{ s.img_idx + 1 }}<text v-if="s.image_summary"> · {{ s.image_summary }}</text></view>
              </view>
            </view>

            <view class="scene-bigimg" @click="onReplaceImage(i)">
              <image
                class="scene-img"
                :src="task.imageUrls[s.img_idx]"
                mode="aspectFill"
              />
              <view class="scene-bigimg-mask" v-if="replacingIdx === i">
                <view class="spinner small"></view>
                <text>上传中…</text>
              </view>
              <view v-else class="scene-bigimg-hint">
                <text class="hint-icon">🔄</text>
                <text>点击换图</text>
              </view>
            </view>

            <view class="scene-field">
              <view class="fld-label">🎙 台词</view>
              <textarea
                class="fld-input"
                v-model="scenes[i].narration"
                :auto-height="true"
                :maxlength="40"
              />
            </view>

            <view class="scene-field">
              <view class="fld-label">🎬 画面</view>
              <textarea
                class="fld-input pale"
                v-model="scenes[i].visual_prompt"
                :auto-height="true"
                :maxlength="120"
              />
            </view>
          </view>
        </view>
      </view>

      <view class="actions safe-bottom">
        <button class="btn ghost" @click="back">返回</button>
        <button class="btn primary" @click="onConfirm">开始生成视频</button>
      </view>
    </view>

    <view v-else-if="task && task.status === 5" class="state-card error">
      <view class="state-icon">!</view>
      <view class="state-text">生成失败</view>
      <view class="state-sub">{{ task.failReason || '请稍后重试' }}</view>
      <button class="btn ghost solo" @click="back">返回</button>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue';
import { onLoad, onUnload } from '@dcloudio/uni-app';
import { getTask, confirmTask, regenerateScript, replaceSceneImage } from '../../api/aiVideo.js';
import { blobUrlToBase64 } from '../../api/oss.js';

const task = ref(null);
const taskId = ref(0);
const scenes = ref([]);
const regenerating = ref(false);
const replacingIdx = ref(-1);
const effDuration = computed(() => task.value?.sceneDuration || Number(import.meta.env.VITE_VIDEO_DURATION || 10));
let pollTimer = null;

async function load() {
  task.value = await getTask(taskId.value);
  if (task.value?.status === 2 && !scenes.value.length) {
    scenes.value = task.value.scenes.map((s) => ({
      img_idx: s.img_idx,
      image_summary: s.image_summary || '',
      narration: s.narration,
      visual_prompt: s.visual_prompt,
    }));
  }
  if (task.value?.status === 1) {
    pollTimer = setTimeout(load, 1500);
  }
}

async function onRegen() {
  if (regenerating.value) return;
  regenerating.value = true;
  uni.showLoading({ title: 'AI 重新构思…' });
  try {
    await regenerateScript(taskId.value);
    scenes.value = [];  // 让 load() 重新同步
    await load();
    uni.hideLoading();
    uni.showToast({ title: '已重新生成', icon: 'success' });
  } catch (e) {
    uni.hideLoading();
    uni.showModal({ title: '重新生成失败', content: e.message, showCancel: false });
  } finally {
    regenerating.value = false;
  }
}

function onReplaceImage(i) {
  if (replacingIdx.value !== -1) return;
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['camera', 'album'],
    success: async (r) => {
      const path = r.tempFilePaths?.[0];
      if (!path) return;
      replacingIdx.value = i;
      try {
        const base64 = await blobUrlToBase64(path);
        const newUrl = await replaceSceneImage({
          taskId: taskId.value,
          sceneIndex: i,
          base64,
        });
        // 同步视图：直接把 task.imageUrls 换掉就行（scene 的 img_idx 已指向该 slot）
        if (task.value?.imageUrls) {
          const slot = scenes.value[i].img_idx;
          task.value.imageUrls[slot] = newUrl;
        }
        uni.showToast({ title: '已替换', icon: 'success' });
      } catch (e) {
        uni.showModal({ title: '换图失败', content: e.message || String(e), showCancel: false });
      } finally {
        replacingIdx.value = -1;
      }
    },
  });
}

async function onConfirm() {
  uni.showLoading({ title: '启动生成' });
  try {
    await confirmTask({ taskId: taskId.value, scenes: scenes.value });
    uni.hideLoading();
    uni.redirectTo({ url: `/pages/ai-video/detail?id=${taskId.value}` });
  } catch (e) {
    uni.hideLoading();
    uni.showModal({ title: '提交失败', content: e.message, showCancel: false });
  }
}

function back() {
  uni.navigateBack();
}

onLoad((q) => {
  taskId.value = Number(q.id);
  load();
});
onUnload(() => {
  if (pollTimer) clearTimeout(pollTimer);
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  padding: 24rpx 24rpx 200rpx;
  min-height: 100vh;
}

.card {
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 32rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.title-card {
  background: linear-gradient(135deg, $brand-primary, $brand-primary-dark);
  color: #fff;

  .title-label {
    font-size: 22rpx;
    opacity: 0.8;
  }

  .title-text {
    margin-top: 8rpx;
    font-size: 36rpx;
    font-weight: 700;
  }
}

.section-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.regen-btn {
  padding: 8rpx 20rpx;
  background: $brand-primary-light;
  color: $brand-primary-dark;
  border-radius: $radius-pill;
  font-size: 22rpx;
  font-weight: 500;

  &.disabled {
    opacity: 0.5;
  }
}

.section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: $text-primary;
}

.section-sub {
  margin-top: 8rpx;
  margin-bottom: 24rpx;
  font-size: 24rpx;
  color: $text-secondary;
}

.state-card {
  margin-top: 200rpx;
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
    font-size: 34rpx;
    font-weight: 600;
    color: $text-primary;
  }

  .state-sub {
    margin-top: 12rpx;
    font-size: 26rpx;
    color: $text-secondary;
  }

  .btn.ghost.solo {
    margin-top: 40rpx;
    width: 60%;
    margin-left: auto;
    margin-right: auto;
    display: block;
  }

  &.error .state-text {
    color: $danger;
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.scenes {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.scene-bigimg {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  margin: 16rpx 0 8rpx;
  background-color: #eaecef;
  border-radius: $radius-sm;
  overflow: hidden;
  cursor: pointer;

  .scene-img {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    :deep(img) {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }

  .scene-bigimg-hint {
    position: absolute;
    right: 16rpx;
    bottom: 16rpx;
    display: flex;
    align-items: center;
    gap: 6rpx;
    padding: 8rpx 16rpx;
    background: rgba(0, 0, 0, 0.55);
    color: #fff;
    font-size: 22rpx;
    border-radius: $radius-pill;

    .hint-icon {
      font-size: 20rpx;
    }
  }

  .scene-bigimg-mask {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 12rpx;
    background: rgba(0, 0, 0, 0.55);
    color: #fff;
    font-size: 24rpx;

    .spinner.small {
      width: 48rpx;
      height: 48rpx;
      border: 4rpx solid rgba(255, 255, 255, 0.3);
      border-top-color: #fff;
      border-radius: 50%;
      animation: spin 1s linear infinite;
      margin: 0;
    }
  }
}

.scene {
  padding: 24rpx;
  background: #fafbfc;
  border-radius: $radius-md;
  border-left: 6rpx solid $brand-primary;

  .scene-head {
    display: flex;
    align-items: center;
    gap: 16rpx;
    margin-bottom: 16rpx;
  }

  .scene-no {
    width: 52rpx;
    height: 52rpx;
    line-height: 52rpx;
    text-align: center;
    background: $brand-primary;
    color: #fff;
    font-size: 26rpx;
    font-weight: 600;
    border-radius: 50%;
  }

  .scene-meta {
    display: flex;
    flex-direction: column;

    .scene-time {
      font-size: 24rpx;
      color: $text-secondary;
      font-weight: 500;
    }

    .scene-imgtag {
      font-size: 20rpx;
      color: $text-placeholder;
      margin-top: 4rpx;
    }
  }
}

.scene-field {
  margin-top: 12rpx;

  .fld-label {
    font-size: 22rpx;
    color: $text-secondary;
    margin-bottom: 6rpx;
  }

  .fld-input {
    display: block;
    width: 100%;
    min-height: 56rpx;
    padding: 12rpx 16rpx;
    background: #fff;
    border-radius: $radius-sm;
    font-size: 26rpx;
    color: $text-primary;
    line-height: 1.5;
    box-sizing: border-box;

    &.pale {
      color: $text-regular;
      font-size: 24rpx;
    }
  }
}

.actions {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  gap: 16rpx;
  padding: 24rpx 32rpx calc(env(safe-area-inset-bottom) + 24rpx);
  background: #fff;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.05);
}

.btn {
  flex: 1;
  height: 96rpx;
  line-height: 96rpx;
  font-size: 30rpx;
  border-radius: $radius-md;

  &.primary {
    background: $brand-primary;
    color: #fff;
  }

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
