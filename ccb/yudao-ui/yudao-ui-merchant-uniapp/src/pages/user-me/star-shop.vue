<template>
  <view class="page">
    <view class="topbar safe-top">
      <text class="back" @click="goBack">‹</text>
      <text class="title">{{ shopName || '店铺' }} · 商品星级</text>
      <view style="width:60rpx"></view>
    </view>

    <!-- 店铺汇总 hero -->
    <view class="shop-hero">
      <view class="name">🏪 {{ shopName || '店铺 ' + tenantId }}</view>
      <view class="stats">
        <view class="stat">
          <view class="lbl">最高星级</view>
          <view class="val stars">{{ maxStar > 0 ? stars(maxStar) : '—' }}</view>
        </view>
        <view class="stat">
          <view class="lbl">推广积分</view>
          <view class="val">{{ ((shopAccount.promoPoints || 0) / 100).toFixed(2) }}</view>
        </view>
        <view class="stat">
          <view class="lbl">消费积分</view>
          <view class="val">{{ ((shopAccount.points || 0) / 100).toFixed(2) }}</view>
        </view>
      </view>
      <view class="hero-tip">
        💡 <text class="b">推广积分</text>可线下到商家提现 · <text class="b">消费积分</text>下次消费直接抵扣（1 积分 = 1 元）
      </view>
    </view>

    <!-- 分享拉新卡片 -->
    <view class="share-card">
      <view class="share-info">
        <view class="share-title">🎁 分享这家店给朋友</view>
        <view class="share-sub">朋友通过你的链接消费，按商品规则给你返团队订单分润</view>
      </view>
      <view class="share-actions">
        <view class="share-btn primary" @click="onCopyLink">复制邀请链接</view>
        <view class="share-btn ghost" @click="onShowQr">生成二维码</view>
      </view>
    </view>

    <!-- QR 弹层 -->
    <view v-if="qrShow" class="qr-mask" @click="qrShow=false">
      <view class="qr-modal" @click.stop>
        <view class="qr-title">长按二维码 → 保存 / 分享给朋友</view>
        <image v-if="qrUrl" class="qr-img" :src="qrUrl" mode="aspectFit" />
        <view class="qr-link">{{ shareUrl }}</view>
        <view class="qr-close" @click="qrShow=false">关闭</view>
      </view>
    </view>

    <view v-if="loading && !items.length" class="empty-tip">加载中…</view>
    <view v-else-if="!items.length" class="empty-state">
      <view class="empty-emoji">🛍</view>
      <view class="empty-title">本店暂无星级商品</view>
      <view class="empty-sub">购买带「推 N 反 1」标识的商品后会在这里展示</view>
    </view>

    <view v-else class="list">
      <view v-for="it in items" :key="it.spuId" class="spu-card">
        <view class="spu-head">
          <image v-if="it.picUrl" class="spu-pic" :src="it.picUrl" mode="aspectFill" />
          <view v-else class="spu-pic placeholder">🛍</view>
          <view class="spu-info">
            <view class="spu-name">{{ it.spuName || '商品 #' + it.spuId }}</view>
            <view class="spu-current">
              <text v-if="it.currentStar > 0" class="badge">{{ stars(it.currentStar) }}</text>
              <text v-else class="badge zero">未升星</text>
              <text v-if="getRules(it).length && it.currentStar >= getRules(it).length" class="progress-text">满星</text>
              <text v-else-if="nextRule(it)" class="progress-text">
                距 {{ stars((it.currentStar || 0) + 1) }} 还差
                {{ Math.max(0, (nextRule(it).requiredCount || 0) - (it.directCount || 0)) }} 个
                {{ (nextRule(it).requiredStar || 0) > 0 ? stars(nextRule(it).requiredStar) : '付费' }} 顾客
              </text>
            </view>
          </view>
        </view>

        <view v-if="getRules(it).length" class="stars-section">
          <view class="stars-title">本商品升星规则 + 权益</view>

          <view
            v-for="(r, i) in getRules(it)"
            :key="i"
            :class="['star-row', (it.currentStar || 0) >= (i + 1) ? 'achieved' : (((it.currentStar || 0) === i) ? 'inprogress' : '')]"
          >
            <view class="star-tag">{{ stars(i + 1) }}</view>
            <view class="star-body">
              <view class="star-section-lbl">
                <template v-if="(it.currentStar || 0) >= (i + 1)">升星条件 <text class="check">✓ 已达成</text></template>
                <template v-else-if="(it.currentStar || 0) === i">升星条件 · 进行中</template>
                <template v-else>升星条件</template>
              </view>
              <view class="cond">
                直推 ≥ <text class="b">{{ r.requiredCount || 0 }}</text> 个
                <text v-if="(r.requiredStar || 0) > 0">{{ stars(r.requiredStar) }} </text>
                <text v-else>付费 </text>顾客 +
                团队累计 ≥ <text class="b">¥{{ ((r.teamSales || 0) / 100).toFixed(2) }}</text>
              </view>
              <view v-if="(it.currentStar || 0) === i" class="progress-bar"><view class="fill" :style="`width:${pct(it, r)}%`"></view></view>
              <view v-if="(it.currentStar || 0) === i" class="progress-detail">
                <text>直推 {{ it.directCount || 0 }} / {{ r.requiredCount || 0 }}</text>
                <text>团队 ¥{{ ((it.teamSalesAmount || 0) / 100).toFixed(2) }} / ¥{{ ((r.teamSales || 0) / 100).toFixed(2) }}</text>
              </view>
              <view class="benefit">
                <text v-if="(it.currentStar || 0) >= (i + 1)">⚡ 享受权益：</text>
                <text v-else>⚡ 升上去后享受：</text>
                <text class="benefit-line">· 团队订单分润 <text class="b">{{ getStarRatio(it, i) }}%</text></text>
                <text class="benefit-line">· 店铺奖池分润 <text class="b">{{ getPoolShare(it, i + 1) }}%</text></text>
              </view>
            </view>
          </view>

          <view v-if="getRules(it).length && (it.currentStar || 0) >= getRules(it).length" class="max-tip">🏆 已达本商品最高星级</view>
        </view>
        <view v-else class="no-rules">商家未配置升星规则；按商品「推 N 反 1」直接返奖。</view>
      </view>
    </view>

    <view class="bottom-space"></view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { request } from '../../api/request.js';
import { useUserStore } from '../../store/user.js';

const userStore = useUserStore();
const tenantId = ref(null);
const shopName = ref('');
const items = ref([]);
const shopAccount = ref({ star: 0, promoPoints: 0, points: 0 });
const loading = ref(false);
const qrShow = ref(false);

const shareUrl = computed(() => {
  if (!tenantId.value) return '';
  const inviter = userStore.userId || '';
  // 强制走 ke. 域；shop-home 顶层 query 让 App.vue 走 shop-share 分支
  let origin = 'https://ke.doupaidoudian.com';
  try {
    if (typeof location !== 'undefined' && location.origin && /doupaidoudian/i.test(location.origin)) {
      const u = new URL(location.origin);
      u.host = u.host.replace(/^(tuo|www|admin)\./, 'ke.');
      if (!/^ke\./.test(u.host)) u.host = 'ke.' + u.host.replace(/^[^.]+\./, '');
      origin = u.origin;
    }
  } catch {}
  return `${origin}/m/shop-home?tenantId=${tenantId.value}${inviter ? `&inviter=${inviter}` : ''}`;
});
const qrUrl = computed(() => {
  if (!shareUrl.value) return '';
  const base = (typeof location !== 'undefined' && location.origin) ? location.origin : '';
  return `${base}/qr?text=${encodeURIComponent(shareUrl.value)}&w=480&m=1&center=${encodeURIComponent(shopName.value || '店铺')}`;
});

function onCopyLink() {
  if (!shareUrl.value) {
    uni.showToast({ title: '链接生成失败', icon: 'none' });
    return;
  }
  uni.setClipboardData({
    data: shareUrl.value,
    success: () => uni.showToast({ title: '邀请链接已复制', icon: 'success' }),
    fail: () => uni.showToast({ title: '复制失败', icon: 'none' }),
  });
}
function onShowQr() {
  qrShow.value = true;
}

const maxStar = computed(() => {
  let mx = 0;
  for (const it of items.value) if ((it.currentStar || 0) > mx) mx = it.currentStar;
  return mx;
});

function stars(n) {
  const cnt = Math.max(0, Math.min(10, parseInt(n) || 0));
  return '⭐'.repeat(cnt);
}
function getRules(it) {
  try {
    const arr = JSON.parse(it.starUpgradeRules || '[]');
    return Array.isArray(arr) ? arr : [];
  } catch { return []; }
}
function getStarRatios(it) {
  try {
    const arr = JSON.parse(it.starRatios || '[]');
    return Array.isArray(arr) ? arr : [];
  } catch { return []; }
}
function getStarRatio(it, idx) {
  const arr = getStarRatios(it);
  return arr[idx] != null ? arr[idx] : 0;
}
function getPoolShare(it, star) {
  // pool dist rules: [{star,ratio,mode,winners?}]
  try {
    const arr = JSON.parse(it.poolDistRules || '[]');
    if (!Array.isArray(arr)) return 0;
    const m = arr.find(x => Number(x.star) === Number(star));
    return m ? (m.ratio || 0) : 0;
  } catch { return 0; }
}
function nextRule(it) {
  const rules = getRules(it);
  const s = it.currentStar || 0;
  if (s >= rules.length) return null;
  return rules[s];
}
function pct(it, r) {
  if (!r) return 0;
  const pDirect = r.requiredCount ? Math.min(100, ((it.directCount || 0) / r.requiredCount) * 100) : 100;
  const pTeam = r.teamSales ? Math.min(100, ((it.teamSalesAmount || 0) / r.teamSales) * 100) : 100;
  return Math.round((pDirect + pTeam) / 2);
}
function goBack() { uni.navigateBack({ fail: () => uni.reLaunch({ url: '/pages/user-me/star' }) }); }

onLoad(async (q) => {
  tenantId.value = q?.tenantId ? Number(q.tenantId) : null;
  shopName.value = q?.shopName ? decodeURIComponent(q.shopName) : '';
  if (!tenantId.value) return;
  loading.value = true;
  try {
    // 1) 该店每个 SPU 的星级 + 商品配置
    const list = await request({
      url: `/app-api/merchant/mini/promo/my-spu-stars?tenantId=${tenantId.value}`,
    });
    items.value = Array.isArray(list) ? list : [];
    // 2) 该店账户余额（推广 / 消费积分；从 my-shops-enriched 单独取）
    try {
      const shops = await request({ url: '/app-api/merchant/mini/member-rel/my-shops-enriched' });
      const me = (shops || []).find(s => s.tenantId === tenantId.value);
      if (me) shopAccount.value = me;
    } catch {}
  } catch { items.value = []; }
  finally { loading.value = false; }
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';
.page { min-height: 100vh; background: $bg-page; padding-bottom: 40rpx; }
.safe-top { padding-top: calc(max(env(safe-area-inset-top, 0px), var(--status-bar-height, 0px)) + 16rpx) !important; }
.topbar { display: flex; align-items: center; padding: 16rpx 32rpx; background: $bg-card; border-bottom: 1rpx solid $border-color; position: sticky; top: 0; z-index: 10; }
.topbar .back { font-size: 44rpx; color: $text-primary; padding-right: 16rpx; }
.topbar .title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; color: $text-primary; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; padding: 0 16rpx; }

.shop-hero {
  margin: 24rpx; padding: 36rpx;
  background: linear-gradient(135deg, #ff9a4a, $brand-primary);
  color: #fff; border-radius: $radius-lg;
  box-shadow: 0 16rpx 40rpx rgba(255,107,53,.30);
}
.shop-hero .name { font-size: 34rpx; font-weight: 700; }
.shop-hero .stats {
  display: flex; gap: 48rpx; margin-top: 24rpx;
  padding-top: 24rpx; border-top: 1rpx solid rgba(255,255,255,.2);
}
.shop-hero .stat .lbl { font-size: 22rpx; opacity: .85; }
.shop-hero .stat .val { font-size: 40rpx; font-weight: 800; margin-top: 8rpx; font-variant-numeric: tabular-nums; }
.shop-hero .stat .val.stars { font-size: 36rpx; letter-spacing: 4rpx; }
.shop-hero .hero-tip {
  margin-top: 20rpx; padding-top: 16rpx;
  border-top: 1rpx solid rgba(255,255,255,.2);
  font-size: 22rpx; line-height: 1.55; opacity: .92;
  .b { font-weight: 700; }
}

.share-card {
  margin: 0 24rpx 24rpx; padding: 28rpx;
  background: $bg-card; border-radius: $radius-lg;
  box-shadow: 0 4rpx 16rpx rgba(15,23,42,.04);
}
.share-card .share-info { margin-bottom: 20rpx; }
.share-card .share-title { font-size: 28rpx; font-weight: 700; color: $text-primary; }
.share-card .share-sub { margin-top: 6rpx; font-size: 22rpx; color: $text-secondary; line-height: 1.55; }
.share-card .share-actions { display: flex; gap: 16rpx; }
.share-card .share-btn {
  flex: 1; height: 76rpx; line-height: 76rpx;
  text-align: center; font-size: 26rpx; font-weight: 600;
  border-radius: $radius-md;
}
.share-card .share-btn.primary { background: $brand-primary; color: #fff; }
.share-card .share-btn.ghost { background: rgba(255,107,53,.08); color: $brand-primary; border: 2rpx solid rgba(255,107,53,.4); }

.qr-mask {
  position: fixed; inset: 0; z-index: 100;
  background: rgba(0,0,0,.6);
  display: flex; align-items: center; justify-content: center;
  padding: 32rpx;
}
.qr-modal {
  width: 80%; max-width: 600rpx;
  background: #fff; border-radius: $radius-lg;
  padding: 40rpx 32rpx;
  text-align: center;
}
.qr-modal .qr-title { font-size: 26rpx; color: $text-primary; font-weight: 600; margin-bottom: 24rpx; }
.qr-modal .qr-img { width: 400rpx; height: 400rpx; }
.qr-modal .qr-link {
  margin-top: 20rpx; padding: 16rpx;
  background: #f6f7f9; border-radius: $radius-sm;
  font-size: 22rpx; color: $text-secondary;
  word-break: break-all;
}
.qr-modal .qr-close {
  margin-top: 24rpx;
  padding: 16rpx; font-size: 28rpx;
  color: $brand-primary; font-weight: 600;
}

.empty-tip { text-align: center; padding: 80rpx 0; color: $text-placeholder; font-size: 26rpx; }
.empty-state { text-align: center; padding: 120rpx 60rpx; }
.empty-state .empty-emoji { font-size: 96rpx; margin-bottom: 24rpx; opacity: .5; }
.empty-state .empty-title { font-size: 32rpx; font-weight: 700; color: $text-primary; }
.empty-state .empty-sub { margin-top: 12rpx; font-size: 24rpx; color: $text-placeholder; }

.list { padding: 0 24rpx; }
.spu-card {
  background: $bg-card; border-radius: $radius-lg;
  padding: 32rpx; margin-bottom: 24rpx;
  box-shadow: 0 4rpx 16rpx rgba(15,23,42,.04);
}
.spu-head { display: flex; align-items: center; gap: 24rpx; }
.spu-pic {
  width: 112rpx; height: 112rpx; border-radius: $radius-md;
  background: #f6f7f9; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 52rpx; color: $text-placeholder;
}
.spu-info { flex: 1; min-width: 0; }
.spu-name { font-size: 30rpx; font-weight: 600; color: $text-primary; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.spu-current { margin-top: 12rpx; display: flex; align-items: center; gap: 16rpx; flex-wrap: wrap; }
.spu-current .badge {
  display: inline-block; padding: 6rpx 24rpx;
  background: linear-gradient(135deg, #fff5ef, #ffd1ba);
  color: $brand-primary; border-radius: 999rpx;
  font-size: 26rpx; font-weight: 700;
  letter-spacing: 4rpx;
}
.spu-current .badge.zero { background: #f6f7f9; color: $text-placeholder; font-weight: 400; letter-spacing: 0; }
.spu-current .progress-text { font-size: 22rpx; color: $text-secondary; }

.stars-section { margin-top: 32rpx; padding-top: 32rpx; border-top: 1rpx dashed $border-color; }
.stars-title { font-size: 26rpx; font-weight: 700; color: $text-primary; margin-bottom: 24rpx; }

.star-row {
  display: flex; gap: 20rpx; padding: 24rpx;
  background: #fafbfd; border-radius: $radius-sm;
  margin-bottom: 16rpx;
  border-left: 6rpx solid $border-color;
}
.star-row.achieved {
  background: linear-gradient(90deg, #fff5ef, #fff);
  border-left-color: $brand-primary;
}
.star-row.inprogress {
  background: linear-gradient(90deg, #fffbf3, #fff);
  border-left-color: #f59e0b;
}
.star-row .star-tag {
  flex-shrink: 0; width: 180rpx; padding: 14rpx 0;
  border-radius: $radius-sm; background: #eaeef2; color: $text-placeholder;
  display: flex; align-items: center; justify-content: center;
  font-size: 22rpx; letter-spacing: 2rpx;
  line-height: 1; height: fit-content;
  white-space: nowrap;
}
.star-row.achieved .star-tag {
  background: linear-gradient(135deg, #ff9a4a, $brand-primary); color: #fff;
  box-shadow: 0 4rpx 12rpx rgba(255,107,53,.25);
}
.star-row.inprogress .star-tag {
  background: linear-gradient(135deg, #fcd34d, #f59e0b); color: #fff;
}
.star-row .star-body { flex: 1; min-width: 0; font-size: 24rpx; line-height: 1.65; }
.star-row .star-section-lbl { color: $text-placeholder; font-size: 22rpx; font-weight: 600; margin-bottom: 4rpx; }
.star-row .check { color: $success; font-weight: 700; margin-left: 8rpx; }
.star-row .cond { color: $text-secondary; }
.star-row.achieved .cond { color: $text-primary; }
.star-row .b { color: $brand-primary; font-weight: 700; }

.star-row .progress-bar {
  margin-top: 12rpx; height: 10rpx; border-radius: 5rpx;
  background: #f0f2f5; overflow: hidden;
}
.star-row .progress-bar .fill {
  height: 100%; background: linear-gradient(90deg, #ff9a4a, $brand-primary);
  border-radius: 5rpx; transition: width .35s ease-out;
}
.star-row .progress-detail {
  margin-top: 8rpx; font-size: 22rpx; color: $text-placeholder;
  display: flex; justify-content: space-between;
}
.star-row .benefit {
  margin-top: 12rpx; color: $brand-primary;
  display: flex; flex-direction: column; gap: 4rpx;
}
.star-row .benefit-line { font-size: 24rpx; }

.no-rules { margin-top: 32rpx; padding: 24rpx; text-align: center; color: $text-placeholder; font-size: 24rpx; background: #fafbfd; border-radius: $radius-sm; }
.max-tip {
  margin-top: 16rpx; padding: 20rpx; text-align: center;
  background: linear-gradient(135deg, #fff8e1, #ffe7a3);
  border-radius: $radius-sm; font-size: 26rpx; color: #b07b15;
  font-weight: 700;
}

.bottom-space { height: 80rpx; }
</style>
