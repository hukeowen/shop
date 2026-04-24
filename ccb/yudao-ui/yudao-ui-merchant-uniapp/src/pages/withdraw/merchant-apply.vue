<template>
  <view class="page">
    <view class="card form-card">
      <view class="section-title">发起提现</view>

      <view class="field">
        <text class="field-label">提现金额（元）</text>
        <input class="field-input" type="digit" v-model="form.amountYuan" placeholder="请输入金额" />
      </view>

      <view class="field">
        <text class="field-label">提现方式</text>
        <picker :range="withdrawTypes" range-key="label" @change="onTypeChange">
          <view class="picker-val">{{ withdrawTypes[form.withdrawTypeIdx].label }} ›</view>
        </picker>
      </view>

      <view class="field" v-if="form.withdrawType === 3">
        <text class="field-label">开户姓名</text>
        <input class="field-input" v-model="form.accountName" placeholder="银行开户人姓名" />
      </view>

      <view class="field" v-if="form.withdrawType === 3">
        <text class="field-label">银行名称</text>
        <input class="field-input" v-model="form.bankName" placeholder="如：工商银行" />
      </view>

      <view class="field">
        <text class="field-label">{{ form.withdrawType === 3 ? '银行卡号' : '收款码/账号' }}</text>
        <input class="field-input" v-model="form.accountNo" placeholder="收款账号或上传收款码" />
      </view>

      <button class="submit-btn" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中...' : '提交申请' }}
      </button>
    </view>

    <view class="section-title history-title">申请记录</view>

    <view v-for="item in list" :key="item.id" class="card history-item">
      <view class="row">
        <text class="label">金额</text>
        <text class="val amount">¥{{ fen2yuan(item.amount) }}</text>
      </view>
      <view class="row">
        <text class="label">方式</text>
        <text class="val">{{ withdrawTypeText(item.withdrawType) }}</text>
      </view>
      <view class="row" v-if="item.accountName">
        <text class="label">姓名</text>
        <text class="val">{{ item.accountName }}</text>
      </view>
      <view class="row" v-if="item.bankName">
        <text class="label">银行</text>
        <text class="val">{{ item.bankName }}</text>
      </view>
      <view class="row">
        <text class="label">状态</text>
        <text class="val" :class="statusClass(item.status)">{{ statusText(item.status) }}</text>
      </view>
      <view class="row" v-if="item.rejectReason">
        <text class="label">驳回原因</text>
        <text class="val danger">{{ item.rejectReason }}</text>
      </view>
      <view class="row" v-if="item.voucherUrl">
        <text class="label">转账凭证</text>
        <image class="voucher" :src="item.voucherUrl" mode="widthFix" @click="previewVoucher(item.voucherUrl)" />
      </view>
      <view class="row">
        <text class="label">申请时间</text>
        <text class="val">{{ formatTime(item.createTime) }}</text>
      </view>
    </view>

    <view v-if="!list.length && !historyLoading" class="empty">暂无申请记录</view>

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

const withdrawTypes = [
  { label: '微信扫码', value: 1 },
  { label: '支付宝扫码', value: 2 },
  { label: '银行转账', value: 3 },
];

const form = ref({
  amountYuan: '',
  withdrawTypeIdx: 0,
  withdrawType: 1,
  accountName: '',
  accountNo: '',
  bankName: '',
});
const submitting = ref(false);

const list = ref([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = 10;
const historyLoading = ref(false);

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)));

function onTypeChange(e) {
  form.value.withdrawTypeIdx = e.detail.value;
  form.value.withdrawType = withdrawTypes[e.detail.value].value;
}

function withdrawTypeText(type) {
  return { 1: '微信', 2: '支付宝', 3: '银行转账' }[type] || '未知';
}

function statusText(s) {
  return { 0: '待审核', 1: '已转账', 2: '已驳回' }[s] || '-';
}

function statusClass(s) {
  return { 0: 'pending', 1: 'success', 2: 'danger' }[s] || '';
}

function formatTime(t) {
  if (!t) return '';
  return String(t).replace('T', ' ').slice(0, 16);
}

function previewVoucher(url) {
  uni.previewImage({ urls: [url] });
}

async function submit() {
  const yuan = parseFloat(form.value.amountYuan);
  if (!yuan || yuan <= 0) {
    uni.showToast({ title: '请输入有效金额', icon: 'none' });
    return;
  }
  const amount = Math.round(yuan * 100);
  submitting.value = true;
  try {
    const params = new URLSearchParams({
      amount,
      withdrawType: form.value.withdrawType,
    });
    if (form.value.accountName) params.set('accountName', form.value.accountName);
    if (form.value.accountNo) params.set('accountNo', form.value.accountNo);
    if (form.value.bankName) params.set('bankName', form.value.bankName);

    await request({ url: `${BASE}/merchant/create?${params}`, method: 'POST' });
    uni.showToast({ title: '申请已提交', icon: 'success' });
    form.value.amountYuan = '';
    form.value.accountNo = '';
    form.value.accountName = '';
    form.value.bankName = '';
    pageNo.value = 1;
    loadHistory();
  } catch {
    // toast from request.js
  } finally {
    submitting.value = false;
  }
}

async function loadHistory() {
  historyLoading.value = true;
  try {
    const data = await request({
      url: `${BASE}/merchant/page?pageNo=${pageNo.value}&pageSize=${pageSize}`,
    });
    list.value = data.list || [];
    total.value = data.total || 0;
  } catch {} finally {
    historyLoading.value = false;
  }
}

function prevPage() { if (pageNo.value > 1) { pageNo.value--; loadHistory(); } }
function nextPage() { if (pageNo.value < totalPages.value) { pageNo.value++; loadHistory(); } }

onShow(() => {
  pageNo.value = 1;
  loadHistory();
});
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
  padding: 24rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.section-title {
  font-size: 28rpx;
  font-weight: 700;
  color: $text-primary;
  margin-bottom: 24rpx;
}

.history-title { margin-top: 8rpx; }

.field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 88rpx;
  border-bottom: 1rpx solid $border-color;

  &:last-of-type { border-bottom: none; }

  .field-label {
    font-size: 28rpx;
    color: $text-secondary;
    flex-shrink: 0;
    width: 180rpx;
  }

  .field-input {
    flex: 1;
    font-size: 28rpx;
    color: $text-primary;
    text-align: right;
  }

  .picker-val {
    font-size: 28rpx;
    color: $text-primary;
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

.history-item .row {
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
    &.pending { color: #F59E0B; }
    &.success { color: #10B981; }
    &.danger { color: $danger; }
  }
}

.voucher {
  width: 160rpx;
  border-radius: $radius-md;
}

.empty {
  padding: 80rpx 0;
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
