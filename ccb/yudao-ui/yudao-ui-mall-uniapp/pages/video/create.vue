<template>
  <s-layout title="AI生成视频">
    <view class="create-container">
      <!-- 标题 -->
      <view class="form-item">
        <text class="label">视频标题</text>
        <input v-model="form.title" placeholder="给视频起个标题" maxlength="50" />
      </view>

      <!-- 文案 -->
      <view class="form-item">
        <text class="label">视频文案</text>
        <textarea v-model="form.description" placeholder="输入视频文案，AI会将文字转成语音旁白" maxlength="500" :auto-height="true" />
        <text class="word-count">{{ form.description.length }}/500</text>
      </view>

      <!-- 图片上传 -->
      <view class="form-item">
        <text class="label">上传图片（{{ form.imageUrls.length }}/9）</text>
        <view class="image-grid">
          <view v-for="(img, index) in form.imageUrls" :key="index" class="image-item">
            <image :src="img" mode="aspectFill" class="img" />
            <view class="delete-btn" @tap="removeImage(index)">×</view>
          </view>
          <view v-if="form.imageUrls.length < 9" class="image-item add-btn" @tap="chooseImages">
            <text class="plus">+</text>
          </view>
        </view>
        <text class="tip">建议上传 3-9 张竖版图片，效果更佳</text>
      </view>

      <!-- 提交按钮 -->
      <button class="ss-btn" :loading="submitting" @tap="submitTask">
        {{ submitting ? '生成中...' : '一键生成视频' }}
      </button>
    </view>
  </s-layout>
</template>

<script setup>
import { ref } from 'vue';
import sheep from '@/sheep';
import VideoApi from '@/sheep/api/video';

const submitting = ref(false);
const form = ref({
  title: '',
  description: '',
  imageUrls: [],
  bgmUrl: '',
});

const chooseImages = () => {
  const remain = 9 - form.value.imageUrls.length;
  uni.chooseImage({
    count: remain,
    success: (res) => {
      res.tempFilePaths.forEach((path) => {
        uni.uploadFile({
          url: sheep.$url.base_url + '/infra/file/upload',
          filePath: path,
          name: 'file',
          header: { Authorization: 'Bearer ' + sheep.$store('user').token },
          success: (uploadRes) => {
            const result = JSON.parse(uploadRes.data);
            if (result.code === 0) {
              form.value.imageUrls.push(result.data);
            }
          },
        });
      });
    },
  });
};

const removeImage = (index) => {
  form.value.imageUrls.splice(index, 1);
};

const submitTask = async () => {
  if (!form.value.title) {
    uni.showToast({ title: '请输入视频标题', icon: 'none' });
    return;
  }
  if (!form.value.description) {
    uni.showToast({ title: '请输入视频文案', icon: 'none' });
    return;
  }
  if (form.value.imageUrls.length === 0) {
    uni.showToast({ title: '请至少上传一张图片', icon: 'none' });
    return;
  }

  submitting.value = true;
  try {
    const { data: taskId } = await VideoApi.create(form.value);
    uni.showToast({ title: '已提交，正在生成', icon: 'success' });
    // 跳转到视频详情页查看进度
    setTimeout(() => {
      uni.redirectTo({ url: '/pages/video/detail?id=' + taskId });
    }, 1500);
  } catch (e) {
    uni.showToast({ title: e.msg || '提交失败', icon: 'none' });
  } finally {
    submitting.value = false;
  }
};
</script>

<style lang="scss" scoped>
.create-container { padding: 20rpx 30rpx; }
.form-item {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  .label { font-size: 30rpx; font-weight: bold; color: #333; margin-bottom: 16rpx; display: block; }
  input { font-size: 28rpx; height: 60rpx; }
  textarea { font-size: 28rpx; width: 100%; min-height: 120rpx; }
  .word-count { font-size: 24rpx; color: #999; text-align: right; display: block; }
  .tip { font-size: 24rpx; color: #999; margin-top: 12rpx; display: block; }
}
.image-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  .image-item {
    width: 180rpx;
    height: 180rpx;
    border-radius: 8rpx;
    overflow: hidden;
    position: relative;
    .img { width: 100%; height: 100%; }
    .delete-btn {
      position: absolute;
      top: 0;
      right: 0;
      width: 40rpx;
      height: 40rpx;
      background: rgba(0,0,0,0.5);
      color: #fff;
      text-align: center;
      line-height: 40rpx;
      font-size: 28rpx;
      border-radius: 0 0 0 8rpx;
    }
  }
  .add-btn {
    border: 2rpx dashed #ddd;
    display: flex;
    align-items: center;
    justify-content: center;
    .plus { font-size: 56rpx; color: #ccc; }
  }
}
.ss-btn {
  background: #ff6600;
  color: #fff;
  border-radius: 48rpx;
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  font-size: 32rpx;
  margin-top: 30rpx;
}
</style>
