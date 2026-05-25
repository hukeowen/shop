<template>
  <view class="page">
    <nav-bar title="我的队列（推 N 反 1）" />
    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!queues.length" icon="🔥" title="还没有在排队的商品" desc="到店下单参与推 N 反 1" />
    <view v-else>
      <view v-for="q in queues" :key="q.id" class="qcard">
        <view class="q-head">
          <text class="q-shop">{{ q.shopName }}</text>
          <text class="q-rule">推 {{ q.n }} 反 1</text>
        </view>
        <view class="q-spu">{{ q.spuName }}</view>
        <view class="q-progress">
          <view class="qp-track">
            <view class="qp-fill" :style="{ width: (q.progress * 100) + '%' }"></view>
          </view>
          <view class="qp-text">{{ q.current }} / {{ q.n }} 人</view>
        </view>
        <view class="q-foot">
          <view class="q-reward">即将到账 <text class="hl">¥{{ q.reward }}</text></view>
          <view class="q-share" @click="onShare(q)">分享拉人 →</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
// import { getMyQueue } from '@/api/promo.js';

const loading = ref(true);
const queues = ref([]);

function onShare(q) {
  uni.showToast({ title: '分享链接已复制', icon: 'success' });
  uni.setClipboardData({ data: `${location?.origin || ''}/u/#/pages/shop/home?tenantId=${q.tenantId}&inviter=${q.userId}` });
}

onMounted(async () => {
  loading.value = true;
  try { queues.value = []; /* queues.value = await getMyQueue(); */ }
  finally { loading.value = false; }
});
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background: $bg-2; padding-bottom: 20px; }
.loading { padding: 40px; text-align: center; color: $t4; }
.qcard { background: #fff; margin: 10px 14px; border-radius: $r-lg; padding: 14px; box-shadow: $sh-1; }
.q-head { display: flex; justify-content: space-between; }
.q-shop { font-size: 13px; color: $t3; font-weight: 700; }
.q-rule { font-size: 11px; padding: 2px 8px; background: $o; color: #fff; border-radius: 6px; font-weight: 700; }
.q-spu { font-size: 15px; font-weight: 800; color: $t1; margin-top: 8px; }
.q-progress { margin-top: 12px; }
.qp-track { height: 8px; background: $line; border-radius: 4px; overflow: hidden; }
.qp-fill { height: 100%; background: linear-gradient(90deg, $o, $gold); transition: width .3s; }
.qp-text { font-size: 11px; color: $t3; margin-top: 4px; }
.q-foot { display: flex; justify-content: space-between; align-items: center; margin-top: 12px; padding-top: 10px; border-top: 1px dashed $line; }
.q-reward { font-size: 13px; color: $t2; }
.q-reward .hl { color: $o; font-weight: 800; font-size: 18px; margin-left: 4px; }
.q-share { padding: 8px 16px; background: linear-gradient(135deg, $o, $o-d); color: #fff; border-radius: $r-pill; font-size: 12px; font-weight: 700; box-shadow: $sh-warm; }
</style>
