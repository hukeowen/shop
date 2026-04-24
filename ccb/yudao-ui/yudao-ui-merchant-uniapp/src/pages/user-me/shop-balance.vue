<template>
  <view class="page">
    <!-- Balance & Points card -->
    <view class="summary-card">
      <view class="summary-item">
        <view class="summary-value">¥{{ balanceYuan }}</view>
        <view class="summary-label">余额</view>
      </view>
      <view class="summary-divider" />
      <view class="summary-item">
        <view class="summary-value">{{ rel.points || 0 }}</view>
        <view class="summary-label">积分</view>
      </view>
    </view>

    <!-- Balance to Points -->
    <view class="section card">
      <view class="section-title">余额转积分</view>
      <view class="form-row">
        <input
          class="input"
          type="digit"
          v-model="convertAmount"
          placeholder="输入金额（元）"
        />
        <view class="btn-sm" @click="doConvert">确认转换</view>
      </view>
      <view class="hint">1元 = 100积分，转换后不可撤回</view>
    </view>

    <!-- Withdraw -->
    <view class="section card">
      <view class="section-title">发起提现</view>
      <view class="form-group">
        <view class="form-row">
          <text class="form-label">金额（元）</text>
          <input class="input" type="digit" v-model="withdrawAmount" placeholder="请输入提现金额" />
        </view>
        <view class="form-row">
          <text class="form-label">提现方式</text>
          <picker :range="withdrawTypeLabels" :value="withdrawTypeIndex" @change="onWithdrawTypeChange">
            <view class="picker-val">{{ withdrawTypeLabels[withdrawTypeIndex] }} ›</view>
          </picker>
        </view>
        <view class="form-row">
          <text class="form-label">账户姓名</text>
          <input class="input" v-model="accountName" placeholder="选填" />
        </view>
        <view class="form-row">
          <text class="form-label">账号/收款码</text>
          <input class="input" v-model="accountNo" placeholder="选填" />
        </view>
        <view v-if="withdrawTypeIndex === 2" class="form-row">
          <text class="form-label">银行名称</text>
          <input class="input" v-model="bankName" placeholder="如：招商银行" />
        </view>
      </view>
      <view class="btn-primary" @click="doWithdraw">提交提现申请</view>
    </view>

    <!-- Withdraw records -->
    <view class="section card">
      <view class="section-title">提现记录</view>
      <view v-if="loadingRecords" class="empty-tip">加载中...</view>
      <view v-else-if="!withdrawRecords.length" class="empty-tip">暂无提现记录</view>
      <view v-else>
        <view
          v-for="item in withdrawRecords"
          :key="item.id"
          class="record-item"
        >
          <view class="record-main">
            <text class="record-amount">¥{{ fen2yuan(item.amount) }}</text>
            <text :class="['record-status', statusClass(item.status)]">{{ statusLabel(item.status) }}</text>
          </view>
          <view class="record-sub">
            <text class="record-type">{{ withdrawTypeLabel(item.withdrawType) }}</text>
            <text class="record-time">{{ formatTime(item.createTime) }}</text>
          </view>
          <view v-if="item.rejectReason" class="record-reason">驳回原因：{{ item.rejectReason }}</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { request } from '../../api/request.js';
import { fen2yuan } from '../../utils/format.js';

const WITHDRAW_TYPES = ['微信', '支付宝', '银行'];

export default {
  data() {
    return {
      tenantId: null,
      rel: { balance: 0, points: 0 },
      convertAmount: '',
      withdrawAmount: '',
      withdrawTypeIndex: 0,
      withdrawTypeLabels: WITHDRAW_TYPES,
      accountName: '',
      accountNo: '',
      bankName: '',
      withdrawRecords: [],
      loadingRecords: false,
    };
  },
  computed: {
    balanceYuan() {
      return fen2yuan(this.rel.balance || 0);
    },
  },
  onLoad(query) {
    this.tenantId = query.tenantId ? Number(query.tenantId) : null;
    this.loadData();
  },
  methods: {
    fen2yuan,
    async loadData() {
      await Promise.all([this.loadRel(), this.loadRecords()]);
    },
    async loadRel() {
      if (!this.tenantId) return;
      try {
        const res = await request({
          url: '/app-api/merchant/mini/member-rel/my',
          tenantId: this.tenantId,
        });
        if (res) this.rel = res;
      } catch {}
    },
    async loadRecords() {
      if (!this.tenantId) return;
      this.loadingRecords = true;
      try {
        const res = await request({
          url: '/app-api/merchant/mini/member-rel/withdraw/list',
          tenantId: this.tenantId,
        });
        this.withdrawRecords = Array.isArray(res) ? res : [];
      } catch {
        this.withdrawRecords = [];
      } finally {
        this.loadingRecords = false;
      }
    },
    onWithdrawTypeChange(e) {
      this.withdrawTypeIndex = Number(e.detail.value);
    },
    async doConvert() {
      const yuan = parseFloat(this.convertAmount);
      if (!yuan || yuan <= 0) {
        uni.showToast({ title: '请输入有效金额', icon: 'none' });
        return;
      }
      const amountFen = Math.round(yuan * 100);
      try {
        await request({
          url: `/app-api/merchant/mini/member-rel/balance-to-points?amountFen=${amountFen}`,
          method: 'POST',
          tenantId: this.tenantId,
        });
        uni.showToast({ title: '转换成功', icon: 'success' });
        this.convertAmount = '';
        await this.loadRel();
      } catch {}
    },
    async doWithdraw() {
      const yuan = parseFloat(this.withdrawAmount);
      if (!yuan || yuan <= 0) {
        uni.showToast({ title: '请输入有效提现金额', icon: 'none' });
        return;
      }
      const amountFen = Math.round(yuan * 100);
      const withdrawType = this.withdrawTypeIndex + 1;
      let url = `/app-api/merchant/mini/member-rel/withdraw?amount=${amountFen}&withdrawType=${withdrawType}`;
      if (this.accountName) url += `&accountName=${encodeURIComponent(this.accountName)}`;
      if (this.accountNo) url += `&accountNo=${encodeURIComponent(this.accountNo)}`;
      if (this.withdrawTypeIndex === 2 && this.bankName) url += `&bankName=${encodeURIComponent(this.bankName)}`;
      try {
        await request({ url, method: 'POST', tenantId: this.tenantId });
        uni.showToast({ title: '申请已提交', icon: 'success' });
        this.withdrawAmount = '';
        this.accountName = '';
        this.accountNo = '';
        this.bankName = '';
        await Promise.all([this.loadRel(), this.loadRecords()]);
      } catch {}
    },
    statusLabel(status) {
      return ['待审核', '已打款', '已驳回'][status] || '未知';
    },
    statusClass(status) {
      return ['status-pending', 'status-done', 'status-reject'][status] || '';
    },
    withdrawTypeLabel(type) {
      return WITHDRAW_TYPES[(type || 1) - 1] || '';
    },
    formatTime(t) {
      if (!t) return '';
      return String(t).replace('T', ' ').substring(0, 16);
    },
  },
};
</script>

<style lang="scss">
@import '../../uni.scss';

.page {
  min-height: 100vh;
  background: #f6f7f9;
  padding-bottom: 40rpx;
}

.summary-card {
  display: flex;
  align-items: center;
  justify-content: space-around;
  background: $brand-primary;
  padding: 60rpx 40rpx;
  margin-bottom: 24rpx;
}

.summary-item {
  text-align: center;
}

.summary-value {
  font-size: 56rpx;
  font-weight: 700;
  color: #fff;
  margin-bottom: 8rpx;
}

.summary-label {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.8);
}

.summary-divider {
  width: 1rpx;
  height: 80rpx;
  background: rgba(255, 255, 255, 0.4);
}

.section {
  margin: 0 24rpx 24rpx;
  padding: 28rpx 32rpx;
  border-radius: $radius-lg;
  background: $bg-card;
}

.section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 20rpx;
}

.form-group {
  margin-bottom: 20rpx;
}

.form-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 16rpx;
}

.form-label {
  font-size: 28rpx;
  color: $text-secondary;
  width: 160rpx;
  flex-shrink: 0;
}

.input {
  flex: 1;
  height: 72rpx;
  border: 1rpx solid $border-color;
  border-radius: $radius-md;
  padding: 0 20rpx;
  font-size: 28rpx;
  color: $text-primary;
  background: #fafafa;
}

.picker-val {
  flex: 1;
  height: 72rpx;
  line-height: 72rpx;
  border: 1rpx solid $border-color;
  border-radius: $radius-md;
  padding: 0 20rpx;
  font-size: 28rpx;
  color: $text-primary;
  background: #fafafa;
}

.btn-sm {
  background: $brand-primary;
  color: #fff;
  border-radius: $radius-md;
  padding: 16rpx 28rpx;
  font-size: 26rpx;
  white-space: nowrap;
  flex-shrink: 0;
}

.btn-primary {
  background: $brand-primary;
  color: #fff;
  border-radius: $radius-pill;
  text-align: center;
  padding: 24rpx 0;
  font-size: 30rpx;
  font-weight: 600;
}

.hint {
  font-size: 24rpx;
  color: $text-placeholder;
  margin-top: 8rpx;
}

.empty-tip {
  text-align: center;
  color: $text-placeholder;
  padding: 40rpx 0;
  font-size: 28rpx;
}

.record-item {
  padding: 20rpx 0;
  border-bottom: 1rpx solid $border-color;

  &:last-child {
    border-bottom: none;
  }
}

.record-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8rpx;
}

.record-amount {
  font-size: 32rpx;
  font-weight: 600;
  color: $text-primary;
}

.record-status {
  font-size: 26rpx;
}

.status-pending { color: $text-secondary; }
.status-done    { color: #07c160; }
.status-reject  { color: $danger; }

.record-sub {
  display: flex;
  justify-content: space-between;
}

.record-type, .record-time {
  font-size: 24rpx;
  color: $text-placeholder;
}

.record-reason {
  font-size: 24rpx;
  color: $danger;
  margin-top: 6rpx;
}
</style>
