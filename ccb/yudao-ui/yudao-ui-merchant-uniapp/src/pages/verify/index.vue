<template>
  <view class="page">
    <nav-bar title="服务卡核销" />

    <!-- 录入区 -->
    <view class="card enter">
      <view class="scan-btn" @click="onScan">
        <text class="scan-ic">📷</text>
        <text class="scan-txt">扫描用户二维码</text>
      </view>
      <view class="or">或手动输入核销码</view>
      <view class="input-row">
        <input class="input" type="number" placeholder="输入卡上的数字码" v-model="cardNo" confirm-type="search" @confirm="onLookup" />
        <view class="q-btn" :class="{ disabled: !cardNo }" @click="onLookup">查询</view>
      </view>
    </view>

    <!-- 卡信息 -->
    <view v-if="info" class="card info">
      <view class="info-head">
        <text class="card-name">{{ info.name }}</text>
        <text class="badge" :class="`st-${info.effectiveStatus}`">{{ statusText(info.effectiveStatus) }}</text>
      </view>
      <view class="row"><text class="lbl">持卡用户</text><text class="val">{{ info.userMobile || ('ID ' + info.userId) }}</text></view>
      <view class="row"><text class="lbl">核销码</text><text class="val mono">{{ info.cardNo }}</text></view>
      <view class="row">
        <text class="lbl">剩余次数</text>
        <text class="val">{{ info.unlimited ? '不限次数' : (info.remainCount + ' 次 / 共 ' + info.maxCount + ' 次') }}</text>
      </view>
      <view class="row"><text class="lbl">有效期至</text><text class="val">{{ fmtDate(info.expireTime) }}</text></view>

      <view v-if="info.redeemable" class="redeem-area">
        <input class="remark" maxlength="30" placeholder="核销备注（选填）" v-model="remark" />
        <view class="redeem-btn" :class="{ busy: redeeming }" @click="onRedeem">{{ redeeming ? '核销中…' : '确认核销一次' }}</view>
      </view>
      <view v-else class="cant">{{ info.reason || '该卡当前不可核销' }}</view>
    </view>

    <!-- 最近核销记录 -->
    <view class="rec-title">最近核销记录</view>
    <view v-if="!records.length" class="rec-empty">暂无核销记录</view>
    <view v-else class="rec-list">
      <view v-for="r in records" :key="r.id" class="rec">
        <view class="rec-l">
          <text class="rec-name">{{ r.cardName }}</text>
          <text class="rec-sub">{{ r.userMobile || '' }} · {{ fmtDateTime(r.verifyTime) }}</text>
        </view>
        <text class="rec-cnt">第 {{ r.countAfter }} 次</text>
      </view>
    </view>
    <view class="bottom-pad"></view>
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { cardVerifyInfo, redeemCard, cardVerifyRecords } from '../../api/card.js';

const cardNo = ref('');
const info = ref(null);
const remark = ref('');
const redeeming = ref(false);
const records = ref([]);

function statusText(s) {
  return { ACTIVE: '可核销', USED_UP: '次数已用尽', EXPIRED: '已过期' }[s] || s;
}
function pad(n) { return n < 10 ? '0' + n : '' + n; }
function fmtDate(ms) {
  if (!ms) return '—';
  const d = new Date(ms);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}
function fmtDateTime(ms) {
  if (!ms) return '';
  const d = new Date(ms);
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function onScan() {
  uni.scanCode({
    scanType: ['qrCode'],
    success: (res) => {
      const v = (res.result || '').trim();
      if (!v) { uni.showToast({ title: '未识别到内容', icon: 'none' }); return; }
      cardNo.value = v;
      onLookup();
    },
    fail: () => { /* 用户取消扫码，忽略 */ },
  });
}

async function onLookup() {
  const no = (cardNo.value || '').trim();
  if (!no) { uni.showToast({ title: '请输入核销码', icon: 'none' }); return; }
  info.value = null;
  remark.value = '';
  uni.showLoading({ title: '查询中…' });
  try {
    info.value = await cardVerifyInfo(no);
  } catch (e) {
    uni.showToast({ title: e?.msg || e?.message || '未找到该卡', icon: 'none', duration: 2200 });
  } finally {
    uni.hideLoading();
  }
}

async function onRedeem() {
  if (!info.value || redeeming.value) return;
  const m = await uni.showModal({
    title: '确认核销',
    content: `核销「${info.value.name}」一次？\n核销后${info.value.unlimited ? '（不限次数卡）' : '剩余次数将减 1'}，操作不可撤销。`,
  });
  if (!m.confirm) return;
  redeeming.value = true;
  try {
    const r = await redeemCard(info.value.cardNo, remark.value);
    const left = r.unlimited ? '不限次数' : `剩余 ${r.remainCount} 次`;
    uni.showToast({ title: `核销成功 · ${left}`, icon: 'success', duration: 1800 });
    await onLookup();   // 刷新卡信息（剩余次数/状态）
    loadRecords();      // 刷新记录
  } catch (e) {
    uni.showToast({ title: e?.msg || e?.message || '核销失败', icon: 'none', duration: 2200 });
  } finally {
    redeeming.value = false;
  }
}

async function loadRecords() {
  try {
    const r = await cardVerifyRecords(1, 20);
    records.value = (r && r.list) || [];
  } catch {
    records.value = [];
  }
}

onShow(loadRecords);
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page { min-height: 100vh; background: #f5f6f8; padding-bottom: 40rpx; }
.card { background: #fff; border-radius: 20rpx; margin: 20rpx 24rpx; padding: 28rpx 28rpx; box-shadow: 0 2rpx 12rpx rgba(0,0,0,.03); }

/* 录入 */
.enter .scan-btn {
  height: 120rpx; border-radius: 16rpx;
  background: linear-gradient(135deg, $brand-primary, #ff9a4a);
  display: flex; align-items: center; justify-content: center; gap: 14rpx;
  .scan-ic { font-size: 44rpx; }
  .scan-txt { color: #fff; font-size: 32rpx; font-weight: 700; }
}
.enter .or { text-align: center; color: $text-secondary; font-size: 24rpx; margin: 20rpx 0 14rpx; }
.input-row { display: flex; gap: 16rpx; }
.input-row .input {
  flex: 1; min-height: 88rpx; background: #f6f7f9; border-radius: 14rpx;
  padding: 0 24rpx; font-size: 32rpx; color: $text-primary; box-sizing: border-box;
}
.input-row .q-btn {
  flex: 0 0 140rpx; min-height: 88rpx; line-height: 88rpx; text-align: center;
  background: $brand-primary; color: #fff; border-radius: 14rpx; font-size: 30rpx; font-weight: 700;
  &.disabled { opacity: .5; }
}

/* 卡信息 */
.info-head { display: flex; align-items: center; justify-content: space-between; padding-bottom: 18rpx; border-bottom: 1rpx solid #eee; margin-bottom: 8rpx; }
.card-name { font-size: 34rpx; font-weight: 800; color: $text-primary; }
.badge { font-size: 22rpx; font-weight: 700; padding: 4rpx 16rpx; border-radius: 999rpx; }
.badge.st-ACTIVE { color: #059669; background: rgba(5,150,105,.1); }
.badge.st-USED_UP { color: #b45309; background: rgba(180,83,9,.1); }
.badge.st-EXPIRED { color: #dc2626; background: rgba(220,38,38,.08); }
.row { display: flex; justify-content: space-between; align-items: center; padding: 14rpx 0; font-size: 28rpx; }
.row .lbl { color: $text-secondary; }
.row .val { color: $text-primary; font-weight: 600; }
.row .val.mono { font-family: monospace; letter-spacing: 1rpx; }

.redeem-area { margin-top: 18rpx; padding-top: 18rpx; border-top: 1rpx dashed #eee; }
.remark { min-height: 80rpx; background: #f6f7f9; border-radius: 12rpx; padding: 0 20rpx; font-size: 28rpx; margin-bottom: 16rpx; box-sizing: border-box; }
.redeem-btn {
  min-height: 92rpx; line-height: 92rpx; text-align: center; border-radius: 16rpx;
  background: linear-gradient(135deg, $brand-primary, #ff8a3a); color: #fff; font-size: 32rpx; font-weight: 800;
  &.busy { opacity: .6; }
}
.cant { margin-top: 16rpx; padding: 20rpx; text-align: center; background: #fef2f2; color: #dc2626; border-radius: 12rpx; font-size: 28rpx; font-weight: 600; }

/* 记录 */
.rec-title { margin: 28rpx 28rpx 12rpx; font-size: 28rpx; font-weight: 700; color: $text-primary; }
.rec-empty { text-align: center; color: $text-placeholder; font-size: 26rpx; padding: 30rpx; }
.rec-list { margin: 0 24rpx; background: #fff; border-radius: 20rpx; overflow: hidden; }
.rec { display: flex; align-items: center; justify-content: space-between; padding: 22rpx 24rpx; border-bottom: 1rpx solid #f2f2f2; }
.rec:last-child { border-bottom: none; }
.rec-l { display: flex; flex-direction: column; gap: 6rpx; }
.rec-name { font-size: 28rpx; font-weight: 600; color: $text-primary; }
.rec-sub { font-size: 22rpx; color: $text-secondary; }
.rec-cnt { font-size: 26rpx; color: $brand-primary; font-weight: 700; }
.bottom-pad { height: 40rpx; }
</style>
