<template>
  <view class="page">
    <view class="list">
      <view
        v-for="t in list"
        :key="t.id"
        class="item card"
        @click="openTask(t)"
      >
        <view
          class="thumb"
          :style="{ backgroundImage: `url(${t.coverUrl || t.imageUrls[0]})` }"
        >
          <view v-if="t.status === 4" class="play">▶</view>
        </view>
        <view class="body">
          <view class="title">{{ t.userDescription || '未命名' }}</view>
          <view class="meta">
            <text
              class="status"
              :style="{ color: statusColor(t.status), background: statusBg(t.status) }"
            >
              {{ statusText(t.status) }}
            </text>
            <text v-if="t.publishedToDouyin" class="tag dy">抖音已发</text>
          </view>
          <view class="time">{{ t.createdAt }}</view>
        </view>
      </view>
      <view v-if="!list.length" class="empty">暂无历史</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getTaskPage } from '../../api/aiVideo.js';
import { AI_VIDEO_STATUS } from '../../utils/format.js';

const list = ref([]);

function statusText(s) {
  return AI_VIDEO_STATUS[s]?.text || s;
}
function statusColor(s) {
  return AI_VIDEO_STATUS[s]?.color || '#999';
}
function statusBg(s) {
  return (AI_VIDEO_STATUS[s]?.color || '#999') + '20';
}

async function load() {
  const page = await getTaskPage();
  list.value = page.list;
}

function openTask(t) {
  if (t.status === 2) {
    uni.navigateTo({ url: `/pages/ai-video/confirm?id=${t.id}` });
  } else {
    uni.navigateTo({ url: `/pages/ai-video/detail?id=${t.id}` });
  }
}

onShow(() => {
  load();
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  padding: 24rpx 24rpx 48rpx;
  min-height: 100vh;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.card {
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.item {
  display: flex;
  gap: 20rpx;

  .thumb {
    position: relative;
    flex-shrink: 0;
    width: 180rpx;
    height: 240rpx;
    border-radius: $radius-md;
    background-size: cover;
    background-position: center;
    background-color: #f0f0f0;

    .play {
      position: absolute;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      background: rgba(0, 0, 0, 0.3);
      color: #fff;
      font-size: 48rpx;
      border-radius: $radius-md;
    }
  }

  .body {
    flex: 1;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    padding: 8rpx 0;
    min-width: 0;
  }

  .title {
    font-size: 28rpx;
    color: $text-primary;
    line-height: 1.5;
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }

  .meta {
    display: flex;
    gap: 12rpx;
    margin-top: 8rpx;

    .status,
    .tag {
      padding: 4rpx 16rpx;
      border-radius: $radius-pill;
      font-size: 22rpx;
    }

    .tag.dy {
      background: #000;
      color: #fff;
    }
  }

  .time {
    font-size: 22rpx;
    color: $text-placeholder;
  }
}

.empty {
  padding: 200rpx 0;
  text-align: center;
  color: $text-placeholder;
  font-size: 26rpx;
}
</style>
