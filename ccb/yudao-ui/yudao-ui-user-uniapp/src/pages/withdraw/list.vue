<template>
  <view class="page">
    <nav-bar title="我的提现记录" />

    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!records.length" title="暂无提现记录" desc="去申请提现把推广奖励换成现金" />

    <view v-else class="list">
      <view v-for="r in records" :key="r.id" class="card">
        <view class="row">
          <text class="lbl">申请编号</text>
          <text class="val">#{{ r.id }}</text>
        </view>
        <view class="row big">
          <text class="lbl">提现金额</text>
          <text class="val amt">¥{{ fen2yuan(r.amount, false) }}</text>
        </view>
        <view class="row">
          <text class="lbl">申请时间</text>
          <text class="val">{{ fmtTime(r.applyAt) }}</text>
        </view>
        <view class="row">
          <text class="lbl">当前状态</text>
          <text class="val status" :class="`st-${r.status}`">{{ statusText(r.status) }}</text>
        </view>
        <view v-if="r.remark" class="row">
          <text class="lbl">备注</text>
          <text class="val small">{{ r.remark }}</text>
        </view>

        <!-- 状态进度 -->
        <view class="steps">
          <view class="step done">
            <text class="dot">✓</text>
            <text class="t">1. 已申请</text>
          </view>
          <view class="bar" :class="{ done: stepReached(r.status, 2) }"></view>
          <view class="step" :class="{ done: stepReached(r.status, 2) }">
            <text class="dot">{{ stepReached(r.status, 2) ? '✓' : '2' }}</text>
            <text class="t">{{ r.status === 'REJECTED' ? '已驳回' : '商户审批' }}</text>
          </view>
          <view class="bar" :class="{ done: stepReached(r.status, 3) }"></view>
          <view class="step" :class="{ done: stepReached(r.status, 3) }">
            <text class="dot">{{ stepReached(r.status, 3) ? '✓' : '3' }}</text>
            <text class="t">商户打款</text>
          </view>
          <view class="bar" :class="{ done: stepReached(r.status, 4) }"></view>
          <view class="step" :class="{ done: stepReached(r.status, 4) }">
            <text class="dot">{{ stepReached(r.status, 4) ? '✓' : '4' }}</text>
            <text class="t">我已收到</text>
          </view>
        </view>

        <!-- 商户已发款 → 用户确认 -->
        <view v-if="r.status === 'PAID'" class="cta-row">
          <text class="hint">商户已线下转账给您，请在收到款项后点击下方按钮确认</text>
          <view class="cta" @click="onConfirm(r)">我已确认收到</view>
        </view>
        <view v-else-if="r.status === 'COMPLETED'" class="done-row">
          ✓ 已完成（您已确认收款）
        </view>
        <view v-else-if="r.status === 'REJECTED'" class="rej-row">
          申请被驳回 · 推广奖励已退回
        </view>
      </view>
    </view>

    <view class="footer-tip">
      平台为技术中介，提现由商户线下转账并标记，<text class="b">您与商户共同确认完成</text>。
      平台不承担兑付保证责任。
    </view>
    <view class="bottom-pad"></view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { request } from '@/utils/request.js';
import { fen2yuan, fmtTime } from '@/utils/format.js';

const loading = ref(true);
const records = ref([]);

function statusText(s) {
  return {
    PENDING: '审批中',
    APPROVED: '审批通过 · 等待商户打款',
    PAID: '商户已打款 · 待您确认',
    COMPLETED: '已完成',
    REJECTED: '已驳回',
  }[s] || s;
}

function stepReached(status, step) {
  // step 2: PENDING 之后；step 3: APPROVED/PAID 之后；step 4: COMPLETED
  if (status === 'PENDING') return step <= 1;
  if (status === 'REJECTED') return step <= 2;
  if (status === 'APPROVED') return step <= 2;
  if (status === 'PAID') return step <= 3;
  if (status === 'COMPLETED') return step <= 4;
  return false;
}

async function load() {
  loading.value = true;
  try {
    const list = await request({ url: '/app-api/merchant/mini/withdraw/my-list' });
    records.value = Array.isArray(list) ? list : (list?.list || []);
  } catch {
    records.value = [];
  } finally {
    loading.value = false;
  }
}

async function onConfirm(r) {
  uni.showModal({
    title: '确认已收到打款',
    content: `您确认已经收到商户线下转账的 ¥${fen2yuan(r.amount, false)} 吗？\n\n确认后本次提现完成，无法撤销。`,
    success: async (res) => {
      if (!res.confirm) return;
      try {
        await request({
          url: `/app-api/merchant/mini/withdraw/confirm-received?id=${r.id}`,
          method: 'POST',
        });
        uni.showToast({ title: '感谢确认', icon: 'success' });
        load();
      } catch (e) {
        uni.showToast({ title: e?.message || '确认失败', icon: 'none' });
      }
    },
  });
}

onMounted(load);
onShow(load);
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.page { min-height: 100vh; background: $bg-2; padding-bottom: 30px; }
.loading { padding: 40px; text-align: center; color: $t4; }

.list { padding: 12px 14px; }
.card {
  background: $card;
  border-radius: $r-lg;
  padding: 16px;
  margin-bottom: 10px;
  box-shadow: $sh-1;
}
.row {
  display: flex; justify-content: space-between; align-items: center;
  padding: 6px 0;
  font-size: 13px;
}
.row.big { padding: 8px 0; }
.row .lbl { color: $t3; flex-shrink: 0; }
.row .val { color: $t1; font-weight: 600; text-align: right; flex: 1; margin-left: 12px; }
.row .val.amt { color: $o-d; font-size: 18px; font-weight: 900; }
.row .val.small { font-size: 12px; color: $t3; }
.row .val.status { font-weight: 800; }
.row .val.status.st-PENDING { color: $t3; }
.row .val.status.st-APPROVED { color: $o; }
.row .val.status.st-PAID { color: #3B82F6; }
.row .val.status.st-COMPLETED { color: #10B981; }
.row .val.status.st-REJECTED { color: #DC2626; }

.steps {
  display: flex; align-items: center;
  margin: 16px 0 0;
  padding-top: 14px;
  border-top: 1px dashed $line;
}
.step {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  flex-shrink: 0;
}
.step .dot {
  width: 22px; height: 22px; border-radius: 50%;
  background: $bg-2; color: $t4;
  font-size: 11px; font-weight: 800;
  display: flex; align-items: center; justify-content: center;
}
.step.done .dot { background: $o; color: #fff; }
.step .t { font-size: 9px; color: $t4; white-space: nowrap; }
.step.done .t { color: $t1; font-weight: 600; }
.bar {
  flex: 1; height: 2px; background: $line;
  margin: 0 -2px 14px;
}
.bar.done { background: $o; }

.cta-row {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed $line;
}
.cta-row .hint {
  display: block;
  font-size: 11.5px; color: $t3;
  margin-bottom: 8px; line-height: 1.5;
}
.cta {
  padding: 10px 0;
  background: linear-gradient(135deg, $o, $o-d);
  color: #fff;
  border-radius: 99px;
  font-size: 14px; font-weight: 800;
  text-align: center;
  box-shadow: $sh-warm;
}
.done-row {
  margin-top: 12px;
  padding: 10px 12px;
  background: rgba(16,185,129,.08);
  border-radius: $r-md;
  color: #10B981;
  font-size: 12.5px; font-weight: 700;
  text-align: center;
}
.rej-row {
  margin-top: 12px;
  padding: 10px 12px;
  background: rgba(220,38,38,.06);
  border-radius: $r-md;
  color: #DC2626;
  font-size: 12.5px; font-weight: 700;
  text-align: center;
}

.footer-tip {
  margin: 18px 14px;
  padding: 12px 16px;
  background: $bg-2;
  border-radius: $r-md;
  border: 1px dashed $line;
  font-size: 11px; color: $t4;
  line-height: 1.7;
}
.footer-tip .b { color: $t2; font-weight: 700; }
.bottom-pad { height: 30px; }
</style>
