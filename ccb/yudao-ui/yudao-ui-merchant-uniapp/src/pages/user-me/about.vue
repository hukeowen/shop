<template>
  <view class="page">
    <view class="topbar safe-top">
      <text class="back" @click="goBack">‹</text>
      <text class="title">关于摊小二</text>
      <view style="width:60rpx"></view>
    </view>

    <view class="hero">
      <view class="logo">摊</view>
      <view class="brand">摊小二</view>
      <view class="tag">让每一个普通生意 都能开口说话</view>
      <view class="ver">v {{ version }}</view>
    </view>

    <view class="info-card">
      <view class="row" @click="copy(env)">
        <text class="lbl">环境</text>
        <text class="val">{{ env || '生产环境' }}</text>
      </view>
      <view class="row" @click="copy(buildTime)">
        <text class="lbl">构建时间</text>
        <text class="val">{{ buildTime }}</text>
      </view>
      <view class="row">
        <text class="lbl">客户端</text>
        <text class="val">{{ platformText }}</text>
      </view>
    </view>

    <view class="story">
      <view class="t">摊小二是什么</view>
      <view class="p">为夫妻店、街边摊、小工作室设计的<text class="b">"AI 上架 + 朋友推荐分钱"</text>SaaS 系统。</view>
      <view class="p">三句话讲透：</view>
      <view class="li">① 拍一张图 → AI 自动写文案、出短视频，一键发抖音</view>
      <view class="li">② 朋友扫码进店买东西 → 你能拿到推广积分（推 N 反 1）</view>
      <view class="li">③ 推广积分换余额、抵消费、提现到微信，每分钱都看得见</view>
    </view>

    <view class="story">
      <view class="t">为什么我们存在</view>
      <view class="p">大平台越来越贵、流量越来越涌向头部商家。我们想给小生意一个工具：让你不用懂运营、不用花投流费，<text class="b">仅凭老顾客口口相传</text>，也能把生意做起来。</view>
    </view>

    <view class="actions">
      <view class="ac" @click="contactSupport">
        <text class="ac-icon">💬</text>
        <text class="ac-label">联系客服</text>
      </view>
      <view class="ac" @click="goBack">
        <text class="ac-icon">📜</text>
        <text class="ac-label">用户协议</text>
      </view>
      <view class="ac" @click="goBack">
        <text class="ac-icon">🔒</text>
        <text class="ac-label">隐私政策</text>
      </view>
    </view>

    <view class="copyright">
      <text>© 2026 摊小二（Tanxiaer）</text>
      <text class="line">All rights reserved.</text>
    </view>
    <view class="bottom-space"></view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';

const version = ref('1.0.0');
const buildTime = ref('—');
const env = ref('');

const platformText = computed(() => {
  const sys = uni.getSystemInfoSync?.() || {};
  return [sys.platform, sys.brand, sys.model].filter(Boolean).join(' / ');
});

function copy(t) {
  if (!t) return;
  uni.setClipboardData({ data: String(t), success: () => uni.showToast({ title: '已复制', icon: 'none' }) });
}
function contactSupport() {
  uni.showModal({
    title: '联系客服',
    content: '工作时间：09:00 ~ 22:00\n服务热线：400-xxxx-xxxx\n或在「帮助与反馈」提交工单',
    showCancel: false,
  });
}
function goBack() { uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/user-me/index' }) }); }

onMounted(() => {
  // 注入 vite 构建变量（uniapp 环境会注入 import.meta.env）
  try {
    const m = import.meta?.env || {};
    env.value = m.MODE || m.NODE_ENV || '';
    buildTime.value = m.VITE_BUILD_TIME || new Date().toISOString().slice(0, 10);
    version.value = m.VITE_APP_VERSION || version.value;
  } catch {}
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';
.page { min-height: 100vh; background: $bg-page; }
.safe-top { padding-top: calc(env(safe-area-inset-top) + 16rpx); }
.topbar { display: flex; align-items: center; padding: 16rpx 32rpx; background: $bg-card; border-bottom: 1rpx solid $border-color; }
.topbar .back { font-size: 44rpx; color: $text-primary; padding-right: 16rpx; }
.topbar .title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; color: $text-primary; }

.hero { text-align: center; padding: 80rpx 32rpx 56rpx; }
.hero .logo {
  width: 160rpx; height: 160rpx; line-height: 160rpx;
  margin: 0 auto;
  background: linear-gradient(135deg, #ff9a4a, #ff6b35);
  border-radius: 36rpx;
  color: #fff; font-size: 80rpx; font-weight: 800;
  box-shadow: 0 12rpx 36rpx rgba(255,107,53,.30);
}
.hero .brand { margin-top: 24rpx; font-size: 44rpx; font-weight: 800; color: $text-primary; letter-spacing: 4rpx; }
.hero .tag { margin-top: 12rpx; font-size: 24rpx; color: $text-secondary; }
.hero .ver { margin-top: 12rpx; font-size: 22rpx; color: $text-placeholder; }

.info-card { margin: 0 32rpx; background: $bg-card; border-radius: $radius-lg; box-shadow: 0 4rpx 12rpx rgba(15,23,42,.03); }
.info-card .row { display: flex; align-items: center; padding: 28rpx 32rpx; border-bottom: 1rpx solid $border-color; }
.info-card .row:last-child { border-bottom: 0; }
.info-card .row .lbl { font-size: 26rpx; color: $text-secondary; }
.info-card .row .val { margin-left: auto; font-size: 26rpx; color: $text-primary; }

.story { margin: 32rpx 32rpx 0; padding: 28rpx 32rpx; background: $bg-card; border-radius: $radius-lg; box-shadow: 0 4rpx 12rpx rgba(15,23,42,.03); }
.story .t { font-size: 30rpx; font-weight: 700; color: $text-primary; margin-bottom: 16rpx; }
.story .p { font-size: 26rpx; color: $text-secondary; line-height: 1.7; }
.story .p .b { color: $brand-primary; font-weight: 700; }
.story .li { font-size: 26rpx; color: $text-primary; margin-top: 12rpx; line-height: 1.6; }

.actions { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 16rpx; margin: 32rpx; }
.actions .ac { background: $bg-card; border-radius: $radius-md; padding: 28rpx 16rpx; text-align: center; box-shadow: 0 2rpx 8rpx rgba(15,23,42,.03); }
.actions .ac .ac-icon { font-size: 48rpx; display: block; }
.actions .ac .ac-label { font-size: 24rpx; color: $text-secondary; margin-top: 8rpx; display: block; }

.copyright { text-align: center; margin: 40rpx 32rpx 24rpx; font-size: 22rpx; color: $text-placeholder; }
.copyright .line { display: block; margin-top: 4rpx; }

.bottom-space { height: 60rpx; }
</style>
