<template>
  <view class="page">
    <!-- ━━━━━━━━━━ 顶部封面 + 导航 ━━━━━━━━━━ -->
    <view class="sh-cover">
      <view class="sh-cover-bg"></view>
      <view class="sh-cover-mark">{{ shopEmoji }}</view>
      <view class="sh-nav" :style="{ paddingTop: (statusH + 8) + 'px' }">
        <view class="sh-ic" @click="goBack">‹</view>
        <view class="sh-nav-right">
          <view class="sh-ic" @click="toggleFav">{{ isFav ? '♥' : '♡' }}</view>
        </view>
      </view>
      <view v-if="shop" class="sh-info">
        <view class="sh-tag-row">🔥 {{ shop.tagRow || '扫码下单 · 每单返推广积分' }}</view>
        <view class="sh-name">{{ shop.shopName || shop.name || '店铺' }}</view>
        <view class="sh-slogan">{{ shop.slogan || '商户营销优惠活动' }}</view>
        <view v-if="shop.featureTagList && shop.featureTagList.length" class="sh-feats">
          <text v-for="(t, i) in shop.featureTagList" :key="i" class="sh-feat">{{ t }}</text>
        </view>
      </view>
    </view>

    <!-- ━━━━━━━━━━ 3 列统计卡 ━━━━━━━━━━ -->
    <view class="sh-info-card">
      <view class="sh-stat-row">
        <view class="sh-stat">
          <view class="v gold"><text class="star">★</text>{{ shop?.rating || '5.0' }}</view>
          <view class="l">{{ shop?.ratingCount ? shop.ratingCount + ' 评分' : (shop?.monthSold > 0 ? '综合评分' : '新店') }}</view>
        </view>
        <view class="sh-stat-divider"></view>
        <view class="sh-stat">
          <view class="v">{{ shop?.monthSold ?? '0' }}</view>
          <view class="l">月售</view>
        </view>
        <view class="sh-stat-divider"></view>
        <view class="sh-stat">
          <view class="v brand">¥{{ myEarn }}</view>
          <view class="l">你已赚</view>
        </view>
      </view>
    </view>

    <!-- ━━━━━━━━━━ 商家资质（营业执照公示）━━━━━━━━━━ -->
    <view class="sh-qual" @click="viewLicense">
      <text class="q-ic">🏢</text>
      <view class="q-body">
        <text class="q-t">商家资质</text>
        <text class="q-sub">查看营业执照</text>
      </view>
      <text class="q-arr">查看 ›</text>
    </view>

    <!-- ━━━━━━━━━━ 招牌商品大卡（推 N 反 1 活动）━━━━━━━━━━ -->
    <view v-if="signatureSpu" class="signature-card" @click="goSignature">
      <view class="sig-crown">👑 招牌 No.1{{ nback ? ` · 推 ${nback.n} 反 1` : '' }}</view>
      <view class="sig-inner">
        <view class="sig-pic">
          <image v-if="signatureSpu.picUrl && !signatureSpu.imgErr" :src="signatureSpu.picUrl" mode="aspectFill" class="sig-pic-img" @error="signatureSpu.imgErr = true" />
          <text v-else class="sig-pic-em">{{ guessEmoji(signatureSpu.name) }}</text>
        </view>
        <view class="sig-body">
          <view class="sig-name">{{ signatureSpu.name }}</view>
          <view class="sig-intro">{{ signatureSpu.introduction || '本店招牌 · 现做现卖' }}</view>
          <view class="sig-tags">
            <view v-if="nback" class="sig-tag promo">推 {{ nback.n }} 反 1</view>
            <view v-if="nback" class="sig-tag amount">每位约 {{ nback.stepPoints }} 积分</view>
            <view v-if="nback && nback.cur > 0" class="sig-tag got">你已获 {{ nback.gotPoints }} 积分</view>
            <view v-else-if="signatureSpu.starCount" class="sig-tag gold">优惠商品</view>
          </view>
          <view class="sig-bot">
            <view class="sig-price-block">
              <view class="sig-price">¥{{ fen2yuan(signatureSpu.price, false) }}</view>
              <view v-if="signatureSpu.marketPrice && signatureSpu.marketPrice > signatureSpu.price" class="sig-orig">¥{{ fen2yuan(signatureSpu.marketPrice, false) }}</view>
            </view>
            <view class="sig-add" @click.stop="onAddSignature">+ 加入</view>
          </view>
        </view>
      </view>
      <view v-if="nback" class="sig-progress">
        <view class="sig-fill" :style="{ width: nback.pct + '%' }"></view>
        <view class="sig-progress-txt" v-if="nback.cur > 0">推送进度 {{ nback.cur }}/{{ nback.n }} · 还差 {{ nback.n - nback.cur }} 位 · 已累计 {{ nback.gotPoints }} 积分</view>
        <view class="sig-progress-txt" v-else>下单激活「推 {{ nback.n }} 反 1」 · 累计 {{ nback.totalPoints }} 积分（约本品 1 件价值）</view>
      </view>

      <!-- ━━━ 规则说明（4 步图解）━━━ -->
      <view v-if="nback" class="sig-rule" @click.stop="ruleExpanded = !ruleExpanded">
        <view class="sig-rule-head">
          <text class="em">📖</text>
          <text class="t">推 {{ nback.n }} 反 1 怎么参与？</text>
          <text class="arr">{{ ruleExpanded ? '收起 ▲' : '点击展开 ▼' }}</text>
        </view>
        <view v-if="ruleExpanded" class="sig-rule-body">
          <view class="step-grid">
            <view class="step">
              <view class="step-em">🛒</view>
              <view class="step-t">1. 自己下单</view>
              <view class="step-d">购买激活活动资格</view>
            </view>
            <view class="step">
              <view class="step-em">👥</view>
              <view class="step-t">2. 推荐朋友</view>
              <view class="step-d">朋友在本店首单</view>
            </view>
            <view class="step">
              <view class="step-em">💎</view>
              <view class="step-t">3. 积分入账</view>
              <view class="step-d">每位约 {{ nback.stepPoints }} 积分</view>
            </view>
            <view class="step">
              <view class="step-em">🎁</view>
              <view class="step-t">4. 商户兑现</view>
              <view class="step-d">商品 / 现金 自选</view>
            </view>
          </view>
          <view class="rule-list">
            <view class="rule-item"><text class="b">活动名称</text>：推 <text class="hl">{{ nback.n }}</text> 反 1（商户独立设定的营销活动）</view>
            <view class="rule-item"><text class="b">如何参与</text>：在本店购买本商品激活，推荐 {{ nback.n }} 位新客户本店首单 → 累计获得本品 1 件价值的积分</view>
            <view class="rule-item"><text class="b">商户承诺</text>：你获得的积分由商户独立承诺兑现，可任选下述任一方式使用 ↓</view>
            <view class="rule-item"><text class="b">用途 ①</text>：<text class="usage">购买本店所有商品</text>（不限本品，结账时按积分抵扣）</view>
            <view class="rule-item"><text class="b">用途 ②</text>：<text class="usage">线下找商家兑换现金</text>（商户独立审批 · 兑付形式以商户为准）</view>
            <view class="rule-item"><text class="b">奖励性质</text>：积分为商户营销凭证，<text class="b">非货币、非证券、非投资品</text></view>
          </view>
          <view class="rule-foot">营销活动与兑付由商户独立负责 · 平台仅提供技术服务 · 不构成担保 / 投资邀约</view>
        </view>
      </view>
    </view>

    <!-- ━━━━━━━━━━ VIP 邀请条 ━━━━━━━━━━ -->
    <view class="vip-strip" @click="openInvitePoster">
      <view class="vip-em">🎁</view>
      <view class="vip-body">
        <view v-if="vip.myStar > 0" class="vip-t">你是 <text class="b">{{ vip.myStar }} 星会员</text>{{ vip.discountText ? `，享 ` : '' }}<text v-if="vip.discountText" class="b">{{ vip.discountText }}</text></view>
        <view v-else class="vip-t"><text class="b">邀好友进店</text> · 每人下单你拿返奖</view>
        <view class="vip-d">
          <text v-if="vip.inviterCount > 0">已邀 {{ vip.inviterCount }} 位 · </text>
          <text>在该店赚 ¥{{ vip.earnYuan }}</text>
        </view>
      </view>
      <view class="vip-cta">邀请有礼 ›</view>
    </view>

    <!-- ━━━━━━━━━━ 优惠券领取条（横滚）━━━━━━━━━━ -->
    <scroll-view v-if="coupons.length" scroll-x class="coupon-strip">
      <view v-for="c in coupons" :key="c.id" class="coupon-card" :class="{ taken: c.taken }" @click="onGrabCoupon(c)">
        <view class="coupon-amt">
          <view class="v"><text class="c">¥</text>{{ fen2yuan(c.discountAmount || c.discountPrice || 0, false) }}</view>
          <view class="cond">{{ c.minAmount || c.usePrice ? `满 ${fen2yuan(c.minAmount || c.usePrice, false)} 可用` : '无门槛' }}</view>
        </view>
        <view class="coupon-divider"></view>
        <view class="coupon-action">
          <view class="grab">{{ c.taken ? '已领' : '领取' }}</view>
        </view>
      </view>
    </scroll-view>

    <!-- ━━━━━━━━━━ Social proof ━━━━━━━━━━ -->
    <view v-if="visitorCount > 0" class="social-proof">
      <text class="ic">👥</text>
      <text><text class="num">{{ visitorCount }}</text> 位邻居近 30 天来过这家店</text>
    </view>

    <!-- ━━━━━━━━━━ 店内中奖滚动（点击进派奖详情）━━━━━━━━━━ -->
    <view v-if="tickerText.length" class="sh-ticker" @click="goWinners">
      <text class="em">🏆</text>
      <view class="roll">
        <view class="roll-track">
          <text v-for="(t, i) in tickerText" :key="i">{{ t }} · </text>
        </view>
      </view>
      <text class="ticker-arrow">›</text>
    </view>

    <!-- V044 合规：删除"我们的承诺" 卡片 — 平台为纯技术中介，营销规则与兑付由商户独立负责，平台不承担担保 -->

    <!-- ━━━━━━━━━━ 商品列表（美团风：左侧分类 tab + 右侧商品大卡）━━━━━━━━━━ -->
    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!spus.length" title="该店暂无商品" />
    <view v-else class="mt-products">
      <!-- 左侧分类 -->
      <scroll-view scroll-y class="mt-side">
        <view class="mt-side-item" :class="{ on: activeCat === 0 }" @click="activeCat = 0">
          <text class="dot" v-if="activeCat === 0"></text>全部
        </view>
        <view v-for="c in cats" :key="c.id" class="mt-side-item" :class="{ on: activeCat === c.id }" @click="activeCat = c.id">
          <text class="dot" v-if="activeCat === c.id"></text>{{ c.name }}
        </view>
      </scroll-view>
      <!-- 右侧商品大卡 -->
      <scroll-view scroll-y class="mt-main">
        <view v-if="!filteredSpus.length" class="mt-empty">该分类暂无商品</view>
        <view v-for="p in filteredSpus" :key="p.id" class="mt-card" @click="goProduct(p)">
          <view class="mt-pic">
            <image v-if="p.picUrl && !p.imgErr" :src="p.picUrl" mode="aspectFill" class="mt-pic-img" @error="onImgErr(p)" />
            <text v-else class="mt-pic-em">{{ guessEmoji(p.name) }}</text>
            <view v-if="p.badge" class="mt-badge" :class="p.badgeClass">{{ p.badge }}</view>
          </view>
          <view class="mt-info">
            <view class="mt-name">{{ p.name }}</view>
            <view v-if="p.introduction" class="mt-intro">{{ p.introduction }}</view>
            <view class="mt-meta">
              <text v-if="p.salesCount" class="mt-sales">月售 {{ p.salesCount }}</text>
              <text v-if="p.rating" class="mt-rate">★ {{ Number(p.rating).toFixed(1) }}</text>
            </view>
            <view class="mt-foot">
              <view class="mt-price-block">
                <text class="mt-cny">¥</text>
                <text class="mt-price">{{ fen2yuan(p.price, false) }}</text>
                <text v-if="p.marketPrice && p.marketPrice > p.price" class="mt-mkt">¥{{ fen2yuan(p.marketPrice, false) }}</text>
              </view>
              <view class="mt-add" @click.stop="onAddCart(p)">+ 加购</view>
            </view>
          </view>
        </view>
      </scroll-view>
    </view>

    <view class="bottom-pad"></view>

    <!-- ━━━━━━━━━━ 底部购物车浮条 ━━━━━━━━━━ -->
    <view v-if="cartCount > 0" class="cart-bar">
      <view class="cart-bar-ic" @click="goCart">
        🛒<view class="badge">{{ cartCount }}</view>
      </view>
      <view class="cart-bar-info">
        <view class="cart-bar-price">¥{{ cartTotalYuan }}</view>
        <view v-if="cartHint" class="cart-bar-d">{{ cartHint }}</view>
      </view>
      <view class="cart-bar-pay" @click="goCart">去结算</view>
    </view>

    <!-- ━━━━━━━━━━ 分享浮标（醒目，避开右上角小程序胶囊）━━━━━━━━━━ -->
    <!-- #ifdef MP-WEIXIN -->
    <!-- 登录了才是原生分享按钮；没登录点了先跳登录（保证分享出去带 inviter 绑推广） -->
    <button v-if="user.isLogin" open-type="share" class="sh-share-fab">↗ 分享给好友</button>
    <view v-else class="sh-share-fab" @click="requireLogin">↗ 分享给好友</view>
    <!-- #endif -->
    <!-- #ifndef MP-WEIXIN -->
    <view class="sh-share-fab" @click="onShareLink">↗ 分享给好友</view>
    <!-- #endif -->

    <!-- ━━━━━━━━━━ 邀请有礼：推广二维码海报弹层 ━━━━━━━━━━ -->
    <view v-if="posterOpen" class="poster-mask" @click="closePoster">
      <view class="poster-wrap" @click.stop>
        <view v-if="posterLoading" class="poster-loading">海报生成中…</view>
        <image v-else-if="posterImage" :src="posterImage" mode="widthFix" class="poster-img" show-menu-by-longpress />
        <view v-else class="poster-loading">海报生成失败，请重试</view>
        <view class="poster-actions">
          <view class="poster-btn primary" @click="onSavePoster">保存到相册</view>
          <view class="poster-btn ghost" @click="onSharePoster">分享</view>
          <view class="poster-btn ghost" @click="closePoster">关闭</view>
        </view>
        <view class="poster-hint">💡 长按上图可直接保存 / 转发，或点「保存到相册」下载</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { onShow, onLoad, onShareAppMessage, onShareTimeline } from '@dcloudio/uni-app';
import { getShopInfo, listShopProducts, getShopVisitorCount, getMyRel, getMyPromoEarned, getShopLicense, toggleShopFavorite } from '@/api/shop.js';
import { savePendingReferrer, flushPendingReferrer } from '@/utils/referral.js';
import { listCategories } from '@/api/product.js';
import { listWinners, getAccount } from '@/api/promo.js';
import { addCart, listCart, getCartCount } from '@/api/cart.js';
import { listCouponTemplates, takeCoupon } from '@/api/coupon.js';
import { request } from '@/utils/request.js';
import { fen2yuan } from '@/utils/format.js';
import { buildInvitePoster, downloadDataUrl } from '@/utils/poster.js';
import { useUserStore } from '@/store/user.js';

const user = useUserStore();
// 路由参数：小程序 setup 阶段 getCurrentPages().options 可能尚未挂上，必须在 onLoad 再同步一次
const route = reactive({});
(function initRoute() {
  try { const ps = getCurrentPages(); Object.assign(route, ps[ps.length - 1]?.options || {}); } catch {}
})();
onLoad((opts) => {
  if (opts) Object.assign(route, opts);
  // 扫小程序码进入：微信把 scene 放到 options.scene（形如 "171-456"，可能被 URL 编码）。
  // 解析为 tenantId-inviter，塞进 route 并暂存推广关系，走与普通带参进店同一套绑定逻辑。
  try {
    if (opts && opts.scene) {
      const decoded = decodeURIComponent(opts.scene);
      const parts = String(decoded).split('-');
      const tid = parts[0] ? parts[0].trim() : '';
      const inv = parts[1] ? parts[1].trim() : '';
      if (tid) route.tenantId = tid;
      if (inv) route.inviter = inv;
      if (inv && tid) savePendingReferrer(inv, tid);
    }
  } catch {}
});

const statusH = ref(20);
const shop = ref(null);
const cats = ref([]);
const activeCat = ref(0);
const spus = ref([]);
const loading = ref(true);
const isFav = ref(false);

const myEarn = ref('0');
const signatureSpu = ref(null);
const nback = ref(null);
const ruleExpanded = ref(false);
const vip = reactive({ myStar: 0, discountText: '', inviterCount: 0, earnYuan: '0' });
const coupons = ref([]);
const visitorCount = ref(0);
const tickerText = ref([]);
const cartCount = ref(0);
const cartTotalFen = ref(0);
const cartHint = ref('');

// 邀请有礼海报弹层
const posterOpen = ref(false);
const posterLoading = ref(false);
const posterImage = ref('');

const cartTotalYuan = computed(() => fen2yuan(cartTotalFen.value, false));

const shopEmoji = computed(() => {
  const name = (shop.value?.shopName || shop.value?.name || '').toLowerCase();
  if (/烤|薯|肉|餐|炒|饭/.test(name)) return '🍠';
  if (/茶|奶|饮/.test(name))         return '🍵';
  if (/果|蔬|生鲜/.test(name))       return '🍇';
  if (/糕|甜|烘焙/.test(name))       return '🍰';
  return '🏪';
});

const filteredSpus = computed(() => {
  const all = spus.value;
  if (activeCat.value === 0) return all;
  return all.filter((p) => p.categoryId === activeCat.value);
});

function picTone(i) { return ['', 'green', 'purple', 'pink'][i % 4]; }
function onImgErr(p) { p.imgErr = true; }
function guessEmoji(name) {
  const n = (name || '').toLowerCase();
  if (/茶|饮|奶|咖啡/.test(n))    return '🍵';
  if (/粥|饭|面|汤/.test(n))      return '🍜';
  if (/烤|肉|串|鸡|鸭/.test(n))   return '🍖';
  if (/果|蔬|菜|生鲜/.test(n))    return '🍇';
  if (/糕|甜|烘焙|蛋糕/.test(n))  return '🍰';
  if (/酒|啤|红/.test(n))         return '🍷';
  return '🍽';
}

function goBack() {
  const pages = getCurrentPages();
  if (pages.length > 1) uni.navigateBack();
  else uni.reLaunch({ url: '/pages/index/index' });
}

// 商家资质：拉营业执照图片，全屏预览（后端只返营业执照，无身份证）
let _licenseCache = null;
async function viewLicense() {
  try {
    if (!_licenseCache) _licenseCache = (await getShopLicense(route.tenantId)) || {};
    const url = _licenseCache.licenseUrl;
    if (!url) { uni.showToast({ title: '该商家暂未公示营业执照', icon: 'none' }); return; }
    uni.previewImage({ urls: [url], current: url });
  } catch { uni.showToast({ title: '资质加载失败', icon: 'none' }); }
}
function goProduct(p) {
  uni.navigateTo({ url: `/pages/product/detail?id=${p.id}&tenantId=${route.tenantId || ''}` });
}
function goSignature() {
  if (signatureSpu.value) goProduct(signatureSpu.value);
}
function goCart() { uni.navigateTo({ url: '/pages/cart/index' }); }
function goWinners() {
  // 跳中奖公榜（派奖详情），带 tenantId 让 winners 页过滤本店
  uni.navigateTo({ url: `/pages/winners/index?tenantId=${route.tenantId || ''}` });
}
async function toggleFav() {
  if (!user.isLogin) return requireLogin();
  if (!route.tenantId) return;
  const next = !isFav.value;
  isFav.value = next; // 乐观更新
  try {
    await toggleShopFavorite(route.tenantId, next);
    uni.showToast({ title: next ? '已收藏' : '已取消收藏', icon: 'none' });
  } catch (e) {
    isFav.value = !next; // 失败回滚
    uni.showToast({ title: '操作失败，请重试', icon: 'none' });
  }
}
function inviteLink() {
  const base = typeof location !== 'undefined' ? location.origin : 'https://ke.doupaidoudian.com';
  return `${base}/#/pages/shop/home?tenantId=${route.tenantId}&inviter=${user.userId || ''}`;
}

// 右上角 ↗：优先调起系统原生分享面板（微信/系统），不支持则降级复制链接
async function onShareLink() {
  const link = inviteLink();
  const title = (shop.value?.shopName || '这家店') + ' · 邀三惠';
  const text = `「${shop.value?.shopName || '本店'}」营销优惠进行中，扫码进店下单还能赚推广积分`;
  try {
    if (typeof navigator !== 'undefined' && navigator.share) {
      await navigator.share({ title, text, url: link });
      return;
    }
  } catch { /* 用户取消原生分享 → 静默返回 */ return; }
  // 微信内置浏览器无 navigator.share：直接弹「推广二维码海报」，长按可保存 / 转发给好友，比单纯复制链接更实用
  openInvitePoster();
}

// 邀请有礼：生成本店专属推广二维码海报（本地 canvas 合成，可保存/分享）
async function openInvitePoster() {
  // 分享前必须登录（否则海报/分享里 inviter 为空，推广关系绑不上）→ 跳登录
  if (!user.isLogin) return requireLogin();
  // #ifdef MP-WEIXIN
  // 小程序：请求后端合成「小程序码海报」——别人扫码直接打开本小程序、落到本店主页并绑推广。
  posterOpen.value = true;
  posterImage.value = '';
  posterLoading.value = true;
  try {
    const dataUrl = await request({
      url: `/app-api/merchant/mini/shop-poster?tenantId=${route.tenantId || ''}`,
    });
    posterImage.value = (typeof dataUrl === 'string' && dataUrl) ? dataUrl : '';
  } catch {
    posterImage.value = '';
  } finally {
    posterLoading.value = false;
  }
  return;
  // #endif
  posterOpen.value = true;
  posterImage.value = '';
  posterLoading.value = true;
  try {
    const spu = signatureSpu.value || null;
    let n = spu?.tuijianN || 0;
    let stepPoints, totalPoints;
    if (spu && n > 0) {
      let ratios = [];
      try { ratios = spu.tuijianRatios ? JSON.parse(spu.tuijianRatios) : []; } catch {}
      if (!Array.isArray(ratios) || ratios.length !== n) ratios = Array.from({ length: n }, () => 100 / n);
      const totalFen = Number(spu.price) || 0;
      const avgRatio = ratios.reduce((s, r) => s + Number(r || 0), 0) / n;
      const sumRatio = ratios.reduce((s, r) => s + Number(r || 0), 0);
      stepPoints = fen2yuan(Math.floor(totalFen * avgRatio / 100), false);
      totalPoints = fen2yuan(Math.floor(totalFen * sumRatio / 100), false);
    }
    posterImage.value = await buildInvitePoster({
      shopName: shop.value?.shopName || shop.value?.name || '本店',
      inviteLink: inviteLink(),
      inviter: user.nickname || (user.phone ? user.phone.slice(-4) : '') || '邀三惠用户',
      spuName: spu?.name || spu?.spuName,
      spuPic: spu?.picUrl || spu?.spuPic,
      priceYuan: spu ? fen2yuan(spu.price || 0, false) : null,
      n: n > 0 ? n : undefined,
      stepPoints,
      totalPoints,
    });
  } catch {
    posterImage.value = '';
  } finally {
    posterLoading.value = false;
  }
}

// #ifdef MP-WEIXIN
// 小程序原生分享：好友/群点开卡片 → 直接打开本小程序落到「这个商家主页」，带 inviter 绑推广。
function shareTitle() {
  return (shop.value?.shopName || shop.value?.name || '这家店') + ' · 扫码进店下单赚推广积分';
}
function sharePath() {
  return `/pages/shop/home?tenantId=${route.tenantId || ''}&inviter=${user.userId || ''}`;
}
onShareAppMessage(() => ({ title: shareTitle(), path: sharePath() }));
onShareTimeline(() => ({
  title: (shop.value?.shopName || shop.value?.name || '这家店') + ' · 邀三惠',
  query: `tenantId=${route.tenantId || ''}&inviter=${user.userId || ''}`,
}));
// #endif

function onSavePoster() {
  if (!posterImage.value) return;
  // #ifdef H5
  const ok = downloadDataUrl(posterImage.value, `invite-${route.tenantId || ''}.png`);
  uni.showToast({ title: ok ? '已保存/下载' : '请长按上图保存', icon: 'none' });
  // #endif
  // #ifdef MP-WEIXIN
  // 后端返回的是 base64 data URL，不能直接当 filePath；先解码写成临时 png 再存相册。
  saveDataUrlToAlbumMp(posterImage.value);
  // #endif
  // #ifndef H5
  // #ifndef MP-WEIXIN
  uni.saveImageToPhotosAlbum({
    filePath: posterImage.value,
    success: () => uni.showToast({ title: '已保存到相册', icon: 'success' }),
    fail: () => uni.showToast({ title: '请长按上图保存', icon: 'none' }),
  });
  // #endif
  // #endif
}

// #ifdef MP-WEIXIN
// 把 data:image/png;base64,xxx 写成临时文件再保存相册（微信不允许 base64 直接 save）
function saveDataUrlToAlbumMp(dataUrl) {
  try {
    const base64 = String(dataUrl).replace(/^data:image\/\w+;base64,/, '');
    const buffer = uni.base64ToArrayBuffer(base64);
    const fs = uni.getFileSystemManager();
    const filePath = `${wx.env.USER_DATA_PATH}/invite-${route.tenantId || 'shop'}-${Date.now()}.png`;
    fs.writeFile({
      filePath,
      data: buffer,
      encoding: 'binary',
      success: () => {
        uni.saveImageToPhotosAlbum({
          filePath,
          success: () => uni.showToast({ title: '已保存到相册', icon: 'success' }),
          fail: (e) => {
            // 用户拒绝相册授权时引导开设置
            if (e && /auth deny|authorize/i.test(e.errMsg || '')) {
              uni.showModal({ title: '需要相册权限', content: '请在设置中开启保存到相册权限', showCancel: true, confirmText: '去设置',
                success: (r) => { if (r.confirm) uni.openSetting(); } });
            } else {
              uni.showToast({ title: '请长按上图保存', icon: 'none' });
            }
          },
        });
      },
      fail: () => uni.showToast({ title: '请长按上图保存', icon: 'none' }),
    });
  } catch {
    uni.showToast({ title: '请长按上图保存', icon: 'none' });
  }
}
// #endif

// 分享海报：H5 优先 Web Share（带图片文件），不支持则提示长按转发
async function onSharePoster() {
  if (!posterImage.value) return;
  try {
    if (typeof navigator !== 'undefined' && navigator.share) {
      const title = (shop.value?.shopName || '本店') + ' · 邀三惠';
      // 能分享文件就带海报图，否则退化为分享链接
      if (navigator.canShare && typeof fetch !== 'undefined') {
        try {
          const blob = await (await fetch(posterImage.value)).blob();
          const file = new File([blob], 'invite-poster.png', { type: 'image/png' });
          if (navigator.canShare({ files: [file] })) {
            await navigator.share({ title, files: [file] });
            return;
          }
        } catch { /* 文件分享失败 → 退化为链接分享 */ }
      }
      await navigator.share({ title, text: '扫码进店参与营销优惠', url: inviteLink() });
      return;
    }
  } catch { return; /* 用户取消 */ }
  uni.showToast({ title: '请长按上图转发给好友', icon: 'none' });
}

function closePoster() {
  posterOpen.value = false;
  posterImage.value = '';
}
async function onAddCart(p) {
  if (!user.isLogin) return requireLogin();
  if (!p.skuIds || !p.skuIds.length) return goProduct(p);
  try {
    await addCart(p.skuIds[0], 1);
    uni.showToast({ title: '已加入', icon: 'success' });
    await loadCart();
  } catch {}
}
async function onAddSignature() {
  if (!signatureSpu.value) return;
  await onAddCart(signatureSpu.value);
}
async function onGrabCoupon(c) {
  if (!user.isLogin) return requireLogin();
  if (c.taken) return uni.showToast({ title: '已领取', icon: 'none' });
  try {
    await takeCoupon(c.id);
    c.taken = true;
    uni.showToast({ title: '领取成功', icon: 'success' });
  } catch {}
}
function requireLogin() {
  // 登录后跳回本店主页；带 tenantId + inviter，保住推广绑定。
  // ⚠ 小程序无 localStorage，必须用 uni.setStorageSync（H5 也兼容），否则登录后回不来。
  const target = `/pages/shop/home?tenantId=${route.tenantId || ''}${route.inviter ? `&inviter=${route.inviter}` : ''}`;
  try { if (typeof localStorage !== 'undefined') localStorage.setItem('redirect:after-login', target); } catch {}
  try { uni.setStorageSync('redirect:after-login', target); } catch {}
  uni.navigateTo({ url: '/pages/login/index' });
}

async function loadShop() {
  try {
    const info = await getShopInfo({ tenantId: route.tenantId });
    shop.value = info ? {
      ...info,
      // VO 字段是 sales30d / avgRating，模板读 monthSold / rating，需映射（否则月售恒显 0）
      monthSold: info.sales30d != null ? info.sales30d : (info.monthSold != null ? info.monthSold : 0),
      rating: info.avgRating || info.rating || null,
      slogan: info.slogan || info.shopDesc || info.introduction || '',
      tagRow: info.tagRow || (info.starLevel ? `⭐ ${info.starLevel} 星店铺` : ''),
      ratingCount: info.ratingCount || info.commentCount || 0,
      // 商户写的「特色标签」CSV（炭火现烤,现做现卖…）→ 用户侧标签展示
      featureTagList: String(info.featureTags || '').split(/[,，、]/).map((t) => t.trim()).filter(Boolean).slice(0, 6),
    } : null;
  } catch {}
}

async function loadProducts() {
  loading.value = true;
  try {
    const r = await listShopProducts(route.tenantId, 1, 50);
    const raw = (r?.list || []);

    // 批量补 tuijianN / tuijianRatios / starCount —— ProductSpuDO 不含营销字段
    const spuIds = raw.map((s) => s.id).filter(Boolean);
    let cfgMap = {};
    if (spuIds.length) {
      try {
        const cfgs = await request({
          url: `/app-api/merchant/mini/promo/product-configs?spuIds=${spuIds.join(',')}`,
        });
        if (Array.isArray(cfgs)) {
          for (const c of cfgs) { if (c && c.spuId) cfgMap[c.spuId] = c; }
        }
      } catch {}
    }

    const list = raw.map((s) => {
      const cfg = cfgMap[s.id] || {};
      const tuijianN = cfg.tuijianN || 0;
      const tuijianRatios = cfg.tuijianRatios || null;
      const starCount = cfg.starCount || 0;
      let badge = '', badgeClass = '';
      if (tuijianN > 0) { badge = `推 ${tuijianN} 反 1`; }
      else if (starCount) { badge = '优惠商品'; badgeClass = 'gold'; }
      return reactive({ ...s, tuijianN, tuijianRatios, starCount, badge, badgeClass, imgErr: false });
    });
    spus.value = list;

    // 招牌：优先 tuijianN>0，否则销量/价格
    const sig = list.find((s) => s.tuijianN > 0)
              || list.slice().sort((a, b) => (b.salesCount || 0) - (a.salesCount || 0))[0]
              || list.slice().sort((a, b) => (b.price || 0) - (a.price || 0))[0];
    signatureSpu.value = sig ? reactive({ ...sig, imgErr: false }) : null;

    // 聚合分类 — 先拿出商品涉及的 categoryId 集合
    const idsInUse = new Set();
    for (const s of list) if (s.categoryId) idsInUse.add(s.categoryId);

    // 拉本店真实分类树（带 tenantId 走本店命名空间，避免跨租户取错名）
    let nameMap = {};
    try {
      const tree = await listCategories(route.tenantId);
      const flat = [];
      const walk = (arr) => {
        if (!Array.isArray(arr)) return;
        for (const n of arr) {
          flat.push(n);
          if (n.children) walk(n.children);
        }
      };
      walk(tree);
      for (const n of flat) if (n.id && n.name) nameMap[n.id] = n.name;
    } catch {}

    // 拼真名 + 兜底
    const catList = [];
    for (const id of idsInUse) {
      catList.push({ id, name: nameMap[id] || `分类 ${id}` });
    }
    cats.value = catList;
  } catch {} finally { loading.value = false; }
}

// 构造 nback 视图模型；优先用真实 ratios 数组，没有就按 1/N 均分
function buildNback(n, cur, totalFen, ratiosJson) {
  let ratios = [];
  try { ratios = ratiosJson ? JSON.parse(ratiosJson) : []; } catch {}
  if (!Array.isArray(ratios) || ratios.length !== n) {
    // 兜底：均匀分布
    ratios = Array.from({ length: n }, () => 100 / n);
  }
  // 单步均值 = totalFen × 平均比例 / 100
  const avgRatio = ratios.reduce((s, r) => s + Number(r || 0), 0) / n;
  const stepFen = Math.floor(totalFen * avgRatio / 100);
  // 累计已得 = 前 cur 个比例之和 × totalFen
  let gotFen = 0;
  for (let i = 0; i < cur && i < ratios.length; i++) {
    gotFen += Math.floor(totalFen * Number(ratios[i] || 0) / 100);
  }
  const sumRatio = ratios.reduce((s, r) => s + Number(r || 0), 0);
  const totalRebateFen = Math.floor(totalFen * sumRatio / 100);
  const gapFen = Math.max(0, totalRebateFen - gotFen);
  const pct = n > 0 ? Math.min(100, Math.round((cur / n) * 100)) : 0;
  return {
    n, cur, pct,
    // 1 积分 = 1 元；底层存的是 fen，显示要 /100（保留 2 位小数）
    stepPoints: fen2yuan(stepFen, false),
    gotPoints: fen2yuan(gotFen, false),
    gapPoints: fen2yuan(gapFen, false),
    totalPoints: fen2yuan(totalRebateFen, false),
  };
}

async function loadNback() {
  if (!user.isLogin || !route.tenantId || !signatureSpu.value) {
    // 即使没登录，也展示骨架（基于 signature 的 tuijianN / tuijianRatios）
    if (signatureSpu.value?.tuijianN) {
      const totalFen = signatureSpu.value.price || 0;
      nback.value = buildNback(signatureSpu.value.tuijianN, 0, totalFen, signatureSpu.value.tuijianRatios);
    } else nback.value = null;
    return;
  }
  try {
    const list = await request({ url: `/app-api/merchant/mini/promo/my-spu-stars?tenantId=${route.tenantId}` });
    const target = (Array.isArray(list) ? list : []).find((x) => x.spuId === signatureSpu.value.id);
    if (target && target.tuijianN > 0) {
      const n = target.tuijianN;
      const cur = Math.min(target.directCount || 0, n);
      const totalFen = signatureSpu.value.price || target.spuPrice || 0;
      nback.value = buildNback(n, cur, totalFen, target.tuijianRatios);
    } else if (signatureSpu.value.tuijianN) {
      const totalFen = signatureSpu.value.price || 0;
      nback.value = buildNback(signatureSpu.value.tuijianN, 0, totalFen, signatureSpu.value.tuijianRatios);
    } else {
      nback.value = null;
    }
  } catch {}
}

async function loadVip() {
  if (!user.isLogin || !route.tenantId) { vip.myStar = 0; vip.earnYuan = '0.00'; return; }
  try {
    const rel = await getMyRel(route.tenantId);
    if (rel) {
      vip.myStar = rel.star || 0;
      vip.discountText = rel.discount ? `${(rel.discount * 10).toFixed(0)}` : '';
      isFav.value = !!rel.favorite; // 收藏状态来自后端 member_shop_rel.favorite
    }
  } catch {}
  // "你已赚" / "在该店赚" = 推广积分 lifetime（累计 amount > 0 的流水，含已抵扣/转换/提现），
  // 而非剩余余额；按 fen 转 ¥ 显示
  try {
    const earn = await getMyPromoEarned(route.tenantId);
    const fen = earn?.lifetimeEarnedFen || 0;
    vip.earnYuan = fen2yuan(fen, false);
    myEarn.value = vip.earnYuan;
  } catch {
    vip.earnYuan = '0.00';
    myEarn.value = '0.00';
  }
  try {
    const ref = await request({ url: `/app-api/merchant/mini/promo/referral/my-children-count?tenantId=${route.tenantId}` });
    vip.inviterCount = ref || 0;
  } catch {}
}

async function loadCoupons() {
  try {
    const list = await listCouponTemplates({ count: 5 });
    coupons.value = (list || []).slice(0, 5).map((c) => ({ ...c, taken: false }));
  } catch {}
}

async function loadVisitor() {
  if (!route.tenantId) return;
  try {
    const r = await getShopVisitorCount(route.tenantId);
    visitorCount.value = r?.count || r || 0;
  } catch {}
}

async function loadTicker() {
  try {
    const list = await listWinners(route.tenantId, 10);
    tickerText.value = (list || []).map((w) => {
      const amt = fen2yuan(w.amount, false);
      return `${w.userMask || '****'} ${w.sourceLabel || '促销优惠'} +¥${amt}`;
    });
  } catch {}
}

async function loadCart() {
  if (!user.isLogin) { cartCount.value = 0; cartTotalFen.value = 0; return; }
  try {
    cartCount.value = (await getCartCount()) || 0;
    if (cartCount.value === 0) { cartTotalFen.value = 0; cartHint.value = ''; return; }
    const r = await listCart();
    const items = (r?.validList || r?.list || []).filter((i) => i.selected !== false);
    const total = items.reduce((s, i) => s + (i.price || 0) * (i.count || 1), 0);
    cartTotalFen.value = total;
    // 简单满减提示：差到 30/50/100 的最近门槛
    const milestones = [3000, 5000, 10000]; // 30 / 50 / 100
    const next = milestones.find((m) => total < m);
    cartHint.value = next ? `差 ¥${fen2yuan(next - total, false)} 享满 ${next/100} 减优惠` : '';
  } catch {}
}

function refreshAll() {
  loadShop();
  loadProducts().then(loadNback);
  loadVip();
  loadCoupons();
  loadVisitor();
  loadTicker();
  loadCart();
}

onMounted(() => {
  try { statusH.value = uni.getSystemInfoSync().statusBarHeight || 20; } catch {}
  // V044 推广绑定：onLoad 时若 URL 有 inviter 参数，先存 pending
  // （兜底：万一 App.vue 没抓到，比如直接 navigate 进来）
  if (route.inviter && route.tenantId) {
    savePendingReferrer(route.inviter, route.tenantId);
  }
  // 已登录用户进店：立即尝试 flush（已绑会被后端拒，幂等安全）
  if (user.isLogin && user.userId && route.tenantId) {
    flushPendingReferrer(user.userId, route.tenantId).catch(() => {});
  }
  refreshAll();
});
onShow(refreshAll);
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.page { min-height: 100vh; background: $bg-2; padding-bottom: 90px; }

/* ━━ Cover ━━ */
.sh-cover {
  position: relative; height: 200px;
  background: linear-gradient(135deg, #FFB174 0%, $o 50%, $o-d 100%);
  overflow: hidden;
}
.sh-cover-bg {
  position: absolute; inset: 0;
  background-image: radial-gradient(rgba(255,255,255,.06) 1px, transparent 1px);
  background-size: 22px 22px;
}
.sh-cover-mark {
  position: absolute; bottom: -20px; right: -10px;
  font-size: 180px; opacity: .12;
  transform: rotate(-15deg); line-height: 1;
}
.sh-nav {
  position: absolute; top: 0; left: 0; right: 0;
  padding: 8px 14px;
  display: flex; justify-content: space-between; align-items: center;
  z-index: 5;
}
.sh-nav-right { display: flex; gap: 8px; }
.sh-ic {
  width: 36px; height: 36px; border-radius: 12px;
  background: rgba(0,0,0,.25);
  backdrop-filter: blur(10px);
  color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 16px;
  border: 1px solid rgba(255,255,255,.15);
}
.sh-info {
  position: absolute; bottom: 50px; left: 18px; right: 18px;
  color: #fff; z-index: 4;
}
.sh-tag-row {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 4px 10px; border-radius: 99px;
  background: rgba(0,0,0,.3); backdrop-filter: blur(10px);
  font-size: 10px; font-weight: 700;
  border: 1px solid rgba(255,255,255,.2);
}
.sh-name {
  font-size: 24px; font-weight: 900; margin-top: 8px;
  text-shadow: 0 2px 8px rgba(0,0,0,.15);
}
.sh-slogan { font-size: 13px; opacity: .9; margin-top: 2px; }
.sh-feats { display: flex; flex-wrap: wrap; gap: 8rpx; margin-top: 12rpx; }
.sh-feat {
  font-size: 11px; line-height: 1.2; color: #fff; font-weight: 600;
  padding: 4rpx 14rpx; border-radius: 999rpx;
  background: rgba(255,255,255,.22); backdrop-filter: blur(4px);
  border: 1rpx solid rgba(255,255,255,.35);
}

/* ━━ Info card ━━ */
.sh-info-card {
  margin: -36px 14px 0;
  background: $card; border-radius: $r-lg;
  padding: 16px; box-shadow: $sh-2;
  border: 1px solid $line;
  position: relative; z-index: 6;
}
.sh-stat-row { display: flex; align-items: center; }
.sh-stat { flex: 1; text-align: center; }
.sh-stat .v { font-size: 17px; font-weight: 800; color: $t1; line-height: 1; }
.sh-stat .v.gold { color: $gold; }
.sh-stat .v.brand {
  background: linear-gradient(135deg, $o, $o-d);
  -webkit-background-clip: text; background-clip: text; color: transparent;
}
.sh-stat .v .star { color: $gold; margin-right: 2px; }
.sh-stat .l { font-size: 11px; color: $t3; margin-top: 4px; font-weight: 500; }
.sh-stat-divider { width: 1px; align-self: stretch; background: $line; }

/* ━━ 商家资质 ━━ */
.sh-qual {
  margin: 10px 14px 0;
  background: $card; border-radius: $r-md;
  padding: 12px 14px; box-shadow: $sh-1;
  border: 1px solid $line;
  display: flex; align-items: center; gap: 10px;
}
.sh-qual .q-ic { font-size: 20px; flex: none; }
.sh-qual .q-body { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.sh-qual .q-t { font-size: 14px; font-weight: 700; color: $t1; }
.sh-qual .q-sub { font-size: 11px; color: $t3; margin-top: 2px; }
.sh-qual .q-arr { font-size: 12px; color: $o; font-weight: 700; flex: none; }

/* ━━ Signature card ━━ */
.signature-card {
  margin: 14px 14px 0;
  background: linear-gradient(135deg, #FFF8F4 0%, #FFEFE3 100%);
  border: 2px solid $o-100;
  border-radius: $r-xl;
  padding: 14px;
  position: relative; overflow: hidden;
  box-shadow: 0 8px 24px rgba(255,107,53,.18);
}
.sig-crown {
  position: absolute; top: -2px; left: 14px;
  padding: 5px 12px;
  background: linear-gradient(135deg, $gold, $gold-d);
  color: #fff; font-size: 10px; font-weight: 800;
  border-radius: 0 0 8px 8px;
  box-shadow: 0 4px 12px rgba(212,146,10,.4);
  z-index: 2;
}
.sig-inner {
  margin-top: 14px;
  display: flex; gap: 12px; align-items: stretch;
}
.sig-pic {
  flex: 0 0 100px; width: 100px; height: 100px;
  border-radius: $r-lg;
  background: linear-gradient(135deg, #FFE0D1, $o-l);
  display: flex; align-items: center; justify-content: center;
  position: relative; overflow: hidden;
  box-shadow: 0 4px 12px rgba(255,107,53,.25);
}
.sig-pic-em { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; font-size: 56px; line-height: 1; }
.sig-pic-img { position: absolute; inset: 0; width: 100%; height: 100%; z-index: 1; }
.sig-body { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.sig-name { font-size: 15px; font-weight: 800; color: $t1; line-height: 1.3; overflow: hidden; }
.sig-intro { font-size: 11px; color: $t3; margin-top: 3px; }
.sig-tags { margin-top: 6px; display: flex; gap: 4px; flex-wrap: wrap; }
.sig-tag {
  font-size: 10px; padding: 2px 7px; border-radius: 4px; font-weight: 700;
}
.sig-tag.promo  { background: linear-gradient(135deg, $o, $o-d); color: #fff; }
.sig-tag.gold   { background: linear-gradient(135deg, $gold, $gold-l); color: #fff; }
.sig-tag.got    { background: $mint-50; color: $mint; }
.sig-tag.amount { background: $o-50; color: $o-d; border: 1px solid $o-100; }
.sig-bot { margin-top: auto; display: flex; justify-content: space-between; align-items: flex-end; }
.sig-price-block { display: flex; flex-direction: column; align-items: flex-start; }
.sig-price {
  font-size: 22px; font-weight: 900;
  background: linear-gradient(135deg, $o, $o-d);
  -webkit-background-clip: text; background-clip: text; color: transparent;
  line-height: 1;
}
.sig-orig { font-size: 11px; color: $t4; text-decoration: line-through; margin-top: 2px; }
.sig-add {
  padding: 8px 16px;
  background: linear-gradient(135deg, $o, $o-d);
  color: #fff; border-radius: 99px;
  font-size: 12px; font-weight: 800;
  box-shadow: $sh-warm;
}
.sig-progress {
  margin-top: 10px;
  height: 28px;
  background: rgba(255,255,255,.5);
  border-radius: 14px;
  padding: 3px;
  position: relative; overflow: hidden;
  border: 1px solid $o-100;
}
.sig-fill {
  height: 22px;
  background: linear-gradient(90deg, $o, $o-l, $gold);
  border-radius: 11px;
  box-shadow: 0 0 12px rgba(255,107,53,.5);
  transition: width .4s;
}
.sig-progress-txt {
  position: absolute; left: 50%; top: 50%; transform: translate(-50%, -50%);
  font-size: 10.5px; font-weight: 800; color: #fff;
  text-shadow: 0 1px 2px rgba(0,0,0,.3); z-index: 2;
  white-space: nowrap;
}

/* ━━ 规则说明（展开式）━━ */
.sig-rule {
  margin-top: 10px;
  background: rgba(255,255,255,.7);
  border: 1px dashed $o-200;
  border-radius: $r-md;
  overflow: hidden;
}
.sig-rule-head {
  display: flex; align-items: center; gap: 6px;
  padding: 9px 12px;
  font-size: 11.5px; font-weight: 800;
  color: $t1;
}
.sig-rule-head .em { font-size: 14px; }
.sig-rule-head .t { flex: 1; }
.sig-rule-head .arr { font-size: 10.5px; color: $o-d; font-weight: 700; }

.sig-rule-body {
  padding: 0 12px 12px;
  border-top: 1px dashed $o-100;
}
.step-grid {
  margin-top: 10px;
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px;
}
.step {
  padding: 8px 4px;
  background: #fff;
  border: 1px solid $o-100;
  border-radius: $r-md;
  text-align: center;
}
.step-em { font-size: 22px; line-height: 1; }
.step-t  { font-size: 10.5px; font-weight: 800; color: $t1; margin-top: 4px; }
.step-d  { font-size: 9.5px; color: $t3; margin-top: 2px; line-height: 1.25; }

.rule-list { margin-top: 10px; }
.rule-item {
  font-size: 11px; color: $t2; line-height: 1.55;
  padding: 4px 0;
}
.rule-item .b { color: $t1; font-weight: 800; }
.rule-item .hl { color: $o; font-weight: 900; }
.rule-item .usage { color: $o-d; font-weight: 700; }

.rule-foot {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed $o-100;
  font-size: 10px; color: $t4; text-align: center;
}

/* ━━ VIP strip ━━ */
.vip-strip {
  margin: 12px 14px 0;
  padding: 12px 14px;
  background: linear-gradient(135deg, $gold-50, $o-50);
  border: 1px solid rgba(212,146,10,.25);
  border-radius: $r-lg;
  display: flex; align-items: center; gap: 10px;
}
.vip-em {
  width: 36px; height: 36px; border-radius: 10px;
  background: linear-gradient(135deg, $gold, $gold-l);
  color: #fff; display: flex; align-items: center; justify-content: center;
  font-size: 18px; box-shadow: 0 4px 10px rgba(212,146,10,.3);
}
.vip-body { flex: 1; min-width: 0; }
.vip-t { font-size: 13px; font-weight: 800; color: $t1; }
.vip-t .b { color: $gold-d; }
.vip-d { font-size: 11px; color: $t3; margin-top: 2px; }
.vip-cta { color: $o-d; font-size: 11px; font-weight: 800; }

/* ━━ Coupon strip ━━ */
.coupon-strip {
  margin: 12px 0 0;
  padding: 0 14px;
  white-space: nowrap;
}
.coupon-card {
  display: inline-flex; vertical-align: top;
  width: 160px; height: 64px;
  margin-right: 8px;
  background:
    radial-gradient(circle at 76px center, transparent 6px, $card 6.5px);
  border-radius: 8px;
  border: 1px dashed $o-200;
  align-items: center;
}
.coupon-card.taken { opacity: .6; }
.coupon-amt { width: 76px; padding: 8px 4px; text-align: center; color: $o-d; }
.coupon-amt .v { font-size: 22px; font-weight: 900; line-height: 1; }
.coupon-amt .v .c { font-size: 12px; margin-right: 1px; }
.coupon-amt .cond { font-size: 9px; margin-top: 2px; }
.coupon-divider { width: 1px; height: 50%; border-left: 1px dashed $o-200; }
.coupon-action { flex: 1; padding: 0 10px; }
.coupon-action .grab {
  background: $o; color: #fff;
  padding: 4px 10px; border-radius: 99px;
  font-size: 11px; font-weight: 700; text-align: center;
}

/* ━━ Social proof ━━ */
.social-proof {
  margin: 10px 14px 0;
  padding: 8px 14px;
  background: $mint-50;
  border-left: 3px solid $mint;
  border-radius: 0 8px 8px 0;
  font-size: 11px; color: #064E3B;
  display: flex; align-items: center; gap: 6px;
}
.social-proof .num { color: $mint; font-weight: 800; }

/* ━━ Ticker ━━ */
.sh-ticker {
  margin: 12px 14px 0;
  padding: 10px 14px;
  background: linear-gradient(90deg, $gold-50, $o-50);
  border: 1px solid $o-100;
  border-radius: 12px;
  display: flex; align-items: center; gap: 10px;
  overflow: hidden;
}
.sh-ticker .em { font-size: 16px; flex-shrink: 0; }
.ticker-arrow { color: $o-d; font-size: 16px; font-weight: 700; flex-shrink: 0; margin-left: 4px; }
.sh-ticker .roll { flex: 1; overflow: hidden; height: 18px; position: relative; }
.sh-ticker .roll-track {
  position: absolute; white-space: nowrap;
  font-size: 11.5px; color: $gold-d; font-weight: 600;
  animation: rollx 22s linear infinite;
}
@keyframes rollx { 0% { transform: translateX(100%); } 100% { transform: translateX(-100%); } }

/* ━━ Promise ━━ */
.promise-card {
  margin: 12px 14px 0;
  padding: 14px;
  background: linear-gradient(135deg, $mint-50, #fff);
  border: 1px solid rgba(16,185,129,.2);
  border-radius: $r-md;
  display: flex; gap: 12px;
}
.promise-ic {
  width: 36px; height: 36px; border-radius: 11px;
  background: $mint; color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 16px; flex-shrink: 0;
  box-shadow: 0 4px 10px rgba(16,185,129,.3);
}
.promise-body { flex: 1; }
.promise-t { font-size: 12px; font-weight: 800; color: #064E3B; }
.promise-d { font-size: 11px; color: #047857; margin-top: 4px; line-height: 1.6; }
.promise-d .b { font-weight: 800; }

/* ━━ 商品区：美团风 左侧分类 tab + 右侧商品大卡 ━━ */
.loading { padding: 40px; text-align: center; color: $t4; }
.mt-products {
  margin: 14px 14px 0;
  display: flex;
  background: $card;
  border-radius: $r-lg;
  overflow: hidden;
  box-shadow: $sh-1;
  height: 600px;  /* 撑出滚动区，足够展示一段商品 */
}
.mt-side {
  width: 90px; flex-shrink: 0;
  background: $bg-2;
  border-right: 1px solid $line;
}
.mt-side-item {
  position: relative;
  padding: 14px 6px;
  text-align: center;
  font-size: 12.5px; color: $t2;
  font-weight: 600;
  word-break: break-all;
}
.mt-side-item.on {
  background: $card;
  color: $o-d;
  font-weight: 800;
}
.mt-side-item .dot {
  position: absolute; left: 0; top: 50%;
  transform: translateY(-50%);
  width: 3px; height: 20px;
  background: linear-gradient(180deg, $o, $o-d);
  border-radius: 0 2px 2px 0;
}

.mt-main { flex: 1; padding: 10px 12px; min-width: 0; }
.mt-empty { padding: 60px 0; text-align: center; color: $t4; font-size: 13px; }

.mt-card {
  display: flex; gap: 10px;
  padding: 10px 0;
  border-bottom: 1px dashed $line;
}
.mt-card:last-child { border-bottom: none; }

.mt-pic {
  width: 88px; height: 88px;
  flex-shrink: 0;
  border-radius: $r-md;
  background: linear-gradient(135deg, #FFF5EB, #FFE9D5);
  position: relative; overflow: hidden;
  display: flex; align-items: center; justify-content: center;
}
.mt-pic-img { width: 100%; height: 100%; }
.mt-pic-em { font-size: 36px; }
.mt-badge {
  position: absolute; top: 4px; left: 4px;
  padding: 2px 6px;
  background: linear-gradient(135deg, $o, $o-d);
  color: #fff; border-radius: 4px;
  font-size: 9px; font-weight: 800;
  box-shadow: 0 2px 4px rgba(255,107,53,.3);
}
.mt-badge.gold { background: linear-gradient(135deg, $gold, $gold-l); }

.mt-info {
  flex: 1; min-width: 0;
  display: flex; flex-direction: column; gap: 4px;
}
.mt-name {
  font-size: 14px; font-weight: 800; color: $t1;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.mt-intro {
  font-size: 11px; color: $t3;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.mt-meta {
  display: flex; gap: 8px; font-size: 11px; color: $t3;
  font-variant-numeric: tabular-nums;
}
.mt-rate { color: $gold-d; font-weight: 700; }

.mt-foot {
  margin-top: auto;
  display: flex; align-items: flex-end; justify-content: space-between; gap: 6px;
}
.mt-price-block { display: flex; align-items: baseline; gap: 4px; }
.mt-cny { font-size: 12px; font-weight: 800; color: $o-d; }
.mt-price {
  font-size: 18px; font-weight: 900; color: $o-d;
  font-variant-numeric: tabular-nums;
}
.mt-mkt {
  font-size: 11px; color: $t4; text-decoration: line-through;
  font-variant-numeric: tabular-nums;
}
.mt-add {
  flex-shrink: 0;
  padding: 5px 12px;
  background: linear-gradient(135deg, $o, $o-d); color: #fff;
  border-radius: 99px;
  font-size: 11.5px; font-weight: 800;
  box-shadow: $sh-warm;
}

/* ━━ Cart bar ━━ */
.cart-bar {
  position: fixed; bottom: 0; left: 0; right: 0;
  padding: 10px 14px 18px;
  padding-bottom: calc(18px + env(safe-area-inset-bottom));
  background: rgba(255,255,255,.96);
  backdrop-filter: blur(20px);
  border-top: 1px solid $line;
  display: flex; align-items: center; gap: 12px;
  z-index: 50;
}
.cart-bar-ic {
  width: 44px; height: 44px; border-radius: 50%;
  background: $bg-2;
  display: flex; align-items: center; justify-content: center;
  font-size: 22px; position: relative;
}
.cart-bar-ic .badge {
  position: absolute; top: -2px; right: -2px;
  min-width: 18px; height: 18px; padding: 0 5px;
  background: $danger; color: #fff;
  border-radius: 99px;
  font-size: 10px; font-weight: 800;
  display: flex; align-items: center; justify-content: center;
  border: 2px solid #fff;
}
.cart-bar-info { flex: 1; }
.cart-bar-price { font-size: 18px; font-weight: 900; color: $o-d; }
.cart-bar-d { font-size: 10px; color: $t3; margin-top: 2px; }
.cart-bar-pay {
  height: 42px; padding: 0 20px; border-radius: 99px;
  background: linear-gradient(135deg, $o, $o-d); color: #fff;
  display: flex; align-items: center; font-weight: 800; font-size: 14px;
  box-shadow: $sh-warm;
}

.bottom-pad { height: 20px; }

/* ━━ 邀请有礼海报弹层 ━━ */
.poster-mask {
  position: fixed; inset: 0; z-index: 999;
  background: rgba(0,0,0,.78);
  display: flex; align-items: center; justify-content: center;
  padding: 36rpx; overflow-y: auto;
}
.poster-wrap { width: 100%; max-width: 720rpx; display: flex; flex-direction: column; align-items: center; }
.poster-img {
  width: 100%; max-width: 640rpx; border-radius: 24rpx;
  box-shadow: 0 16px 48px rgba(0,0,0,.4); background: #FFF9F0;
}
.poster-loading {
  width: 100%; max-width: 640rpx; height: 900rpx; border-radius: 24rpx;
  background: rgba(255,255,255,.1);
  display: flex; align-items: center; justify-content: center;
  color: rgba(255,255,255,.85); font-size: 28rpx;
}
.poster-actions { margin-top: 28rpx; display: flex; gap: 20rpx; width: 100%; }
.poster-btn {
  flex: 1; padding: 26rpx; text-align: center; border-radius: 999rpx;
  font-size: 28rpx; font-weight: 800;
}
.poster-btn.primary {
  background: linear-gradient(135deg, $o, $o-d); color: #fff;
  box-shadow: 0 8rpx 28rpx rgba(255,107,53,.4);
}
.poster-btn.ghost {
  background: rgba(255,255,255,.18); color: #fff;
  border: 2rpx solid rgba(255,255,255,.3);
}
.poster-hint { margin-top: 20rpx; font-size: 22rpx; color: rgba(255,255,255,.7); text-align: center; }

/* 分享浮标：醒目橙色药丸，固定右下、避开右上角胶囊 */
.sh-share-fab {
  position: fixed;
  right: 20rpx;
  bottom: 200rpx;
  z-index: 90;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 76rpx;
  padding: 0 30rpx;
  border-radius: 40rpx;
  background: linear-gradient(135deg, #ff6b35, #ff9b5e);
  color: #fff;
  font-size: 27rpx;
  font-weight: 700;
  line-height: 76rpx;
  box-shadow: 0 10rpx 26rpx rgba(255,107,53,.45);
  border: none;
}
.sh-share-fab::after { border: none; }
</style>
