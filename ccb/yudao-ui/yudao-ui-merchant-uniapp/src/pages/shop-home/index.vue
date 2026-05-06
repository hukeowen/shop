<template>
  <view class="page">
    <!-- 沉浸式头图 -->
    <view class="sh-cover" :style="coverStyle">
      <view class="sh-nav-row safe-top">
        <view class="sh-icon-btn back" @click="goBack">‹</view>
        <view class="group">
          <view
            class="sh-icon-btn fav"
            :class="{ on: myRel?.favorite }"
            @click="toggleFavorite"
          >{{ myRel?.favorite ? '♥' : '♡' }}</view>
          <view class="sh-icon-btn share" @click="onShare">↗</view>
        </view>
      </view>
      <view class="sh-cover-inner">
        <view class="name-block">
          <view v-if="bizTag" class="biz-tag">🔥 {{ bizTag }}</view>
          <view class="name">{{ shopInfo?.shopName || shopInfo?.name || '加载中...' }}</view>
          <view class="slogan" v-if="slogan">{{ slogan }}</view>
        </view>
      </view>
    </view>

    <!-- 店铺信息卡 -->
    <view class="sh-info-card">
      <view class="stat-row">
        <view class="stat">
          <view class="num"><text class="icon">★</text>{{ formatRating(shopInfo?.avgRating) }}</view>
          <view class="lbl">评分</view>
        </view>
        <view class="stat-divider"></view>
        <view class="stat">
          <view class="num">{{ shopInfo?.sales30d || 0 }}</view>
          <view class="lbl">月售</view>
        </view>
        <view class="stat-divider"></view>
        <view class="stat">
          <view class="num brand">¥{{ fen2yuan(myRel?.balance || 0) }}</view>
          <view class="lbl">我已赚</view>
        </view>
        <view v-if="myRel?.star" class="my-star">
          ⭐ {{ myRel.star }} 星会员
          <text v-if="memberDiscount"><br><text style="font-size:18rpx;font-weight:400;">享 {{ memberDiscount }} 折</text></text>
        </view>
      </view>
      <!-- 店铺特色 chips（商户在 me/shop-edit 自填，无值整段不显示） -->
      <view v-if="featureChips.length" class="feature-chips">
        <text
          v-for="(c, i) in featureChips"
          :key="c"
          class="chip"
          :class="{ hot: i === 0 }"
        >{{ i === 0 ? '🔥 ' : '' }}{{ c }}</text>
      </view>
      <view class="quick-meta">
        <text class="pt" v-if="distanceText">📍 {{ distanceText }}<text v-if="shopInfo?.address"> · {{ shopInfo.address }}</text></text>
        <text class="pt" v-else-if="shopInfo?.address">📍 {{ shopInfo.address }}</text>
        <text class="dot" v-if="(distanceText || shopInfo?.address) && shopInfo?.businessHours"></text>
        <text class="pt" v-if="shopInfo?.businessHours">
          <text class="open-now">● 营业中</text> {{ shopInfo.businessHours }}
        </text>
      </view>
    </view>

    <!-- 会员特权 + 邀请条 -->
    <view class="vip-strip" @click="onShare">
      <view class="vip-icon">🎁</view>
      <view class="vip-info">
        <view class="vip-title">
          <text v-if="myRel?.star && memberDiscount">你是 <text class="b">{{ myRel.star }} 星会员</text>，享 <text class="b">{{ memberDiscount }} 折</text></text>
          <text v-else-if="myRel?.star">你是 <text class="b">{{ myRel.star }} 星会员</text></text>
          <text v-else>邀请好友赚返奖</text>
        </view>
        <view class="vip-sub">
          <text v-if="myRel">已邀请 {{ inviterCount }} 位好友 · 在该店赚 ¥{{ fen2yuan(myRel?.balance || 0) }}</text>
          <text v-else>每邀 1 位好友购买，按 v6 推 N 反 1 拿返奖</text>
        </view>
      </view>
      <view class="vip-cta">邀请赚奖 ›</view>
    </view>

    <!-- 招牌商品大卡（products 第一个；推 N 反 1 标仅在该商品启用 tuijian 时显示） -->
    <view v-if="signatureSpu" class="sh-section-title">
      <view class="sh-section-h3">
        <text class="ic">🏆</text>本店招牌
      </view>
    </view>
    <view v-if="signatureSpu" class="signature-card" @click="goDetail(signatureSpu)">
      <view class="crown-tag">👑 招牌 No.1</view>
      <view class="signature-card-inner">
        <view class="pic" :style="picStyle(signatureSpu, 0)">{{ pickEmoji(signatureSpu) }}</view>
        <view class="info">
          <view class="pname">{{ signatureSpu.name }}</view>
          <view class="ptag">{{ signatureSpu.introduction || '本店招牌 · 现做现卖' }}</view>
          <view class="stats">
            <text v-if="signatureSpu.salesCount">已售 <text class="em">{{ signatureSpu.salesCount }}</text></text>
            <text v-if="shopInfo?.avgRating">★ <text class="em">{{ formatRating(shopInfo.avgRating) }}</text></text>
            <text v-if="signatureTuijian" class="brand-em">{{ signatureTuijian }}</text>
          </view>
          <view class="price-row">
            <view class="price">
              <text class="cny">¥</text>{{ fen2yuan(getSpuPrice(signatureSpu)) }}
              <text v-if="signatureSpu.marketPrice && signatureSpu.marketPrice > getSpuPrice(signatureSpu)"
                    class="original">¥{{ fen2yuan(signatureSpu.marketPrice) }}</text>
            </view>
            <view class="add-big" @click.stop="addCart(signatureSpu)">+ 加入</view>
          </view>
        </view>
      </view>
    </view>

    <!-- 全部商品 -->
    <view v-if="products.length" class="sh-section-title">
      <view class="sh-section-h3">全部商品</view>
    </view>
    <view class="cat-tab">
      <view class="it active">全部</view>
    </view>
    <view v-if="loading && !products.length" class="empty-tip">加载中...</view>
    <view v-else-if="!products.length" class="empty-tip">暂无商品</view>
    <view v-else class="product-grid">
      <view
        v-for="(spu, i) in products"
        :key="spu.id"
        class="pcard"
        @click="goDetail(spu)"
      >
        <view v-if="i === 0" class="corner-tag">🔥 热销</view>
        <view class="pic" :style="picStyle(spu, i)">{{ pickEmoji(spu) }}</view>
        <view class="body">
          <view class="name">{{ spu.name }}</view>
          <view class="tag">{{ spu.introduction || '现做现卖' }}</view>
          <view class="row">
            <view class="price"><text class="cny">¥</text>{{ fen2yuan(getSpuPrice(spu)) }}</view>
            <view class="add-btn" @click.stop="addCart(spu)">+</view>
          </view>
        </view>
      </view>
    </view>

    <view class="bottom-space"></view>

    <!-- 底部购物车栏 -->
    <view class="sh-cart-bar safe-bottom" v-if="cartCount > 0">
      <view class="cart-icon" @click="goCart">
        🛒<text class="badge">{{ cartCount }}</text>
      </view>
      <view class="total">
        <view class="price">¥{{ fen2yuan(cartTotal) }}</view>
        <view class="delivery">满 30 立减 5</view>
      </view>
      <view class="pay-btn" @click="goCart">去结算</view>
    </view>

    <!-- 分享弹层 -->
    <view v-if="showShare" class="share-mask" @click.self="showShare = false">
      <view class="share-sheet">
        <view class="share-title">邀请好友进店</view>
        <view class="share-sub">朋友通过你的链接进店并下单，按 v6 推 N 反 1 自动返奖到你的推广积分</view>
        <view v-if="myShareQr" class="share-qr-wrap">
          <image :src="myShareQr" class="share-qr" mode="aspectFit" />
          <text class="share-qr-tip">长按图片可保存到相册</text>
        </view>
        <view class="share-link-row">
          <text class="share-link">{{ myShareUrl }}</text>
        </view>
        <view class="share-actions">
          <button class="share-btn primary" @click="onCopyShare">复制链接</button>
          <button class="share-btn ghost" @click="showShare = false">关闭</button>
        </view>
      </view>
    </view>

    <!-- 底部 tabbar：分享落地直进 shop-home 时也能切回首页/订单/我的 -->
    <RoleTabBar current="/pages/shop-home/index" />
  </view>
</template>

<script setup>
import { ref, computed, reactive } from 'vue';
import { onLoad, onShow } from '@dcloudio/uni-app';
import { request } from '../../api/request.js';
import { fen2yuan } from '../../utils/format.js';
import { savePendingReferrer, flushPendingReferrer } from '../../utils/referral.js';
import { useUserStore } from '../../store/user.js';

const PUBLIC_BASE_URL =
  (typeof import.meta !== 'undefined' && import.meta.env?.VITE_PUBLIC_BASE_URL) ||
  'http://www.doupaidoudian.com';

const userStore = useUserStore();
const tenantId = ref(null);
const shopInfo = ref(null);
const products = ref([]);
const cartCount = ref(0);
const cartTotal = ref(0);
const loading = ref(false);
const myRel = ref(null); // 含 favorite/star/balance/points
const inviterCount = ref(0);
// 招牌商品（products 第一项 + 它的 promo 配置——含「推 N 反 1」N 值）
const signaturePromo = ref(null);

// 招牌商品 = products 第一个（暂按当前排序；后续 M7 接销量排序）
const signatureSpu = computed(() => products.value && products.value[0] ? products.value[0] : null);
// 「推 N 反 1」标，仅在该商品启用了推荐返佣时显示
const signatureTuijian = computed(() => {
  const p = signaturePromo.value;
  if (!p || !p.tuijianEnabled || !p.tuijianN || p.tuijianN < 1) return '';
  return `推 ${p.tuijianN} 反 1`;
});

const showShare = ref(false);
const myShareUrl = ref('');
const myShareQr = ref('');

const bizTag = computed(() => {
  if (!shopInfo.value) return '';
  if (shopInfo.value.sales30d > 1000) return '人气小店';
  if (myRel.value?.star >= 3) return '老会员推荐';
  return '';
});
const slogan = computed(() => {
  return shopInfo.value?.description || '欢迎光临';
});
// 距离文本：< 1 km 显示米，否则 km 一位小数（user-h5 ④ "0.3km · 三里屯"）
const distanceText = computed(() => {
  const m = shopInfo.value?.distanceMeter;
  if (m == null || m < 0) return '';
  if (m < 1000) return `${m} m`;
  return `${(m / 1000).toFixed(1)} km`;
});

// 店铺特色 chips（来自 shop_info.feature_tags，CSV）
const featureChips = computed(() => {
  const raw = shopInfo.value?.featureTags;
  if (!raw || typeof raw !== 'string') return [];
  return raw.split(/[,，]/).map((s) => s.trim()).filter(Boolean).slice(0, 6);
});

// 折扣文案：从 shop_promo_config.star_discount_rates 读（百分制，100=原价）
//   - 商户没配 starDiscountRates → 返空，模板对应 v-if 隐藏整段折扣展示
//   - 配了但当前星级对应值是 100/无效 → 也返空（不打折就别显示）
//   - 配了 90 → 返 '9'（即 9 折）
const memberDiscount = computed(() => {
  const raw = shopInfo.value?.starDiscountRates;
  if (!raw) return '';
  let arr = null;
  try { arr = typeof raw === 'string' ? JSON.parse(raw) : raw; } catch { return ''; }
  if (!Array.isArray(arr) || arr.length === 0) return '';
  const s = myRel.value?.star || 0;
  const idx = Math.max(0, Math.min(s, arr.length - 1));
  const pct = Number(arr[idx]);
  if (!Number.isFinite(pct) || pct <= 0 || pct >= 100) return '';
  // 90 → 9; 95 → 9.5; 88 → 8.8
  return (pct / 10).toString();
});
const coverStyle = computed(() => {
  const palette = ['#ffd1ba,#ff9a4a,#ff6b35', '#c9e0ff,#6196f0,#3a78d8', '#d3f4d3,#6fcf6f,#3aa83a', '#ffd0dc,#ee5a8b,#cc3d6d'];
  const idx = (Number(tenantId.value) || 0) % palette.length;
  return `background: linear-gradient(135deg, ${palette[idx]});`;
});

function picStyle(spu, i) {
  const palette = ['#ffe1c8,#ffae74', '#d6e9ff,#80b3ff', '#d8f5d6,#6fcf6f', '#ffd6e0,#ff8aa7'];
  return `background: linear-gradient(135deg, ${palette[i % palette.length]});`;
}
function pickEmoji(spu) {
  const n = spu.name || '';
  if (/(地瓜|薯)/.test(n)) return '🍠';
  if (/(玉米)/.test(n)) return '🌽';
  if (/(茶|奶茶)/.test(n)) return '🍵';
  if (/(果|莓|桃|柑|橙|葡萄)/.test(n)) return '🍇';
  if (/(肉|串|烧)/.test(n)) return '🍖';
  if (/(包|馒头)/.test(n)) return '🥯';
  if (/(咖啡)/.test(n)) return '☕';
  if (/(蛋糕|甜)/.test(n)) return '🍰';
  return '🛍';
}
function getSpuPrice(spu) {
  return spu.price ?? (spu.skus && spu.skus[0] && spu.skus[0].price) ?? 0;
}
function formatRating(r) {
  if (r == null) return '5.0';
  return Number(r).toFixed(1);
}

async function loadShopInfo() {
  if (!tenantId.value) return;
  // 顺便取一下 GPS（拿不到也不阻塞流程，distance 段会 v-if 隐藏）
  let geoQuery = '';
  try {
    const loc = await new Promise((res) => {
      uni.getLocation({
        type: 'wgs84',
        success: (r) => res(r),
        fail: () => res(null),
      });
    });
    if (loc && loc.longitude && loc.latitude) {
      geoQuery = `&userLng=${loc.longitude}&userLat=${loc.latitude}`;
    }
  } catch {}
  try {
    shopInfo.value = await request({
      url: `/app-api/merchant/shop/public/info?tenantId=${tenantId.value}${geoQuery}`,
    });
    // 缓存店铺名，user-me/invite 二维码中心叠这个名字用
    const sn = shopInfo.value?.shopName || shopInfo.value?.name;
    if (sn) {
      try { uni.setStorageSync('lastShopName', sn); } catch {}
    }
  } catch {}
}
async function loadProducts() {
  if (!tenantId.value) return;
  try {
    // C 端用户访问商户店铺：不传 header tenant-id（与用户自身 token tenant 冲突会被
     // TenantSecurityWebFilter 401「您无权访问该租户的数据」），后端公开接口已 @TenantIgnore
     // 并按 query.tenantId 自取数据。
     const res = await request({
       url: `/app-api/product/spu/page?pageNo=1&pageSize=20&tenantId=${tenantId.value}`,
     });
    products.value = (res && res.list) ? res.list : (Array.isArray(res) ? res : []);
    // 加载完商品后异步拉招牌商品的「推 N 反 1」配置（products[0] 作招牌）
    loadSignaturePromo();
  } catch { products.value = []; }
}
// 招牌商品的 promo 配置（产生「推 4 反 1」标）
async function loadSignaturePromo() {
  signaturePromo.value = null;
  const spu = signatureSpu.value;
  if (!spu || !spu.id) return;
  try {
    const cfg = await request({
      url: `/app-api/merchant/mini/promo/product-config?spuId=${spu.id}`,
    });
    signaturePromo.value = cfg || null;
  } catch { signaturePromo.value = null; }
}
async function loadCart() {
  if (!tenantId.value) return;
  try {
    // 购物车按用户自己 token tenant 走，不传商户 tenantId 头（避免越权 401）
    const res = await request({
      url: '/app-api/trade/cart/list',
    });
    const list = (res && res.validList) || (res && res.list) || (Array.isArray(res) ? res : []);
    cartCount.value = list.reduce((s, c) => s + (c.count || 0), 0);
    cartTotal.value = list.reduce((s, c) => s + (c.count || 0) * (c.price || c.spu?.price || 0), 0);
  } catch {
    cartCount.value = 0; cartTotal.value = 0;
  }
}
async function loadMyRel() {
  if (!tenantId.value || !userStore.token) return;
  try {
    // 用 enriched 接口，过滤当前 tenant
    const list = await request({
      url: '/app-api/merchant/mini/member-rel/my-shops-enriched',
    });
    myRel.value = (list || []).find(s => Number(s.tenantId) === Number(tenantId.value)) || null;
  } catch { myRel.value = null; }
  // 跨店推荐人数（顺手拿）
  try {
    const r = await request({ url: '/app-api/merchant/mini/promo/referral/my-children-count' });
    inviterCount.value = r?.count ?? 0;
  } catch { inviterCount.value = 0; }
}
async function loadAll() {
  loading.value = true;
  try {
    await Promise.all([loadShopInfo(), loadProducts(), loadCart(), loadMyRel()]);
  } finally { loading.value = false; }
}

async function toggleFavorite() {
  if (!userStore.token) {
    uni.showModal({ title: '请先登录', content: '登录后才能收藏店铺', showCancel: false });
    return;
  }
  if (!tenantId.value) return;
  const next = !myRel.value?.favorite;
  try {
    await request({
      url: `/app-api/merchant/mini/member-rel/favorite/toggle?tenantId=${tenantId.value}&favorite=${next}`,
      method: 'POST',
    });
    if (myRel.value) myRel.value.favorite = next;
    else myRel.value = { favorite: next };
    uni.showToast({ title: next ? '已收藏' : '已取消收藏', icon: 'success' });
  } catch (e) {
    uni.showToast({ title: '操作失败', icon: 'none' });
  }
}

async function addCart(spu) {
  if (!userStore.token) {
    uni.showModal({ title: '请先登录', content: '登录后即可加入购物车', showCancel: false });
    return;
  }
  try {
    const skuId = spu.skuId || (spu.skus && spu.skus[0] && spu.skus[0].id);
    if (!skuId) {
      goDetail(spu);
      return;
    }
    await request({
      url: '/app-api/trade/cart/add',
      method: 'POST',
      data: { skuId, count: 1 },
    });
    uni.showToast({ title: '已加入购物车', icon: 'success' });
    loadCart();
  } catch (e) {
    uni.showToast({ title: e?.message || '加购失败', icon: 'none' });
  }
}

function buildMyShareUrl() {
  if (!userStore.userId) return '';
  if (!tenantId.value) return '';
  let origin = PUBLIC_BASE_URL;
  try {
    if (typeof location !== 'undefined' && location.origin && /doupaidoudian/i.test(location.origin)) {
      origin = location.origin;
    }
  } catch {}
  return `${origin}/m/shop-home?tenantId=${tenantId.value}&inviter=${userStore.userId}`;
}
async function onShare() {
  if (!userStore.userId || !userStore.token) {
    uni.showModal({ title: '请先登录', content: '登录后才能生成自己的邀请链接', showCancel: false });
    return;
  }
  if (!tenantId.value) {
    uni.showToast({ title: '店铺信息缺失', icon: 'none' });
    return;
  }
  myShareUrl.value = buildMyShareUrl();
  // sidecar /qr 出图：必须用 location.origin 绝对 URL（H5 base path 会把 /qr 改成 /m/qr 导致 404）
  const shopCenter = shopInfo.value?.shopName || shopInfo.value?.name || '';
  const base = (typeof location !== 'undefined' && location.origin) ? location.origin : '';
  let qr = `${base}/qr?text=${encodeURIComponent(myShareUrl.value)}&w=480&m=1`;
  if (shopCenter) qr += `&center=${encodeURIComponent(shopCenter)}`;
  myShareQr.value = qr;
  showShare.value = true;
}
function onCopyShare() {
  if (!myShareUrl.value) return;
  uni.setClipboardData({
    data: myShareUrl.value,
    success: () => uni.showToast({ title: '已复制', icon: 'success' }),
    fail: () => uni.showToast({ title: '复制失败', icon: 'none' }),
  });
}

function goDetail(spu) { uni.navigateTo({ url: `/pages/product/detail?spuId=${spu.id}&tenantId=${tenantId.value}` }); }
function goCart() { uni.navigateTo({ url: `/pages/cart/index?tenantId=${tenantId.value}` }); }
// switchTab 在没全局 tabBar 时静默失败，分享落地直进 shop-home 时 navigateBack 也无前一页 → reLaunch
function goBack() { uni.navigateBack({ fail: () => uni.reLaunch({ url: '/pages/user-home/index' }) }); }

onLoad((query) => {
  tenantId.value = query.tenantId ? Number(query.tenantId) : null;
  const referrerUserId = query.referrerUserId
    ? Number(query.referrerUserId)
    : (query.inviter ? Number(query.inviter) : null);

  if (tenantId.value) {
    uni.setStorageSync('lastShopTenantId', tenantId.value);
    // 注：lastShopName 在 loadShopInfo 加载完成后写入（onLoad 时机 shopInfo 还没拉到）
    request({
      url: `/app-api/merchant/mini/member-rel/visit?tenantId=${tenantId.value}${referrerUserId ? `&referrerUserId=${referrerUserId}` : ''}`,
      method: 'POST',
    }).catch(() => {});
  }
  if (referrerUserId) savePendingReferrer(referrerUserId);

  let isLoggedIn = !!(userStore?.token || userStore?.userId);
  if (isLoggedIn && userStore?.userId) flushPendingReferrer(userStore.userId);
  loadAll();

  if (!isLoggedIn && tenantId.value) {
    const redirect = `/pages/shop-home/index?tenantId=${tenantId.value}` + (referrerUserId ? `&inviter=${referrerUserId}` : '');
    setTimeout(() => {
      uni.showModal({
        title: '欢迎光临',
        content: '登录后即可下单 / 参与营销活动',
        confirmText: '去登录',
        cancelText: '先逛逛',
        success: (r) => {
          if (r.confirm) {
            uni.navigateTo({ url: `/pages/login/index?redirect=${encodeURIComponent(redirect)}` });
          }
        },
      });
    }, 600);
  }
});
onShow(() => loadCart());
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  min-height: 100vh;
  background: $bg-page;
  padding-bottom: 200rpx;
}
.safe-top { padding-top: env(safe-area-inset-top); }
.safe-bottom { padding-bottom: env(safe-area-inset-bottom); }

.sh-cover {
  height: 480rpx; position: relative; overflow: hidden;
  background: linear-gradient(135deg, #ff9a4a 0%, $brand-primary 50%, $brand-primary-dark 100%);
}
.sh-cover::before {
  content: '🍠';
  position: absolute;
  right: -60rpx; bottom: -60rpx;
  font-size: 400rpx; opacity: .14; transform: rotate(-12deg);
}
.sh-nav-row {
  position: absolute; top: 24rpx; left: 24rpx; right: 24rpx;
  display: flex; justify-content: space-between; align-items: center;
  z-index: 3;
}
.sh-nav-row .group { display: flex; gap: 16rpx; }
.sh-icon-btn {
  width: 72rpx; height: 72rpx; border-radius: 50%;
  background: rgba(255,255,255,.25); backdrop-filter: blur(12rpx);
  border: 2rpx solid rgba(255,255,255,.30);
  color: #fff; line-height: 72rpx; text-align: center;
  font-size: 36rpx;
  box-shadow: 0 4rpx 16rpx rgba(0,0,0,.10);
}
.sh-icon-btn.back { font-size: 44rpx; font-weight: 600; }
.sh-icon-btn.fav.on { background: rgba(238,90,139,.85); color: #fff; }
.sh-cover-inner {
  position: relative; z-index: 2; height: 100%;
  display: flex; flex-direction: column; justify-content: flex-end;
  padding: 0 32rpx 40rpx;
}
.name-block { color: #fff; text-shadow: 0 2rpx 16rpx rgba(0,0,0,.20); }
.biz-tag {
  display: inline-block; padding: 4rpx 20rpx;
  background: rgba(255,255,255,.22); backdrop-filter: blur(12rpx);
  border: 2rpx solid rgba(255,255,255,.30);
  border-radius: 999rpx; font-size: 22rpx; font-weight: 600;
  margin-bottom: 16rpx;
}
.name-block .name { font-size: 52rpx; font-weight: 800; line-height: 1.1; }
.name-block .slogan { margin-top: 8rpx; font-size: 26rpx; opacity: .92; }

.sh-info-card {
  margin: -64rpx 32rpx 0; padding: 28rpx 32rpx;
  background: $bg-card; border-radius: $radius-lg;
  box-shadow: 0 8rpx 32rpx rgba(15,23,42,.06);
  position: relative; z-index: 5;
}
.sh-info-card .stat-row {
  display: flex; align-items: center; gap: 24rpx;
  padding-bottom: 24rpx; border-bottom: 1rpx dashed $border-color;
}
.sh-info-card .stat-row .stat { text-align: center; }
.sh-info-card .stat-row .stat:first-child { text-align: left; }
.sh-info-card .stat-row .stat .num {
  font-size: 36rpx; font-weight: 800; color: $text-primary;
  font-variant-numeric: tabular-nums;
}
.sh-info-card .stat-row .stat .num.brand { color: $brand-primary; }
.sh-info-card .stat-row .stat .num .icon { font-size: 28rpx; color: $brand-primary; margin-right: 4rpx; }
.sh-info-card .stat-row .stat .lbl { font-size: 20rpx; color: $text-placeholder; margin-top: 4rpx; }
.sh-info-card .stat-divider {
  width: 2rpx; height: 56rpx; background: $border-color; align-self: center;
}
.sh-info-card .stat-row .my-star {
  margin-left: auto; padding: 12rpx 24rpx;
  background: linear-gradient(135deg,#fff5ef,#ffe1c8);
  border: 2rpx solid rgba(255,107,53,.35);
  border-radius: 999rpx;
  font-size: 22rpx; font-weight: 700; color: $brand-primary;
  text-align: center; line-height: 1.2;
}
// 店铺特色 chips（user-h5 line 791-806）
.sh-info-card .feature-chips {
  margin-top: 20rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}
.sh-info-card .feature-chips .chip {
  background: $bg-page;
  color: $text-regular;
  font-size: 22rpx;
  padding: 8rpx 20rpx;
  border-radius: 999rpx;
  font-weight: 500;
}
.sh-info-card .feature-chips .chip.hot {
  background: $brand-light;
  color: $brand-primary;
  font-weight: 600;
}

.sh-info-card .quick-meta {
  margin-top: 20rpx; display: flex; align-items: center; gap: 16rpx;
  font-size: 22rpx; color: $text-secondary;
}
.sh-info-card .quick-meta .open-now { color: $success; font-weight: 700; }
.sh-info-card .quick-meta .dot {
  width: 6rpx; height: 6rpx; border-radius: 50%;
  background: $text-placeholder;
}

.vip-strip {
  margin: 24rpx 32rpx; padding: 28rpx 32rpx;
  background: linear-gradient(135deg, #fff5ef 0%, #ffe1c8 100%);
  border-radius: $radius-lg;
  border: 2rpx solid rgba(255,107,53,.30);
  display: flex; align-items: center; gap: 24rpx;
  position: relative; overflow: hidden;
}
.vip-strip::before {
  content: ''; position: absolute;
  right: -80rpx; top: -80rpx;
  width: 200rpx; height: 200rpx;
  background: radial-gradient(circle, rgba(255,107,53,.18), transparent);
  border-radius: 50%;
}
.vip-icon {
  width: 80rpx; height: 80rpx;
  background: linear-gradient(135deg, $brand-primary, $brand-primary-dark);
  color: #fff; border-radius: $radius-md;
  display: flex; align-items: center; justify-content: center;
  font-size: 44rpx;
  box-shadow: 0 8rpx 24rpx rgba(255,107,53,.30);
  flex-shrink: 0; z-index: 1;
}
.vip-info { flex: 1; min-width: 0; z-index: 1; }
.vip-title { font-size: 26rpx; font-weight: 700; color: $text-primary; }
.vip-title .b { color: $brand-primary; font-size: 28rpx; }
.vip-sub { margin-top: 4rpx; font-size: 22rpx; color: $text-secondary; }
.vip-cta {
  background: $brand-primary; color: #fff;
  font-size: 22rpx; font-weight: 700;
  padding: 12rpx 24rpx; border-radius: 999rpx;
  box-shadow: 0 4rpx 16rpx rgba(255,107,53,.30);
  z-index: 1;
}

// shop section title
.sh-section-title {
  margin: 32rpx 32rpx 16rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.sh-section-h3 {
  font-size: 30rpx;
  font-weight: 700;
  color: $text-primary;
  display: flex;
  align-items: center;
  gap: 12rpx;
  .ic { color: $brand-primary; font-size: 32rpx; }
}

// 招牌商品大卡（原型 line 961-1034）
.signature-card {
  position: relative;
  margin: 0 32rpx 24rpx;
  background: linear-gradient(135deg, #fff 0%, #fff5ef 50%, #fff 100%);
  border: 4rpx solid $brand-light-2;
  border-radius: $radius-lg;
  overflow: hidden;
  box-shadow: 0 8rpx 32rpx rgba(255, 107, 53, 0.10);
}
.signature-card .crown-tag {
  position: absolute;
  top: 0; left: 0;
  background: linear-gradient(135deg, $brand-primary, $brand-primary-dark);
  color: #fff;
  font-size: 22rpx; font-weight: 800;
  padding: 6rpx 28rpx 6rpx 20rpx;
  border-radius: $radius-lg 0 999rpx 0;
  z-index: 2;
  box-shadow: 0 4rpx 16rpx rgba(255, 107, 53, 0.40);
}
.signature-card-inner {
  display: flex;
  gap: 24rpx;
  padding: 32rpx;
}
.signature-card .pic {
  width: 200rpx; height: 200rpx;
  border-radius: $radius-md;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 112rpx;
  flex-shrink: 0;
  box-shadow: 0 8rpx 24rpx rgba(255, 107, 53, 0.20);
}
.signature-card .info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.signature-card .pname {
  font-size: 32rpx;
  font-weight: 700;
  color: $text-primary;
}
.signature-card .ptag {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: $text-secondary;
}
.signature-card .stats {
  margin-top: auto;
  display: flex; gap: 20rpx;
  align-items: center;
  font-size: 22rpx; color: $text-secondary;
  flex-wrap: wrap;
  .em { color: $brand-primary; font-weight: 700; }
  .brand-em { color: $brand-primary; font-weight: 700; }
}
.signature-card .price-row {
  margin-top: 16rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.signature-card .price {
  color: $brand-primary;
  font-weight: 800;
  font-size: 44rpx;
  line-height: 1;
  .cny { font-size: 24rpx; }
  .original {
    color: $text-secondary;
    font-size: 22rpx;
    font-weight: 400;
    text-decoration: line-through;
    margin-left: 12rpx;
  }
}
.signature-card .add-big {
  background: linear-gradient(135deg, $brand-primary, $brand-primary-dark);
  color: #fff;
  height: 64rpx;
  padding: 0 32rpx;
  border-radius: 999rpx;
  font-size: 26rpx;
  font-weight: 700;
  line-height: 64rpx;
  box-shadow: 0 8rpx 24rpx rgba(255, 107, 53, 0.30);
}

.cat-tab {
  display: flex; gap: 0; background: $bg-card;
  margin: 24rpx 32rpx 0; border-radius: $radius-md;
  padding: 8rpx; box-shadow: 0 4rpx 16rpx rgba(15,23,42,.04);
}
.cat-tab .it {
  flex: 1; text-align: center; padding: 16rpx;
  font-size: 26rpx; color: $text-secondary;
  border-radius: $radius-sm;
}
.cat-tab .it.active { background: $brand-primary-light; color: $brand-primary; font-weight: 600; }

.empty-tip { text-align: center; padding: 80rpx 0; color: $text-placeholder; font-size: 26rpx; }

.product-grid {
  display: grid; grid-template-columns: repeat(2, 1fr);
  gap: 16rpx; padding: 24rpx 32rpx;
}
.pcard {
  background: $bg-card; border-radius: $radius-md;
  overflow: hidden; box-shadow: 0 4rpx 16rpx rgba(15,23,42,.04);
  position: relative;
}
.pcard .corner-tag {
  position: absolute; top: 12rpx; left: 0;
  background: linear-gradient(135deg, $brand-primary, $brand-primary-dark);
  color: #fff; font-size: 20rpx; font-weight: 700;
  padding: 4rpx 16rpx;
  border-radius: 0 999rpx 999rpx 0; z-index: 2;
}
.pcard .pic {
  height: 220rpx;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 72rpx;
}
.pcard .body { padding: 16rpx 20rpx 20rpx; }
.pcard .name {
  font-size: 26rpx; font-weight: 600; color: $text-primary;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.pcard .tag {
  margin-top: 4rpx; font-size: 20rpx; color: $text-placeholder;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.pcard .row {
  margin-top: 12rpx; display: flex; align-items: center; justify-content: space-between;
}
.pcard .price {
  font-size: 32rpx; font-weight: 800; color: $brand-primary;
  font-variant-numeric: tabular-nums;
}
.pcard .price .cny { font-size: 22rpx; font-weight: 600; }
.pcard .add-btn {
  width: 56rpx; height: 56rpx; border-radius: 50%;
  background: $brand-primary; color: #fff;
  font-size: 36rpx; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 4rpx 12rpx rgba(255,107,53,.30);
}

.bottom-space { height: 40rpx; }

.sh-cart-bar {
  position: fixed; bottom: 0; left: 0; right: 0;
  background: $bg-card;
  padding: 24rpx 32rpx;
  padding-bottom: calc(env(safe-area-inset-bottom) + 24rpx);
  box-shadow: 0 -4rpx 32rpx rgba(0,0,0,.06);
  display: flex; align-items: center; gap: 20rpx; z-index: 50;
}
.sh-cart-bar .cart-icon {
  width: 96rpx; height: 96rpx; border-radius: 50%;
  background: $brand-primary;
  display: flex; align-items: center; justify-content: center;
  font-size: 44rpx; color: #fff;
  position: relative;
}
.sh-cart-bar .badge {
  position: absolute; top: -4rpx; right: -4rpx;
  min-width: 36rpx; height: 36rpx; line-height: 36rpx;
  border-radius: 18rpx; padding: 0 8rpx;
  background: $danger; color: #fff;
  font-size: 20rpx; font-weight: 700; text-align: center;
}
.sh-cart-bar .total { flex: 1; }
.sh-cart-bar .total .price {
  font-size: 36rpx; font-weight: 800; color: $brand-primary;
  font-variant-numeric: tabular-nums;
}
.sh-cart-bar .total .delivery { font-size: 22rpx; color: $text-placeholder; }
.sh-cart-bar .pay-btn {
  background: $brand-primary; color: #fff;
  height: 80rpx; padding: 0 40rpx; line-height: 80rpx;
  border-radius: 40rpx; font-size: 28rpx; font-weight: 700;
}

// 分享弹层
.share-mask {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,.55); z-index: 2000;
  display: flex; align-items: flex-end;
}
.share-sheet {
  width: 100%; background: $bg-card;
  border-radius: $radius-lg $radius-lg 0 0;
  padding: 36rpx 32rpx;
  padding-bottom: calc(env(safe-area-inset-bottom) + 32rpx);
}
.share-title { text-align: center; font-size: 32rpx; font-weight: 700; color: $text-primary; }
.share-sub { margin-top: 12rpx; text-align: center; font-size: 22rpx; color: $text-secondary; line-height: 1.5; }
.share-qr-wrap { margin-top: 28rpx; display: flex; flex-direction: column; align-items: center; }
.share-qr { width: 400rpx; height: 400rpx; border-radius: $radius-md; }
.share-qr-tip { margin-top: 12rpx; font-size: 22rpx; color: $text-placeholder; }
.share-link-row {
  margin-top: 24rpx; padding: 16rpx 24rpx;
  background: $bg-page; border-radius: $radius-md;
}
.share-link { font-size: 22rpx; color: $text-secondary; word-break: break-all; }
.share-actions {
  margin-top: 24rpx; display: flex; gap: 16rpx;
}
.share-actions .share-btn {
  flex: 1; height: 88rpx; line-height: 88rpx;
  border-radius: $radius-md; font-size: 28rpx; font-weight: 600;
  &::after { border: none; }
}
.share-actions .share-btn.primary { background: $brand-primary; color: #fff; }
.share-actions .share-btn.ghost { background: $bg-page; color: $text-secondary; }
</style>
