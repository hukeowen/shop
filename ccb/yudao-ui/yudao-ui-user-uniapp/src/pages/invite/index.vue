<template>
  <view class="page">
    <view class="hero">
      <view class="hero-em">🎁</view>
      <view class="hero-t">邀请好友进店下单</view>
      <view class="hero-d">每店一条专属链接 · 朋友首单立减 · 你拿推广积分</view>
    </view>

    <!-- 资格门槛：必须先在某店完成「推 N 反 1」商品购买 -->
    <view v-if="!loading && !eligible" class="gate">
      <view class="gate-em">🔒</view>
      <view class="gate-t">先完成「推 N 反 1」购买，才能开启邀请</view>
      <view class="gate-d">
        平台规则：只有在某个店铺成功购买过「推 N 反 1」商品后，你才有资格分享该店的邀请链接。
        这样一来你和朋友都在同一店铺内，你的邀请奖励才能从该店派发。
      </view>
      <view class="gate-list">
        <view class="gate-step"><text class="n">1</text>选一家本地店，挑一个「推 N 反 1」商品</view>
        <view class="gate-step"><text class="n">2</text>完成下单（首单还能用券）</view>
        <view class="gate-step"><text class="n">3</text>回到本页，按店分享专属链接</view>
      </view>
      <view class="gate-btn" @click="goNearby">去逛附近店 →</view>
    </view>

    <!-- 已激活：按店列出邀请链接 -->
    <view v-if="!loading && eligible" class="shops">
      <view class="shops-tip">
        <text class="em">💡</text>
        <text>你已在 <text class="hl">{{ shops.length }}</text> 家店激活邀请资格，请选择对应店铺生成链接</text>
      </view>
      <view v-for="(s, i) in shopsWithLinks" :key="s.tenantId" class="shop-card" :class="{ active: activeIdx === i }">
        <view class="shop-head" @click="activeIdx = i">
          <view class="shop-em">🏪</view>
          <view class="shop-body">
            <view class="shop-name">{{ s.shopName }}</view>
            <view class="shop-meta">
              <text v-if="s.queueingCount">进行中 {{ s.queueingCount }}</text>
              <text v-if="s.queueingCount && s.completedCount"> · </text>
              <text v-if="s.completedCount">已完成 {{ s.completedCount }}</text>
            </view>
          </view>
          <view class="shop-arrow">{{ activeIdx === i ? '▼' : '›' }}</view>
        </view>
        <view v-if="activeIdx === i" class="shop-expand">
          <view class="link-box">{{ s.inviteLink }}</view>
          <view class="link-actions">
            <view class="link-btn primary" @click="onCopy(s.inviteLink)">复制链接</view>
            <view class="link-btn ghost" @click="onShowQr(s)">查看二维码</view>
          </view>
          <view class="rule-tip">
            朋友点开链接 → 进入「{{ s.shopName }}」 → 注册登录 → 即与你绑定为本店「直推关系」（终身仅此一店）
          </view>
        </view>
      </view>
    </view>

    <!-- 我的累计 -->
    <view v-if="!loading && eligible" class="stat-card">
      <view class="stat-title">我的累计</view>
      <view class="stat-row"><text class="l">已邀朋友（跨店）</text><text class="hl">{{ totalChildren }} 人</text></view>
      <view class="stat-row"><text class="l">推广积分余额</text><text class="hl">¥{{ totalEarn }}</text></view>
      <view class="stat-foot">
        推广积分用途：① 该店内消费抵扣 ② 申请向商户提现（线下兑付）
      </view>
    </view>

    <!-- QR 弹层 -->
    <view v-if="qrVisible" class="qr-mask" @click="qrVisible = false">
      <view class="qr-box" @click.stop>
        <view class="qr-title">{{ qrShop?.shopName }}</view>
        <image v-if="qrShop?.qrUrl" :src="qrShop.qrUrl" class="qr-img" mode="aspectFit" />
        <view v-else class="qr-fallback">长按复制链接发给朋友</view>
        <view class="qr-link">{{ qrShop?.inviteLink }}</view>
        <view class="qr-close" @click="qrVisible = false">关闭</view>
      </view>
    </view>

    <view v-if="loading" class="loading">加载中…</view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useUserStore } from '@/store/user.js';
import { getInviteEligibility, getAccount, getMyChildrenCount } from '@/api/promo.js';
import { fen2yuan } from '@/utils/format.js';

const user = useUserStore();
const loading = ref(true);
const eligible = ref(false);
const shops = ref([]);
const activeIdx = ref(0);
const totalChildren = ref(0);
const totalEarn = ref('0.00');
const qrVisible = ref(false);
const qrShop = ref(null);

const baseOrigin = computed(() => (typeof location !== 'undefined' ? location.origin : 'https://ke.doupaidoudian.com'));

const shopsWithLinks = computed(() =>
  shops.value.map((s) => ({
    ...s,
    inviteLink: `${baseOrigin.value}/#/pages/shop/home?tenantId=${s.tenantId}&inviter=${user.userId || ''}`,
  })),
);

function onCopy(link) {
  uni.setClipboardData({ data: link, success: () => uni.showToast({ title: '已复制', icon: 'success' }) });
}

function onShowQr(s) {
  // 暂用 google chart QR API 兜底（没接公司图库时）
  const enc = encodeURIComponent(s.inviteLink);
  qrShop.value = { ...s, qrUrl: `https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=${enc}` };
  qrVisible.value = true;
}

function goNearby() {
  uni.switchTab({ url: '/pages/nearby/index', fail: () => uni.navigateTo({ url: '/pages/nearby/index' }) });
}

onMounted(async () => {
  loading.value = true;
  try {
    const r = await getInviteEligibility();
    eligible.value = !!r?.eligible;
    shops.value = Array.isArray(r?.shops) ? r.shops : [];
  } catch {
    eligible.value = false;
    shops.value = [];
  }
  // 跨店聚合：每个激活的店逐个查 my-children-count 求和
  try {
    if (eligible.value && shops.value.length) {
      const counts = await Promise.all(
        shops.value.map((s) => getMyChildrenCount(s.tenantId).catch(() => 0)),
      );
      totalChildren.value = counts.reduce((acc, c) => acc + (Number(c) || 0), 0);
    }
  } catch {}
  try {
    const acct = await getAccount();
    totalEarn.value = fen2yuan(acct?.promoPointBalance || 0, false);
  } catch {}
  loading.value = false;
});
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.page { min-height: 100vh; background: $bg-2; padding-bottom: 80rpx; }

.hero {
  padding: 36rpx 28rpx 32rpx;
  background: linear-gradient(135deg, #18130E, #2A1A0F);
  color: #fff; text-align: center;
  border-bottom-left-radius: 32rpx; border-bottom-right-radius: 32rpx;
}
.hero-em { font-size: 84rpx; }
.hero-t {
  font-size: 36rpx; font-weight: 900; margin-top: 8rpx;
  background: linear-gradient(135deg, #fff, $gold-l);
  -webkit-background-clip: text; background-clip: text; color: transparent;
}
.hero-d { font-size: 22rpx; opacity: .7; margin-top: 8rpx; }

/* 资格门槛 */
.gate {
  margin: 24rpx 28rpx;
  background: #fff; border-radius: 24rpx; padding: 36rpx 28rpx;
  box-shadow: $sh-1;
  text-align: center;
}
.gate-em { font-size: 80rpx; line-height: 1; }
.gate-t {
  font-size: 32rpx; font-weight: 900; color: $t1;
  margin-top: 16rpx;
}
.gate-d {
  font-size: 24rpx; color: $t3; line-height: 1.6;
  margin-top: 12rpx;
}
.gate-list { margin-top: 24rpx; text-align: left; }
.gate-step {
  display: flex; align-items: center; gap: 14rpx;
  padding: 14rpx 16rpx;
  background: $bg-2; border-radius: 16rpx;
  font-size: 26rpx; color: $t2; font-weight: 600;
  margin-bottom: 12rpx;
}
.gate-step .n {
  width: 44rpx; height: 44rpx; border-radius: 50%;
  background: linear-gradient(135deg, $o, $o-d);
  color: #fff; text-align: center; line-height: 44rpx;
  font-weight: 900; font-size: 24rpx;
  flex-shrink: 0;
}
.gate-btn {
  margin-top: 24rpx;
  padding: 22rpx;
  background: linear-gradient(135deg, $o, $o-d);
  color: #fff; border-radius: 999rpx;
  font-weight: 900; font-size: 28rpx; letter-spacing: 2rpx;
  box-shadow: $sh-warm;
}

/* 店铺列表 */
.shops { padding: 0 20rpx; }
.shops-tip {
  display: flex; align-items: center; gap: 10rpx;
  padding: 16rpx 20rpx;
  font-size: 24rpx; color: $t2;
  margin: 20rpx 8rpx 8rpx;
}
.shops-tip .em { font-size: 28rpx; }
.shops-tip .hl { color: $o; font-weight: 900; }

.shop-card {
  background: #fff; border-radius: 20rpx;
  margin: 14rpx 8rpx;
  box-shadow: $sh-1;
  overflow: hidden;
  transition: box-shadow .15s;
}
.shop-card.active { box-shadow: 0 6rpx 24rpx rgba(255,107,53,.18); }
.shop-head {
  display: flex; align-items: center; gap: 16rpx;
  padding: 22rpx 22rpx;
}
.shop-em {
  width: 72rpx; height: 72rpx; border-radius: 18rpx;
  background: linear-gradient(135deg, $o-50, $bg-2);
  display: flex; align-items: center; justify-content: center;
  font-size: 36rpx;
  flex-shrink: 0;
}
.shop-body { flex: 1; min-width: 0; }
.shop-name {
  font-size: 30rpx; font-weight: 800; color: $t1;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.shop-meta {
  margin-top: 6rpx;
  font-size: 22rpx; color: $t3;
}
.shop-arrow { color: $t4; font-size: 28rpx; flex-shrink: 0; }

.shop-expand {
  padding: 0 22rpx 22rpx;
  border-top: 1rpx solid $line;
}
.link-box {
  margin-top: 16rpx;
  padding: 16rpx;
  background: $bg-2; border-radius: 12rpx;
  font-size: 22rpx; color: $t3; word-break: break-all;
  line-height: 1.5;
}
.link-actions {
  margin-top: 14rpx;
  display: flex; gap: 14rpx;
}
.link-btn {
  flex: 1; padding: 18rpx;
  text-align: center; border-radius: 999rpx;
  font-weight: 800; font-size: 26rpx;
}
.link-btn.primary {
  background: linear-gradient(135deg, $o, $o-d); color: #fff;
  box-shadow: $sh-warm;
}
.link-btn.ghost {
  background: $bg-2; color: $t2;
  border: 2rpx solid $line;
}
.rule-tip {
  margin-top: 16rpx;
  font-size: 22rpx; color: $t4; line-height: 1.6;
}

/* 统计卡 */
.stat-card {
  background: #fff; margin: 20rpx 28rpx;
  border-radius: 20rpx; padding: 24rpx;
  box-shadow: $sh-1;
}
.stat-title {
  font-size: 28rpx; font-weight: 800; color: $t1;
  margin-bottom: 14rpx;
}
.stat-row {
  display: flex; justify-content: space-between;
  padding: 10rpx 0; font-size: 26rpx; color: $t2;
}
.stat-row .hl { color: $o; font-weight: 900; }
.stat-foot {
  margin-top: 14rpx; padding-top: 14rpx;
  border-top: 1rpx solid $line;
  font-size: 22rpx; color: $t4; line-height: 1.6;
}

/* QR */
.qr-mask {
  position: fixed; inset: 0; z-index: 999;
  background: rgba(0,0,0,.7);
  display: flex; align-items: center; justify-content: center;
}
.qr-box {
  width: 80vw; max-width: 600rpx;
  background: #fff; border-radius: 24rpx;
  padding: 32rpx; text-align: center;
}
.qr-title { font-size: 28rpx; font-weight: 800; color: $t1; }
.qr-img { width: 480rpx; height: 480rpx; margin: 20rpx auto; display: block; }
.qr-fallback { padding: 80rpx 20rpx; color: $t3; font-size: 26rpx; }
.qr-link {
  padding: 12rpx; background: $bg-2; border-radius: 12rpx;
  font-size: 20rpx; color: $t3; word-break: break-all;
}
.qr-close {
  margin-top: 16rpx; padding: 18rpx;
  background: $bg-2; border-radius: 999rpx;
  font-weight: 700; font-size: 26rpx; color: $t2;
}

.loading { text-align: center; padding: 60rpx 0; color: $t4; font-size: 26rpx; }
</style>
