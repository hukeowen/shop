<template>
  <view class="page">
    <view class="topbar safe-top">
      <text class="back" @click="goBack">‹</text>
      <text class="title">帮助与反馈</text>
      <text class="right" @click="toggleHistory">{{ showHistory ? '提交' : '我的反馈' }}</text>
    </view>

    <!-- 提交反馈表单 -->
    <view v-if="!showHistory">
      <!-- FAQ -->
      <view class="faq">
        <view class="faq-title">常见问题</view>
        <view
          v-for="(q, i) in faqs"
          :key="i"
          :class="['faq-item', expanded === i ? 'open' : '']"
        >
          <view class="faq-q" @click="expanded = expanded === i ? -1 : i">
            <text class="q-text">{{ q.q }}</text>
            <text class="q-icon">{{ expanded === i ? '−' : '+' }}</text>
          </view>
          <view v-if="expanded === i" class="faq-a">{{ q.a }}</view>
        </view>
      </view>

      <view class="form-card">
        <view class="form-title">还没解决？告诉我们</view>

        <view class="cat-row">
          <text
            v-for="c in categories"
            :key="c.k"
            :class="['cat', form.category === c.k ? 'active' : '']"
            @click="form.category = c.k"
          >{{ c.l }}</text>
        </view>

        <textarea
          class="content-area"
          v-model="form.content"
          placeholder="请详细描述问题或建议（5-2000 字）"
          maxlength="2000"
        />
        <view class="word-count">{{ (form.content || '').length }} / 2000</view>

        <input
          class="contact"
          v-model="form.contact"
          placeholder="联系方式（手机/微信，可选）"
          maxlength="64"
        />

        <view class="submit-btn" :class="canSubmit ? '' : 'disabled'" @click="submit">
          {{ submitting ? '提交中...' : '提交反馈' }}
        </view>

        <view class="form-tip">
          💬 工作时间内（09:00-22:00）通常 24h 内回复，加急可拨打 400 客服
        </view>
      </view>
    </view>

    <!-- 我的反馈历史 -->
    <view v-else>
      <view v-if="loadingList" class="empty-tip">加载中…</view>
      <view v-else-if="!list.length" class="empty-state">
        <view class="empty-emoji">📝</view>
        <view class="empty-title">还没提交过反馈</view>
        <view class="empty-sub">遇到问题随时来这里告诉我们</view>
      </view>

      <view v-else>
        <view v-for="r in list" :key="r.id" class="hist-card">
          <view class="hist-hdr">
            <text class="cat-tag">{{ catLabel(r.category) }}</text>
            <text :class="['status', 'st-' + (r.status || 0)]">{{ statusLabel(r.status) }}</text>
            <text class="time">{{ formatTime(r.createTime) }}</text>
          </view>
          <view class="hist-content">{{ r.content }}</view>
          <view v-if="r.reply" class="hist-reply">
            <view class="reply-label">客服回复：</view>
            <view class="reply-text">{{ r.reply }}</view>
          </view>
        </view>
      </view>
    </view>
    <view class="bottom-space"></view>
  </view>
</template>

<script setup>
import { ref, computed, reactive, onMounted } from 'vue';
import { request } from '../../api/request.js';

const expanded = ref(-1);
const showHistory = ref(false);
const submitting = ref(false);

const faqs = [
  { q: '推广积分能干什么？',
    a: '推广积分可按 1:1 兑换为消费积分（下单时抵扣），满 100 分可申请提现到该店余额。每家店是独立账户。' },
  { q: '为什么我没拿到推广奖？',
    a: '需要"朋友通过你的链接进店并下单 / 自然进店买你买过的同款商品"。你必须在该店"我的队列"里能看到这个商品（A 层或 B 层）。' },
  { q: '为什么余额不能跨店用？',
    a: '余额按店独立结算，避免跨店资金风险。可在"我的"→某家店的余额卡里发起提现到微信。' },
  { q: '怎么把店铺收藏到首页？',
    a: '进入店铺首页（shop-home），点右上角的 ♥ 心形图标即可收藏；"我的"页可一键查看收藏夹。' },
  { q: '订单未付款怎么取消？',
    a: '在"订单"页找到该订单，点右下角"取消"按钮即可。已付款订单需联系商家退款。' },
];

const categories = [
  { k: 'BUG',     l: '问题反馈' },
  { k: 'FEATURE', l: '功能建议' },
  { k: 'PAYMENT', l: '支付' },
  { k: 'ACCOUNT', l: '账户' },
  { k: 'SHOP',    l: '店铺' },
  { k: 'OTHER',   l: '其他' },
];

const form = reactive({ category: 'OTHER', content: '', contact: '' });
const list = ref([]);
const loadingList = ref(false);

const canSubmit = computed(() => (form.content || '').trim().length >= 5 && !submitting.value);

function catLabel(k) {
  return (categories.find(c => c.k === k) || { l: '其他' }).l;
}
function statusLabel(s) {
  return ({ 0: '待处理', 1: '处理中', 2: '已解决', 3: '已关闭' })[s] || '待处理';
}
function formatTime(t) {
  if (!t) return '';
  const d = new Date(typeof t === 'string' ? t.replace(' ', 'T') : t);
  if (isNaN(d.getTime())) return '';
  return `${d.getMonth() + 1}-${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

async function submit() {
  if (!canSubmit.value) {
    if ((form.content || '').trim().length < 5) {
      uni.showToast({ title: '请描述详细一点（≥ 5 字）', icon: 'none' });
    }
    return;
  }
  submitting.value = true;
  try {
    await request({
      url: '/app-api/merchant/mini/feedback/submit',
      method: 'POST',
      data: {
        category: form.category,
        content: form.content.trim(),
        contact: form.contact?.trim() || null,
      },
    });
    uni.showToast({ title: '已收到，我们会尽快处理', icon: 'success' });
    form.content = '';
    form.contact = '';
    setTimeout(() => { showHistory.value = true; loadList(); }, 800);
  } catch (e) {
    uni.showToast({ title: e?.message || '提交失败，请稍后再试', icon: 'none' });
  } finally {
    submitting.value = false;
  }
}

async function loadList() {
  loadingList.value = true;
  try {
    const res = await request({ url: '/app-api/merchant/mini/feedback/my-list?pageNo=1&pageSize=50' });
    list.value = res?.list || [];
  } catch { list.value = []; }
  finally { loadingList.value = false; }
}

function toggleHistory() {
  showHistory.value = !showHistory.value;
  if (showHistory.value && !list.value.length) {
    loadList();
  }
}
function goBack() { uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/user-me/index' }) }); }

onMounted(() => {});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';
.page { min-height: 100vh; background: $bg-page; }
.safe-top { padding-top: calc(env(safe-area-inset-top) + 16rpx); }

.topbar { display: flex; align-items: center; padding: 16rpx 32rpx; background: $bg-card; border-bottom: 1rpx solid $border-color; }
.topbar .back { font-size: 44rpx; color: $text-primary; padding-right: 16rpx; }
.topbar .title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; color: $text-primary; }
.topbar .right { font-size: 26rpx; color: $brand-primary; }

.faq { margin: 24rpx 32rpx; background: $bg-card; border-radius: $radius-md; box-shadow: 0 4rpx 12rpx rgba(15,23,42,.03); padding: 16rpx 0; }
.faq .faq-title { padding: 16rpx 32rpx 12rpx; font-size: 28rpx; font-weight: 700; color: $text-primary; }
.faq .faq-item { padding: 0 32rpx; border-top: 1rpx dashed $border-color; }
.faq .faq-q { display: flex; align-items: center; padding: 24rpx 0; }
.faq .faq-q .q-text { flex: 1; font-size: 26rpx; color: $text-primary; }
.faq .faq-q .q-icon { font-size: 32rpx; color: $brand-primary; }
.faq .faq-a { padding: 0 0 24rpx; font-size: 24rpx; color: $text-secondary; line-height: 1.7; }

.form-card { margin: 24rpx 32rpx; padding: 32rpx; background: $bg-card; border-radius: $radius-lg; box-shadow: 0 4rpx 16rpx rgba(15,23,42,.04); }
.form-card .form-title { font-size: 30rpx; font-weight: 700; color: $text-primary; margin-bottom: 24rpx; }

.cat-row { display: flex; flex-wrap: wrap; gap: 12rpx; margin-bottom: 24rpx; }
.cat-row .cat { padding: 12rpx 24rpx; background: $bg-page; color: $text-secondary; font-size: 24rpx; border-radius: 999rpx; }
.cat-row .cat.active { background: $brand-primary; color: #fff; font-weight: 600; }

.content-area { width: 100%; min-height: 240rpx; padding: 24rpx; background: $bg-page; border-radius: $radius-md; font-size: 28rpx; color: $text-primary; line-height: 1.6; box-sizing: border-box; }
.word-count { text-align: right; margin-top: 8rpx; font-size: 22rpx; color: $text-placeholder; font-variant-numeric: tabular-nums; }
.contact { width: 100%; margin-top: 16rpx; padding: 24rpx; background: $bg-page; border-radius: $radius-md; font-size: 28rpx; color: $text-primary; box-sizing: border-box; }

.submit-btn { margin-top: 32rpx; padding: 24rpx; background: $brand-primary; color: #fff; text-align: center; border-radius: $radius-md; font-size: 30rpx; font-weight: 700; }
.submit-btn.disabled { opacity: 0.5; }
.form-tip { margin-top: 16rpx; font-size: 22rpx; color: $text-placeholder; text-align: center; }

.empty-tip { text-align: center; padding: 80rpx 0; color: $text-placeholder; font-size: 26rpx; }
.empty-state { text-align: center; padding: 120rpx 60rpx; }
.empty-state .empty-emoji { font-size: 96rpx; margin-bottom: 24rpx; opacity: .5; }
.empty-state .empty-title { font-size: 32rpx; font-weight: 700; color: $text-primary; }
.empty-state .empty-sub { margin-top: 12rpx; font-size: 24rpx; color: $text-placeholder; }

.hist-card { margin: 24rpx 32rpx; padding: 28rpx 32rpx; background: $bg-card; border-radius: $radius-lg; box-shadow: 0 4rpx 12rpx rgba(15,23,42,.03); }
.hist-hdr { display: flex; align-items: center; gap: 12rpx; margin-bottom: 12rpx; }
.hist-hdr .cat-tag { font-size: 22rpx; padding: 4rpx 12rpx; background: $brand-primary-light; color: $brand-primary; border-radius: 8rpx; font-weight: 600; }
.hist-hdr .status { font-size: 22rpx; padding: 4rpx 12rpx; border-radius: 8rpx; font-weight: 600; }
.hist-hdr .status.st-0 { background: rgba(245,158,11,.15); color: #B45309; }
.hist-hdr .status.st-1 { background: rgba(59,130,246,.15); color: #2563EB; }
.hist-hdr .status.st-2 { background: rgba(16,185,129,.15); color: $success; }
.hist-hdr .status.st-3 { background: $bg-page; color: $text-placeholder; }
.hist-hdr .time { margin-left: auto; font-size: 22rpx; color: $text-placeholder; font-variant-numeric: tabular-nums; }
.hist-content { font-size: 26rpx; color: $text-primary; line-height: 1.6; }
.hist-reply { margin-top: 16rpx; padding: 16rpx 20rpx; background: rgba(255,107,53,.06); border-left: 4rpx solid $brand-primary; border-radius: 8rpx; }
.hist-reply .reply-label { font-size: 22rpx; color: $brand-primary; font-weight: 700; margin-bottom: 4rpx; }
.hist-reply .reply-text { font-size: 24rpx; color: $text-secondary; line-height: 1.6; }

.bottom-space { height: 80rpx; }
</style>
