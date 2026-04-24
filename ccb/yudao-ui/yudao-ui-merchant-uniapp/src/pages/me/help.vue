<template>
  <view class="page">
    <view class="card section">
      <view class="section-title">常见问题</view>
      <view v-for="item in faqs" :key="item.q" class="faq-item" @click="item.open = !item.open">
        <view class="faq-q">
          <text class="q-text">{{ item.q }}</text>
          <text class="arrow">{{ item.open ? '∧' : '∨' }}</text>
        </view>
        <view v-if="item.open" class="faq-a">{{ item.a }}</view>
      </view>
    </view>

    <view class="card section">
      <view class="section-title">联系我们</view>
      <view class="contact-row">
        <text class="contact-label">客服邮箱</text>
        <text class="contact-val" @click="copyEmail">support@tanzxiaer.com</text>
      </view>
      <view class="contact-row">
        <text class="contact-label">服务时间</text>
        <text class="contact-val">工作日 09:00 - 18:00</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { reactive } from 'vue';

const faqs = reactive([
  {
    q: '如何开通在线支付？',
    a: '在"我的"→"在线支付开通"填写通联支付商户号和密钥，提交后等待平台审核，审核通过后自动开通。',
    open: false,
  },
  {
    q: '用户佣金提现如何审核？',
    a: '在"我的"→"用户提现审核"中查看待审提现申请，点击"通过"即可，系统自动完成转账。',
    open: false,
  },
  {
    q: '积分和返佣比例如何设置？',
    a: '在"我的"→"返佣与积分设置"中配置一二级返佣比例、积分兑换比例等参数，保存后立即生效。',
    open: false,
  },
  {
    q: 'AI 视频配额不足怎么办？',
    a: '在"我的"→"AI 视频配额"页面购买加量包，支付后配额立即到账，永久有效不过期。',
    open: false,
  },
  {
    q: '订单的到店付款如何确认收款？',
    a: '在订单列表找到对应订单，点击"已收款"按钮确认，系统自动触发返佣和积分发放。',
    open: false,
  },
]);

function copyEmail() {
  uni.setClipboardData({
    data: 'support@tanzxiaer.com',
    success: () => uni.showToast({ title: '邮箱已复制', icon: 'none' }),
  });
}
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  min-height: 100vh;
  padding: 24rpx;
}

.card {
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 28rpx 32rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.section-title {
  font-size: 28rpx;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 20rpx;
}

.faq-item {
  padding: 20rpx 0;
  border-bottom: 1rpx solid $border-color;

  &:last-child { border-bottom: none; }
}

.faq-q {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.q-text {
  font-size: 28rpx;
  color: $text-primary;
  flex: 1;
  padding-right: 16rpx;
}

.arrow {
  font-size: 24rpx;
  color: $text-placeholder;
}

.faq-a {
  margin-top: 16rpx;
  font-size: 26rpx;
  color: $text-secondary;
  line-height: 1.7;
  background: #f9f9f9;
  padding: 16rpx 20rpx;
  border-radius: $radius-md;
}

.contact-row {
  display: flex;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 1rpx solid $border-color;

  &:last-child { border-bottom: none; }
}

.contact-label {
  font-size: 28rpx;
  color: $text-secondary;
  width: 160rpx;
}

.contact-val {
  font-size: 28rpx;
  color: $brand-primary;
}
</style>
