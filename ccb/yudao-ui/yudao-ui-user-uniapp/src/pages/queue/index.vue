<template>
  <view class="page">
    <nav-bar title="我的队列（推 N 反 1）" />
    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!queues.length" icon="🔥" title="还没有在排队的商品" desc="到店下单参与推 N 反 1" />
    <view v-else>
      <view v-for="q in queues" :key="q.queueId || q.id" class="qcard">
        <view class="q-head">
          <text class="q-shop">{{ q.shopName || '某店铺' }}</text>
          <text v-if="q.tuijianN" class="q-rule">推 {{ q.tuijianN }} 反 1</text>
        </view>
        <view class="q-spu">{{ q.spuName || '商品' }}</view>
        <view class="q-progress">
          <view class="qp-track">
            <view class="qp-fill" :style="{ width: progressPct(q) + '%' }"></view>
          </view>
          <view class="qp-text">{{ q.currentCount || 0 }} / {{ q.requiredCount || q.tuijianN || '?' }} 人 · 还差 <text class="b">{{ gap(q) }}</text> 人出队</view>
        </view>
        <view class="q-foot">
          <view class="q-reward">即将到账 <text class="hl">¥{{ fen2yuan(q.rewardAmount || 0, false) }}</text></view>
          <view class="q-share" @click="onShare(q)">分享拉人 →</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { listMyQueues } from '@/api/promo.js';
import { useUserStore } from '@/store/user.js';
import { fen2yuan } from '@/utils/format.js';

const user = useUserStore();
const loading = ref(true);
const queues = ref([]);

function progressPct(q) {
  const cur = q.currentCount || 0;
  const req = q.requiredCount || q.tuijianN || 1;
  return Math.min(100, Math.round((cur / req) * 100));
}
function gap(q) {
  const cur = q.currentCount || 0;
  const req = q.requiredCount || q.tuijianN || 0;
  return Math.max(0, req - cur);
}
function onShare(q) {
  const base = typeof location !== 'undefined' ? location.origin : 'https://m.doupaidoudian.com';
  const link = `${base}/#/pages/shop/home?tenantId=${q.tenantId}&inviter=${user.userId || ''}&spuId=${q.spuId || ''}`;
  uni.setClipboardData({ data: link, success: () => uni.showToast({ title: '链接已复制', icon: 'success' }) });
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
.qcard { background: #fff; margin: 10px 14px; border-radius: $r-lg; padding: 14px; box-shadow: $sh-1; }
.q-head { display: flex; justify-content: space-between; align-items: center; }
.q-shop { font-size: 13px; color: $t3; font-weight: 700; }
.q-rule { font-size: 11px; padding: 2px 8px; background: $o; color: #fff; border-radius: 6px; font-weight: 700; }
.q-spu { font-size: 15px; font-weight: 800; color: $t1; margin-top: 8px; }
.q-progress { margin-top: 12px; }
.qp-track { height: 8px; background: $line; border-radius: 4px; overflow: hidden; }
.qp-fill { height: 100%; background: linear-gradient(90deg, $o, $gold); transition: width .3s; }
.qp-text { font-size: 11px; color: $t3; margin-top: 4px; }
.qp-text .b { color: $o; font-weight: 800; }
.q-foot { display: flex; justify-content: space-between; align-items: center; margin-top: 12px; padding-top: 10px; border-top: 1px dashed $line; }
.q-reward { font-size: 13px; color: $t2; }
.q-reward .hl { color: $o; font-weight: 800; font-size: 18px; margin-left: 4px; }
.q-share { padding: 8px 16px; background: linear-gradient(135deg, $o, $o-d); color: #fff; border-radius: $r-pill; font-size: 12px; font-weight: 700; box-shadow: $sh-warm; }
</style>
