<template>
  <view class="page">
    <view class="topbar safe-top">
      <text class="tb-back" @click="goBack">‹</text>
      <text class="tb-title">服务卡核销</text>
      <text class="tb-right"></text>
    </view>

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

    <!-- #ifdef H5 -->
    <!-- H5 摄像头扫码遮罩：getUserMedia + jsQR 实时识别 -->
    <view v-if="scanning" class="scan-mask">
      <view class="scan-host" id="qr-cam-host"></view>
      <view class="scan-frame"></view>
      <view class="scan-hint">{{ scanErr || '将用户的二维码对准取景框' }}</view>
      <view class="scan-cancel" @click="stopH5Scan">取消</view>
    </view>
    <!-- #endif -->
  </view>
</template>

<script setup>
import { ref, nextTick } from 'vue';
import { onShow, onHide, onUnload } from '@dcloudio/uni-app';
import { cardVerifyInfo, redeemCard, cardVerifyRecords } from '../../api/card.js';

const cardNo = ref('');
const info = ref(null);
const remark = ref('');
const redeeming = ref(false);
const records = ref([]);

// H5 摄像头扫码状态
const scanning = ref(false);
const scanErr = ref('');

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

// 扫到内容后统一处理：取数字串（兼容二维码里带前后缀/URL 的情况）
function onScanned(raw) {
  const v = (raw || '').trim();
  if (!v) { uni.showToast({ title: '未识别到内容', icon: 'none' }); return; }
  // 卡的二维码内容就是纯数字核销码；若扫到的是含数字的串，抽取最长数字段兜底
  const m = v.match(/\d{6,}/);
  cardNo.value = m ? m[0] : v;
  onLookup();
}

function onScan() {
  // #ifndef H5
  // App / 小程序：原生扫码最稳
  uni.scanCode({
    scanType: ['qrCode'],
    success: (res) => onScanned(res.result),
    fail: () => { /* 用户取消扫码，忽略 */ },
  });
  // #endif
  // #ifdef H5
  startH5Scan();
  // #endif
}

// #ifdef H5
// ===== H5 摄像头扫码：getUserMedia 取流 + jsQR 逐帧解码 =====
let _stream = null;
let _video = null;
let _canvas = null;
let _raf = 0;
let _jsQR = null;

async function startH5Scan() {
  scanErr.value = '';
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    uni.showToast({ title: '当前浏览器不支持扫码，请手动输入', icon: 'none', duration: 2500 });
    return;
  }
  scanning.value = true;
  await nextTick();
  try {
    if (!_jsQR) _jsQR = (await import('jsqr')).default;
    _stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: { ideal: 'environment' } },
      audio: false,
    });
    const host = document.getElementById('qr-cam-host');
    if (!host) throw new Error('no-host');
    _video = document.createElement('video');
    _video.setAttribute('playsinline', 'true');
    _video.setAttribute('muted', 'true');
    _video.muted = true;
    _video.autoplay = true;
    _video.style.cssText = 'width:100%;height:100%;object-fit:cover;';
    host.innerHTML = '';
    host.appendChild(_video);
    _video.srcObject = _stream;
    await _video.play().catch(() => {});
    _canvas = document.createElement('canvas');
    _tick();
  } catch (e) {
    const name = e && e.name;
    scanErr.value =
      name === 'NotAllowedError' ? '未授权摄像头，请在浏览器设置允许后重试'
      : name === 'NotFoundError' ? '未检测到摄像头'
      : '无法打开摄像头，请手动输入核销码';
    // 取消流但留遮罩显示错误，用户可点取消
    if (_stream) { _stream.getTracks().forEach((t) => t.stop()); _stream = null; }
  }
}

function _tick() {
  _raf = requestAnimationFrame(_tick);
  if (!_video || _video.readyState !== _video.HAVE_ENOUGH_DATA) return;
  const w = _video.videoWidth, h = _video.videoHeight;
  if (!w || !h) return;
  _canvas.width = w; _canvas.height = h;
  const ctx = _canvas.getContext('2d', { willReadFrequently: true });
  ctx.drawImage(_video, 0, 0, w, h);
  let img;
  try { img = ctx.getImageData(0, 0, w, h); } catch { return; }
  const code = _jsQR(img.data, w, h, { inversionAttempts: 'dontInvert' });
  if (code && code.data) {
    const val = code.data;
    stopH5Scan();
    onScanned(val);
  }
}

function stopH5Scan() {
  if (_raf) { cancelAnimationFrame(_raf); _raf = 0; }
  if (_stream) { _stream.getTracks().forEach((t) => t.stop()); _stream = null; }
  if (_video) { try { _video.srcObject = null; } catch {} _video = null; }
  const host = document.getElementById('qr-cam-host');
  if (host) host.innerHTML = '';
  scanning.value = false;
}

onHide(() => { if (scanning.value) stopH5Scan(); });
onUnload(() => { if (scanning.value) stopH5Scan(); });
// #endif

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

function goBack() { uni.navigateBack({ fail: () => uni.reLaunch({ url: '/pages/me/index' }) }); }

onShow(loadRecords);
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.safe-top { padding-top: calc(max(env(safe-area-inset-top, 0px), var(--status-bar-height, 0px)) + 16rpx); }
.topbar {
  display: flex; align-items: center; padding: 16rpx 24rpx;
  background: #fff; border-bottom: 1rpx solid #eef0f4;
}
.topbar .tb-back { font-size: 44rpx; color: #1b1f23; width: 60rpx; }
.topbar .tb-title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; color: #1b1f23; }
.topbar .tb-right { width: 60rpx; }

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

/* H5 摄像头扫码遮罩 */
.scan-mask {
  position: fixed; left: 0; top: 0; right: 0; bottom: 0; z-index: 999;
  background: #000;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
}
.scan-host { position: absolute; left: 0; top: 0; width: 100%; height: 100%; overflow: hidden; }
.scan-frame {
  position: relative; z-index: 2;
  width: 460rpx; height: 460rpx; border-radius: 28rpx;
  border: 4rpx solid rgba(255,255,255,.9);
  box-shadow: 0 0 0 9999rpx rgba(0,0,0,.45);
}
.scan-hint {
  position: relative; z-index: 2; margin-top: 48rpx;
  color: #fff; font-size: 28rpx; text-align: center; padding: 0 60rpx; line-height: 1.5;
}
.scan-cancel {
  position: absolute; z-index: 3; bottom: 100rpx; left: 50%; transform: translateX(-50%);
  padding: 18rpx 70rpx; border-radius: 999rpx;
  background: rgba(255,255,255,.16); color: #fff; font-size: 30rpx; font-weight: 600;
  border: 2rpx solid rgba(255,255,255,.4);
}
</style>
