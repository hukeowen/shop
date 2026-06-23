<template>
  <view class="page">
    <nav-bar :title="isEdit ? '编辑收货地址' : '新增收货地址'" />
    <view class="card">
      <view class="row">
        <text class="label">收货人</text>
        <input class="inp" v-model="form.name" placeholder="收货人姓名" placeholder-class="ph" />
      </view>
      <view class="row">
        <text class="label">手机号</text>
        <input class="inp" v-model="form.mobile" type="number" maxlength="11" placeholder="收货人手机号" placeholder-class="ph" />
      </view>
      <view class="row">
        <text class="label">所在地区</text>
        <picker mode="multiSelector" :range="columns" :value="colIndex" @columnchange="onColumnChange" @change="onRegionConfirm">
          <view class="picker-val" :class="{ ph: !regionText }">{{ regionText || '请选择省 / 市 / 区' }}</view>
        </picker>
      </view>
      <view class="row">
        <text class="label">详细地址</text>
        <input class="inp" v-model="form.detailAddress" placeholder="街道 / 楼栋 / 门牌号" placeholder-class="ph" />
      </view>
      <view class="row">
        <text class="label">设为默认</text>
        <switch :checked="form.defaultStatus" color="#FF6B35" style="transform:scale(.9)" @change="(e) => (form.defaultStatus = e.detail.value)" />
      </view>
    </view>
    <view class="save-btn" :class="{ disabled: saving }" @click="onSave">{{ saving ? '保存中…' : '保存' }}</view>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { createAddress, updateAddress, getAreaTree } from '@/api/address.js';

const isEdit = ref(false);
const editId = ref(null);
const saving = ref(false);
const form = reactive({ name: '', mobile: '', areaId: null, detailAddress: '', defaultStatus: false });

// 省市区树 + 三列联动
let tree = [];
const columns = ref([[], [], []]);
const colIndex = ref([0, 0, 0]);
const regionText = ref('');

function rebuildColumns() {
  const provinces = tree;
  const p = provinces[colIndex.value[0]] || {};
  const cities = p.children || [];
  const c = cities[colIndex.value[1]] || {};
  const districts = c.children || [];
  columns.value = [
    provinces.map((x) => x.name),
    cities.map((x) => x.name),
    districts.map((x) => x.name),
  ];
}
async function loadTree() {
  try { tree = (await getAreaTree()) || []; rebuildColumns(); } catch {}
}
function onColumnChange(e) {
  const { column, value } = e.detail;
  const idx = [...colIndex.value];
  idx[column] = value;
  if (column === 0) { idx[1] = 0; idx[2] = 0; }
  else if (column === 1) { idx[2] = 0; }
  colIndex.value = idx;
  rebuildColumns();
}
function onRegionConfirm(e) {
  const idx = e.detail.value;
  colIndex.value = idx;
  const p = tree[idx[0]] || {};
  const c = (p.children || [])[idx[1]] || {};
  const d = (c.children || [])[idx[2]] || {};
  regionText.value = [p.name, c.name, d.name].filter(Boolean).join(' ');
  form.areaId = d.id || c.id || p.id || null;
}

onLoad((q) => {
  loadTree();
  if (q && q.id) {
    isEdit.value = true;
    editId.value = Number(q.id);
    try {
      const a = uni.getStorageSync('editing-address');
      if (a && String(a.id) === String(q.id)) {
        form.name = a.name || '';
        form.mobile = a.mobile || '';
        form.detailAddress = a.detailAddress || '';
        form.defaultStatus = !!a.defaultStatus;
        form.areaId = a.areaId || null;
        regionText.value = a.areaName || '';
      }
    } catch {}
  }
});

async function onSave() {
  if (saving.value) return;
  if (!form.name.trim()) return uni.showToast({ title: '请填写收货人', icon: 'none' });
  if (!/^1\d{10}$/.test(form.mobile)) return uni.showToast({ title: '手机号格式不正确', icon: 'none' });
  if (!form.areaId) return uni.showToast({ title: '请选择所在地区', icon: 'none' });
  if (!form.detailAddress.trim()) return uni.showToast({ title: '请填写详细地址', icon: 'none' });
  saving.value = true;
  try {
    const body = {
      name: form.name.trim(),
      mobile: form.mobile,
      areaId: form.areaId,
      detailAddress: form.detailAddress.trim(),
      defaultStatus: !!form.defaultStatus,
    };
    if (isEdit.value) { body.id = editId.value; await updateAddress(body); }
    else await createAddress(body);
    uni.showToast({ title: '已保存', icon: 'success' });
    setTimeout(() => uni.navigateBack(), 600);
  } catch (err) {
    uni.showToast({ title: err?.message || '保存失败', icon: 'none' });
  } finally {
    saving.value = false;
  }
}
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background: $bg-2; }
.card { margin: 12px 14px 0; background: #fff; border-radius: $r-lg; box-shadow: $sh-1; overflow: hidden; }
.row { display: flex; align-items: center; padding: 14px 16px; border-bottom: 1px solid $line; min-height: 26px; }
.row:last-child { border-bottom: none; }
.row .label { width: 76px; font-size: 14px; color: $t1; font-weight: 600; flex-shrink: 0; }
.inp { flex: 1; font-size: 14px; color: $t1; }
.ph { color: $t4; }
.picker-val { flex: 1; font-size: 14px; color: $t1; }
.picker-val.ph { color: $t4; }
.save-btn { margin: 16px 14px; height: 46px; border-radius: $r-pill; background: linear-gradient(135deg, $o, $o-d); color: #fff; font-size: 15px; font-weight: 800; display: flex; align-items: center; justify-content: center; box-shadow: $sh-warm; }
.save-btn.disabled { opacity: .6; }
</style>
