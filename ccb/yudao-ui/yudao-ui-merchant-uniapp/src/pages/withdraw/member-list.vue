<template>
  <view class="page">
    <view class="tabs">
      <view
        v-for="tab in tabs"
        :key="tab.value"
        class="tab"
        :class="{ active: activeTab === tab.value }"
        @click="switchTab(tab.value)"
      >{{ tab.label }}</view>
    </view>

    <view v-for="item in list" :key="item.id" class="card item">
      <view class="row">
        <text class="label">提现金额</text>
        <text class="val amount">¥{{ fen2yuan(item.amount) }}</text>
      </view>
      <view class="row">
        <text class="label">提现方式</text>
        <text class="val">{{ withdrawTypeText(item.withdrawType) }}</text>
      </view>
      <view class="row" v-if="item.accountName">
        <text class="label">姓名</text>
        <text class="val">{{ item.accountName }}</text>
      </view>
      <view class="row" v-if="item.accountNo">
        <text class="label">账号</text>
        <text class="val">{{ item.accountNo }}</text>
      </view>
      <view class="row" v-if="item.bankName">
        <text class="label">银行</text>
        <text class="val">{{ item.bankName }}</text>
      </view>
      <view class="row">
        <text class="label">申请时间</text>
        <text class="val">{{ formatTime(item.createTime) }}</text>
      </view>
      <view class="row" v-if="item.voucherUrl && activeTab === 1">
        <text class="label">转账凭证</text>
        <text class="val link">{{ item.voucherUrl }}</text>
      </view>
      <view class="row" v-if="item.rejectReason && activeTab === 2">
        <text class="label">驳回原因</text>
        <text class="val danger">{{ item.rejectReason }}</text>
      </view>

      <view class="actions" v-if="activeTab === 0">
        <button class="btn approve" @click="approvePrompt(item)">通过</button>
        <button class="btn reject" @click="rejectPrompt(item)">驳回</button>
      </view>
    </view>

    <view v-if="!list.length && !loading" class="empty">暂无提现申请</view>

    <view class="pagination" v-if="total > pageSize">
      <button class="page-btn" :disabled="pageNo <= 1" @click="prevPage">上一页</button>
      <text class="page-info">{{ pageNo }} / {{ totalPages }}</text>
      <button class="page-btn" :disabled="pageNo >= totalPages" @click="nextPage">下一页</button>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { request } from '../../api/request.js';
import { fen2yuan } from '../../utils/format.js';

const BASE = '/app-api/merchant/mini/withdraw';

const tabs = [
  { label: '待审核', value: 0 },
  { label: '已打款', value: 1 },
  { label: '已驳回', value: 2 },
];
const activeTab = ref(0);
const list = ref([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = 10;
const loading = ref(false);

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)));

function withdrawTypeText(type) {
  return { 1: '微信', 2: '支付宝', 3: '银行卡' }[type] || '未知';
}

function formatTime(t) {
  if (!t) return '';
  return String(t).replace('T', ' ').slice(0, 16);
}

async function load() {
  loading.value = true;
  try {
    const data = await request({
      url: `${BASE}/member/page?status=${activeTab.value}&pageNo=${pageNo.value}&pageSize=${pageSize}`,
    });
    list.value = data.list || [];
    total.value = data.total || 0;
  } catch {
    // toast shown by request.js
  } finally {
    loading.value = false;
  }
}

function switchTab(val) {
  activeTab.value = val;
  pageNo.value = 1;
  load();
}

function prevPage() {
  if (pageNo.value > 1) { pageNo.value--; load(); }
}
function nextPage() {
  if (pageNo.value < totalPages.value) { pageNo.value++; load(); }
}

async function approvePrompt(item) {
  const r = await uni.showModal({
    title: '填写转账凭证',
    content: '请输入凭证URL',
    editable: true,
    placeholderText: '请输入转账凭证截图URL',
  });
  if (!r.confirm) return;
  const voucherUrl = r.content || '';
  if (!voucherUrl) {
    uni.showToast({ title: '请填写凭证URL', icon: 'none' });
    return;
  }
  try {
    await request({
      url: `${BASE}/member/approve?id=${item.id}&voucherUrl=${encodeURIComponent(voucherUrl)}`,
      method: 'POST',
    });
    uni.showToast({ title: '已通过', icon: 'success' });
    load();
  } catch {}
}

async function rejectPrompt(item) {
  const r = await uni.showModal({
    title: '驳回原因',
    content: '请确认驳回',
    editable: true,
    placeholderText: '请输入驳回原因',
  });
  if (!r.confirm) return;
  const reason = r.content || '不符合提现条件';
  try {
    await request({
      url: `${BASE}/member/reject?id=${item.id}&rejectReason=${encodeURIComponent(reason)}`,
      method: 'POST',
    });
    uni.showToast({ title: '已驳回', icon: 'success' });
    load();
  } catch {}
}

onShow(() => {
  pageNo.value = 1;
  load();
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  padding: 24rpx;
  min-height: 100vh;
}

.tabs {
  display: flex;
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 8rpx;
  margin-bottom: 24rpx;
  gap: 8rpx;

  .tab {
    flex: 1;
    text-align: center;
    height: 64rpx;
    line-height: 64rpx;
    border-radius: $radius-md;
    font-size: 26rpx;
    color: $text-secondary;

    &.active {
      background: $brand-primary;
      color: #fff;
      font-weight: 600;
    }
  }
}

.card {
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 24rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10rpx 0;
  border-bottom: 1rpx solid $border-color;

  &:last-of-type { border-bottom: none; }

  .label {
    font-size: 26rpx;
    color: $text-secondary;
    flex-shrink: 0;
    margin-right: 16rpx;
  }

  .val {
    font-size: 26rpx;
    color: $text-primary;
    text-align: right;

    &.amount { color: $brand-primary; font-weight: 700; font-size: 30rpx; }
    &.danger { color: $danger; }
    &.link { color: $brand-primary; word-break: break-all; }
  }
}

.actions {
  display: flex;
  gap: 16rpx;
  margin-top: 24rpx;

  .btn {
    flex: 1;
    height: 72rpx;
    line-height: 72rpx;
    border-radius: $radius-pill;
    font-size: 28rpx;
    font-weight: 600;

    &.approve { background: $brand-primary; color: #fff; }
    &.reject { background: #fff; color: $danger; border: 1rpx solid $danger; }
    &::after { border: none; }
  }
}

.empty {
  padding: 120rpx 0;
  text-align: center;
  color: $text-placeholder;
  font-size: 26rpx;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 24rpx;
  padding: 32rpx 0;

  .page-btn {
    height: 64rpx;
    line-height: 64rpx;
    padding: 0 32rpx;
    background: $brand-primary;
    color: #fff;
    border-radius: $radius-pill;
    font-size: 26rpx;

    &[disabled] { background: $text-placeholder; opacity: 0.6; }
    &::after { border: none; }
  }

  .page-info { font-size: 26rpx; color: $text-secondary; }
}
</style>
