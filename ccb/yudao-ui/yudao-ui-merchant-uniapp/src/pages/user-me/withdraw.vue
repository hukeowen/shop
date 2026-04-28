<template>
  <view class="page">
    <!-- 余额头部 -->
    <view class="hero">
      <text class="label">推广积分余额</text>
      <text class="amt">{{ promoBalance }}</text>
      <text class="unit">分（≈ {{ (promoBalance / 100).toFixed(2) }} 元）</text>
    </view>

    <!-- 申请表单 -->
    <view class="card">
      <view class="card-title">提现申请</view>
      <view class="field">
        <text class="label">提现金额</text>
        <input class="input" type="number" v-model="amount" placeholder="单位：分" />
      </view>
      <view class="hint">最低提现门槛：{{ threshold }} 分（{{ (threshold / 100).toFixed(2) }} 元）</view>
      <button class="btn primary" :disabled="submitting" @click="onApply">
        {{ submitting ? '提交中…' : '提交申请' }}
      </button>
      <view class="rules">
        申请提交后，推广积分将立即从余额中扣减；商户审批通过后线下打款；驳回时会原路退还积分。
      </view>
    </view>

    <!-- 我的申请列表 -->
    <view class="card">
      <view class="card-title">我的申请</view>
      <view v-if="!records.length && !loading" class="empty">暂无申请</view>
      <view v-for="r in records" :key="r.id" class="row">
        <view class="row1">
          <text class="amt">{{ r.amount }} 分</text>
          <text :class="['st', r.status.toLowerCase()]">{{ statusLabel(r.status) }}</text>
        </view>
        <view class="row2">
          <text>申请：{{ formatTime(r.applyAt) }}</text>
          <text v-if="r.processedAt">处理：{{ formatTime(r.processedAt) }}</text>
        </view>
        <view v-if="r.processorRemark" class="remark">备注：{{ r.processorRemark }}</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { applyWithdraw, listMyWithdraws, getMyAccount, getShopPromoConfig } from '../../api/promo.js';

const promoBalance = ref(0);
const threshold = ref(10000);
const amount = ref('');
const submitting = ref(false);
const records = ref([]);
const loading = ref(false);

const STATUS_MAP = {
  PENDING: '待审核',
  APPROVED: '已通过 待打款',
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

async function loadAll() {
  loading.value = true;
  try {
    const [acct, cfg, list] = await Promise.all([
      getMyAccount(),
      getShopPromoConfig(),
      listMyWithdraws(),
    ]);
    if (acct) promoBalance.value = acct.promoPointBalance ?? 0;
    if (cfg && cfg.withdrawThreshold != null) threshold.value = cfg.withdrawThreshold;
    records.value = list || [];
  } finally {
    loading.value = false;
  }
}

async function onApply() {
  const amt = parseInt(amount.value);
  if (!(amt > 0)) {
    uni.showToast({ title: '请输入有效金额', icon: 'none' });
    return;
  }
  if (amt < threshold.value) {
    uni.showToast({ title: `低于提现门槛 ${threshold.value} 分`, icon: 'none' });
    return;
  }
  if (amt > promoBalance.value) {
    uni.showToast({ title: '余额不足', icon: 'none' });
    return;
  }
  submitting.value = true;
  try {
    await applyWithdraw(amt);
    uni.showToast({ title: '申请已提交', icon: 'success' });
    amount.value = '';
    await loadAll();
  } finally {
    submitting.value = false;
  }
}

onMounted(loadAll);
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  padding: 24rpx 24rpx 60rpx;
  min-height: 100vh;
}

.hero {
  text-align: center;
  background: linear-gradient(135deg, #ff9a4a, #ff6b35);
  color: #fff;
  border-radius: $radius-lg;
  padding: 40rpx 32rpx;
  margin-bottom: 24rpx;

  .label {
    display: block;
    font-size: 24rpx;
    opacity: 0.85;
  }

  .amt {
    display: block;
    font-size: 64rpx;
    font-weight: 700;
    margin: 12rpx 0 4rpx;
  }

  .unit {
    font-size: 22rpx;
    opacity: 0.85;
  }
}

.card {
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 28rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);

  .card-title {
    font-size: 28rpx;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: 16rpx;
  }
}

.field {
  margin-bottom: 12rpx;

  .label {
    display: block;
    font-size: 26rpx;
    color: $text-secondary;
    margin-bottom: 10rpx;
  }
}

.input {
  width: 100%;
  height: 80rpx;
  padding: 0 24rpx;
  background: #f6f7f9;
  border-radius: $radius-md;
  font-size: 28rpx;
  box-sizing: border-box;
}

.hint {
  margin: 8rpx 0 16rpx;
  font-size: 22rpx;
  color: $text-secondary;
}

.btn {
  width: 100%;
  height: 96rpx;
  line-height: 96rpx;
  font-size: 32rpx;
  font-weight: 600;
  border-radius: $radius-md;

  &.primary {
    background: $brand-primary;
    color: #fff;
  }

  &[disabled] {
    background: $text-placeholder;
    color: #fff;
    opacity: 0.7;
  }

  &::after {
    border: none;
  }
}

.rules {
  margin-top: 20rpx;
  font-size: 22rpx;
  color: $text-secondary;
  line-height: 1.6;
}

.empty {
  text-align: center;
  padding: 48rpx 0;
  font-size: 26rpx;
  color: $text-placeholder;
}

.row {
  padding: 18rpx 0;
  border-bottom: 1rpx solid $border-color;

  &:last-child {
    border-bottom: none;
  }

  .row1 {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .amt {
    font-size: 32rpx;
    font-weight: 600;
    color: $text-primary;
  }

  .st {
    font-size: 24rpx;
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
    margin-top: 8rpx;
    display: flex;
    gap: 24rpx;
    font-size: 22rpx;
    color: $text-secondary;
  }

  .remark {
    margin-top: 6rpx;
    font-size: 22rpx;
    color: $text-placeholder;
  }
}
</style>
