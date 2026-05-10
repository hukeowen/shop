<template>
  <view class="page">
    <!-- 周期 tabs -->
    <view class="period-tabs">
      <view class="pt" :class="{ active: period === 'day' }" @click="setPeriod('day')">日</view>
      <view class="pt" :class="{ active: period === 'week' }" @click="setPeriod('week')">周</view>
      <view class="pt" :class="{ active: period === 'month' }" @click="setPeriod('month')">月</view>
      <view class="pt" :class="{ active: period === 'year' }" @click="setPeriod('year')">年</view>
      <view class="pt-label">{{ data?.periodLabel || '-' }}</view>
    </view>

    <!-- 总览大数 -->
    <view class="summary-card" v-if="data">
      <view class="sc-period">本{{ periodName }}销售总览</view>
      <view class="sc-big">¥{{ fen2yuan(data.salesAmount) }}</view>
      <view class="sc-label">销售总金额（订单按定价合计）</view>
      <view class="sc-grid">
        <view class="sc-item">
          <text class="l">实付总金额</text>
          <text class="v">¥{{ fen2yuan(data.actualPayAmount) }}</text>
        </view>
        <view class="sc-item">
          <text class="l">推广积分发出</text>
          <text class="v">¥{{ fen2yuan(data.promoIssued) }}</text>
        </view>
        <view class="sc-item">
          <text class="l">入池累计</text>
          <text class="v">¥{{ fen2yuan(data.poolDeposit) }}</text>
        </view>
        <view class="sc-item">
          <text class="l">退款 / 售后</text>
          <text class="v">¥{{ fen2yuan(data.refundAmount) }}</text>
        </view>
      </view>
      <view class="sc-net">
        <text class="l">净收入 = 实付 - 退款 - 推广积分支出</text>
        <text class="v">¥{{ fen2yuan(data.netIncome) }}</text>
      </view>
    </view>

    <!-- 资金分布 -->
    <view class="panel" v-if="data">
      <view class="panel-h">
        <text class="t">💰 资金分布</text>
      </view>
      <view class="funds-grid">
        <view class="funds-item">
          <text class="l">用户应付未提余额</text>
          <text class="v warn">¥{{ fen2yuan(data.pendingBalance) }}</text>
        </view>
        <view class="funds-item">
          <text class="l">已提现累计</text>
          <text class="v">¥{{ fen2yuan(data.withdrawnAmount) }}</text>
        </view>
        <view class="funds-item">
          <text class="l">待处理提现申请</text>
          <text class="v">{{ data.pendingWithdrawCount }} 笔</text>
        </view>
        <view class="funds-item">
          <text class="l">店铺余额</text>
          <text class="v success">¥{{ fen2yuan(data.shopBalance) }}</text>
        </view>
      </view>
    </view>

    <!-- 双线趋势 -->
    <view class="panel" v-if="data">
      <view class="panel-h">
        <text class="t">📊 趋势</text>
        <text class="sub">{{ data.trendLabels.length }} 个数据点</text>
      </view>
      <view class="trend-bigchart">
        <view
          class="b"
          v-for="(s, i) in data.trendSales"
          :key="i"
        >
          <view class="bf" :style="{ height: barH(s, maxSales) + '%' }"></view>
        </view>
      </view>
      <view class="trend-legend">
        <view class="lg"><view class="sw brand"></view>销售额</view>
        <view class="lg"><view class="sw success"></view>实付（v8 抵扣后）</view>
      </view>
    </view>

    <!-- 推广积分对账 -->
    <view class="panel" v-if="data">
      <view class="panel-h">
        <text class="t">🎯 推广积分对账（v8）</text>
      </view>
      <view class="reconcile-row">
        <text class="l">发出推广积分（订单触发）</text>
        <text class="v outflow">-¥{{ fen2yuan(data.promoIssued) }}</text>
      </view>
      <view class="reconcile-row">
        <text class="l">入池累计（未发放）</text>
        <text class="v income">+¥{{ fen2yuan(data.poolDeposit) }}</text>
      </view>
    </view>

    <!-- 客户洞察 -->
    <view class="panel" v-if="data">
      <view class="panel-h">
        <text class="t">👥 客户洞察</text>
      </view>
      <view class="funds-grid">
        <view class="funds-item">
          <text class="l">客单价</text>
          <text class="v">¥{{ fen2yuan(data.avgOrderValue) }}</text>
        </view>
        <view class="funds-item">
          <text class="l">复购率</text>
          <text class="v">{{ (data.repurchaseRate || 0).toFixed(1) }}%</text>
        </view>
        <view class="funds-item">
          <text class="l">新客 / 老客</text>
          <text class="v">{{ data.newCustomerOrders || 0 }} / {{ data.oldCustomerOrders || 0 }}</text>
        </view>
        <view class="funds-item">
          <text class="l">推荐订单占比</text>
          <text class="v">{{ (data.referralOrderRatio || 0).toFixed(1) }}%</text>
        </view>
      </view>
    </view>

    <!-- 时段热力图 -->
    <view class="panel" v-if="heatmap">
      <view class="panel-h">
        <text class="t">⏰ 时段订单热力（{{ period === 'year' ? '本年' : (period === 'week' ? '本周' : period === 'day' ? '今日' : '本月') }}）</text>
      </view>
      <view class="heatmap">
        <view
          class="cell"
          :class="hLevel(h)"
          v-for="(h, i) in heatmap.hourly"
          :key="i"
          :title="i + ':00 ' + h + ' 单'"
        ></view>
      </view>
      <view class="heatmap-label">
        <text>0:00</text>
        <text>6:00</text>
        <text>12:00</text>
        <text>18:00</text>
        <text>23:59</text>
      </view>
      <view class="heatmap-legend">
        <text>少</text>
        <view class="sw" style="background: #f0f0f3;"></view>
        <view class="sw" style="background: #fff5e6;"></view>
        <view class="sw" style="background: #ffd6b3;"></view>
        <view class="sw" style="background: #ffae74;"></view>
        <view class="sw" style="background: #FF6B35;"></view>
        <text>多</text>
      </view>
    </view>

    <!-- 推 N 反 1 漏斗 -->
    <view class="panel" v-if="funnel">
      <view class="panel-h">
        <text class="t">🎯 推 N 反 1 漏斗</text>
      </view>
      <view class="funnel">
        <view class="funnel-row">
          <text class="name">激活</text>
          <view class="funnel-bar" :style="{ width: '100%' }">
            <text>{{ funnel.activatedUsers }} 人</text>
            <text>100%</text>
          </view>
        </view>
        <view class="funnel-row">
          <text class="name">推进中</text>
          <view class="funnel-bar" :style="{ width: pct(funnel.inProgressUsers, funnel.activatedUsers) + '%' }">
            <text>{{ funnel.inProgressUsers }} 人 IN_PROGRESS</text>
            <text>{{ pct(funnel.inProgressUsers, funnel.activatedUsers) }}%</text>
          </view>
        </view>
        <view class="funnel-row">
          <text class="name">已完成</text>
          <view class="funnel-bar" :style="{ width: pct(funnel.completedUsers, funnel.activatedUsers) + '%' }">
            <text>{{ funnel.completedUsers }} 人 COMPLETED</text>
            <text>{{ pct(funnel.completedUsers, funnel.activatedUsers) }}%</text>
          </view>
        </view>
        <view class="funnel-row">
          <text class="name">直推有效</text>
          <view class="funnel-bar" :style="{ width: pct(funnel.contributionCount, funnel.activatedUsers) + '%' }">
            <text>{{ funnel.contributionCount }} 对首贡献</text>
            <text>{{ pct(funnel.contributionCount, funnel.activatedUsers) }}%</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 商品排行 -->
    <view class="panel" v-if="rank.length">
      <view class="panel-h">
        <text class="t">🛍 商品销售排行 Top {{ rank.length }}</text>
        <text class="more" @click="goRankFull">完整排行 ›</text>
      </view>
      <view class="rank-row" v-for="(p, i) in rank" :key="p.spuId">
        <view class="rank-no" :class="rankClass(i)">{{ i + 1 }}</view>
        <view class="rank-name">{{ p.name }}</view>
        <view class="rank-meta">
          <text class="cnt">售 {{ p.salesCount }} 件</text>
          <text class="amt">¥{{ fen2yuan(p.salesAmount) }}</text>
        </view>
      </view>
    </view>

    <view class="bottom-space"></view>
  </view>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { fen2yuan } from '../../utils/format.js';
import {
  getSalesStats, getProductRank, getHourlyHeatmap, getReferralFunnel,
} from '../../api/report.js';

const period = ref('month');
const data = ref(null);
const rank = ref([]);
const heatmap = ref(null);
const funnel = ref(null);

const periodName = computed(() => ({ day: '日', week: '周', month: '月', year: '年' }[period.value] || '月'));
const maxSales = computed(() => Math.max(1, ...(data.value?.trendSales || [1])));

function barH(v, max) {
  if (!max || max <= 0) return 0;
  return Math.max(2, Math.round((v / max) * 100));
}
function pct(num, total) {
  if (!total || total <= 0) return 0;
  return Math.round((num / total) * 100);
}
function hLevel(h) {
  if (!h || h === 0) return '';
  // 阶梯：1-2 单 l1, 3-5 l2, 6-10 l3, 10+ l4
  if (h >= 10) return 'l4';
  if (h >= 6) return 'l3';
  if (h >= 3) return 'l2';
  return 'l1';
}
function rankClass(i) {
  return ['gold', 'silver', 'bronze'][i] || 'normal';
}
async function setPeriod(p) {
  period.value = p;
  await loadAll();
}
function goRankFull() {
  uni.navigateTo({ url: '/pages/me/product-rank' });
}

async function loadAll() {
  try {
    data.value = await getSalesStats(period.value);
  } catch {}
  try {
    rank.value = await getProductRank(period.value, 'amount', 5) || [];
  } catch { rank.value = []; }
  try {
    heatmap.value = await getHourlyHeatmap(period.value === 'year' ? 'month' : period.value);
  } catch {}
  try {
    funnel.value = await getReferralFunnel();
  } catch {}
}

onMounted(loadAll);
onShow(loadAll);
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  min-height: 100vh;
  background: $bg-page;
  padding-bottom: 32rpx;
}

.period-tabs {
  display: flex; align-items: center;
  background: #fff; padding: 16rpx 24rpx; gap: 12rpx;
  border-bottom: 1rpx solid $border-color;
  .pt {
    padding: 8rpx 28rpx; border-radius: 999rpx;
    background: #f0f0f3; font-size: 26rpx;
    &.active { background: $brand-primary; color: #fff; }
  }
  .pt-label { margin-left: auto; font-size: 24rpx; color: $text-secondary; }
}

.summary-card {
  margin: 24rpx;
  padding: 32rpx 28rpx;
  background: linear-gradient(135deg, #FF6B35 0%, #FF9A4A 100%);
  border-radius: 24rpx;
  color: #fff;
  .sc-period { font-size: 24rpx; opacity: 0.85; }
  .sc-big {
    font-size: 64rpx; font-weight: 800; margin: 8rpx 0 4rpx;
    font-variant-numeric: tabular-nums;
  }
  .sc-label { font-size: 24rpx; opacity: 0.85; margin-bottom: 24rpx; }
  .sc-grid {
    display: grid; grid-template-columns: 1fr 1fr; gap: 12rpx;
  }
  .sc-item {
    background: rgba(255, 255, 255, 0.18); border-radius: 12rpx; padding: 14rpx 16rpx;
    .l { display: block; font-size: 22rpx; opacity: 0.9; }
    .v { display: block; font-size: 30rpx; font-weight: 700; margin-top: 4rpx; }
  }
  .sc-net {
    margin-top: 20rpx; padding-top: 20rpx;
    border-top: 1rpx dashed rgba(255, 255, 255, 0.3);
    display: flex; justify-content: space-between; align-items: baseline;
    .l { font-size: 24rpx; opacity: 0.9; }
    .v { font-size: 38rpx; font-weight: 800; }
  }
}

.panel {
  margin: 16rpx 24rpx;
  padding: 24rpx;
  background: #fff;
  border-radius: 16rpx;
  .panel-h {
    display: flex; justify-content: space-between; align-items: center;
    margin-bottom: 16rpx;
    .t { font-size: 28rpx; font-weight: 600; }
    .sub, .more { font-size: 22rpx; color: $text-secondary; }
    .more { color: $brand-primary; }
  }
}

.funds-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: 12rpx;
}
.funds-item {
  padding: 16rpx 20rpx;
  background: #f7f7f8; border-radius: 12rpx;
  .l { display: block; font-size: 22rpx; color: $text-secondary; }
  .v {
    display: block; font-size: 32rpx; font-weight: 700; margin-top: 4rpx;
    font-variant-numeric: tabular-nums;
    &.warn { color: $warning; }
    &.success { color: $success; }
  }
}

.trend-bigchart {
  display: flex; align-items: flex-end; justify-content: space-between;
  height: 160rpx; margin-top: 8rpx;
  .b { flex: 1; margin: 0 2rpx; display: flex; flex-direction: column; align-items: center; justify-content: flex-end; }
  .bf { width: 100%; max-width: 32rpx; background: linear-gradient(180deg, $brand-primary, #ffe1cc); border-radius: 4rpx 4rpx 0 0; }
}
.trend-legend {
  display: flex; gap: 24rpx; font-size: 22rpx; color: $text-secondary; margin-top: 12rpx;
  .lg { display: flex; align-items: center; gap: 8rpx; }
  .sw { width: 18rpx; height: 18rpx; border-radius: 4rpx; }
  .sw.brand { background: $brand-primary; }
  .sw.success { background: $success; }
}

.reconcile-row {
  display: flex; justify-content: space-between; padding: 12rpx 0;
  font-size: 26rpx;
  border-bottom: 1rpx dashed $border-color;
  &:last-child { border-bottom: none; }
  .l { color: $text-secondary; }
  .v {
    font-weight: 600; font-variant-numeric: tabular-nums;
    &.income { color: $success; }
    &.outflow { color: $danger; }
  }
}

.heatmap {
  display: grid; grid-template-columns: repeat(24, 1fr); gap: 4rpx; margin-top: 8rpx;
  .cell {
    aspect-ratio: 1; border-radius: 4rpx; background: #f0f0f3;
    &.l1 { background: #fff5e6; }
    &.l2 { background: #ffd6b3; }
    &.l3 { background: #ffae74; }
    &.l4 { background: $brand-primary; }
  }
}
.heatmap-label {
  display: flex; justify-content: space-between; font-size: 18rpx; color: $text-secondary; margin-top: 8rpx;
}
.heatmap-legend {
  display: flex; align-items: center; gap: 8rpx; font-size: 18rpx; color: $text-secondary; justify-content: flex-end; margin-top: 12rpx;
  .sw { width: 22rpx; height: 22rpx; border-radius: 4rpx; }
}

.funnel {
  padding-top: 8rpx;
  .funnel-row { display: flex; align-items: center; margin-bottom: 12rpx; }
  .name { width: 120rpx; font-size: 24rpx; flex-shrink: 0; }
  .funnel-bar {
    height: 50rpx; border-radius: 8rpx;
    background: linear-gradient(90deg, $brand-primary, #ffae74);
    color: #fff; font-size: 22rpx; font-weight: 600;
    padding: 0 16rpx; line-height: 50rpx;
    display: flex; justify-content: space-between; align-items: center;
    min-width: 50rpx; box-sizing: border-box;
  }
}

.rank-row {
  display: flex; align-items: center; padding: 16rpx 0;
  border-bottom: 1rpx solid $border-color;
  &:last-child { border-bottom: none; }
  .rank-no {
    width: 44rpx; height: 44rpx; border-radius: 50%;
    text-align: center; line-height: 44rpx;
    color: #fff; font-size: 24rpx; font-weight: 700;
    background: $text-placeholder;
    margin-right: 16rpx;
    &.gold { background: #ffcc00; }
    &.silver { background: #c0c5cf; }
    &.bronze { background: #d6793a; }
  }
  .rank-name { flex: 1; font-size: 28rpx; }
  .rank-meta {
    text-align: right;
    .cnt { font-size: 22rpx; color: $text-secondary; display: block; }
    .amt { font-size: 28rpx; color: $brand-primary; font-weight: 700; }
  }
}

.bottom-space { height: 80rpx; }
</style>
