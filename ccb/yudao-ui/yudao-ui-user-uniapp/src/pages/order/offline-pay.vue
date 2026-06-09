<template>
  <view class="page">
    <view class="topbar">
      <text class="back" @click="goBack">‹</text>
      <text class="title">线下转账付款</text>
      <text class="right" @click="goOrders">订单</text>
    </view>

    <view v-if="loading" class="loading">加载中…</view>

    <template v-else-if="!found">
      <view class="empty">
        <text class="empty-em">🔍</text>
        <text class="empty-t">未找到该订单的线下收款信息</text>
        <view class="btn ghost" @click="goOrders">返回我的订单</view>
      </view>
    </template>

    <template v-else>
      <!-- 金额 -->
      <view class="amt-card">
        <text class="amt-shop">{{ info.shopName || '商家收款' }}</text>
        <text class="amt-label">应付金额</text>
        <text class="amt-val">¥{{ yuan(info.payPrice) }}</text>
      </view>

      <!-- 已确认 -->
      <view v-if="info.status === 2" class="result ok">
        <text class="result-em">✅</text>
        <text class="result-t">商家已确认收款</text>
        <text class="result-s">订单进入后续处理，可在「我的订单」查看</text>
        <view class="btn primary" @click="goOrders">查看我的订单</view>
      </view>

      <!-- 待确认 -->
      <view v-else-if="info.status === 1" class="result wait">
        <text class="result-em">⏳</text>
        <text class="result-t">凭证已提交，等待商家确认</text>
        <text class="result-s">商家核对到账后会确认收款。如长时间未确认可联系商家{{ info.merchantMobile ? '：' + info.merchantMobile : '' }}</text>
        <image v-if="info.proofUrl" :src="info.proofUrl" mode="widthFix" class="my-proof" @click="preview(info.proofUrl)" />
        <view class="btn ghost" @click="reupload">重新上传凭证</view>
      </view>

      <!-- 待付款 / 被驳回 -->
      <template v-else>
        <view v-if="info.status === 3" class="reject-tip">
          <text class="reject-em">⚠️</text>
          <text>商家驳回了上次的凭证{{ info.rejectReason ? '：' + info.rejectReason : '' }}，请重新付款并上传</text>
        </view>

        <!-- 收款码 -->
        <view class="qr-card">
          <view class="qr-tabs" v-if="hasWechat && hasAlipay">
            <view class="qr-tab" :class="{ on: channel === 'wechat' }" @click="channel = 'wechat'">微信</view>
            <view class="qr-tab" :class="{ on: channel === 'alipay' }" @click="channel = 'alipay'">支付宝</view>
          </view>
          <view class="qr-box" v-if="currentQr">
            <image :src="currentQr" mode="aspectFit" class="qr-img" @click="preview(currentQr)" />
            <text class="qr-tip">用{{ channel === 'wechat' ? '微信' : '支付宝' }}扫码付 ¥{{ yuan(info.payPrice) }} · 点图可放大/长按保存</text>
          </view>
          <view class="qr-none" v-else>
            <text>商家暂未设置收款码</text>
            <text v-if="info.merchantMobile" class="qr-none-sub">可联系商家：{{ info.merchantMobile }}</text>
          </view>
        </view>

        <!-- 上传凭证 -->
        <view class="proof-card">
          <text class="proof-title">上传付款凭证</text>
          <view class="proof-up" @click="pickProof">
            <image v-if="proofUrl" :src="proofUrl" mode="aspectFit" class="proof-img" />
            <view v-else class="proof-empty">
              <text class="proof-plus">+</text>
              <text class="proof-hint">上传转账成功截图</text>
            </view>
          </view>
          <input class="remark" v-model="remark" placeholder="备注（选填，如转账后四位）" maxlength="60" />
        </view>

        <view class="footer safe-bottom">
          <view class="btn primary" :class="{ disabled: submitting || !proofUrl }" @click="submit">
            {{ submitting ? '提交中…' : '提交凭证，通知商家确认' }}
          </view>
        </view>
      </template>
    </template>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { getOfflinePayInfo, submitOfflineProof } from '@/api/order.js';
import { chooseAndUploadImage } from '@/api/upload.js';

const loading = ref(true);
const found = ref(false);
const info = ref({});
const orderId = ref('');
const channel = ref('wechat');
const proofUrl = ref('');
const remark = ref('');
const submitting = ref(false);

const hasWechat = computed(() => !!info.value.wechatPayQrUrl);
const hasAlipay = computed(() => !!info.value.alipayPayQrUrl);
const currentQr = computed(() => (channel.value === 'wechat' ? info.value.wechatPayQrUrl : info.value.alipayPayQrUrl));

function yuan(fen) {
  return ((Number(fen) || 0) / 100).toFixed(2);
}

async function load() {
  loading.value = true;
  try {
    const data = await getOfflinePayInfo(orderId.value);
    if (!data || !data.found) {
      found.value = false;
    } else {
      found.value = true;
      info.value = data;
      // 默认选有码的渠道
      if (data.wechatPayQrUrl) channel.value = 'wechat';
      else if (data.alipayPayQrUrl) channel.value = 'alipay';
      // 回填已上传的备注/凭证（被驳回时方便参考）
      if (data.buyerRemark) remark.value = data.buyerRemark;
    }
  } catch {
    found.value = false;
  } finally {
    loading.value = false;
  }
}

async function pickProof() {
  uni.showLoading({ title: '上传中…' });
  try {
    const url = await chooseAndUploadImage();
    proofUrl.value = url;
    uni.hideLoading();
    uni.showToast({ title: '已上传', icon: 'success' });
  } catch (e) {
    uni.hideLoading();
    if (e?.errMsg && /cancel/i.test(e.errMsg)) return;
    uni.showToast({ title: '上传失败：' + (e?.message || '请重试'), icon: 'none' });
  }
}

function preview(url) {
  if (url) uni.previewImage({ urls: [url] });
}

function reupload() {
  // 待确认状态下允许覆盖重传：切回上传态
  info.value = { ...info.value, status: 0 };
  proofUrl.value = '';
}

async function submit() {
  if (submitting.value) return;
  if (!proofUrl.value) {
    uni.showToast({ title: '请先上传付款凭证', icon: 'none' });
    return;
  }
  submitting.value = true;
  try {
    await submitOfflineProof({
      orderId: Number(orderId.value),
      proofUrl: proofUrl.value,
      payChannel: channel.value,
      remark: remark.value || undefined,
    });
    uni.showToast({ title: '凭证已提交', icon: 'success' });
    await load();
  } catch (e) {
    uni.showToast({ title: e?.message || '提交失败', icon: 'none' });
  } finally {
    submitting.value = false;
  }
}

function goBack() {
  const ps = getCurrentPages();
  if (ps.length > 1) uni.navigateBack();
  else uni.reLaunch({ url: '/pages/order/list' });
}
function goOrders() {
  uni.reLaunch({ url: '/pages/order/list' });
}

onLoad((q) => {
  orderId.value = q.orderId || q.id || '';
  load();
});
</script>

<style scoped>
.page { min-height: 100vh; background: #FAFAF7; padding-bottom: 160rpx; }

.topbar {
  display: flex; align-items: center; padding: 24rpx 28rpx;
  background: #fff; border-bottom: 1rpx solid #F0EDE8;
  position: sticky; top: 0; z-index: 10;
}
.topbar .back { font-size: 44rpx; color: #1A1A1A; padding: 0 16rpx; line-height: 1; }
.topbar .title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 700; color: #1A1A1A; }
.topbar .right { font-size: 26rpx; color: #F0541E; padding: 0 16rpx; font-weight: 600; }

.loading { padding: 120rpx 0; text-align: center; color: #B0AAA2; font-size: 28rpx; }

.empty { padding: 140rpx 48rpx; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 20rpx; }
.empty-em { font-size: 72rpx; }
.empty-t { font-size: 28rpx; color: #8A847C; }

/* 金额卡 */
.amt-card {
  margin: 28rpx; padding: 44rpx 28rpx;
  background: linear-gradient(135deg, #FF6B35, #F0541E);
  border-radius: 24rpx; color: #fff;
  display: flex; flex-direction: column; align-items: center; gap: 10rpx;
  box-shadow: 0 8rpx 24rpx rgba(240,84,30,.25);
}
.amt-shop { font-size: 28rpx; font-weight: 600; opacity: .95; }
.amt-label { font-size: 24rpx; opacity: .85; margin-top: 8rpx; }
.amt-val { font-size: 72rpx; font-weight: 800; line-height: 1.1; }

/* 结果态 */
.result {
  margin: 28rpx; padding: 56rpx 32rpx; background: #fff;
  border-radius: 24rpx; text-align: center;
  display: flex; flex-direction: column; align-items: center; gap: 16rpx;
}
.result-em { font-size: 80rpx; }
.result-t { font-size: 32rpx; font-weight: 700; color: #1A1A1A; }
.result-s { font-size: 24rpx; color: #8A847C; line-height: 1.6; padding: 0 20rpx; }
.result.ok .result-t { color: #16A34A; }
.my-proof { width: 60%; border-radius: 16rpx; margin-top: 12rpx; }

.reject-tip {
  margin: 28rpx 28rpx 0; padding: 22rpx 24rpx;
  background: #FFF1F0; border: 1rpx solid #FFC9C4; border-radius: 16rpx;
  display: flex; gap: 12rpx; align-items: flex-start;
  font-size: 24rpx; color: #D03A2C; line-height: 1.5;
}
.reject-em { font-size: 30rpx; }

/* 收款码 */
.qr-card {
  margin: 28rpx; padding: 28rpx; background: #fff; border-radius: 24rpx;
}
.qr-tabs { display: flex; gap: 16rpx; margin-bottom: 24rpx; }
.qr-tab {
  flex: 1; text-align: center; padding: 18rpx 0;
  border-radius: 14rpx; background: #F5F3F0; color: #6B655D;
  font-size: 28rpx; font-weight: 700;
}
.qr-tab.on { background: #FFEDE4; color: #F0541E; }
.qr-box { display: flex; flex-direction: column; align-items: center; gap: 16rpx; }
.qr-img { width: 440rpx; height: 440rpx; border-radius: 16rpx; background: #F5F3F0; }
.qr-tip { font-size: 24rpx; color: #8A847C; text-align: center; }
.qr-none { padding: 60rpx 0; text-align: center; color: #B0AAA2; font-size: 28rpx; display: flex; flex-direction: column; gap: 10rpx; }
.qr-none-sub { font-size: 24rpx; color: #8A847C; }

/* 上传凭证 */
.proof-card { margin: 28rpx; padding: 28rpx; background: #fff; border-radius: 24rpx; }
.proof-title { font-size: 28rpx; font-weight: 700; color: #1A1A1A; }
.proof-up {
  margin-top: 20rpx; width: 280rpx; height: 280rpx;
  border-radius: 16rpx; background: #F5F3F0; border: 2rpx dashed #DAD5CE;
  display: flex; align-items: center; justify-content: center; overflow: hidden;
}
.proof-img { width: 100%; height: 100%; }
.proof-empty { display: flex; flex-direction: column; align-items: center; gap: 10rpx; color: #B0AAA2; }
.proof-plus { font-size: 64rpx; font-weight: 300; line-height: 1; }
.proof-hint { font-size: 24rpx; }
.remark {
  margin-top: 24rpx; padding: 22rpx 24rpx;
  background: #F5F3F0; border-radius: 14rpx;
  font-size: 28rpx; color: #1A1A1A;
}

/* footer */
.footer {
  position: fixed; left: 0; right: 0; bottom: 0;
  padding: 20rpx 28rpx calc(env(safe-area-inset-bottom) + 20rpx);
  background: #fff; border-top: 1rpx solid #F0EDE8;
}
.btn {
  text-align: center; border-radius: 999rpx; padding: 26rpx 0;
  font-size: 30rpx; font-weight: 700;
}
.btn.primary { background: linear-gradient(135deg, #FF6B35, #F0541E); color: #fff; }
.btn.primary.disabled { opacity: .5; }
.btn.ghost { background: #F5F3F0; color: #6B655D; margin-top: 20rpx; }
</style>
