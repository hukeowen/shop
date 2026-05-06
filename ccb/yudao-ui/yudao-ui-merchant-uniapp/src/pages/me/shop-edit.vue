<template>
  <view class="page">
    <view v-if="loading" class="empty-tip">加载中...</view>
    <template v-else>
      <view class="card section">
        <view class="field">
          <text class="label">店铺名称</text>
          <input class="input" v-model="form.shopName" placeholder="请输入店铺名称" />
        </view>
        <view class="field">
          <text class="label">客服电话</text>
          <input class="input" type="number" v-model="form.mobile" placeholder="请输入客服电话" />
        </view>
        <view class="field">
          <text class="label">营业时间</text>
          <input class="input" v-model="form.businessHours" placeholder="如：09:00-22:00" />
        </view>
        <view class="field">
          <text class="label">详细地址</text>
          <input class="input" v-model="form.address" placeholder="请输入店铺地址" />
        </view>
        <view class="field">
          <text class="label">店铺简介</text>
          <textarea class="textarea" v-model="form.description" placeholder="请输入店铺简介" />
        </view>
        <view class="field">
          <text class="label">店铺公告</text>
          <textarea class="textarea" v-model="form.notice" placeholder="请输入店铺公告" />
        </view>
        <view class="field">
          <text class="label">特色标签</text>
          <input
            class="input"
            v-model="form.featureTags"
            placeholder="逗号分隔，最多 6 个，如：炭火现烤,现做现卖,不赶时间"
            maxlength="64"
          />
          <text class="hint">用户进店时显示在店铺信息卡上（第 1 个会高亮带 🔥）</text>
        </view>
      </view>

      <view class="bottom-bar safe-bottom">
        <view class="save-btn" @click="save">保存</view>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { request } from '../../api/request.js';

const loading = ref(true);
const form = ref({
  shopName: '',
  mobile: '',
  businessHours: '',
  address: '',
  description: '',
  notice: '',
  featureTags: '',
});

onLoad(async () => {
  try {
    const res = await request({ url: '/app-api/merchant/mini/shop/info' });
    if (res) {
      form.value.shopName = res.shopName || '';
      form.value.mobile = res.mobile || '';
      form.value.businessHours = res.businessHours || '';
      form.value.address = res.address || '';
      form.value.description = res.description || '';
      form.value.notice = res.notice || '';
      form.value.featureTags = res.featureTags || '';
    }
  } catch {}
  loading.value = false;
});

async function save() {
  if (!form.value.shopName?.trim()) {
    uni.showToast({ title: '店铺名称不能为空', icon: 'none' });
    return;
  }
  try {
    await request({
      url: '/app-api/merchant/mini/shop/info',
      method: 'PUT',
      data: form.value,
    });
    uni.showToast({ title: '保存成功', icon: 'success' });
    setTimeout(() => uni.navigateBack(), 1000);
  } catch {}
}
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  min-height: 100vh;
  padding: 24rpx;
  padding-bottom: 140rpx;
}

.empty-tip {
  text-align: center;
  color: $text-placeholder;
  padding: 120rpx 0;
  font-size: 28rpx;
}

.card {
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 16rpx 32rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.field {
  display: flex;
  align-items: flex-start;
  padding: 24rpx 0;
  border-bottom: 1rpx solid $border-color;

  &:last-child {
    border-bottom: none;
  }
}

.label {
  width: 160rpx;
  font-size: 28rpx;
  color: $text-secondary;
  flex-shrink: 0;
  padding-top: 4rpx;
}

.input {
  flex: 1;
  font-size: 28rpx;
  color: $text-primary;
  height: 56rpx;
}

.textarea {
  flex: 1;
  font-size: 28rpx;
  color: $text-primary;
  min-height: 120rpx;
  line-height: 1.6;
}

.hint {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: $text-secondary;
  line-height: 1.4;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 16rpx 32rpx;
  background: #fff;
  border-top: 1rpx solid $border-color;
}

.save-btn {
  background: $brand-primary;
  color: #fff;
  border-radius: $radius-pill;
  padding: 24rpx 0;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
}
</style>
