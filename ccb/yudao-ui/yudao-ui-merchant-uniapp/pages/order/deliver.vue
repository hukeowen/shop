<template>
  <view class="page" v-if="order">
    <view class="card">
      <view class="section-title">订单信息</view>
      <view class="info-row"><text class="k">订单号</text><text class="v">{{ order.id }}</text></view>
      <view class="info-row"><text class="k">收货人</text><text class="v">{{ order.userNickname }} · {{ order.userMobile }}</text></view>
      <view class="info-row"><text class="k">收货地址</text><text class="v">{{ order.address }}</text></view>
    </view>

    <view class="card">
      <view class="section-title">填写物流单号</view>
      <view class="field">
        <text class="label">快递公司</text>
        <picker
          mode="selector"
          :value="companyIdx"
          :range="companies"
          range-key="name"
          @change="onPickCompany"
        >
          <view class="picker">
            <text :class="{ placeholder: companyIdx < 0 }">
              {{ companyIdx < 0 ? '选择快递公司' : companies[companyIdx].name }}
            </text>
            <text class="arrow">›</text>
          </view>
        </picker>
      </view>
      <view class="field">
        <text class="label">快递单号</text>
        <input
          class="input"
          placeholder="请输入快递单号"
          v-model="expressNo"
        />
      </view>
    </view>

    <view class="tip card">
      <text>提示：发货后买家会收到短信通知，可在订单详情查看物流轨迹。</text>
    </view>

    <view class="actions safe-bottom">
      <button class="btn primary" :disabled="!canSubmit" @click="onSubmit">
        确认发货
      </button>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { getOrder, deliverOrder } from '../../api/order.js';
import { EXPRESS_COMPANIES } from '../../utils/format.js';

const companies = EXPRESS_COMPANIES;
const order = ref(null);
const orderId = ref('');
const companyIdx = ref(-1);
const expressNo = ref('');

const canSubmit = computed(() => companyIdx.value >= 0 && expressNo.value.length >= 6);

async function load() {
  order.value = await getOrder(orderId.value);
}

function onPickCompany(e) {
  companyIdx.value = Number(e.detail.value);
}

async function onSubmit() {
  if (!canSubmit.value) return;
  uni.showLoading({ title: '提交中' });
  await deliverOrder({
    id: orderId.value,
    expressCompany: companies[companyIdx.value].name,
    expressNo: expressNo.value,
  });
  uni.hideLoading();
  uni.showToast({ title: '发货成功', icon: 'success' });
  setTimeout(() => {
    uni.navigateBack();
  }, 1000);
}

onLoad((q) => {
  orderId.value = q.id;
  load();
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

.info-row {
  display: flex;
  padding: 12rpx 0;
  font-size: 26rpx;

  .k {
    width: 160rpx;
    color: $text-secondary;
  }

  .v {
    flex: 1;
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

.input,
.picker {
  display: flex;
  align-items: center;
  width: 100%;
  height: 88rpx;
  padding: 0 24rpx;
  background: #f6f7f9;
  border-radius: $radius-md;
  font-size: 30rpx;
  color: $text-primary;
  line-height: 88rpx;
}

.picker {
  justify-content: space-between;

  .placeholder {
    color: $text-placeholder;
  }

  .arrow {
    color: $text-placeholder;
    font-size: 36rpx;
  }
}

.tip {
  font-size: 24rpx;
  color: $text-secondary;
  line-height: 1.6;
  background: #fff8ef;
}

.actions {
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
  font-size: 30rpx;
  border-radius: $radius-md;

  &.primary {
    background: $brand-primary;
    color: #fff;
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
