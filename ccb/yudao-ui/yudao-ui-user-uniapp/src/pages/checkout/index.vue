<template>
  <view class="page">
    <nav-bar title="结算" />
    <view class="card addr" @click="goAddress">
      <view class="addr-ic">📍</view>
      <view v-if="address" class="addr-body">
        <view class="addr-name">{{ address.name }} {{ address.mobile }}</view>
        <view class="addr-detail">{{ address.detail }}</view>
      </view>
      <view v-else class="addr-empty">请选择收货地址</view>
      <view class="addr-arrow">›</view>
    </view>

    <view class="card">
      <view class="card-title">{{ shopName }}</view>
      <view v-for="it in items" :key="it.id" class="ci">
        <view class="ci-pic">{{ it.em || '🛍' }}</view>
        <view class="ci-body">
          <view class="ci-name">{{ it.name }}</view>
          <view class="ci-qty">x {{ it.count }}</view>
        </view>
        <view class="ci-price">¥{{ it.price }}</view>
      </view>
    </view>

    <view class="card deduct">
      <view class="card-title">优惠</view>
      <view v-for="d in deducts" :key="d.k" class="dd-row">
        <text class="dd-l">{{ d.label }}</text>
        <text class="dd-v">−¥{{ d.amount }}</text>
      </view>
      <view class="dd-row total">
        <text class="dd-l">应付</text>
        <text class="dd-v hl">¥{{ payable }}</text>
      </view>
    </view>

    <view class="footer">
      <view class="amt">合计 <text class="hl">¥{{ payable }}</text></view>
      <view class="pay" @click="onPay">立即支付</view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
// import { checkoutPreview, submitOrder } from '@/api/order.js';
// import { getDefaultAddress } from '@/api/address.js';

const address = ref(null);
const shopName = ref('');
const items = ref([]);
const deducts = ref([]);
const payable = ref('0.00');

function goAddress() { uni.navigateTo({ url: '/pages/address/list?select=1' }); }
function onPay() {
  uni.showLoading({ title: '下单中…' });
  setTimeout(() => { uni.hideLoading(); uni.reLaunch({ url: '/pages/pay-success/index' }); }, 800);
}

onMounted(async () => {
  // address.value = await getDefaultAddress();
  // const preview = await checkoutPreview(...); items.value = preview.items; deducts.value = preview.deducts; payable.value = (preview.payPrice/100).toFixed(2);
});
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background: $bg-2; padding-bottom: 80px; }
.card { background: #fff; margin: 10px 14px; border-radius: $r-md; padding: 14px; box-shadow: $sh-1; }
.card-title { font-size: 14px; font-weight: 800; color: $t1; margin-bottom: 10px; }
.addr { display: flex; gap: 10px; align-items: center; }
.addr-ic { width: 32px; height: 32px; background: $o-50; color: $o; border-radius: 8px; display: flex; align-items: center; justify-content: center; }
.addr-body { flex: 1; }
.addr-empty { flex: 1; color: $t4; font-size: 13px; }
.addr-name { font-size: 14px; font-weight: 700; color: $t1; }
.addr-detail { font-size: 12px; color: $t3; margin-top: 2px; }
.addr-arrow { color: $t4; }

.ci { display: flex; gap: 10px; padding: 8px 0; align-items: center; }
.ci-pic { width: 40px; height: 40px; background: $o-50; color: $o; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-size: 20px; }
.ci-body { flex: 1; }
.ci-name { font-size: 13px; font-weight: 700; color: $t1; }
.ci-qty { font-size: 11px; color: $t3; margin-top: 2px; }
.ci-price { font-size: 14px; color: $o; font-weight: 800; }

.deduct .dd-row { display: flex; justify-content: space-between; padding: 6px 0; font-size: 13px; color: $t2; }
.dd-v { color: $danger; font-weight: 700; }
.dd-row.total { border-top: 1px dashed $line; padding-top: 10px; margin-top: 6px; font-weight: 800; color: $t1; }
.dd-row.total .hl { color: $o; font-size: 16px; }

.footer { position: fixed; left: 0; right: 0; bottom: 0; display: flex; align-items: center; gap: 12px; padding: 10px 14px; padding-bottom: calc(10px + env(safe-area-inset-bottom)); background: #fff; border-top: 1px solid $line; }
.amt { flex: 1; font-size: 13px; color: $t2; }
.amt .hl { color: $o; font-weight: 900; font-size: 20px; margin-left: 4px; }
.pay { padding: 12px 30px; background: linear-gradient(135deg, $o, $o-d); color: #fff; border-radius: $r-pill; font-weight: 800; box-shadow: $sh-warm; }
</style>
