<template>
  <view class="page">
    <view class="topbar safe-top">
      <text class="back" @click="goBack">‹</text>
      <text class="title">推广积分明细</text>
      <text class="right" @click="goConvert">兑换</text>
    </view>

    <view v-if="myShops.length" class="hero" :style="heroStyle">
      <view class="label">{{ currentShop?.shopName || '请选店铺' }} · 推广积分</view>
      <view class="num">{{ currentShop?.points || 0 }}<text class="unit"> 分</text></view>
      <view class="hint">💡 1 推广积分 = 1 消费积分（可兑换） · 满 100 分可提现到该店余额</view>
    </view>

    <!-- 店铺切换 tab -->
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
      <view class="empty-title">暂无积分流水</view>
      <view class="empty-sub">在该店消费 / 推荐朋友购买后会自动产生记录</view>
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
        <view class="amt">{{ r.amount >= 0 ? '+' : '' }}{{ r.amount }}</view>
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
const heroStyle = computed(() => {
  return `background: linear-gradient(135deg, #ff9a4a, #ff6b35);`;
});

function shortName(n) {
  if (!n) return '店铺';
  return n.length > 5 ? n.slice(0, 4) + '..' : n;
}
function flowIcon(r) {
  const map = {
    DIRECT: '↗', QUEUE: '↗', SELF_PURCHASE: '↗',
    COMMISSION: '🏆', POOL: '🎲', CONVERT: '↘',
    WITHDRAW: '↘', CONSUME: '↘',
  };
  return map[r.sourceType] || (r.amount >= 0 ? '↗' : '↘');
}
function flowName(r) {
  const map = {
    DIRECT: '直推奖', QUEUE: '队列返奖', SELF_PURCHASE: '插队自购奖',
    COMMISSION: '团队极差奖', POOL: '星级积分池中奖',
    CONVERT: '兑换为消费积分', WITHDRAW: '提现到余额', CONSUME: '消费抵扣',
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
      url: '/app-api/merchant/mini/promo/promo-records?pageNo=1&pageSize=50',
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
function goConvert() { uni.showToast({ title: '兑换页开发中', icon: 'none' }); }
function goBack() { uni.navigateBack({ fail: () => uni.reLaunch({ url: '/pages/user-me/index' }) }); }

onMounted(async () => {
  await loadShops();
  await loadRecords();
});
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

.hero {
  margin: 24rpx 32rpx; padding: 40rpx;
  border-radius: $radius-lg; color: #fff;
  box-shadow: 0 8rpx 32rpx rgba(255,107,53,.30);
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
.cat-tab .it.active { background: $brand-primary-light; color: $brand-primary; font-weight: 600; }

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
  background: rgba(16,185,129,.12); color: $success;
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
  color: $success;
}
.flow-row.minus .amt { color: $danger; }

.bottom-space { height: 80rpx; }
</style>
