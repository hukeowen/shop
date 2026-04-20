<template>
  <view class="page">
    <view class="card">
      <view class="section-title">上传 3 张照片</view>
      <view class="section-sub">建议：产品特写、制作过程、成品展示</view>
      <view class="pics">
        <view
          class="pic"
          v-for="(url, i) in imageUrls"
          :key="i"
          :style="{ backgroundImage: `url(${url})` }"
        >
          <view class="pic-del" @click="removePic(i)">×</view>
        </view>
        <view v-if="imageUrls.length < 3" class="pic add" @click="pickImage">
          <text class="plus">＋</text>
          <text class="add-text">添加照片 {{ imageUrls.length }}/3</text>
        </view>
      </view>
    </view>

    <view class="card">
      <view class="section-title">一句话亮点</view>
      <view class="section-sub">像对朋友吆喝一样说 1-2 句</view>
      <textarea
        class="textarea"
        :maxlength="120"
        placeholder="例：现烤蜜薯 5 块一个，流糖心甜到跺脚"
        v-model="description"
      />
      <view class="counter">{{ description.length }} / 120</view>
    </view>

    <view class="card tips">
      <view class="tip-title">小贴士</view>
      <view class="tip-item">· 光线要亮，避免逆光</view>
      <view class="tip-item">· 图里尽量别带水印</view>
      <view class="tip-item">· 每生成一次消耗 1 次配额，失败不扣</view>
    </view>

    <view class="actions safe-bottom">
      <button class="btn primary" :disabled="!canSubmit" @click="onSubmit">
        开始生成
      </button>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue';
import { createTask } from '../../api/aiVideo.js';

const imageUrls = ref([]);
const description = ref('');

const canSubmit = computed(
  () => imageUrls.value.length >= 1 && description.value.trim().length >= 5
);

function pickImage() {
  uni.chooseImage({
    count: 3 - imageUrls.value.length,
    success: (r) => {
      imageUrls.value.push(...r.tempFilePaths.slice(0, 3 - imageUrls.value.length));
    },
  });
}

function removePic(i) {
  imageUrls.value.splice(i, 1);
}

async function onSubmit() {
  if (!canSubmit.value) return;
  uni.showLoading({ title: '提交中' });
  try {
    const taskId = await createTask({
      imageUrls: imageUrls.value,
      userDescription: description.value.trim(),
    });
    uni.hideLoading();
    uni.redirectTo({ url: `/pages/ai-video/confirm?id=${taskId}` });
  } catch (e) {
    uni.hideLoading();
  }
}
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  padding: 24rpx 24rpx 200rpx;
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
  font-size: 24rpx;
  color: $text-secondary;
}

.pics {
  margin-top: 24rpx;
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 16rpx;

  .pic {
    position: relative;
    padding-top: 100%;
    border-radius: $radius-md;
    background-size: cover;
    background-position: center;
    background-color: #f6f7f9;

    .pic-del {
      position: absolute;
      top: 8rpx;
      right: 8rpx;
      width: 40rpx;
      height: 40rpx;
      line-height: 40rpx;
      text-align: center;
      background: rgba(0, 0, 0, 0.5);
      color: #fff;
      border-radius: 50%;
      font-size: 28rpx;
    }

    &.add {
      padding-top: 0;
      aspect-ratio: 1;
      display: flex;
      flex-direction: column;
      justify-content: center;
      align-items: center;
      border: 2rpx dashed $text-placeholder;
      color: $text-secondary;
      background: transparent;

      .plus {
        font-size: 60rpx;
        font-weight: 200;
        line-height: 1;
        color: $brand-primary;
      }

      .add-text {
        margin-top: 8rpx;
        font-size: 22rpx;
      }
    }
  }
}

.textarea {
  display: block;
  width: 100%;
  min-height: 200rpx;
  margin-top: 20rpx;
  padding: 24rpx;
  background: #f6f7f9;
  border-radius: $radius-md;
  font-size: 28rpx;
  line-height: 1.6;
  color: $text-primary;
  box-sizing: border-box;
}

.counter {
  margin-top: 12rpx;
  text-align: right;
  font-size: 22rpx;
  color: $text-placeholder;
}

.tips {
  background: #fff8ef;
  box-shadow: none;

  .tip-title {
    font-size: 26rpx;
    font-weight: 600;
    color: $warning;
    margin-bottom: 12rpx;
  }

  .tip-item {
    font-size: 24rpx;
    color: $text-regular;
    line-height: 1.8;
  }
}

.actions {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 24rpx 32rpx calc(env(safe-area-inset-bottom) + 24rpx);
  background: #fff;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.05);
}

.btn {
  width: 100%;
  height: 96rpx;
  line-height: 96rpx;
  font-size: 30rpx;
  border-radius: $radius-md;

  &.primary {
    background: $brand-primary;
    color: #fff;
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
