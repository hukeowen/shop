<template>
  <view class="page">
    <view class="hero">
      <view class="hero-bg"></view>
      <view class="greet-row">
        <view class="avatar">{{ avatarText }}</view>
        <view class="greet">
          <view class="hi">{{ greeting }} ☀</view>
          <view class="name">想吃点什么，{{ nickname || '小客' }}？</view>
        </view>
        <view class="head-ics">
          <view class="head-ic" @click="goNearby">📍</view>
          <view class="head-ic">🔔<view class="dot"></view></view>
        </view>
      </view>

      <view class="search" @click="goSearch">
        <text class="ic">🔍</text>
        <text class="ph">搜店铺、找商品、看附近</text>
        <text class="voice">🎙</text>
      </view>

      <view class="ticker">
        <text class="trophy">🏆</text>
        <view class="roll">
          <view class="roll-track">
            <text v-for="(t, i) in tickerItems" :key="i" class="t-item">{{ t }} · </text>
          </view>
        </view>
      </view>
    </view>

    <!-- 我今日刚到账 -->
    <view class="mae-card" v-if="todayRecords.length || loadingMae">
      <view class="mae-head">
        <text class="mae-tag">💎 你今日刚到账</text>
        <text class="mae-more" @click="goWallet">明细 ›</text>
      </view>
      <view class="mae-list">
        <view v-for="r in todayRecords" :key="r.id" class="mae-row" :class="{ normal: !r.highlight }">
          <view class="mae-ic" :class="r.iconClass">{{ r.icon }}</view>
          <view class="mae-body">
            <view class="mae-name">{{ r.title }}<text v-if="r.badge" class="badge">{{ r.badge }}</text></view>
            <view class="mae-d">{{ r.desc }}</view>
          </view>
          <view class="mae-amt" :class="{ normal: !r.highlight }">+{{ r.amount }}</view>
        </view>
      </view>
      <view class="mae-foot">
        <view class="sum">今日合计入账 <text class="b">¥{{ todaySum.yuan }}</text> + <text class="b">{{ todaySum.points }} 分</text></view>
        <view class="mae-btn" @click="goWithdraw">💸 提现 →</view>
      </view>
    </view>

    <!-- 快入口 -->
    <view class="quick">
      <view class="qk" @click="goNearby"><view class="qk-ic">📍</view><text>附近</text></view>
      <view class="qk" @click="goWinners"><view class="qk-ic">🏆<view class="live-dot"></view></view><text>中奖榜</text></view>
      <view class="qk" @click="goQueue"><view class="qk-ic">🔥</view><text>我的队列</text></view>
      <view class="qk" @click="goCoupon"><view class="qk-ic">🎟</view><text>优惠券</text></view>
      <view class="qk" @click="onScan"><view class="qk-ic">📜</view><text>扫码</text></view>
    </view>

    <!-- 推 N 反 1 队列提醒 -->
    <view v-if="queueTip" class="hqt" @click="goQueue">
      <view class="hqt-ic">🔥</view>
      <view class="hqt-body">
        <view class="hqt-t">{{ queueTip.shopName }} · {{ queueTip.spuName }} <text class="b">还差 {{ queueTip.gap }} 人即出队 +¥{{ queueTip.amount }}</text></view>
        <view class="hqt-d">分享给朋友扫码下单 → 你立即出队拿全额</view>
      </view>
      <view class="hqt-cta">分享 →</view>
    </view>

    <!-- 分类 -->
    <view class="home-cats">
      <view v-for="c in categories" :key="c.k" class="home-cat" @click="goCategory(c)">
        <view class="em">{{ c.em }}</view>
        <text class="l">{{ c.label }}</text>
      </view>
    </view>

    <!-- 营销双卡 -->
    <view class="section-title"><text class="h">玩法专区</text><text class="s">商户派奖 · 1:1 提现</text></view>
    <view class="feats">
      <view class="feat rank" @click="goWinners">
        <text class="em-bg">🏆</text>
        <view class="hf-tag">🏆 中奖公榜</view>
        <view class="hf-title">看谁刚拿到奖</view>
        <view class="hf-sub">榜一排名 · 按店</view>
        <view class="hf-bot">
          <text class="hf-meta">今日派奖 <text class="b">¥{{ stat.todayAward }}</text></text>
          <text class="hf-cta">查看 →</text>
        </view>
      </view>
      <view class="feat nback" @click="goQueue">
        <text class="em-bg">🔥</text>
        <view class="hf-tag">🔥 推 N 反 1</view>
        <view class="hf-title">买 N 件 免单 1 件</view>
        <view class="hf-sub">朋友买你也得返</view>
        <view class="hf-bot">
          <text class="hf-meta">在队列 <text class="b">{{ stat.myQueueCount }} 个</text></text>
          <text class="hf-cta">查看 →</text>
        </view>
      </view>
    </view>

    <!-- 最近去过 -->
    <view v-if="recentShops.length" class="section-title">
      <text class="h">最近去过</text>
      <text class="more" @click="goNearby">全部 ›</text>
    </view>
    <scroll-view v-if="recentShops.length" scroll-x class="recent">
      <view v-for="s in recentShops" :key="s.id" class="recent-card" @click="goShop(s)">
        <view class="recent-cover" :style="{ background: s.cover || coverColor(s.id) }">
          <text class="recent-tag">{{ s.lastVisit || '最近' }}</text>
        </view>
        <view class="recent-body">
          <view class="recent-name">{{ s.name }}</view>
          <view class="recent-meta">
            <text>已下 <text class="b">{{ s.orderCount || 0 }} 单</text></text>
            <text class="dot"></text>
            <text>{{ s.distance || '—' }}</text>
          </view>
        </view>
      </view>
    </scroll-view>

    <!-- 附近商家 -->
    <view class="section-title">
      <text class="h">附近商家</text>
      <text class="s">店主推明星商品</text>
      <text class="more" @click="goNearby">全部 ›</text>
    </view>
    <view v-if="loadingShops" class="loading">加载中…</view>
    <empty-state v-else-if="!nearbyShops.length" icon="🏪" title="附近暂无店铺" desc="换个位置或拉远范围试试" />
    <view v-else>
      <view v-for="s in nearbyShops" :key="s.id" class="shop-card" @click="goShop(s)">
        <view class="shop-head">
          <view class="shop-pic">{{ s.name?.[0] || '店' }}</view>
          <view class="shop-info">
            <view class="shop-row1">
              <text class="shop-name">{{ s.name }}</text>
              <text v-if="s.star" class="shop-badge gold">⭐ {{ s.star }} 星</text>
            </view>
            <view v-if="s.promoLine" class="promo-row">{{ s.promoLine }}</view>
            <view class="shop-meta">
              <text class="rating">★ {{ s.rating || '4.8' }}</text>
              <text class="dist">📍 {{ s.distance || '—' }}</text>
              <text>月售 {{ s.monthSold || '—' }}</text>
            </view>
          </view>
        </view>
        <view v-if="s.starSpu" class="star-prod">
          <view class="sp-pic">{{ s.starSpu.em || '🛍' }}</view>
          <view class="sp-info">
            <view class="sp-name">{{ s.starSpu.name }}</view>
            <view class="sp-tag-row">
              <text v-if="s.starSpu.promo" class="promo">{{ s.starSpu.promo }}</text>
              <text v-if="s.starSpu.got" class="got">{{ s.starSpu.got }}</text>
            </view>
          </view>
          <view class="sp-price">
            <view class="v">¥{{ s.starSpu.price }}</view>
            <text class="enter">进店 →</text>
          </view>
        </view>
      </view>
    </view>

    <view class="bottom-pad"></view>

    <!-- 浮动收益球 -->
    <view v-if="todaySum.yuan > 0" class="float-earn" @click="goWallet">
      <view class="n">{{ todaySum.yuan }}</view>
      <view class="l">今日</view>
    </view>

    <bottom-nav active="index" />
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useUserStore } from '@/store/user.js';
// import { listNearbyShops } from '@/api/shop.js';
// import { listPromoRecords, getMyQueue } from '@/api/promo.js';

const user = useUserStore();

const nickname = computed(() => user.nickname || '');
const avatarText = computed(() => (user.nickname?.[0]) || '客');
const greeting = computed(() => {
  const h = new Date().getHours();
  if (h < 5)  return '凌晨好';
  if (h < 11) return '早上好';
  if (h < 14) return '中午好';
  if (h < 18) return '下午好';
  return '晚上好';
});

const tickerItems = ref([
  '爱家超市派奖 138****6789 ¥10',
  '王师傅烤地瓜 推 4 反 1 出队 ¥10',
  '老张水果摊派奖 ¥58',
  '林家茶馆派奖 ¥5',
]);

const loadingMae = ref(false);
const todayRecords = ref([]);
const todaySum = ref({ yuan: '0.00', points: 0 });

const stat = ref({ todayAward: '482', myQueueCount: 0 });

const queueTip = ref(null);

const categories = ref([
  { k: 'food',   em: '🍔', label: '餐饮' },
  { k: 'tea',    em: '🍵', label: '茶饮' },
  { k: 'bake',   em: '🍰', label: '烘焙' },
  { k: 'fresh',  em: '🍇', label: '生鲜' },
  { k: 'beauty', em: '💆', label: '美容' },
]);

const recentShops = ref([]);
const loadingShops = ref(false);
const nearbyShops = ref([]);

function coverColor(id) {
  const colors = ['linear-gradient(135deg,#FF6B35,#E25316)', 'linear-gradient(135deg,#D4920A,#A66E00)', 'linear-gradient(135deg,#10B981,#0E9E6D)', 'linear-gradient(135deg,#6366F1,#4F46E5)'];
  return colors[(id || 0) % colors.length];
}

function goSearch() { uni.navigateTo({ url: '/pages/search/index' }); }
function goNearby() { uni.reLaunch({ url: '/pages/nearby/index' }); }
function goWinners() { uni.reLaunch({ url: '/pages/winners/index' }); }
function goQueue() { uni.navigateTo({ url: '/pages/queue/index' }); }
function goCoupon() { uni.navigateTo({ url: '/pages/coupon/index' }); }
function goWallet() { uni.navigateTo({ url: '/pages/wallet/index' }); }
function goWithdraw() { uni.navigateTo({ url: '/pages/withdraw/index' }); }
function goCategory(c) { uni.navigateTo({ url: `/pages/category/index?k=${c.k}` }); }
function goShop(s) { uni.navigateTo({ url: `/pages/shop/home?id=${s.id}&tenantId=${s.tenantId || s.id}` }); }
function onScan() {
  // #ifdef MP-WEIXIN || APP-PLUS
  uni.scanCode({ success: (r) => { uni.showToast({ title: r.result, icon: 'none' }); } });
  // #endif
  // #ifdef H5
  uni.showToast({ title: 'H5 不支持扫码，请用 APP/小程序', icon: 'none' });
  // #endif
}

onMounted(async () => {
  // TODO: 接 listNearbyShops / listPromoRecords / getMyQueue
});
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.page { min-height: 100vh; padding-bottom: 90px; background: $bg; }
.hero {
  position: relative;
  padding: 18px 14px 12px;
  margin-bottom: 12px;
  &::before {
    content: ''; position: absolute; inset: 0;
    background:
      radial-gradient(500px 200px at 30% 0%, rgba(255,107,53,.22), transparent 60%),
      linear-gradient(180deg, #18130E 0%, #2A1A0F 100%);
    z-index: -1;
    border-bottom-left-radius: 28px;
    border-bottom-right-radius: 28px;
  }
}
.greet-row { display: flex; align-items: center; gap: 12px; padding: 6px 4px; }
.avatar {
  width: 42px; height: 42px; border-radius: 50%;
  background: linear-gradient(135deg, $o, $gold);
  color: #fff; display: flex; align-items: center; justify-content: center;
  font-weight: 700; font-size: 16px;
}
.greet { flex: 1; }
.greet .hi { color: rgba(255,255,255,.7); font-size: 12px; }
.greet .name { color: #fff; font-size: 16px; font-weight: 700; margin-top: 2px; }
.head-ics { display: flex; gap: 10px; }
.head-ic {
  width: 36px; height: 36px; border-radius: 50%;
  background: rgba(255,255,255,.12); color: #fff;
  display: flex; align-items: center; justify-content: center;
  position: relative; font-size: 16px;
}
.head-ic .dot {
  position: absolute; top: 8px; right: 8px;
  width: 6px; height: 6px; border-radius: 50%;
  background: $danger;
}

.search {
  margin-top: 12px;
  display: flex; align-items: center;
  background: rgba(255,255,255,.96);
  border-radius: 16px; padding: 10px 14px;
  gap: 8px; box-shadow: $sh-2;
}
.search .ic { color: $o; font-size: 16px; }
.search .ph { flex: 1; color: $t4; font-size: 14px; }
.search .voice { color: $o; font-size: 16px; }

.ticker {
  margin-top: 10px;
  display: flex; align-items: center; gap: 6px;
  color: rgba(255,255,255,.85);
  font-size: 12px;
  background: rgba(255,255,255,.06);
  border-radius: 10px; padding: 6px 10px;
}
.ticker .trophy { color: $gold-l; }
.ticker .roll { flex: 1; overflow: hidden; }
.ticker .t-item { white-space: nowrap; }

.mae-card {
  margin: 0 14px 12px; padding: 16px;
  background: $card; border-radius: $r-lg;
  box-shadow: $sh-2;
}
.mae-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.mae-tag { font-size: 14px; font-weight: 800; color: $t1; }
.mae-more { font-size: 12px; color: $o; }
.mae-row {
  display: flex; align-items: center; gap: 10px;
  padding: 8px 0;
  border-bottom: 1px dashed $line;
  &:last-child { border-bottom: none; }
}
.mae-ic {
  width: 36px; height: 36px; border-radius: 10px;
  background: $o-50; color: $o;
  display: flex; align-items: center; justify-content: center;
  font-size: 16px;
  &.coin { background: $gold-50; color: $gold-d; }
  &.pt   { background: $mint-50; color: $mint; }
}
.mae-body { flex: 1; min-width: 0; }
.mae-name { font-size: 14px; font-weight: 700; color: $t1; }
.mae-name .badge {
  margin-left: 6px;
  background: linear-gradient(135deg, $danger, #C72030);
  color: #fff; font-size: 10px; padding: 2px 6px; border-radius: 6px;
}
.mae-d { font-size: 11px; color: $t3; margin-top: 2px; }
.mae-amt {
  font-size: 18px; font-weight: 900;
  background: linear-gradient(135deg, $o, $o-d);
  -webkit-background-clip: text; background-clip: text; color: transparent;
  &.normal { font-size: 15px; color: $t2; background: none; -webkit-text-fill-color: initial; }
}
.mae-foot {
  margin-top: 12px;
  display: flex; align-items: center; justify-content: space-between;
  font-size: 12px; color: $t2;
}
.mae-foot .sum .b { color: $o; font-weight: 800; }
.mae-btn {
  padding: 8px 16px; border-radius: $r-pill;
  background: linear-gradient(135deg, $o, $o-d); color: #fff;
  font-size: 12px; font-weight: 700;
  box-shadow: $sh-warm;
}

.quick {
  display: flex; padding: 0 8px; gap: 6px; margin-bottom: 12px;
}
.qk {
  flex: 1; padding: 10px 0;
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  background: $card; border-radius: $r-md; box-shadow: $sh-1;
  font-size: 12px; color: $t2; font-weight: 600;
}
.qk-ic {
  width: 36px; height: 36px; border-radius: 10px;
  background: $o-50; color: $o;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px; position: relative;
}
.qk-ic .live-dot {
  position: absolute; top: 4px; right: 4px;
  width: 7px; height: 7px; border-radius: 50%;
  background: $danger; box-shadow: 0 0 0 3px rgba(230,57,70,.25);
}

.hqt {
  margin: 0 14px 12px; padding: 12px 14px;
  display: flex; align-items: center; gap: 10px;
  background: linear-gradient(135deg, #FFF1EB, #FFE6D9);
  border-radius: $r-lg;
  box-shadow: $sh-warm;
}
.hqt-ic { font-size: 22px; }
.hqt-body { flex: 1; min-width: 0; }
.hqt-t { font-size: 13px; font-weight: 700; color: $t1; line-height: 1.4; }
.hqt-t .b { color: $o; }
.hqt-d { font-size: 11px; color: $t3; margin-top: 3px; }
.hqt-cta {
  padding: 8px 14px; border-radius: $r-pill;
  background: linear-gradient(135deg, $o, $o-d); color: #fff;
  font-size: 12px; font-weight: 700;
}

.home-cats {
  display: flex; padding: 0 14px; gap: 10px; margin-bottom: 14px;
}
.home-cat {
  flex: 1; padding: 12px 0;
  background: $card; border-radius: $r-md; box-shadow: $sh-1;
  display: flex; flex-direction: column; align-items: center; gap: 4px;
}
.home-cat .em { font-size: 22px; }
.home-cat .l { font-size: 11px; color: $t2; font-weight: 600; }

.section-title {
  display: flex; align-items: baseline;
  padding: 14px 16px 8px;
  .h { font-size: 16px; font-weight: 800; color: $t1; }
  .s { margin-left: 8px; font-size: 11px; color: $t4; }
  .more { margin-left: auto; font-size: 12px; color: $o; }
}

.feats {
  display: flex; padding: 0 14px; gap: 10px; margin-bottom: 14px;
}
.feat {
  flex: 1; padding: 14px 12px;
  border-radius: $r-lg;
  position: relative; overflow: hidden;
  color: #fff;
  min-height: 120px;
  display: flex; flex-direction: column;
}
.feat.rank  { background: linear-gradient(135deg, #D4920A, #A66E00); box-shadow: $sh-gold; }
.feat.nback { background: linear-gradient(135deg, $o, $o-d); box-shadow: $sh-warm; }
.em-bg {
  position: absolute; right: -8px; bottom: -10px;
  font-size: 72px; opacity: .18;
}
.hf-tag { font-size: 11px; font-weight: 800; opacity: .9; }
.hf-title { font-size: 16px; font-weight: 900; margin-top: 4px; }
.hf-sub { font-size: 11px; opacity: .8; margin-top: 2px; }
.hf-bot { margin-top: auto; padding-top: 12px; display: flex; align-items: baseline; justify-content: space-between; }
.hf-meta { font-size: 11px; opacity: .9; }
.hf-meta .b { font-weight: 800; font-size: 13px; }
.hf-cta { font-size: 11px; font-weight: 700; }

.recent {
  white-space: nowrap; padding: 0 14px; margin-bottom: 6px;
}
.recent-card {
  display: inline-block;
  width: 140px; margin-right: 10px;
  background: $card; border-radius: $r-md; box-shadow: $sh-1;
  overflow: hidden;
}
.recent-cover {
  height: 76px; position: relative;
  background: linear-gradient(135deg, $o, $o-d);
}
.recent-tag {
  position: absolute; top: 6px; right: 6px;
  background: rgba(0,0,0,.4); color: #fff;
  font-size: 10px; padding: 2px 6px; border-radius: 6px;
}
.recent-body { padding: 8px 10px; }
.recent-name { font-size: 13px; font-weight: 700; color: $t1; }
.recent-meta { display: flex; align-items: center; gap: 4px; font-size: 11px; color: $t3; margin-top: 4px; }
.recent-meta .b { color: $o; font-weight: 700; }
.recent-meta .dot { width: 3px; height: 3px; border-radius: 50%; background: $line-d; }

.shop-card {
  margin: 0 14px 10px; padding: 12px;
  background: $card; border-radius: $r-lg; box-shadow: $sh-1;
}
.shop-head { display: flex; gap: 12px; }
.shop-pic {
  width: 56px; height: 56px; border-radius: 14px;
  background: linear-gradient(135deg, $o, $o-d); color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-weight: 800; font-size: 22px;
}
.shop-info { flex: 1; min-width: 0; }
.shop-row1 { display: flex; align-items: center; gap: 6px; }
.shop-name { font-size: 15px; font-weight: 800; color: $t1; }
.shop-badge {
  font-size: 10px; padding: 2px 6px; border-radius: 6px;
  background: $bg-2; color: $t3; font-weight: 700;
  &.gold { background: $gold-50; color: $gold-d; }
}
.promo-row {
  margin-top: 4px;
  font-size: 11px; color: $o; font-weight: 700;
  background: $o-50; padding: 3px 8px; border-radius: 6px;
  display: inline-block;
}
.shop-meta {
  display: flex; gap: 8px; margin-top: 6px;
  font-size: 11px; color: $t3;
  .rating { color: $gold-d; font-weight: 700; }
}

.star-prod {
  margin-top: 10px; padding-top: 10px;
  display: flex; align-items: center; gap: 10px;
  border-top: 1px dashed $line;
}
.sp-pic {
  width: 48px; height: 48px; border-radius: $r-md;
  background: $o-50; color: $o;
  display: flex; align-items: center; justify-content: center;
  font-size: 22px;
}
.sp-info { flex: 1; min-width: 0; }
.sp-name { font-size: 13px; font-weight: 700; color: $t1; }
.sp-tag-row { display: flex; gap: 4px; margin-top: 4px; }
.sp-tag-row .promo {
  font-size: 10px; padding: 2px 6px; border-radius: 4px;
  background: $o-50; color: $o-d; font-weight: 700;
  &.gold { background: $gold-50; color: $gold-d; }
}
.sp-tag-row .got {
  font-size: 10px; padding: 2px 6px; border-radius: 4px;
  background: $mint-50; color: $mint; font-weight: 700;
}
.sp-price { text-align: right; }
.sp-price .v {
  font-size: 16px; font-weight: 800;
  background: linear-gradient(135deg, $o, $o-d);
  -webkit-background-clip: text; background-clip: text; color: transparent;
}
.sp-price .enter { font-size: 11px; color: $o; font-weight: 700; }

.loading { padding: 30px; text-align: center; color: $t4; font-size: 12px; }
.bottom-pad { height: 24px; }

.float-earn {
  position: fixed;
  right: 14px; bottom: 90px;
  width: 60px; height: 60px;
  border-radius: 50%;
  background: linear-gradient(135deg, $o, $gold);
  color: #fff;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  box-shadow: $sh-warm;
  z-index: 20;
  .n { font-size: 14px; font-weight: 900; }
  .l { font-size: 9px; opacity: .8; }
}
</style>
