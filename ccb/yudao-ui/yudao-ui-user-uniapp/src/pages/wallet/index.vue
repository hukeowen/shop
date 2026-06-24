<template>
  <view class="page">
    <nav-bar title="我的钱包" bg="transparent" txt="#fff" />
    <view class="hero">
      <view class="hero-tag">💎 推广积分（跨店合计 · 商户营销凭证）</view>
      <view class="hero-amt">{{ fen2yuan(totalPromo, false) }} <text class="unit">积分</text></view>
      <view class="hero-sub">积分为各商户营销凭证 · 兑付由对应商户独立审批 · 平台不担保</view>
      <view class="hero-actions">
        <view class="btn ghost" @click="goWithdrawList">兑付记录</view>
        <view class="btn ghost" @click="goPromoRecords">推广明细</view>
      </view>
    </view>

    <view class="section-title"><text class="h">按店铺资产</text><text class="sub">兑付按店独立申请</text></view>
    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!shops.length" icon="💎" title="暂无店铺积分" desc="在店铺消费 / 参与营销活动即可获得" />
    <view v-else>
      <view v-for="s in shops" :key="s.tenantId" class="shop">
        <view class="s-head">
          <text class="s-name">{{ s.shopName || '店铺 #' + s.tenantId }}</text>
        </view>
        <view class="s-body">
          <view class="s-stat" @click="goShopPromo(s)">
            <view class="v gold">{{ fen2yuan(s.promoPoints || 0, false) }}</view>
            <view class="l">推广积分</view>
          </view>
          <view class="s-divider"></view>
          <view class="s-stat" @click="goShopConsume(s)">
            <view class="v">{{ fen2yuan(s.points || 0, false) }}</view>
            <view class="l">消费积分</view>
          </view>
          <view class="s-act" :class="{ disabled: !(s.promoPoints > 0) }" @click="goWithdraw(s)">申请兑付</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { listMyShopsEnriched } from '@/api/shop.js';
import { fen2yuan } from '@/utils/format.js';

const loading = ref(true);
const shops = ref([]);
const totalPromo = computed(() => shops.value.reduce((s, x) => s + (x.promoPoints || 0), 0));

function goWithdraw(s) {
  if (!(s.promoPoints > 0)) { uni.showToast({ title: '该店暂无可兑付推广积分', icon: 'none' }); return; }
  uni.navigateTo({ url: `/pages/withdraw/index?tenantId=${s.tenantId}&shopName=${encodeURIComponent(s.shopName || '')}&balance=${s.promoPoints || 0}` });
}
function goWithdrawList() { uni.navigateTo({ url: '/pages/withdraw/list' }); }
function goPromoRecords() { uni.navigateTo({ url: '/pages/points/promo' }); }
function goShopPromo(s) { uni.navigateTo({ url: `/pages/points/promo?tenantId=${s.tenantId}&shopName=${encodeURIComponent(s.shopName || '')}` }); }
function goShopConsume(s) { uni.navigateTo({ url: `/pages/points/consume?tenantId=${s.tenantId}&shopName=${encodeURIComponent(s.shopName || '')}` }); }

async function load() {
  loading.value = true;
  try {
    const list = await listMyShopsEnriched();
    // 只展示有资产的店（推广积分 / 消费积分 任一 > 0）
    shops.value = (list || []).filter((s) => (s.promoPoints || 0) > 0 || (s.points || 0) > 0);
  } catch { shops.value = []; }
  finally { loading.value = false; }
}
onShow(load);
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background: $bg-2; padding-bottom: 30px; }
.hero { padding: 24px 14px 20px; background: linear-gradient(135deg, #18130E, #2A1A0F); color: #fff; border-bottom-left-radius: 28px; border-bottom-right-radius: 28px; }
.hero-tag { font-size: 12px; opacity: .7; }
.hero-amt { font-size: 38px; font-weight: 900; margin-top: 4px; background: linear-gradient(135deg, #fff, $gold-l); -webkit-background-clip: text; background-clip: text; color: transparent; }
.hero-amt .unit { font-size: 16px; font-weight: 700; -webkit-text-fill-color: $gold-l; }
.hero-sub { margin-top: 6px; font-size: 11px; color: rgba(255,255,255,.55); }
.hero-actions { display: flex; gap: 8px; margin-top: 18px; }
.btn { flex: 1; padding: 11px 0; text-align: center; border-radius: $r-pill; font-weight: 800; font-size: 13px; }
.btn.ghost { background: rgba(255,255,255,.12); color: #fff; }
.section-title { display: flex; align-items: baseline; padding: 18px 14px 8px; }
.section-title .h { font-size: 15px; font-weight: 800; color: $t1; }
.section-title .sub { font-size: 11px; color: $t3; margin-left: 8px; }
.loading { padding: 40px; text-align: center; color: $t4; }
.shop { background: #fff; margin: 8px 14px; border-radius: $r-lg; padding: 14px; box-shadow: $sh-1; }
.s-head { margin-bottom: 10px; }
.s-name { font-size: 15px; font-weight: 800; color: $t1; }
.s-body { display: flex; align-items: center; }
.s-stat { flex: 1; text-align: center; }
.s-stat .v { font-size: 19px; font-weight: 900; color: $t1; font-variant-numeric: tabular-nums; }
.s-stat .v.gold { color: $gold-d; }
.s-stat .l { font-size: 11px; color: $t3; margin-top: 2px; }
.s-divider { width: 1px; height: 30px; background: $line; }
.s-act { flex: none; margin-left: 12px; padding: 9px 16px; border-radius: $r-pill; background: linear-gradient(135deg, $o, $o-d); color: #fff; font-size: 13px; font-weight: 800; box-shadow: $sh-warm; }
.s-act.disabled { background: $bg-2; color: $t4; box-shadow: none; }
</style>
