<template>
  <view class="page">
    <view class="hero">
      <view class="hero-l">本店会员</view>
      <view class="hero-big">{{ list.length }}</view>
      <view class="hero-sub">推荐入店 {{ referralCount }} 人 · 本店独立营销活动</view>
    </view>

    <view class="member-row" v-for="m in list" :key="m.userId">
      <view class="avatar" :style="avatarStyle(m)">{{ maskedMobile(m).slice(-2) }}</view>
      <view class="m-info">
        <view class="nm">{{ maskedMobile(m) }}</view>
        <view class="desc">
          <text v-if="m.parentUserId">由推荐人 {{ maskInviter(m.parentUserId) }} 邀请入店</text>
          <text v-else>自然入店</text>
          ·
          <text>已购 {{ m.orderCount || 0 }} 单 ¥{{ fen2yuan(m.orderAmount) }}</text>
        </view>
      </view>
      <view class="m-stats">
        <view class="star">★ {{ m.currentStar || 0 }}</view>
        <view class="bal">余额 ¥{{ fen2yuan(m.balance) }}</view>
        <view class="promo">推广积分 {{ fen2yuan(m.promoPointBalance) }}</view>
      </view>
    </view>

    <view class="empty" v-if="!list.length">暂无会员</view>
    <view class="legal-tip">
      会员数据仅用于经营管理 · 不向第三方共享 · 营销活动由本商户独立设定与兑付
    </view>
    <view class="bottom-space"></view>
  </view>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { fen2yuan } from '../../utils/format.js';
import { listMembers } from '../../api/report.js';

const list = ref([]);
const referralCount = computed(() => list.value.filter(m => m.parentUserId && m.parentUserId > 0).length);

// 手机号脱敏：138****5678（隐藏中间 4 位）
function maskedMobile(m) {
  const raw = String(m?.mobile || '').replace(/\D/g, '');
  if (raw.length === 11) return raw.slice(0, 3) + '****' + raw.slice(-4);
  if (raw.length >= 7) return raw.slice(0, 3) + '****' + raw.slice(-4);
  // 没有手机号 → 兜底 "会员 #xxxx"
  return '会员 ' + (m?.userId || '?');
}

// 推荐人脱敏：找不到 mobile 时退回 ID
function maskInviter(uid) {
  const inviter = list.value.find((x) => x.userId === uid);
  return inviter ? maskedMobile(inviter) : `#${uid}`;
}

function avatarStyle(m) {
  const palette = ['#ffd1ba,#ff6b35', '#c9e0ff,#6196f0', '#d3f4d3,#4cb84c', '#ffd0dc,#ee5a8b'];
  const idx = (m.userId || 0) % palette.length;
  return `background: linear-gradient(135deg, ${palette[idx]});`;
}

async function load() {
  try { list.value = await listMembers(1, 50) || []; }
  catch { list.value = []; }
}
onMounted(load);
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  min-height: 100vh; background: $bg-page; padding-bottom: 32rpx;
}
.hero {
  background: linear-gradient(135deg, #FF6B35 0%, #FF9A4A 100%);
  color: #fff;
  padding: 32rpx 28rpx;
  margin: 24rpx; border-radius: 24rpx;
  .hero-l { font-size: 24rpx; opacity: 0.85; }
  .hero-big { font-size: 64rpx; font-weight: 800; margin: 8rpx 0 4rpx; font-variant-numeric: tabular-nums; }
  .hero-sub { font-size: 22rpx; opacity: 0.85; }
}
.member-row {
  display: flex; align-items: center;
  margin: 0 24rpx 16rpx;
  padding: 24rpx;
  background: #fff;
  border-radius: 16rpx;
}
.avatar {
  width: 80rpx; height: 80rpx; border-radius: 50%;
  color: #fff; display: flex; align-items: center; justify-content: center;
  font-weight: 700; font-size: 32rpx; margin-right: 20rpx;
}
.m-info { flex: 1; }
.m-info .nm { font-size: 30rpx; font-weight: 600; }
.m-info .desc { font-size: 22rpx; color: $text-secondary; margin-top: 4rpx; }
.m-stats {
  text-align: right; font-size: 22rpx; color: $text-secondary;
  .star { color: $warning; font-weight: 700; font-size: 26rpx; }
  .bal { margin-top: 4rpx; }
  .promo { color: $brand-primary; font-weight: 600; }
}
.empty { text-align: center; padding: 80rpx 0; color: $text-placeholder; font-size: 24rpx; }
.legal-tip {
  margin: 32rpx 24rpx 0;
  padding: 20rpx 24rpx;
  background: $bg-card;
  border: 2rpx dashed $border-color;
  border-radius: 16rpx;
  font-size: 22rpx;
  color: $text-placeholder;
  line-height: 1.6;
  text-align: center;
}
.bottom-space { height: 80rpx; }
</style>
