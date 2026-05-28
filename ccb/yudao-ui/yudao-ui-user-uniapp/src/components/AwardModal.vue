<template>
  <view v-if="visible" class="aw-mask" @click="onClose">
    <view class="aw-card" :class="{ 'aw-card-in': visible }" @click.stop>
      <view class="aw-confetti">🎉</view>
      <view class="aw-title">恭喜！让利到账</view>
      <view class="aw-amount">+ ¥{{ (amount/100).toFixed(2) }}</view>
      <view class="aw-source">{{ source || '推广积分' }}</view>
      <view v-if="hint" class="aw-hint">{{ hint }}</view>
      <view class="aw-actions">
        <view class="aw-btn aw-btn-ghost" @click="onClose">知道了</view>
        <view class="aw-btn aw-btn-primary" @click="onViewWallet">去钱包</view>
      </view>
    </view>
  </view>
</template>

<script setup>
const props = defineProps({
  visible: { type: Boolean, default: false },
  amount:  { type: Number, default: 0 }, // 分
  source:  { type: String, default: '' },
  hint:    { type: String, default: '' },
});
const emit = defineEmits(['close', 'view']);

function onClose() { emit('close'); }
function onViewWallet() {
  emit('view');
  uni.navigateTo({ url: '/pages/wallet/index' });
}
</script>

<style lang="scss" scoped>
.aw-mask {
  position: fixed; inset: 0;
  background: rgba(0,0,0,.55);
  display: flex; align-items: center; justify-content: center;
  z-index: 99;
  backdrop-filter: blur(6px);
}
.aw-card {
  width: 80%; max-width: 320px;
  background: linear-gradient(180deg, #FFFAF3 0%, #FFFFFF 100%);
  border-radius: 24px;
  padding: 28px 24px 20px;
  text-align: center;
  box-shadow: 0 30px 80px rgba(0,0,0,.4);
  transform: scale(.85);
  opacity: 0;
  animation: aw-in .35s cubic-bezier(.2,.8,.2,1) forwards;
}
@keyframes aw-in {
  to { transform: scale(1); opacity: 1; }
}
.aw-confetti { font-size: 48px; margin-bottom: 4px; }
.aw-title { font-size: 18px; font-weight: 900; color: #1B1F23; }
.aw-amount {
  margin-top: 14px;
  font-size: 38px; font-weight: 900;
  background: linear-gradient(135deg, #FF6B35, #D4920A);
  -webkit-background-clip: text; background-clip: text; color: transparent;
}
.aw-source { margin-top: 6px; font-size: 13px; color: #5A6577; }
.aw-hint { margin-top: 10px; font-size: 12px; color: #94A3B8; }
.aw-actions {
  margin-top: 24px;
  display: flex; gap: 10px;
}
.aw-btn {
  flex: 1; padding: 12px 0;
  border-radius: 999px;
  font-size: 14px; font-weight: 700;
  text-align: center;
}
.aw-btn-ghost   { background: #F6F7F9; color: #5A6577; }
.aw-btn-primary {
  background: linear-gradient(135deg, #FF6B35, #E25316);
  color: #fff;
  box-shadow: 0 8px 24px rgba(255,107,53,.4);
}
</style>
