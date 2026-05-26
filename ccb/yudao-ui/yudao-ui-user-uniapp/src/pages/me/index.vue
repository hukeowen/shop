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
          <view v-else class="me-phone">登录即解锁推广积分 / 派奖 / 提现</view>
        </view>
        <view class="me-set">⚙</view>
      </view>

      <view class="me-earn">
        <view class="me-earn-l">累计推广收益</view>
        <view class="me-earn-v">¥{{ earnYuan }}</view>
        <view v-if="user.isLogin" class="me-earn-grow">↑ 今日 +¥{{ todayEarnYuan }}</view>
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
          <view class="n gold">¥{{ s.promoYuan }}</view>
          <view class="l">推广积分</view>
        </view>
        <view class="msb-stat" @click.stop="goShopRecords(s, 'consume')">
          <view class="n purple">¥{{ s.consumeYuan }}</view>
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
        <view class="msb-act" @click.stop="goInvite"><text class="em">🤝</text>邀请</view>
      </view>
    </view>

    <!-- 资产·推广 -->
    <view class="me-grid">
      <view class="me-grid-title">资产 · 推广</view>
      <view class="me-row" @click="goWallet"><view class="me-row-icon">💰</view><text class="me-row-name">我的钱包（按店铺）</text><text class="me-row-tag">{{ myShops.length }} 家</text><text class="me-row-arrow">›</text></view>
      <view class="me-row" @click="goPromoRecords"><view class="me-row-icon alt-1">📊</view><text class="me-row-name">推广积分明细</text><text class="me-row-arrow">›</text></view>
      <view class="me-row" @click="goQueue"><view class="me-row-icon alt-2">🔥</view><text class="me-row-name">我的队列（推 N 反 1）</text><text v-if="queueTotal" class="me-row-tag">{{ queueTotal }} 个</text><text class="me-row-arrow">›</text></view>
      <view class="me-row" @click="goWinners"><view class="me-row-icon alt-3">⭐</view><text class="me-row-name">店铺星级</text><text class="me-row-arrow">›</text></view>
      <view class="me-row" @click="goWinners"><view class="me-row-icon">🏆</view><text class="me-row-name">派奖公告 / 榜一排名</text><text class="me-row-arrow">›</text></view>
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
      <view class="me-row"><view class="me-row-icon alt-1">ℹ</view><text class="me-row-name">关于客小二</text><text class="me-row-arrow">›</text></view>
      <view v-if="user.isLogin" class="me-row logout-row" @click="onLogout"><view class="me-row-icon danger">⏻</view><text class="me-row-name danger">退出登录</text><text class="me-row-arrow">›</text></view>
    </view>

    <view class="bottom-pad"></view>
    <bottom-nav active="me" />
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { useUserStore } from '@/store/user.js';
import { getAccount, getTodayStat, listMyQueues } from '@/api/promo.js';
import { listMyShopsEnriched } from '@/api/shop.js';
import { getCartCount } from '@/api/cart.js';
import { getUnusedCouponCount } from '@/api/coupon.js';
import { favoriteCount } from '@/api/product.js';
import { fen2yuan, fmtTime } from '@/utils/format.js';

const user = useUserStore();
const avatarText = computed(() => (user.nickname?.[0] || user.phone?.[0] || '客'));
const maskedPhone = computed(() => {
  const p = user.phone || '';
  if (p.length < 11) return p;
  return p.slice(0, 3) + '****' + p.slice(-4);
});

const earnYuan = ref('0.00');
const todayEarnYuan = ref('0.00');
const myShops = ref([]);
const queueTotal = ref(0);
const queues = ref([]);
const favCount = ref(0);
const couponCount = ref(0);

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
function goWinners()       { uni.reLaunch({ url: '/pages/winners/index' }); }
function goShop(s) { uni.navigateTo({ url: `/pages/shop/home?id=${s.id || s.tenantId}&tenantId=${s.tenantId || s.id}` }); }

function onLogout() {
  uni.showModal({ title: '退出登录', content: '确定退出吗？', success: ({ confirm }) => {
    if (confirm) { user.logout(); uni.reLaunch({ url: '/pages/me/index' }); }
  }});
}

async function load() {
  if (!user.isLogin) {
    myShops.value = [];
    earnYuan.value = '0.00';
    todayEarnYuan.value = '0.00';
    return;
  }
  try {
    const acct = await getAccount();
    earnYuan.value = fen2yuan(acct?.promoPointBalance || 0, false);
  } catch {}
  try {
    const stat = await getTodayStat();
    todayEarnYuan.value = fen2yuan(stat?.promoAmountToday || 0, false);
  } catch {}
  try {
    queues.value = await listMyQueues() || [];
    queueTotal.value = queues.value.length;
  } catch {}
  try {
    const list = await listMyShopsEnriched();
    myShops.value = (list || []).map((s) => {
      const queue = queues.value.find((q) => q.tenantId === s.tenantId);
      const reqN = queue ? (queue.requiredCount || queue.tuijianN) : 0;
      const queueBar = (queue && reqN > 0) ? {
        text: `${queue.spuName || '商品'} · 推 ${reqN} 反 1 进度`,
        bold: `${queue.currentCount || 0}/${reqN}`,
      } : null;
      // VO 字段：promoPoints (Long 分) / points (Long 分，消费积分) / star (Integer) / lastVisitAt
      const star = s.star || s.starLevel || 0;
      return {
        ...s,
        starLevel: star,                      // 兼容旧模板引用
        promoYuan: fen2yuan(s.promoPoints || 0, false),   // 推广积分 → ¥
        consumeYuan: fen2yuan(s.points || 0, false),      // 消费积分 → ¥（100 积分=¥1）
        lastVisitText: s.lastVisitAt ? `最近 ${fmtTime(s.lastVisitAt)}` : '',
        queueCount: queue ? 1 : 0,
        queueActive: !!queue,
        hasQueue: !!queue,
        queueBar,
        starExtraText: star >= 3 ? `${star} 星额外 +${star}%` : '',
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
