<template>
  <view class="page">
    <nav-bar title="我的钱包" bg="transparent" txt="#fff" />
    <view class="hero">
      <view class="hero-tag">💎 推广积分（商户营销活动凭证）</view>
      <view class="hero-amt">{{ promoPoints }} <text class="unit">积分</text></view>
      <view class="hero-sub">积分为商户独立营销活动凭证 · 不构成货币 · 兑付规则与额度由商户独立设定与审批</view>
      <view class="hero-row">
        <view class="hr-col">
          <view class="hr-l">推广积分</view>
          <view class="hr-v">{{ promoPoints }} <text class="u">积分</text></view>
        </view>
        <view class="hr-col">
          <view class="hr-l">消费积分</view>
          <view class="hr-v">{{ consumePoints }} <text class="u">积分</text></view>
        </view>
        <view class="hr-col">
          <view class="hr-l">今日入账</view>
          <view class="hr-v">{{ todayPoints }} <text class="u">积分</text></view>
        </view>
      </view>
      <view class="hero-actions">
        <view class="btn warm" @click="goWithdraw">申请兑付</view>
        <view class="btn ghost" @click="goWithdrawList">兑付记录</view>
        <view class="btn ghost" @click="goPromoRecords">推广明细</view>
      </view>
    </view>

    <view class="section-title"><text class="h">最近到账</text></view>
    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!records.length" title="暂无到账记录" />
    <view v-else>
      <view v-for="r in records" :key="r.id" class="row">
        <view class="r-ic">{{ iconFor(r.sourceType) }}</view>
        <view class="r-body">
          <view class="r-t">{{ r.remark || labelFor(r.sourceType) }}</view>
          <view class="r-d">{{ fmtTime(r.createTime) }}</view>
        </view>
        <view class="r-amt" :class="{ neg: r.amount < 0 }">{{ r.amount > 0 ? '+' : '' }}{{ r.amount }} <text class="u">积分</text></view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getAccount, listPromoRecords, getTodayStat } from '@/api/promo.js';
import { fmtTime } from '@/utils/format.js';

const promoPoints = ref(0);
const consumePoints = ref(0);
const todayPoints = ref(0);
const loading = ref(false);
const records = ref([]);

function iconFor(t) {
  if (t === 'POOL')       return '🏆';
  if (t === 'QUEUE')      return '💰';
  if (t === 'DIRECT')     return '👥';
  if (t === 'COMMISSION') return '⭐';
  if (t === 'WITHDRAW')   return '💸';
  if (t === 'CONVERT')    return '🔄';
  return '🪙';
}
function labelFor(t) {
  if (t === 'POOL' || t === 'POOL_V8')              return '促销让利抽中';
  if (t === 'QUEUE')                                 return '邀请累积奖';
  if (t === 'DIRECT')                                return '邀请有礼';
  if (t === 'COMMISSION')                            return '分享激励';
  if (t === 'SELF_BATCH' || t === 'SELF_PROGRESS')   return '复购感谢奖';
  if (t === 'SELF_COMMISSION')                       return '复购感谢奖';
  if (t === 'REFERRAL_PROGRESS' ||
      t === 'REFERRAL_COMMISSION')                   return '分享感谢奖';
  if (t === 'WITHDRAW')                              return '提现';
  if (t === 'CONVERT')                               return '积分兑换';
  if (t === 'MANUAL_PATCH')                          return '账户调整';
  if (t === 'REDEEM_ORDER')                          return '订单抵扣';
  if (t === 'ORDER_DEDUCT')                          return '订单使用';
  return '分享奖励';
}

function goWithdraw()        { uni.navigateTo({ url: '/pages/withdraw/index' }); }
function goWithdrawList()    { uni.navigateTo({ url: '/pages/withdraw/list' }); }
function goPromoRecords()    { uni.navigateTo({ url: '/pages/points/promo' }); }
function goConsumeRecords()  { uni.navigateTo({ url: '/pages/points/consume' }); }

async function load() {
  try {
    const acct = await getAccount();
    promoPoints.value = acct?.promoPointBalance || 0;
    consumePoints.value = acct?.consumePointBalance || 0;
  } catch {}
  try {
    const stat = await getTodayStat();
    todayPoints.value = stat?.promoAmountToday || 0;
  } catch {}
  loading.value = true;
  try {
    const page = await listPromoRecords(1, 20);
    records.value = page?.list || [];
  } finally { loading.value = false; }
}
onMounted(load);
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
.hero-row { display: flex; margin-top: 16px; }
.hr-col { flex: 1; }
.hr-l { font-size: 11px; opacity: .6; }
.hr-v { font-size: 18px; font-weight: 800; margin-top: 2px; }
.hr-v .u { font-size: 11px; font-weight: 600; color: rgba(255,255,255,.7); margin-left: 2px; }
.hero-actions { display: flex; gap: 8px; margin-top: 20px; }
.btn { flex: 1; padding: 12px 0; text-align: center; border-radius: $r-pill; font-weight: 800; font-size: 13px; }
.btn.warm { background: linear-gradient(135deg, $o, $o-d); color: #fff; box-shadow: $sh-warm; }
.btn.ghost { background: rgba(255,255,255,.12); color: #fff; }
.section-title { padding: 18px 14px 8px; }
.section-title .h { font-size: 14px; font-weight: 800; color: $t1; }
.loading { padding: 40px; text-align: center; color: $t4; }
.row { display: flex; gap: 10px; padding: 12px; background: #fff; margin: 6px 14px; border-radius: $r-md; align-items: center; box-shadow: $sh-1; }
.r-ic { width: 36px; height: 36px; background: $o-50; color: $o; border-radius: 10px; display: flex; align-items: center; justify-content: center; font-size: 16px; }
.r-body { flex: 1; min-width: 0; }
.r-t { font-size: 13px; font-weight: 700; color: $t1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.r-d { font-size: 11px; color: $t3; margin-top: 2px; }
.r-amt { font-size: 16px; font-weight: 800; color: $o; flex-shrink: 0; }
.r-amt.neg { color: $t3; }
.r-amt .u { font-size: 11px; font-weight: 600; color: $t3; margin-left: 2px; }
</style>
