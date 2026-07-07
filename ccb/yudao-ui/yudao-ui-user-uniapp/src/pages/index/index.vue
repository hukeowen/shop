<template>
  <view class="page">
    <!-- ━━━━━━ 首次访问 合规协议弹窗 ━━━━━━ -->
    <view v-if="showCompliance" class="cmp-mask" @click.stop>
      <view class="cmp">
        <view class="cmp-hd">
          <view class="cmp-i"><text class="emo">🛡️</text></view>
          <text class="cmp-t">平台使用提示</text>
        </view>
        <scroll-view scroll-y class="cmp-bd">
          <view class="cmp-p"><text class="b">1. 平台定位</text>：本平台为「商户营销服务工具」，性质为<text class="hl">纯技术信息中介</text>，不销售商品、不沉淀资金，不对商户经营行为及结果承担责任。</view>
          <view class="cmp-p"><text class="b">2. 交易关系</text>：您购买的商品 / 服务均由独立经营的<text class="hl">商户提供</text>，您与商户形成独立买卖合同关系，与平台无关。</view>
          <view class="cmp-p"><text class="b">3. 营销激励</text>：「分享激励」「邀请有礼」等活动奖励来自<text class="hl">商户自费</text>营销优惠预算，严格单层奖励直接邀请人，<text class="hl">不构成投资</text>、无保证收益。</view>
          <view class="cmp-p"><text class="b">4. 解释权</text>：商户对营销活动规则保留<text class="hl">最终解释权</text>，任何争议<text class="hl">请直接联系商户</text>处理，平台不承担兑付保证。</view>
          <view class="cmp-p"><text class="b">5. 禁止行为</text>：禁止传播"投资 / 躺赚 / 暴富"等误导话术，禁止任何形式的传销 / 资金盘。违规将封号并移交司法机关。</view>
        </scroll-view>
        <view class="cmp-links">
          <text class="lk" @click="goCmpAgreement('user')">《用户服务协议》</text>
          <text class="lk" @click="goCmpAgreement('privacy')">《隐私协议》</text>
        </view>
        <view class="cmp-cta" @click="acceptCompliance">我已阅读并同意</view>
      </view>
    </view>

    <!-- ━━━━━━ 顶栏：问候 + 定位 + 通知 + 搜索 + ticker ━━━━━━ -->
    <view :style="sbhSpacer"></view>
    <view class="top">
      <view class="greet">
        <view class="av">{{ avatarText }}</view>
        <view class="g">
          <view class="hi">{{ greeting }}，欢迎回来</view>
          <view class="loc" @click="goNearby">
            <text class="emo pin">📍</text>
            {{ locText }}
            <text class="emo cv">▾</text>
          </view>
        </view>
        <view class="ico-wrap" @click="goWallet">
          <text class="emo ico">👛</text>
        </view>
      </view>
      <view class="searchbar" @click="goSearch">
        <text class="emo s">🔍</text>
        <text class="ph">搜店铺、商品</text>
        <text class="go">搜索</text>
      </view>
      <view v-if="tickerText.length" class="ticker">
        <text class="emo tp">🏆</text>
        <view class="roll"><text class="rt num">{{ tickerText.join('　·　') }}</text></view>
      </view>
    </view>

    <!-- ━━━━━━ 今日到账（可关闭提示，无兑付 CTA）/ 未登录引导 / 空状态 ━━━━━━ -->
    <view v-if="user.isLogin && todayRecords.length && !maeHidden" class="mae">
      <view class="mae-h">
        <view class="t"><text class="emo ic">⭐</text>你今日刚到账</view>
        <view class="close" @click="maeHidden = true"><text class="emo cls">✕</text></view>
      </view>
      <view v-for="r in todayRecords" :key="r.id" class="mae-row">
        <view class="r-ic" :class="r.cls"><text class="emo">🛍️</text></view>
        <view class="r-bd"><view class="n">{{ r.title }}</view><view class="d">{{ r.time }} · {{ r.sourceLabel }}</view></view>
        <view class="r-amt num">+¥{{ r.amount }}</view>
      </view>
      <view class="mae-ft">
        <view class="sum">今日合计入账 <text class="b num">¥{{ todaySumYuan }}</text></view>
        <view class="look" @click="goWallet">查看明细 <text class="emo chev">›</text></view>
      </view>
    </view>
    <view v-else-if="!user.isLogin" class="tipcard" @click="goLogin">
      <view class="tc-ic"><text class="emo">👤</text></view>
      <view class="tc-bd"><view class="t">登录享老客分享激励</view><view class="d">商户营销优惠 · 邀请有礼 · 推广积分</view></view>
      <view class="tc-cta">登录</view>
    </view>

    <!-- ━━━━━━ 5 快入口 ━━━━━━ -->
    <view class="quick">
      <view class="qk" @click="goNearby"><view class="b"><text class="emo">📍</text></view><text class="t">附近</text></view>
      <view class="qk" @click="goWinners"><view class="b"><text class="emo">🏆</text></view><text class="t">优惠榜</text><view class="live"></view></view>
      <view class="qk" @click="goQueue"><view class="b"><text class="emo">📋</text></view><text class="t">我的队列</text></view>
      <view class="qk" @click="goCoupon"><view class="b"><text class="emo">🎟️</text></view><text class="t">优惠券</text></view>
      <view class="qk" @click="onScan"><view class="b"><text class="emo">📷</text></view><text class="t">扫码</text></view>
    </view>

    <!-- ━━━━━━ 推 N 反 1 进行中提醒 ━━━━━━ -->
    <view v-if="queueTip" class="qtip" @click="goQueue">
      <view class="ic"><text class="emo">📋</text></view>
      <view class="bd"><view class="t">{{ queueTip.shopName }} · {{ queueTip.spuName }} <text class="b">还差 {{ queueTip.gap }} 人出队 +¥{{ queueTip.amount }}</text></view><view class="d">分享给朋友扫码下单 → 你立即出队拿全额</view></view>
      <view class="cta">分享</view>
    </view>

    <!-- ━━━━━━ 分类 ━━━━━━ -->
    <view class="sec"><text class="h">逛分类</text></view>
    <scroll-view scroll-x :show-scrollbar="false" class="cats">
      <view class="cat" @click="goCategory('snack')"><view class="b"><text class="emo">🍜</text></view><text class="t">小吃</text></view>
      <view class="cat" @click="goCategory('drink')"><view class="b"><text class="emo">🧋</text></view><text class="t">奶茶</text></view>
      <view class="cat" @click="goCategory('bbq')"><view class="b"><text class="emo">🍢</text></view><text class="t">烧烤</text></view>
      <view class="cat" @click="goCategory('restaurant')"><view class="b"><text class="emo">🍽️</text></view><text class="t">餐厅</text></view>
      <view class="cat" @click="goCategory('tea_house')"><view class="b"><text class="emo">🍵</text></view><text class="t">茶馆</text></view>
      <view class="cat" @click="goCategory('fruit')"><view class="b"><text class="emo">🍉</text></view><text class="t">水果</text></view>
      <view class="cat" @click="goCategory('super')"><view class="b"><text class="emo">🛒</text></view><text class="t">超市</text></view>
      <view class="cat" @click="goCategory('bakery')"><view class="b"><text class="emo">🥐</text></view><text class="t">烘焙</text></view>
      <view class="cat" @click="goCategory('beauty')"><view class="b"><text class="emo">💄</text></view><text class="t">美容</text></view>
      <view class="cat" @click="goCategory('')"><view class="b"><text class="emo">🗂️</text></view><text class="t">全部</text></view>
    </scroll-view>

    <!-- ━━━━━━ 玩法专区 ━━━━━━ -->
    <view class="sec"><text class="h">优惠专区</text><text class="sub">商户营销优惠</text></view>
    <view class="plays">
      <view class="play rank" @click="goWinners">
        <view class="ppic"><text class="emo">🏆</text></view>
        <text class="ptag">商户优惠榜</text>
        <text class="ph2">看谁刚得奖</text>
        <view class="pft"><view class="m">今日优惠<text class="b num">¥{{ stat.todayAward }}</text></view><text class="pgo">查看</text></view>
      </view>
      <view class="play nb" @click="goQueue">
        <view class="ppic"><text class="emo">📋</text></view>
        <text class="ptag">推 N 反 1</text>
        <text class="ph2">买 N 反 1 件</text>
        <view class="pft"><view class="m">在队列<text class="b num">{{ stat.myQueueCount }} 个</text></view><text class="pgo">查看</text></view>
      </view>
    </view>

    <!-- ━━━━━━ 最近去过 ━━━━━━ -->
    <template v-if="recentShops.length">
      <view class="sec"><text class="h">最近去过</text><text class="more" @click="goNearby">全部 ›</text></view>
      <scroll-view scroll-x :show-scrollbar="false" class="recent">
        <view v-for="s in recentShops" :key="s.id" class="rc" @click="goShop(s)">
          <view class="rc-cv" :class="s.coverTone"><image v-if="s.coverUrl" :src="s.coverUrl" mode="aspectFill" class="rc-img" /><text v-else class="rc-ph">{{ (s.name || '店')[0] }}</text><text class="vt">{{ s.lastVisit }}</text></view>
          <view class="rc-bd"><view class="n">{{ s.name }}</view><view class="m">余 <text class="b num">¥{{ s.balanceYuan }}</text><text v-if="s.promoPoint"> · 推 <text class="b num">{{ s.promoPoint }}</text></text></view></view>
        </view>
      </scroll-view>
    </template>

    <!-- ━━━━━━ 附近好店（单列大卡）━━━━━━ -->
    <view class="sec"><text class="h">附近好店</text><text class="sub">店主推招牌</text><text class="more" @click="goNearby">全部 ›</text></view>
    <view v-if="loadingShops" class="loading">加载中…</view>
    <empty-state v-else-if="!nearbyShops.length" icon="🏪" title="附近暂无店铺" desc="换个位置或允许定位试试" />
    <view v-else class="shops">
      <view v-for="s in nearbyShops" :key="s.id" class="shop" @click="goShop(s)">
        <view class="cv">
          <image v-if="s.coverUrl || s.topSpu?.picUrl" :src="s.coverUrl || s.topSpu.picUrl" mode="aspectFill" class="cv-img" />
          <text v-else class="cv-ph">{{ (s.name || '店')[0] }}</text>
          <view class="st" :class="s.open ? 'open' : 'close'"><text class="d"></text>{{ s.open ? '营业中' : '休息中' }}</view>
        </view>
        <view class="bd">
          <view class="nm">{{ s.name }}</view>
          <view class="mt">
            <text v-if="s.rating" class="rate">★ {{ Number(s.rating).toFixed(1) }}</text>
            <text v-if="s.rating && s.monthSold != null" class="dot"></text>
            <text v-if="s.monthSold != null" class="num">月售 {{ s.monthSold }}</text>
            <text v-if="s.distance" class="dot"></text>
            <text v-if="s.distance" class="num">{{ s.distance }}</text>
          </view>
          <view v-if="s.promoLine || s.star" class="tags">
            <text v-if="s.promoLine" class="tag">{{ s.promoLine }}</text>
            <text v-if="s.star" class="tag line">{{ s.star }} 星店</text>
          </view>
          <view v-if="s.topSpu" class="spu" @click.stop="goSpu(s)">
            <text class="l">招牌</text>
            <text class="n">{{ s.topSpu.name }}</text>
            <text class="p num"><text class="c">¥</text>{{ fmtYuan(s.topSpu.price) }}</text>
          </view>
        </view>
      </view>
    </view>

    <view class="bottom-pad"></view>
    <bottom-nav active="index" :cart-count="cartCount" />
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { useUserStore } from '@/store/user.js';
import { listWinnersTicker, listMyQueues, listPromoRecords, getTodayStat } from '@/api/promo.js';
import { listShops, listMyShopsEnriched, reverseGeo } from '@/api/shop.js';
import { getCartCount } from '@/api/cart.js';
import { fen2yuan, fmtTime, fmtDistance } from '@/utils/format.js';
import { sbhSpacerStyle } from '@/utils/safeTop.js';
const sbhSpacer = sbhSpacerStyle();

const user = useUserStore();
const avatarText = computed(() => (user.nickname?.[0] || '邀'));
const maeHidden = ref(false); // 今日到账提示可关闭

const greeting = computed(() => {
  const h = new Date().getHours();
  if (h < 5)  return '凌晨好';
  if (h < 11) return '早上好';
  if (h < 14) return '中午好';
  if (h < 18) return '下午好';
  return '晚上好';
});
const greetTitle = computed(() => `想吃点什么，${user.nickname || (user.phone ? user.phone.slice(-4) : '小客')}？`);

// 定位文案 + 坐标。小程序自动取坐标用于「附近」距离排序；
// 具体地址需逆地理解析（后端代理腾讯位置服务），拿到后回填 locText。
const locText = ref('当前位置 · 看附近');
const userLoc = ref(null); // { lng, lat }
function loadLocation() {
  // #ifdef MP-WEIXIN || APP-PLUS
  locText.value = '定位中…';
  uni.getLocation({
    type: 'gcj02',
    success: (res) => {
      userLoc.value = { lng: res.longitude, lat: res.latitude };
      reverseGeocode(res.longitude, res.latitude);
      loadNearby(); // 拿到坐标后按距离重排
    },
    fail: () => { locText.value = '点击开启定位 · 看附近'; },
  });
  // #endif
}
// 逆地理解析 → 具体地址（街道/门牌）。key 在后端，前端只传坐标
async function reverseGeocode(lng, lat) {
  try {
    const r = await reverseGeo(lng, lat);
    // 后端返回 { address, recommend, city, district } —— 优先门牌级 recommend
    locText.value = (r && (r.recommend || r.address)) ? `${r.recommend || r.address}` : '已定位 · 看附近';
  } catch { locText.value = '已定位 · 看附近'; }
}

// 滚动条：跨店最新优惠
const tickerText = ref([]);
async function loadTicker() {
  try {
    const list = await listWinnersTicker(8);
    tickerText.value = (list || []).map((w) => {
      const amt = fen2yuan(w.amount, false);
      return `${w.shopName || '店铺'} ${w.sourceLabel || '促销优惠'} ${w.userMask || ''} ¥${amt}`;
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
    const today = new Date().toDateString();
    const labelOf = (t) => ({
      DIRECT: '邀请有礼', QUEUE: '活动完成奖', COMMISSION: '分享激励',
      POOL_V8: '促销优惠', POOL: '促销优惠',
      SELF_BATCH: '复购优惠', SELF_PROGRESS: '复购优惠', SELF_COMMISSION: '复购优惠',
      REFERRAL_PROGRESS: '分享感谢奖', REFERRAL_COMMISSION: '分享感谢奖',
      CONVERT: '积分兑换', WITHDRAW: '提现', MANUAL_PATCH: '账户调整',
      REDEEM_ORDER: '订单抵扣',
    }[t] || '分享奖励');
    const rows = (page?.list || [])
      .filter((r) => new Date(String(r.createTime).replace('T', ' ').replace(/-/g, '/')).toDateString() === today)
      .map((r) => {
        let cls = '';
        if (r.sourceType === 'QUEUE')           { cls = 'coin'; }
        else if (r.sourceType === 'COMMISSION') { cls = 'pt'; }
        return {
          id: r.id,
          cls,
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
      // VO 字段为 maxN / accumulatedCount / unitPrice（原 requiredCount/currentCount/rewardAmount 不存在）
      const sorted = [...list].sort((a, b) =>
        ((a.maxN || 0) - (a.accumulatedCount || 0)) -
        ((b.maxN || 0) - (b.accumulatedCount || 0)));
      const q = sorted[0];
      const gap = (q.maxN || 0) - (q.accumulatedCount || 0);
      const amt = q.unitPrice || q.accumulatedAmount || 0;
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

// 今日全网派奖（玩法卡 stat） — 跨店统计
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
    const list = await listMyShopsEnriched();
    const sorted = [...(list || [])].sort((a, b) => (b.lastVisitAt || 0) - (a.lastVisitAt || 0));
    recentShops.value = sorted.slice(0, 4).map((s, i) => ({
      id: s.id || s.tenantId,
      tenantId: s.tenantId,
      name: s.shopName || s.name || `店铺 #${s.tenantId}`,
      coverUrl: s.coverUrl || s.topPicUrl || s.shopLogo || '', // 封面优先，无封面用销量最高商品图，再不行字母占位
      coverTone: ['', 't2', 't3', 't4'][i],
      lastVisit: s.lastVisitAt ? fmtTime(s.lastVisitAt) : '最近',
      star: s.star || 0,
      balanceYuan: fen2yuan(s.balance || 0, false),
      promoPoint: (s.promoPoints || 0) > 0 ? fen2yuan(s.promoPoints, false) : '', // 分→积分(×100已修)，0 时空串隐藏
    }));
  } catch {}
}

// 附近店铺 — 真接口
const nearbyShops = ref([]);
const loadingShops = ref(false);
async function loadNearby() {
  loadingShops.value = true;
  try {
    const params = { pageNo: 1, pageSize: 20 };
    if (userLoc.value) { params.userLng = userLoc.value.lng; params.userLat = userLoc.value.lat; }
    const r = await listShops(params);
    const items = r?.list || r || [];
    nearbyShops.value = items.slice(0, 10).map((s) => ({
      id: s.id || s.tenantId,
      tenantId: s.tenantId || s.id,
      name: s.shopName || s.name || '店铺',
      coverUrl: s.coverUrl || s.shopLogo || '',
      star: s.starLevel || s.star,
      newTag: s.newShop ? '新店送 ¥5' : '',
      rating: s.avgRating || s.rating,
      distance: s.distance != null && s.distance > 0 ? fmtDistance(s.distance) : '',
      monthSold: s.sales30d != null ? s.sales30d : (s.monthSold != null ? s.monthSold : null),
      open: s.isOpenNow === true,
      promoLine: s.promoLine || (s.tuijianN ? `推 ${s.tuijianN} 反 1` : ''),
      topSpu: s.topSpu || null,
    }));
  } catch {} finally { loadingShops.value = false; }
}

// 购物车角标
const cartCount = ref(0);
async function loadCart() {
  if (!user.isLogin) { cartCount.value = 0; return; }
  try { cartCount.value = (await getCartCount()) || 0; } catch {}
}

function goSearch() { uni.navigateTo({ url: '/pages/search/index' }); }
function goNearby() { uni.navigateTo({ url: '/pages/nearby/index' }); }
function goWinners() { uni.reLaunch({ url: '/pages/winners/index' }); }
function goQueue() { uni.navigateTo({ url: '/pages/queue/index' }); }
function goCoupon() { uni.navigateTo({ url: '/pages/coupon/index' }); }
function goWallet() { uni.navigateTo({ url: '/pages/wallet/index' }); }
function goWithdraw() { uni.navigateTo({ url: '/pages/wallet/index' }); } // 兑付按店申请，先进钱包选店
function goCategory(k) { uni.navigateTo({ url: `/pages/nearby/index?bt=${k}` }); }
function goShop(s) {
  const tid = s.tenantId || s.id;
  uni.navigateTo({ url: `/pages/shop/home?id=${s.id || tid}&tenantId=${tid}` });
}
const fmtYuan = (fen) => fen2yuan(fen, false);
function goSpu(s) {
  if (!s.topSpu?.id) return goShop(s);
  uni.navigateTo({ url: `/pages/product/detail?id=${s.topSpu.id}&tenantId=${s.tenantId || s.id}` });
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
onMounted(() => {
  refreshAll();
  loadLocation();
});

// V044 合规：首次访问强制阅读合规警告
const COMPLIANCE_KEY = 'ke-compliance-accepted-v1';
const showCompliance = ref(false);
onMounted(() => {
  try {
    const accepted = uni.getStorageSync(COMPLIANCE_KEY);
    if (!accepted) showCompliance.value = true;
  } catch {}
});
function acceptCompliance() {
  try { uni.setStorageSync(COMPLIANCE_KEY, String(Date.now())); } catch {}
  showCompliance.value = false;
}
function goCmpAgreement(type) {
  uni.navigateTo({ url: `/pages/agreement/index?type=${type}` });
}
onShow(refreshAll);
</script>

<style lang="scss" scoped>
/* v15 设计语言：亮色现代 · 单一品牌橙 · emoji 图标 · 分级圆角 · 真实图 */
.page{
  --bg:#F4F5F7;--card:#FFFFFF;--ink:#15171A;--ink2:#767C85;--ink3:#A8AEB7;--line:#EEF0F3;--fill:#F2F3F5;
  --br:#FF5A2C;--br-d:#E8431A;--br-50:#FFF1EC;--br-100:#FFE2D6;
  --grad:linear-gradient(135deg,#FF8A4B,#FF5A2C 60%,#F1430F);
  --price:#F5331F;--star:#FF9F1C;--ok:#13B26A;--gold:#B07D2B;--gold-50:#FBF3E2;
  --r1:18px;--r2:14px;--r3:10px;
  --sh:0 1px 2px rgba(20,22,26,.04),0 8px 22px -10px rgba(20,22,26,.12);
  --sh-sm:0 1px 2px rgba(20,22,26,.05);
  min-height:100vh;background:var(--bg);padding-bottom:96px;
  font-family:-apple-system,BlinkMacSystemFont,"PingFang SC","Microsoft YaHei",sans-serif;color:var(--ink);
}
/* emoji 图标基类：统一行高、去斜体、用系统彩色 emoji 字体 */
.emo{display:inline-block;line-height:1;font-style:normal;text-align:center;
  font-family:"Segoe UI Emoji","Apple Color Emoji","Noto Color Emoji","Twemoji Mozilla",sans-serif}
.num{font-variant-numeric:tabular-nums}

/* 合规弹窗 */
.cmp-mask{position:fixed;inset:0;z-index:9999;background:rgba(20,22,26,.52);display:flex;align-items:center;justify-content:center;padding:26px;backdrop-filter:blur(5px)}
.cmp{width:100%;max-height:78vh;background:var(--card);border-radius:20px;padding:22px 20px 20px;display:flex;flex-direction:column;box-shadow:0 24px 60px rgba(0,0,0,.3)}
.cmp-hd{display:flex;align-items:center;gap:9px;padding-bottom:13px;border-bottom:1px solid var(--line)}
.cmp-i{width:34px;height:34px;border-radius:10px;background:var(--br-50);display:flex;align-items:center;justify-content:center;flex:none}
.cmp-i .emo{font-size:18px}
.cmp-t{font-size:16px;font-weight:700;letter-spacing:-.3px}
.cmp-bd{flex:1;padding:14px 0 4px}
.cmp-p{font-size:12.5px;color:var(--ink2);line-height:1.72;margin-bottom:10px}
.cmp-p .b{color:var(--ink);font-weight:700}
.cmp-p .hl{color:var(--br-d);font-weight:600}
.cmp-links{display:flex;gap:16px;justify-content:center;padding:12px 0 14px}
.cmp-links .lk{font-size:12px;color:var(--br-d);text-decoration:underline}
.cmp-cta{height:46px;border-radius:23px;background:var(--grad);color:#fff;font-size:15px;font-weight:700;display:flex;align-items:center;justify-content:center;box-shadow:0 8px 18px -6px rgba(232,67,26,.5)}

/* 顶栏 */
.top{position:sticky;top:0;z-index:30;background:var(--card);padding:6px 16px 12px}
.greet{display:flex;align-items:center;gap:10px;height:42px}
.av{width:38px;height:38px;border-radius:50%;background:var(--grad);color:#fff;display:flex;align-items:center;justify-content:center;font-size:16px;font-weight:700;flex:none}
.g{flex:1;min-width:0}
.g .hi{font-size:11.5px;color:var(--ink3)}
.g .loc{display:flex;align-items:center;gap:3px;font-size:15px;font-weight:600;letter-spacing:-.3px;margin-top:1px}
.g .loc .pin{font-size:13px}
.g .loc .cv{font-size:11px;color:var(--ink3)}
.ico-wrap{flex:none}
.ico{font-size:22px}
.searchbar{margin-top:10px;height:40px;background:var(--fill);border-radius:20px;display:flex;align-items:center;gap:8px;padding:0 6px 0 14px}
.searchbar .s{font-size:15px;flex:none}
.searchbar .ph{flex:1;font-size:13.5px;color:var(--ink3)}
.searchbar .go{height:30px;padding:0 16px;border-radius:16px;background:var(--grad);color:#fff;font-size:13px;font-weight:700;display:flex;align-items:center}
.ticker{margin-top:10px;height:30px;background:var(--gold-50);border-radius:9px;display:flex;align-items:center;gap:8px;padding:0 12px;overflow:hidden}
.ticker .tp{font-size:13px;flex:none}
.ticker .roll{flex:1;overflow:hidden;height:16px;position:relative}
.ticker .rt{position:absolute;white-space:nowrap;font-size:11px;color:var(--gold);font-weight:600;animation:rollx 22s linear infinite}
@keyframes rollx{0%{transform:translateX(100%)}100%{transform:translateX(-100%)}}

/* 今日到账 */
.mae{margin:12px 16px 0;background:var(--card);border-radius:var(--r1);box-shadow:var(--sh);padding:14px 16px}
.mae-h{display:flex;align-items:center;margin-bottom:11px}
.mae-h .t{font-size:13px;font-weight:700;display:flex;align-items:center;gap:6px}
.mae-h .t .ic{font-size:13px}
.mae-h .close{margin-left:auto}
.mae-h .close .cls{font-size:15px;color:var(--ink3)}
.mae-row{display:flex;align-items:center;gap:10px;padding:9px 0}
.mae-row+.mae-row{border-top:1px solid var(--line)}
.mae-row .r-ic{width:32px;height:32px;border-radius:9px;background:var(--gold-50);display:flex;align-items:center;justify-content:center;flex:none}
.mae-row .r-ic .emo{font-size:16px}
.mae-row .r-ic.coin{background:var(--br-50)}
.mae-row .r-ic.pt{background:#EDE9FE}
.mae-row .r-bd{flex:1;min-width:0}
.mae-row .r-bd .n{font-size:13px;font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.mae-row .r-bd .d{font-size:11px;color:var(--ink3);margin-top:1px}
.mae-row .r-amt{font-size:16px;font-weight:800;color:var(--gold)}
.mae-ft{display:flex;align-items:center;margin-top:11px;padding-top:11px;border-top:1px dashed var(--line)}
.mae-ft .sum{font-size:11.5px;color:var(--ink2)}
.mae-ft .sum .b{color:var(--ink);font-weight:800;font-size:15px}
.mae-ft .look{margin-left:auto;font-size:11.5px;color:var(--ink3);display:flex;align-items:center;gap:2px}
.mae-ft .look .chev{font-size:14px;color:var(--ink3)}

/* 登录引导卡 */
.tipcard{margin:12px 16px 0;background:var(--card);border-radius:var(--r1);box-shadow:var(--sh);padding:14px 16px;display:flex;align-items:center;gap:12px}
.tc-ic{width:40px;height:40px;border-radius:12px;background:var(--br-50);display:flex;align-items:center;justify-content:center;flex:none}
.tc-ic .emo{font-size:22px}
.tc-bd{flex:1;min-width:0}
.tc-bd .t{font-size:15px;font-weight:700}
.tc-bd .d{font-size:11px;color:var(--ink3);margin-top:2px}
.tc-cta{flex:none;height:34px;padding:0 18px;border-radius:10px;background:var(--grad);color:#fff;font-size:13px;font-weight:700;display:flex;align-items:center}

/* 5 快入口 */
.quick{margin:12px 16px 0;background:var(--card);border-radius:var(--r1);box-shadow:var(--sh);padding:16px 6px;display:flex}
.qk{flex:1;display:flex;flex-direction:column;align-items:center;gap:7px;position:relative}
.qk .b{width:44px;height:44px;border-radius:13px;background:var(--br-50);display:flex;align-items:center;justify-content:center}
.qk .b .emo{font-size:23px}
.qk .t{font-size:11.5px;color:var(--ink2);font-weight:500}
.qk .live{position:absolute;top:-1px;left:calc(50% + 12px);width:7px;height:7px;border-radius:4px;background:var(--price);box-shadow:0 0 0 2.5px var(--card)}

/* 推N反1提醒 */
.qtip{margin:12px 16px 0;background:var(--br-50);border:1px solid var(--br-100);border-radius:var(--r2);padding:12px 14px;display:flex;align-items:center;gap:11px}
.qtip .ic{width:38px;height:38px;border-radius:11px;background:var(--grad);display:flex;align-items:center;justify-content:center;flex:none}
.qtip .ic .emo{font-size:19px}
.qtip .bd{flex:1;min-width:0}
.qtip .bd .t{font-size:12.5px;font-weight:600}
.qtip .bd .t .b{color:var(--br-d)}
.qtip .bd .d{font-size:11px;color:var(--ink2);margin-top:1px}
.qtip .cta{flex:none;height:30px;padding:0 13px;border-radius:8px;background:#fff;border:1px solid var(--br-100);color:var(--br-d);font-size:12px;font-weight:700;display:flex;align-items:center}

/* 区块标题 */
.sec{display:flex;align-items:baseline;margin:20px 16px 11px}
.sec .h{font-size:17px;font-weight:800;letter-spacing:-.3px}
.sec .sub{font-size:11.5px;color:var(--ink3);margin-left:8px}
.sec .more{margin-left:auto;font-size:12px;color:var(--ink2)}

/* 横滚滚动条隐藏（uniapp H5 scroll-view 内部）*/
.cats ::-webkit-scrollbar, .recent ::-webkit-scrollbar{width:0;height:0;display:none}
.cats, .recent{scrollbar-width:none;-ms-overflow-style:none}

/* 分类横滚 */
.cats{white-space:nowrap;padding:0 16px}
.cat{display:inline-flex;flex-direction:column;align-items:center;gap:7px;width:56px;vertical-align:top}
.cat .b{width:46px;height:46px;border-radius:50%;background:var(--card);box-shadow:var(--sh-sm);display:flex;align-items:center;justify-content:center;margin:0 auto}
.cat .b .emo{font-size:24px}
.cat .t{font-size:11.5px;color:var(--ink2)}

/* 玩法双卡 */
.plays{display:flex;gap:11px;margin:0 16px}
.play{flex:1;border-radius:var(--r2);padding:14px;position:relative;overflow:hidden;min-height:106px;display:flex;flex-direction:column;justify-content:space-between;color:#fff}
.play.rank{background:linear-gradient(135deg,#C99A4B,#9A6E22)}
.play.nb{background:var(--grad)}
.play .ppic{position:absolute;right:-2px;bottom:-6px;opacity:.22}
.play .ppic .emo{font-size:56px}
.play .ptag{font-size:10px;font-weight:700;letter-spacing:.5px;opacity:.95;position:relative;z-index:1}
.play .ph2{font-size:15.5px;font-weight:800;letter-spacing:-.3px;position:relative;z-index:1;margin-top:4px}
.play .pft{display:flex;align-items:center;justify-content:space-between;position:relative;z-index:1}
.play .pft .m{font-size:10px;opacity:.9}
.play .pft .m .b{font-size:14px;font-weight:800;display:block;letter-spacing:-.3px}
.play .pft .pgo{font-size:10px;font-weight:700;background:rgba(255,255,255,.94);color:var(--ink);height:22px;padding:0 9px;border-radius:6px;display:flex;align-items:center}

/* 最近去过 */
.recent{white-space:nowrap;padding:2px 16px 0}
.rc{display:inline-block;width:142px;margin-right:11px;background:var(--card);border-radius:var(--r2);box-shadow:var(--sh-sm);overflow:hidden;vertical-align:top}
.rc-cv{height:74px;position:relative;background:linear-gradient(135deg,#FFD8B8,#FF9A4A);display:flex;align-items:center;justify-content:center}
.rc-cv.t2{background:linear-gradient(135deg,#FFE8C9,#FFCF6B)}
.rc-cv.t3{background:linear-gradient(135deg,#D6F0E5,#7FD6B5)}
.rc-cv.t4{background:linear-gradient(135deg,#FFDDE5,#F9A8D4)}
.rc-cv .rc-img{position:absolute;top:0;left:0;width:100%;height:100%}
.rc-cv .rc-ph{font-size:28px;font-weight:800;color:rgba(255,255,255,.92)}
.rc-cv .vt{position:absolute;left:6px;bottom:6px;height:18px;padding:0 7px;border-radius:5px;background:rgba(0,0,0,.4);color:#fff;font-size:9.5px;font-weight:600;display:flex;align-items:center}
.rc-bd{padding:8px 10px 10px}
.rc-bd .n{font-size:12.5px;font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.rc-bd .m{font-size:10.5px;color:var(--ink3);margin-top:3px}
.rc-bd .m .b{color:var(--br-d);font-weight:700}

/* 附近好店 单列卡 */
.loading{padding:30px;text-align:center;color:var(--ink3);font-size:12px}
.shops{padding:0 16px}
.shop{background:var(--card);border-radius:var(--r1);box-shadow:var(--sh);padding:12px;display:flex;gap:12px;margin-bottom:11px}
.shop .cv{width:108px;height:108px;flex:none;border-radius:13px;overflow:hidden;position:relative;background:var(--fill);display:flex;align-items:center;justify-content:center}
.shop .cv .cv-img{width:100%;height:100%}
.shop .cv .cv-ph{font-size:34px;font-weight:800;color:#C4C9D0}
.shop .cv .st{position:absolute;left:7px;bottom:7px;display:flex;align-items:center;gap:4px;height:21px;padding:0 8px;border-radius:6px;font-size:10px;font-weight:700;color:#fff}
.shop .cv .st.open{background:rgba(19,178,106,.95)}
.shop .cv .st.close{background:rgba(80,86,94,.92)}
.shop .cv .st .d{width:4px;height:4px;border-radius:2px;background:#fff}
.shop .bd{flex:1;min-width:0;display:flex;flex-direction:column}
.shop .nm{font-size:16px;font-weight:600;letter-spacing:-.3px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.shop .mt{display:flex;align-items:center;gap:7px;margin-top:5px;font-size:12px;color:var(--ink2)}
.shop .mt .rate{color:var(--star);font-weight:700}
.shop .mt .dot{width:3px;height:3px;border-radius:2px;background:var(--line)}
.shop .tags{display:flex;gap:6px;margin-top:8px;flex-wrap:wrap}
.shop .tag{font-size:11px;font-weight:600;height:21px;padding:0 8px;border-radius:6px;display:inline-flex;align-items:center;background:var(--br-50);color:var(--br-d)}
.shop .tag.line{background:transparent;border:1px solid var(--line);color:var(--ink2);font-weight:500}
.shop .spu{display:flex;align-items:center;gap:8px;margin-top:auto;padding-top:9px}
.shop .spu .l{flex:none;font-size:10px;font-weight:700;color:#fff;background:var(--gold);height:18px;padding:0 6px;border-radius:5px;display:flex;align-items:center}
.shop .spu .n{flex:1;min-width:0;font-size:12px;color:var(--ink2);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.shop .spu .p{flex:none;font-size:15px;font-weight:800;color:var(--price)}
.shop .spu .p .c{font-size:10px}

.bottom-pad{height:12px}
</style>
