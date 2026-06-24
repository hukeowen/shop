<template>
  <view class="page">
    <nav-bar title="申请兑换积分" />

    <view class="card shop-card">
      <view class="shop-row">
        <text class="shop-ic">🏪</text>
        <text class="shop-name">{{ shopName || '店铺' }}</text>
      </view>
      <view class="card-row">
        <text class="l">该店可申请兑换推广积分</text>
        <text class="hl">{{ fen2yuan(promoBalance, false) }} 积分</text>
      </view>
    </view>

    <view class="card">
      <view class="card-title">申请兑换积分数</view>
      <view class="amt-input">
        <input v-model="amount" type="digit" placeholder="请输入积分数（1 积分 = ¥1）" />
        <text class="unit">积分</text>
        <text class="all" @click="amount = fen2yuan(promoBalance, false)">全部</text>
      </view>
      <view v-if="amtFenInt > 0 && amtFenInt > promoBalance" class="warn">超出该店可兑换余额</view>
    </view>

    <view class="tip">
      说明：推广积分为<text class="b">该店商户</text>的营销凭证。本兑换申请将由<text class="b">{{ shopName || '该商户' }}</text>独立审批，<text class="b">兑换形式（现金 / 商品 / 优惠）与到账时间由商户决定</text>，商户审批后会与你联系。平台仅提供技术信息撮合，<text class="b">不构成兑换承诺 / 担保</text>。提交后该积分将冻结，驳回则自动退回。
    </view>

    <view class="submit" :class="{ disabled: !canSubmit }" @click="onSubmit">{{ submitting ? '提交中…' : '提交兑换申请' }}</view>
    <view class="link" @click="goList">查看我的兑换记录 ›</view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { getAccount, applyPromoWithdraw } from '@/api/promo.js';
import { fen2yuan } from '@/utils/format.js';

const tenantId = ref(null);
const shopName = ref('');
const promoBalance = ref(0);
const amount = ref('');
const submitting = ref(false);

// 用户输入"积分"数（= 元值，1 积分 = ¥1）；底层 fen = 积分 × 100
const amtFenInt = computed(() => {
  const v = Number(amount.value);
  if (Number.isNaN(v) || v <= 0) return 0;
  return Math.round(v * 100);
});

const canSubmit = computed(() => {
  if (submitting.value || !tenantId.value) return false;
  if (amtFenInt.value < 100) return false;          // 最低 1 积分
  if (amtFenInt.value > promoBalance.value) return false;
  return true;
});

async function loadBalance() {
  if (!tenantId.value) return;
  try {
    const acct = await getAccount(tenantId.value);
    promoBalance.value = acct?.promoPointBalance || 0;
  } catch {}
}

onLoad((q) => {
  tenantId.value = q && q.tenantId ? Number(q.tenantId) : null;
  shopName.value = q && q.shopName ? decodeURIComponent(q.shopName) : '';
  if (q && q.balance != null) promoBalance.value = Number(q.balance) || 0;
  loadBalance();
});

async function onSubmit() {
  if (!canSubmit.value) return;
  if (!tenantId.value) return uni.showToast({ title: '缺少店铺信息', icon: 'none' });
  submitting.value = true;
  try {
    await applyPromoWithdraw(amtFenInt.value, tenantId.value);
    uni.showToast({ title: '已提交，等商户审批', icon: 'success' });
    setTimeout(() => uni.navigateBack(), 900);
  } catch (e) {
    uni.showToast({ title: e?.message || '提交失败', icon: 'none' });
  } finally {
    submitting.value = false;
  }
}
function goList() { uni.navigateTo({ url: '/pages/withdraw/list' }); }
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background: $bg-2; padding-bottom: 40px; }
.card { background: #fff; margin: 10px 14px; border-radius: $r-md; padding: 14px; box-shadow: $sh-1; }
.shop-card { margin-top: 14px; }
.shop-row { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; padding-bottom: 10px; border-bottom: 1px solid $line; }
.shop-ic { font-size: 18px; }
.shop-name { font-size: 15px; font-weight: 800; color: $t1; }
.card-title { font-size: 14px; font-weight: 800; color: $t1; margin-bottom: 10px; }
.card-row { display: flex; justify-content: space-between; align-items: center; }
.l { font-size: 13px; color: $t2; }
.hl { font-size: 20px; font-weight: 900; color: $o; }
.amt-input { display: flex; align-items: center; gap: 8px; padding: 6px 0; border-bottom: 1px solid $line; }
.amt-input input { flex: 1; font-size: 24px; font-weight: 800; color: $t1; }
.amt-input .unit { font-size: 14px; color: $o; font-weight: 700; }
.all { color: $o; font-size: 12px; font-weight: 700; }
.warn { color: $danger; font-size: 11px; margin-top: 6px; }
.tip { padding: 12px 24px; font-size: 11px; color: $t4; line-height: 1.6; }
.tip .b { color: $t2; font-weight: 700; }
.submit { margin: 20px 14px 0; padding: 14px; background: linear-gradient(135deg, $o, $o-d); color: #fff; text-align: center; border-radius: $r-pill; font-weight: 800; font-size: 15px; box-shadow: $sh-warm; }
.submit.disabled { background: #94A3B8; box-shadow: none; opacity: .7; }
.link { margin: 14px; text-align: center; font-size: 12px; color: $o-d; font-weight: 600; }
</style>
