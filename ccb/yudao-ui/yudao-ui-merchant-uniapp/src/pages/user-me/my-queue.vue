<template>
  <view class="page">
    <view class="topbar safe-top">
      <text class="back" @click="goBack">‹</text>
      <text class="title">我的队列</text>
      <text class="right" @click="showRules">规则</text>
    </view>

    <view class="info-tip">
      <text class="b">v6 推 N 反 1：</text>
      你买过的商品会进入队列，朋友通过你或自然进店买同款 → 你按当前位置拿对应比例的推广积分；累计满 N 次出队，共得 100% 商品价。
    </view>

    <view v-if="loading" class="empty-tip">加载中…</view>
    <view v-else-if="!list.length" class="empty-state">
      <view class="empty-emoji">🎯</view>
      <view class="empty-title">暂无队列记录</view>
      <view class="empty-sub">下单参与"推 N 反 1"的商品后会进入队列</view>
      <view class="empty-cta" @click="goHome">去逛附近店铺 ›</view>
    </view>

    <view v-else>
      <view v-for="row in list" :key="row.spuId" class="queue-card">
        <view class="hdr">
          <view class="pic-mini" :style="picStyle(row)">{{ initial(row) }}</view>
          <text class="name">{{ row.shopName || '店铺' }} · {{ row.spuName || `商品 #${row.spuId}` }}</text>
          <text :class="['layer-tag', 'L-' + (row.layer || 'B')]">{{ row.layer || 'B' }} 层</text>
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
    title: 'v6 推 N 反 1 规则',
    content: '· A 层（主动）：自购或推下级成交 → 优先返奖\n· B 层（被动）：自然消费进队 → A 空才轮到\n· 满 N 次出队，累计返 100% 商品价\n· 终生制，不降级',
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
.layer-tag {
  font-size: 20rpx; font-weight: 700;
  padding: 4rpx 16rpx; border-radius: 999rpx;
}
.layer-tag.L-A { background: rgba(255,107,53,.18); color: $brand-primary; }
.layer-tag.L-B { background: rgba(99,102,241,.15); color: #6366F1; }

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
