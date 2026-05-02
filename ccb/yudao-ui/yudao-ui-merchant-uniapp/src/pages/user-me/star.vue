<template>
  <view class="page">
    <view class="topbar safe-top">
      <text class="back" @click="goBack">‹</text>
      <text class="title">店铺星级</text>
      <view style="width:60rpx"></view>
    </view>

    <view class="info-tip">
      <text class="b">星级 = 在该店的等级特权：</text>
      达成"直推 N 人 + 团队销 M 单"自动升星，<text class="b">终生制不降级</text>，每升一星可享对应等级的团队极差奖与积分池抽奖资格。
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

    <view v-if="loading && !current" class="empty-tip">加载中…</view>
    <view v-else-if="!myShops.length" class="empty-state">
      <view class="empty-emoji">⭐</view>
      <view class="empty-title">暂未加入任何店铺</view>
      <view class="empty-sub">扫码或链接进入第一家店铺即可建立星级账户</view>
    </view>

    <view v-else-if="current" class="container">
      <!-- 当前星级 hero -->
      <view class="hero">
        <view class="shop">{{ currentShop?.shopName || '当前店铺' }}</view>
        <view class="star-badge">
          <text v-if="current.currentStar > 0" class="num">{{ current.currentStar }}★</text>
          <text v-else class="num zero">未达星级</text>
        </view>
        <view class="hint">{{ statusHint }}</view>
      </view>

      <!-- 数据卡 -->
      <view class="grid">
        <view class="cell">
          <view class="label">直推下级</view>
          <view class="num">{{ current.directCount || 0 }}</view>
          <view class="unit">人</view>
        </view>
        <view class="cell">
          <view class="label">团队销售</view>
          <view class="num">{{ current.teamSalesCount || 0 }}</view>
          <view class="unit">单</view>
        </view>
        <view class="cell">
          <view class="label">推广积分</view>
          <view class="num">{{ current.promoPointBalance || 0 }}</view>
          <view class="unit">分</view>
        </view>
        <view class="cell">
          <view class="label">消费积分</view>
          <view class="num">{{ current.consumePointBalance || 0 }}</view>
          <view class="unit">分</view>
        </view>
      </view>

      <!-- 升星进度 -->
      <view v-if="nextRule" class="upgrade-card">
        <view class="title">距下一星：{{ (current.currentStar || 0) + 1 }}★</view>

        <view class="row">
          <view class="row-label">直推下级 {{ current.directCount || 0 }} / {{ nextRule.directCount }}</view>
          <view class="bar"><view class="fill" :style="`width:${pct(current.directCount, nextRule.directCount)}%`"></view></view>
        </view>

        <view class="row">
          <view class="row-label">团队销售 {{ current.teamSalesCount || 0 }} / {{ nextRule.teamSales }}</view>
          <view class="bar"><view class="fill" :style="`width:${pct(current.teamSalesCount, nextRule.teamSales)}%`"></view></view>
        </view>

        <view class="row-tip">📌 升星条件 = 两项 <text class="b">同时满足</text>，达成后自动升级</view>
      </view>
      <view v-else class="upgrade-card max">
        <view class="title">🏆 已达本店最高星级</view>
        <view class="row-tip">享受最高等级的极差奖与积分池抽奖权重</view>
      </view>

      <!-- 完整星级规则 -->
      <view v-if="rules.length" class="rules-card">
        <view class="rules-title">本店升星规则</view>
        <view v-for="(r, i) in rules" :key="i" :class="['rule-row', (current.currentStar || 0) >= (i+1) ? 'achieved' : '']">
          <view class="rule-star">
            <text>{{ i + 1 }}★</text>
            <text v-if="(current.currentStar || 0) >= (i+1)" class="check">✓</text>
          </view>
          <view class="rule-cond">
            直推 ≥ <text class="b">{{ r.directCount }}</text> 人 + 团队销 ≥ <text class="b">{{ r.teamSales }}</text> 单
          </view>
        </view>
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
const current = ref(null);
const rules = ref([]);
const loading = ref(false);

const currentShop = computed(() => myShops.value.find(s => s.tenantId === currentTenantId.value));
const nextRule = computed(() => {
  const star = current.value?.currentStar || 0;
  if (star >= rules.value.length) return null;
  return rules.value[star];
});
const statusHint = computed(() => {
  const s = current.value?.currentStar || 0;
  if (s === 0) return '完成 1★ 升星条件即可解锁极差奖资格';
  if (s >= rules.value.length) return '已达最高星级，享受满级权益';
  return `已是 ${s} 星，继续推荐下一星只差一步`;
});

function shortName(n) {
  if (!n) return '店铺';
  return n.length > 5 ? n.slice(0, 4) + '..' : n;
}
function pct(cur, max) {
  if (!max || max <= 0) return 100;
  const v = Math.min(100, Math.round(((cur || 0) / max) * 100));
  return v;
}

async function loadShops() {
  try {
    const list = await request({ url: '/app-api/merchant/mini/member-rel/my-shops-enriched' });
    myShops.value = list || [];
    if (!currentTenantId.value && myShops.value.length) {
      currentTenantId.value = myShops.value[0].tenantId;
    }
  } catch { myShops.value = []; }
}
async function loadConfig() {
  if (!currentTenantId.value) return;
  try {
    const conf = await request({
      url: '/app-api/merchant/mini/promo/config',
      tenantId: currentTenantId.value,
    });
    if (conf?.starUpgradeRules) {
      try { rules.value = JSON.parse(conf.starUpgradeRules) || []; }
      catch { rules.value = []; }
    } else {
      rules.value = [];
    }
  } catch { rules.value = []; }
}
async function loadAccount() {
  if (!currentTenantId.value) return;
  loading.value = true;
  try {
    const acc = await request({
      url: '/app-api/merchant/mini/promo/account',
      tenantId: currentTenantId.value,
    });
    current.value = acc || { currentStar: 0, directCount: 0, teamSalesCount: 0 };
  } catch {
    current.value = { currentStar: 0, directCount: 0, teamSalesCount: 0 };
  } finally {
    loading.value = false;
  }
}

function switchShop(tid) {
  if (currentTenantId.value === tid) return;
  currentTenantId.value = tid;
  loadConfig();
  loadAccount();
}
function goBack() { uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/user-me/index' }) }); }

onMounted(async () => {
  await loadShops();
  await Promise.all([loadConfig(), loadAccount()]);
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';
.page { min-height: 100vh; background: $bg-page; }
.safe-top { padding-top: calc(env(safe-area-inset-top) + 16rpx); }

.topbar { display: flex; align-items: center; padding: 16rpx 32rpx; background: $bg-card; border-bottom: 1rpx solid $border-color; }
.topbar .back { font-size: 44rpx; color: $text-primary; padding-right: 16rpx; }
.topbar .title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; color: $text-primary; }

.info-tip {
  margin: 24rpx 32rpx; padding: 24rpx 28rpx;
  background: linear-gradient(135deg, #fff5ef, #ffe1c8);
  border-left: 6rpx solid $brand-primary;
  border-radius: $radius-md;
  font-size: 24rpx; color: $text-primary; line-height: 1.6;
}
.info-tip .b { font-weight: 700; color: $brand-primary; }

.cat-tab { display: flex; background: $bg-card; margin: 0 32rpx; padding: 8rpx; border-radius: $radius-md; overflow-x: auto; white-space: nowrap; }
.cat-tab::-webkit-scrollbar { height: 0; }
.cat-tab .it { flex-shrink: 0; padding: 16rpx 24rpx; font-size: 26rpx; color: $text-secondary; border-radius: $radius-sm; }
.cat-tab .it.active { background: $brand-primary-light; color: $brand-primary; font-weight: 600; }

.empty-tip { text-align: center; padding: 80rpx 0; color: $text-placeholder; font-size: 26rpx; }
.empty-state { text-align: center; padding: 120rpx 60rpx; }
.empty-state .empty-emoji { font-size: 96rpx; margin-bottom: 24rpx; opacity: .5; }
.empty-state .empty-title { font-size: 32rpx; font-weight: 700; color: $text-primary; }
.empty-state .empty-sub { margin-top: 12rpx; font-size: 24rpx; color: $text-placeholder; }

.container { padding-bottom: 40rpx; }

.hero {
  margin: 24rpx 32rpx; padding: 40rpx;
  background: linear-gradient(135deg, #ff9a4a, #ff6b35);
  border-radius: $radius-lg; color: #fff;
  box-shadow: 0 8rpx 32rpx rgba(255,107,53,.30);
  text-align: center;
}
.hero .shop { font-size: 26rpx; opacity: .85; }
.hero .star-badge { margin: 16rpx 0 12rpx; }
.hero .star-badge .num { font-size: 88rpx; font-weight: 800; }
.hero .star-badge .num.zero { font-size: 44rpx; opacity: .85; }
.hero .hint { font-size: 22rpx; opacity: .85; }

.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16rpx; margin: 0 32rpx; }
.grid .cell {
  background: $bg-card; border-radius: $radius-md; padding: 24rpx 28rpx;
  box-shadow: 0 4rpx 12rpx rgba(15,23,42,.04);
}
.grid .cell .label { font-size: 22rpx; color: $text-placeholder; }
.grid .cell .num { font-size: 40rpx; font-weight: 800; color: $text-primary; font-variant-numeric: tabular-nums; margin: 4rpx 0; }
.grid .cell .unit { font-size: 22rpx; color: $text-placeholder; }

.upgrade-card {
  margin: 24rpx 32rpx; padding: 32rpx;
  background: $bg-card; border-radius: $radius-lg;
  box-shadow: 0 4rpx 16rpx rgba(15,23,42,.04);
}
.upgrade-card.max { text-align: center; padding: 48rpx 32rpx; background: linear-gradient(135deg, #fff8e1, #ffe7a3); }
.upgrade-card .title { font-size: 30rpx; font-weight: 700; color: $brand-primary; margin-bottom: 24rpx; }
.upgrade-card .row { margin-bottom: 16rpx; }
.upgrade-card .row-label { font-size: 24rpx; color: $text-secondary; margin-bottom: 8rpx; font-variant-numeric: tabular-nums; }
.upgrade-card .bar { height: 16rpx; border-radius: 8rpx; background: $border-color; overflow: hidden; }
.upgrade-card .bar .fill { height: 100%; background: $brand-primary; border-radius: 8rpx; transition: width .35s ease-out; }
.upgrade-card .row-tip { margin-top: 16rpx; font-size: 22rpx; color: $text-placeholder; }
.upgrade-card .row-tip .b { color: $brand-primary; font-weight: 700; }

.rules-card { margin: 24rpx 32rpx; padding: 24rpx 32rpx; background: $bg-card; border-radius: $radius-md; box-shadow: 0 4rpx 12rpx rgba(15,23,42,.03); }
.rules-card .rules-title { font-size: 26rpx; font-weight: 700; color: $text-primary; margin-bottom: 16rpx; }
.rule-row { display: flex; align-items: center; padding: 16rpx 0; border-bottom: 1rpx dashed $border-color; }
.rule-row:last-child { border-bottom: 0; }
.rule-row .rule-star {
  width: 88rpx; flex-shrink: 0;
  font-size: 28rpx; font-weight: 800; color: $text-placeholder;
}
.rule-row.achieved .rule-star { color: $brand-primary; }
.rule-row .rule-star .check { color: $success; margin-left: 6rpx; }
.rule-row .rule-cond { flex: 1; font-size: 24rpx; color: $text-secondary; }
.rule-row .rule-cond .b { color: $brand-primary; font-weight: 700; }
.rule-row.achieved .rule-cond { color: $text-primary; }

.bottom-space { height: 80rpx; }
</style>
