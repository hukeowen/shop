<template>
  <view class="page">
    <view class="topbar safe-top">
      <text class="back" @click="goBack">‹</text>
      <text class="title">购物车（{{ totalCount }}）</text>
      <text class="right" @click="toggleEdit">{{ editMode ? '完成' : '编辑' }}</text>
    </view>

    <view class="tip-bar">
      ⚠ 一次只能在<text class="b">同一家店</text>结算（订单按店独立 + v6 营销 / 余额 / 积分都按店隔离）。
    </view>

    <view v-if="loading && !items.length" class="empty-tip">加载中...</view>
    <view v-else-if="!items.length" class="empty-state">
      <view class="empty-emoji">🛒</view>
      <view class="empty-title">购物车是空的</view>
      <view class="empty-sub">逛逛附近店铺，看到喜欢的就加进来</view>
      <view class="empty-cta" @click="goHome">去逛附近店铺 ›</view>
    </view>

    <view v-else>
      <!-- 当前结算店铺组（高亮） -->
      <view v-if="currentGroup" class="cart-shop-grp current">
        <view class="gh">
          <view class="check on">✓</view>
          <view class="pic" :style="picStyle(currentGroup)">{{ initial(currentGroup) }}</view>
          <text class="name">{{ currentGroup.shopName }}</text>
          <text class="tag">本次结算</text>
        </view>
        <view
          v-for="item in currentGroup.items"
          :key="item.id"
          class="cart-row"
        >
          <view class="check on">✓</view>
          <view class="pic-item" :style="itemPicStyle(item)">{{ pickEmoji(item) }}</view>
          <view class="info">
            <view class="iname">{{ item.spuName || item.name || '商品' }}</view>
            <view class="spec" v-if="item.skuName || itemSpec(item)">{{ item.skuName || itemSpec(item) }}</view>
            <view class="row">
              <view class="price">¥{{ fen2yuan(item.price) }}</view>
              <view class="qty">
                <view class="btn" @click="changeQty(item, -1)">−</view>
                <text class="num">{{ item.count }}</text>
                <view class="btn" @click="changeQty(item, +1)">+</view>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 其他店铺（灰色，点击切换为当前结算店铺） -->
      <view v-if="otherGroups.length" class="other-shops-title">其他店铺（点击切换）</view>
      <view
        v-for="grp in otherGroups"
        :key="grp.tenantId"
        class="cart-shop-grp other"
        @click="switchCurrent(grp.tenantId)"
      >
        <view class="gh">
          <view class="check off">○</view>
          <view class="pic" :style="picStyle(grp)">{{ initial(grp) }}</view>
          <text class="name">{{ grp.shopName }}</text>
          <text class="tag-other">点此切换 ›</text>
        </view>
        <view
          v-for="item in grp.items.slice(0, 2)"
          :key="item.id"
          class="cart-row off"
        >
          <view class="pic-item" :style="itemPicStyle(item)">{{ pickEmoji(item) }}</view>
          <view class="info">
            <view class="iname">{{ item.spuName || item.name }}</view>
            <view class="row">
              <view class="price">¥{{ fen2yuan(item.price) }}</view>
              <view class="off-qty">x {{ item.count }}</view>
            </view>
          </view>
        </view>
        <view v-if="grp.items.length > 2" class="more-items">还有 {{ grp.items.length - 2 }} 件商品...</view>
      </view>

      <view class="bottom-space"></view>
    </view>

    <view v-if="items.length" class="cart-bottom">
      <view class="check-all on">✓</view>
      <text class="label-all">全选本店</text>
      <view class="total">
        <text class="label">{{ currentGroup?.shopName || '' }}</text>
        <text class="price">¥{{ fen2yuan(currentTotal) }}</text>
      </view>
      <view class="checkout-btn" @click="goCheckout">结算（{{ currentGroup?.items?.length || 0 }}）</view>
    </view>

    <RoleTabBar current="/pages/cart/index" force-role="member" />
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { request } from '../../api/request.js';
import { fen2yuan } from '../../utils/format.js';

const items = ref([]); // 所有店铺购物车项扁平
const loading = ref(false);
const editMode = ref(false);
const currentTenantId = ref(null);

// 按 tenantId 分组（前端聚合，后端 cart/list 返带 tenantId）
const groups = computed(() => {
  const map = new Map();
  for (const it of items.value) {
    const tid = it.tenantId || 0;
    if (!map.has(tid)) {
      map.set(tid, { tenantId: tid, shopName: it.shopName || `店铺 #${tid}`, items: [] });
    }
    map.get(tid).items.push(it);
  }
  return Array.from(map.values());
});
const currentGroup = computed(() => groups.value.find(g => g.tenantId === currentTenantId.value) || groups.value[0]);
const otherGroups = computed(() => groups.value.filter(g => g.tenantId !== currentGroup.value?.tenantId));
const totalCount = computed(() => items.value.reduce((s, i) => s + (i.count || 0), 0));
const currentTotal = computed(() => (currentGroup.value?.items || []).reduce((s, i) => s + (i.count || 0) * (i.price || 0), 0));

const initial = (g) => (g.shopName || '店')[0];
const picStyle = (g) => {
  const palette = ['#ffd1ba,#ff6b35', '#c9e0ff,#6196f0', '#d3f4d3,#4cb84c', '#ffd0dc,#ee5a8b'];
  const idx = (Number(g.tenantId) || 0) % palette.length;
  return `background: linear-gradient(135deg, ${palette[idx]});`;
};
const itemPicStyle = (it) => {
  const palette = ['#ffe1c8,#ffae74', '#d6e9ff,#80b3ff', '#d8f5d6,#6fcf6f', '#ffd6e0,#ff8aa7'];
  const idx = (Number(it.skuId) || 0) % palette.length;
  return `background: linear-gradient(135deg, ${palette[idx]});`;
};
function pickEmoji(it) {
  const n = it.spuName || it.name || '';
  if (/(地瓜|薯)/.test(n)) return '🍠';
  if (/(玉米)/.test(n)) return '🌽';
  if (/(茶|奶茶)/.test(n)) return '🍵';
  if (/(果|莓|葡萄)/.test(n)) return '🍇';
  if (/(肉|串|烧)/.test(n)) return '🍖';
  if (/(咖啡)/.test(n)) return '☕';
  return '🛍';
}
function itemSpec(it) {
  if (!it.properties || !it.properties.length) return '';
  return it.properties.map(p => p.valueName).join(' / ');
}

async function load() {
  loading.value = true;
  try {
    // 跨店购物车：调 cart/list 不带 tenantId，希望后端返扁平含 tenantId；如果后端只能按 tenantId 查，
    // 前端循环各店调用合并
    const res = await request({ url: '/app-api/trade/cart/list' });
    let list = (res && res.validList) || (res && res.list) || (Array.isArray(res) ? res : []);
    // 兼容：嵌套 spu 把 tenantId 挂上去
    list = list.map(it => ({
      ...it,
      tenantId: it.tenantId || it.spu?.tenantId,
      price: it.price || it.spu?.price || (it.sku && it.sku.price) || 0,
      shopName: it.shopName || it.spu?.shopName || it.spu?.tenantName,
    }));
    items.value = list;
    // 默认选中"最近一次访问的店铺"或第一组
    const lastTid = uni.getStorageSync('lastShopTenantId');
    if (lastTid && groups.value.find(g => g.tenantId === Number(lastTid))) {
      currentTenantId.value = Number(lastTid);
    } else {
      currentTenantId.value = groups.value[0]?.tenantId || null;
    }
    // 异步拉每个 tenantId 的店铺名（不阻塞主路径，拿到后 reactive 更新）
    loadShopNames();
  } catch {
    items.value = [];
  } finally {
    loading.value = false;
  }
}

// 拉每个 tenantId 对应的 shopName（去重 + 并发）
async function loadShopNames() {
  const tids = Array.from(new Set(items.value.map(it => it.tenantId).filter(Boolean)));
  if (!tids.length) return;
  const results = await Promise.all(
    tids.map(tid =>
      request({ url: `/app-api/merchant/shop/public/info?tenantId=${tid}` })
        .then(s => ({ tid, name: s?.shopName || `店铺 #${tid}` }))
        .catch(() => ({ tid, name: `店铺 #${tid}` }))
    )
  );
  const map = {};
  results.forEach(({ tid, name }) => { map[tid] = name; });
  // 反写到 items（响应式更新 groups computed）
  items.value = items.value.map(it => ({ ...it, shopName: map[it.tenantId] || it.shopName }));
}

async function changeQty(item, delta) {
  const next = (item.count || 0) + delta;
  if (next <= 0) {
    uni.showModal({
      title: '移除商品',
      content: '确定要从购物车中移除吗？',
      success: async (r) => {
        if (!r.confirm) return;
        try {
          // 用户购物车按 token tenant 走，不传商户 tenantId 头
          await request({
            url: '/app-api/trade/cart/delete',
            method: 'DELETE',
            data: { ids: item.id },
          });
          load();
        } catch { uni.showToast({ title: '操作失败', icon: 'none' }); }
      },
    });
    return;
  }
  try {
    await request({
      url: '/app-api/trade/cart/update-count',
      method: 'PUT',
      data: { id: item.id, count: next },
    });
    item.count = next;
  } catch { uni.showToast({ title: '操作失败', icon: 'none' }); }
}

function switchCurrent(tid) {
  currentTenantId.value = tid;
  uni.setStorageSync('lastShopTenantId', tid);
}

function goCheckout() {
  if (!currentGroup.value) return;
  const cartIds = currentGroup.value.items.map(i => i.id).join(',');
  uni.navigateTo({
    url: `/pages/checkout/index?tenantId=${currentGroup.value.tenantId}&cartIds=${cartIds}`,
  });
}

function toggleEdit() { editMode.value = !editMode.value; }
function goBack() { uni.navigateBack({ fail: () => goHome() }); }
function goHome() { uni.reLaunch({ url: '/pages/user-home/index' }); }

onMounted(load);
onShow(load);
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  min-height: 100vh; background: $bg-page; padding-bottom: 200rpx;
}
.safe-top { padding-top: calc(env(safe-area-inset-top) + 16rpx); }
.safe-bottom { padding-bottom: env(safe-area-inset-bottom); }

.topbar {
  display: flex; align-items: center; padding: 16rpx 32rpx;
  background: $bg-card; border-bottom: 1rpx solid $border-color;
}
.topbar .back { font-size: 44rpx; color: $text-primary; padding-right: 16rpx; }
.topbar .title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; color: $text-primary; }
.topbar .right { font-size: 26rpx; color: $brand-primary; padding-left: 16rpx; }

.tip-bar {
  margin: 24rpx 32rpx 16rpx; padding: 20rpx 28rpx;
  background: #FFF8EF; border-radius: $radius-md;
  border-left: 6rpx solid $warning;
  font-size: 24rpx; color: #B26A00; line-height: 1.5;
}
.tip-bar .b { font-weight: 700; }

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

.cart-shop-grp {
  margin: 24rpx 32rpx; background: $bg-card;
  border-radius: $radius-lg; overflow: hidden;
  box-shadow: 0 4rpx 16rpx rgba(15,23,42,.04);
}
.cart-shop-grp.current { border: 4rpx solid $brand-primary; }
.cart-shop-grp.other { opacity: .65; }
.gh {
  padding: 24rpx 32rpx; display: flex; align-items: center; gap: 16rpx;
  border-bottom: 1rpx solid $border-color;
}
.check {
  width: 36rpx; height: 36rpx; border-radius: 50%;
  border: 4rpx solid $brand-primary;
  font-size: 24rpx; line-height: 28rpx; text-align: center;
  font-weight: 700; color: $brand-primary;
}
.check.on { background: $brand-primary; color: #fff; }
.check.off { border-color: $text-placeholder; color: $text-placeholder; }
.gh .pic {
  width: 48rpx; height: 48rpx; border-radius: $radius-sm;
  color: #fff; font-size: 22rpx; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.gh .name { flex: 1; font-size: 28rpx; font-weight: 700; color: $text-primary; }
.gh .tag {
  background: $brand-primary; color: #fff;
  font-size: 20rpx; padding: 4rpx 16rpx; border-radius: 999rpx;
}
.gh .tag-other {
  background: $bg-page; color: $text-placeholder;
  font-size: 20rpx; padding: 4rpx 16rpx; border-radius: 999rpx;
}

.cart-row {
  display: flex; gap: 20rpx; padding: 24rpx 32rpx;
  border-bottom: 1rpx solid $border-color;
}
.cart-row:last-child { border-bottom: 0; }
.cart-row .pic-item {
  width: 128rpx; height: 128rpx; border-radius: $radius-md;
  color: #fff; font-size: 56rpx;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.cart-row .info { flex: 1; min-width: 0; }
.cart-row .iname { font-size: 26rpx; color: $text-primary; font-weight: 500; }
.cart-row .spec { margin-top: 8rpx; font-size: 22rpx; color: $text-placeholder; }
.cart-row .row { margin-top: 16rpx; display: flex; align-items: center; justify-content: space-between; }
.cart-row .price {
  color: $brand-primary; font-weight: 800; font-size: 30rpx;
  font-variant-numeric: tabular-nums;
}
.cart-row .qty {
  display: flex; align-items: center; gap: 16rpx;
  background: $bg-page; border-radius: 999rpx;
  padding: 4rpx;
}
.cart-row .qty .btn {
  width: 44rpx; height: 44rpx; border-radius: 50%;
  background: $bg-card; color: $text-secondary; font-size: 28rpx;
  line-height: 44rpx; text-align: center;
}
.cart-row .qty .num { font-size: 26rpx; font-weight: 700; min-width: 36rpx; text-align: center; }
.cart-row .off-qty { font-size: 22rpx; color: $text-placeholder; }

.other-shops-title {
  margin: 24rpx 32rpx 8rpx;
  font-size: 22rpx; color: $text-placeholder;
}
.more-items {
  text-align: center; padding: 16rpx 0;
  font-size: 22rpx; color: $text-placeholder;
}

.bottom-space { height: 40rpx; }

.cart-bottom {
  position: fixed; bottom: 0; left: 0; right: 0;
  background: $bg-card; padding: 24rpx 32rpx;
  padding-bottom: calc(env(safe-area-inset-bottom) + 24rpx);
  box-shadow: 0 -4rpx 32rpx rgba(0,0,0,.06);
  display: flex; align-items: center; gap: 20rpx; z-index: 50;
}
.check-all { flex-shrink: 0; }
.label-all { font-size: 24rpx; color: $text-secondary; flex-shrink: 0; }
.cart-bottom .total {
  flex: 1; text-align: right;
}
.cart-bottom .total .label { font-size: 22rpx; color: $text-placeholder; margin-right: 8rpx; }
.cart-bottom .total .price {
  font-size: 40rpx; font-weight: 800; color: $brand-primary;
  font-variant-numeric: tabular-nums;
}
.cart-bottom .checkout-btn {
  background: $brand-primary; color: #fff;
  height: 84rpx; padding: 0 44rpx;
  border-radius: 42rpx; line-height: 84rpx;
  font-size: 28rpx; font-weight: 700;
}
</style>
