<template>
  <view class="page" v-if="task">
    <view v-if="task.status === 3" class="state-card">
      <view class="spinner"></view>
      <view class="state-text">视频合成中...</view>
      <view class="state-sub">通常 1-3 分钟，可离开本页，完成后会收到通知</view>
      <view class="poll-progress">
        <view class="poll-fill" :style="{ width: progress + '%' }"></view>
      </view>
    </view>

    <view v-else-if="task.status === 4" class="content">
      <view class="video-box">
        <video
          :src="task.videoUrl"
          :poster="task.coverUrl"
          controls
          :show-center-play-btn="true"
          :show-fullscreen-btn="true"
          object-fit="contain"
          class="video"
        />
      </view>

      <view class="card">
        <view class="section-title">文案</view>
        <view class="lines-readonly">
          <view class="rl" v-for="(l, i) in task.finalCopywriting" :key="i">
            {{ i + 1 }}. {{ l }}
          </view>
        </view>
      </view>

      <view class="actions-grid">
        <view class="act" @click="onDownload">
          <view class="act-icon dl">↓</view>
          <text>下载到相册</text>
        </view>
        <view class="act" @click="onCopyLink">
          <view class="act-icon cp">⎘</view>
          <text>复制链接</text>
        </view>
        <view class="act" @click="onShare">
          <view class="act-icon sh">↗</view>
          <text>分享</text>
        </view>
      </view>

      <view class="card douyin">
        <view class="douyin-row">
          <view class="douyin-body">
            <view class="douyin-title">发布到抖音</view>
            <view class="douyin-sub" v-if="!task.publishedToDouyin">
              一键转发到您的抖音账号
            </view>
            <view class="douyin-sub published" v-else>
              已发布到抖音 ✓
            </view>
          </view>
          <button
            class="btn primary small"
            :disabled="task.publishedToDouyin"
            @click="onPublish"
          >
            {{ task.publishedToDouyin ? '已发布' : '去发布' }}
          </button>
        </view>
      </view>
    </view>

    <view v-else-if="task.status === 5" class="state-card error">
      <view class="state-icon">!</view>
      <view class="state-text">生成失败</view>
      <view class="state-sub">{{ task.failReason }}</view>
      <button class="btn ghost" @click="retry">重新创建</button>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { onLoad, onUnload } from '@dcloudio/uni-app';
import { getTask, publishToDouyin, getDouyinAuthUrl } from '../../api/aiVideo.js';

const task = ref(null);
const taskId = ref(0);
const progress = ref(15);

let pollTimer = null;
let progTimer = null;

async function load() {
  task.value = await getTask(taskId.value);
  if (task.value?.status === 3) {
    pollTimer = setTimeout(load, 3000);
    if (!progTimer) {
      progTimer = setInterval(() => {
        if (progress.value < 92) progress.value += 2;
      }, 1500);
    }
  } else if (progTimer) {
    clearInterval(progTimer);
    progTimer = null;
    progress.value = 100;
  }
}

function onDownload() {
  // #ifdef H5
  uni.showToast({ title: 'H5 原型：已复制视频链接，粘贴到浏览器保存', icon: 'none' });
  uni.setClipboardData({ data: task.value.videoUrl });
  // #endif
  // #ifndef H5
  uni.showLoading({ title: '下载中' });
  uni.downloadFile({
    url: task.value.videoUrl,
    success: (r) => {
      uni.saveVideoToPhotosAlbum({
        filePath: r.tempFilePath,
        success: () => uni.showToast({ title: '已保存到相册' }),
        fail: () => uni.showToast({ title: '保存失败', icon: 'none' }),
        complete: () => uni.hideLoading(),
      });
    },
    fail: () => {
      uni.hideLoading();
      uni.showToast({ title: '下载失败', icon: 'none' });
    },
  });
  // #endif
}

function onCopyLink() {
  uni.setClipboardData({
    data: task.value.videoUrl,
    success: () => uni.showToast({ title: '链接已复制' }),
  });
}

function onShare() {
  uni.showToast({ title: '原型阶段：调用系统分享', icon: 'none' });
}

async function onPublish() {
  const modal = await uni.showModal({
    title: '发布到抖音',
    content: '未授权商家将先跳转抖音完成授权，授权后返回 App 再次点击即可发布。',
    confirmText: '继续',
  });
  if (!modal.confirm) return;

  uni.showLoading({ title: '发布中' });
  try {
    await publishToDouyin(taskId.value);
    task.value.publishedToDouyin = true;
    uni.hideLoading();
    uni.showToast({ title: '已发布到抖音', icon: 'success' });
  } catch (e) {
    uni.hideLoading();
    // 若未授权，引导授权
    const { url } = await getDouyinAuthUrl();
    uni.setClipboardData({ data: url });
    uni.showToast({ title: '请先授权抖音账号，链接已复制', icon: 'none' });
  }
}

function retry() {
  uni.redirectTo({ url: '/pages/ai-video/create' });
}

onLoad((q) => {
  taskId.value = Number(q.id);
  load();
});

onUnload(() => {
  if (pollTimer) clearTimeout(pollTimer);
  if (progTimer) clearInterval(progTimer);
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  padding: 0 24rpx 48rpx;
  min-height: 100vh;
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
    line-height: 1.5;
  }

  .poll-progress {
    margin: 40rpx auto 0;
    max-width: 400rpx;
    height: 10rpx;
    background: $brand-primary-light;
    border-radius: 5rpx;
    overflow: hidden;
  }

  .poll-fill {
    height: 100%;
    background: $brand-primary;
    border-radius: 5rpx;
    transition: width 0.3s;
  }

  .btn.ghost {
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

.video-box {
  margin: 24rpx 0;
  background: #000;
  border-radius: $radius-lg;
  overflow: hidden;

  .video {
    width: 100%;
    height: 800rpx;
    background: #000;
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

.lines-readonly {
  .rl {
    padding: 10rpx 0;
    font-size: 28rpx;
    color: $text-regular;
    line-height: 1.6;
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

      &.dl {
        background: #10b981;
      }
      &.cp {
        background: #3b82f6;
      }
      &.sh {
        background: #8b5cf6;
      }
    }
  }
}

.douyin {
  .douyin-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 24rpx;
  }

  .douyin-title {
    font-size: 30rpx;
    font-weight: 600;
    color: $text-primary;
  }

  .douyin-sub {
    margin-top: 6rpx;
    font-size: 24rpx;
    color: $text-secondary;

    &.published {
      color: $success;
    }
  }
}

.btn {
  height: 96rpx;
  line-height: 96rpx;
  padding: 0 40rpx;
  font-size: 30rpx;
  border-radius: $radius-md;

  &.small {
    height: 72rpx;
    line-height: 72rpx;
    padding: 0 32rpx;
    font-size: 26rpx;
    border-radius: $radius-pill;
  }

  &.primary {
    background: $brand-primary;
    color: #fff;
  }

  &.ghost {
    background: #fff;
    color: $brand-primary;
    border: 2rpx solid $brand-primary;
  }

  &[disabled] {
    background: $text-placeholder;
    color: #fff;
  }

  &::after {
    border: none;
  }
}
</style>
