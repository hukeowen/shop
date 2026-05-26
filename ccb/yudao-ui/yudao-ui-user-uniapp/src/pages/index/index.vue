<template>
  <view class="page">
    <!-- ━━━━━━━━━━ HERO（暖橙渐变 + 圆点底纹 + 右上柔光球）━━━━━━━━━━ -->
    <view class="home-hero">
      <view class="hero-deco-dots"></view>
      <view class="hero-deco-glow"></view>

      <view class="home-greet-row">
        <view class="home-avatar">{{ avatarText }}</view>
        <view class="home-greet">
          <view class="hi">{{ greeting }} ☀</view>
          <view class="name">{{ greetTitle }}</view>
        </view>
        <view class="home-head-ics">
          <view class="home-head-ic">📍</view>
          <view class="home-head-ic">🔔<view class="dot"></view></view>
        </view>
      </view>

      <view class="home-search" @click="goSearch">
        <text class="ic">🔍</text>
        <text class="ph">搜店铺、找商品、看附近</text>
        <view class="voice">🎙</view>
      </view>

      <view class="home-ticker">
        <text class="trophy">🏆</text>
        <view class="roll">
          <view class="roll-track">
            <text v-for="(t, i) in tickerText" :key="i">{{ t }} · </text>
          </view>
        </view>
      </view>
    </view>

    <!-- ━━━━━━━━━━ 我今日刚到账 — 仅登录后 + 有记录显示 ━━━━━━━━━━ -->
    <view v-if="user.isLogin && todayRecords.length" class="mae-card">
      <view class="mae-head">
        <view class="mae-tag">💎 你今日刚到账</view>
        <text class="mae-more" @click="goWallet">明细 ›</text>
      </view>
      <view class="mae-list">
        <view v-for="r in todayRecords" :key="r.id" class="mae-row" :class="{ normal: !r.highlight }">
          <view class="mae-ic" :class="r.cls">{{ r.icon }}</view>
          <view class="mae-body">
            <view class="mae-name">{{ r.title }}</view>
            <view class="mae-d">{{ r.time }} · {{ r.sourceLabel }}</view>
          </view>
          <view class="mae-amt" :class="{ normal: !r.highlight }">+¥{{ r.amount }}</view>
        </view>
      </view>
      <view class="mae-foot">
        <view class="sum">今日合计入账 <text class="b">¥{{ todaySumYuan }}</text>
          <text v-if="todayStat.consume > 0"> + <text class="b">{{ todaySumPoints }} 分</text></text></view>
        <view class="mae-btn" @click="goWithdraw">💸 提现 →</view>
      </view>
    </view>
    <!-- 未登录态：登录引导卡 -->
    <view v-else-if="!user.isLogin" class="mae-card login-tip" @click="goLogin">
      <view class="lt-em">👋</view>
      <view class="lt-body">
        <view class="lt-t">登录开启赚钱模式</view>
        <view class="lt-d">商户实时派奖 / 推 N 反 1 / 1:1 提现</view>
      </view>
      <view class="lt-cta">登录 →</view>
    </view>
    <!-- 已登录但今日还没到账：兜底空状态卡（同样上拉与 hero 重叠，避免大块橙色空白） -->
    <view v-else class="mae-card empty-today" @click="goNearby">
      <view class="lt-em">🌅</view>
      <view class="lt-body">
        <view class="lt-t">今日还没到账</view>
        <view class="lt-d">逛逛附近店铺 · 下单参与「推 N 反 1」立即赚</view>
      </view>
      <view class="lt-cta">去赚 →</view>
    </view>

    <!-- ━━━━━━━━━━ 5 快入口 ━━━━━━━━━━ -->
    <view class="home-quick">
      <view class="qk" @click="goNearby"><view class="qk-ic">📍</view><view class="qk-text">附近</view></view>
      <view class="qk" @click="goWinners"><view class="qk-ic">🏆<view class="live-dot"></view></view><view class="qk-text">派奖公告</view></view>
      <view class="qk" @click="goQueue"><view class="qk-ic">🔥</view><view class="qk-text">我的队列</view></view>
      <view class="qk" @click="goCoupon"><view class="qk-ic">🎟</view><view class="qk-text">优惠券</view></view>
      <view class="qk" @click="onScan"><view class="qk-ic">📜</view><view class="qk-text">扫码</view></view>
    </view>

    <!-- ━━━━━━━━━━ 推 N 反 1 进行中提醒 — 仅有进行中队列时显示 ━━━━━━━━━━ -->
    <view v-if="queueTip" class="home-queue-tip" @click="goQueue">
      <view class="hqt-ic">🔥</view>
      <view class="hqt-body">
        <view class="hqt-t">{{ queueTip.shopName }} · {{ queueTip.spuName }} <text class="b">还差 {{ queueTip.gap }} 人即出队 +¥{{ queueTip.amount }}</text></view>
        <view class="hqt-d">分享给朋友扫码下单 → 你立即出队拿全额</view>
      </view>
      <view class="hqt-cta">分享 →</view>
    </view>

    <!-- ━━━━━━━━━━ 分类（与系统 BUSINESS_CONTEXT_MAP 编码对齐：snack/drink/bbq/restaurant/tea_house/fruit/super/bakery/beauty/other）━━━━━━━━━━ -->
    <view class="home-cats">
      <view class="home-cat" @click="goCategory('snack')"><view class="em">🥟</view><view class="l">小吃</view></view>
      <view class="home-cat" @click="goCategory('drink')"><view class="em">🧋</view><view class="l">奶茶</view></view>
      <view class="home-cat" @click="goCategory('bbq')"><view class="em">🍢</view><view class="l">烧烤</view></view>
      <view class="home-cat" @click="goCategory('restaurant')"><view class="em">🍽</view><view class="l">餐厅</view></view>
      <view class="home-cat" @click="goCategory('tea_house')"><view class="em">🏯</view><view class="l">茶馆</view></view>
      <view class="home-cat" @click="goCategory('fruit')"><view class="em">🍓</view><view class="l">水果</view></view>
      <view class="home-cat" @click="goCategory('super')"><view class="em">🛒</view><view class="l">超市</view></view>
      <view class="home-cat" @click="goCategory('bakery')"><view class="em">🥐</view><view class="l">烘焙</view></view>
      <view class="home-cat" @click="goCategory('beauty')"><view class="em">💄</view><view class="l">美容</view></view>
      <view class="home-cat" @click="goCategory('')"><view class="em">🏪</view><view class="l">全部</view></view>
    </view>

    <!-- ━━━━━━━━━━ 营销双卡 ━━━━━━━━━━ -->
    <view class="section-title">
      <view class="h3">玩法专区 <text class="small">商户派奖 · 1:1 提现</text></view>
    </view>
    <view class="home-feats">
      <view class="home-feat rank" @click="goWinners">
        <text class="em-bg">🏆</text>
        <view class="hf-tag">🏆 派奖公告</view>
        <view>
          <view class="hf-title">看谁刚拿到奖</view>
          <view class="hf-sub">榜一排名 · 按店</view>
        </view>
        <view class="hf-bot">
          <view class="hf-meta">今日派奖 <text>¥{{ stat.todayAward }}</text></view>
          <view class="hf-cta">查看榜单 →</view>
        </view>
      </view>
      <view class="home-feat nback" @click="goQueue">
        <text class="em-bg">🔥</text>
        <view class="hf-tag">🔥 推 N 反 1</view>
        <view>
          <view class="hf-title">买 N 件 免单 1 件</view>
          <view class="hf-sub">朋友买你也得返</view>
        </view>
        <view class="hf-bot">
          <view class="hf-meta">在队列 <text>{{ stat.myQueueCount }} 个</text></view>
          <view class="hf-cta">查看 →</view>
        </view>
      </view>
    </view>

    <!-- ━━━━━━━━━━ 最近去过 横滚 — 仅登录后 + 有数据 ━━━━━━━━━━ -->
    <template v-if="recentShops.length">
      <view class="section-title">
        <view class="h3">最近去过</view>
        <text class="more" @click="goNearby">全部 ›</text>
      </view>
      <scroll-view scroll-x class="recent-scroll">
        <view v-for="s in recentShops" :key="s.id" class="recent-card" @click="goShop(s)">
          <view class="recent-cover" :class="s.coverTone">
            <view class="recent-tag">{{ s.lastVisit }}</view>
            <view v-if="s.star > 0" class="recent-star">★{{ s.star }}</view>
          </view>
          <view class="recent-body">
            <view class="recent-name">{{ s.name }}</view>
            <view class="recent-meta">
              <text>余 <text class="b">¥{{ s.balanceYuan }}</text></text>
              <view v-if="s.promoPoint > 0" class="dot"></view>
              <text v-if="s.promoPoint > 0">推 <text class="b">{{ s.promoPoint }}</text></text>
            </view>
          </view>
        </view>
      </scroll-view>
    </template>

    <!-- ━━━━━━━━━━ 附近商家 — 真数据 ━━━━━━━━━━ -->
    <view class="section-title">
      <view class="h3">附近商家 <text class="small">店主推明星商品</text></view>
      <text class="more" @click="goNearby">全部 ›</text>
    </view>
    <view v-if="loadingShops" class="loading">加载中…</view>
    <empty-state v-else-if="!nearbyShops.length" icon="🏪" title="附近暂无店铺" desc="换个位置或允许定位试试" />
    <view v-else class="shop-grid">
      <view v-for="s in nearbyShops" :key="s.id" class="shop-card-g" @click="goShop(s)">
        <view class="shop-cover">
          <image v-if="s.coverUrl" :src="s.coverUrl" mode="aspectFill" class="cover-img" />
          <text v-else class="cover-em">{{ (s.name || '店')[0] }}</text>
          <view class="shop-status-mini" :class="{ closed: !s.open }">
            <text class="dot"></text>{{ s.open ? '营业中' : '休息中' }}
          </view>
          <view v-if="s.star" class="cover-star">★{{ s.star }}</view>
        </view>
        <view class="card-body">
          <view class="card-name">{{ s.name }}</view>
          <view v-if="s.promoLine" class="card-promo">{{ s.promoLine }}</view>
          <view class="card-meta">
            <text v-if="s.rating" class="rating">★ {{ s.rating }}</text>
            <text v-if="s.monthSold != null">月售 {{ s.monthSold }}</text>
          </view>
        </view>
      </view>
    </view>
    <view class="bottom-pad"></view>

    <!-- 浮动收益球（仅登录后有今日入账时显示） -->
    <view v-if="user.isLogin && todayStat.promo > 0" class="float-earn" @click="goWallet">
      <view class="n">{{ todaySumYuan }}</view>
      <view class="l">今日</view>
    </view>

    <bottom-nav active="index" :cart-count="cartCount" />
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { useUserStore } from '@/store/user.js';
import { listWinnersTicker, listMyQueues, listPromoRecords, getTodayStat } from '@/api/promo.js';
import { listShops, listMyShopsEnriched } from '@/api/shop.js';
import { getCartCount } from '@/api/cart.js';
import { fen2yuan, fmtTime, fmtDistance } from '@/utils/format.js';

const user = useUserStore();
const avatarText = computed(() => (user.nickname?.[0] || '客'));

const greeting = computed(() => {
  const h = new Date().getHours();
  if (h < 5)  return '凌晨好';
  if (h < 11) return '早上好';
  if (h < 14) return '中午好';
  if (h < 18) return '下午好';
  return '晚上好';
});
const greetTitle = computed(() => `想吃点什么，${user.nickname || (user.phone ? user.phone.slice(-4) : '小客')}？`);

// 滚动条：跨店最新派奖
const tickerText = ref([]);
async function loadTicker() {
  try {
    const list = await listWinnersTicker(8);
    tickerText.value = (list || []).map((w) => {
      const amt = fen2yuan(w.amount, false);
      return `${w.shopName || '店铺'} ${w.sourceLabel || '派奖'} ${w.userMask || ''} ¥${amt}`;
    });
  } catch {}
}

// 我今日刚到账 — 最近 3 条 + 今日合计（未登录跳过）
const todayRecords = ref([]);
const todayStat = ref({ promo: 0, consume: 0, count: 0 });
async function loadTodayMae() {
  if (!user.isLogin) { todayRecords.value = []; todayStat.value = { promo: 0, consume: 0, count: 0 }; return; }
  try {
    const [page, stat] = await Promise.all([
      listPromoRecords(1, 3),
      getTodayStat(),
    ]);
    todayStat.value = {
      promo: stat?.promoAmountToday || 0,
      consume: stat?.consumeAmountToday || 0,
      count: stat?.awardCountToday || 0,
    };
    // 只保留今日的（接口已分页倒序，前端再按 createTime 过滤）
    const today = new Date().toDateString();
    const labelOf = (t) => ({
      DIRECT: '直推返现', QUEUE: '推 N 反 1 出队', COMMISSION: '团队佣金',
      POOL: '派奖池中奖', CONVERT: '积分转换', WITHDRAW: '提现',
    }[t] || '推广奖励');
    const rows = (page?.list || [])
      .filter((r) => new Date(String(r.createTime).replace('T', ' ').replace(/-/g, '/')).toDateString() === today)
      .map((r) => {
        let icon = '🏆', cls = '';
        if (r.sourceType === 'QUEUE')           { icon = '💰'; cls = 'coin'; }
        else if (r.sourceType === 'COMMISSION') { icon = '⭐'; cls = 'pt'; }
        return {
          id: r.id,
          icon, cls,
          title: r.remark || labelOf(r.sourceType),
          sourceLabel: labelOf(r.sourceType),
          time: fmtTime(r.createTime),
          amount: fen2yuan(r.amount, false),
          highlight: r.sourceType === 'POOL' || r.sourceType === 'QUEUE',
        };
      });
    todayRecords.value = rows;
  } catch {}
}
const todaySumYuan = computed(() => fen2yuan(todayStat.value.promo, false));
const todaySumPoints = computed(() => Math.round(todayStat.value.consume / 100));

// 推 N 反 1 队列提醒 + 在队列数（未登录跳过）
const stat = ref({ todayAward: '0', myQueueCount: 0 });
const queueTip = ref(null);
async function loadQueueTip() {
  if (!user.isLogin) { queueTip.value = null; stat.value.myQueueCount = 0; return; }
  try {
    const list = await listMyQueues();
    stat.value.myQueueCount = (list || []).length;
    if (list && list.length) {
      const sorted = [...list].sort((a, b) =>
        ((a.requiredCount || 0) - (a.currentCount || 0)) -
        ((b.requiredCount || 0) - (b.currentCount || 0)));
      const q = sorted[0];
      const gap = (q.requiredCount || 0) - (q.currentCount || 0);
      const amt = q.rewardAmount || 0;
      // gap<=0 或 amount<=0 视为脏数据，不显示
      if (gap > 0 && amt > 0) {
        queueTip.value = {
          shopName: q.shopName || '店铺',
          spuName: q.spuName || '商品',
          gap,
          amount: fen2yuan(amt, false),
          tenantId: q.tenantId,
        };
      } else {
        queueTip.value = null;
      }
    } else {
      queueTip.value = null;
    }
  } catch {}
}

// 今日全网派奖（feat 卡 hero stat） — 跨店统计
async function loadFeatStat() {
  try {
    const s = await getTodayStat();
    if (s && s.promoAmountToday != null) {
      stat.value.todayAward = fen2yuan(s.promoAmountToday, false);
    }
  } catch {}
}

// 最近去过的店 — 仅登录
const recentShops = ref([]);
async function loadRecent() {
  if (!user.isLogin) { recentShops.value = []; return; }
  try {
    // /my-shops 只返关系（无 shopName）→ 改用 /my-shops-enriched 拿名字 + 余额
    const list = await listMyShopsEnriched();
    // 按最后访问时间倒序
    const sorted = [...(list || [])].sort((a, b) => (b.lastVisitAt || 0) - (a.lastVisitAt || 0));
    recentShops.value = sorted.slice(0, 4).map((s, i) => ({
      id: s.id || s.tenantId,
      tenantId: s.tenantId,
      name: s.shopName || s.name || `店铺 #${s.tenantId}`,
      coverTone: ['', 't2', 't3', 't4'][i],
      lastVisit: s.lastVisitAt ? fmtTime(s.lastVisitAt) : '最近',
      star: s.star || 0,
      balanceYuan: fen2yuan(s.balance || 0, false),
      promoPoint: s.promoPoints || 0,
    }));
  } catch {}
}

// 附近店铺 — 真接口，前 3 个展示
const nearbyShops = ref([]);
const loadingShops = ref(false);
async function loadNearby() {
  loadingShops.value = true;
  try {
    // 默认 20 个 → 取前 10 展示；无坐标 / 未开业 都展示，让用户知道有哪些店
    const r = await listShops({ pageNo: 1, pageSize: 20 });
    const items = r?.list || r || [];
    nearbyShops.value = items.slice(0, 10).map((s) => ({
      id: s.id || s.tenantId,
      tenantId: s.tenantId || s.id,
      name: s.shopName || s.name || '店铺',
      coverUrl: s.coverUrl || s.shopLogo || '',
      star: s.starLevel || s.star,
      newTag: s.newShop ? '新店送 ¥5' : '',
      rating: s.avgRating || s.rating,
      // 距离仅在后端真返了 distance（用户给了坐标）才显示，否则不占位
      distance: s.distance != null && s.distance > 0 ? fmtDistance(s.distance) : '',
      monthSold: s.sales30d != null ? s.sales30d : (s.monthSold != null ? s.monthSold : null),
      // 营业状态：后端 /public/list 已计算 isOpenNow（综合 status / todayOpenAt / manualClosed / businessHours）
      open: s.isOpenNow === true,
      promoLine: s.promoLine || (s.tuijianN ? `推 ${s.tuijianN} 反 1 进行中` : ''),
      starSpu: null,
    }));
  } catch {} finally { loadingShops.value = false; }
}

// 购物车角标
async function loadCart() {
  if (!user.isLogin) { cartCount.value = 0; return; }
  try { cartCount.value = (await getCartCount()) || 0; } catch {}
}
const cartCount = ref(0);

function goSearch() { uni.navigateTo({ url: '/pages/search/index' }); }
function goNearby() { uni.navigateTo({ url: '/pages/nearby/index' }); }
function goWinners() { uni.reLaunch({ url: '/pages/winners/index' }); }
function goQueue() { uni.navigateTo({ url: '/pages/queue/index' }); }
function goCoupon() { uni.navigateTo({ url: '/pages/coupon/index' }); }
function goWallet() { uni.navigateTo({ url: '/pages/wallet/index' }); }
function goWithdraw() { uni.navigateTo({ url: '/pages/withdraw/index' }); }
// 首页"餐饮/茶饮/烘焙/生鲜/美容"是商家业务类型（不是商品分类），跳附近店铺并自动按 businessType 筛选
function goCategory(k) { uni.navigateTo({ url: `/pages/nearby/index?bt=${k}` }); }
function goShop(s) {
  const tid = s.tenantId || s.id;
  uni.navigateTo({ url: `/pages/shop/home?id=${s.id || tid}&tenantId=${tid}` });
}
function goStarSpu(s) {
  if (s.starSpu?.id) {
    uni.navigateTo({ url: `/pages/product/detail?id=${s.starSpu.id}&tenantId=${s.tenantId}` });
  } else {
    goShop(s);
  }
}
function goLogin() { uni.navigateTo({ url: '/pages/login/index' }); }
function onScan() {
  // #ifdef MP-WEIXIN || APP-PLUS
  uni.scanCode({ success: (r) => uni.showToast({ title: r.result, icon: 'none' }) });
  // #endif
  // #ifdef H5
  uni.showToast({ title: 'H5 不支持扫码，请用 APP/小程序', icon: 'none' });
  // #endif
}

function refreshAll() {
  loadTicker();
  loadFeatStat();
  loadNearby();
  loadCart();
  loadTodayMae();
  loadQueueTip();
  loadRecent();
}
onMounted(refreshAll);
onShow(refreshAll);
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.page {
  min-height: 100vh;
  background: $bg;
  padding-bottom: 90px;
}

/* ━━━━━━━━━━━━━━━ HERO ━━━━━━━━━━━━━━━ */
.home-hero {
  position: relative;
  padding: 8px 18px 40px;  /* 美团风：紧凑 hero，不留大块底色空白 */
  background:
    radial-gradient(600px 400px at 100% 0%, rgba(255,178,121,.5), transparent 60%),
    linear-gradient(160deg, #FFB174 0%, $o 55%, $o-d 100%);
  color: #fff;
  overflow: hidden;
}
.hero-deco-dots {
  position: absolute; inset: 0;
  background-image: radial-gradient(rgba(255,255,255,.06) 1px, transparent 1px);
  background-size: 22px 22px;
  pointer-events: none;
}
.hero-deco-glow {
  position: absolute;
  top: -30%; right: -20%; width: 280px; height: 280px;
  background: radial-gradient(circle, rgba(255,255,255,.25), transparent 60%);
  pointer-events: none;
}
.home-greet-row {
  display: flex; align-items: center; gap: 12px;
  padding-top: 6px; position: relative; z-index: 2;
}
.home-avatar {
  width: 42px; height: 42px; border-radius: 50%;
  background: linear-gradient(135deg, #fff, #FFE0D1);
  color: $o-d;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px; font-weight: 800;
  border: 2px solid rgba(255,255,255,.3);
  box-shadow: 0 4px 12px rgba(0,0,0,.15);
}
.home-greet { flex: 1; min-width: 0; }
.home-greet .hi { font-size: 12px; opacity: .9; font-weight: 500; color: #fff; }
.home-greet .name { font-size: 18px; font-weight: 800; letter-spacing: -.3px; color: #fff; }
.home-head-ics { display: flex; gap: 6px; }
.home-head-ic {
  width: 36px; height: 36px; border-radius: 11px;
  background: rgba(255,255,255,.18);
  backdrop-filter: blur(10px);
  display: flex; align-items: center; justify-content: center;
  font-size: 15px; color: #fff;
  position: relative;
  border: 1px solid rgba(255,255,255,.15);
}
.home-head-ic .dot {
  position: absolute; top: 7px; right: 7px;
  width: 6px; height: 6px; border-radius: 99px;
  background: #FEF3C7; box-shadow: 0 0 8px #FEF3C7;
}
.home-search {
  margin-top: 16px;
  background: rgba(255,255,255,.98);
  border-radius: $r-pill;
  padding: 10px 8px 10px 18px;
  display: flex; align-items: center; gap: 8px;
  box-shadow: 0 8px 24px rgba(0,0,0,.15);
  position: relative; z-index: 2;
}
.home-search .ic { font-size: 15px; color: $t3; }
.home-search .ph { flex: 1; font-size: 14px; color: $t3; }
.home-search .voice {
  width: 32px; height: 32px; border-radius: 50%;
  background: $o-50; color: $o-d;
  display: flex; align-items: center; justify-content: center;
  font-size: 15px;
}
.home-ticker {
  margin-top: 14px;
  padding: 8px 14px;
  background: rgba(0,0,0,.18);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255,255,255,.15);
  border-radius: 99px;
  display: flex; align-items: center; gap: 10px;
  overflow: hidden;
  position: relative; z-index: 2;
}
.home-ticker .trophy {
  font-size: 16px;
}
.home-ticker .roll {
  flex: 1; overflow: hidden; height: 18px; position: relative;
}
.home-ticker .roll-track {
  position: absolute; white-space: nowrap;
  font-size: 11.5px; font-weight: 600; color: #FFEAD8;
  animation: rollx 22s linear infinite;
}
@keyframes rollx { 0% { transform: translateX(100%); } 100% { transform: translateX(-100%); } }

/* ━━━━━━━━━━━━━━━ 未登录引导卡 / 已登录空状态卡（取代 mae-card）━━━━━━━━━━━━━━━ */
.mae-card.login-tip,
.mae-card.empty-today {
  display: flex; align-items: center; gap: 14px;
  padding: 14px 16px;
}
.lt-em { font-size: 32px; }
.lt-body { flex: 1; }
.lt-t { font-size: 15px; font-weight: 800; color: $t1; }
.lt-d { font-size: 11px; color: $t3; margin-top: 2px; }
.lt-cta {
  padding: 8px 16px; border-radius: 99px;
  background: linear-gradient(135deg, $o, $o-d); color: #fff;
  font-size: 12px; font-weight: 800;
  box-shadow: $sh-warm;
}

/* 加载占位 */
.loading { padding: 30px; text-align: center; color: $t4; font-size: 12px; }

/* 店铺图 fallback */
.pic-img { width: 100%; height: 100%; }

/* ━━━━━━━━━━━━━━━ MAE 卡（上拉负 margin 与 hero 重叠）━━━━━━━━━━━━━━━ */
.mae-card {
  margin: -28px 14px 0;  /* 配合紧凑 hero 调小负 margin */
  background: linear-gradient(135deg, #fff 0%, #FFFBF6 100%);
  border-radius: $r-xl;
  padding: 16px 18px;
  box-shadow: $sh-3;
  position: relative; overflow: hidden;
  z-index: 5;
  border: 1px solid $o-100;
}
.mae-head {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 10px;
}
.mae-tag {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 4px 10px; border-radius: 99px;
  background: linear-gradient(135deg, $gold, $gold-l);
  color: #fff; font-size: 10px; font-weight: 800;
  letter-spacing: .5px;
  box-shadow: $sh-gold;
}
.mae-more { font-size: 11px; color: $t3; font-weight: 600; }
.mae-list { display: flex; flex-direction: column; gap: 8px; }
.mae-row {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 12px;
  background: linear-gradient(90deg, $gold-50, $o-50);
  border: 1px solid $o-100;
  border-radius: 12px;
  position: relative; overflow: hidden;
}
.mae-row.normal {
  background: $bg-2;
  border-color: $line;
}
.mae-ic {
  width: 32px; height: 32px; border-radius: 10px;
  background: linear-gradient(135deg, $gold, $gold-l);
  color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 16px; flex-shrink: 0;
  box-shadow: 0 4px 10px rgba(212,146,10,.3);
}
.mae-ic.coin { background: linear-gradient(135deg, $o, $o-d); box-shadow: 0 4px 10px rgba(255,107,53,.3); }
.mae-ic.pt   { background: $purple; box-shadow: 0 4px 10px rgba(99,102,241,.3); }
.mae-body { flex: 1; min-width: 0; }
.mae-name {
  font-size: 13px; font-weight: 800; color: $t1;
  display: flex; align-items: center; gap: 6px;
}
.mae-name .badge {
  font-size: 9px; padding: 1px 6px; border-radius: 4px; font-weight: 800;
  background: linear-gradient(135deg, $gold, $gold-l);
  color: #fff;
}
.mae-d { font-size: 11px; color: $t3; margin-top: 2px; }
.mae-d .b { color: $t1; font-weight: 700; }
.mae-amt {
  font-size: 18px; font-weight: 900; letter-spacing: -.3px;
  background: linear-gradient(135deg, $gold, $o);
  -webkit-background-clip: text; background-clip: text; color: transparent;
  font-variant-numeric: tabular-nums;
}
.mae-amt text { font-size: 11px; }
.mae-amt.normal {
  background: none;
  color: $o-d;
  -webkit-text-fill-color: $o-d;
}
.mae-foot {
  margin-top: 10px;
  display: flex; justify-content: space-between; align-items: center;
  padding-top: 10px; border-top: 1px dashed $line;
}
.mae-foot .sum { font-size: 11px; color: $t3; font-weight: 600; }
.mae-foot .sum .b {
  color: $o-d; font-weight: 900; font-size: 15px; margin: 0 2px;
  font-variant-numeric: tabular-nums;
}
.mae-btn {
  padding: 7px 14px; border-radius: 99px;
  background: linear-gradient(135deg, $o, $o-d);
  color: #fff; font-size: 11px; font-weight: 800;
  box-shadow: $sh-warm;
}

/* ━━━━━━━━━━━━━━━ 5 快入口 ━━━━━━━━━━━━━━━ */
.home-quick {
  display: grid; grid-template-columns: repeat(5, 1fr); gap: 4px;
  margin: 14px 14px 0; padding: 12px 6px;
  background: $card; border-radius: $r-lg;
  box-shadow: $sh-1;
}
.qk { text-align: center; padding: 4px 0; position: relative; }
.qk-ic {
  width: 40px; height: 40px; margin: 0 auto 5px;
  border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  font-size: 20px;
  position: relative;
}
.qk:nth-child(1) .qk-ic { background: linear-gradient(135deg, $o-50, $o-100); color: $o-d; }
.qk:nth-child(2) .qk-ic { background: linear-gradient(135deg, $gold-50, #FCD34D); color: #92400E; }
.qk:nth-child(3) .qk-ic { background: linear-gradient(135deg, $mint-50, $mint-l); color: #047857; }
.qk:nth-child(4) .qk-ic { background: linear-gradient(135deg, #DBEAFE, #93C5FD); color: #1E40AF; }
.qk:nth-child(5) .qk-ic { background: linear-gradient(135deg, #FCE7F3, #F9A8D4); color: #BE185D; }
.qk-ic .live-dot {
  position: absolute; top: -2px; right: -2px;
  width: 8px; height: 8px; border-radius: 99px;
  background: $danger;
  box-shadow: 0 0 6px $danger;
}
.qk-text { font-size: 11px; color: $t1; font-weight: 600; }

/* ━━━━━━━━━━━━━━━ 推 N 反 1 提醒 ━━━━━━━━━━━━━━━ */
.home-queue-tip {
  margin: 14px 14px 0;
  padding: 14px 16px;
  border-radius: $r-lg;
  background: linear-gradient(135deg, #FFF8F4 0%, #FFEFE3 100%);
  border: 1px solid $o-100;
  display: flex; align-items: center; gap: 12px;
  position: relative; overflow: hidden;
}
.hqt-ic {
  width: 40px; height: 40px; border-radius: 12px;
  background: linear-gradient(135deg, $o, $o-d);
  color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px; flex-shrink: 0;
  box-shadow: $sh-warm;
}
.hqt-body { flex: 1; min-width: 0; }
.hqt-t { font-size: 13px; font-weight: 800; color: $t1; }
.hqt-t .b { color: $o-d; }
.hqt-d { font-size: 11px; color: $t3; margin-top: 2px; }
.hqt-cta {
  padding: 7px 12px; border-radius: 99px;
  background: $card; color: $o-d;
  font-size: 11px; font-weight: 800;
  border: 1px solid $o-100;
}

/* ━━━━━━━━━━━━━━━ 分类 5 格（美团风圆形彩色图标）━━━━━━━━━━━━━━━ */
.home-cats {
  display: grid; grid-template-columns: repeat(5, 1fr); gap: 4px;
  margin: 14px 10px 0; padding: 14px 4px;
  background: #fff;
  border-radius: $r-lg;
  box-shadow: $sh-1;
}
.home-cat { text-align: center; padding: 6px 0; }
.home-cat .em {
  display: inline-flex; align-items: center; justify-content: center;
  width: 44px; height: 44px; border-radius: 50%;
  font-size: 22px;
  background: linear-gradient(135deg, $o-50, $o-100);
  box-shadow: 0 2px 6px rgba(255,107,53,.12);
  margin-bottom: 6px;
}
/* 每格不同色系，更鲜活（按 nth-child 给 grid item 上色，复用 emoji 圆形底） */
.home-cat:nth-child(1) .em { background: linear-gradient(135deg, #FEF3C7, #FDE68A); }   /* 小吃 黄 */
.home-cat:nth-child(2) .em { background: linear-gradient(135deg, #FCE7F3, #FBCFE8); }   /* 奶茶 粉 */
.home-cat:nth-child(3) .em { background: linear-gradient(135deg, #FFEDD5, #FED7AA); }   /* 烧烤 橙 */
.home-cat:nth-child(4) .em { background: linear-gradient(135deg, #FEE2E2, #FECACA); }   /* 餐厅 红 */
.home-cat:nth-child(5) .em { background: linear-gradient(135deg, #EDE9FE, #DDD6FE); }   /* 茶馆 紫 */
.home-cat:nth-child(6) .em { background: linear-gradient(135deg, #FCE7F3, #F9A8D4); }   /* 水果 桃 */
.home-cat:nth-child(7) .em { background: linear-gradient(135deg, #DBEAFE, #BFDBFE); }   /* 超市 蓝 */
.home-cat:nth-child(8) .em { background: linear-gradient(135deg, #FEF3C7, #FCD34D); }   /* 烘焙 金 */
.home-cat:nth-child(9) .em { background: linear-gradient(135deg, #FCE7F3, #F9A8D4); }   /* 美容 粉 */
.home-cat:nth-child(10) .em { background: linear-gradient(135deg, #F1F5F9, #E2E8F0); }  /* 全部 灰 */
.home-cat .l { font-size: 11.5px; color: $t1; font-weight: 600; margin-top: 4px; }

/* ━━━━━━━━━━━━━━━ 区块标题 ━━━━━━━━━━━━━━━ */
.section-title {
  display: flex; align-items: center; justify-content: space-between;
  margin: 18px 16px 10px;
}
.section-title .h3 {
  font-size: 16px; font-weight: 800; color: $t1;
  letter-spacing: -.3px;
}
.section-title .small { font-size: 11px; color: $t3; font-weight: 500; margin-left: 6px; }
.section-title .more { font-size: 12px; color: $t3; }

/* ━━━━━━━━━━━━━━━ 营销双卡 ━━━━━━━━━━━━━━━ */
.home-feats {
  display: grid; grid-template-columns: 1fr 1fr; gap: 10px;
  margin: 0 14px;
}
.home-feat {
  border-radius: $r-lg;
  padding: 14px; position: relative; overflow: hidden;
  min-height: 130px;
  display: flex; flex-direction: column; justify-content: space-between;
  color: #fff;
}
.home-feat.rank  { background: linear-gradient(135deg, $gold 0%, #B07300 100%); box-shadow: $sh-gold; }
.home-feat.nback { background: linear-gradient(135deg, $o 0%, $o-d 100%); box-shadow: $sh-warm; }
.home-feat .em-bg {
  position: absolute; bottom: -10px; right: -8px;
  font-size: 72px; opacity: .25;
}
.hf-tag {
  display: inline-block;
  padding: 3px 8px; border-radius: 99px;
  background: rgba(255,255,255,.25);
  backdrop-filter: blur(10px);
  font-size: 10px; font-weight: 800; letter-spacing: .5px;
  align-self: flex-start;
  position: relative; z-index: 1;
}
.hf-title {
  font-size: 16px; font-weight: 900; letter-spacing: -.3px;
  line-height: 1.2; position: relative; z-index: 1;
}
.hf-sub {
  font-size: 10px; opacity: .9; margin-top: 2px;
  position: relative; z-index: 1;
}
.hf-bot {
  display: flex; justify-content: space-between; align-items: center;
  position: relative; z-index: 1;
}
.hf-meta { font-size: 10px; opacity: .85; font-weight: 600; }
.hf-meta text { font-size: 14px; font-weight: 900; opacity: 1; display: block; }
.hf-cta {
  padding: 6px 10px; border-radius: 99px;
  background: #fff; color: $t1;
  font-size: 10px; font-weight: 800;
}

/* ━━━━━━━━━━━━━━━ 最近横滚 ━━━━━━━━━━━━━━━ */
.recent-scroll {
  white-space: nowrap;
  padding: 4px 14px 12px;
}
.recent-card {
  display: inline-block; vertical-align: top;
  width: 158px; margin-right: 10px;
  background: $card; border-radius: $r-lg;
  box-shadow: $sh-1; overflow: hidden;
  border: 1px solid $line;
}
.recent-cover {
  height: 84px; position: relative;
  background: linear-gradient(135deg, #FFD8B8, #FF9A4A);
  display: flex; align-items: flex-end; padding: 8px;
}
.recent-cover.t2 { background: linear-gradient(135deg, #FFE8C9, $gold-l); }
.recent-cover.t3 { background: linear-gradient(135deg, #D6F0E5, $mint-l); }
.recent-cover.t4 { background: linear-gradient(135deg, #FFDDE5, #F9A8D4); }
.recent-tag {
  background: rgba(0,0,0,.4);
  color: #fff; border-radius: 99px;
  padding: 2px 9px; font-size: 10px; font-weight: 600;
  position: relative; z-index: 1;
}
.recent-body { padding: 9px 10px 10px; }
.recent-name { font-size: 13px; font-weight: 700; color: $t1; }
.recent-meta {
  display: flex; align-items: center; gap: 5px; margin-top: 4px;
  font-size: 10px; color: $t3;
}
.recent-meta .b { color: $o-d; font-weight: 800; }
.recent-meta .dot { width: 3px; height: 3px; background: $t4; border-radius: 50%; }
.recent-star {
  position: absolute; top: 6px; right: 6px;
  padding: 2px 7px; border-radius: 99px;
  background: linear-gradient(135deg, $gold, $gold-d);
  color: #fff; font-size: 10px; font-weight: 800;
  box-shadow: 0 2px 6px rgba(212,146,10,.4);
}

/* ━━━━━━━━━━━━━━━ 附近商家 2 列 grid（紧凑）━━━━━━━━━━━━━━━ */
.shop-grid {
  padding: 0 14px;
  display: grid; grid-template-columns: 1fr 1fr; gap: 10px;
}
.shop-card-g {
  background: $card;
  border-radius: $r-lg;
  border: 1px solid $line;
  box-shadow: 0 1px 2px rgba(15,23,42,.04), 0 4px 12px rgba(15,23,42,.05);
  overflow: hidden;
  transition: transform .15s ease;
}
.shop-card-g:active { transform: scale(.98); }
.shop-cover {
  height: 96px;
  position: relative;
  /* 无照片时统一暖米色，告别红蓝绿乱跳；有照片优先用真照片 */
  background: linear-gradient(135deg, #FFF5EB, #FFE9D5);
  display: flex; align-items: center; justify-content: center;
  overflow: hidden;
}
.cover-img { width: 100%; height: 100%; }
.cover-em {
  font-size: 42px; font-weight: 800;
  color: $o-d;
  text-shadow: 0 2px 4px rgba(255,107,53,.15);
  letter-spacing: -1px;
}
.shop-status-mini {
  position: absolute; top: 8px; right: 8px;
  display: inline-flex; align-items: center; gap: 4px;
  padding: 3px 8px; border-radius: 99px;
  background: rgba(16,185,129,.95);
  color: #fff; font-size: 9.5px; font-weight: 800;
  backdrop-filter: blur(8px);
}
.shop-status-mini .dot {
  width: 5px; height: 5px; border-radius: 50%;
  background: #fff;
  animation: shop-pulse 1.8s ease-in-out infinite;
}
.shop-status-mini.closed {
  background: rgba(100,116,139,.9);
}
.shop-status-mini.closed .dot { animation: none; }
@keyframes shop-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: .5; transform: scale(.7); }
}
.cover-star {
  position: absolute; top: 8px; left: 8px;
  padding: 3px 8px; border-radius: 99px;
  background: linear-gradient(135deg, $gold, $gold-d);
  color: #fff; font-size: 10px; font-weight: 800;
  box-shadow: 0 2px 8px rgba(212,146,10,.4);
}
.card-body { padding: 10px 12px 12px; }
.card-name {
  font-size: 14px; font-weight: 800; color: $t1;
  letter-spacing: -.3px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.card-promo {
  margin-top: 6px;
  display: inline-block;
  padding: 2px 7px;
  background: linear-gradient(135deg, $o-50, $gold-50);
  color: $o-d;
  font-size: 10px; font-weight: 700;
  border-radius: 4px;
  border: 1px solid $o-100;
}
.card-meta {
  margin-top: 6px;
  display: flex; align-items: center; gap: 8px;
  font-size: 11px; color: $t3;
}
.card-meta .rating { color: $gold-d; font-weight: 700; }

/* ━━━━━━━━━━━━━━━ 旧 shop-card（仍在用于 with-star 等其它位置）━━━━━━━━━━━━━━━ */
.shop-card {
  display: flex; gap: 12px; padding: 12px 14px 12px 18px;
  align-items: center;
  background: $card; border-radius: $r-lg;
  margin: 0 14px 10px;
  box-shadow: 0 1px 2px rgba(15,23,42,.04), 0 4px 12px rgba(15,23,42,.05);
  position: relative; overflow: hidden;
  border: 1px solid $line;
  transition: transform .15s ease, box-shadow .15s ease;
}
.shop-card:active {
  transform: scale(.985);
  box-shadow: 0 1px 2px rgba(15,23,42,.06), 0 2px 6px rgba(15,23,42,.04);
}
.shop-card.has-promo::before {
  content: ''; position: absolute;
  top: 12px; left: 0; bottom: 12px; width: 3px;
  background: linear-gradient(180deg, $o, $gold);
  border-radius: 0 3px 3px 0;
}
.shop-card.with-star {
  flex-direction: column;
  padding: 0; gap: 0;
  align-items: stretch;
}
.shop-card.with-star .shop-head {
  display: flex; gap: 12px;
  padding: 14px 14px 12px;
  align-items: center;
}
.shop-pic {
  flex: 0 0 60px; width: 60px; height: 60px; border-radius: 14px;
  background: linear-gradient(135deg, #FFD1BA, $o);
  color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 26px; font-weight: 800;
  position: relative; overflow: hidden;
  box-shadow: 0 4px 12px rgba(255,107,53,.25), inset 0 1px 0 rgba(255,255,255,.25);
}
.shop-pic::after {
  content: ''; position: absolute; inset: 0;
  background: radial-gradient(circle at 30% 20%, rgba(255,255,255,.35), transparent 50%);
  pointer-events: none;
}
.shop-pic.alt-1 { background: linear-gradient(135deg, #C9E0FF, #6196F0); box-shadow: 0 4px 12px rgba(97,150,240,.25); }
.shop-pic.alt-2 { background: linear-gradient(135deg, #D3F4D3, #4CB84C); box-shadow: 0 4px 12px rgba(76,184,76,.25); }
.shop-pic.alt-3 { background: linear-gradient(135deg, #FFD0DC, #EE5A8B); box-shadow: 0 4px 12px rgba(238,90,139,.25); }
.shop-pic .badge {
  position: absolute; top: -4px; right: -4px;
  padding: 2px 6px;
  background: linear-gradient(135deg, $gold, $gold-l);
  color: #fff; font-size: 9px; font-weight: 800;
  border-radius: 6px;
  box-shadow: 0 2px 8px rgba(212,146,10,.4);
}
.shop-info { flex: 1; min-width: 0; }
.shop-row1 { display: flex; align-items: center; gap: 6px; }
.shop-name {
  font-size: 16px; font-weight: 800; color: $t1; flex: 1;
  letter-spacing: -.3px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.shop-badge {
  background: $o-50; color: $o-d;
  font-size: 10px; padding: 2px 7px; border-radius: 99px;
  font-weight: 700; flex-shrink: 0;
  border: 1px solid $o-100;
}
.shop-badge.gold {
  background: $gold-50; color: $gold-d;
  border-color: rgba(212,146,10,.25);
}
.shop-badge.closed {
  background: $bg-2; color: $t4;
  border-color: $line-d;
}
.shop-promo-row {
  margin-top: 6px;
  display: inline-flex; align-items: center; gap: 4px;
  padding: 3px 8px; border-radius: 6px;
  background: linear-gradient(135deg, $o-50, $gold-50);
  color: $o-d;
  font-size: 11px; font-weight: 700;
  border: 1px solid $o-100;
}
.shop-meta {
  margin-top: 8px; display: flex; align-items: center; gap: 10px;
  font-size: 11.5px; color: $t3; font-weight: 500;
}
.shop-meta .rating { color: $gold; font-weight: 700; }
.shop-meta .dist { color: $mint; font-weight: 700; }

/* 右侧营业状态 pill */
.shop-status {
  flex-shrink: 0;
  display: inline-flex; align-items: center; gap: 5px;
  padding: 5px 10px;
  border-radius: 99px;
  font-size: 10.5px; font-weight: 800;
  background: $mint-50; color: #047857;
}
.shop-status .dot {
  width: 6px; height: 6px; border-radius: 50%;
  background: $mint;
  box-shadow: 0 0 6px $mint;
  animation: shop-pulse 1.8s ease-in-out infinite;
}
.shop-status.closed {
  background: $bg-2; color: $t4;
}
.shop-status.closed .dot {
  background: $t4; box-shadow: none; animation: none;
}
@keyframes shop-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: .5; transform: scale(.8); }
}

/* with-star: 店主推商品 */
.shop-card.with-star .star-prod {
  display: flex; gap: 10px; align-items: center;
  padding: 12px 14px 13px;
  background: linear-gradient(135deg, $bg-3, #fff);
  border-top: 1px dashed $line;
  position: relative;
}
.sp-pic {
  width: 56px; height: 56px; border-radius: $r-md;
  background: linear-gradient(135deg, #FFE5D1, #FFC09A);
  display: flex; align-items: center; justify-content: center;
  font-size: 30px; flex-shrink: 0;
}
.sp-pic.green { background: linear-gradient(135deg, #DFF3DF, #A4DBA4); }
.sp-pic.cream { background: linear-gradient(135deg, #F3E5C5, #E0BD7E); }
.sp-info { flex: 1; min-width: 0; }
.sp-name {
  font-size: 13px; font-weight: 700; color: $t1;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.sp-tag-row { display: flex; gap: 4px; margin-top: 4px; }
.sp-tag {
  font-size: 10px; padding: 2px 6px; border-radius: 4px; font-weight: 700;
}
.sp-tag.promo { background: $o-50; color: $o-d; border: 1px solid $o-100; }
.sp-tag.promo.gold { background: $gold-50; color: $gold-d; border-color: rgba(212,146,10,.25); }
.sp-tag.got { background: $mint-50; color: #047857; border: 1px solid $mint-l; }
.sp-price { text-align: right; flex-shrink: 0; }
.sp-price .v {
  font-size: 18px; font-weight: 900;
  background: linear-gradient(135deg, $o, $o-d);
  -webkit-background-clip: text; background-clip: text; color: transparent;
}
.sp-price .v text { font-size: 11px; }
.sp-price .enter { font-size: 10px; color: $o-d; font-weight: 700; margin-top: 2px; }

/* more-shops */
.more-shops {
  margin: 0 14px;
  padding: 14px 16px;
  background: linear-gradient(135deg, $bg-3, #fff);
  border: 1px dashed $o-100;
  border-radius: $r-lg;
  display: flex; align-items: center; gap: 12px;
}
.ms-em { font-size: 32px; }
.ms-t { flex: 1; }
.ms-t .t { font-size: 14px; font-weight: 800; color: $t1; }
.ms-t .d { font-size: 11px; color: $t3; margin-top: 2px; }
.ms-arrow { font-size: 24px; color: $o-d; font-weight: 800; }

.bottom-pad { height: 12px; }

/* ━━━━━━━━━━━━━━━ 浮动收益球 ━━━━━━━━━━━━━━━ */
.float-earn {
  position: fixed;
  right: 14px; bottom: 92px;
  width: 60px; height: 60px;
  border-radius: 50%;
  background: linear-gradient(135deg, $gold, #B07300);
  color: #fff;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  box-shadow: $sh-gold;
  z-index: 20;
  border: 2px solid #fff;
}
.float-earn .n { font-size: 14px; font-weight: 900; }
.float-earn .l { font-size: 9px; opacity: .9; margin-top: -2px; }
</style>
