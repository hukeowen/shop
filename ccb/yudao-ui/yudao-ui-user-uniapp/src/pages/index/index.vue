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

    <!-- ━━━━━━━━━━ 我今日刚到账 — 上拉负 margin 与 hero 重叠 ━━━━━━━━━━ -->
    <view class="mae-card">
      <view class="mae-head">
        <view class="mae-tag">💎 你今日刚到账</view>
        <text class="mae-more" @click="goWallet">明细 ›</text>
      </view>
      <view class="mae-list">
        <view class="mae-row">
          <view class="mae-ic">🏆</view>
          <view class="mae-body">
            <view class="mae-name">爱家超市 派奖<view class="badge">中奖</view></view>
            <view class="mae-d">月饼礼盒奖池 · <text class="b">14:23</text> · 推广积分</view>
          </view>
          <view class="mae-amt">+10<text>.00</text></view>
        </view>
        <view class="mae-row normal">
          <view class="mae-ic coin">💰</view>
          <view class="mae-body">
            <view class="mae-name">王师傅烤地瓜 · 推 N 反 1 返奖</view>
            <view class="mae-d">第 3 件返奖 · <text class="b">11:08</text> · 推广积分</view>
          </view>
          <view class="mae-amt normal">+2.50</view>
        </view>
        <view class="mae-row normal">
          <view class="mae-ic pt">⭐</view>
          <view class="mae-body">
            <view class="mae-name">王师傅烤地瓜 · 消费积分</view>
            <view class="mae-d">¥10 消费 · <text class="b">10:42</text> · 1 元 = 1 分</view>
          </view>
          <view class="mae-amt normal">+10 分</view>
        </view>
      </view>
      <view class="mae-foot">
        <view class="sum">今日合计入账 <text class="b">¥12.50</text> + <text class="b">10 分</text></view>
        <view class="mae-btn" @click="goWithdraw">💸 提现 →</view>
      </view>
    </view>

    <!-- ━━━━━━━━━━ 5 快入口 ━━━━━━━━━━ -->
    <view class="home-quick">
      <view class="qk" @click="goNearby"><view class="qk-ic">📍</view><view class="qk-text">附近</view></view>
      <view class="qk" @click="goWinners"><view class="qk-ic">🏆<view class="live-dot"></view></view><view class="qk-text">中奖榜</view></view>
      <view class="qk" @click="goQueue"><view class="qk-ic">🔥</view><view class="qk-text">我的队列</view></view>
      <view class="qk" @click="goCoupon"><view class="qk-ic">🎟</view><view class="qk-text">优惠券</view></view>
      <view class="qk" @click="onScan"><view class="qk-ic">📜</view><view class="qk-text">扫码</view></view>
    </view>

    <!-- ━━━━━━━━━━ 推 N 反 1 进行中提醒 ━━━━━━━━━━ -->
    <view class="home-queue-tip" @click="goQueue">
      <view class="hqt-ic">🔥</view>
      <view class="hqt-body">
        <view class="hqt-t">老张水果摊 · 阳光玫瑰 <text class="b">还差 1 人即出队 +¥46.40</text></view>
        <view class="hqt-d">分享给朋友扫码下单 → 你立即出队拿全额</view>
      </view>
      <view class="hqt-cta">分享 →</view>
    </view>

    <!-- ━━━━━━━━━━ 5 分类 ━━━━━━━━━━ -->
    <view class="home-cats">
      <view class="home-cat" @click="goCategory('food')"><view class="em">🍔</view><view class="l">餐饮</view></view>
      <view class="home-cat" @click="goCategory('tea')"><view class="em">🍵</view><view class="l">茶饮</view></view>
      <view class="home-cat" @click="goCategory('bake')"><view class="em">🍰</view><view class="l">烘焙</view></view>
      <view class="home-cat" @click="goCategory('fresh')"><view class="em">🍇</view><view class="l">生鲜</view></view>
      <view class="home-cat" @click="goCategory('beauty')"><view class="em">💆</view><view class="l">美容</view></view>
    </view>

    <!-- ━━━━━━━━━━ 营销双卡 ━━━━━━━━━━ -->
    <view class="section-title">
      <view class="h3">玩法专区 <text class="small">商户派奖 · 1:1 提现</text></view>
    </view>
    <view class="home-feats">
      <view class="home-feat rank" @click="goWinners">
        <text class="em-bg">🏆</text>
        <view class="hf-tag">🏆 中奖公榜</view>
        <view>
          <view class="hf-title">看谁刚拿到奖</view>
          <view class="hf-sub">榜一排名 · 按店</view>
        </view>
        <view class="hf-bot">
          <view class="hf-meta">今日派奖 <text>¥482</text></view>
          <view class="hf-cta">查看榜单 →</view>
        </view>
      </view>
      <view class="home-feat nback" @click="goQueue">
        <text class="em-bg">🔥</text>
        <view class="hf-tag">🔥 推 N 反 1</view>
        <view>
          <view class="hf-title">买 4 件 免单 1 件</view>
          <view class="hf-sub">朋友买你也得返</view>
        </view>
        <view class="hf-bot">
          <view class="hf-meta">在队列 <text>3 个</text></view>
          <view class="hf-cta">查看 →</view>
        </view>
      </view>
    </view>

    <!-- ━━━━━━━━━━ 最近去过 横滚 ━━━━━━━━━━ -->
    <view class="section-title">
      <view class="h3">最近去过</view>
      <text class="more" @click="goNearby">全部 ›</text>
    </view>
    <scroll-view scroll-x class="recent-scroll">
      <view class="recent-card">
        <view class="recent-cover"><view class="recent-tag">2 小时前</view></view>
        <view class="recent-body"><view class="recent-name">王师傅烤地瓜</view>
          <view class="recent-meta"><text>已下 <text class="b">5 单</text></text><view class="dot"></view><text>0.3km</text></view>
        </view>
      </view>
      <view class="recent-card">
        <view class="recent-cover t2"><view class="recent-tag">昨天</view></view>
        <view class="recent-body"><view class="recent-name">林家茶馆</view>
          <view class="recent-meta"><text>已下 <text class="b">2 单</text></text><view class="dot"></view><text>1.1km</text></view>
        </view>
      </view>
      <view class="recent-card">
        <view class="recent-cover t3"><view class="recent-tag">3 天前</view></view>
        <view class="recent-body"><view class="recent-name">老张水果摊</view>
          <view class="recent-meta"><text>已下 <text class="b">1 单</text></text><view class="dot"></view><text>0.8km</text></view>
        </view>
      </view>
      <view class="recent-card">
        <view class="recent-cover t4"><view class="recent-tag">上周</view></view>
        <view class="recent-body"><view class="recent-name">小陈奶茶店</view>
          <view class="recent-meta"><text>已下 <text class="b">8 单</text></text><view class="dot"></view><text>2.0km</text></view>
        </view>
      </view>
    </scroll-view>

    <!-- ━━━━━━━━━━ 附近商家 — 3 个 shop-card with star-prod ━━━━━━━━━━ -->
    <view class="section-title">
      <view class="h3">附近商家 <text class="small">店主推明星商品</text></view>
      <text class="more" @click="goNearby">全部 ›</text>
    </view>

    <!-- 店 ① 王师傅烤地瓜 + 推 N 反 1 -->
    <view class="shop-card has-promo with-star">
      <view class="shop-head">
        <view class="shop-pic">王<view class="badge">🏆</view></view>
        <view class="shop-info">
          <view class="shop-row1">
            <text class="shop-name">王师傅烤地瓜</text>
            <view class="shop-badge gold">⭐ 3 星</view>
          </view>
          <view class="shop-promo-row">推 4 反 1 · 已 ¥5 / 共 ¥10</view>
          <view class="shop-meta">
            <text class="rating">★ 4.9</text>
            <text class="dist">📍 0.3km</text>
            <text>月售 1280</text>
          </view>
        </view>
      </view>
      <view class="star-prod">
        <view class="sp-pic">🍠</view>
        <view class="sp-info">
          <view class="sp-name">现烤蜜薯（大）· 流糖心</view>
          <view class="sp-tag-row">
            <view class="sp-tag promo">推 4 反 1</view>
            <view class="sp-tag got">你已得 ¥5</view>
          </view>
        </view>
        <view class="sp-price">
          <view class="v">¥10<text>.00</text></view>
          <view class="enter">进店 →</view>
        </view>
      </view>
    </view>

    <!-- 店 ② 林家茶馆 + 派奖池 -->
    <view class="shop-card has-promo with-star gold-star">
      <view class="shop-head">
        <view class="shop-pic alt-1">林</view>
        <view class="shop-info">
          <view class="shop-row1">
            <text class="shop-name">林家茶馆</text>
            <view class="shop-badge">新店送 ¥5</view>
          </view>
          <view class="shop-promo-row give">商户派奖中 · 今日已发 ¥120</view>
          <view class="shop-meta">
            <text class="rating">★ 4.7</text>
            <text class="dist">📍 1.1km</text>
            <text>月售 320</text>
          </view>
        </view>
      </view>
      <view class="star-prod">
        <view class="sp-pic green">🍵</view>
        <view class="sp-info">
          <view class="sp-name">老白茶 100g 礼盒装 · 陈期 5 年</view>
          <view class="sp-tag-row">
            <view class="sp-tag promo gold">派奖池</view>
            <view class="sp-tag got">下单进派奖名单</view>
          </view>
        </view>
        <view class="sp-price">
          <view class="v">¥168<text>.00</text></view>
          <view class="enter">进店 →</view>
        </view>
      </view>
    </view>

    <!-- 店 ③ 老张水果摊 + 快出队 -->
    <view class="shop-card has-promo with-star">
      <view class="shop-head">
        <view class="shop-pic alt-2">张</view>
        <view class="shop-info">
          <view class="shop-row1">
            <text class="shop-name">老张水果摊</text>
            <view class="shop-badge gold">⭐ 2 星</view>
          </view>
          <view class="shop-promo-row">推 5 反 1 · 还差 1 人出队</view>
          <view class="shop-meta">
            <text class="rating">★ 4.8</text>
            <text class="dist">📍 0.8km</text>
            <text>月售 2150</text>
          </view>
        </view>
      </view>
      <view class="star-prod">
        <view class="sp-pic cream">🍇</view>
        <view class="sp-info">
          <view class="sp-name">阳光玫瑰葡萄 · 1.5 斤装</view>
          <view class="sp-tag-row">
            <view class="sp-tag promo">推 5 反 1</view>
            <view class="sp-tag got">⚡ 你队列差 1 人出队</view>
          </view>
        </view>
        <view class="sp-price">
          <view class="v">¥58<text>.00</text></view>
          <view class="enter">进店 →</view>
        </view>
      </view>
    </view>

    <!-- 更多店铺入口 -->
    <view class="more-shops" @click="goNearby">
      <view class="ms-em">🏪</view>
      <view class="ms-t">
        <view class="t">查看附近所有店铺</view>
        <view class="d">本商圈共 18 家 · 按距离 / 销量 / 推 N 反 1 排序</view>
      </view>
      <view class="ms-arrow">›</view>
    </view>

    <view class="bottom-pad"></view>

    <!-- 浮动收益球 -->
    <view class="float-earn" @click="goWallet">
      <view class="n">12.50</view>
      <view class="l">今日</view>
    </view>

    <bottom-nav active="index" :cart-count="3" />
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
import { useUserStore } from '@/store/user.js';

const user = useUserStore();
const avatarText = computed(() => (user.nickname?.[0] || '小'));

const greeting = computed(() => {
  const h = new Date().getHours();
  if (h < 5)  return '凌晨好';
  if (h < 11) return '早上好';
  if (h < 14) return '中午好';
  if (h < 18) return '下午好';
  return '晚上好';
});
const greetTitle = computed(() => `想吃点什么，${user.nickname || '小明'}？`);

const tickerText = ref([
  '爱家超市 派奖给 138****6789 ¥10.00',
  '王师傅烤地瓜 推 4 反 1 出队 156****1234 拿到 ¥10.00',
  '老张水果摊 派奖给 186****0001 ¥58.00',
  '林家茶馆 派奖给 139****8888 ¥5.00',
]);

function goSearch() { uni.navigateTo({ url: '/pages/search/index' }); }
function goNearby() { uni.navigateTo({ url: '/pages/nearby/index' }); }
function goWinners() { uni.reLaunch({ url: '/pages/winners/index' }); }
function goQueue() { uni.navigateTo({ url: '/pages/queue/index' }); }
function goCoupon() { uni.navigateTo({ url: '/pages/coupon/index' }); }
function goWallet() { uni.navigateTo({ url: '/pages/wallet/index' }); }
function goWithdraw() { uni.navigateTo({ url: '/pages/withdraw/index' }); }
function goCategory(k) { uni.navigateTo({ url: `/pages/category/index?k=${k}` }); }
function onScan() {
  // #ifdef MP-WEIXIN || APP-PLUS
  uni.scanCode({ success: (r) => uni.showToast({ title: r.result, icon: 'none' }) });
  // #endif
  // #ifdef H5
  uni.showToast({ title: 'H5 不支持扫码，请用 APP/小程序', icon: 'none' });
  // #endif
}
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
  padding: 8px 18px 90px;
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

/* ━━━━━━━━━━━━━━━ MAE 卡（上拉负 margin 与 hero 重叠）━━━━━━━━━━━━━━━ */
.mae-card {
  margin: -76px 14px 0;
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

/* ━━━━━━━━━━━━━━━ 分类 5 格 ━━━━━━━━━━━━━━━ */
.home-cats {
  display: grid; grid-template-columns: repeat(5, 1fr); gap: 6px;
  margin: 14px 14px 0; padding: 4px 0;
}
.home-cat { text-align: center; padding: 8px 0; }
.home-cat .em { font-size: 24px; }
.home-cat .l { font-size: 11px; color: $t2; font-weight: 500; margin-top: 4px; }

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

/* ━━━━━━━━━━━━━━━ 店铺卡（含 with-star 店主推商品）━━━━━━━━━━━━━━━ */
.shop-card {
  display: flex; gap: 12px; padding: 14px;
  background: $card; border-radius: $r-lg;
  margin: 0 14px 10px;
  box-shadow: $sh-1;
  position: relative; overflow: hidden;
}
.shop-card.has-promo::before {
  content: ''; position: absolute;
  top: 0; left: 0; bottom: 0; width: 3px;
  background: linear-gradient(180deg, $o, $gold);
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
  flex: 0 0 70px; width: 70px; height: 70px; border-radius: $r-md;
  background: linear-gradient(135deg, #FFD1BA, $o);
  color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 28px; font-weight: 800;
  position: relative;
  box-shadow: 0 4px 12px rgba(255,107,53,.25);
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
  font-size: 15px; font-weight: 700; color: $t1; flex: 1;
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
  margin-top: 6px; display: flex; align-items: center; gap: 10px;
  font-size: 11px; color: $t3;
}
.shop-meta .rating { color: $gold; font-weight: 700; }
.shop-meta .dist { color: $mint; font-weight: 700; }

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
