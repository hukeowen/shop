<template>
  <view class="page">
    <view class="hero safe-top">
      <view class="hero-title">AI 一键成片</view>
      <view class="hero-sub">3 张照片 + 一句话，1 分钟生成抖音爆款视频</view>

      <view class="quota-box" @click="goQuota">
        <view class="quota-label">剩余配额</view>
        <view class="quota-text">
          <text>{{ quota.remaining ?? quota.total }}</text>
          <text class="quota-total"> 条可用 · 点击购买加量</text>
        </view>
      </view>

      <button class="cta" @click="goCreate">
        <text class="cta-icon">＋</text>
        开始创作
      </button>
    </view>

    <view class="steps card">
      <view class="section-title">怎么做？</view>
      <view class="step">
        <view class="step-no">1</view>
        <view class="step-body">
          <view class="step-title">拍 3 张照片</view>
          <view class="step-desc">产品细节 / 制作过程 / 成品，越真实越打动人</view>
        </view>
      </view>
      <view class="step">
        <view class="step-no">2</view>
        <view class="step-body">
          <view class="step-title">一句话说亮点</view>
          <view class="step-desc">"现烤 / 5 块 / 软糯"——告诉 AI 你的记忆点</view>
        </view>
      </view>
      <view class="step">
        <view class="step-no">3</view>
        <view class="step-body">
          <view class="step-title">AI 写文案 + 合成视频</view>
          <view class="step-desc">自动配音配乐，一键发布到抖音</view>
        </view>
      </view>
    </view>

    <view class="card demo" v-if="DEMO_VIDEO_URL" @click="playDemo">
      <view class="section-title">▶ 看一条示例成片</view>
      <view class="demo-sub">实际效果演示 · 点击播放</view>
      <video
        v-if="demoPlaying"
        :src="DEMO_VIDEO_URL"
        autoplay
        controls
        class="demo-video"
        object-fit="contain"
      />
      <view v-else class="demo-thumb">
        <view class="play-icon">▶</view>
      </view>
    </view>

    <view class="recent card" v-if="recent.length">
      <view class="section-title-row">
        <text class="section-title">最近记录</text>
        <text class="link" @click="goHistory">查看全部 ›</text>
      </view>
      <view
        class="recent-item"
        v-for="t in recent"
        :key="t.id"
        @click="openTask(t)"
      >
        <view class="thumb" :style="{ backgroundImage: `url(${t.coverUrl || t.imageUrls[0]})` }">
          <view v-if="t.status === 4" class="play">▶</view>
        </view>
        <view class="recent-body">
          <view class="recent-title">{{ t.userDescription || '未命名' }}</view>
          <view class="recent-meta">
            <text
              class="status-tag"
              :style="{ color: statusColor(t.status), background: statusBg(t.status) }"
            >
              {{ statusText(t.status) }}
            </text>
            <text class="time">{{ t.createdAt }}</text>
          </view>
        </view>
      </view>
    </view>

    <view class="bottom-space" />
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getQuota, getTaskPage } from '../../api/aiVideo.js';
import { AI_VIDEO_STATUS } from '../../utils/format.js';

// 配置一条真实示例视频 URL 后卡片才会显示（空字符串 = 隐藏）
const DEMO_VIDEO_URL = '';

const quota = ref({ remaining: 0, total: 0, used: 0 });
const recent = ref([]);
const demoPlaying = ref(false);

function playDemo() {
  demoPlaying.value = true;
}

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
  quota.value = await getQuota();
  const page = await getTaskPage();
  recent.value = page.list.slice(0, 3);
}

function goCreate() {
  uni.navigateTo({ url: '/pages/ai-video/create' });
}
function goHistory() {
  uni.navigateTo({ url: '/pages/ai-video/history' });
}
function goQuota() {
  uni.navigateTo({ url: '/pages/ai-video/quota' });
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
  padding: 0 0 48rpx;
  min-height: 100vh;
}

.safe-top {
  padding-top: calc(env(safe-area-inset-top) + 48rpx);
}

.hero {
  margin: 0 24rpx;
  padding: 0 16rpx 32rpx;
  color: $text-primary;

  .hero-title {
    font-size: 52rpx;
    font-weight: 800;
    letter-spacing: 2rpx;
  }

  .hero-sub {
    margin-top: 12rpx;
    font-size: 26rpx;
    color: $text-secondary;
  }

  .quota-box {
    margin-top: 32rpx;
    padding: 24rpx;
    background: linear-gradient(135deg, #fff3ec, #ffe8db);
    border-radius: $radius-md;

    .quota-label {
      font-size: 24rpx;
      color: $text-secondary;
    }

    .quota-text {
      margin-top: 10rpx;
      font-size: 24rpx;
      color: $text-regular;
      text-align: right;

      :first-child {
        color: $brand-primary;
        font-weight: 700;
        font-size: 32rpx;
      }
    }
  }

  .cta {
    margin-top: 32rpx;
    width: 100%;
    height: 112rpx;
    line-height: 112rpx;
    background: linear-gradient(135deg, $brand-primary, #ff9b5e);
    color: #fff;
    font-size: 34rpx;
    font-weight: 700;
    border-radius: $radius-md;
    box-shadow: 0 16rpx 40rpx rgba(255, 107, 53, 0.3);

    &::after {
      border: none;
    }

    .cta-icon {
      font-size: 44rpx;
      font-weight: 300;
      margin-right: 12rpx;
    }
  }
}

.card {
  margin: 24rpx;
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 32rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: $text-primary;
}

.section-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24rpx;

  .link {
    font-size: 26rpx;
    color: $brand-primary;
  }
}

.steps {
  .section-title {
    margin-bottom: 24rpx;
  }

  .step {
    display: flex;
    gap: 24rpx;
    padding: 20rpx 0;

    .step-no {
      flex-shrink: 0;
      width: 56rpx;
      height: 56rpx;
      line-height: 56rpx;
      text-align: center;
      background: $brand-primary-light;
      color: $brand-primary;
      border-radius: 50%;
      font-weight: 700;
      font-size: 28rpx;
    }

    .step-title {
      font-size: 28rpx;
      font-weight: 600;
      color: $text-primary;
    }

    .step-desc {
      margin-top: 6rpx;
      font-size: 24rpx;
      color: $text-secondary;
      line-height: 1.5;
    }
  }
}

.recent-item {
  display: flex;
  gap: 20rpx;
  padding: 20rpx 0;
  border-bottom: 1rpx solid $border-color;

  &:last-child {
    border-bottom: none;
  }

  .thumb {
    position: relative;
    flex-shrink: 0;
    width: 140rpx;
    height: 200rpx;
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
      font-size: 40rpx;
      border-radius: $radius-md;
    }
  }

  .recent-body {
    flex: 1;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    padding: 8rpx 0;
    min-width: 0;
  }

  .recent-title {
    font-size: 28rpx;
    color: $text-primary;
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }

  .recent-meta {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 12rpx;

    .status-tag {
      padding: 4rpx 16rpx;
      border-radius: $radius-pill;
      font-size: 22rpx;
    }

    .time {
      font-size: 22rpx;
      color: $text-placeholder;
    }
  }
}

.demo {
  .demo-sub {
    font-size: 24rpx;
    color: $text-secondary;
    margin-bottom: 20rpx;
  }

  .demo-thumb {
    height: 200rpx;
    background: #1a1a1a;
    border-radius: $radius-md;
    display: flex;
    align-items: center;
    justify-content: center;

    .play-icon {
      color: #fff;
      font-size: 60rpx;
    }
  }

  .demo-video {
    width: 100%;
    height: 400rpx;
    border-radius: $radius-md;
  }
}

.bottom-space {
  height: 80rpx;
}
</style>
