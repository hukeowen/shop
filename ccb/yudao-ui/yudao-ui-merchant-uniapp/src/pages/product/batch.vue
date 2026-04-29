<template>
  <view class="page">
    <!-- 步骤 1：拍照 / 选图 -->
    <view v-if="step === 'pick'" class="card empty">
      <view class="empty-icon">📷</view>
      <view class="empty-title">AI 上架</view>
      <view class="empty-desc">拍一张摊位全景照，AI 会自动识别每一件商品，你只需要填价格</view>
      <button class="btn primary" @click="pickImage">拍张全摊照</button>
      <view class="tip">识别用时约 5–10 秒 · 每张原图先上传到 OSS 给 AI 看</view>
    </view>

    <!-- 步骤 2：AI 识别中 -->
    <view v-else-if="step === 'detecting'" class="card state-card">
      <image class="preview" :src="previewUrl" mode="aspectFit" />
      <view class="spinner"></view>
      <view class="state-text">AI 正在识别…</view>
      <view class="state-sub">{{ detectingTip }}</view>
    </view>

    <!-- 步骤 3：识别结果 -->
    <view v-else-if="step === 'review'">
      <view class="card preview-card">
        <image class="preview" :src="previewUrl" mode="aspectFit" />
        <view class="preview-foot">
          识别到 <text class="strong">{{ items.length }}</text> 件商品
          <text class="reshoot" @click="reshoot">🔄 重拍</text>
        </view>
      </view>

      <view class="card" v-for="(it, i) in items" :key="i">
        <view class="item-head">
          <image class="item-img" :src="it.previewUrl" mode="aspectFill" />
          <view class="item-body">
            <input
              class="item-name"
              v-model="items[i].name"
              maxlength="20"
              placeholder="商品名"
            />
            <view class="item-cat">
              <picker
                mode="selector"
                :value="catIndex(it.categoryId)"
                :range="CATEGORIES"
                range-key="name"
                @change="(e) => onPickCat(i, e)"
              >
                <text class="item-cat-text">{{ it.categoryName }} ›</text>
              </picker>
            </view>
          </view>
          <view class="item-del" @click="removeItem(i)">×</view>
        </view>

        <view class="item-field">
          <text class="fld-label">价格</text>
          <view class="fld-row">
            <text class="prefix">¥</text>
            <input
              class="fld-input"
              type="digit"
              placeholder="必填"
              :value="items[i].priceYuan"
              @input="(e) => onPriceInput(i, e)"
            />
          </view>
        </view>
        <view class="item-field last">
          <text class="fld-label">一句话</text>
          <input
            class="fld-input slim"
            v-model="items[i].introduction"
            maxlength="50"
            placeholder="选填"
          />
        </view>
      </view>

      <view class="actions safe-bottom">
        <button class="btn ghost" @click="reshoot">重新拍</button>
        <button
          class="btn primary"
          :disabled="!canSubmit || submitting"
          @click="onSubmit"
        >
          {{ submitting ? '上架中…' : `批量上架 (${filledCount}/${items.length})` }}
        </button>
      </view>
    </view>

    <!-- 步骤 4：全部完成 -->
    <view v-else-if="step === 'done'" class="card state-card success">
      <view class="state-icon">✓</view>
      <view class="state-text">已批量上架 {{ createdCount }} 件商品</view>
      <button class="btn primary" @click="gotoList">去商品管理</button>
      <button class="btn ghost" @click="reset">再来一批</button>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue';
import { blobUrlToBase64, uploadImage } from '../../api/oss.js';
import { detectProducts } from '../../api/productDetect.js';
import { cropByBbox } from '../../api/cropImage.js';
import { CATEGORIES, createSpu } from '../../api/product.js';

const step = ref('pick'); // pick | detecting | review | done
const previewUrl = ref('');
const originalOssUrl = ref('');
const detectingTip = ref('读取图片…');
const items = ref([]);
const submitting = ref(false);
const createdCount = ref(0);

const filledCount = computed(
  () => items.value.filter((it) => it.price > 0).length
);

const canSubmit = computed(
  () => items.value.length > 0 && items.value.every((it) => it.price > 0 && it.name.trim())
);

function catIndex(id) {
  const i = CATEGORIES.findIndex((c) => c.id === id);
  return i >= 0 ? i : 0;
}

function onPickCat(i, e) {
  const cat = CATEGORIES[Number(e.detail.value)];
  items.value[i].categoryId = cat.id;
  items.value[i].categoryName = cat.name;
}

function onPriceInput(i, e) {
  const v = e.detail.value || '';
  const n = parseFloat(v);
  items.value[i].priceYuan = v;
  items.value[i].price = isNaN(n) ? 0 : Math.round(n * 100);
}

function removeItem(i) {
  items.value.splice(i, 1);
  if (!items.value.length) reshoot();
}

function reset() {
  step.value = 'pick';
  previewUrl.value = '';
  originalOssUrl.value = '';
  items.value = [];
  createdCount.value = 0;
}

function reshoot() {
  reset();
  pickImage();
}

function pickImage() {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['camera', 'album'],
    success: async (r) => {
      const path = r.tempFilePaths[0];
      previewUrl.value = path;
      step.value = 'detecting';
      try {
        detectingTip.value = '上传到 OSS…';
        const base64 = await blobUrlToBase64(path);
        originalOssUrl.value = (await uploadImage(base64, { ext: 'jpg' })).url;
        detectingTip.value = '豆包视觉模型识别中…';
        const detected = await detectProducts(originalOssUrl.value);
        detectingTip.value = `裁切 ${detected.length} 件商品…`;
        const cropped = [];
        for (let i = 0; i < detected.length; i++) {
          try {
            const { previewUrl: pv, base64: b64 } = await cropByBbox(path, detected[i].bbox);
            cropped.push({
              ...detected[i],
              previewUrl: pv,
              cropBase64: b64,
              price: 0,
              priceYuan: '',
            });
          } catch (e) {
            console.warn('裁切失败 #' + i, e.message);
          }
        }
        if (!cropped.length) throw new Error('识别结果均无法裁切');
        items.value = cropped;
        step.value = 'review';
      } catch (e) {
        step.value = 'pick';
        uni.showModal({
          title: 'AI 识别失败',
          content: e.message || '请换一张更清晰的全摊照重试',
          showCancel: false,
        });
      }
    },
  });
}

async function onSubmit() {
  if (!canSubmit.value || submitting.value) return;
  submitting.value = true;
  uni.showLoading({ title: `上架 0/${items.value.length}` });
  let ok = 0;
  try {
    for (const it of items.value) {
      uni.showLoading({ title: `上架 ${ok}/${items.value.length}` });
      // 裁切图上传到 OSS → 得到商品主图 URL
      const { url: picUrl } = await uploadImage(it.cropBase64, { ext: 'jpg' });
      await createSpu({
        picUrl,
        name: it.name.trim(),
        price: it.price,
        categoryId: it.categoryId,
        stock: null,
        introduction: it.introduction?.trim() || '',
        brokerageEnabled: true,
        pushBackEnabled: false,
      });
      ok += 1;
    }
    createdCount.value = ok;
    step.value = 'done';
  } catch (e) {
    uni.showModal({
      title: `上架中断：已成功 ${ok} 件`,
      content: e.message,
      showCancel: false,
    });
  } finally {
    uni.hideLoading();
    submitting.value = false;
  }
}

function gotoList() {
  uni.redirectTo({ url: '/pages/product/list' });
}
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
  padding: 24rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.empty {
  text-align: center;
  padding: 80rpx 32rpx;

  .empty-icon {
    font-size: 96rpx;
    margin-bottom: 20rpx;
  }

  .empty-title {
    font-size: 36rpx;
    font-weight: 700;
    color: $text-primary;
  }

  .empty-desc {
    margin-top: 12rpx;
    font-size: 26rpx;
    color: $text-secondary;
    line-height: 1.6;
  }

  .btn {
    margin-top: 40rpx;
    width: 100%;
  }

  .tip {
    margin-top: 20rpx;
    font-size: 22rpx;
    color: $text-placeholder;
  }
}

.state-card {
  text-align: center;
  padding: 48rpx 32rpx;

  .preview {
    width: 100%;
    max-height: 500rpx;
    margin-bottom: 24rpx;
    border-radius: $radius-md;
    background: #f6f7f9;
  }

  .spinner {
    width: 64rpx;
    height: 64rpx;
    margin: 0 auto 20rpx;
    border: 5rpx solid $brand-primary-light;
    border-top-color: $brand-primary;
    border-radius: 50%;
    animation: spin 1s linear infinite;
  }

  .state-icon {
    width: 80rpx;
    height: 80rpx;
    line-height: 80rpx;
    margin: 0 auto 20rpx;
    background: $success;
    color: #fff;
    font-size: 48rpx;
    border-radius: 50%;
  }

  .state-text {
    font-size: 30rpx;
    font-weight: 600;
    color: $text-primary;
  }

  .state-sub {
    margin-top: 8rpx;
    font-size: 24rpx;
    color: $text-secondary;
  }

  .btn {
    margin-top: 32rpx;
  }

  .btn + .btn {
    margin-top: 12rpx;
  }

  &.success .state-icon {
    background: $success;
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.preview-card {
  .preview {
    width: 100%;
    max-height: 540rpx;
    border-radius: $radius-md;
    background: #000;
  }

  .preview-foot {
    margin-top: 16rpx;
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 24rpx;
    color: $text-secondary;

    .strong {
      color: $brand-primary;
      font-weight: 700;
      font-size: 30rpx;
      margin: 0 4rpx;
    }

    .reshoot {
      color: $brand-primary;
      font-size: 24rpx;
    }
  }
}

.item-head {
  display: flex;
  gap: 16rpx;
  align-items: flex-start;
  padding-bottom: 16rpx;

  .item-img {
    width: 140rpx;
    height: 140rpx;
    border-radius: $radius-md;
    background: #f0f0f0;
    flex-shrink: 0;
  }

  .item-body {
    flex: 1;
    min-width: 0;

    .item-name {
      width: 100%;
      font-size: 30rpx;
      font-weight: 600;
      color: $text-primary;
      padding: 8rpx 0;
      border-bottom: 1rpx solid $border-color;
    }

    .item-cat {
      margin-top: 10rpx;

      .item-cat-text {
        display: inline-block;
        padding: 4rpx 16rpx;
        font-size: 22rpx;
        color: $brand-primary-dark;
        background: $brand-primary-light;
        border-radius: $radius-pill;
      }
    }
  }

  .item-del {
    width: 56rpx;
    height: 56rpx;
    line-height: 56rpx;
    text-align: center;
    background: #f0f1f4;
    border-radius: 50%;
    font-size: 32rpx;
    color: $text-placeholder;
    flex-shrink: 0;
  }
}

.item-field {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 0;
  border-top: 1rpx solid $border-color;

  &.last {
    padding-bottom: 0;
  }

  .fld-label {
    flex-shrink: 0;
    width: 120rpx;
    font-size: 26rpx;
    color: $text-regular;
  }

  .fld-row {
    flex: 1;
    display: flex;
    align-items: center;
    gap: 8rpx;

    .prefix {
      font-size: 28rpx;
      color: $text-placeholder;
    }
  }

  .fld-input {
    flex: 1;
    font-size: 28rpx;
    color: $text-primary;

    &.slim {
      font-size: 26rpx;
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
  font-weight: 600;
  border-radius: $radius-md;

  &.primary {
    background: $brand-primary;
    color: #fff;
  }

  &.ghost {
    background: #fff;
    color: $brand-primary;
    border: 2rpx solid $brand-primary;
    flex: 0 0 200rpx;
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
