<template>
  <view class="page">
    <!-- 主表单：照片 + 名称 + 价格 + 分类 -->
    <view class="card">
      <view class="pic-area">
        <view
          v-if="form.picUrl"
          class="pic-wrap"
          :style="{ backgroundImage: `url(${form.picUrl})` }"
          @click="pickImage"
        >
          <view class="pic-replace">点击替换</view>
        </view>
        <view v-else class="pic-add" @click="pickImage">
          <view class="plus">＋</view>
          <text>拍张照</text>
        </view>
      </view>
    </view>

    <view class="card">
      <view class="field">
        <text class="label">名称</text>
        <input
          class="input"
          maxlength="20"
          placeholder="例：现烤蜜薯"
          v-model="form.name"
        />
      </view>
      <view class="field">
        <text class="label">价格</text>
        <view class="input-row">
          <text class="prefix">¥</text>
          <input
            class="input"
            type="digit"
            placeholder="0.00"
            :value="priceYuan"
            @input="onPriceInput"
          />
        </view>
      </view>
      <view class="field">
        <text class="label">分类</text>
        <picker
          mode="selector"
          :value="categoryIdx"
          :range="categories"
          range-key="name"
          @change="onPickCategory"
        >
          <view class="picker-row">
            <text class="picker-text">{{ categories[categoryIdx].name }}</text>
            <text class="arrow">›</text>
          </view>
        </picker>
      </view>
    </view>

    <!-- 高级设置（折叠） -->
    <view class="card advanced">
      <view class="advanced-head" @click="advancedOpen = !advancedOpen">
        <text class="advanced-title">高级设置</text>
        <text class="advanced-state">{{ advancedHint }}</text>
        <text class="arrow" :class="{ open: advancedOpen }">›</text>
      </view>
      <view v-if="advancedOpen" class="advanced-body">
        <view class="switch-row">
          <view class="switch-body">
            <view class="switch-title">参与返利</view>
            <view class="switch-desc">分享后下单，按全局比例给上线分成</view>
          </view>
          <switch
            :checked="form.brokerageEnabled"
            color="#FF6B35"
            @change="(e) => (form.brokerageEnabled = e.detail.value)"
          />
        </view>
        <view class="switch-row">
          <view class="switch-body">
            <view class="switch-title">参与推N返一</view>
            <view class="switch-desc">推 N 个新会员付费，返 1 年订阅给推荐人</view>
          </view>
          <switch
            :checked="form.pushBackEnabled"
            color="#FF6B35"
            @change="(e) => (form.pushBackEnabled = e.detail.value)"
          />
        </view>
        <view class="field">
          <text class="label">库存</text>
          <input
            class="input"
            type="number"
            placeholder="留空 = 无限量"
            v-model="form.stock"
          />
        </view>
        <view class="field last">
          <text class="label">一句话简介</text>
          <input
            class="input"
            maxlength="50"
            placeholder="选填，例：现烤现卖"
            v-model="form.introduction"
          />
        </view>
      </view>
    </view>

    <view class="actions safe-bottom">
      <button v-if="isEdit" class="btn ghost" @click="onDelete">删除</button>
      <button class="btn primary" :disabled="!canSubmit" @click="onSubmit">
        {{ isEdit ? '保存' : '上架' }}
      </button>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import {
  CATEGORIES,
  DEFAULT_CATEGORY_ID,
  createSpu,
  deleteSpu,
  getSpu,
  updateSpu,
} from '../../api/product.js';

const categories = CATEGORIES;
const isEdit = ref(false);
const editingId = ref(0);
const advancedOpen = ref(false);

const form = reactive({
  picUrl: '',
  name: '',
  price: 0,
  categoryId: DEFAULT_CATEGORY_ID,
  stock: '',
  introduction: '',
  brokerageEnabled: true,
  pushBackEnabled: false,
});

const categoryIdx = computed(() => {
  const i = categories.findIndex((c) => c.id === form.categoryId);
  return i >= 0 ? i : 0;
});

const priceYuan = computed(() =>
  form.price ? (form.price / 100).toFixed(2) : ''
);

const canSubmit = computed(
  () => form.picUrl && form.name.trim() && form.price > 0
);

const advancedHint = computed(() => {
  const tags = [];
  if (form.brokerageEnabled) tags.push('返利');
  if (form.pushBackEnabled) tags.push('推N返一');
  if (form.stock) tags.push(`库存 ${form.stock}`);
  return tags.join(' · ') || '默认';
});

function onPickCategory(e) {
  form.categoryId = categories[Number(e.detail.value)].id;
}

function onPriceInput(e) {
  const v = e.detail.value || '';
  const n = parseFloat(v);
  form.price = isNaN(n) ? 0 : Math.round(n * 100);
}

function pickImage() {
  uni.chooseImage({
    count: 1,
    success: (r) => {
      form.picUrl = r.tempFilePaths[0];
    },
  });
}

async function loadIfEdit(id) {
  const s = await getSpu(id);
  if (!s) {
    uni.showToast({ title: '商品不存在', icon: 'none' });
    return;
  }
  form.picUrl = s.picUrl;
  form.name = s.name;
  form.price = s.price;
  form.categoryId = s.categoryId || DEFAULT_CATEGORY_ID;
  form.stock = s.stock === 9999 ? '' : String(s.stock);
  form.introduction = s.introduction;
  form.brokerageEnabled = s.brokerageEnabled;
  form.pushBackEnabled = s.pushBackEnabled;
  // 编辑态如果有非默认设置，自动展开高级
  if (!form.brokerageEnabled || form.pushBackEnabled || form.stock || form.introduction) {
    advancedOpen.value = true;
  }
  uni.setNavigationBarTitle({ title: '编辑商品' });
}

async function onSubmit() {
  if (!canSubmit.value) return;
  uni.showLoading({ title: isEdit.value ? '保存中' : '上架中' });
  const payload = {
    ...form,
    stock: form.stock ? Number(form.stock) : 9999,
  };
  try {
    if (isEdit.value) {
      await updateSpu({ id: editingId.value, ...payload });
      uni.hideLoading();
      uni.showToast({ title: '已保存', icon: 'success' });
    } else {
      await createSpu(payload);
      uni.hideLoading();
      uni.showToast({ title: '上架成功', icon: 'success' });
    }
    setTimeout(() => uni.navigateBack(), 800);
  } catch (e) {
    uni.hideLoading();
  }
}

async function onDelete() {
  const r = await uni.showModal({
    title: '删除商品',
    content: '删除后无法恢复，确认删除？',
    confirmColor: '#EF4444',
  });
  if (!r.confirm) return;
  await deleteSpu(editingId.value);
  uni.showToast({ title: '已删除', icon: 'success' });
  setTimeout(() => uni.navigateBack(), 800);
}

onLoad((q) => {
  if (q.id) {
    isEdit.value = true;
    editingId.value = Number(q.id);
    loadIfEdit(editingId.value);
  } else {
    uni.setNavigationBarTitle({ title: '上架商品' });
  }
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
  padding: 28rpx 32rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.pic-area {
  display: flex;
  justify-content: center;
}

.pic-wrap {
  position: relative;
  width: 360rpx;
  height: 360rpx;
  border-radius: $radius-lg;
  background-size: cover;
  background-position: center;

  .pic-replace {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    height: 60rpx;
    line-height: 60rpx;
    text-align: center;
    background: rgba(0, 0, 0, 0.5);
    color: #fff;
    font-size: 22rpx;
    border-radius: 0 0 $radius-lg $radius-lg;
  }
}

.pic-add {
  width: 360rpx;
  height: 360rpx;
  border: 2rpx dashed $text-placeholder;
  border-radius: $radius-lg;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: $text-secondary;
  font-size: 26rpx;
  background: #fafbfc;

  .plus {
    font-size: 96rpx;
    color: $brand-primary;
    line-height: 1;
    margin-bottom: 16rpx;
    font-weight: 200;
  }
}

.field {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 20rpx 0;
  border-bottom: 1rpx solid $border-color;

  &.last,
  &:last-child {
    border-bottom: none;
  }

  .label {
    flex-shrink: 0;
    width: 140rpx;
    font-size: 28rpx;
    color: $text-regular;
  }
}

.input {
  flex: 1;
  height: 72rpx;
  font-size: 30rpx;
  color: $text-primary;
}

.input-row {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8rpx;

  .prefix {
    font-size: 30rpx;
    color: $text-placeholder;
  }
}

.picker-row {
  flex: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 72rpx;

  .picker-text {
    font-size: 30rpx;
    color: $text-primary;
  }

  .arrow {
    color: $text-placeholder;
    font-size: 36rpx;
  }
}

.advanced {
  padding: 0 32rpx;
}

.advanced-head {
  display: flex;
  align-items: center;
  height: 88rpx;

  .advanced-title {
    font-size: 28rpx;
    color: $text-primary;
    font-weight: 500;
  }

  .advanced-state {
    flex: 1;
    margin-left: 16rpx;
    font-size: 24rpx;
    color: $text-secondary;
  }

  .arrow {
    color: $text-placeholder;
    font-size: 36rpx;
    transition: transform 0.2s;

    &.open {
      transform: rotate(90deg);
    }
  }
}

.advanced-body {
  padding: 0 0 24rpx;
  border-top: 1rpx solid $border-color;
}

.switch-row {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 24rpx 0;
  border-bottom: 1rpx solid $border-color;

  .switch-body {
    flex: 1;
    min-width: 0;
  }

  .switch-title {
    font-size: 28rpx;
    font-weight: 500;
    color: $text-primary;
  }

  .switch-desc {
    margin-top: 6rpx;
    font-size: 22rpx;
    color: $text-secondary;
    line-height: 1.5;
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
  font-size: 32rpx;
  font-weight: 600;
  border-radius: $radius-md;

  &.primary {
    background: $brand-primary;
    color: #fff;
  }

  &.ghost {
    background: #fff;
    color: $danger;
    border: 2rpx solid $danger;
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
