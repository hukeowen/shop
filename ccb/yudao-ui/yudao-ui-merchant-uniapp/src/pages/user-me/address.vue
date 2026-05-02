<template>
  <view class="page">
    <view class="topbar safe-top">
      <text class="back" @click="goBack">‹</text>
      <text class="title">收货地址</text>
      <view style="width:60rpx"></view>
    </view>

    <view v-if="loading && !list.length" class="empty-tip">加载中…</view>
    <view v-else-if="!list.length && !showEdit" class="empty-state">
      <view class="empty-emoji">📍</view>
      <view class="empty-title">还没有收货地址</view>
      <view class="empty-sub">添加地址后下单时可一键带入</view>
      <view class="empty-cta" @click="addNew">+ 添加新地址</view>
    </view>

    <view v-else-if="!showEdit">
      <view
        v-for="addr in list"
        :key="addr.id"
        :class="['addr-card', addr.defaultStatus ? 'default' : '']"
      >
        <view class="hdr">
          <text class="name">{{ addr.name }}</text>
          <text class="mobile">{{ formatMobile(addr.mobile) }}</text>
          <text v-if="addr.defaultStatus" class="default-tag">默认</text>
        </view>
        <view class="addr">{{ areaText(addr) }} {{ addr.detailAddress }}</view>
        <view class="ops">
          <text class="op" @click="editAddr(addr)">编辑</text>
          <text v-if="!addr.defaultStatus" class="op" @click="setDefault(addr)">设为默认</text>
          <text class="op danger" @click="removeAddr(addr)">删除</text>
        </view>
      </view>
      <view class="add-btn" @click="addNew">+ 新增收货地址</view>
    </view>

    <!-- 编辑表单 -->
    <view v-if="showEdit" class="form-card">
      <view class="form-title">{{ form.id ? '编辑地址' : '新增地址' }}</view>

      <view class="form-row">
        <text class="lbl">收件人</text>
        <input class="ipt" v-model="form.name" placeholder="请输入姓名" maxlength="20" />
      </view>
      <view class="form-row">
        <text class="lbl">手机号</text>
        <input class="ipt" v-model="form.mobile" placeholder="请输入 11 位手机号" type="number" maxlength="11" />
      </view>
      <view class="form-row" @click="pickArea">
        <text class="lbl">所在地区</text>
        <text :class="['ipt', form.areaText ? '' : 'placeholder']">{{ form.areaText || '请选择省市区' }}</text>
        <text class="arrow">›</text>
      </view>
      <view class="form-row textarea">
        <text class="lbl">详细地址</text>
        <textarea
          class="ipt"
          v-model="form.detailAddress"
          placeholder="街道、楼栋、门牌号等"
          maxlength="200"
        />
      </view>
      <view class="form-row">
        <text class="lbl">设为默认</text>
        <switch :checked="!!form.defaultStatus" color="#FF6B35" @change="(e) => form.defaultStatus = e.detail.value" />
      </view>

      <view class="form-actions">
        <view class="btn-cancel" @click="cancelEdit">取消</view>
        <view class="btn-save" @click="saveAddr">保存</view>
      </view>
    </view>
    <view class="bottom-space"></view>
  </view>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue';
import { request } from '../../api/request.js';

const list = ref([]);
const loading = ref(false);
const showEdit = ref(false);
const form = reactive({
  id: null, name: '', mobile: '', areaId: null,
  areaText: '', detailAddress: '', defaultStatus: false,
});

function emptyForm() {
  form.id = null; form.name = ''; form.mobile = '';
  form.areaId = null; form.areaText = ''; form.detailAddress = '';
  form.defaultStatus = list.value.length === 0;
}

function formatMobile(m) {
  if (!m || m.length < 7) return m || '';
  return m.slice(0, 3) + ' **** ' + m.slice(-4);
}
function areaText(a) {
  return a.areaName || (a.areaId ? `[地区${a.areaId}]` : '');
}

async function load() {
  loading.value = true;
  try {
    const data = await request({ url: '/app-api/member/address/list' });
    list.value = (data || []).slice().sort((a, b) => (b.defaultStatus ? 1 : 0) - (a.defaultStatus ? 1 : 0));
  } catch { list.value = []; }
  finally { loading.value = false; }
}

function addNew() {
  emptyForm();
  showEdit.value = true;
}
function editAddr(addr) {
  form.id = addr.id;
  form.name = addr.name;
  form.mobile = addr.mobile;
  form.areaId = addr.areaId;
  form.areaText = addr.areaName || '';
  form.detailAddress = addr.detailAddress;
  form.defaultStatus = !!addr.defaultStatus;
  showEdit.value = true;
}
function cancelEdit() {
  showEdit.value = false;
  emptyForm();
}

function pickArea() {
  // uni-app H5 没有内置省市区选择器：用一个最简的输入框降级
  // 真实生产建议接入 uni-data-picker 或 uni-ui，按 area 表选择 areaId
  uni.showModal({
    title: '所在地区',
    editable: true,
    placeholderText: '例：北京市朝阳区',
    content: form.areaText || '',
    success: ({ confirm, content }) => {
      if (!confirm) return;
      form.areaText = (content || '').trim();
      // 没接入省市区表时，用 0 占位（areaId 必填，0 = 未指定）
      if (!form.areaId) form.areaId = 0;
    },
  });
}

async function saveAddr() {
  if (!form.name) { uni.showToast({ title: '请输入收件人', icon: 'none' }); return; }
  if (!/^1\d{10}$/.test(form.mobile)) { uni.showToast({ title: '请输入正确的手机号', icon: 'none' }); return; }
  if (!form.detailAddress) { uni.showToast({ title: '请输入详细地址', icon: 'none' }); return; }
  if (form.areaId == null) form.areaId = 0;
  const data = {
    name: form.name,
    mobile: form.mobile,
    areaId: form.areaId,
    detailAddress: form.detailAddress,
    defaultStatus: !!form.defaultStatus,
  };
  try {
    uni.showLoading({ title: '保存中', mask: true });
    if (form.id) {
      await request({ url: '/app-api/member/address/update', method: 'PUT', data: { ...data, id: form.id } });
    } else {
      await request({ url: '/app-api/member/address/create', method: 'POST', data });
    }
    uni.hideLoading();
    uni.showToast({ title: '保存成功', icon: 'success' });
    showEdit.value = false;
    emptyForm();
    await load();
  } catch (e) {
    uni.hideLoading();
    uni.showToast({ title: e?.message || '保存失败', icon: 'none' });
  }
}

function setDefault(addr) {
  // 复用 update 接口，把当前 addr.defaultStatus = true
  uni.showLoading({ title: '设置中', mask: true });
  request({
    url: '/app-api/member/address/update',
    method: 'PUT',
    data: {
      id: addr.id,
      name: addr.name,
      mobile: addr.mobile,
      areaId: addr.areaId,
      detailAddress: addr.detailAddress,
      defaultStatus: true,
    },
  }).then(() => {
    uni.hideLoading();
    uni.showToast({ title: '已设为默认', icon: 'success' });
    load();
  }).catch(e => {
    uni.hideLoading();
    uni.showToast({ title: e?.message || '设置失败', icon: 'none' });
  });
}

function removeAddr(addr) {
  uni.showModal({
    title: '确认删除',
    content: `删除"${addr.name} ${formatMobile(addr.mobile)}"`,
    confirmColor: '#E63946',
    success: ({ confirm }) => {
      if (!confirm) return;
      uni.showLoading({ title: '删除中', mask: true });
      request({ url: `/app-api/member/address/delete?id=${addr.id}`, method: 'DELETE' })
        .then(() => {
          uni.hideLoading();
          uni.showToast({ title: '已删除', icon: 'success' });
          load();
        })
        .catch(e => {
          uni.hideLoading();
          uni.showToast({ title: e?.message || '删除失败', icon: 'none' });
        });
    },
  });
}

function goBack() { uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/user-me/index' }) }); }

onMounted(load);
</script>

<style lang="scss" scoped>
@import '../../uni.scss';
.page { min-height: 100vh; background: $bg-page; }
.safe-top { padding-top: calc(env(safe-area-inset-top) + 16rpx); }
.topbar { display: flex; align-items: center; padding: 16rpx 32rpx; background: $bg-card; border-bottom: 1rpx solid $border-color; }
.topbar .back { font-size: 44rpx; color: $text-primary; padding-right: 16rpx; }
.topbar .title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; color: $text-primary; }

.empty-tip { text-align: center; padding: 80rpx 0; color: $text-placeholder; font-size: 26rpx; }
.empty-state { text-align: center; padding: 120rpx 60rpx; }
.empty-state .empty-emoji { font-size: 96rpx; margin-bottom: 24rpx; opacity: .5; }
.empty-state .empty-title { font-size: 32rpx; font-weight: 700; color: $text-primary; }
.empty-state .empty-sub { margin-top: 12rpx; font-size: 24rpx; color: $text-placeholder; }
.empty-state .empty-cta { margin-top: 40rpx; display: inline-block; padding: 20rpx 56rpx; background: $brand-primary; color: #fff; border-radius: 999rpx; font-size: 28rpx; font-weight: 600; }

.addr-card {
  margin: 24rpx 32rpx; padding: 32rpx;
  background: $bg-card; border-radius: $radius-lg;
  box-shadow: 0 4rpx 16rpx rgba(15,23,42,.04);
  border: 2rpx solid transparent;
}
.addr-card.default { border-color: $brand-primary-light; }
.addr-card .hdr { display: flex; align-items: center; gap: 16rpx; margin-bottom: 12rpx; }
.addr-card .name { font-size: 28rpx; font-weight: 700; color: $text-primary; }
.addr-card .mobile { font-size: 26rpx; color: $text-secondary; font-variant-numeric: tabular-nums; }
.addr-card .default-tag { font-size: 20rpx; color: $brand-primary; background: $brand-primary-light; padding: 4rpx 12rpx; border-radius: 8rpx; font-weight: 600; }
.addr-card .addr { font-size: 26rpx; color: $text-secondary; line-height: 1.5; margin-bottom: 16rpx; }
.addr-card .ops { display: flex; gap: 32rpx; padding-top: 16rpx; border-top: 1rpx dashed $border-color; }
.addr-card .ops .op { font-size: 24rpx; color: $text-secondary; }
.addr-card .ops .op.danger { color: $danger; margin-left: auto; }

.add-btn { margin: 32rpx; padding: 24rpx; background: $brand-primary; color: #fff; text-align: center; border-radius: $radius-md; font-size: 28rpx; font-weight: 700; }

.form-card { margin: 24rpx 32rpx; padding: 32rpx; background: $bg-card; border-radius: $radius-lg; box-shadow: 0 4rpx 16rpx rgba(15,23,42,.04); }
.form-card .form-title { font-size: 30rpx; font-weight: 700; color: $text-primary; margin-bottom: 24rpx; }
.form-row { display: flex; align-items: center; padding: 24rpx 0; border-bottom: 1rpx solid $border-color; }
.form-row.textarea { align-items: flex-start; }
.form-row .lbl { width: 160rpx; font-size: 26rpx; color: $text-secondary; flex-shrink: 0; }
.form-row .ipt { flex: 1; font-size: 28rpx; color: $text-primary; padding: 0; }
.form-row textarea.ipt { min-height: 120rpx; line-height: 1.5; }
.form-row .placeholder { color: $text-placeholder; }
.form-row .arrow { color: $text-placeholder; font-size: 32rpx; margin-left: 8rpx; }

.form-actions { display: flex; gap: 16rpx; margin-top: 32rpx; }
.form-actions .btn-cancel, .form-actions .btn-save { flex: 1; padding: 24rpx; text-align: center; border-radius: $radius-md; font-size: 28rpx; font-weight: 700; }
.form-actions .btn-cancel { background: $bg-page; color: $text-secondary; }
.form-actions .btn-save { background: $brand-primary; color: #fff; }

.bottom-space { height: 80rpx; }
</style>
