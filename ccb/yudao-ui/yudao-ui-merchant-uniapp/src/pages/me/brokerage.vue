<template>
  <view class="page">
    <!-- 返佣设置 -->
    <view class="card">
      <view class="section-title">返佣设置</view>
      <view class="row">
        <text class="label">开启返佣</text>
        <switch :checked="form.brokerageEnabled" @change="e => form.brokerageEnabled = e.detail.value" color="#FF6B35" />
      </view>
      <template v-if="form.brokerageEnabled">
        <view class="field">
          <text class="label">一级佣金比例 (%)</text>
          <input class="input" type="digit" v-model="form.firstBrokeragePercent" placeholder="如 5" />
        </view>
        <view class="field">
          <text class="label">二级佣金比例 (%)</text>
          <input class="input" type="digit" v-model="form.secondBrokeragePercent" placeholder="如 2" />
        </view>
        <view class="field">
          <text class="label">佣金冻结天数</text>
          <input class="input" type="number" v-model="form.freezeDays" placeholder="如 7" />
        </view>
      </template>
    </view>

    <!-- 推N返1活动 -->
    <view class="card">
      <view class="section-title">推广活动（推N返1）</view>
      <view class="row">
        <text class="label">开启推广奖励</text>
        <switch :checked="form.pushReturnEnabled" @change="e => form.pushReturnEnabled = e.detail.value" color="#FF6B35" />
      </view>
      <template v-if="form.pushReturnEnabled">
        <view class="field">
          <text class="label">邀请有效订单数 (N)</text>
          <input class="input" type="number" v-model="form.pushN" placeholder="如 5" />
        </view>
        <view class="field">
          <text class="label">达标奖励金额 (元)</text>
          <input class="input" type="digit" v-model="form.returnAmountYuan" placeholder="如 30" />
        </view>
      </template>
    </view>

    <!-- 积分设置 -->
    <view class="card">
      <view class="section-title">积分设置</view>
      <view class="field">
        <text class="label">消费1元赠积分数</text>
        <input class="input" type="number" v-model="form.pointPerYuan" placeholder="0=关闭积分" />
      </view>
    </view>

    <!-- 提现设置 -->
    <view class="card">
      <view class="section-title">提现设置</view>
      <view class="field">
        <text class="label">最低提现金额 (元)</text>
        <input class="input" type="digit" v-model="form.minWithdrawAmountYuan" placeholder="如 100" />
      </view>
    </view>

    <view class="safe-bottom">
      <button class="btn primary" :disabled="saving" @click="onSave">
        {{ saving ? '保存中…' : '保存设置' }}
      </button>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { request } from '../../api/request.js';

const BASE = '/app-api/merchant/mini/shop';

const saving = ref(false);
const form = ref({
  brokerageEnabled: false,
  firstBrokeragePercent: '0',
  secondBrokeragePercent: '0',
  freezeDays: '7',
  pushReturnEnabled: false,
  pushN: '5',
  returnAmountYuan: '0',
  pointPerYuan: '0',
  minWithdrawAmountYuan: '100',
});

async function load() {
  try {
    const data = await request({ url: `${BASE}/brokerage-config` });
    if (data) {
      form.value = {
        brokerageEnabled: !!data.brokerageEnabled,
        firstBrokeragePercent: String(data.firstBrokeragePercent ?? '0'),
        secondBrokeragePercent: String(data.secondBrokeragePercent ?? '0'),
        freezeDays: String(data.freezeDays ?? '7'),
        pushReturnEnabled: !!data.pushReturnEnabled,
        pushN: String(data.pushN ?? '5'),
        returnAmountYuan: String((data.returnAmount ?? 0) / 100),
        pointPerYuan: String(data.pointPerYuan ?? '0'),
        minWithdrawAmountYuan: String((data.minWithdrawAmount ?? 10000) / 100),
      };
    }
  } catch {
    // ignore
  }
}

async function onSave() {
  saving.value = true;
  try {
    await request({
      url: `${BASE}/brokerage-config`,
      method: 'PUT',
      data: {
        brokerageEnabled: form.value.brokerageEnabled,
        firstBrokeragePercent: parseFloat(form.value.firstBrokeragePercent) || 0,
        secondBrokeragePercent: parseFloat(form.value.secondBrokeragePercent) || 0,
        freezeDays: parseInt(form.value.freezeDays) || 7,
        pushReturnEnabled: form.value.pushReturnEnabled,
        pushN: parseInt(form.value.pushN) || 5,
        returnAmount: Math.round((parseFloat(form.value.returnAmountYuan) || 0) * 100),
        pointPerYuan: parseInt(form.value.pointPerYuan) || 0,
        minWithdrawAmount: Math.round((parseFloat(form.value.minWithdrawAmountYuan) || 100) * 100),
      },
    });
    uni.showToast({ title: '保存成功', icon: 'success' });
  } catch {
    // toast shown by request.js
  } finally {
    saving.value = false;
  }
}

onMounted(load);
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
  padding: 28rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.section-title {
  font-size: 28rpx;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 20rpx;
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16rpx 0;

  .label {
    font-size: 28rpx;
    color: $text-primary;
  }
}

.field {
  padding: 16rpx 0;

  .label {
    display: block;
    font-size: 26rpx;
    color: $text-secondary;
    margin-bottom: 12rpx;
  }
}

.input {
  width: 100%;
  height: 88rpx;
  padding: 0 24rpx;
  background: #f6f7f9;
  border-radius: $radius-md;
  font-size: 30rpx;
  color: $text-primary;
  line-height: 88rpx;
  box-sizing: border-box;
}

.safe-bottom {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 24rpx 32rpx calc(env(safe-area-inset-bottom) + 24rpx);
  background: #fff;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.05);
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
</style>
