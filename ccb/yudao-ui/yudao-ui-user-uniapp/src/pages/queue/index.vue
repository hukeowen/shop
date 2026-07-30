<template>
  <view class="page">
    <nav-bar title="我的队列（推 N 反 1）" />

    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!queues.length" icon="🔥" title="还没有在排队的商品" desc="到店购买开通「推 N 反 1」的商品自动入队" />

    <view v-if="queues.length" class="rule-tip">
      💡 <text class="b">出队条件</text>：本商品被你<text class="b">推荐 N 次</text>（或自购 N 件）后自动出队。
      <text class="dim">店铺的促销优惠 / 分享激励等其他奖励，会进入你的"推广积分"但不影响出队进度。</text>
    </view>

    <view v-else>
      <view v-for="(q, i) in queues" :key="`${q.tenantId}-${q.spuId}-${i}`" class="qcard">
        <view class="q-head">
          <text class="q-shop">{{ q.shopName || `店铺#${q.tenantId}` }}</text>
          <text v-if="q.maxN" class="q-rule">推 {{ q.maxN }} 反 1</text>
          <text v-if="q.layer" class="q-layer" :class="`l-${q.layer.toLowerCase()}`">{{ layerLabel(q.layer) }}</text>
        </view>

        <view class="q-spu" @click="goSpu(q)">{{ q.spuName || '商品' }} ›</view>

        <view class="q-progress">
          <view class="qp-track">
            <view class="qp-fill" :style="{ width: progressPct(q) + '%' }"></view>
          </view>
          <view class="qp-text">
            进度
            <text class="b">{{ q.accumulatedCount || 0 }} / {{ q.maxN || '?' }}</text>
            <text v-if="gap(q) > 0"> · 还差 <text class="b">{{ gap(q) }}</text> 次反奖出队</text>
            <text v-else> · 已满足出队条件</text>
          </view>
        </view>

        <view class="q-ratios" v-if="q.ratiosText">分配规则：{{ q.ratiosText }}</view>

        <view class="q-stats">
          <view class="q-stat">
            <text class="v">¥{{ fen2yuan(q.accumulatedAmount || 0, false) }}</text>
            <text class="l">本队反奖</text>
          </view>
          <view class="q-stat-divider"></view>
          <view class="q-stat">
            <text class="v hl">¥{{ fen2yuan(q.unitPrice || 0, false) }}</text>
            <text class="l">下次到账</text>
          </view>
          <view class="q-stat-divider"></view>
          <view class="q-stat">
            <text class="v sm">{{ fmtTime(q.joinedAt) || '—' }}</text>
            <text class="l">入队时间</text>
          </view>
        </view>

        <view class="q-foot">
          <view class="q-share" @click="onShare(q)">📤 分享拉人 加速出队</view>
        </view>
      </view>
    </view>
    <view class="bottom-pad"></view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { listMyQueues } from '@/api/promo.js';
import { useUserStore } from '@/store/user.js';
import { fen2yuan, fmtTime } from '@/utils/format.js';

const user = useUserStore();
const loading = ref(true);
const queues = ref([]);

function progressPct(q) {
  const cur = Number(q.accumulatedCount) || 0;
  const req = Number(q.maxN) || 1;
  return Math.min(100, Math.round((cur / req) * 100));
}
function gap(q) {
  const cur = Number(q.accumulatedCount) || 0;
  const req = Number(q.maxN) || 0;
  return Math.max(0, req - cur);
}
function layerLabel(l) {
  if (l === 'A') return 'A 层 · 优先返奖';
  if (l === 'B') return 'B 层 · 排队中';
  return l;
}
function goSpu(q) {
  if (!q.spuId) return;
  uni.navigateTo({ url: `/pages/product/detail?id=${q.spuId}&tenantId=${q.tenantId || ''}` });
}
function onShare(q) {
  // #ifdef H5
  const base = location.origin;
  // #endif
  // #ifndef H5
  const base = 'https://ke.doupaidoudian.com';
  // #endif
  const link = `${base}/#/pages/product/detail?id=${q.spuId || ''}&tenantId=${q.tenantId || ''}&inviter=${user.userId || ''}`;
  uni.setClipboardData({ data: link, success: () => uni.showToast({ title: '链接已复制 · 发给朋友', icon: 'success' }) });
}

async function load() {
  loading.value = true;
  try { queues.value = await listMyQueues() || []; }
  catch { queues.value = []; }
  finally { loading.value = false; }
}
onMounted(load);
onShow(load);
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.page { min-height: 100vh; background: $bg-2; padding-bottom: 20px; }
.loading { padding: 40px; text-align: center; color: $t4; }
.bottom-pad { height: 30px; }

.rule-tip {
  margin: 10px 14px;
  padding: 10px 12px;
  background: $o-50;
  color: $t2;
  border-radius: $r-md;
  border: 1px solid $o-100;
  font-size: 12px;
  line-height: 1.6;
}
.rule-tip .b { color: $o-d; font-weight: 800; }
.rule-tip .dim { color: $t3; font-size: 11px; display: block; margin-top: 4px; }

.qcard {
  background: #fff;
  margin: 10px 14px;
  border-radius: $r-lg;
  padding: 16px;
  box-shadow: 0 1px 2px rgba(15,23,42,.04), 0 4px 12px rgba(15,23,42,.05);
}

.q-head {
  display: flex; align-items: center; gap: 6px; flex-wrap: wrap;
}
.q-shop { font-size: 13px; color: $t3; font-weight: 700; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.q-rule {
  font-size: 11px;
  padding: 3px 8px;
  background: linear-gradient(135deg, $o, $o-d);
  color: #fff; border-radius: 6px; font-weight: 800;
  box-shadow: $sh-warm;
}
.q-layer {
  font-size: 10.5px;
  padding: 3px 8px;
  border-radius: 6px;
  font-weight: 800;
}
.q-layer.l-a { background: linear-gradient(135deg, $gold, $gold-d); color: #fff; }
.q-layer.l-b { background: $bg-2; color: $t3; }

.q-spu {
  font-size: 15px; font-weight: 800; color: $t1; margin-top: 10px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

.q-progress { margin-top: 12px; }
.qp-track {
  height: 10px; background: $bg-2; border-radius: 5px; overflow: hidden;
  border: 1px solid $line;
}
.qp-fill {
  height: 100%;
  background: linear-gradient(90deg, $o, $gold);
  transition: width .3s ease;
  box-shadow: 0 1px 4px rgba(255,107,53,.4);
}
.qp-text { font-size: 11.5px; color: $t3; margin-top: 6px; font-variant-numeric: tabular-nums; }
.qp-text .b { color: $o-d; font-weight: 800; }

.q-ratios {
  margin-top: 10px;
  padding: 8px 12px;
  background: $o-50;
  color: $o-d;
  border-radius: $r-md;
  font-size: 11.5px;
  font-weight: 600;
}

.q-stats {
  display: flex; align-items: center;
  margin-top: 12px;
  padding: 10px 0;
  border-top: 1px dashed $line;
  border-bottom: 1px dashed $line;
}
.q-stat {
  flex: 1; display: flex; flex-direction: column; align-items: center; gap: 2px;
}
.q-stat .v { font-size: 16px; font-weight: 800; color: $t1; font-variant-numeric: tabular-nums; }
.q-stat .v.hl { color: $o; }
.q-stat .v.sm { font-size: 12px; color: $t2; font-weight: 700; }
.q-stat .l { font-size: 10.5px; color: $t4; }
.q-stat-divider { width: 1px; height: 26px; background: $line; }

.q-foot {
  margin-top: 14px;
  display: flex; justify-content: center;
}
.q-share {
  padding: 10px 22px;
  background: linear-gradient(135deg, $o, $o-d);
  color: #fff; border-radius: $r-pill;
  font-size: 13px; font-weight: 800;
  box-shadow: $sh-warm;
}
</style>
