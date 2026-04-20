<template>
  <view class="page">
    <view v-if="task && task.status === 1" class="state-card">
      <view class="spinner"></view>
      <view class="state-text">AI 正在生成文案...</view>
      <view class="state-sub">通常 3-10 秒</view>
    </view>

    <view v-else-if="task && task.status === 2" class="content">
      <view class="card">
        <view class="section-title">AI 生成的文案</view>
        <view class="section-sub">每一句都可以改，或直接删除</view>

        <view class="lines">
          <view class="line" v-for="(l, i) in lines" :key="i">
            <view class="line-no">{{ i + 1 }}</view>
            <textarea
              class="line-input"
              v-model="lines[i]"
              :auto-height="true"
            />
            <view class="line-del" @click="removeLine(i)">删</view>
          </view>
        </view>

        <view class="line-add" @click="addLine">＋ 添加一句</view>
      </view>

      <view class="card">
        <view class="section-title">背景音乐（选填）</view>
        <view class="bgm-list">
          <view
            class="bgm"
            v-for="b in bgmList"
            :key="b.id"
            :class="{ active: selectedBgm === b.id }"
            @click="selectedBgm = b.id"
          >
            <text>{{ b.name }}</text>
            <text class="bgm-mood">{{ b.mood }}</text>
          </view>
        </view>
      </view>

      <view class="actions safe-bottom">
        <button class="btn ghost" @click="useOriginal">用原版</button>
        <button class="btn primary" @click="onConfirm">生成视频</button>
      </view>
    </view>

    <view v-else-if="task && task.status === 5" class="state-card error">
      <view class="state-icon">!</view>
      <view class="state-text">生成失败</view>
      <view class="state-sub">{{ task.failReason || '请稍后重试' }}</view>
      <button class="btn ghost" @click="back">返回</button>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { onLoad, onUnload } from '@dcloudio/uni-app';
import { getTask, confirmTask } from '../../api/aiVideo.js';

const task = ref(null);
const taskId = ref(0);
const lines = ref([]);
const selectedBgm = ref(null);

const bgmList = [
  { id: 1, name: '欢快', mood: '适合美食 / 零食' },
  { id: 2, name: '治愈', mood: '适合手作 / 花草' },
  { id: 3, name: '活力', mood: '适合运动 / 街拍' },
];

let pollTimer = null;

async function load() {
  task.value = await getTask(taskId.value);
  if (task.value?.status === 2 && !lines.value.length) {
    lines.value = [...(task.value.aiCopywriting || [])];
  }
  // status=1 继续轮询
  if (task.value?.status === 1) {
    pollTimer = setTimeout(load, 2000);
  }
}

function removeLine(i) {
  lines.value.splice(i, 1);
}
function addLine() {
  lines.value.push('');
}

async function onConfirm() {
  const finals = lines.value.map((l) => l.trim()).filter(Boolean);
  if (!finals.length) {
    uni.showToast({ title: '至少保留一句文案', icon: 'none' });
    return;
  }
  uni.showLoading({ title: '提交中' });
  await confirmTask({ taskId: taskId.value, finalCopywriting: finals, bgmId: selectedBgm.value });
  uni.hideLoading();
  uni.redirectTo({ url: `/pages/ai-video/detail?id=${taskId.value}` });
}

function useOriginal() {
  lines.value = [...(task.value.aiCopywriting || [])];
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

.lines {
  display: flex;
  flex-direction: column;
  gap: 16rpx;

  .line {
    display: flex;
    align-items: flex-start;
    gap: 12rpx;
    padding: 16rpx;
    background: #fafbfc;
    border-radius: $radius-md;
  }

  .line-no {
    flex-shrink: 0;
    width: 40rpx;
    height: 40rpx;
    line-height: 40rpx;
    text-align: center;
    background: $brand-primary;
    color: #fff;
    font-size: 22rpx;
    border-radius: 50%;
  }

  .line-input {
    flex: 1;
    min-height: 56rpx;
    padding: 4rpx 0;
    font-size: 28rpx;
    color: $text-primary;
    line-height: 1.5;
    background: transparent;
  }

  .line-del {
    flex-shrink: 0;
    font-size: 22rpx;
    color: $text-secondary;
    padding: 6rpx 12rpx;
  }
}

.line-add {
  margin-top: 20rpx;
  text-align: center;
  color: $brand-primary;
  font-size: 26rpx;
  padding: 20rpx;
  border: 2rpx dashed $brand-primary;
  border-radius: $radius-md;
}

.bgm-list {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 16rpx;

  .bgm {
    padding: 24rpx 16rpx;
    background: #f6f7f9;
    border-radius: $radius-md;
    border: 2rpx solid transparent;
    text-align: center;
    font-size: 26rpx;
    color: $text-primary;

    .bgm-mood {
      display: block;
      margin-top: 6rpx;
      font-size: 20rpx;
      color: $text-secondary;
    }

    &.active {
      background: $brand-primary-light;
      border-color: $brand-primary;
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
