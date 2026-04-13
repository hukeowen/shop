<template>
  <s-layout title="视频详情">
    <view class="detail-container" v-if="task">
      <!-- 状态卡片 -->
      <view class="status-card" :class="'status-' + task.status">
        <text class="status-text">{{ statusText[task.status] }}</text>
        <text v-if="task.status === 3" class="fail-reason">{{ task.failReason }}</text>
      </view>

      <!-- 视频预览 -->
      <view v-if="task.videoUrl" class="video-section">
        <video :src="task.videoUrl" class="video-player" controls object-fit="contain" />
      </view>

      <!-- 视频信息 -->
      <view class="info-card">
        <text class="title">{{ task.title }}</text>
        <text class="desc">{{ task.description }}</text>
        <view class="image-preview">
          <image v-for="(img, i) in task.imageUrls" :key="i" :src="img" mode="aspectFill" class="thumb"
            @tap="previewImage(i)" />
        </view>
      </view>

      <!-- 抖音发布 -->
      <view v-if="task.status === 2" class="publish-section">
        <view class="publish-status" v-if="task.douyinPublishStatus > 0">
          <text>抖音发布状态：{{ douyinStatusText[task.douyinPublishStatus] }}</text>
          <text v-if="task.douyinItemId" class="item-id">作品ID：{{ task.douyinItemId }}</text>
        </view>
        <button v-if="task.douyinPublishStatus === 0" class="ss-btn douyin-btn" @tap="publishToDouyin">
          发布到抖音
        </button>
      </view>

      <!-- 保存到相册 -->
      <button v-if="task.videoUrl" class="ss-btn save-btn" @tap="saveVideo">保存视频到相册</button>
    </view>

    <!-- 加载中 -->
    <view v-if="loading" class="loading">
      <text>加载中...</text>
    </view>
  </s-layout>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import VideoApi from '@/sheep/api/video';

const task = ref(null);
const loading = ref(true);
let taskId = null;
let pollTimer = null;

const statusText = { 0: '等待处理', 1: '视频生成中...', 2: '生成完成', 3: '生成失败' };
const douyinStatusText = { 0: '未发布', 1: '发布中', 2: '已发布', 3: '发布失败' };

onLoad((options) => {
  taskId = options.id;
  loadTask();
});

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer);
});

const loadTask = async () => {
  loading.value = true;
  const { data } = await VideoApi.get(taskId);
  task.value = data;
  loading.value = false;

  // 如果还在生成中，轮询状态
  if (data.status === 0 || data.status === 1) {
    pollTimer = setInterval(async () => {
      const { data: updated } = await VideoApi.get(taskId);
      task.value = updated;
      if (updated.status >= 2) {
        clearInterval(pollTimer);
        pollTimer = null;
      }
    }, 3000);
  }
};

const previewImage = (index) => {
  uni.previewImage({ urls: task.value.imageUrls, current: index });
};

const publishToDouyin = async () => {
  uni.showLoading({ title: '发布中...' });
  try {
    await VideoApi.publishDouyin(taskId);
    uni.showToast({ title: '已提交发布', icon: 'success' });
    loadTask();
  } catch (e) {
    uni.showToast({ title: e.msg || '发布失败', icon: 'none' });
  } finally {
    uni.hideLoading();
  }
};

const saveVideo = () => {
  uni.downloadFile({
    url: task.value.videoUrl,
    success: (res) => {
      uni.saveVideoToPhotosAlbum({
        filePath: res.tempFilePath,
        success: () => uni.showToast({ title: '已保存到相册', icon: 'success' }),
        fail: () => uni.showToast({ title: '保存失败', icon: 'none' }),
      });
    },
  });
};
</script>

<style lang="scss" scoped>
.detail-container { padding: 20rpx 30rpx; }
.status-card {
  background: #fff;
  border-radius: 16rpx;
  padding: 30rpx;
  text-align: center;
  .status-text { font-size: 32rpx; font-weight: bold; }
  .fail-reason { font-size: 26rpx; color: #ff4d4f; margin-top: 10rpx; display: block; }
  &.status-1 .status-text { color: #ff9900; }
  &.status-2 .status-text { color: #07c160; }
  &.status-3 .status-text { color: #ff4d4f; }
}
.video-section {
  margin-top: 20rpx;
  .video-player { width: 100%; height: 500rpx; border-radius: 16rpx; }
}
.info-card {
  background: #fff;
  border-radius: 16rpx;
  padding: 30rpx;
  margin-top: 20rpx;
  .title { font-size: 32rpx; font-weight: bold; display: block; }
  .desc { font-size: 28rpx; color: #666; margin-top: 12rpx; display: block; }
  .image-preview { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 16rpx; }
  .thumb { width: 120rpx; height: 120rpx; border-radius: 8rpx; }
}
.publish-section {
  background: #fff;
  border-radius: 16rpx;
  padding: 30rpx;
  margin-top: 20rpx;
  .item-id { font-size: 24rpx; color: #999; margin-top: 8rpx; display: block; }
}
.ss-btn {
  border-radius: 48rpx;
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  font-size: 32rpx;
  margin-top: 20rpx;
}
.douyin-btn { background: #000; color: #fff; }
.save-btn { background: #07c160; color: #fff; }
.loading { text-align: center; padding: 100rpx; color: #999; }
</style>
