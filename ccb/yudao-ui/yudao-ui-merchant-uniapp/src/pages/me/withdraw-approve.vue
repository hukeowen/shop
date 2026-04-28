<template>
  <view class="page">
    <!-- 状态 tab -->
    <view class="tabs">
      <view
        v-for="t in tabs"
        :key="t.value"
        :class="['tab', tab === t.value && 'active']"
        @click="switchTab(t.value)"
      >
        {{ t.label }}
      </view>
    </view>

    <view class="list">
      <view v-if="!records.length && !loading" class="empty">暂无申请</view>
      <view v-for="r in records" :key="r.id" class="card">
        <view class="row1">
          <text class="amt">{{ r.amount }} 分</text>
          <text :class="['st', r.status.toLowerCase()]">{{ statusLabel(r.status) }}</text>
        </view>
        <view class="row2">
          <text>用户 ID：{{ r.userId }}</text>
          <text>申请：{{ formatTime(r.applyAt) }}</text>
        </view>
        <view v-if="r.processedAt" class="row3">
          处理：{{ formatTime(r.processedAt) }}{{ r.processorRemark ? ' · ' + r.processorRemark : '' }}
        </view>

        <!-- 操作按钮按状态显示 -->
        <view class="actions" v-if="r.status === 'PENDING'">
          <button class="btn ghost danger" @click="onReject(r)">驳回</button>
          <button class="btn primary" @click="onApprove(r)">通过</button>
        </view>
        <view class="actions" v-else-if="r.status === 'APPROVED'">
          <button class="btn primary" @click="onMarkPaid(r)">标记已打款</button>
        </view>
      </view>

      <view v-if="hasMore" class="more" @click="loadMore">{{ loading ? '加载中…' : '加载更多' }}</view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import {
  pageWithdrawAdmin,
  approveWithdraw,
  rejectWithdraw,
  markPaidWithdraw,
} from '../../api/promo.js';

const tabs = [
  { value: 'PENDING', label: '待审' },
  { value: 'APPROVED', label: '待打款' },
  { value: 'PAID', label: '已完成' },
  { value: 'REJECTED', label: '已驳回' },
  { value: '', label: '全部' },
];

const tab = ref('PENDING');
const records = ref([]);
const pageNo = ref(1);
const pageSize = 20;
const total = ref(0);
const loading = ref(false);
const hasMore = ref(false);

const STATUS_MAP = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  PAID: '已打款',
};

function statusLabel(s) {
  return STATUS_MAP[s] || s;
}

function formatTime(ts) {
  if (!ts) return '-';
  const d = new Date(ts);
  if (isNaN(d.getTime())) return ts;
  const pad = (n) => (n < 10 ? '0' + n : n);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

async function loadList(reset = true) {
  if (loading.value) return;
  loading.value = true;
  try {
    if (reset) {
      records.value = [];
      pageNo.value = 1;
    }
    const page = await pageWithdrawAdmin({
      status: tab.value || undefined,
      pageNo: pageNo.value,
      pageSize,
    });
    if (page) {
      records.value = records.value.concat(page.list || []);
      total.value = page.total ?? records.value.length;
      hasMore.value = records.value.length < total.value;
    }
  } finally {
    loading.value = false;
  }
}

function switchTab(t) {
  if (tab.value === t) return;
  tab.value = t;
  loadList(true);
}

function loadMore() {
  if (!hasMore.value) return;
  pageNo.value += 1;
  loadList(false);
}

async function promptRemark(title) {
  return new Promise((resolve) => {
    uni.showModal({
      title,
      editable: true,
      placeholderText: '备注（选填）',
      success: (res) => {
        if (res.confirm) resolve(res.content || '');
        else resolve(null);
      },
      fail: () => resolve(null),
    });
  });
}

async function onApprove(r) {
  const remark = await promptRemark(`通过 #${r.id} ${r.amount} 分？`);
  if (remark === null) return;
  try {
    await approveWithdraw(r.id, remark);
    uni.showToast({ title: '已通过', icon: 'success' });
    await loadList(true);
  } catch (e) {
    // toast 由 request 兜底
  }
}

async function onReject(r) {
  const remark = await promptRemark(`驳回 #${r.id}（积分将退还）？`);
  if (remark === null) return;
  try {
    await rejectWithdraw(r.id, remark);
    uni.showToast({ title: '已驳回，积分已退还', icon: 'success' });
    await loadList(true);
  } catch {}
}

async function onMarkPaid(r) {
  const remark = await promptRemark(`标记 #${r.id} 已打款？`);
  if (remark === null) return;
  try {
    await markPaidWithdraw(r.id, remark);
    uni.showToast({ title: '已标记已打款', icon: 'success' });
    await loadList(true);
  } catch {}
}

onMounted(() => loadList(true));
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  padding: 0 0 60rpx;
  min-height: 100vh;
  background: #f6f7f9;
}

.tabs {
  display: flex;
  background: #fff;
  border-bottom: 1rpx solid $border-color;
  overflow-x: auto;

  .tab {
    flex: 0 0 auto;
    padding: 24rpx 32rpx;
    font-size: 28rpx;
    color: $text-secondary;

    &.active {
      color: $brand-primary;
      font-weight: 600;
      border-bottom: 4rpx solid $brand-primary;
    }
  }
}

.list {
  padding: 20rpx 24rpx;
}

.empty {
  text-align: center;
  padding: 80rpx 0;
  color: $text-placeholder;
  font-size: 26rpx;
}

.card {
  background: #fff;
  border-radius: $radius-lg;
  padding: 24rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);

  .row1 {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .amt {
    font-size: 34rpx;
    font-weight: 700;
    color: $text-primary;
  }

  .st {
    font-size: 22rpx;
    padding: 4rpx 16rpx;
    border-radius: 999rpx;

    &.pending {
      background: rgba(255, 165, 0, 0.12);
      color: #d97706;
    }
    &.approved {
      background: rgba(99, 102, 241, 0.12);
      color: #6366f1;
    }
    &.paid {
      background: rgba(22, 163, 74, 0.12);
      color: #16a34a;
    }
    &.rejected {
      background: rgba(239, 68, 68, 0.12);
      color: #ef4444;
    }
  }

  .row2 {
    margin-top: 12rpx;
    display: flex;
    justify-content: space-between;
    font-size: 22rpx;
    color: $text-secondary;
  }

  .row3 {
    margin-top: 6rpx;
    font-size: 22rpx;
    color: $text-placeholder;
  }

  .actions {
    margin-top: 18rpx;
    display: flex;
    gap: 16rpx;
    justify-content: flex-end;

    .btn {
      flex: 0 0 200rpx;
      height: 72rpx;
      line-height: 72rpx;
      font-size: 26rpx;
      border-radius: $radius-md;

      &.primary {
        background: $brand-primary;
        color: #fff;
      }

      &.ghost {
        background: #fff;
        color: $text-primary;
        border: 2rpx solid $border-color;
      }

      &.ghost.danger {
        color: #ef4444;
        border-color: #ef4444;
      }

      &::after {
        border: none;
      }
    }
  }
}

.more {
  text-align: center;
  padding: 24rpx 0;
  font-size: 26rpx;
  color: $brand-primary;
}
</style>
