<template>
  <view class="page">
    <nav-bar title="邀请好友" bg="transparent" txt="#fff" />
    <view class="hero">
      <view class="hero-em">🎁</view>
      <view class="hero-t">邀请好友扫码下单</view>
      <view class="hero-d">你拿推广积分 · 朋友首单立减</view>
    </view>
    <view class="card">
      <view class="card-title">我的专属邀请链接</view>
      <view class="link">{{ inviteLink }}</view>
      <view class="copy" @click="onCopy">复制链接</view>
    </view>
    <view class="card">
      <view class="card-title">我的推荐人</view>
      <view v-if="parent" class="parent">
        <view class="p-ava">{{ parent.nickname?.[0] || '上' }}</view>
        <view class="p-body">
          <view class="p-name">{{ parent.nickname || parent.phoneMask || '上级' }}</view>
          <view class="p-d">ID: {{ parent.userId }}</view>
        </view>
      </view>
      <view v-else class="empty-inline">暂未绑定</view>
    </view>
    <view class="card">
      <view class="card-title">我已邀请</view>
      <view class="i-row"><text class="l">总邀请人数</text><text class="hl">{{ childrenCount }} 人</text></view>
      <view class="i-row"><text class="l">我的累计推广积分</text><text class="hl">¥{{ totalEarn }}</text></view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useUserStore } from '@/store/user.js';
import { getReferralParent, getMyChildrenCount, getAccount } from '@/api/promo.js';
import { fen2yuan } from '@/utils/format.js';

const user = useUserStore();
const parent = ref(null);
const childrenCount = ref(0);
const totalEarn = ref('0.00');

const inviteLink = computed(() => {
  const base = typeof location !== 'undefined' ? location.origin : 'https://m.doupaidoudian.com';
  // 落地到首页 + inviter 参数；用户分享时 tenantId 由具体店决定
  return `${base}/#/pages/index/index?inviter=${user.userId || ''}`;
});

function onCopy() {
  uni.setClipboardData({ data: inviteLink.value, success: () => uni.showToast({ title: '已复制', icon: 'success' }) });
}

onMounted(async () => {
  try { parent.value = await getReferralParent(user.tenantId); } catch {}
  try { childrenCount.value = (await getMyChildrenCount(user.tenantId)) || 0; } catch {}
  try {
    const acct = await getAccount();
    totalEarn.value = fen2yuan(acct?.promoPointBalance || 0, false);
  } catch {}
});
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background: $bg-2; padding-bottom: 30px; }
.hero { padding: 24px 14px; background: linear-gradient(135deg, #18130E, #2A1A0F); color: #fff; text-align: center; border-bottom-left-radius: 24px; border-bottom-right-radius: 24px; }
.hero-em { font-size: 56px; }
.hero-t { font-size: 18px; font-weight: 900; margin-top: 6px; background: linear-gradient(135deg, #fff, $gold-l); -webkit-background-clip: text; background-clip: text; color: transparent; }
.hero-d { font-size: 12px; opacity: .7; margin-top: 4px; }
.card { background: #fff; margin: 10px 14px; border-radius: $r-md; padding: 14px; box-shadow: $sh-1; }
.card-title { font-size: 14px; font-weight: 800; color: $t1; margin-bottom: 10px; }
.link { padding: 10px; background: $bg-2; border-radius: $r-sm; font-size: 12px; color: $t3; word-break: break-all; }
.copy { margin-top: 10px; padding: 10px; background: linear-gradient(135deg, $o, $o-d); color: #fff; text-align: center; border-radius: $r-pill; font-weight: 700; font-size: 13px; box-shadow: $sh-warm; }
.parent { display: flex; gap: 10px; align-items: center; }
.p-ava { width: 40px; height: 40px; border-radius: 50%; background: linear-gradient(135deg, $o, $gold); color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 800; }
.p-body { flex: 1; }
.p-name { font-size: 14px; font-weight: 700; color: $t1; }
.p-d { font-size: 11px; color: $t3; margin-top: 2px; }
.empty-inline { font-size: 12px; color: $t4; padding: 8px 0; }
.i-row { display: flex; justify-content: space-between; padding: 8px 0; font-size: 13px; color: $t2; }
.hl { color: $o; font-weight: 800; }
</style>
