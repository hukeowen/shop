<template>
  <view class="page">
    <nav-bar title="我的钱包" bg="transparent" txt="#fff" />

    <!-- ━━ Hero ━━ -->
    <view class="hero">
      <view class="hero-bg"></view>
      <view class="hero-tag">💎 推广积分 · 商户营销凭证</view>
      <view class="hero-amt">{{ fen2yuan(totalPromo, false) }}<text class="unit"> 积分</text></view>
      <view class="hero-cap">当前可用（跨店合计）</view>

      <view class="hero-stats">
        <view class="hs">
          <text class="v">{{ fen2yuan(totalExchanged, false) }}</text>
          <text class="l">已兑换推广积分</text>
        </view>
        <view class="hs-div"></view>
        <view class="hs">
          <text class="v">{{ shops.length }}</text>
          <text class="l">有积分店铺</text>
        </view>
      </view>

      <view class="hero-actions">
        <view class="btn" @click="goWithdrawList"><text class="b-ic">🧾</text>兑换记录</view>
        <view class="btn" @click="goPromoRecords"><text class="b-ic">📊</text>推广明细</view>
      </view>
    </view>

    <!-- ━━ 按店资产 ━━ -->
    <view class="section-title">
      <text class="h">按店铺资产</text>
      <text class="sub">兑换按店独立申请 · 商户审批</text>
    </view>

    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!shops.length" icon="💎" title="暂无店铺积分" desc="在店铺消费 / 参与营销活动即可获得" />
    <view v-else class="shops">
      <view v-for="(s, i) in shops" :key="s.tenantId" class="shop">
        <view class="s-head">
          <view class="s-logo" :class="['', 't2', 't3', 't4'][i % 4]">{{ (s.shopName || '店')[0] }}</view>
          <text class="s-name">{{ s.shopName || '店铺 #' + s.tenantId }}</text>
          <text v-if="s.promoPoints > 0" class="s-flag">可兑换</text>
        </view>
        <view class="s-body">
          <view class="s-stat" @click="goShopPromo(s)">
            <view class="v gold">{{ fen2yuan(s.promoPoints || 0, false) }}</view>
            <view class="l">推广积分</view>
          </view>
          <view class="s-divider"></view>
          <view class="s-stat" @click="goShopConsume(s)">
            <view class="v mint">{{ fen2yuan(s.points || 0, false) }}</view>
            <view class="l">消费积分</view>
          </view>
          <view class="s-act" :class="{ disabled: !(s.promoPoints > 0) }" @click="goWithdraw(s)">申请兑换</view>
        </view>
      </view>
    </view>
    <view class="foot-tip">推广积分为各商户营销凭证，兑换由对应商户独立审批，平台不担保。</view>
    <view class="bottom-pad"></view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { listMyShopsEnriched } from '@/api/shop.js';
import { listMyPromoWithdraws } from '@/api/promo.js';
import { fen2yuan } from '@/utils/format.js';

const loading = ref(true);
const shops = ref([]);
const totalExchanged = ref(0);
const totalPromo = computed(() => shops.value.reduce((s, x) => s + (x.promoPoints || 0), 0));

function goWithdraw(s) {
  if (!(s.promoPoints > 0)) { uni.showToast({ title: '该店暂无可兑换推广积分', icon: 'none' }); return; }
  uni.navigateTo({ url: `/pages/withdraw/index?tenantId=${s.tenantId}&shopName=${encodeURIComponent(s.shopName || '')}&balance=${s.promoPoints || 0}` });
}
function goWithdrawList() { uni.navigateTo({ url: '/pages/withdraw/list' }); }
function goPromoRecords() { uni.navigateTo({ url: '/pages/points/promo' }); }
function goShopPromo(s) { uni.navigateTo({ url: `/pages/points/promo?tenantId=${s.tenantId}&shopName=${encodeURIComponent(s.shopName || '')}` }); }
function goShopConsume(s) { uni.navigateTo({ url: `/pages/points/consume?tenantId=${s.tenantId}&shopName=${encodeURIComponent(s.shopName || '')}` }); }

async function load() {
  loading.value = true;
  try {
    const [list, withdraws] = await Promise.all([
      listMyShopsEnriched(),
      listMyPromoWithdraws().catch(() => []),
    ]);
    shops.value = (list || []).filter((s) => (s.promoPoints || 0) > 0 || (s.points || 0) > 0);
    // 已兑换推广积分 = 非驳回的兑换申请总额（申请时即扣减积分）
    totalExchanged.value = (withdraws || [])
      .filter((w) => w && w.status !== 'REJECTED')
      .reduce((a, w) => a + (w.amount || 0), 0);
  } catch { shops.value = []; }
  finally { loading.value = false; }
}
onShow(load);
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background: $bg-2; padding-bottom: 30px; }

/* ━━ Hero ━━ */
.hero {
  position: relative; overflow: hidden;
  padding: 26px 16px 22px;
  background: linear-gradient(150deg, #1B1208 0%, #2C1B0E 55%, #3A2410 100%);
  color: #fff;
  border-bottom-left-radius: 28px; border-bottom-right-radius: 28px;
}
.hero-bg {
  position: absolute; right: -40px; top: -50px;
  width: 240px; height: 240px; border-radius: 50%;
  background: radial-gradient(circle, rgba(212,146,10,.38), transparent 65%);
  pointer-events: none;
}
.hero-tag { font-size: 12px; color: rgba(255,255,255,.65); position: relative; z-index: 1; }
.hero-amt {
  margin-top: 6px; position: relative; z-index: 1;
  font-size: 42px; font-weight: 900; letter-spacing: -1px;
  background: linear-gradient(135deg, #fff, $gold-l);
  -webkit-background-clip: text; background-clip: text; color: transparent;
}
.hero-amt .unit { font-size: 15px; font-weight: 700; -webkit-text-fill-color: $gold-l; }
.hero-cap { font-size: 11px; color: rgba(255,255,255,.5); margin-top: 2px; position: relative; z-index: 1; }
.hero-stats {
  display: flex; align-items: center;
  margin-top: 16px; padding: 12px 0;
  background: rgba(255,255,255,.06); border-radius: 14px;
  position: relative; z-index: 1;
}
.hs { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 3px; }
.hs .v { font-size: 18px; font-weight: 800; color: $gold-l; font-variant-numeric: tabular-nums; }
.hs .l { font-size: 10.5px; color: rgba(255,255,255,.55); }
.hs-div { width: 1px; height: 26px; background: rgba(255,255,255,.12); }
.hero-actions { display: flex; gap: 10px; margin-top: 16px; position: relative; z-index: 1; }
.btn {
  flex: 1; height: 40px; border-radius: 999px;
  background: rgba(255,255,255,.12);
  display: flex; align-items: center; justify-content: center; gap: 5px;
  font-size: 13px; font-weight: 700; color: #fff;
}
.btn .b-ic { font-size: 14px; }

/* ━━ section ━━ */
.section-title { display: flex; align-items: baseline; padding: 20px 16px 10px; }
.section-title .h { font-size: 16px; font-weight: 800; color: $t1; }
.section-title .sub { font-size: 11px; color: $t3; margin-left: 8px; }
.loading { padding: 40px; text-align: center; color: $t4; }

/* ━━ shop cards ━━ */
.shops { padding: 0 14px; }
.shop { background: #fff; border-radius: $r-lg; padding: 14px 16px; margin-bottom: 12px; box-shadow: $sh-2; }
.s-head { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.s-logo {
  width: 30px; height: 30px; border-radius: 9px; flex: none;
  background: linear-gradient(135deg, #FFD1BA, $o); color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 14px; font-weight: 800;
}
.s-logo.t2 { background: linear-gradient(135deg, #FFE8C9, $gold); }
.s-logo.t3 { background: linear-gradient(135deg, #D3F4D3, $mint); }
.s-logo.t4 { background: linear-gradient(135deg, #FFDDE5, #F472B6); }
.s-name { flex: 1; min-width: 0; font-size: 15px; font-weight: 800; color: $t1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.s-flag { flex: none; font-size: 10px; font-weight: 700; color: $o-d; background: $o-50; padding: 2px 8px; border-radius: 999px; }
.s-body { display: flex; align-items: center; }
.s-stat { flex: 1; text-align: center; }
.s-stat .v { font-size: 20px; font-weight: 900; color: $t1; font-variant-numeric: tabular-nums; }
.s-stat .v.gold { color: $gold-d; }
.s-stat .v.mint { color: $mint; }
.s-stat .l { font-size: 11px; color: $t3; margin-top: 2px; }
.s-divider { width: 1px; height: 30px; background: $line; }
.s-act { flex: none; margin-left: 12px; padding: 10px 16px; border-radius: 999px; background: linear-gradient(135deg, $o, $o-d); color: #fff; font-size: 13px; font-weight: 800; box-shadow: $sh-warm; }
.s-act.disabled { background: $bg-2; color: $t4; box-shadow: none; }

.foot-tip { padding: 14px 22px 0; font-size: 11px; color: $t4; line-height: 1.6; }
.bottom-pad { height: 20px; }
</style>
