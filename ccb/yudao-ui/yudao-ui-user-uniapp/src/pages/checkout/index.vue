<template>
  <view class="page">
    <nav-bar title="结算" />
    <view class="card addr" @click="goAddress">
      <view class="addr-ic">📍</view>
      <view v-if="address" class="addr-body">
        <view class="addr-name">{{ address.name }} {{ address.mobile }}</view>
        <view class="addr-detail">{{ address.areaName || '' }} {{ address.detailAddress }}</view>
      </view>
      <view v-else class="addr-empty">请选择收货地址</view>
      <view class="addr-arrow">›</view>
    </view>

    <view v-if="loading" class="loading">加载中…</view>
    <view v-else>
      <view class="card">
        <view class="card-title">{{ shopName }}</view>
        <view v-for="it in items" :key="it.skuId" class="ci">
          <view class="ci-pic">
            <image v-if="it.picUrl" :src="it.picUrl" mode="aspectFill" class="pic-img" />
            <text v-else>🛍</text>
          </view>
          <view class="ci-body">
            <view class="ci-name">{{ it.spuName }}</view>
            <view class="ci-qty">x {{ it.count }}</view>
          </view>
          <view class="ci-price">¥{{ fen2yuan(it.price, false) }}</view>
        </view>
      </view>

      <view class="card deduct">
        <view class="card-title">费用明细</view>
        <view class="dd-row"><text class="dd-l">商品金额</text><text class="dd-v">¥{{ fen2yuan(preview?.totalPrice || 0, false) }}</text></view>
        <view v-if="preview?.deliveryPrice" class="dd-row"><text class="dd-l">运费</text><text class="dd-v">¥{{ fen2yuan(preview.deliveryPrice, false) }}</text></view>
        <view v-if="preview?.couponPrice" class="dd-row"><text class="dd-l">优惠券</text><text class="dd-v neg">−¥{{ fen2yuan(preview.couponPrice, false) }}</text></view>
        <view v-if="preview?.promotionPrice" class="dd-row"><text class="dd-l">活动优惠</text><text class="dd-v neg">−¥{{ fen2yuan(preview.promotionPrice, false) }}</text></view>
        <view v-if="preview?.pointPrice" class="dd-row"><text class="dd-l">积分抵扣</text><text class="dd-v neg">−¥{{ fen2yuan(preview.pointPrice, false) }}</text></view>
        <view class="dd-row total">
          <text class="dd-l">应付</text>
          <text class="dd-v hl">¥{{ fen2yuan(preview?.payPrice || 0, false) }}</text>
        </view>
      </view>
    </view>

    <view class="footer">
      <view class="amt">合计 <text class="hl">¥{{ fen2yuan(preview?.payPrice || 0, false) }}</text></view>
      <view class="pay" :class="{ disabled: !canPay }" @click="onPay">{{ submitting ? '下单中…' : '立即支付' }}</view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { settlement, checkoutSubmit } from '@/api/order.js';
import { getDefaultAddress } from '@/api/address.js';
import { fen2yuan } from '@/utils/format.js';

const route = (() => {
  try { const ps = getCurrentPages(); return ps[ps.length - 1]?.options || {}; } catch { return {}; }
})();

const address = ref(null);
const loading = ref(true);
const preview = ref(null);
const items = ref([]);
const shopName = ref('');
const submitting = ref(false);
const canPay = computed(() => !!preview.value && !!address.value && !submitting.value);

function goAddress() { uni.navigateTo({ url: '/pages/address/list?select=1' }); }

function parseItems() {
  // type=buy_now → ?skuId=X&count=N
  // type=cart    → ?skus=skuId:count,skuId:count
  if (route.type === 'buy_now' && route.skuId) {
    return [{ skuId: Number(route.skuId), count: Number(route.count || 1) }];
  }
  if (route.type === 'cart' && route.skus) {
    return route.skus.split(',').map((p) => {
      const [skuId, count] = p.split(':');
      return { skuId: Number(skuId), count: Number(count || 1) };
    });
  }
  return [];
}

async function loadPreview() {
  loading.value = true;
  try {
    const its = parseItems();
    if (!its.length) return;
    const body = await settlement({ items: JSON.stringify(its) });
    preview.value = body;
    items.value = body?.items || its;
    shopName.value = body?.shopName || body?.merchantName || '订单';
  } catch {} finally { loading.value = false; }
}

async function onPay() {
  if (!canPay.value) return;
  submitting.value = true;
  try {
    const sel = uni.getStorageSync('selected-address');
    const addrId = address.value?.id || sel?.id;
    const its = parseItems();
    const result = await checkoutSubmit({
      addressId: addrId,
      items: its,
      remark: '',
    }, route.tenantId);
    uni.removeStorageSync('selected-address');
    // 保留 reward 信息（如有），传给 pay-success 页
    if (result?.rewardAmount) {
      uni.setStorageSync('pay-success-reward', { amount: result.rewardAmount, source: '推广奖励' });
    }
    uni.setStorageSync('pay-success-amount', preview.value?.payPrice || 0);
    uni.reLaunch({ url: '/pages/pay-success/index' });
  } catch {} finally { submitting.value = false; }
}

onMounted(async () => {
  try {
    const sel = uni.getStorageSync('selected-address');
    address.value = sel || (await getDefaultAddress());
  } catch {}
  await loadPreview();
});
onShow(async () => {
  // 从地址页回来重新读 selected-address
  try {
    const sel = uni.getStorageSync('selected-address');
    if (sel) address.value = sel;
  } catch {}
});
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background: $bg-2; padding-bottom: 80px; }
.loading { padding: 40px; text-align: center; color: $t4; }
.card { background: #fff; margin: 10px 14px; border-radius: $r-md; padding: 14px; box-shadow: $sh-1; }
.card-title { font-size: 14px; font-weight: 800; color: $t1; margin-bottom: 10px; }
.addr { display: flex; gap: 10px; align-items: center; }
.addr-ic { width: 32px; height: 32px; background: $o-50; color: $o; border-radius: 8px; display: flex; align-items: center; justify-content: center; }
.addr-body { flex: 1; }
.addr-empty { flex: 1; color: $t4; font-size: 13px; }
.addr-name { font-size: 14px; font-weight: 700; color: $t1; }
.addr-detail { font-size: 12px; color: $t3; margin-top: 2px; }
.addr-arrow { color: $t4; font-size: 18px; }
.ci { display: flex; gap: 10px; padding: 8px 0; align-items: center; }
.ci-pic { width: 40px; height: 40px; background: $o-50; color: $o; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-size: 20px; overflow: hidden; flex-shrink: 0; }
.pic-img { width: 100%; height: 100%; }
.ci-body { flex: 1; min-width: 0; }
.ci-name { font-size: 13px; font-weight: 700; color: $t1; }
.ci-qty { font-size: 11px; color: $t3; margin-top: 2px; }
.ci-price { font-size: 14px; color: $o; font-weight: 800; flex-shrink: 0; }
.deduct .dd-row { display: flex; justify-content: space-between; padding: 6px 0; font-size: 13px; color: $t2; }
.dd-v { color: $t1; font-weight: 700; }
.dd-v.neg { color: $danger; }
.dd-row.total { border-top: 1px dashed $line; padding-top: 10px; margin-top: 6px; font-weight: 800; color: $t1; }
.dd-row.total .hl { color: $o; font-size: 18px; }
.footer { position: fixed; left: 0; right: 0; bottom: 0; display: flex; align-items: center; gap: 12px; padding: 10px 14px; padding-bottom: calc(10px + env(safe-area-inset-bottom)); background: #fff; border-top: 1px solid $line; }
.amt { flex: 1; font-size: 13px; color: $t2; }
.amt .hl { color: $o; font-weight: 900; font-size: 20px; margin-left: 4px; }
.pay { padding: 12px 30px; background: linear-gradient(135deg, $o, $o-d); color: #fff; border-radius: $r-pill; font-weight: 800; box-shadow: $sh-warm; }
.pay.disabled { background: #94A3B8; box-shadow: none; opacity: .7; }
</style>
