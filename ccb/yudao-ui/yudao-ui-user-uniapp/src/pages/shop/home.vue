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
          <view class="sh-ic" @click="onShare">↗</view>
        </view>
      </view>
      <view v-if="shop" class="sh-info">
        <view class="sh-tag-row">🔥 {{ shop.tagRow || '扫码下单 · 每单返推广积分' }}</view>
        <view class="sh-name">{{ shop.shopName || shop.name || '店铺' }}</view>
        <view class="sh-slogan">{{ shop.slogan || '商户实时派奖 · 1:1 现金提现' }}</view>
      </view>
    </view>

    <!-- ━━━━━━━━━━ 3 列统计卡（上拉 -36px 与 cover 重叠） ━━━━━━━━━━ -->
    <view class="sh-info-card">
      <view class="sh-stat-row">
        <view class="sh-stat">
          <view class="v gold"><text class="star">★</text>{{ shop?.rating || '5.0' }}</view>
          <view class="l">{{ shop?.ratingCount ? shop.ratingCount + ' 评分' : '新店' }}</view>
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

    <!-- ━━━━━━━━━━ 商户派奖动态 ━━━━━━━━━━ -->
    <view v-if="giveStat.amount > 0" class="shop-give-card" @click="goWinners">
      <view class="sgc-em">🎁</view>
      <view class="sgc-body">
        <view class="sgc-t">本店<text class="b">正在派奖</text> · 今日已发 ¥{{ giveStat.amount }} / {{ giveStat.count }} 人</view>
        <view class="sgc-d">
          <text class="live">派奖中</text>
          <text>· 下单即纳入派奖名单</text>
        </view>
      </view>
      <view class="sgc-cta">查看榜 →</view>
    </view>

    <!-- ━━━━━━━━━━ 推 N 反 1 主推大卡 ━━━━━━━━━━ -->
    <view v-if="nback" class="nback-card">
      <view class="nb-deco">🔥</view>
      <view class="nb-head">
        <view class="nb-l">
          <view class="nb-tag">推 {{ nback.n }} 反 1 · 不会差一刀</view>
          <view class="nb-t">买够 {{ nback.n }} 件，<text class="h">1 件免单</text></view>
          <view class="nb-sub">推广积分 1:1 提现 · 实在透明</view>
        </view>
        <view class="nb-money">
          <view class="nb-mv">¥{{ nback.gotYuan }}</view>
          <view class="nb-ml">已 拿 / 共 ¥{{ nback.totalYuan }}</view>
        </view>
      </view>
      <view class="nb-bar">
        <view class="nb-fill" :style="{ width: nback.pct + '%' }"></view>
        <view class="nb-txt">已完成 {{ nback.cur }} / {{ nback.n }} 件 · 第 {{ nback.n }} 件立即免单</view>
      </view>
      <view class="nb-foot">
        <view class="info">当前阶段 <text class="b">{{ nback.phase }}</text></view>
        <view class="nb-cta" @click="onShare">🤝 分享朋友</view>
      </view>
    </view>

    <!-- ━━━━━━━━━━ 商家承诺 ━━━━━━━━━━ -->
    <view class="promise-card">
      <view class="promise-ic">✓</view>
      <view class="promise-body">
        <view class="promise-t">我们的承诺</view>
        <view class="promise-d">
          ✓ 推广积分 <text class="b">1:1 提现</text>，满 100 起
        </view>
        <view class="promise-d">
          ✓ 进度真实记录，<text class="b">不存在「差一刀」</text>
        </view>
        <view class="promise-d">
          ✓ 派奖明细 + 链路全部可查
        </view>
      </view>
    </view>

    <!-- ━━━━━━━━━━ 店内中奖滚动 ━━━━━━━━━━ -->
    <view v-if="tickerText.length" class="sh-ticker">
      <text class="em">🏆</text>
      <view class="roll">
        <view class="roll-track">
          <text v-for="(t, i) in tickerText" :key="i">{{ t }} · </text>
        </view>
      </view>
    </view>

    <!-- ━━━━━━━━━━ 分类 chips ━━━━━━━━━━ -->
    <scroll-view scroll-x class="sh-cats">
      <view class="sh-cat" :class="{ on: activeCat === 0 }" @click="activeCat = 0">全部</view>
      <view v-for="c in cats" :key="c.id" class="sh-cat" :class="{ on: activeCat === c.id }" @click="activeCat = c.id">
        {{ c.name }}
      </view>
    </scroll-view>

    <!-- ━━━━━━━━━━ 商品 grid（2 列）━━━━━━━━━━ -->
    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!filteredSpus.length" title="该店暂无商品" />
    <view v-else class="sh-grid">
      <view v-for="(p, i) in filteredSpus" :key="p.id" class="pcard" @click="goProduct(p)">
        <view class="ppic" :class="picTone(i)">
          <image v-if="p.picUrl && !p.imgErr" :src="p.picUrl" mode="aspectFill" class="ppic-img" @error="onImgErr(p)" />
          <text v-else class="ppic-em">{{ guessEmoji(p.name) }}</text>
          <view v-if="p.badge" class="pbadge" :class="p.badgeClass">{{ p.badge }}</view>
        </view>
        <view class="pname">{{ p.name }}</view>
        <view class="pmeta">
          <view class="pprice">¥{{ fen2yuan(p.price, false) }}</view>
          <view v-if="p.earnText" class="pearn" :class="p.earnClass">{{ p.earnText }}</view>
        </view>
      </view>
    </view>

    <view class="bottom-pad"></view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getShopInfo, listShopProducts } from '@/api/shop.js';
import { listWinners, getAccount, getTodayStat } from '@/api/promo.js';
import { request } from '@/utils/request.js';
import { fen2yuan } from '@/utils/format.js';
import { useUserStore } from '@/store/user.js';

const user = useUserStore();
const route = (() => {
  try { const ps = getCurrentPages(); return ps[ps.length - 1]?.options || {}; } catch { return {}; }
})();

const statusH = ref(20);
const shop = ref(null);
const cats = ref([]);
const activeCat = ref(0);
const spus = ref([]);
const loading = ref(true);
const isFav = ref(false);
const myEarn = ref('0');
const giveStat = ref({ amount: '0', count: 0 });
const tickerText = ref([]);
const nback = ref(null);

const shopEmoji = computed(() => {
  const name = (shop.value?.shopName || shop.value?.name || '').toLowerCase();
  if (/烤|薯|肉|餐|炒|饭/.test(name)) return '🍠';
  if (/茶|奶|饮/.test(name))         return '🍵';
  if (/果|蔬|生鲜/.test(name))       return '🍇';
  if (/糕|甜|烘焙/.test(name))       return '🍰';
  return '🏪';
});

const filteredSpus = computed(() => {
  if (activeCat.value === 0) return spus.value;
  return spus.value.filter((p) => p.categoryId === activeCat.value);
});

function picTone(i) {
  return ['', 'green', 'purple', 'pink'][i % 4];
}
function onImgErr(p) {
  // 直接 mutate p.imgErr 不会触发 Vue 重渲染（spus 数组 item 不是深响应式）
  // 必须替换数组里这一项
  const idx = spus.value.findIndex((x) => x.id === p.id);
  if (idx >= 0) spus.value.splice(idx, 1, { ...spus.value[idx], imgErr: true });
}
function guessEmoji(name) {
  const n = (name || '').toLowerCase();
  if (/茶|饮|奶|咖啡/.test(n)) return '🍵';
  if (/粥|饭|面|汤/.test(n))   return '🍜';
  if (/烤|肉|串|鸡|鸭/.test(n))return '🍖';
  if (/果|蔬|菜|生鲜/.test(n)) return '🍇';
  if (/糕|甜|烘焙|蛋糕/.test(n))return '🍰';
  if (/酒|啤|红/.test(n))      return '🍷';
  return '🍽';
}

function goBack() {
  const pages = getCurrentPages();
  if (pages.length > 1) uni.navigateBack();
  else uni.reLaunch({ url: '/pages/index/index' });
}
function goProduct(p) {
  uni.navigateTo({ url: `/pages/product/detail?id=${p.id}&tenantId=${route.tenantId || shop.value?.tenantId || ''}` });
}
function goWinners() { uni.reLaunch({ url: '/pages/winners/index' }); }
function toggleFav() {
  if (!user.isLogin) {
    try { localStorage.setItem('redirect:after-login', `/pages/shop/home?tenantId=${route.tenantId || ''}`); } catch {}
    return uni.navigateTo({ url: '/pages/login/index' });
  }
  isFav.value = !isFav.value;
  uni.showToast({ title: isFav.value ? '已收藏' : '取消收藏', icon: 'none' });
}
function onShare() {
  const base = typeof location !== 'undefined' ? location.origin : 'https://ke.doupaidoudian.com';
  const link = `${base}/#/pages/shop/home?tenantId=${route.tenantId}&inviter=${user.userId || ''}`;
  uni.setClipboardData({ data: link, success: () => uni.showToast({ title: '链接已复制', icon: 'success' }) });
}

async function loadShop() {
  try {
    const info = await getShopInfo({ tenantId: route.tenantId });
    shop.value = info ? {
      ...info,
      slogan: info.slogan || info.shopDesc || info.introduction || '',
      tagRow: info.tagRow || (info.starLevel ? `⭐ ${info.starLevel} 星店铺` : ''),
      ratingCount: info.ratingCount || info.commentCount || 0,
    } : null;
  } catch {}
}

async function loadProducts() {
  loading.value = true;
  try {
    const r = await listShopProducts(route.tenantId, 1, 50);
    const list = (r?.list || []).map((s) => {
      // 计算 badge / earnText 从 promoConfig（先简化按字段优先级判断）
      let badge = '', badgeClass = '', earnText = '', earnClass = '';
      if (s.tuijianN) {
        badge = `推 ${s.tuijianN} 反 1`;
        earnText = `最高 ¥${fen2yuan((s.price || 0) / s.tuijianN, false)}`;
        earnClass = 'brand';
      } else if (s.starCount) {
        badge = '派奖商品';
        badgeClass = 'gold';
        earnText = '下单进派奖池';
        earnClass = 'gold';
      }
      return { ...s, badge, badgeClass, earnText, earnClass };
    });
    spus.value = list.map((p) => ({ ...p, imgErr: false }));
    // 聚合分类（无 categoryName 时用顺序编号「分类 1/2/...」而非「分类#20」）
    const catMap = new Map();
    let idx = 1;
    for (const s of list) {
      if (s.categoryId && !catMap.has(s.categoryId)) {
        catMap.set(s.categoryId, {
          id: s.categoryId,
          name: s.categoryName || `分类 ${idx++}`,
        });
      }
    }
    cats.value = [...catMap.values()];
  } catch {} finally { loading.value = false; }
}

async function loadGiveStat() {
  try {
    const stat = await getTodayStat();
    if (stat?.promoAmountToday) {
      giveStat.value = {
        amount: fen2yuan(stat.promoAmountToday, false),
        count: stat.awardCountToday || 0,
      };
    }
  } catch {}
}

async function loadTicker() {
  try {
    const list = await listWinners(route.tenantId, 10);
    tickerText.value = (list || []).map((w) => {
      const amt = fen2yuan(w.amount, false);
      return `${w.userMask || '****'} ${w.sourceLabel || '派奖'} +¥${amt}`;
    });
  } catch {}
}

async function loadMyEarn() {
  if (!user.isLogin) { myEarn.value = '0'; return; }
  try {
    const acct = await getAccount(route.tenantId);
    myEarn.value = fen2yuan(acct?.promoPointBalance || 0, false);
  } catch {}
}

async function loadNback() {
  // 用 my-spu-stars 拿当前店里我已得 + 推 N 进度（简化版）
  if (!user.isLogin) { nback.value = null; return; }
  try {
    const list = await request({ url: '/app-api/merchant/mini/promo/my-spu-stars', tenantId: route.tenantId });
    if (Array.isArray(list) && list.length) {
      // 找进度最快的 SPU
      const sorted = [...list]
        .filter((x) => x.tuijianN > 0)
        .sort((a, b) => (b.directCount / b.tuijianN) - (a.directCount / a.tuijianN));
      const top = sorted[0];
      if (top) {
        const n = top.tuijianN;
        const cur = Math.min(top.directCount || 0, n);
        const pct = Math.min(100, Math.round((cur / n) * 100));
        const totalFen = top.spuPrice || top.price || 0;
        const gotFen = Math.floor(totalFen * cur / n);
        nback.value = {
          n,
          cur,
          pct,
          gotYuan: fen2yuan(gotFen, false),
          totalYuan: fen2yuan(totalFen, false),
          phase: cur >= n ? '完成期' : (cur > 0 ? '进行中' : '即将开启'),
        };
      }
    }
  } catch {}
}

function refreshAll() {
  loadShop();
  loadProducts();
  loadGiveStat();
  loadTicker();
  loadMyEarn();
  loadNback();
}

onMounted(() => {
  try { statusH.value = uni.getSystemInfoSync().statusBarHeight || 20; } catch {}
  refreshAll();
});
onShow(refreshAll);
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.page { min-height: 100vh; background: $bg-2; padding-bottom: 30px; }

/* ━━━━━━━━━━ Cover ━━━━━━━━━━ */
.sh-cover {
  position: relative;
  height: 200px;
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
  transform: rotate(-15deg);
  line-height: 1;
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
  color: #fff;
  z-index: 4;
}
.sh-tag-row {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 4px 10px; border-radius: 99px;
  background: rgba(0,0,0,.3);
  backdrop-filter: blur(10px);
  font-size: 10px; font-weight: 700;
  border: 1px solid rgba(255,255,255,.2);
}
.sh-name {
  font-size: 24px; font-weight: 900; letter-spacing: -.5px;
  margin-top: 8px; text-shadow: 0 2px 8px rgba(0,0,0,.15);
}
.sh-slogan { font-size: 13px; opacity: .9; margin-top: 2px; }

/* ━━━━━━━━━━ Info card ━━━━━━━━━━ */
.sh-info-card {
  margin: -36px 14px 0;
  background: $card; border-radius: $r-lg;
  padding: 16px; box-shadow: $sh-2;
  border: 1px solid $line;
  position: relative; z-index: 6;
}
.sh-stat-row { display: flex; align-items: center; gap: 0; }
.sh-stat { flex: 1; text-align: center; }
.sh-stat .v {
  font-size: 17px; font-weight: 800; color: $t1;
  line-height: 1;
}
.sh-stat .v.gold  { color: $gold; }
.sh-stat .v.brand {
  background: linear-gradient(135deg, $o, $o-d);
  -webkit-background-clip: text; background-clip: text; color: transparent;
}
.sh-stat .v .star { color: $gold; margin-right: 2px; }
.sh-stat .l { font-size: 11px; color: $t3; margin-top: 4px; font-weight: 500; }
.sh-stat-divider { width: 1px; align-self: stretch; background: $line; }

/* ━━━━━━━━━━ Give card ━━━━━━━━━━ */
.shop-give-card {
  margin: 14px 14px 0;
  padding: 14px;
  background: linear-gradient(135deg, $gold-50, #FFF8F4);
  border: 1px solid rgba(212,146,10,.25);
  border-radius: $r-lg;
  display: flex; align-items: center; gap: 12px;
  position: relative; overflow: hidden;
  box-shadow: 0 4px 12px rgba(212,146,10,.1);
}
.sgc-em {
  width: 44px; height: 44px; border-radius: 12px;
  background: linear-gradient(135deg, $gold, $gold-l);
  color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 22px; flex-shrink: 0;
  box-shadow: $sh-gold;
}
.sgc-body { flex: 1; }
.sgc-t { font-size: 13px; font-weight: 800; color: $t1; }
.sgc-t .b {
  background: linear-gradient(135deg, $gold-d, $o);
  -webkit-background-clip: text; background-clip: text; color: transparent;
  margin: 0 2px;
}
.sgc-d { font-size: 11px; color: $t3; margin-top: 2px; display: flex; align-items: center; gap: 6px; }
.sgc-d .live { color: $mint; font-weight: 700; }
.sgc-cta {
  padding: 7px 12px; border-radius: 99px;
  background: $card; color: $gold-d;
  font-size: 11px; font-weight: 800;
  border: 1px solid rgba(212,146,10,.3);
}

/* ━━━━━━━━━━ Nback card ━━━━━━━━━━ */
.nback-card {
  margin: 12px 14px 0;
  padding: 18px 20px;
  background:
    radial-gradient(400px 200px at 100% 0%, rgba(254,243,199,.35), transparent 60%),
    linear-gradient(135deg, #FFF0E0, #FFE0C5);
  border-radius: $r-xl;
  border: 1px solid $o-100;
  position: relative; overflow: hidden;
  box-shadow: 0 8px 24px rgba(255,107,53,.12);
}
.nb-deco {
  position: absolute; top: -30px; right: -10px;
  font-size: 100px; opacity: .08;
  transform: rotate(15deg);
  pointer-events: none;
}
.nb-head { display: flex; justify-content: space-between; align-items: flex-start; position: relative; }
.nb-l { flex: 1; }
.nb-tag {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 4px 10px;
  background: linear-gradient(135deg, $gold, $gold-l);
  color: #fff;
  border-radius: 6px;
  font-size: 10px; font-weight: 800; letter-spacing: .5px;
  margin-bottom: 10px;
  box-shadow: 0 4px 10px rgba(212,146,10,.3);
}
.nb-t {
  font-size: 22px; font-weight: 900;
  letter-spacing: -.5px; line-height: 1.15; color: $t1;
}
.nb-t .h {
  background: linear-gradient(135deg, $gold-d, $o, $o-d);
  -webkit-background-clip: text; background-clip: text; color: transparent;
}
.nb-sub { font-size: 11px; color: $t2; margin-top: 6px; font-weight: 500; }
.nb-money { text-align: right; flex-shrink: 0; }
.nb-mv {
  font-size: 30px; font-weight: 900; letter-spacing: -.5px;
  background: linear-gradient(135deg, $mint, $gold);
  -webkit-background-clip: text; background-clip: text; color: transparent;
  line-height: 1;
}
.nb-ml { font-size: 9px; color: $t3; margin-top: 2px; letter-spacing: .5px; font-weight: 700; }
.nb-bar {
  margin-top: 16px;
  height: 36px;
  background: rgba(255,255,255,.5);
  border-radius: 18px;
  padding: 4px;
  display: flex; align-items: center;
  position: relative; overflow: hidden;
  border: 1px solid $o-100;
}
.nb-fill {
  height: 28px;
  background: linear-gradient(90deg, $o, $o-l, $gold);
  border-radius: 14px;
  position: relative;
  box-shadow: 0 0 16px rgba(255,107,53,.5);
  transition: width .4s ease;
}
.nb-txt {
  position: absolute; left: 50%; top: 50%; transform: translate(-50%, -50%);
  font-size: 11px; font-weight: 800; color: #fff;
  text-shadow: 0 1px 2px rgba(0,0,0,.3);
  letter-spacing: .3px; z-index: 2;
}
.nb-foot {
  margin-top: 14px;
  display: flex; justify-content: space-between; align-items: center;
}
.nb-foot .info { font-size: 11px; color: $t2; font-weight: 500; }
.nb-foot .info .b { color: $o-d; font-weight: 800; }
.nb-cta {
  padding: 8px 16px;
  background: linear-gradient(135deg, $o, $o-d);
  color: #fff; border-radius: 99px;
  font-size: 11px; font-weight: 800;
  box-shadow: $sh-warm;
}

/* ━━━━━━━━━━ Promise ━━━━━━━━━━ */
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

/* ━━━━━━━━━━ Ticker ━━━━━━━━━━ */
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
.sh-ticker .roll { flex: 1; overflow: hidden; height: 18px; position: relative; }
.sh-ticker .roll-track {
  position: absolute; white-space: nowrap;
  font-size: 11.5px; color: $gold-d; font-weight: 600;
  animation: rollx 22s linear infinite;
}
@keyframes rollx { 0% { transform: translateX(100%); } 100% { transform: translateX(-100%); } }

/* ━━━━━━━━━━ Cats ━━━━━━━━━━ */
.sh-cats {
  padding: 0 14px;
  white-space: nowrap;
  margin: 14px 0 12px;
}
.sh-cat {
  display: inline-block;
  padding: 7px 16px; border-radius: 99px;
  background: $bg-2; color: $t2;
  font-size: 12px; font-weight: 700;
  border: 1px solid $line;
  margin-right: 8px;
}
.sh-cat.on {
  background: linear-gradient(135deg, $o, $o-d);
  color: #fff;
  border-color: transparent;
  box-shadow: $sh-warm;
}

/* ━━━━━━━━━━ Grid ━━━━━━━━━━ */
.loading { padding: 40px; text-align: center; color: $t4; }
.sh-grid {
  padding: 0 14px;
  display: grid; grid-template-columns: 1fr 1fr; gap: 10px;
}
.pcard {
  background: $card; border-radius: $r-md;
  padding: 8px; border: 1px solid $line;
  position: relative; overflow: hidden;
}
.ppic {
  height: 110px; border-radius: 10px;
  background: linear-gradient(135deg, #FFE0D1, $o-l);
  display: flex; align-items: center; justify-content: center; font-size: 48px;
  margin-bottom: 8px; position: relative; overflow: hidden;
}
.ppic.green  { background: linear-gradient(135deg, #D1FAE5, #6EE7B7); }
.ppic.purple { background: linear-gradient(135deg, #E0D4FF, #C4B5FD); }
.ppic.pink   { background: linear-gradient(135deg, #FCE7F3, #F9A8D4); }
.ppic-img { width: 100%; height: 100%; }
.ppic-em { font-size: 48px; line-height: 1; }
.pbadge {
  position: absolute; top: 6px; left: 6px;
  padding: 3px 7px;
  background: linear-gradient(135deg, $o, $o-d);
  color: #fff; border-radius: 6px;
  font-size: 9px; font-weight: 800;
  box-shadow: 0 2px 6px rgba(255,107,53,.4);
}
.pbadge.gold { background: linear-gradient(135deg, $gold, $gold-l); box-shadow: 0 2px 6px rgba(212,146,10,.4); }
.pbadge.mint { background: linear-gradient(135deg, $mint, $mint-l); box-shadow: 0 2px 6px rgba(16,185,129,.4); }
.pname {
  font-size: 13px; font-weight: 700; color: $t1;
  line-height: 1.3; height: 34px; overflow: hidden;
}
.pmeta { display: flex; justify-content: space-between; align-items: center; margin-top: 6px; }
.pprice { font-size: 16px; font-weight: 900; color: $o; }
.pearn { font-size: 10px; color: $t3; font-weight: 700; }
.pearn.brand {
  background: linear-gradient(135deg, $o, $o-d);
  -webkit-background-clip: text; background-clip: text; color: transparent;
}
.pearn.gold { color: $gold-d; }

.bottom-pad { height: 20px; }
</style>
