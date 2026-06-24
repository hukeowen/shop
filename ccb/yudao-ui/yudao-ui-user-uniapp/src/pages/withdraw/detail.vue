<template>
  <view class="page">
    <nav-bar title="兑换详情" />

    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!r" icon="🧾" title="未找到该申请" desc="可能已被删除" />

    <view v-else class="wrap">
      <!-- 店铺 + 金额 头 -->
      <view class="hero">
        <view class="shop-row">
          <view class="logo">{{ shopFirst }}</view>
          <text class="shop-name">{{ shopName }}</text>
        </view>
        <view class="amt-box">
          <text class="amt">{{ fen2yuan(r.amount, false) }}</text>
          <text class="amt-unit"> 积分</text>
        </view>
        <text class="amt-cap">申请兑换推广积分（1 积分 = ¥1）</text>
        <view class="status-pill" :class="`st-${r.status}`">{{ statusText(r.status) }}</view>
      </view>

      <!-- 进度 -->
      <view class="card">
        <view class="card-h">办理进度</view>
        <view class="steps">
          <view class="step done">
            <text class="dot">✓</text><text class="t">已申请</text>
          </view>
          <view class="bar" :class="{ done: reached(2) }"></view>
          <view class="step" :class="{ done: reached(2), rej: r.status === 'REJECTED' }">
            <text class="dot">{{ r.status === 'REJECTED' ? '✕' : (reached(2) ? '✓' : '2') }}</text>
            <text class="t">{{ r.status === 'REJECTED' ? '已驳回' : '商户审批' }}</text>
          </view>
          <view class="bar" :class="{ done: reached(3) }"></view>
          <view class="step" :class="{ done: reached(3) }">
            <text class="dot">{{ reached(3) ? '✓' : '3' }}</text><text class="t">商户打款</text>
          </view>
          <view class="bar" :class="{ done: reached(4) }"></view>
          <view class="step" :class="{ done: reached(4) }">
            <text class="dot">{{ reached(4) ? '✓' : '4' }}</text><text class="t">我已收到</text>
          </view>
        </view>
      </view>

      <!-- 信息 -->
      <view class="card">
        <view class="info">
          <text class="k">申请编号</text><text class="v">#{{ r.id }}</text>
        </view>
        <view class="info">
          <text class="k">申请时间</text><text class="v">{{ fmtTime(r.applyAt) }}</text>
        </view>
        <view v-if="r.processedAt" class="info">
          <text class="k">处理时间</text><text class="v">{{ fmtTime(r.processedAt) }}</text>
        </view>
        <view v-if="r.remark || r.processorRemark" class="info">
          <text class="k">商户备注</text><text class="v small">{{ r.remark || r.processorRemark }}</text>
        </view>
      </view>

      <!-- 支付凭证 -->
      <view v-if="r.payProofUrl" class="card">
        <view class="card-h">商户支付凭证</view>
        <image :src="r.payProofUrl" mode="widthFix" class="proof-img" @click="previewProof(r.payProofUrl)" />
        <text class="proof-tip">点击图片可放大查看</text>
      </view>

      <!-- 状态行动 -->
      <view v-if="r.status === 'PAID'" class="cta-card">
        <text class="cta-hint">商户已线下转账给您，请在确认收到款项后点击下方按钮。</text>
        <view class="cta" @click="onConfirm">我已确认收到</view>
      </view>
      <view v-else-if="r.status === 'COMPLETED'" class="result done">✓ 已完成（您已确认收款）</view>
      <view v-else-if="r.status === 'REJECTED'" class="result rej">申请被驳回 · 推广积分已退回到您的账户</view>
      <view v-else class="result wait">等待商户处理中…</view>

      <view class="footer-tip">
        平台仅提供技术信息撮合，兑换形式与额度由<text class="b">商户独立审批</text>，您与商户共同确认完成；
        <text class="b">平台不构成兑换承诺、不承担担保责任</text>。
      </view>
    </view>
    <view class="bottom-pad"></view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { request } from '@/utils/request.js';
import { listMyPromoWithdraws } from '@/api/promo.js';
import { getShopInfo } from '@/api/shop.js';
import { fen2yuan, fmtTime } from '@/utils/format.js';

const loading = ref(true);
const id = ref(null);
const r = ref(null);
const shopName = ref('店铺');
const shopFirst = computed(() => (shopName.value || '店').charAt(0));

function statusText(s) {
  return {
    PENDING: '审批中',
    APPROVED: '审批通过 · 等待商户打款',
    PAID: '商户已打款 · 待您确认',
    COMPLETED: '已完成',
    REJECTED: '已驳回',
  }[s] || s;
}
function reached(step) {
  const s = r.value?.status;
  if (s === 'PENDING') return step <= 1;
  if (s === 'REJECTED') return step <= 2;
  if (s === 'APPROVED') return step <= 2;
  if (s === 'PAID') return step <= 3;
  if (s === 'COMPLETED') return step <= 4;
  return step <= 1;
}

async function load() {
  loading.value = true;
  try {
    const list = await listMyPromoWithdraws();
    const arr = Array.isArray(list) ? list : (list?.list || []);
    r.value = arr.find((x) => String(x.id) === String(id.value)) || null;
    if (r.value?.tenantId) {
      try { const info = await getShopInfo({ tenantId: r.value.tenantId }); if (info?.shopName) shopName.value = info.shopName; } catch {}
    }
  } catch {
    r.value = null;
  } finally {
    loading.value = false;
  }
}

function previewProof(url) { if (url) uni.previewImage({ urls: [url], current: url }); }

function onConfirm() {
  uni.showModal({
    title: '确认已收到打款',
    content: `您确认已收到商户对应 ${fen2yuan(r.value.amount, false)} 积分的线下兑换吗？\n\n确认后本次申请完成，无法撤销。`,
    success: async (res) => {
      if (!res.confirm) return;
      try {
        await request({ url: `/app-api/merchant/mini/withdraw/confirm-received?id=${r.value.id}`, method: 'POST' });
        uni.showToast({ title: '感谢确认', icon: 'success' });
        load();
      } catch (e) {
        uni.showToast({ title: e?.message || '确认失败', icon: 'none' });
      }
    },
  });
}

onLoad((q) => {
  id.value = q && q.id ? q.id : null;
  load();
});
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.page { min-height: 100vh; background: $bg-2; padding-bottom: 30px; }
.loading { padding: 40px; text-align: center; color: $t4; }
.wrap { padding: 0 0 10px; }

/* hero */
.hero {
  margin: 12px 14px 0; padding: 20px 18px;
  background: linear-gradient(150deg, #1B1208 0%, #2C1B0E 60%, #3A2410 100%);
  border-radius: $r-lg; color: #fff; position: relative; overflow: hidden;
}
.shop-row { display: flex; align-items: center; gap: 9px; }
.logo {
  width: 28px; height: 28px; border-radius: 8px;
  background: linear-gradient(135deg, #FFE8C9, $gold); color: #5a3a10;
  display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: 800;
}
.shop-name { font-size: 14px; font-weight: 700; color: rgba(255,255,255,.92); }
.amt-box { margin-top: 14px; }
.amt { font-size: 40px; font-weight: 900; letter-spacing: -1px; color: $gold-l; font-variant-numeric: tabular-nums; }
.amt-unit { font-size: 14px; font-weight: 700; color: $gold-l; }
.amt-cap { display: block; font-size: 11px; color: rgba(255,255,255,.5); margin-top: 2px; }
.status-pill {
  display: inline-block; margin-top: 14px;
  font-size: 12px; font-weight: 800; padding: 4px 12px; border-radius: 999px;
  background: rgba(255,255,255,.14); color: #fff;
}
.status-pill.st-COMPLETED { background: rgba(16,185,129,.25); color: #6EE7B7; }
.status-pill.st-REJECTED  { background: rgba(220,38,38,.25);  color: #FCA5A5; }
.status-pill.st-PAID      { background: rgba(59,130,246,.25); color: #93C5FD; }

/* card */
.card { margin: 12px 14px 0; background: $card; border-radius: $r-lg; padding: 16px; box-shadow: $sh-1; }
.card-h { font-size: 13px; font-weight: 800; color: $t1; margin-bottom: 14px; }

/* steps */
.steps { display: flex; align-items: center; }
.step { display: flex; flex-direction: column; align-items: center; gap: 5px; flex: none; }
.step .dot {
  width: 24px; height: 24px; border-radius: 50%;
  background: $bg-2; color: $t4; font-size: 12px; font-weight: 800;
  display: flex; align-items: center; justify-content: center;
}
.step.done .dot { background: $o; color: #fff; }
.step.rej .dot { background: #DC2626; color: #fff; }
.step .t { font-size: 10px; color: $t4; white-space: nowrap; }
.step.done .t { color: $t1; font-weight: 700; }
.bar { flex: 1; height: 2px; background: $line; margin: 0 -2px 16px; }
.bar.done { background: $o; }

/* info */
.info { display: flex; justify-content: space-between; align-items: center; padding: 7px 0; font-size: 13px; }
.info .k { color: $t3; flex: none; }
.info .v { color: $t1; font-weight: 600; text-align: right; margin-left: 14px; }
.info .v.small { font-size: 12px; color: $t2; font-weight: 500; }

/* proof */
.proof-img { width: 100%; border-radius: 10px; border: 1px solid $line; background: $bg-2; }
.proof-tip { display: block; font-size: 11px; color: $t4; margin-top: 8px; text-align: center; }

/* cta / result */
.cta-card { margin: 14px 14px 0; padding: 14px 16px; background: $card; border-radius: $r-lg; box-shadow: $sh-1; }
.cta-hint { display: block; font-size: 12px; color: $t3; line-height: 1.5; margin-bottom: 10px; }
.cta {
  padding: 12px 0; background: linear-gradient(135deg, $o, $o-d); color: #fff;
  border-radius: 999px; font-size: 15px; font-weight: 800; text-align: center; box-shadow: $sh-warm;
}
.result { margin: 14px 14px 0; padding: 12px; border-radius: $r-md; font-size: 12.5px; font-weight: 700; text-align: center; }
.result.done { background: rgba(16,185,129,.08); color: #10B981; }
.result.rej { background: rgba(220,38,38,.06); color: #DC2626; }
.result.wait { background: $bg-2; color: $t3; }

.footer-tip { margin: 16px 14px 0; padding: 12px 16px; background: $bg-2; border-radius: $r-md; border: 1px dashed $line; font-size: 11px; color: $t4; line-height: 1.7; }
.footer-tip .b { color: $t2; font-weight: 700; }
.bottom-pad { height: 30px; }
</style>
