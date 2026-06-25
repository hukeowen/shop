<template>
  <view class="page">
    <nav-bar title="我的卡包" />

    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!groups.length" icon="🎫" title="暂无卡包" desc="购买含服务卡的商品后，卡会自动到这里" />

    <view v-else class="wrap">
      <view v-for="g in groups" :key="g.tenantId" class="shop-group">
        <view class="shop-head">
          <view class="shop-logo">{{ (g.shopName || '店')[0] }}</view>
          <text class="shop-name">{{ g.shopName }}</text>
          <text class="shop-cnt">{{ g.cards.length }} 张</text>
        </view>

        <view v-for="c in g.cards" :key="c.id" class="card" :class="'st-' + c.effectiveStatus" @click="goDetail(c)">
          <view class="card-l">
            <view class="card-name-row">
              <text class="card-name">{{ c.name }}</text>
              <text class="badge" :class="'b-' + c.effectiveStatus">{{ statusText(c.effectiveStatus) }}</text>
            </view>
            <view class="card-meta">
              <text class="m">{{ c.unlimited ? '不限次数' : ('剩 ' + c.remainCount + '/' + c.maxCount + ' 次') }}</text>
              <text class="dot">·</text>
              <text class="m">{{ fmtDate(c.expireTime) }} 到期</text>
            </view>
          </view>
          <view class="card-r">
            <text class="show">出示</text>
            <text class="arrow">›</text>
          </view>
        </view>
      </view>
    </view>

    <view class="foot-tip">服务卡由对应商户提供与核销，平台仅做技术信息撮合。</view>
    <view class="bottom-pad"></view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { listMyCards } from '@/api/card.js';

const loading = ref(true);
const cards = ref([]);

// 按店分组（保持后端返回的倒序）
const groups = computed(() => {
  const map = new Map();
  for (const c of cards.value) {
    if (!map.has(c.tenantId)) {
      map.set(c.tenantId, { tenantId: c.tenantId, shopName: c.shopName, cards: [] });
    }
    map.get(c.tenantId).cards.push(c);
  }
  return Array.from(map.values());
});

function statusText(s) {
  return { ACTIVE: '可用', USED_UP: '已用完', EXPIRED: '已过期' }[s] || s;
}
function pad(n) { return n < 10 ? '0' + n : '' + n; }
function fmtDate(ms) {
  if (!ms) return '—';
  const d = new Date(ms);
  return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())}`;
}

function goDetail(c) {
  uni.navigateTo({ url: `/pages/card-package/detail?id=${c.id}` });
}

async function load() {
  loading.value = true;
  try {
    const list = await listMyCards();
    cards.value = Array.isArray(list) ? list : [];
  } catch {
    cards.value = [];
  } finally {
    loading.value = false;
  }
}
onShow(load);
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.page { min-height: 100vh; background: $bg-2; padding-bottom: 30px; }
.loading { padding: 40px; text-align: center; color: $t4; }
.wrap { padding: 12px 14px 0; }

.shop-group { margin-bottom: 18px; }
.shop-head { display: flex; align-items: center; gap: 9px; margin-bottom: 10px; padding: 0 2px; }
.shop-logo {
  width: 26px; height: 26px; border-radius: 8px; flex: none;
  background: linear-gradient(135deg, #FFE8C9, $gold); color: #5a3a10;
  display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 800;
}
.shop-name { flex: 1; font-size: 14px; font-weight: 800; color: $t1; }
.shop-cnt { font-size: 11px; color: $t4; }

.card {
  position: relative; display: flex; align-items: center; justify-content: space-between;
  background: linear-gradient(135deg, #2C1B0E, #4A2F14);
  border-radius: 14px; padding: 16px 16px; margin-bottom: 10px; overflow: hidden;
}
.card::before {
  content: ''; position: absolute; right: -30px; top: -30px; width: 120px; height: 120px;
  border-radius: 50%; background: radial-gradient(circle, rgba(212,146,10,.3), transparent 65%);
}
.card.st-USED_UP, .card.st-EXPIRED { background: linear-gradient(135deg, #3a3a3a, #555); }
.card-l { position: relative; z-index: 1; flex: 1; min-width: 0; }
.card-name-row { display: flex; align-items: center; gap: 8px; }
.card-name { font-size: 17px; font-weight: 800; color: #fff; }
.badge { font-size: 10px; font-weight: 700; padding: 1px 7px; border-radius: 999px; }
.badge.b-ACTIVE { color: #18130E; background: $gold-l; }
.badge.b-USED_UP, .badge.b-EXPIRED { color: #fff; background: rgba(255,255,255,.25); }
.card-meta { margin-top: 7px; display: flex; align-items: center; gap: 6px; }
.card-meta .m { font-size: 12px; color: rgba(255,255,255,.8); }
.card-meta .dot { color: rgba(255,255,255,.4); }
.card-r { position: relative; z-index: 1; display: flex; align-items: center; gap: 2px; flex: none; }
.card-r .show { font-size: 13px; font-weight: 700; color: $gold-l; }
.card-r .arrow { font-size: 17px; color: rgba(255,255,255,.5); }

.foot-tip { padding: 14px 22px 0; font-size: 11px; color: $t4; line-height: 1.6; }
.bottom-pad { height: 24px; }
</style>
