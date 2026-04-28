<template>
  <view class="page">
    <!-- 总览卡 -->
    <view class="hero">
      <view class="balances">
        <view class="bal">
          <view class="label">推广积分</view>
          <view class="value">{{ promoBalance }}</view>
          <view class="unit">分（{{ (promoBalance / 100).toFixed(2) }} 元）</view>
        </view>
        <view class="bal">
          <view class="label">消费积分</view>
          <view class="value">{{ consumeBalance }}</view>
          <view class="unit">分</view>
        </view>
      </view>
      <view class="star">
        <text>当前星级：</text>
        <text class="num">{{ star }}</text>
        <text>★</text>
        <text class="hint">直推 {{ directCount }} · 团队链 {{ teamSales }}</text>
      </view>
    </view>

    <!-- 转换 -->
    <view class="card">
      <view class="card-title">推广 → 消费 转换</view>
      <view class="conv-row">
        <input class="input" type="number" v-model="convertAmount" placeholder="推广积分(分)" />
        <button class="btn ghost" :disabled="converting" @click="onConvert">
          {{ converting ? '转换中…' : '确认转换' }}
        </button>
      </view>
      <view class="hint inline">按商户配置 ratio 折算；不可逆。</view>
    </view>

    <!-- 操作 -->
    <view class="card">
      <view class="card-title">操作</view>
      <view class="ops">
        <view class="op-btn" @click="goWithdraw">
          <text class="op-icon">¥</text>
          <text>申请提现</text>
        </view>
        <view class="op-btn" @click="goPoolRounds">
          <text class="op-icon">★</text>
          <text>积分池历史</text>
        </view>
      </view>
    </view>

    <!-- 流水 tab -->
    <view class="card">
      <view class="tabs">
        <view :class="['tab', tab === 'PROMO' && 'active']" @click="switchTab('PROMO')">推广积分流水</view>
        <view :class="['tab', tab === 'CONSUME' && 'active']" @click="switchTab('CONSUME')">消费积分流水</view>
      </view>

      <view v-if="!records.length && !loading" class="empty">暂无流水</view>
      <view v-for="r in records" :key="r.id" class="record">
        <view class="row1">
          <text class="src">{{ sourceLabel(r.sourceType) }}</text>
          <text :class="['amt', r.amount > 0 ? 'plus' : 'minus']">
            {{ r.amount > 0 ? '+' : '' }}{{ r.amount }}
          </text>
        </view>
        <view class="row2">
          <text class="remark">{{ r.remark || '-' }}</text>
          <text class="bal">余额 {{ r.balanceAfter }}</text>
        </view>
        <view class="row3">{{ formatTime(r.createTime) }}</view>
      </view>

      <view v-if="hasMore" class="more" @click="loadMore">{{ loading ? '加载中…' : '加载更多' }}</view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import {
  getMyAccount,
  listPromoRecords,
  listConsumeRecords,
  convertPromoToConsume,
} from '../../api/promo.js';

const promoBalance = ref(0);
const consumeBalance = ref(0);
const star = ref(0);
const directCount = ref(0);
const teamSales = ref(0);

const tab = ref('PROMO');           // PROMO / CONSUME
const records = ref([]);
const pageNo = ref(1);
const pageSize = 20;
const total = ref(0);
const loading = ref(false);
const hasMore = ref(false);

const converting = ref(false);
const convertAmount = ref('');

const SOURCE_LABELS = {
  DIRECT: '直推奖',
  QUEUE: '队列返奖',
  SELF_PURCHASE: '自购插队',
  COMMISSION: '团队极差',
  POOL: '积分池',
  CONVERT: '转换',
  WITHDRAW: '提现',
  WITHDRAW_REFUND: '提现退还',
  CONSUME: '消费返积分',
  REDEEM: '下单抵扣',
};

function sourceLabel(s) {
  return SOURCE_LABELS[s] || s;
}

function formatTime(ts) {
  if (!ts) return '';
  const d = typeof ts === 'string' ? new Date(ts) : new Date(ts);
  if (isNaN(d.getTime())) return ts;
  const pad = (n) => (n < 10 ? '0' + n : n);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

async function loadAccount() {
  try {
    const acct = await getMyAccount();
    if (acct) {
      promoBalance.value = acct.promoPointBalance ?? 0;
      consumeBalance.value = acct.consumePointBalance ?? 0;
      star.value = acct.currentStar ?? 0;
      directCount.value = acct.directCount ?? 0;
      teamSales.value = acct.teamSalesCount ?? 0;
    }
  } catch {
    // 静默
  }
}

async function loadRecords(reset = true) {
  if (loading.value) return;
  loading.value = true;
  try {
    if (reset) {
      records.value = [];
      pageNo.value = 1;
    }
    const fn = tab.value === 'PROMO' ? listPromoRecords : listConsumeRecords;
    const page = await fn({ pageNo: pageNo.value, pageSize });
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
  loadRecords(true);
}

function loadMore() {
  if (!hasMore.value) return;
  pageNo.value += 1;
  loadRecords(false);
}

async function onConvert() {
  const amt = parseInt(convertAmount.value);
  if (!(amt > 0)) {
    uni.showToast({ title: '请输入有效的转换金额', icon: 'none' });
    return;
  }
  if (amt > promoBalance.value) {
    uni.showToast({ title: '推广积分余额不足', icon: 'none' });
    return;
  }
  converting.value = true;
  try {
    await convertPromoToConsume(amt, Date.now());
    uni.showToast({ title: '转换成功', icon: 'success' });
    convertAmount.value = '';
    await loadAccount();
    await loadRecords(true);
  } finally {
    converting.value = false;
  }
}

function goWithdraw() {
  uni.navigateTo({ url: '/pages/user-me/withdraw' });
}

function goPoolRounds() {
  uni.showToast({ title: '即将上线', icon: 'none' });
}

onMounted(async () => {
  await loadAccount();
  await loadRecords(true);
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  padding: 24rpx 24rpx 60rpx;
  min-height: 100vh;
}

.hero {
  background: linear-gradient(135deg, #ff9a4a, #ff6b35);
  border-radius: $radius-lg;
  padding: 36rpx 32rpx;
  color: #fff;
  margin-bottom: 24rpx;

  .balances {
    display: flex;
    gap: 24rpx;
  }

  .bal {
    flex: 1;
    background: rgba(255, 255, 255, 0.16);
    border-radius: $radius-md;
    padding: 24rpx;
  }

  .label {
    font-size: 24rpx;
    opacity: 0.9;
  }

  .value {
    font-size: 56rpx;
    font-weight: 700;
    margin: 8rpx 0 4rpx;
  }

  .unit {
    font-size: 22rpx;
    opacity: 0.85;
  }

  .star {
    margin-top: 24rpx;
    font-size: 26rpx;
    display: flex;
    align-items: center;

    .num {
      font-size: 36rpx;
      font-weight: 700;
      margin: 0 6rpx;
    }

    .hint {
      margin-left: auto;
      font-size: 22rpx;
      opacity: 0.85;
    }
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

.conv-row {
  display: flex;
  gap: 16rpx;

  .input {
    flex: 1;
    height: 80rpx;
    padding: 0 24rpx;
    background: #f6f7f9;
    border-radius: $radius-md;
    font-size: 28rpx;
  }

  .btn.ghost {
    flex: 0 0 200rpx;
    height: 80rpx;
    background: rgba(255, 107, 53, 0.08);
    color: $brand-primary;
    border: 2rpx solid rgba(255, 107, 53, 0.4);
    font-size: 26rpx;
    border-radius: $radius-md;

    &::after {
      border: none;
    }
  }
}

.hint.inline {
  display: block;
  margin-top: 12rpx;
  font-size: 22rpx;
  color: $text-secondary;
}

.ops {
  display: flex;
  gap: 16rpx;

  .op-btn {
    flex: 1;
    height: 96rpx;
    border-radius: $radius-md;
    background: #f6f7f9;
    color: $text-primary;
    font-size: 28rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 12rpx;

    .op-icon {
      width: 44rpx;
      height: 44rpx;
      line-height: 44rpx;
      text-align: center;
      border-radius: 50%;
      background: $brand-primary;
      color: #fff;
      font-size: 22rpx;
    }
  }
}

.tabs {
  display: flex;
  border-bottom: 1rpx solid $border-color;
  margin-bottom: 16rpx;

  .tab {
    flex: 1;
    text-align: center;
    padding: 16rpx 0;
    font-size: 28rpx;
    color: $text-secondary;

    &.active {
      color: $brand-primary;
      font-weight: 600;
      border-bottom: 4rpx solid $brand-primary;
    }
  }
}

.empty {
  text-align: center;
  color: $text-placeholder;
  padding: 48rpx 0;
  font-size: 26rpx;
}

.record {
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

  .src {
    font-size: 28rpx;
    color: $text-primary;
    font-weight: 500;
  }

  .amt {
    font-size: 32rpx;
    font-weight: 600;

    &.plus {
      color: #16a34a;
    }
    &.minus {
      color: #ef4444;
    }
  }

  .row2 {
    display: flex;
    justify-content: space-between;
    margin-top: 8rpx;
    font-size: 22rpx;
    color: $text-secondary;
  }

  .row3 {
    margin-top: 4rpx;
    font-size: 22rpx;
    color: $text-placeholder;
  }
}

.more {
  text-align: center;
  padding: 24rpx 0;
  font-size: 26rpx;
  color: $brand-primary;
}
</style>
