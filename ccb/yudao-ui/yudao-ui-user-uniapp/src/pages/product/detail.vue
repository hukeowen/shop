<template>
  <view class="page">
    <view v-if="loading" class="empty-tip">加载中...</view>
    <view v-else-if="!product" class="empty-tip">商品不存在</view>
    <view v-else>
      <!-- 主图 -->
      <view class="pd-pic">
        <view class="nav-back" @click="goBack">‹</view>
        <view class="nav-share" @click="onShare">↗</view>
        <image v-if="product.picUrl" :src="product.picUrl" class="main-pic" mode="aspectFill" />
        <view v-else class="main-pic-emoji">{{ pickEmoji() }}</view>
        <view v-if="product.picUrls && product.picUrls.length > 1" class="pager">1 / {{ product.picUrls.length }}</view>
      </view>

      <!-- 信息卡 -->
      <view class="pd-info">
        <view class="price-row">
          <text class="price"><text class="cny">¥</text>{{ fen2yuan(currentPrice) }}</text>
          <text v-if="originalPrice && originalPrice > currentPrice" class="original">¥{{ fen2yuan(originalPrice) }}</text>
          <text v-if="promoConfig?.tuijianEnabled" class="promo-tag">邀 {{ promoConfig.tuijianN }} 累积</text>
        </view>
        <view class="name">{{ product.name }}</view>
        <view class="intro" v-if="product.introduction">{{ product.introduction }}</view>
        <view class="meta">
          <text>已售 <text class="b">{{ product.salesCount || 0 }}</text></text>
          <text>评分 <text class="b">{{ formatRating(product.rating) }}</text></text>
          <text>库存 <text class="b">{{ product.stock || '充足' }}</text></text>
        </view>
      </view>

      <!-- 营销活动 高亮（V044 合规话术）-->
      <view class="promo-banner" v-if="promoConfig?.tuijianEnabled">
        <view class="ic">🎯</view>
        <view class="txt">
          推荐 {{ promoConfig.tuijianN }} 位朋友首单 → 累计获得 <text class="b">本品价对应营销积分</text>
          <view class="sub">商户独立营销活动 · 积分按 {{ formatRatios(promoConfig) }} 分配 · 积分仅供本店消费抵扣 / 申请商户兑付</view>
        </view>
      </view>

      <!-- 规格选择 -->
      <view v-if="product.skus && product.skus.length > 1" class="pd-section">
        <view class="hdr"><text>已选规格</text><text class="t-brand">{{ selectedSkuName }} ›</text></view>
        <view class="sku-list">
          <text
            v-for="sku in product.skus"
            :key="sku.id"
            :class="['sku-pill', selectedSkuId === sku.id ? 'active' : '']"
            @click="selectSku(sku)"
          >{{ skuLabel(sku) }}</text>
        </view>
      </view>

      <!-- 商品介绍 -->
      <view class="pd-section" v-if="product.description">
        <view class="hdr"><text>商品介绍</text></view>
        <view class="body">{{ product.description }}</view>
      </view>

      <!-- v8 本期中奖名单 + 池余额 -->
      <view v-if="poolBalance > 0 || latestWinners.length > 0" class="pd-section pool-section">
        <view class="hdr">
          <text>奖池公示</text>
          <text class="t-brand" @click="goPoolPublic">查看全部 ›</text>
        </view>
        <view class="pool-strip">
          <text class="pool-bal">当前奖池 <text class="b">¥{{ fen2yuan(poolBalance) }}</text></text>
          <text class="pool-out" v-if="poolTotalOut > 0">已发 ¥{{ fen2yuan(poolTotalOut) }}</text>
        </view>
        <view v-if="latestWinners.length === 0" class="pool-empty">暂无中奖记录</view>
        <view v-else class="winners-list">
          <view v-for="(w, idx) in latestWinners.slice(0, 6)" :key="w.id" class="winner-row" :class="{ self: w.isSelf }">
            <text class="rank">{{ idx + 1 }}</text>
            <image v-if="w.avatar" :src="w.avatar" class="ava" mode="aspectFill" />
            <view v-else class="ava default-ava">{{ (w.maskedNickname || '?').charAt(0) }}</view>
            <view class="info">
              <view class="r1">
                <text class="nk">{{ w.maskedNickname }}</text>
                <text v-if="w.isSelf" class="self-tag">我</text>
                <text class="star-tag">{{ w.star }}星</text>
              </view>
              <text class="r2">{{ w.mode === 'LOTTERY' ? '抽中' : '均分' }}</text>
            </view>
            <text class="amt">+¥{{ fen2yuan(w.amount) }}</text>
          </view>
        </view>
      </view>

      <view class="bottom-space"></view>
    </view>

    <!-- 底部操作栏 -->
    <view v-if="product" class="pd-bottom safe-bottom">
      <view class="ic-btn" @click="goShop">
        <text class="ic">🏪</text>
        <text class="lbl">店铺</text>
      </view>
      <view class="ic-btn" @click="goCart">
        <text class="ic">🛒</text>
        <text class="lbl">购物车</text>
        <text v-if="cartCount" class="badge">{{ cartCount }}</text>
      </view>
      <view class="add-cart" @click="addToCart">加入购物车</view>
      <view class="buy-now" @click="buyNow">立即购买</view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { request } from '@/utils/request.js';
import { fen2yuan } from '@/utils/format.js';

const spuId = ref(null);
const tenantId = ref(null);
const product = ref(null);
const selectedSkuId = ref(null);
const promoConfig = ref(null);
const cartCount = ref(0);
const loading = ref(false);

// v8 奖池公示（最近一次中奖名单 + 池余额）
const poolBalance = ref(0);
const poolTotalOut = ref(0);
const latestWinners = ref([]);

const selectedSku = computed(() => {
  if (!product.value || !product.value.skus) return null;
  return product.value.skus.find(s => s.id === selectedSkuId.value) || null;
});
const currentPrice = computed(() => selectedSku.value?.price ?? product.value?.price ?? 0);
const originalPrice = computed(() => selectedSku.value?.marketPrice ?? product.value?.marketPrice ?? 0);
const selectedSkuName = computed(() => skuLabel(selectedSku.value));

function skuLabel(sku) {
  if (!sku) return '';
  return sku.name || sku.properties?.map(p => p.valueName).join('/') || `规格${sku.id}`;
}
function formatRating(r) { return r == null ? '5.0' : Number(r).toFixed(1); }
function pickEmoji() {
  const n = product.value?.name || '';
  if (/(地瓜|薯)/.test(n)) return '🍠';
  if (/(玉米)/.test(n)) return '🌽';
  if (/(茶|奶茶)/.test(n)) return '🍵';
  if (/(果|莓|葡萄)/.test(n)) return '🍇';
  if (/(肉|串|烧)/.test(n)) return '🍖';
  if (/(咖啡)/.test(n)) return '☕';
  return '🛍';
}
function formatRatios(cfg) {
  try {
    const arr = JSON.parse(cfg.tuijianRatios || '[]');
    return arr.join('% / ') + '%';
  } catch { return '比例配置中'; }
}

async function loadProduct() {
  if (!spuId.value) return;
  loading.value = true;
  try {
    // 不传 header tenantId（与用户 token tenant 冲突会被 TenantSecurityWebFilter 拦 401）；
    // mall spu 主键全局唯一，不需要 tenant 参数
    product.value = await request({
      url: `/app-api/product/spu/get-detail?id=${spuId.value}`,
    });
    if (product.value?.skus?.length) {
      selectedSkuId.value = product.value.skus[0].id;
    }
    // 兜底：URL 未传 tenantId 时（如首页搜索点进来），用 spu.tenantId
    if (!tenantId.value && product.value?.tenantId) {
      tenantId.value = product.value.tenantId;
      // 拉到 tenantId 后再补两个依赖 tenantId 的请求
      loadPromoConfig();
    }
  } catch { product.value = null; }
  finally { loading.value = false; }
}

async function loadPromoConfig() {
  if (!spuId.value || !tenantId.value) return;
  try {
    // promo product-config 已 @TenantIgnore 按 spuId 查；不传 header 避免越权
    promoConfig.value = await request({
      url: `/app-api/merchant/mini/promo/product-config?spuId=${spuId.value}`,
    });
  } catch { promoConfig.value = null; }
}

// v8 加载奖池公示数据（池余额 + 最近一次中奖名单）
async function loadPoolPublic() {
  if (!spuId.value) return;
  try {
    const [bal, lw] = await Promise.all([
      request({ url: `/app-api/merchant/promo/pool/balance?spuId=${spuId.value}` }),
      request({ url: `/app-api/merchant/promo/pool/latest-payouts?spuId=${spuId.value}&limit=6` }),
    ]);
    if (bal) {
      poolBalance.value = bal.poolBalance || 0;
      poolTotalOut.value = bal.totalOut || 0;
    }
    latestWinners.value = Array.isArray(lw) ? lw : [];
  } catch {
    poolBalance.value = 0;
    latestWinners.value = [];
  }
}

function goPoolPublic() {
  // 用 user uniapp 现有的中奖榜代替老 merchant 的奖池公示页
  uni.navigateTo({ url: `/pages/winners/index?tenantId=${tenantId.value}` });
}

async function loadCartCount() {
  try {
    // 购物车按用户自身 token tenant 走，不传商户 tenantId 头
    const r = await request({ url: '/app-api/trade/cart/get-count' });
    cartCount.value = typeof r === 'number' ? r : (r?.count || 0);
  } catch { cartCount.value = 0; }
}

function selectSku(sku) {
  selectedSkuId.value = sku.id;
}

async function addToCart() {
  if (!selectedSku.value) {
    uni.showToast({ title: '请先选择规格', icon: 'none' });
    return;
  }
  try {
    // 加购按用户 token tenant 走，不传商户 tenantId 头
    await request({
      url: '/app-api/trade/cart/add',
      method: 'POST',
      data: { skuId: selectedSkuId.value, count: 1 },
    });
    uni.showToast({ title: '已加入购物车', icon: 'success' });
    loadCartCount();
  } catch (e) {
    uni.showToast({ title: e?.message || '加购失败', icon: 'none' });
  }
}

function buyNow() {
  if (!selectedSku.value) { uni.showToast({ title: '请先选择规格', icon: 'none' }); return; }
  // 多带 spuId 让 checkout 立即购买路径能拉到 sku.price 真实价格
  uni.navigateTo({
    url: `/pages/checkout/index?tenantId=${tenantId.value}&spuId=${spuId.value}&skuId=${selectedSkuId.value}&count=1`,
  });
}

function onShare() { uni.showToast({ title: '从店铺页 ↗ 分享', icon: 'none' }); }
function goShop() { uni.navigateTo({ url: `/pages/shop/home?tenantId=${tenantId.value}` }); }
function goCart() { uni.navigateTo({ url: `/pages/cart/index?tenantId=${tenantId.value}` }); }
function goBack() { uni.navigateBack(); }

onLoad((q) => {
  // 兼容两种参数名：spuId（老 merchant 项目）和 id（新 user uniapp shop/home 跳法）
  const sid = q.spuId || q.id;
  spuId.value = sid ? Number(sid) : null;
  tenantId.value = q.tenantId ? Number(q.tenantId) : null;
  loadProduct();
  loadPromoConfig();
  loadCartCount();
  loadPoolPublic();
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page { min-height: 100vh; background: $bg-page; padding-bottom: 200rpx; }
.safe-bottom { padding-bottom: env(safe-area-inset-bottom); }

.empty-tip { text-align: center; padding: 80rpx 0; color: $text-placeholder; font-size: 26rpx; }

.pd-pic {
  height: 640rpx; position: relative; overflow: hidden;
  background: linear-gradient(135deg, #ffd1ba, #ff9a4a 50%, $brand-primary 100%);
  display: flex; align-items: center; justify-content: center;
}
.pd-pic .main-pic { width: 100%; height: 100%; }
.pd-pic .main-pic-emoji { font-size: 200rpx; color: #fff; }
.pd-pic .nav-back, .pd-pic .nav-share {
  position: absolute; top: calc(env(safe-area-inset-top) + 24rpx);
  width: 72rpx; height: 72rpx; border-radius: 50%;
  background: rgba(0,0,0,.30); color: #fff;
  line-height: 72rpx; text-align: center;
  z-index: 3; backdrop-filter: blur(12rpx);
}
.pd-pic .nav-back { left: 24rpx; font-size: 44rpx; }
.pd-pic .nav-share { right: 24rpx; font-size: 32rpx; }
.pd-pic .pager {
  position: absolute; bottom: 24rpx; left: 50%; transform: translateX(-50%);
  background: rgba(0,0,0,.35); color: #fff;
  padding: 6rpx 24rpx; border-radius: 999rpx;
  font-size: 22rpx;
}

.pd-info {
  background: $bg-card; padding: 32rpx;
  border-radius: $radius-lg $radius-lg 0 0;
  margin-top: -24rpx; position: relative; z-index: 2;
}
.pd-info .price-row { display: flex; align-items: baseline; gap: 16rpx; }
.pd-info .price {
  color: $brand-primary; font-size: 56rpx; font-weight: 800;
  font-variant-numeric: tabular-nums;
}
.pd-info .price .cny { font-size: 28rpx; }
.pd-info .original {
  color: $text-placeholder; font-size: 26rpx;
  text-decoration: line-through;
}
.pd-info .promo-tag {
  margin-left: auto;
  background: $brand-primary; color: #fff;
  font-size: 22rpx; padding: 4rpx 16rpx; border-radius: 999rpx;
}
.pd-info .name { margin-top: 16rpx; font-size: 36rpx; font-weight: 700; color: $text-primary; }
.pd-info .intro { margin-top: 8rpx; font-size: 24rpx; color: $text-secondary; line-height: 1.5; }
.pd-info .meta {
  margin-top: 20rpx; display: flex; gap: 32rpx;
  font-size: 22rpx; color: $text-placeholder;
}
.pd-info .meta .b { color: $text-primary; font-size: 26rpx; font-weight: 700; font-variant-numeric: tabular-nums; }

.promo-banner {
  margin: 24rpx 32rpx; padding: 24rpx 28rpx;
  background: linear-gradient(135deg, #fff5ef, #ffe1c8);
  border-radius: $radius-md; border: 2rpx solid rgba(255,107,53,.30);
  display: flex; align-items: center; gap: 20rpx;
}
.promo-banner .ic { font-size: 44rpx; }
.promo-banner .txt { flex: 1; font-size: 24rpx; color: $text-primary; }
.promo-banner .txt .b { color: $brand-primary; font-weight: 800; }
.promo-banner .sub { font-size: 20rpx; color: $text-placeholder; margin-top: 4rpx; }

.pd-section {
  background: $bg-card; border-radius: $radius-lg;
  margin: 24rpx 32rpx; padding: 28rpx;
  box-shadow: 0 4rpx 16rpx rgba(15,23,42,.04);
}
.pd-section .hdr {
  font-size: 26rpx; font-weight: 600; color: $text-primary;
  display: flex; justify-content: space-between; align-items: center;
}
.pd-section .hdr .t-brand { color: $brand-primary; font-weight: 400; font-size: 24rpx; }
.pd-section .body { margin-top: 16rpx; font-size: 26rpx; color: $text-secondary; line-height: 1.7; }

.pool-section .pool-strip {
  margin-top: 14rpx;
  padding: 16rpx 20rpx;
  background: linear-gradient(135deg, rgba(255,107,53,.08), rgba(255,154,74,.06));
  border-radius: $radius-md;
  display: flex; justify-content: space-between;
  .pool-bal { font-size: 24rpx; color: $text-secondary; }
  .pool-bal .b { color: $brand-primary; font-weight: 800; font-size: 30rpx; }
  .pool-out { font-size: 22rpx; color: $text-secondary; align-self: center; }
}
.pool-section .pool-empty {
  margin-top: 16rpx; text-align: center; padding: 30rpx 0;
  color: $text-placeholder; font-size: 24rpx;
}
.pool-section .winners-list { margin-top: 12rpx; }
.pool-section .winner-row {
  display: flex; align-items: center; gap: 16rpx;
  padding: 14rpx 0;
  border-bottom: 1rpx dashed $border-color;
  &:last-child { border-bottom: none; }
  &.self { background: rgba(255,107,53,.05); border-radius: $radius-md; padding-left: 12rpx; padding-right: 12rpx; }
  .rank {
    flex: 0 0 36rpx; height: 36rpx; line-height: 36rpx; text-align: center;
    font-size: 22rpx; color: $text-secondary; font-weight: 600;
  }
  .ava {
    width: 60rpx; height: 60rpx; border-radius: 50%;
    flex: 0 0 60rpx;
  }
  .default-ava {
    background: $bg-page; color: $text-secondary;
    line-height: 60rpx; text-align: center;
    font-size: 26rpx; font-weight: 600;
  }
  .info {
    flex: 1; display: flex; flex-direction: column; gap: 4rpx;
    .r1 { display: flex; align-items: center; gap: 8rpx; }
    .nk { font-size: 24rpx; color: $text-primary; font-weight: 500; }
    .self-tag {
      font-size: 18rpx; background: $brand-primary; color: #fff;
      padding: 2rpx 8rpx; border-radius: 999rpx;
    }
    .star-tag {
      font-size: 18rpx; background: rgba(255,107,53,.12); color: $brand-primary;
      padding: 2rpx 10rpx; border-radius: 999rpx;
    }
    .r2 { font-size: 20rpx; color: $text-secondary; }
  }
  .amt { font-size: 26rpx; font-weight: 700; color: $brand-primary; }
}

.sku-list { margin-top: 16rpx; display: flex; flex-wrap: wrap; gap: 12rpx; }
.sku-pill {
  padding: 12rpx 24rpx; border-radius: 999rpx;
  font-size: 24rpx; color: $text-secondary;
  background: $bg-page; border: 2rpx solid transparent;
}
.sku-pill.active {
  background: $brand-primary-light; color: $brand-primary;
  border-color: $brand-primary; font-weight: 700;
}

.bottom-space { height: 40rpx; }

.pd-bottom {
  position: fixed; bottom: 0; left: 0; right: 0;
  background: $bg-card; padding: 16rpx 24rpx;
  padding-bottom: calc(env(safe-area-inset-bottom) + 16rpx);
  box-shadow: 0 -4rpx 32rpx rgba(0,0,0,.06);
  display: flex; align-items: stretch; gap: 16rpx; z-index: 50;
}
.pd-bottom .ic-btn {
  width: 96rpx; display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  color: $text-secondary; font-size: 20rpx; gap: 4rpx;
  position: relative;
}
.pd-bottom .ic-btn .ic { font-size: 40rpx; }
.pd-bottom .ic-btn .badge {
  position: absolute; top: -4rpx; right: 12rpx;
  min-width: 32rpx; height: 32rpx; line-height: 32rpx;
  border-radius: 16rpx; padding: 0 8rpx;
  background: $danger; color: #fff;
  font-size: 18rpx; font-weight: 700; text-align: center;
}
.pd-bottom .add-cart {
  flex: 1; background: linear-gradient(135deg, #ff9a4a, $brand-primary);
  color: #fff; border-radius: 999rpx;
  display: flex; align-items: center; justify-content: center;
  font-size: 28rpx; font-weight: 700;
  box-shadow: 0 4rpx 16rpx rgba(255,107,53,.30);
}
.pd-bottom .buy-now {
  flex: 1; background: $brand-primary-dark;
  color: #fff; border-radius: 999rpx;
  display: flex; align-items: center; justify-content: center;
  font-size: 28rpx; font-weight: 700;
  box-shadow: 0 4rpx 16rpx rgba(216,86,42,.30);
}
</style>
