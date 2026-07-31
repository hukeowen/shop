<template>
  <view class="page">
    <view class="topbar safe-top">
      <text class="tb-back" @click="goBack">‹</text>
      <text class="tb-title">用户提现审批</text>
      <text class="tb-right"></text>
    </view>

    <!-- 状态筛选 tab -->
    <scroll-view scroll-x class="tab-bar">
      <view v-for="t in tabs" :key="t.k"
            class="tab" :class="{ on: status === t.k }"
            @click="onTab(t.k)">
        {{ t.label }}
      </view>
    </scroll-view>

    <view v-if="loading" class="loading">加载中…</view>
    <view v-else-if="!records.length" class="empty">暂无提现申请</view>

    <view v-else class="list">
      <view v-for="r in records" :key="r.id" class="card">
        <view class="head">
          <text class="id">#{{ r.id }}</text>
          <text class="status" :class="`st-${r.status}`">{{ statusText(r.status) }}</text>
        </view>
        <view class="row big">
          <text class="lbl">用户申请金额</text>
          <text class="amt">¥{{ fen2yuan(r.amount) }}</text>
        </view>
        <view class="row">
          <text class="lbl">用户 ID</text>
          <text class="val">{{ r.userId }}</text>
        </view>
        <view class="row">
          <text class="lbl">申请时间</text>
          <text class="val">{{ fmtTime(r.applyAt) }}</text>
        </view>
        <view v-if="r.remark" class="row">
          <text class="lbl">备注</text>
          <text class="val">{{ r.remark }}</text>
        </view>

        <view v-if="r.status === 'PENDING'" class="cta-row">
          <view class="cta reject" @click="onReject(r)">驳回</view>
          <view class="cta approve" @click="onApprove(r)">审批通过</view>
        </view>
        <view v-else-if="r.status === 'APPROVED'" class="cta-row">
          <text class="hint">请线下联系用户完成转账</text>
          <view class="cta full" @click="onMarkPaid(r)">已转账，标记已打款</view>
        </view>
        <view v-else-if="r.status === 'PAID'" class="info-row">
          已打款 · 等待用户确认收款
        </view>
        <view v-else-if="r.status === 'COMPLETED'" class="info-row done">
          ✓ 已完成 · 用户确认收款
        </view>
        <view v-else-if="r.status === 'REJECTED'" class="info-row rej">
          已驳回 · 推广积分已退回用户
        </view>
      </view>
    </view>

    <view class="footer-tip">
      工作流：用户申请 → 商户审批 → 线下转账 → 商户标记已打款 → 用户确认收款 → 完成。<br/>
      平台为技术中介，<text class="b">提现资金由商户自行划拨</text>，平台不参与代收代付。
    </view>
    <view class="bottom-pad"></view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { request } from '../../api/request.js';

const tabs = [
  { k: '', label: '全部' },
  { k: 'PENDING', label: '待审批' },
  { k: 'APPROVED', label: '待打款' },
  { k: 'PAID', label: '待用户确认' },
  { k: 'COMPLETED', label: '已完成' },
  { k: 'REJECTED', label: '已驳回' },
];

const status = ref('PENDING');
const loading = ref(false);
const records = ref([]);

function fen2yuan(fen, withSign = false) {
  if (fen == null) return withSign ? '¥0.00' : '0.00';
  const n = (Number(fen) / 100).toFixed(2);
  return withSign ? `¥${n}` : n;
}
function fmtTime(t) {
  if (!t) return '';
  const d = typeof t === 'number' ? new Date(t) : new Date(String(t).replace('T', ' ').replace(/-/g, '/'));
  if (Number.isNaN(d.getTime())) return '';
  const pad = (x) => String(x).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function statusText(s) {
  return {
    PENDING: '待审批',
    APPROVED: '待打款',
    PAID: '已打款 · 等用户确认',
    COMPLETED: '已完成',
    REJECTED: '已驳回',
  }[s] || s;
}

function onTab(k) { status.value = k; load(); }

async function load() {
  loading.value = true;
  try {
    const url = `/app-api/merchant/mini/withdraw/page?pageNo=1&pageSize=50${status.value ? `&status=${status.value}` : ''}`;
    const r = await request({ url });
    records.value = r?.list || [];
  } catch { records.value = []; }
  finally { loading.value = false; }
}

async function onApprove(r) {
  uni.showModal({
    title: '审批通过',
    content: `通过用户 ${r.userId} 的 ¥${fen2yuan(r.amount)} 提现申请？\n通过后请尽快线下转账。`,
    success: async (res) => {
      if (!res.confirm) return;
      try {
        await request({ url: `/app-api/merchant/mini/withdraw/approve?id=${r.id}`, method: 'POST' });
        uni.showToast({ title: '审批通过', icon: 'success' });
        load();
      } catch (e) { uni.showToast({ title: e?.message || '操作失败', icon: 'none' }); }
    },
  });
}

async function onReject(r) {
  uni.showModal({
    title: '驳回申请',
    content: `驳回用户 ${r.userId} 的 ¥${fen2yuan(r.amount)} 提现？\n推广积分将自动退回用户。`,
    editable: true,
    placeholderText: '驳回原因（可选）',
    success: async (res) => {
      if (!res.confirm) return;
      try {
        const remark = encodeURIComponent(res.content || '');
        await request({ url: `/app-api/merchant/mini/withdraw/reject?id=${r.id}&remark=${remark}`, method: 'POST' });
        uni.showToast({ title: '已驳回', icon: 'success' });
        load();
      } catch (e) { uni.showToast({ title: e?.message || '操作失败', icon: 'none' }); }
    },
  });
}

async function onMarkPaid(r) {
  uni.showModal({
    title: '确认已打款',
    content: `确认已线下转账 ¥${fen2yuan(r.amount)} 给用户 ${r.userId}？\n标记后用户将看到并需确认收款。`,
    editable: true,
    placeholderText: '转账流水号（可选）',
    success: async (res) => {
      if (!res.confirm) return;
      try {
        const remark = encodeURIComponent(res.content || '');
        await request({ url: `/app-api/merchant/mini/withdraw/mark-paid?id=${r.id}&remark=${remark}`, method: 'POST' });
        uni.showToast({ title: '已标记已打款', icon: 'success' });
        load();
      } catch (e) { uni.showToast({ title: e?.message || '操作失败', icon: 'none' }); }
    },
  });
}

function goBack() { uni.navigateBack({ fail: () => uni.reLaunch({ url: '/pages/me/index' }) }); }

onMounted(load);
onShow(load);
</script>

<style lang="scss" scoped>
.safe-top { padding-top: calc(max(env(safe-area-inset-top, 0px), var(--status-bar-height, 0px)) + 16rpx) !important; }
.topbar {
  display: flex; align-items: center; padding: 16rpx 24rpx;
  background: #fff; border-bottom: 1rpx solid #eef0f4;
}
.topbar .tb-back { font-size: 44rpx; color: #1b1f23; width: 60rpx; }
.topbar .tb-title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; color: #1b1f23; }
.topbar .tb-right { width: 60rpx; }

.page { min-height: 100vh; background: #F6F7F9; padding-bottom: 30px; }

.tab-bar {
  white-space: nowrap;
  background: #fff;
  padding: 16rpx 24rpx;
  border-bottom: 1rpx solid #EEF0F4;
}
.tab {
  display: inline-block;
  padding: 12rpx 24rpx;
  margin-right: 12rpx;
  font-size: 24rpx;
  color: #606266;
  background: #F4F5F7;
  border-radius: 99rpx;
}
.tab.on {
  background: linear-gradient(135deg, #FF6B35, #E25316);
  color: #fff;
  font-weight: 700;
}

.loading, .empty { padding: 80rpx 0; text-align: center; color: #909399; font-size: 26rpx; }

.list { padding: 20rpx 24rpx; }
.card {
  background: #fff;
  border-radius: 24rpx;
  padding: 32rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 4rpx 12rpx rgba(0,0,0,.04);
}
.head {
  display: flex; justify-content: space-between; align-items: center;
  padding-bottom: 20rpx;
  border-bottom: 1rpx dashed #EEF0F4;
  margin-bottom: 16rpx;
}
.head .id { font-size: 22rpx; color: #909399; font-family: monospace; }
.head .status { font-size: 24rpx; font-weight: 700; padding: 4rpx 16rpx; border-radius: 8rpx; }
.head .status.st-PENDING { color: #909399; background: #F4F5F7; }
.head .status.st-APPROVED { color: #FF6B35; background: rgba(255,107,53,.1); }
.head .status.st-PAID { color: #3B82F6; background: rgba(59,130,246,.1); }
.head .status.st-COMPLETED { color: #10B981; background: rgba(16,185,129,.1); }
.head .status.st-REJECTED { color: #DC2626; background: rgba(220,38,38,.1); }

.row { display: flex; justify-content: space-between; padding: 8rpx 0; font-size: 26rpx; }
.row.big { padding: 16rpx 0; align-items: center; }
.row .lbl { color: #909399; }
.row .val { color: #303133; font-weight: 600; }
.row .amt { color: #E25316; font-size: 42rpx; font-weight: 900; }

.cta-row {
  display: flex; gap: 16rpx;
  margin-top: 24rpx;
  padding-top: 20rpx;
  border-top: 1rpx dashed #EEF0F4;
}
.cta-row .hint {
  display: block;
  width: 100%;
  font-size: 22rpx; color: #909399;
  margin-bottom: 12rpx;
}
.cta {
  flex: 1;
  padding: 20rpx 0;
  border-radius: 99rpx;
  font-size: 28rpx; font-weight: 800;
  text-align: center;
}
.cta.approve { background: linear-gradient(135deg, #FF6B35, #E25316); color: #fff; }
.cta.reject { background: #fff; color: #DC2626; border: 1rpx solid #DC2626; }
.cta.full { width: 100%; background: linear-gradient(135deg, #3B82F6, #2563EB); color: #fff; }

.info-row {
  margin-top: 24rpx;
  padding: 16rpx 24rpx;
  background: rgba(59,130,246,.06);
  color: #3B82F6;
  border-radius: 12rpx;
  font-size: 24rpx; font-weight: 700;
  text-align: center;
}
.info-row.done { background: rgba(16,185,129,.08); color: #10B981; }
.info-row.rej { background: rgba(220,38,38,.06); color: #DC2626; }

.footer-tip {
  margin: 24rpx;
  padding: 20rpx 24rpx;
  background: #fff;
  border-radius: 16rpx;
  border: 1rpx dashed #EEF0F4;
  font-size: 22rpx; color: #909399; line-height: 1.7;
}
.footer-tip .b { color: #606266; font-weight: 700; }
.bottom-pad { height: 40rpx; }
</style>
