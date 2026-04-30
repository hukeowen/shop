<template>
  <view class="page">
    <view class="card">
      <view class="section-title">上传商品照片</view>
      <view class="section-sub">1-6 张，角度多一点脚本更丰富（自动上传到 OSS）</view>

      <view class="pics">
        <view
          class="pic"
          v-for="(item, i) in images"
          :key="i"
          :style="{ backgroundImage: `url(${item.preview})` }"
        >
          <view class="pic-del" @click="removePic(i)">×</view>
        </view>
        <view v-if="images.length < 6" class="pic add" @click="pickImage">
          <text class="plus">＋</text>
          <text class="add-text">{{ images.length ? '继续加' : '拍 / 选图' }}</text>
          <text class="add-count">{{ images.length }}/6</text>
        </view>
      </view>
    </view>

    <view class="card">
      <view class="section-title-row">
        <view class="section-title">一句话亮点</view>
        <view v-if="images.length && !autoFilling" class="ai-refill" @click="triggerAutoFill">
          <text>✦ AI 重新识别</text>
        </view>
        <view v-if="autoFilling" class="ai-filling">
          <text>AI 识别中…</text>
        </view>
      </view>
      <view class="section-sub">上传图片后 AI 自动识别，可直接修改</view>
      <textarea
        class="textarea"
        :class="{ filling: autoFilling }"
        :maxlength="120"
        :placeholder="autoFilling ? 'AI 正在识别图片亮点…' : '例：现烤蜜薯 5 块一个，流糖心甜到跺脚'"
        :disabled="autoFilling"
        v-model="description"
      />
      <view class="counter">{{ description.length }} / 120</view>
    </view>

    <view class="card">
      <view class="section-title">选择配音</view>
      <view class="section-sub">每幕一句台词，这个声音来读</view>
      <view class="voice-list">
        <view
          v-for="v in voices"
          :key="v.key"
          class="voice"
          :class="{ active: voiceKey === v.key }"
          @click="voiceKey = v.key"
        >
          <view class="voice-name">{{ v.name }}</view>
          <view class="voice-desc">{{ v.desc }}</view>
        </view>
      </view>
    </view>

    <view class="card">
      <view class="section-title">画面比例</view>
      <view class="ratio-list">
        <view
          v-for="r in ratios"
          :key="r.key"
          class="ratio"
          :class="{ active: ratio === r.key }"
          @click="ratio = r.key"
        >
          <view class="ratio-box" :class="r.key === '9:16' ? 'portrait' : 'landscape'"></view>
          <text>{{ r.name }}</text>
        </view>
      </view>
    </view>

    <!-- 美化开关：默认开（即梦 CV 给每张图加暖色调/浅景深/电影质感后再喂 Seedance） -->
    <view class="card enhance-card">
      <view class="enhance-row">
        <view class="enhance-info">
          <view class="enhance-title">✨ AI 美化预处理</view>
          <view class="enhance-desc">手机原图 → 即梦 CV 美化 → 浅景深 / 暖色调 / 电影质感，视频质感提升一档</view>
        </view>
        <switch
          :checked="enhance"
          color="#FF6B35"
          @change="(e) => (enhance = e.detail.value)"
        />
      </view>
      <view class="enhance-tip">关闭可省 ~20 秒；开启每张耗 3-5 秒，失败自动回原图</view>
    </view>

    <view class="card tips">
      <view class="tip-title">接下来会发生</view>
      <view class="tip-item">1. 图片上传到 OSS（秒级）</view>
      <view class="tip-item">2. 豆包视觉 LLM 看图 → 每张图拆 1 幕脚本（5-15 秒）</view>
      <view class="tip-item">3. 你检查脚本，可改可留</view>
      <view class="tip-item">4. Seedance 1.5 Pro 并行生成每段 10s 视频（每幕独立，2-3 分钟）</view>
      <view class="tip-item">5. 豆包 TTS 配音（并行）</view>
      <view class="tip-item warn">约 ¥3 / 条，失败不扣</view>
    </view>

    <view class="actions safe-bottom">
      <button class="btn primary" :disabled="!canSubmit || submitting" @click="onSubmit">
        {{ submitting ? (submitPhaseLabel || 'AI 处理中...') : '开始生成' }}
      </button>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue';
import { createTask } from '../../api/aiVideo.js';
import { blobUrlToBase64 } from '../../api/oss.js';
import { VOICES } from '../../api/voice.js';
import { generateHighlight } from '../../api/scriptLlm.js';
import { useUserStore } from '../../store/user.js';

const userStore = useUserStore();
const images = ref([]); // [{ preview: blobUrl, base64 }]
const description = ref('');
const voiceKey = ref('cancan');
const ratio = ref('9:16');
const enhance = ref(true); // 默认开美化预处理
const submitting = ref(false);
const submitPhaseLabel = ref(''); // 提交时分阶段文案
const autoFilling = ref(false);
// 标记当前内容是否是 AI 自动填写的（未被用户手动改过）
let aiFilledText = '';

const voices = VOICES;
const ratios = [
  { key: '9:16', name: '竖版 9:16' },
  { key: '16:9', name: '横版 16:9' },
  { key: '1:1', name: '方形 1:1' },
];

const canSubmit = computed(
  () => images.value.length >= 1 && description.value.trim().length >= 5 && !autoFilling.value
);

async function triggerAutoFill() {
  if (!images.value.length || autoFilling.value) return;
  // 用户手动改过就不覆盖，除非主动点"重新识别"
  autoFilling.value = true;
  const savedDesc = description.value;
  description.value = '';
  try {
    const base64s = images.value.slice(0, 3).map((x) => x.base64);
    const result = await generateHighlight(base64s);
    description.value = result;
    aiFilledText = result;
  } catch (e) {
    description.value = savedDesc;
    uni.showToast({ title: 'AI 识别失败，请手动填写', icon: 'none' });
  } finally {
    autoFilling.value = false;
  }
}

function pickImage() {
  uni.chooseImage({
    count: 6 - images.value.length,
    sizeType: ['compressed'],
    sourceType: ['camera', 'album'],
    success: async (r) => {
      uni.showLoading({ title: '读取中' });
      try {
        for (const p of r.tempFilePaths) {
          const base64 = await blobUrlToBase64(p);
          images.value.push({ preview: p, base64 });
          if (images.value.length >= 6) break;
        }
      } catch (e) {
        uni.showToast({ title: '图片读取失败：' + e.message, icon: 'none' });
        return;
      } finally {
        uni.hideLoading();
      }
      // 如果用户还没有手动填写（或内容是上次 AI 填的），自动重新识别
      const userEdited = description.value && description.value !== aiFilledText;
      if (!userEdited) triggerAutoFill();
    },
  });
}

function removePic(i) {
  images.value.splice(i, 1);
}

async function onSubmit() {
  if (!canSubmit.value) return;
  submitting.value = true;
  submitPhaseLabel.value = '准备上传…';
  uni.showLoading({ title: '上传图片' });
  try {
    const taskId = await createTask({
      imageBase64s: images.value.map((x) => x.base64),
      userDescription: description.value.trim(),
      voiceKey: voiceKey.value,
      ratio: ratio.value,
      shopName: userStore.shop?.name || '',
      enhance: enhance.value,
      onProgress: (s) => {
        if (s.phase === 'uploading') {
          submitPhaseLabel.value = '上传图片到 OSS…';
          uni.showLoading({ title: '上传图片' });
        } else if (s.phase === 'enhancing') {
          submitPhaseLabel.value = `AI 美化 ${s.enhancedCount}/${s.totalCount}`;
          uni.showLoading({ title: `AI 美化 ${s.enhancedCount}/${s.totalCount}` });
        } else if (s.phase === 'scripting') {
          submitPhaseLabel.value = 'AI 看图 + 拆镜头';
          uni.showLoading({ title: 'AI 拆镜头' });
        }
      },
    });
    uni.hideLoading();
    uni.redirectTo({ url: `/pages/ai-video/confirm?id=${taskId}` });
  } catch (e) {
    uni.hideLoading();
    uni.showModal({ title: '提交失败', content: e.message || '未知错误', showCancel: false });
  } finally {
    submitting.value = false;
    submitPhaseLabel.value = '';
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
  gap: 12rpx;

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
      gap: 4rpx;
      border: 2rpx dashed $text-placeholder;
      color: $text-secondary;
      background: transparent;

      .plus {
        font-size: 56rpx;
        font-weight: 200;
        line-height: 1;
        color: $brand-primary;
      }

      .add-text {
        font-size: 22rpx;
      }

      .add-count {
        font-size: 20rpx;
        color: $text-placeholder;
      }
    }
  }
}

.section-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ai-refill {
  font-size: 22rpx;
  color: $brand-primary;
  padding: 6rpx 16rpx;
  background: $brand-primary-light;
  border-radius: $radius-pill;
  flex-shrink: 0;
}

.ai-filling {
  font-size: 22rpx;
  color: $text-secondary;
  flex-shrink: 0;
  animation: pulse 1.2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.textarea {
  display: block;
  width: 100%;
  min-height: 180rpx;
  margin-top: 20rpx;
  padding: 24rpx;
  background: #f6f7f9;
  border-radius: $radius-md;
  font-size: 28rpx;
  line-height: 1.6;
  color: $text-primary;
  box-sizing: border-box;
  transition: border-color 0.2s;
  border: 2rpx solid transparent;

  &.filling {
    border-color: $brand-primary-light;
    color: $text-secondary;
  }
}

.counter {
  margin-top: 12rpx;
  text-align: right;
  font-size: 22rpx;
  color: $text-placeholder;
}

.voice-list {
  margin-top: 20rpx;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;

  .voice {
    padding: 20rpx 16rpx;
    background: #f6f7f9;
    border-radius: $radius-md;
    border: 2rpx solid transparent;

    .voice-name {
      font-size: 28rpx;
      font-weight: 600;
      color: $text-primary;
    }

    .voice-desc {
      margin-top: 6rpx;
      font-size: 22rpx;
      color: $text-secondary;
    }

    &.active {
      background: $brand-primary-light;
      border-color: $brand-primary;
    }
  }
}

.ratio-list {
  margin-top: 20rpx;
  display: flex;
  gap: 24rpx;
  justify-content: space-around;

  .ratio {
    flex: 1;
    padding: 20rpx 16rpx;
    background: #f6f7f9;
    border-radius: $radius-md;
    border: 2rpx solid transparent;
    text-align: center;
    font-size: 24rpx;
    color: $text-regular;

    .ratio-box {
      margin: 0 auto 12rpx;
      background: $text-placeholder;
      border-radius: 6rpx;

      &.portrait {
        width: 36rpx;
        height: 64rpx;
      }
      &.landscape {
        width: 64rpx;
        height: 36rpx;
      }
      &:not(.portrait):not(.landscape) {
        width: 48rpx;
        height: 48rpx;
      }
    }

    &.active {
      background: $brand-primary-light;
      border-color: $brand-primary;

      .ratio-box {
        background: $brand-primary;
      }
    }
  }
}

.enhance-card {
  .enhance-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 16rpx;
  }
  .enhance-info {
    flex: 1;
    min-width: 0;
  }
  .enhance-title {
    font-size: 28rpx;
    font-weight: 600;
    color: $text-primary;
  }
  .enhance-desc {
    margin-top: 6rpx;
    font-size: 22rpx;
    color: $text-secondary;
    line-height: 1.5;
  }
  .enhance-tip {
    margin-top: 16rpx;
    font-size: 20rpx;
    color: $text-placeholder;
  }
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

    &.warn {
      margin-top: 8rpx;
      color: $danger;
      font-weight: 500;
    }
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
