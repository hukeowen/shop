<template>
  <s-layout title="我的视频">
    <view class="list-container">
      <view v-for="item in list" :key="item.id" class="video-card" @tap="goDetail(item.id)">
        <image v-if="item.imageUrls && item.imageUrls.length" :src="item.imageUrls[0]" class="cover" mode="aspectFill" />
        <view class="card-info">
          <text class="card-title">{{ item.title }}</text>
          <text class="card-status" :class="'s-' + item.status">{{ statusText[item.status] }}</text>
          <text class="card-time">{{ item.createTime }}</text>
        </view>
        <view v-if="item.douyinPublishStatus === 2" class="douyin-badge">已发抖音</view>
      </view>
      <view v-if="list.length === 0 && !loading" class="empty">
        <text>还没有视频，去创建一个吧</text>
        <button class="ss-btn" @tap="goCreate">AI生成视频</button>
      </view>
    </view>
  </s-layout>
</template>

<script setup>
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import VideoApi from '@/sheep/api/video';

const list = ref([]);
const loading = ref(false);
const statusText = { 0: '待处理', 1: '生成中', 2: '已完成', 3: '失败' };

onShow(async () => {
  loading.value = true;
  // 由于后端是分页接口，这里传默认参数
  // 实际使用时可以从 merchantInfo 获取 merchantId
  try {
    const { data } = await VideoApi.get(0); // 获取列表需要扩展后端接口
    // 暂时用简单方式
  } catch (e) {}
  loading.value = false;
});

const goDetail = (id) => {
  uni.navigateTo({ url: '/pages/video/detail?id=' + id });
};

const goCreate = () => {
  uni.navigateTo({ url: '/pages/video/create' });
};
</script>

<style lang="scss" scoped>
.list-container { padding: 20rpx 30rpx; }
.video-card {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 16rpx;
  padding: 20rpx;
  margin-bottom: 16rpx;
  position: relative;
  .cover { width: 150rpx; height: 150rpx; border-radius: 8rpx; margin-right: 20rpx; }
  .card-info { flex: 1; }
  .card-title { font-size: 28rpx; font-weight: bold; display: block; }
  .card-status { font-size: 24rpx; margin-top: 8rpx; display: block; }
  .s-1 { color: #ff9900; }
  .s-2 { color: #07c160; }
  .s-3 { color: #ff4d4f; }
  .card-time { font-size: 22rpx; color: #999; margin-top: 6rpx; display: block; }
  .douyin-badge {
    position: absolute;
    top: 16rpx;
    right: 16rpx;
    background: #000;
    color: #fff;
    font-size: 20rpx;
    padding: 4rpx 12rpx;
    border-radius: 20rpx;
  }
}
.empty {
  text-align: center;
  padding: 100rpx 0;
  color: #999;
  .ss-btn {
    background: #ff6600;
    color: #fff;
    border-radius: 48rpx;
    height: 80rpx;
    line-height: 80rpx;
    width: 300rpx;
    margin: 30rpx auto;
    font-size: 28rpx;
  }
}
</style>
