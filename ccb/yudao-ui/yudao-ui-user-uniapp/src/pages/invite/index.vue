<template>
  <view class="page">
    <nav-bar title="邀请好友" bg="transparent" txt="#fff" />
    <view class="hero">
      <view class="hero-em">🎁</view>
      <view class="hero-t">邀请好友扫码下单</view>
      <view class="hero-d">你拿推广分 · 朋友首单立减</view>
    </view>
    <view class="card">
      <view class="card-title">我的专属邀请链接</view>
      <view class="link">{{ inviteLink }}</view>
      <view class="copy" @click="onCopy">复制链接</view>
    </view>
    <view class="card">
      <view class="card-title">微信二维码</view>
      <view class="qr">{{ inviteLink ? '🟦 (二维码占位)' : '生成中…' }}</view>
      <view class="qr-tip">长按图片保存 → 微信朋友圈/对话</view>
    </view>
    <view class="card">
      <view class="card-title">我已邀请</view>
      <view class="i-row"><text class="l">总邀请人数</text><text class="hl">{{ stat.invited }} 人</text></view>
      <view class="i-row"><text class="l">他们累计为我贡献</text><text class="hl">¥{{ stat.contribution }}</text></view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useUserStore } from '@/store/user.js';
const user = useUserStore();
const stat = ref({ invited: 0, contribution: '0.00' });
const inviteLink = computed(() => {
  const base = typeof location !== 'undefined' ? location.origin : 'https://ke.doupaidoudian.com';
  return `${base}/u/#/pages/shop/home?inviter=${user.userId || ''}`;
});
function onCopy() {
  uni.setClipboardData({ data: inviteLink.value, success: () => uni.showToast({ title: '已复制', icon: 'success' }) });
}
onMounted(() => { /* TODO: load invite stats */ });
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
.qr { height: 160px; display: flex; align-items: center; justify-content: center; font-size: 16px; color: $t4; background: $bg-2; border-radius: $r-sm; }
.qr-tip { margin-top: 8px; font-size: 11px; color: $t4; text-align: center; }
.i-row { display: flex; justify-content: space-between; padding: 8px 0; font-size: 13px; color: $t2; }
.hl { color: $o; font-weight: 800; }
</style>
