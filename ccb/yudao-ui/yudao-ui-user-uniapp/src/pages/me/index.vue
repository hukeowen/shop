<template>
  <view class="page">
    <view class="hero">
      <view class="me-row">
        <view class="me-avatar">{{ avatarText }}</view>
        <view class="me-body">
          <view v-if="user.isLogin" class="me-name">{{ user.nickname || user.phone || '客小二用户' }}</view>
          <view v-else class="me-name" @click="goLogin">点我登录 ›</view>
          <view class="me-sub" v-if="user.isLogin">ID: {{ user.userId }}</view>
        </view>
        <view class="me-set">⚙</view>
      </view>

      <view class="earn-card">
        <view class="ec-head">
          <text class="ec-tag">💎 累计已赚</text>
          <text class="ec-more" @click="goWallet">钱包 ›</text>
        </view>
        <view class="ec-amt">¥{{ totalEarn }}</view>
        <view class="ec-row">
          <view class="ec-col">
            <view class="ec-l">推广积分</view>
            <view class="ec-v">{{ promoBalance }}</view>
          </view>
          <view class="ec-col">
            <view class="ec-l">消费积分</view>
            <view class="ec-v">{{ consumeBalance }}</view>
          </view>
          <view class="ec-col">
            <view class="ec-l">今日入账</view>
            <view class="ec-v hl">¥{{ todayEarn }}</view>
          </view>
        </view>
      </view>
    </view>

    <view class="grid">
      <view v-for="g in grids" :key="g.k" class="g" @click="go(g)">
        <view class="g-ic">{{ g.ic }}</view>
        <view class="g-l">{{ g.label }}</view>
        <view v-if="g.badge" class="g-badge">{{ g.badge }}</view>
      </view>
    </view>

    <view class="section-title"><text class="h">我的服务</text></view>
    <view class="list">
      <view v-for="r in services" :key="r.k" class="li" @click="go(r)">
        <text class="li-ic">{{ r.ic }}</text>
        <text class="li-l">{{ r.label }}</text>
        <text class="li-arrow">›</text>
      </view>
    </view>

    <view v-if="user.isLogin" class="logout" @click="onLogout">退出登录</view>
    <view class="bottom-pad"></view>
    <bottom-nav active="me" />
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { useUserStore } from '@/store/user.js';
import { getAccount, getTodayStat } from '@/api/promo.js';
import { getCartCount } from '@/api/cart.js';
import { getUnusedCouponCount } from '@/api/coupon.js';
import { favoriteCount } from '@/api/product.js';
import { fen2yuan } from '@/utils/format.js';

const user = useUserStore();
const avatarText = computed(() => (user.nickname?.[0] || user.phone?.[0] || '客'));

const totalEarn = ref('0.00');
const promoBalance = ref(0);
const consumeBalance = ref(0);
const todayEarn = ref('0.00');

const grids = ref([
  { k: 'orders', ic: '📋', label: '订单',   url: '/pages/order/list',        badge: '' },
  { k: 'cart',   ic: '🛒', label: '购物车', url: '/pages/cart/index',        badge: '' },
  { k: 'coupon', ic: '🎟', label: '优惠券', url: '/pages/coupon/index',      badge: '' },
  { k: 'fav',    ic: '❤️', label: '收藏',   url: '/pages/favorites/index',   badge: '' },
]);

const services = [
  { k: 'wallet',   ic: '💰', label: '我的钱包',           url: '/pages/wallet/index' },
  { k: 'withdraw', ic: '💸', label: '提现申请',           url: '/pages/withdraw/index' },
  { k: 'queue',    ic: '🔥', label: '我的队列（推 N 反 1）', url: '/pages/queue/index' },
  { k: 'invite',   ic: '👥', label: '邀请好友',           url: '/pages/invite/index' },
  { k: 'promo-pt', ic: '⭐', label: '推广积分明细',       url: '/pages/points/promo' },
  { k: 'cons-pt',  ic: '🪙', label: '消费积分明细',       url: '/pages/points/consume' },
  { k: 'address',  ic: '📍', label: '收货地址',           url: '/pages/address/list' },
];

function goLogin() { uni.navigateTo({ url: '/pages/login/index' }); }
function goWallet() { uni.navigateTo({ url: '/pages/wallet/index' }); }
function go(g) {
  if (!user.isLogin) return goLogin();
  uni.navigateTo({ url: g.url });
}
function onLogout() {
  uni.showModal({ title: '退出登录', content: '确定退出吗？', success: ({ confirm }) => {
    if (confirm) { user.logout(); uni.reLaunch({ url: '/pages/me/index' }); }
  }});
}

async function load() {
  if (!user.isLogin) return;
  try {
    const acct = await getAccount();
    promoBalance.value = acct?.promoPointBalance || 0;
    consumeBalance.value = acct?.consumePointBalance || 0;
    totalEarn.value = fen2yuan(promoBalance.value, false);
  } catch {}
  try {
    const stat = await getTodayStat();
    todayEarn.value = fen2yuan(stat?.promoAmountToday || 0, false);
  } catch {}
  // badge 数（购物车 / 优惠券 / 收藏）— 失败不影响主流程
  try { const n = await getCartCount();          if (n) grids.value.find((g) => g.k === 'cart').badge = n; } catch {}
  try { const n = await getUnusedCouponCount();  if (n) grids.value.find((g) => g.k === 'coupon').badge = n; } catch {}
  try { const n = await favoriteCount();         if (n) grids.value.find((g) => g.k === 'fav').badge = n; } catch {}
}
onMounted(load);
onShow(load);
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; padding-bottom: 90px; background: $bg; }
.hero {
  padding: 24px 14px 16px;
  background:
    radial-gradient(500px 220px at 20% 0%, rgba(255,107,53,.25), transparent 60%),
    linear-gradient(180deg, #18130E 0%, #2A1A0F 100%);
  border-bottom-left-radius: 24px; border-bottom-right-radius: 24px;
  color: #fff;
}
.me-row { display: flex; align-items: center; gap: 12px; }
.me-avatar {
  width: 54px; height: 54px; border-radius: 50%;
  background: linear-gradient(135deg, $o, $gold); color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-weight: 800; font-size: 22px;
}
.me-body { flex: 1; }
.me-name { font-size: 17px; font-weight: 800; }
.me-sub { font-size: 11px; opacity: .6; margin-top: 2px; }
.me-set { font-size: 20px; opacity: .8; }

.earn-card {
  margin-top: 16px;
  background: rgba(255,255,255,.08);
  border-radius: $r-lg;
  padding: 14px;
}
.ec-head { display: flex; align-items: center; justify-content: space-between; }
.ec-tag { font-size: 12px; opacity: .8; }
.ec-more { font-size: 11px; color: $gold-l; }
.ec-amt {
  margin-top: 6px;
  font-size: 32px; font-weight: 900;
  background: linear-gradient(135deg, #fff, $gold-l);
  -webkit-background-clip: text; background-clip: text; color: transparent;
}
.ec-row { display: flex; margin-top: 12px; }
.ec-col { flex: 1; }
.ec-l { font-size: 11px; opacity: .7; }
.ec-v { font-size: 16px; font-weight: 800; margin-top: 2px; }
.ec-v.hl { color: $o-l; }

.grid {
  display: flex; margin: 16px 14px 0; gap: 8px;
}
.g {
  flex: 1; padding: 14px 0;
  background: $card; border-radius: $r-md; box-shadow: $sh-1;
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  position: relative;
}
.g-ic { font-size: 22px; }
.g-l { font-size: 11px; color: $t2; font-weight: 600; }
.g-badge {
  position: absolute; top: 6px; right: 14px;
  background: $danger; color: #fff;
  font-size: 9px; padding: 2px 6px; border-radius: 8px;
}

.section-title { padding: 18px 16px 8px; }
.section-title .h { font-size: 14px; font-weight: 800; color: $t1; }

.list { margin: 0 14px; background: $card; border-radius: $r-md; overflow: hidden; box-shadow: $sh-1; }
.li {
  display: flex; align-items: center; gap: 10px;
  padding: 14px 14px;
  border-bottom: 1px solid $line;
  &:last-child { border-bottom: none; }
}
.li-ic { font-size: 18px; width: 24px; }
.li-l { flex: 1; font-size: 14px; color: $t1; }
.li-arrow { color: $t4; font-size: 18px; }

.logout {
  margin: 30px 14px 0;
  padding: 14px 0;
  text-align: center;
  background: $card; border-radius: $r-md;
  color: $danger; font-size: 14px; font-weight: 700;
}
.bottom-pad { height: 30px; }
</style>
