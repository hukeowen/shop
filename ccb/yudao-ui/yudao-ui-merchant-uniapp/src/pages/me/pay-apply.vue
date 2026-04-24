<template>
  <view class="page">
    <!-- 状态卡片 -->
    <view class="card status-card" v-if="shop">
      <view class="status-row">
        <text class="status-label">当前状态</text>
        <text class="status-val" :class="statusClass">{{ statusText }}</text>
      </view>
      <view class="status-row" v-if="shop.payApplyRejectReason">
        <text class="status-label">驳回原因</text>
        <text class="status-val danger">{{ shop.payApplyRejectReason }}</text>
      </view>
      <view class="status-row" v-if="shop.tlMchId">
        <text class="status-label">通联商户号</text>
        <text class="status-val">{{ shop.tlMchId }}</text>
      </view>
      <view class="status-row" v-if="shop.tlMchKey">
        <text class="status-label">密钥（脱敏）</text>
        <text class="status-val">{{ shop.tlMchKey }}</text>
      </view>
    </view>

    <!-- 说明 -->
    <view class="card desc-card">
      <view class="desc-title">关于在线支付</view>
      <view class="desc-body">
        <view class="desc-item">· 开通后用户可通过微信支付在线下单</view>
        <view class="desc-item">· 未开通时订单走「到店付款」，您手动确认收款</view>
        <view class="desc-item">· 需提供通联支付平台分配的商户号和密钥</view>
        <view class="desc-item">· 平台审核通常 1-2 个工作日完成</view>
      </view>
    </view>

    <!-- 申请表单（未申请或已驳回时显示） -->
    <view class="card form-card" v-if="canApply">
      <view class="form-title">{{ shop && shop.payApplyStatus === 3 ? '重新申请' : '提交申请' }}</view>

      <view class="field">
        <text class="field-label">通联商户号</text>
        <input class="field-input" v-model="form.tlMchId" placeholder="请输入商户号" />
      </view>
      <view class="field">
        <text class="field-label">通联密钥</text>
        <input class="field-input" v-model="form.tlMchKey" placeholder="请输入密钥（不会明文存储）" password />
      </view>

      <button class="submit-btn" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中...' : '提交开通申请' }}
      </button>
    </view>

    <!-- 已提交等待审核 -->
    <view class="card tip-card" v-if="shop && shop.payApplyStatus === 1">
      <text class="tip">申请已提交，请等待平台审核（通常 1-2 个工作日）</text>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { request } from '../../api/request.js';

const BASE = '/app-api/merchant/mini/shop';

const shop = ref(null);
const form = ref({ tlMchId: '', tlMchKey: '' });
const submitting = ref(false);

const statusText = computed(() => {
  const s = shop.value?.payApplyStatus;
  if (s == null) return '未申请';
  return { 0: '未申请', 1: '审核中', 2: '已开通', 3: '已驳回' }[s] ?? '-';
});

const statusClass = computed(() => {
  const s = shop.value?.payApplyStatus;
  if (s == null || s === 0) return 'gray';
  return { 1: 'pending', 2: 'success', 3: 'danger' }[s] ?? '';
});

const canApply = computed(() => {
  const s = shop.value?.payApplyStatus;
  return s == null || s === 0 || s === 3;
});

async function load() {
  try {
    shop.value = await request({ url: `${BASE}/pay-apply` });
  } catch {}
}

async function submit() {
  if (!form.value.tlMchId.trim() || !form.value.tlMchKey.trim()) {
    uni.showToast({ title: '请填写商户号和密钥', icon: 'none' });
    return;
  }
  submitting.value = true;
  try {
    const params = new URLSearchParams({
      tlMchId: form.value.tlMchId.trim(),
      tlMchKey: form.value.tlMchKey.trim(),
    });
    await request({ url: `${BASE}/pay-apply?${params}`, method: 'POST' });
    uni.showToast({ title: '申请已提交', icon: 'success' });
    form.value = { tlMchId: '', tlMchKey: '' };
    load();
  } catch {
    // toast from request.js
  } finally {
    submitting.value = false;
  }
}

onShow(() => load());
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  padding: 24rpx;
  min-height: 100vh;
}

.card {
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 28rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.status-card .status-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12rpx 0;
  border-bottom: 1rpx solid $border-color;

  &:last-child { border-bottom: none; }

  .status-label { font-size: 26rpx; color: $text-secondary; }

  .status-val {
    font-size: 26rpx; color: $text-primary;

    &.gray { color: $text-placeholder; }
    &.pending { color: #F59E0B; font-weight: 600; }
    &.success { color: #10B981; font-weight: 600; }
    &.danger { color: $danger; }
  }
}

.desc-card {
  .desc-title {
    font-size: 28rpx;
    font-weight: 700;
    color: $text-primary;
    margin-bottom: 16rpx;
  }

  .desc-item {
    font-size: 24rpx;
    color: $text-secondary;
    line-height: 48rpx;
  }
}

.form-card {
  .form-title {
    font-size: 28rpx;
    font-weight: 700;
    color: $text-primary;
    margin-bottom: 20rpx;
  }
}

.field {
  display: flex;
  align-items: center;
  min-height: 88rpx;
  border-bottom: 1rpx solid $border-color;

  .field-label {
    font-size: 28rpx;
    color: $text-secondary;
    width: 180rpx;
    flex-shrink: 0;
  }

  .field-input {
    flex: 1;
    font-size: 28rpx;
    color: $text-primary;
    text-align: right;
  }
}

.submit-btn {
  margin-top: 32rpx;
  height: 88rpx;
  line-height: 88rpx;
  background: $brand-primary;
  color: #fff;
  border-radius: $radius-pill;
  font-size: 30rpx;
  font-weight: 600;

  &[disabled] { opacity: 0.5; }
  &::after { border: none; }
}

.tip-card .tip {
  font-size: 26rpx;
  color: #F59E0B;
  line-height: 1.6;
}
</style>
