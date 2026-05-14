<template>
  <view class="page">
    <view class="topbar safe-top">
      <text class="back" @click="goBack">‹</text>
      <text class="title">我的队列</text>
      <text class="right" @click="showRules">规则</text>
    </view>

    <view class="info-tip">
      <text class="b">邀请激励：</text>
      自购该商品 → 激活推广资格；自购或朋友首单买同款 → 累计 +1，按 1/N 返推广积分；
      累计满 N 次进入「已完成」，之后自购或朋友首单按订单金额 × 后续 % 返推广积分。
    </view>

    <view v-if="loading" class="empty-tip">加载中…</view>
    <view v-else-if="!list.length" class="empty-state">
      <view class="empty-emoji">🎯</view>
      <view class="empty-title">暂无队列记录</view>
      <view class="empty-sub">下单参与"邀请激励"的商品后会进入队列</view>
      <view class="empty-cta" @click="goHome">去逛附近店铺 ›</view>
    </view>

    <view v-else>
      <view v-for="row in list" :key="row.spuId" class="queue-card">
        <view class="hdr">
          <view class="pic-mini" :style="picStyle(row)">{{ initial(row) }}</view>
          <text class="name">{{ row.shopName || '店铺' }} · {{ row.spuName || `商品 #${row.spuId}` }}</text>
          <text :class="['state-tag', 'S-' + stateOf(row)]">{{ stateLabel(row) }}</text>
        </view>
        <view class="product">
          推 {{ row.maxN || '?' }} 反 1 ·
          {{ row.ratiosText || '比例配置中' }}
          · 商品价 ¥{{ fen2yuan(row.unitPrice || 0) }}
        </view>
        <view class="progress">
          <view
            v-for="i in (row.maxN || 0)"
            :key="i"
            :class="['seg', segClass(row, i)]"
          ></view>
        </view>
        <view class="progress-text">
          <text>已得 <text class="b">¥{{ fen2yuan(row.accumulatedAmount || 0) }}</text>（{{ row.accumulatedCount }}/{{ row.maxN || '?' }} 次）</text>
          <text v-if="(row.maxN - row.accumulatedCount) === 1" class="urgent">⚡ 还差 1 次出队</text>
          <text v-else-if="row.maxN > row.accumulatedCount">距出队还差 <text class="b">{{ row.maxN - row.accumulatedCount }}</text> 次</text>
          <text v-else class="success">✓ 已出队</text>
        </view>
      </view>
    </view>
    <view class="bottom-space"></view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { request } from '../../api/request.js';
import { fen2yuan } from '../../utils/format.js';

const list = ref([]);
const loading = ref(false);

const initial = (r) => (r.shopName || '店')[0];
const picStyle = (r) => {
  const palette = ['#ffd1ba,#ff6b35', '#c9e0ff,#6196f0', '#d3f4d3,#4cb84c', '#ffd0dc,#ee5a8b'];
  const idx = (Number(r.tenantId || r.spuId) || 0) % palette.length;
  return `background: linear-gradient(135deg, ${palette[idx]});`;
};

// v7：后端把 state(IN_PROGRESS / COMPLETED) 通过 layer 字段透传过来
function stateOf(row) {
  const v = row.layer || row.state || '';
  if (v === 'COMPLETED' || v === 'EXITED') return 'COMPLETED';
  return 'IN_PROGRESS';
}
function stateLabel(row) {
  return stateOf(row) === 'COMPLETED' ? '已完成 ✓' : '推广中';
}

function segClass(row, i) {
  // i 1-based
  const idx = i - 1;
  const acc = row.accumulatedCount || 0;
  if (idx < acc) return 'done';
  if (idx === acc && acc < (row.maxN || 0)) return 'cur';
  return '';
}

async function load() {
  loading.value = true;
  try {
    const data = await request({ url: '/app-api/merchant/mini/promo/my-queues' });
    list.value = Array.isArray(data) ? data : [];
  } catch { list.value = []; }
  finally { loading.value = false; }
}

function showRules() {
  uni.showModal({
    title: '邀请激励 规则',
    content: '1. 必须自购才激活资格（首单不返奖）\n2. 已激活：自购或朋友首单累计 +1，按 1/N 返推广积分\n3. 满 N 次后进入"已完成"，之后按订单金额 × 后续% 返推广积分\n4. 每个朋友对每个邀请人在每个商品上只贡献 1 次（首单）\n5. 邀请人未自购该商品 → 完全跳过，不发奖\n6. 返奖基准 = 用户实际支付金额',
    showCancel: false,
  });
}

function goBack() { uni.navigateBack({ fail: () => goHome() }); }
function goHome() { uni.reLaunch({ url: '/pages/user-home/index' }); }

onMounted(load);
onShow(load);
</script>

<style lang="scss" scoped>
@import '../../uni.scss';
.page { min-height: 100vh; background: $bg-page; }
.safe-top { padding-top: calc(env(safe-area-inset-top) + 16rpx); }

.topbar {
  display: flex; align-items: center; padding: 16rpx 32rpx;
  background: $bg-card; border-bottom: 1rpx solid $border-color;
}
.topbar .back { font-size: 44rpx; color: $text-primary; padding-right: 16rpx; }
.topbar .title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; color: $text-primary; }
.topbar .right { font-size: 26rpx; color: $brand-primary; }

.info-tip {
  margin: 24rpx 32rpx; padding: 24rpx 28rpx;
  background: linear-gradient(135deg, #fff5ef, #ffe1c8);
  border-left: 6rpx solid $brand-primary;
  border-radius: $radius-md;
  font-size: 24rpx; color: $text-primary; line-height: 1.6;
}
.info-tip .b { font-weight: 700; color: $brand-primary; }

.empty-tip { text-align: center; padding: 80rpx 0; color: $text-placeholder; font-size: 26rpx; }
.empty-state { text-align: center; padding: 120rpx 60rpx; }
.empty-state .empty-emoji { font-size: 96rpx; margin-bottom: 24rpx; opacity: .5; }
.empty-state .empty-title { font-size: 32rpx; font-weight: 700; color: $text-primary; }
.empty-state .empty-sub { margin-top: 12rpx; font-size: 24rpx; color: $text-placeholder; }
.empty-state .empty-cta {
  margin-top: 40rpx; display: inline-block;
  padding: 16rpx 40rpx; background: $brand-primary; color: #fff;
  border-radius: 999rpx; font-size: 26rpx; font-weight: 600;
}

.queue-card {
  margin: 24rpx 32rpx; padding: 32rpx;
  background: $bg-card; border-radius: $radius-lg;
  box-shadow: 0 4rpx 16rpx rgba(15,23,42,.04);
}
.hdr { display: flex; align-items: center; gap: 16rpx; margin-bottom: 16rpx; }
.pic-mini {
  width: 56rpx; height: 56rpx; border-radius: $radius-sm;
  color: #fff; font-size: 22rpx; font-weight: 800;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.name {
  flex: 1; font-size: 26rpx; font-weight: 700; color: $text-primary;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.state-tag {
  font-size: 20rpx; font-weight: 700;
  padding: 4rpx 16rpx; border-radius: 999rpx;
  flex-shrink: 0;
}
.state-tag.S-IN_PROGRESS { background: rgba(255,107,53,.18); color: $brand-primary; }
.state-tag.S-COMPLETED { background: rgba(16,185,129,.18); color: $success; }

.product { font-size: 22rpx; color: $text-secondary; margin-bottom: 24rpx; }

.progress { display: flex; gap: 8rpx; margin-bottom: 12rpx; }
.progress .seg {
  flex: 1; height: 16rpx; border-radius: 8rpx;
  background: $border-color;
}
.progress .seg.done { background: $brand-primary; }
.progress .seg.cur { background: $brand-primary; position: relative; }
.progress .seg.cur::after {
  content: ''; position: absolute; right: 0; top: 0; bottom: 0;
  width: 50%; background: rgba(255,107,53,.30);
}

.progress-text {
  display: flex; justify-content: space-between;
  font-size: 22rpx; color: $text-placeholder;
}
.progress-text .b {
  color: $brand-primary; font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.progress-text .urgent {
  color: $brand-primary; font-weight: 700;
}
.progress-text .success { color: $success; font-weight: 700; }

.bottom-space { height: 40rpx; }
</style>
