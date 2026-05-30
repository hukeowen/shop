<template>
  <view class="page">
    <!-- ━━━━━━━━━━ Hero ━━━━━━━━━━ -->
    <view class="me-hero">
      <view class="me-hero-bg"></view>
      <view class="me-top-row">
        <view class="me-avatar">{{ avatarText }}</view>
        <view class="me-info">
          <view class="me-name-row">
            <view v-if="user.isLogin" class="me-name">{{ user.nickname || user.phone?.slice(-4) || '客小二' }}</view>
            <view v-else class="me-name" @click="goLogin">点我登录 ›</view>
            <view v-if="maxStar > 0" class="me-star">{{ '★'.repeat(maxStar) }} {{ maxStar }} 星</view>
          </view>
          <view v-if="user.isLogin" class="me-phone">
            {{ maskedPhone }} · 已加入 <text class="b">{{ myShops.length }}</text> 家店
          </view>
          <view v-else class="me-phone">登录即解锁推广积分 / 商户让利 / 提现</view>
        </view>
        <view class="me-set">⚙</view>
      </view>

      <view class="me-earn">
        <view class="me-earn-l">累计推广积分（按店申请兑付）</view>
        <view class="me-earn-v">{{ fen2yuan(earnPoints, false) }} <text class="me-earn-u">积分</text></view>
        <view v-if="user.isLogin" class="me-earn-grow">↑ 今日 +{{ fen2yuan(todayEarnPoints, false) }} 积分</view>
        <view class="me-earn-acts">
          <view class="me-earn-act primary" @click="goWithdraw">
            <view class="em">💸</view>
            <view class="l">提现</view>
          </view>
          <view class="me-earn-act" @click="goPromoRecords">
            <view class="em">📊</view>
            <view class="l">推广明细</view>
          </view>
          <view class="me-earn-act" @click="goConsumeRecords">
            <view class="em">🪙</view>
            <view class="l">消费明细</view>
          </view>
        </view>
      </view>
    </view>

    <!-- ━━━━━━━━━━ 3 列跨店统计 — 上拉 -68px 与 hero 重叠 ━━━━━━━━━━ -->
    <view class="me-summary">
      <view class="item"><view class="num">{{ myShops.length }}</view><view class="label">加入店铺</view></view>
      <view class="item"><view class="num">{{ totalOrders }}</view><view class="label">累计订单</view></view>
      <view class="item"><view class="num brand">{{ totalInvited }}</view><view class="label">推荐好友</view></view>
    </view>

    <!-- 距升星 -->
    <view v-if="upgradeInfo" class="me-star-card">
      <view class="msc-head">
        <view class="msc-title">距 <text class="h">{{ upgradeInfo.targetStar }} 星</text> 还差</view>
        <view class="msc-meta">{{ upgradeInfo.fromStar }} 星 / {{ upgradeInfo.maxStar }} 星</view>
      </view>
      <view class="msc-bar"><view class="msc-fill" :style="{ width: upgradeInfo.pct + '%' }"></view></view>
      <view class="msc-progress-lbl">
        <text>直推 <text class="b">{{ upgradeInfo.direct }} / {{ upgradeInfo.directNeed }} 人</text></text>
        <text>团队 <text class="b">¥{{ upgradeInfo.teamYuan }} / ¥{{ upgradeInfo.teamNeedYuan }}</text></text>
      </view>
      <view class="msc-actions">
        <view class="msc-btn p" @click="goInvite">分享邀请 →</view>
        <view class="msc-btn s">看晋级规则</view>
      </view>
    </view>

    <!-- 资产隔离 tip-bar -->
    <view v-if="myShops.length" class="me-tip-bar">
      💡 资产按<text class="b">店铺独立</text>：推广积分 / 消费积分 / 星级 都隔离在每家店账户
    </view>

    <!-- 我加入的店铺 (大卡) -->
    <view v-if="myShops.length" class="section-title">
      <text class="h3">我加入的店铺 <text class="small">{{ myShops.length }} 家 · 资产独立</text></text>
      <text class="more">管理 ›</text>
    </view>
    <view v-for="(s, i) in myShops" :key="s.tenantId || s.id" class="ms-big" :class="{ 'gold-border': s.starLevel >= 3, 'has-queue': s.hasQueue }">
      <view class="msb-head">
        <view class="msb-pic" :class="['', 'alt-1', 'alt-2', 'alt-3'][i % 4]">{{ (s.shopName || s.name || '店')[0] }}</view>
        <view class="msb-name-col">
          <view class="msb-name-row">
            <text class="msb-name">{{ s.shopName || s.name }}</text>
            <view v-if="s.starLevel > 0" class="msb-star">{{ '★'.repeat(s.starLevel) }} {{ s.starLevel }} 星</view>
            <view v-else class="msb-star empty">☆ 暂无</view>
          </view>
          <view class="msb-meta">
            <text v-if="s.lastVisitText">{{ s.lastVisitText }}</text>
            <text v-if="s.starExtraText" class="ok">{{ s.starExtraText }}</text>
          </view>
        </view>
        <view class="msb-enter" @click="goShop(s)">›</view>
      </view>
      <view class="msb-stats">
        <view class="msb-stat" @click.stop="goShopRecords(s, 'promo')">
          <view class="n gold">{{ fen2yuan(s.promoPointsRaw, false) }} <text class="u">积分</text></view>
          <view class="l">推广积分</view>
        </view>
        <view class="msb-stat" @click.stop="goShopRecords(s, 'consume')">
          <view class="n purple">{{ fen2yuan(s.consumePointsRaw, false) }} <text class="u">积分</text></view>
          <view class="l">消费积分</view>
        </view>
        <view class="msb-stat">
          <view class="n" :class="s.queueActive ? 'orange' : 'mute'">{{ s.queueCount || 0 }}</view>
          <view class="l">推 N 反 1</view>
          <text v-if="s.queueActive" class="micro warn">活跃</text>
        </view>
      </view>
      <view v-if="s.queueBar" class="msb-queue-bar">
        <text class="em">🔥</text>
        <view class="t">{{ s.queueBar.text }} <text class="b">{{ s.queueBar.bold }}</text></view>
        <text class="go" @click="goQueue">看队列 ›</text>
      </view>
      <view class="msb-acts">
        <view class="msb-act primary" @click.stop="goShopRecords(s, 'promo')"><text class="em">📊</text>积分明细</view>
        <view class="msb-act" @click.stop="goShop(s)"><text class="em">🏪</text>进店</view>
        <view class="msb-act"
              :class="{ disabled: !s.inviteEligible }"
              @click.stop="onShopInvite(s)">
          <text class="em">🤝</text>{{ s.inviteEligible ? '邀请' : '邀请·待激活' }}
        </view>
      </view>
    </view>

    <!-- ━━━━━━━━━━ 推广海报弹层（按店，canvas 合成整图）━━━━━━━━━━ -->
    <view v-if="posterShop" class="poster-mask" @click="closePoster">
      <view class="poster-wrap" @click.stop>
        <view v-if="posterLoading" class="poster-loading">海报生成中…</view>
        <image v-else-if="posterImage" :src="posterImage" mode="widthFix" class="poster-img" show-menu-by-longpress />
        <view v-else class="poster-loading">海报生成失败，请重试</view>
        <view class="poster-actions">
          <view class="poster-btn primary" @click="onSavePoster">保存海报</view>
          <view class="poster-btn ghost" @click="onCopyPosterLink">复制链接</view>
          <view class="poster-btn ghost" @click="closePoster">关闭</view>
        </view>
        <view class="poster-hint">💡 长按上图可直接保存到相册，或点「保存海报」下载</view>
      </view>
    </view>

    <!-- 资产·推广 -->
    <view class="me-grid">
      <view class="me-grid-title">资产 · 推广</view>
      <view class="me-row" @click="goWallet"><view class="me-row-icon">💰</view><text class="me-row-name">我的钱包（按店铺）</text><text class="me-row-tag">{{ myShops.length }} 家</text><text class="me-row-arrow">›</text></view>
      <view class="me-row" @click="goPromoRecords"><view class="me-row-icon alt-1">📊</view><text class="me-row-name">推广积分明细</text><text class="me-row-arrow">›</text></view>
      <view class="me-row" @click="goQueue"><view class="me-row-icon alt-2">🔥</view><text class="me-row-name">我的队列（推 N 反 1）</text><text v-if="queueTotal" class="me-row-tag">{{ queueTotal }} 个</text><text class="me-row-arrow">›</text></view>
      <view class="me-row" @click="goWinners"><view class="me-row-icon alt-3">⭐</view><text class="me-row-name">店铺星级</text><text class="me-row-arrow">›</text></view>
      <view class="me-row" @click="goWinners"><view class="me-row-icon">🏆</view><text class="me-row-name">商户让利公告 / 榜一排名</text><text class="me-row-arrow">›</text></view>
      <view class="me-row" @click="goInvite"><view class="me-row-icon alt-1">🤝</view><text class="me-row-name">邀请好友（先选店铺）</text><text v-if="totalInvited" class="me-row-tag">已邀 {{ totalInvited }} 人</text><text class="me-row-arrow">›</text></view>
    </view>

    <!-- 收藏·浏览 -->
    <view class="me-grid">
      <view class="me-grid-title">收藏 · 浏览</view>
      <view class="me-row" @click="goFav"><view class="me-row-icon alt-4">❤</view><text class="me-row-name">我收藏的店铺</text><text class="me-row-arrow">›</text></view>
      <view class="me-row" @click="goFav"><view class="me-row-icon alt-1">⭐</view><text class="me-row-name">商品收藏夹</text><text v-if="favCount" class="me-row-tag">{{ favCount }} 件</text><text class="me-row-arrow">›</text></view>
      <view class="me-row" @click="goCoupon"><view class="me-row-icon">🎫</view><text class="me-row-name">我的优惠券</text><text v-if="couponCount" class="me-row-tag">{{ couponCount }} 张</text><text class="me-row-arrow">›</text></view>
    </view>

    <!-- 平台·设置 -->
    <view class="me-grid">
      <view class="me-grid-title">平台 · 设置</view>
      <view class="me-row" @click="goAddress"><view class="me-row-icon alt-3">📍</view><text class="me-row-name">收货地址</text><text class="me-row-arrow">›</text></view>
      <view class="me-row"><view class="me-row-icon alt-2">❓</view><text class="me-row-name">帮助与反馈</text><text class="me-row-arrow">›</text></view>
      <view class="me-row" @click="goAgreement('user')"><view class="me-row-icon alt-1">📄</view><text class="me-row-name">用户服务协议</text><text class="me-row-arrow">›</text></view>
      <view class="me-row" @click="goAgreement('privacy')"><view class="me-row-icon alt-1">🔒</view><text class="me-row-name">隐私协议</text><text class="me-row-arrow">›</text></view>
      <view class="me-row"><view class="me-row-icon alt-1">ℹ</view><text class="me-row-name">关于客小二</text><text class="me-row-arrow">›</text></view>
      <view v-if="user.isLogin" class="me-row logout-row" @click="onLogout"><view class="me-row-icon danger">⏻</view><text class="me-row-name danger">退出登录</text><text class="me-row-arrow">›</text></view>
    </view>

    <!-- V044 合规：底部声明 -->
    <view class="legal-footer">
      <text class="legal-line">本平台为商户营销服务工具，商品由商户独立经营销售。</text>
      <text class="legal-line">营销奖励严格单层，仅奖励直接邀请人；商户保留<text class="b">最终解释权</text>。</text>
      <text class="legal-line">奖励来自商户自费让利预算，<text class="b">不构成投资</text>。</text>
    </view>

    <view class="bottom-pad"></view>
    <bottom-nav active="me" />
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { useUserStore } from '@/store/user.js';
import { getAccount, getTodayStat, listMyQueues, getInviteEligibility } from '@/api/promo.js';
import { listMyShopsEnriched } from '@/api/shop.js';
import { getCartCount } from '@/api/cart.js';
import { getUnusedCouponCount } from '@/api/coupon.js';
import { favoriteCount } from '@/api/product.js';
import { fen2yuan, fmtTime } from '@/utils/format.js';
import { buildInvitePoster, downloadDataUrl } from '@/utils/poster.js';

const user = useUserStore();
const avatarText = computed(() => (user.nickname?.[0] || user.phone?.[0] || '客'));
const maskedPhone = computed(() => {
  const p = user.phone || '';
  if (p.length < 11) return p;
  return p.slice(0, 3) + '****' + p.slice(-4);
});

const earnPoints = ref(0);
const todayEarnPoints = ref(0);
const myShops = ref([]);
const queueTotal = ref(0);
const queues = ref([]);
const favCount = ref(0);
const couponCount = ref(0);
const eligibilityMap = ref({}); // tenantId → { topTuijianSpu, ... }
const posterShop = ref(null);
const posterImage = ref('');
const posterLoading = ref(false);

const maxStar = computed(() => myShops.value.reduce((m, s) => Math.max(m, s.starLevel || 0), 0));
const totalOrders = computed(() => myShops.value.reduce((s, x) => s + (x.orderCount || 0), 0));
const totalInvited = computed(() => myShops.value.reduce((s, x) => s + (x.invitedCount || 0), 0));

// 升星：选取最近一家有 nextStar 的店
const upgradeInfo = computed(() => {
  for (const s of myShops.value) {
    if (s.upgradeInfo && s.upgradeInfo.targetStar) return s.upgradeInfo;
  }
  return null;
});

function goLogin()         { uni.navigateTo({ url: '/pages/login/index' }); }
function goWallet()        { user.isLogin ? uni.navigateTo({ url: '/pages/wallet/index' })   : goLogin(); }
function goWithdraw()      { user.isLogin ? uni.navigateTo({ url: '/pages/withdraw/index' }) : goLogin(); }
function goPromoRecords()  { user.isLogin ? uni.navigateTo({ url: '/pages/points/promo' })   : goLogin(); }
function goConsumeRecords(){ user.isLogin ? uni.navigateTo({ url: '/pages/points/consume' }) : goLogin(); }
function goShopRecords(s, tab) {
  if (!user.isLogin) return goLogin();
  const url = `/pages/points/${tab || 'promo'}?tenantId=${s.tenantId}&shopName=${encodeURIComponent(s.shopName || s.name || '')}`;
  uni.navigateTo({ url });
}
function goQueue()         { user.isLogin ? uni.navigateTo({ url: '/pages/queue/index' })    : goLogin(); }
function goInvite()        { user.isLogin ? uni.navigateTo({ url: '/pages/invite/index' })   : goLogin(); }
function goFav()           { user.isLogin ? uni.navigateTo({ url: '/pages/favorites/index' }): goLogin(); }
function goCoupon()        { user.isLogin ? uni.navigateTo({ url: '/pages/coupon/index' })   : goLogin(); }
function goAddress()       { user.isLogin ? uni.navigateTo({ url: '/pages/address/list' })   : goLogin(); }
function goAgreement(type) { uni.navigateTo({ url: `/pages/agreement/index?type=${type}` }); }
function goWinners()       { uni.reLaunch({ url: '/pages/winners/index' }); }
function goShop(s) { uni.navigateTo({ url: `/pages/shop/home?id=${s.id || s.tenantId}&tenantId=${s.tenantId || s.id}` }); }

// 规则计算（按真实 ratios，没有就均分）；1 积分 = 1 元，底层 fen /100
function calcRule(spu) {
  if (!spu || !spu.tuijianN) return null;
  const n = spu.tuijianN;
  let ratios = [];
  try { ratios = spu.tuijianRatios ? JSON.parse(spu.tuijianRatios) : []; } catch {}
  if (!Array.isArray(ratios) || ratios.length !== n) ratios = Array.from({ length: n }, () => 100 / n);
  const totalFen = Number(spu.price) || 0;
  const avgRatio = ratios.reduce((s, r) => s + Number(r || 0), 0) / n;
  const stepFen = Math.floor(totalFen * avgRatio / 100);
  const sumRatio = ratios.reduce((s, r) => s + Number(r || 0), 0);
  const totalRebateFen = Math.floor(totalFen * sumRatio / 100);
  return { n, stepPoints: fen2yuan(stepFen, false), totalPoints: fen2yuan(totalRebateFen, false) };
}

// 跨店海报：按店邀请入口 ——
//   有资格（购买过推 N 反 1）→ 生成海报；无资格 → 弹 toast 引导先购买。
async function onShopInvite(s) {
  if (!user.isLogin) return goLogin();
  if (!s.inviteEligible) {
    uni.showToast({ title: '先在本店完成「推 N 反 1」购买才能开启邀请', icon: 'none', duration: 2200 });
    return;
  }
  const baseOrigin = (typeof location !== 'undefined' && location.origin) || 'https://ke.doupaidoudian.com';
  const inviteLink = `${baseOrigin}/#/pages/shop/home?tenantId=${s.tenantId}&inviter=${user.userId || ''}`;
  posterShop.value = { tenantId: s.tenantId, shopName: s.shopName || s.name, inviteLink };
  posterImage.value = '';
  posterLoading.value = true;
  try {
    const spu = s.topTuijianSpu || null;
    const rule = calcRule(spu);
    posterImage.value = await buildInvitePoster({
      shopName: s.shopName || s.name,
      inviteLink,
      inviter: user.nickname || (user.phone ? user.phone.slice(-4) : '') || '客小二用户',
      spuName: spu?.spuName,
      spuPic: spu?.spuPic,
      priceYuan: spu ? fen2yuan(spu.price || 0, false) : null,
      n: rule?.n,
      stepPoints: rule?.stepPoints,
      totalPoints: rule?.totalPoints,
    });
  } catch {
    posterImage.value = '';
  } finally {
    posterLoading.value = false;
  }
}

function onCopyPosterLink() {
  if (!posterShop.value) return;
  uni.setClipboardData({
    data: posterShop.value.inviteLink,
    success: () => uni.showToast({ title: '链接已复制', icon: 'success' }),
  });
}

function onSavePoster() {
  if (!posterImage.value) return;
  const ok = downloadDataUrl(posterImage.value, `invite-${posterShop.value?.tenantId || ''}.png`);
  uni.showToast({ title: ok ? '已保存/下载' : '请长按上图保存', icon: 'none' });
}

function closePoster() {
  posterShop.value = null;
  posterImage.value = '';
}

function onLogout() {
  uni.showModal({ title: '退出登录', content: '确定退出吗？', success: ({ confirm }) => {
    if (confirm) { user.logout(); uni.reLaunch({ url: '/pages/me/index' }); }
  }});
}

async function load() {
  if (!user.isLogin) {
    myShops.value = [];
    earnPoints.value = 0;
    todayEarnPoints.value = 0;
    return;
  }
  try {
    const acct = await getAccount();
    earnPoints.value = acct?.promoPointBalance || 0;
  } catch {}
  try {
    const stat = await getTodayStat();
    todayEarnPoints.value = stat?.promoAmountToday || 0;
  } catch {}
  try {
    queues.value = await listMyQueues() || [];
    queueTotal.value = queues.value.length;
  } catch {}
  // 邀请资格：按店判断 — 只有在该店买过推 N 反 1 才能分享
  try {
    const r = await getInviteEligibility();
    const map = {};
    if (r && Array.isArray(r.shops)) {
      for (const sh of r.shops) {
        if (sh && sh.tenantId) map[sh.tenantId] = sh;
      }
    }
    eligibilityMap.value = map;
  } catch { eligibilityMap.value = {}; }
  try {
    const list = await listMyShopsEnriched();
    myShops.value = (list || []).map((s) => {
      const queue = queues.value.find((q) => q.tenantId === s.tenantId);
      const reqN = queue ? (queue.requiredCount || queue.tuijianN) : 0;
      const queueBar = (queue && reqN > 0) ? {
        text: `${queue.spuName || '商品'} · 推 ${reqN} 反 1进度`,
        bold: `${queue.currentCount || 0}/${reqN}`,
      } : null;
      // VO 字段：promoPoints (Long 分) / points (Long 分，消费积分) / star (Integer) / lastVisitAt
      const star = s.star || s.starLevel || 0;
      const elig = eligibilityMap.value[s.tenantId];
      return {
        ...s,
        starLevel: star,                      // 兼容旧模板引用
        promoPointsRaw: s.promoPoints || 0,                // 推广积分 (单位"积分")
        consumePointsRaw: s.points || 0,                  // 消费积分 (单位"积分")
        lastVisitText: s.lastVisitAt ? `最近 ${fmtTime(s.lastVisitAt)}` : '',
        queueCount: queue ? 1 : 0,
        queueActive: !!queue,
        hasQueue: !!queue,
        queueBar,
        starExtraText: star >= 3 ? `${star} 星额外 +${star}%` : '',
        // 邀请门槛：购买过推 N 反 1 商品（queueing OR completed 均算）
        inviteEligible: !!elig,
        topTuijianSpu: elig?.topTuijianSpu || null,
      };
    });
  } catch {}
  try { favCount.value = (await favoriteCount()) || 0; } catch {}
  try { couponCount.value = (await getUnusedCouponCount()) || 0; } catch {}
}
onMounted(load);
onShow(load);
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.page { min-height: 100vh; padding-bottom: 90px; background: $bg-2; }

/* ━━━━━━━━━━ Hero ━━━━━━━━━━ */
.me-hero {
  position: relative;
  padding: 12px 18px 80px;
  background:
    radial-gradient(500px 350px at 0% 0%, rgba(212,146,10,.45), transparent 60%),
    linear-gradient(160deg, #FF8857 0%, $o 40%, $o-d 100%);
  color: #fff;
  overflow: hidden;
}
.me-hero-bg {
  position: absolute; inset: 0;
  background-image: radial-gradient(rgba(255,255,255,.06) 1px, transparent 1px);
  background-size: 22px 22px;
}
.me-top-row { display: flex; align-items: center; gap: 12px; position: relative; z-index: 2; }
.me-avatar {
  width: 56px; height: 56px; border-radius: 50%;
  background: linear-gradient(135deg, #FFE8D4, #FFF);
  color: $o-d;
  display: flex; align-items: center; justify-content: center;
  font-size: 22px; font-weight: 800;
  border: 3px solid rgba(255,255,255,.4);
  box-shadow: 0 6px 16px rgba(0,0,0,.15);
}
.me-info { flex: 1; min-width: 0; }
.me-name-row { display: flex; align-items: center; gap: 8px; }
.me-name { font-size: 18px; font-weight: 800; }
.me-star {
  display: inline-flex; align-items: center; gap: 3px;
  padding: 2px 8px;
  background: linear-gradient(135deg, $gold, $gold-l);
  border-radius: 6px; color: #fff;
  font-size: 10px; font-weight: 800;
  box-shadow: 0 2px 8px rgba(0,0,0,.15);
}
.me-phone { font-size: 12px; opacity: .85; margin-top: 3px; }
.me-phone .b { color: #FEF3C7; font-weight: 700; }
.me-set {
  width: 36px; height: 36px; border-radius: 11px;
  background: rgba(255,255,255,.2); backdrop-filter: blur(10px);
  display: flex; align-items: center; justify-content: center;
  font-size: 14px;
  border: 1px solid rgba(255,255,255,.2);
  position: relative; z-index: 2;
}

/* Hero 收益大数字 */
.me-earn {
  margin-top: 18px;
  background: rgba(0,0,0,.18); backdrop-filter: blur(20px);
  border: 1px solid rgba(255,255,255,.18);
  border-radius: $r-2xl;
  padding: 18px 20px 16px;
  position: relative; z-index: 2;
}
.me-earn-l {
  font-size: 11px; color: #FFEAD8; font-weight: 700; letter-spacing: 1.5px;
}
.me-earn-l::before { content: '💎 '; }
.me-earn-v {
  font-size: 44px; font-weight: 900;
  letter-spacing: -2px; margin-top: 6px; line-height: 1;
  background: linear-gradient(135deg, #fff, #FEF3C7);
  -webkit-background-clip: text; background-clip: text; color: transparent;
  text-shadow: 0 4px 20px rgba(254,243,199,.3);
}
.me-earn-u {
  font-size: 16px; font-weight: 700;
  -webkit-text-fill-color: #FEF3C7;
  margin-left: 4px;
}
.me-earn-grow {
  display: inline-block;
  margin-top: 8px;
  padding: 3px 10px;
  background: rgba(16,185,129,.25); backdrop-filter: blur(10px);
  color: #6EE7B7; border-radius: 6px;
  font-size: 10px; font-weight: 800;
  border: 1px solid rgba(110,231,183,.3);
}
.me-earn-acts {
  display: grid; grid-template-columns: 1fr 1fr 1fr;
  gap: 8px; margin-top: 16px;
}
.me-earn-act {
  padding: 11px 6px;
  background: rgba(255,255,255,.15); backdrop-filter: blur(10px);
  border: 1px solid rgba(255,255,255,.2);
  border-radius: 12px;
  text-align: center;
}
.me-earn-act.primary {
  background: linear-gradient(135deg, #fff, #FFE0D1);
  color: $o-d;
  border-color: transparent;
  box-shadow: 0 4px 12px rgba(0,0,0,.1);
}
.me-earn-act .em { font-size: 18px; }
.me-earn-act .l { font-size: 11px; font-weight: 800; margin-top: 2px; }
.me-earn-act.primary .l { color: $o-d; }

/* 3 列统计 上拉 */
.me-summary {
  background: $card; border-radius: $r-lg;
  margin: -68px 14px 0; box-shadow: $sh-2;
  display: grid; grid-template-columns: repeat(3, 1fr);
  padding: 16px 0; position: relative; z-index: 5;
  border: 1px solid $line;
}
.me-summary .item { text-align: center; border-right: 1px solid $line; }
.me-summary .item:last-child { border-right: 0; }
.me-summary .num { font-size: 22px; font-weight: 800; color: $t1; line-height: 1; }
.me-summary .num.brand { color: $o-d; }
.me-summary .label { font-size: 11px; color: $t3; margin-top: 4px; font-weight: 500; }

/* 升星 */
.me-star-card {
  margin: 14px 14px 0;
  padding: 16px;
  background: linear-gradient(135deg, $gold-50 0%, #FFF8F4 100%);
  border-radius: $r-lg;
  border: 1px solid rgba(212,146,10,.2);
}
.msc-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.msc-title { font-size: 13px; font-weight: 800; color: $t1; }
.msc-title .h {
  background: linear-gradient(135deg, $gold, $o);
  -webkit-background-clip: text; background-clip: text; color: transparent;
  font-size: 16px; margin: 0 2px;
}
.msc-meta { font-size: 11px; color: $t3; font-weight: 600; }
.msc-bar { height: 8px; border-radius: 99px; background: rgba(212,146,10,.15); overflow: hidden; }
.msc-fill {
  height: 100%;
  background: linear-gradient(90deg, $gold, $gold-l, $o);
  border-radius: 99px;
  box-shadow: 0 0 12px rgba(245,178,122,.5);
  transition: width .4s;
}
.msc-progress-lbl { display: flex; justify-content: space-between; margin-top: 8px; font-size: 11px; color: $t2; font-weight: 600; }
.msc-progress-lbl .b { color: $gold-d; font-weight: 800; }
.msc-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-top: 12px; }
.msc-btn { padding: 9px; border-radius: 10px; font-size: 12px; font-weight: 800; text-align: center; }
.msc-btn.p { background: linear-gradient(135deg, $gold, $gold-l); color: #fff; box-shadow: $sh-gold; }
.msc-btn.s { background: $card; color: $t2; border: 1px solid rgba(212,146,10,.25); }

/* 资产 tip-bar */
.me-tip-bar {
  margin: 12px 14px 0;
  padding: 10px 14px;
  background: linear-gradient(135deg, #FFF8EF, #FFEFE3);
  border-left: 3px solid $warn;
  border-radius: 0 $r-md $r-md 0;
  font-size: 11.5px; color: #92400E;
  line-height: 1.6;
}
.me-tip-bar .b { color: $o-d; font-weight: 800; }

/* section title */
.section-title {
  display: flex; align-items: baseline; justify-content: space-between;
  padding: 18px 16px 8px;
}
.section-title .h3 { font-size: 16px; font-weight: 800; color: $t1; }
.section-title .small { font-size: 11px; color: $t3; font-weight: 500; margin-left: 6px; }
.section-title .more { font-size: 12px; color: $t3; }

/* ms-big */
.ms-big {
  margin: 12px 14px 0;
  background: $card;
  border-radius: $r-lg;
  border: 1px solid $line;
  box-shadow: $sh-1;
  overflow: hidden;
  position: relative;
}
.ms-big.gold-border {
  border-color: rgba(212,146,10,.25);
  background: linear-gradient(135deg, #fff, #FFFBF6 30%);
}
.ms-big.has-queue::before {
  content: ''; position: absolute; top: 0; left: 0; bottom: 0; width: 3px;
  background: linear-gradient(180deg, $o, $gold);
}
.msb-head {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 16px 12px;
  background: linear-gradient(135deg, $o-50, transparent 70%);
}
.ms-big.gold-border .msb-head {
  background: linear-gradient(135deg, $gold-50, transparent 70%);
}
.msb-pic {
  width: 44px; height: 44px; border-radius: 12px;
  background: linear-gradient(135deg, #FFD1BA, $o);
  color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px; font-weight: 800; flex-shrink: 0;
  box-shadow: 0 4px 10px rgba(255,107,53,.2);
}
.msb-pic.alt-1 { background: linear-gradient(135deg, #C9E0FF, #6196F0); }
.msb-pic.alt-2 { background: linear-gradient(135deg, #D3F4D3, #4CB84C); }
.msb-pic.alt-3 { background: linear-gradient(135deg, #FFD0DC, #EE5A8B); }
.msb-name-col { flex: 1; min-width: 0; }
.msb-name-row { display: flex; align-items: center; gap: 8px; }
.msb-name { font-size: 15px; font-weight: 800; color: $t1; overflow: hidden; }
.msb-star {
  display: inline-flex; align-items: center; gap: 2px;
  padding: 2px 8px; border-radius: 99px;
  background: linear-gradient(135deg, $gold, $gold-l);
  color: #fff; font-size: 10px; font-weight: 800;
  flex-shrink: 0;
}
.msb-star.empty { background: $bg-2; color: $t4; }
.msb-meta { font-size: 11px; color: $t3; margin-top: 3px; display: flex; gap: 8px; }
.msb-meta .b { color: $t1; font-weight: 700; }
.msb-meta .ok { color: $mint; font-weight: 700; }
.msb-enter {
  width: 32px; height: 32px; border-radius: 10px;
  background: $card; color: $o-d;
  display: flex; align-items: center; justify-content: center;
  font-size: 16px; font-weight: 700;
  border: 1px solid $o-100;
}

.msb-stats { display: grid; grid-template-columns: repeat(4, 1fr); padding: 12px 8px; }
.msb-stat { text-align: center; padding: 8px 4px; border-right: 1px dashed $line; position: relative; }
.msb-stat:last-child { border-right: 0; }
.msb-stat .n { font-size: 17px; font-weight: 900; color: $t1; line-height: 1; }
.msb-stat .n .u { font-size: 10px; font-weight: 700; color: $t3; margin-left: 2px; }
.msb-stat .n.orange {
  background: linear-gradient(135deg, $o, $o-d);
  -webkit-background-clip: text; background-clip: text; color: transparent;
}
.msb-stat .n.gold {
  background: linear-gradient(135deg, $gold, $o);
  -webkit-background-clip: text; background-clip: text; color: transparent;
}
.msb-stat .n.purple { color: $purple; }
.msb-stat .n.mute { color: $t4; }
.msb-stat .l { font-size: 10px; color: $t3; margin-top: 5px; font-weight: 600; }
.msb-stat .micro {
  position: absolute; top: 4px; right: 6px;
  font-size: 9px; padding: 1px 5px; border-radius: 99px;
  background: $mint-50; color: $mint;
  font-weight: 800;
}
.msb-stat .micro.warn { background: $o-50; color: $o-d; }

.msb-queue-bar {
  margin: 0 14px 10px;
  padding: 9px 12px;
  background: linear-gradient(135deg, $o-50, $gold-50);
  border: 1px solid $o-100;
  border-radius: 10px;
  display: flex; align-items: center; gap: 10px;
  font-size: 11px; color: $t2;
}
.msb-queue-bar .em { font-size: 14px; }
.msb-queue-bar .t { flex: 1; }
.msb-queue-bar .t .b { color: $o-d; font-weight: 800; }
.msb-queue-bar .go { color: $o-d; font-size: 11px; font-weight: 800; }

.msb-acts {
  display: grid; grid-template-columns: 1fr 1fr 1fr;
  border-top: 1px dashed $line;
  padding: 4px;
}
.msb-act {
  text-align: center; padding: 10px 4px;
  font-size: 11.5px; font-weight: 700;
  color: $t2;
  display: flex; align-items: center; justify-content: center; gap: 5px;
  position: relative;
}
.msb-act:not(:last-child)::after {
  content: ''; position: absolute; right: 0; top: 25%; bottom: 25%; width: 1px;
  background: $line;
}
.msb-act .em { font-size: 14px; }
.msb-act.primary { color: $o-d; }
.msb-act.disabled { color: $t4; opacity: .65; }

/* ━━━━━━━━━━ 推广海报弹层 ━━━━━━━━━━ */
.poster-mask {
  position: fixed; inset: 0; z-index: 999;
  background: rgba(0,0,0,.78);
  display: flex; align-items: center; justify-content: center;
  padding: 18px;
  overflow-y: auto;
}
.poster-wrap {
  width: 100%; max-width: 360px;
  display: flex; flex-direction: column; align-items: center;
}
.poster-img {
  width: 100%; max-width: 340px;
  border-radius: 14px;
  box-shadow: 0 16px 48px rgba(0,0,0,.4);
  background: #FFF9F0;
}
.poster-loading {
  width: 100%; max-width: 340px; height: 460px;
  border-radius: 14px;
  background: rgba(255,255,255,.1);
  display: flex; align-items: center; justify-content: center;
  color: rgba(255,255,255,.85); font-size: 14px;
}

.poster-actions {
  margin-top: 14px;
  display: flex; gap: 10px; width: 100%;
}
.poster-btn {
  flex: 1; padding: 13px;
  text-align: center; border-radius: 999px;
  font-size: 14px; font-weight: 800;
}
.poster-btn.primary {
  background: linear-gradient(135deg, $o, $o-d);
  color: #fff;
  box-shadow: 0 4px 14px rgba(255,107,53,.4);
}
.poster-btn.ghost {
  background: rgba(255,255,255,.18);
  color: #fff;
  border: 1px solid rgba(255,255,255,.3);
}
.poster-hint {
  margin-top: 10px;
  font-size: 11px; color: rgba(255,255,255,.7);
  text-align: center;
}

/* V044 合规：底部声明 */
.legal-footer {
  margin: 24px 14px 80px;
  padding: 14px 16px;
  background: $bg-2;
  border-radius: $r-md;
  border: 1px dashed $line;
}
.legal-line {
  display: block;
  font-size: 11px;
  color: $t4;
  line-height: 1.7;
  text-align: center;
}
.legal-line .b { color: $t2; font-weight: 700; }

/* me-grid 列表 */
.me-grid {
  background: $card; border: 1px solid $line;
  border-radius: $r-lg;
  margin: 14px 14px 0;
  overflow: hidden;
}
.me-grid-title {
  padding: 14px 16px 8px;
  font-size: 13px; font-weight: 800; color: $t1;
  display: flex; align-items: center; gap: 6px;
}
.me-grid-title::before {
  content: ''; width: 3px; height: 12px; border-radius: 2px;
  background: $o;
}
.me-row {
  display: flex; align-items: center; gap: 12px;
  padding: 13px 16px;
  border-top: 1px solid $line;
}
.me-row:first-of-type { border-top: 0; }
.me-row-icon {
  width: 32px; height: 32px; border-radius: 9px;
  background: $o-50; color: $o-d;
  display: flex; align-items: center; justify-content: center;
  font-size: 16px; flex-shrink: 0;
}
.me-row-icon.alt-1 { background: rgba(99,102,241,.12);  color: $purple; }
.me-row-icon.alt-2 { background: $mint-50;              color: $mint; }
.me-row-icon.alt-3 { background: rgba(245,158,11,.14); color: $warn; }
.me-row-icon.alt-4 { background: rgba(238,90,139,.14); color: #EE5A8B; }
.me-row-icon.danger { background: rgba(239,68,68,.12); color: $danger; }
.me-row-name { flex: 1; font-size: 14px; color: $t1; font-weight: 600; }
.me-row-name.danger { color: $danger; }
.me-row-tag {
  font-size: 11px; background: $o-50; color: $o-d;
  padding: 2px 8px; border-radius: 99px; font-weight: 700;
}
.me-row-arrow { font-size: 16px; color: $t4; }
.logout-row { border-top: 6px solid $bg-2; }

.bottom-pad { height: 12px; }
</style>
