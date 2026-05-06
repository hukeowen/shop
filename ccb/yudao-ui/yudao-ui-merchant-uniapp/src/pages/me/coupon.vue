<template>
  <view class="page">
    <!-- 顶部 + 新建按钮 -->
    <view class="topbar">
      <text class="back" @click="goBack">‹</text>
      <text class="title">优惠券</text>
      <text class="add" @click="openEdit(null)">+ 新建</text>
    </view>

    <view v-if="loading" class="empty-tip">加载中...</view>
    <view v-else-if="!list.length" class="empty-tip">还没建过券，点右上"+ 新建"试试</view>

    <view v-else class="list">
      <view
        v-for="c in list"
        :key="c.id"
        class="coupon-row"
        :class="{ off: c.status === 1 }"
      >
        <view class="amt-block">
          <view class="amt"><text class="cny">¥</text>{{ fen2yuan(c.discountAmount) }}</view>
          <view class="cond">满 {{ fen2yuan(c.minAmount) }} 用</view>
        </view>
        <view class="info">
          <view class="name">
            {{ c.name }}
            <text v-if="c.tag === 'NEW'" class="tag">新人</text>
          </view>
          <view class="meta">
            领取 {{ c.takenCount || 0 }}<text v-if="c.totalCount > 0"> / {{ c.totalCount }}</text>
            · 有效 {{ c.validDays }} 天
          </view>
        </view>
        <view class="actions">
          <text class="op" @click="openEdit(c)">编辑</text>
          <text class="op" @click="toggleStatus(c)">{{ c.status === 0 ? '下架' : '上架' }}</text>
          <text class="op danger" @click="onDelete(c)">删</text>
        </view>
      </view>
    </view>

    <!-- 编辑弹层 -->
    <view v-if="showEdit" class="mask" @click.self="showEdit = false">
      <view class="sheet">
        <view class="sheet-title">{{ form.id ? '编辑优惠券' : '新建优惠券' }}</view>
        <view class="field">
          <text class="label">券名</text>
          <input class="input" v-model="form.name" maxlength="32" placeholder="如：满 50 减 5" />
        </view>
        <view class="field">
          <text class="label">面额（元）</text>
          <input class="input" type="digit" v-model="form.discountYuan" placeholder="如 5" />
        </view>
        <view class="field">
          <text class="label">门槛（元，0=无门槛）</text>
          <input class="input" type="digit" v-model="form.minYuan" placeholder="如 50" />
        </view>
        <view class="field">
          <text class="label">发行总量（0=不限）</text>
          <input class="input" type="number" v-model="form.totalCount" placeholder="如 100" />
        </view>
        <view class="field">
          <text class="label">领后有效（天）</text>
          <input class="input" type="number" v-model="form.validDays" placeholder="如 30" />
        </view>
        <view class="field tag-field">
          <text class="label">标签</text>
          <view class="tag-row">
            <text
              class="tag-pill"
              :class="{ active: form.tag === '' }"
              @click="form.tag = ''"
            >通用</text>
            <text
              class="tag-pill"
              :class="{ active: form.tag === 'NEW' }"
              @click="form.tag = 'NEW'"
            >新人专享</text>
          </view>
        </view>

        <view class="sheet-actions">
          <button class="btn ghost" @click="showEdit = false">取消</button>
          <button class="btn primary" :disabled="saving" @click="onSave">
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { request } from '../../api/request.js';
import { fen2yuan } from '../../utils/format.js';

const loading = ref(true);
const list = ref([]);
const showEdit = ref(false);
const saving = ref(false);
const form = ref({
  id: null,
  name: '',
  discountYuan: '',
  minYuan: '',
  totalCount: '0',
  validDays: '30',
  tag: '',
});

async function load() {
  loading.value = true;
  try {
    const res = await request({ url: '/app-api/merchant/mini/coupon/list' });
    list.value = Array.isArray(res) ? res : [];
  } catch { list.value = []; }
  loading.value = false;
}

function openEdit(c) {
  if (c) {
    form.value = {
      id: c.id,
      name: c.name || '',
      discountYuan: ((c.discountAmount || 0) / 100).toString(),
      minYuan: ((c.minAmount || 0) / 100).toString(),
      totalCount: String(c.totalCount || 0),
      validDays: String(c.validDays || 30),
      tag: c.tag || '',
    };
  } else {
    form.value = { id: null, name: '', discountYuan: '', minYuan: '', totalCount: '0', validDays: '30', tag: '' };
  }
  showEdit.value = true;
}

async function onSave() {
  if (!form.value.name?.trim()) { uni.showToast({ title: '券名必填', icon: 'none' }); return; }
  const dy = parseFloat(form.value.discountYuan);
  if (!(dy > 0)) { uni.showToast({ title: '面额必须 > 0', icon: 'none' }); return; }
  const my = parseFloat(form.value.minYuan);
  if (!(my >= 0)) { uni.showToast({ title: '门槛不能为负', icon: 'none' }); return; }
  const days = parseInt(form.value.validDays);
  if (!(days >= 1)) { uni.showToast({ title: '有效天数 ≥ 1', icon: 'none' }); return; }

  saving.value = true;
  try {
    await request({
      url: '/app-api/merchant/mini/coupon/save',
      method: 'POST',
      data: {
        id: form.value.id || undefined,
        name: form.value.name.trim(),
        discountAmount: Math.round(dy * 100),
        minAmount: Math.round(my * 100),
        totalCount: parseInt(form.value.totalCount) || 0,
        validDays: days,
        tag: form.value.tag || null,
      },
    });
    uni.showToast({ title: '已保存', icon: 'success' });
    showEdit.value = false;
    await load();
  } catch (e) {
    uni.showToast({ title: '保存失败：' + (e?.message || ''), icon: 'none' });
  } finally {
    saving.value = false;
  }
}

async function toggleStatus(c) {
  const next = c.status === 0 ? 1 : 0;
  try {
    await request({
      url: `/app-api/merchant/mini/coupon/${c.id}/status?status=${next}`,
      method: 'PUT',
    });
    await load();
  } catch {}
}

async function onDelete(c) {
  const r = await uni.showModal({ title: '提示', content: `删除券「${c.name}」？` });
  if (!r.confirm) return;
  try {
    await request({ url: `/app-api/merchant/mini/coupon/${c.id}`, method: 'DELETE' });
    await load();
  } catch {}
}

function goBack() { uni.navigateBack({ fail: () => uni.reLaunch({ url: '/pages/me/index' }) }); }

onMounted(load);
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  min-height: 100vh;
  background: $bg-page;
  padding-bottom: 80rpx;
}
.topbar {
  display: flex; align-items: center;
  padding: 24rpx 32rpx;
  background: $bg-card;
  border-bottom: 2rpx solid $border-color;
  .back { font-size: 44rpx; color: $text-primary; padding-right: 16rpx; }
  .title { flex: 1; font-size: 32rpx; font-weight: 700; color: $text-primary; text-align: center; }
  .add { font-size: 26rpx; color: $brand-primary; font-weight: 700; }
}

.empty-tip { text-align: center; color: $text-secondary; padding: 120rpx 0; font-size: 26rpx; }

.list { padding: 24rpx 32rpx; display: flex; flex-direction: column; gap: 16rpx; }

.coupon-row {
  display: flex; align-items: center; gap: 24rpx;
  padding: 24rpx;
  background: linear-gradient(135deg, #fff 0%, #fff5ef 100%);
  border-radius: $radius-md;
  border: 2rpx dashed $brand-primary;
  &.off { background: #fafafa; border-color: $border-color; opacity: 0.7; }
}
.amt-block { text-align: center; flex-shrink: 0; min-width: 140rpx; }
.amt {
  font-size: 44rpx; font-weight: 800; color: $brand-primary;
  font-variant-numeric: tabular-nums; line-height: 1;
  .cny { font-size: 22rpx; }
}
.cond { font-size: 22rpx; color: $text-secondary; margin-top: 8rpx; }
.info { flex: 1; min-width: 0; }
.name {
  font-size: 28rpx; font-weight: 700; color: $text-primary;
  display: flex; align-items: center; gap: 12rpx; flex-wrap: wrap;
  .tag {
    background: $brand-primary; color: #fff;
    font-size: 18rpx; font-weight: 700;
    padding: 2rpx 12rpx; border-radius: 999rpx;
  }
}
.meta { margin-top: 8rpx; font-size: 22rpx; color: $text-secondary; }
.actions {
  display: flex; flex-direction: column; gap: 8rpx;
  .op {
    font-size: 22rpx; color: $brand-primary;
    padding: 6rpx 16rpx;
    border: 1rpx solid $brand-light-2;
    border-radius: 999rpx;
    text-align: center;
    &.danger { color: $danger; border-color: rgba(230,57,70,0.3); }
  }
}

// 编辑弹层
.mask {
  position: fixed; inset: 0;
  background: rgba(0,0,0,.45);
  display: flex; align-items: flex-end;
  z-index: 999;
}
.sheet {
  width: 100%;
  background: $bg-card;
  border-radius: 32rpx 32rpx 0 0;
  padding: 32rpx 32rpx 48rpx;
  max-height: 80vh; overflow-y: auto;
}
.sheet-title { font-size: 32rpx; font-weight: 700; text-align: center; margin-bottom: 24rpx; }
.field {
  display: flex; align-items: center;
  padding: 20rpx 0;
  border-bottom: 1rpx solid $border-color;
  .label { width: 240rpx; font-size: 26rpx; color: $text-secondary; flex-shrink: 0; }
  .input { flex: 1; font-size: 28rpx; color: $text-primary; height: 56rpx; }
  &.tag-field { flex-direction: column; align-items: flex-start; gap: 12rpx; }
}
.tag-row { display: flex; gap: 16rpx; }
.tag-pill {
  font-size: 24rpx;
  padding: 8rpx 24rpx;
  border-radius: 999rpx;
  background: $bg-page;
  color: $text-regular;
  &.active { background: $brand-primary; color: #fff; }
}
.sheet-actions { display: flex; gap: 24rpx; margin-top: 32rpx; }
.btn {
  flex: 1; height: 88rpx; line-height: 88rpx;
  border-radius: 44rpx; font-size: 30rpx; font-weight: 700;
  &.primary { background: $brand-primary; color: #fff; }
  &.ghost { background: $bg-page; color: $text-regular; }
}
</style>
