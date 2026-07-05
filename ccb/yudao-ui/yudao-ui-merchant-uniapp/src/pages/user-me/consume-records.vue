<template>
  <view class="page">
    <view class="topbar safe-top">
      <text class="back" @click="goBack">‹</text>
      <text class="title">消费积分明细</text>
      <text class="right"></text>
    </view>

    <view v-if="myShops.length" class="hero" :style="heroStyle">
      <view class="label">{{ currentShop?.shopName || '请选店铺' }} · 消费积分</view>
      <view class="num">{{ Math.floor((currentShop?.points ?? 0) / 100) }}<text class="unit"> 积分</text></view>
      <view class="hint">💡 1 积分 = 1 元 · 下单时可直接抵扣订单金额</view>
    </view>

    <view v-if="myShops.length" class="cat-tab">
      <text
        v-for="s in myShops"
        :key="s.tenantId"
        :class="['it', currentTenantId === s.tenantId ? 'active' : '']"
        @click="switchShop(s.tenantId)"
      >{{ shortName(s.shopName) }}</text>
    </view>

    <view class="section-title">
      <text>积分流水</text>
    </view>

    <view v-if="loading && !records.length" class="empty-tip">加载中...</view>
    <view v-else-if="!records.length" class="empty-state">
      <view class="empty-emoji">📊</view>
      <view class="empty-title">暂无消费积分流水</view>
      <view class="empty-sub">在该店购买带「下单返积分」商品后会自动产生记录</view>
    </view>

    <view v-else class="card">
      <view
        v-for="r in records"
        :key="r.id"
        :class="['flow-row', r.amount >= 0 ? 'plus' : 'minus']"
      >
        <view class="icon">{{ flowIcon(r) }}</view>
        <view class="body">
          <view class="name">{{ flowName(r) }}</view>
          <view class="meta">{{ formatTime(r.createTime) }}{{ r.remark ? ' · ' + r.remark : '' }}</view>
        </view>
        <view class="amt">{{ r.amount >= 0 ? '+' : '' }}{{ Math.floor(Math.abs(r.amount) / 100) * (r.amount >= 0 ? 1 : -1) }} 积分</view>
      </view>
    </view>
    <view class="bottom-space"></view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { request } from '../../api/request.js';

const myShops = ref([]);
const currentTenantId = ref(null);
const records = ref([]);
const loading = ref(false);

const currentShop = computed(() => myShops.value.find(s => s.tenantId === currentTenantId.value));
const heroStyle = computed(() => `background: linear-gradient(135deg, #4ade80, #16a34a);`);

function shortName(n) {
  if (!n) return '店铺';
  return n.length > 5 ? n.slice(0, 4) + '..' : n;
}
function flowIcon(r) {
  const map = {
    CONSUME: '🛒',         // 下单返
    REDEEM: '↘',           // 下单抵扣
    REDEEM_REFUND: '↩',    // 订单取消退还
    CONVERT: '↗',          // 推广积分兑入
  };
  return map[r.sourceType] || (r.amount >= 0 ? '↗' : '↘');
}
function flowName(r) {
  const map = {
    CONSUME: '下单返积分',
    REDEEM: '下单抵扣',
    REDEEM_REFUND: '订单取消退还',
    CONVERT: '从推广积分兑换',
  };
  return map[r.sourceType] || (r.title || '积分变动');
}
function formatTime(t) {
  if (!t) return '';
  const d = new Date(typeof t === 'string' ? t.replace(' ', 'T') : t);
  if (isNaN(d.getTime())) return '';
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

async function loadShops() {
  try {
    const list = await request({ url: '/app-api/merchant/mini/member-rel/my-shops-enriched' });
    myShops.value = list || [];
    if (myShops.value.length && !currentTenantId.value) {
      currentTenantId.value = myShops.value[0].tenantId;
    }
  } catch { myShops.value = []; }
}
async function loadRecords() {
  if (!currentTenantId.value) return;
  loading.value = true;
  try {
    const res = await request({
      url: '/app-api/merchant/mini/promo/consume-records?pageNo=1&pageSize=50',
      tenantId: currentTenantId.value,
    });
    records.value = res?.list || res || [];
  } catch { records.value = []; }
  finally { loading.value = false; }
}

function switchShop(tid) {
  currentTenantId.value = tid;
  loadRecords();
}
function goBack() { uni.navigateBack({ fail: () => uni.reLaunch({ url: '/pages/user-me/index' }) }); }

onMounted(async () => {
  await loadShops();
  await loadRecords();
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';
.page { min-height: 100vh; background: $bg-page; }
.safe-top { padding-top: calc(max(env(safe-area-inset-top, 0px), var(--status-bar-height, 0px)) + 16rpx); }
.topbar {
  display: flex; align-items: center; padding: 16rpx 32rpx;
  background: $bg-card; border-bottom: 1rpx solid $border-color;
}
.topbar .back { font-size: 44rpx; color: $text-primary; padding-right: 16rpx; }
.topbar .title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; color: $text-primary; }
.topbar .right { width: 60rpx; }

.hero {
  margin: 24rpx 32rpx; padding: 40rpx;
  border-radius: $radius-lg; color: #fff;
  box-shadow: 0 8rpx 32rpx rgba(22,163,74,.30);
}
.hero .label { font-size: 24rpx; opacity: .85; }
.hero .num {
  font-size: 64rpx; font-weight: 800; margin-top: 8rpx;
  font-variant-numeric: tabular-nums;
}
.hero .num .unit { font-size: 28rpx; font-weight: 600; opacity: .85; }
.hero .hint {
  margin-top: 16rpx; font-size: 22rpx; opacity: .85;
  border-top: 1rpx solid rgba(255,255,255,.20); padding-top: 16rpx;
}

.cat-tab {
  display: flex; gap: 0; background: $bg-card;
  margin: 0 32rpx; padding: 8rpx;
  border-radius: $radius-md;
  overflow-x: auto;
  white-space: nowrap;
}
.cat-tab::-webkit-scrollbar { height: 0; }
.cat-tab .it {
  flex-shrink: 0;
  padding: 16rpx 24rpx;
  font-size: 26rpx; color: $text-secondary;
  border-radius: $radius-sm;
}
.cat-tab .it.active { background: rgba(22,163,74,.12); color: #16a34a; font-weight: 600; }

.section-title {
  margin: 32rpx 32rpx 16rpx;
  font-size: 28rpx; font-weight: 700; color: $text-primary;
}

.empty-tip { text-align: center; padding: 80rpx 0; color: $text-placeholder; font-size: 26rpx; }
.empty-state { text-align: center; padding: 80rpx 60rpx; }
.empty-state .empty-emoji { font-size: 96rpx; margin-bottom: 24rpx; opacity: .5; }
.empty-state .empty-title { font-size: 32rpx; font-weight: 700; color: $text-primary; }
.empty-state .empty-sub { margin-top: 12rpx; font-size: 24rpx; color: $text-placeholder; }

.card { margin: 0 32rpx; background: $bg-card; border-radius: $radius-lg; box-shadow: 0 4rpx 16rpx rgba(15,23,42,.04); }
.flow-row {
  display: flex; align-items: center; padding: 28rpx 32rpx;
  border-bottom: 1rpx solid $border-color;
}
.flow-row:last-child { border-bottom: 0; }
.flow-row .icon {
  width: 72rpx; height: 72rpx; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 32rpx; flex-shrink: 0; margin-right: 24rpx;
  background: rgba(22,163,74,.12); color: #16a34a;
}
.flow-row.minus .icon { background: rgba(230,57,70,.12); color: $danger; }
.flow-row .body { flex: 1; min-width: 0; }
.flow-row .name { font-size: 26rpx; font-weight: 500; color: $text-primary; }
.flow-row .meta {
  margin-top: 4rpx; font-size: 22rpx; color: $text-placeholder;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.flow-row .amt {
  font-size: 32rpx; font-weight: 800;
  font-variant-numeric: tabular-nums;
  color: #16a34a;
}
.flow-row.minus .amt { color: $danger; }

.bottom-space { height: 80rpx; }
</style>
