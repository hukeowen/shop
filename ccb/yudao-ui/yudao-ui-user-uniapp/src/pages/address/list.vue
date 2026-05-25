<template>
  <view class="page">
    <nav-bar title="收货地址" />
    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!list.length" icon="📍" title="还没有收货地址" desc="添加一个，结算更顺手" />
    <view v-else>
      <view v-for="a in list" :key="a.id" class="addr" @click="onSelect(a)">
        <view class="a-l">
          <view class="a-name">{{ a.name }} <text class="a-mob">{{ a.mobile }}</text></view>
          <view class="a-detail">{{ a.areaName }} {{ a.detailAddress }}</view>
          <view v-if="a.defaultStatus" class="a-default">默认</view>
        </view>
        <view class="a-edit" @click.stop="onEdit(a)">编辑</view>
      </view>
    </view>
    <view class="add-btn" @click="onAdd">+ 添加新地址</view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
// import { listAddresses } from '@/api/address.js';

const loading = ref(true);
const list = ref([]);
const inSelectMode = ref(false);

function onSelect(a) {
  if (inSelectMode.value) {
    uni.setStorageSync('selected-address', a);
    uni.navigateBack();
  }
}
function onEdit(a) { uni.showToast({ title: '编辑功能 WIP', icon: 'none' }); }
function onAdd() { uni.showToast({ title: '新增功能 WIP', icon: 'none' }); }

onMounted(async () => {
  const opts = (() => { try { const ps = getCurrentPages(); return ps[ps.length - 1]?.options || {}; } catch { return {}; } })();
  inSelectMode.value = opts.select === '1';
  loading.value = true;
  try { list.value = []; /* list.value = await listAddresses(); */ }
  finally { loading.value = false; }
});
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background: $bg-2; padding-bottom: 80px; }
.loading { padding: 40px; text-align: center; color: $t4; }
.addr { display: flex; gap: 12px; padding: 14px; background: #fff; margin: 10px 14px; border-radius: $r-md; box-shadow: $sh-1; }
.a-l { flex: 1; min-width: 0; }
.a-name { font-size: 15px; font-weight: 700; color: $t1; }
.a-mob { color: $t3; font-weight: 500; font-size: 13px; margin-left: 6px; }
.a-detail { font-size: 12px; color: $t3; margin-top: 4px; }
.a-default { display: inline-block; margin-top: 6px; padding: 2px 8px; background: $o; color: #fff; font-size: 10px; border-radius: 4px; font-weight: 700; }
.a-edit { padding: 4px 10px; color: $o; font-size: 12px; font-weight: 700; align-self: center; }
.add-btn { position: fixed; left: 14px; right: 14px; bottom: 14px; bottom: calc(14px + env(safe-area-inset-bottom)); padding: 14px; background: linear-gradient(135deg, $o, $o-d); color: #fff; text-align: center; border-radius: $r-pill; font-weight: 800; box-shadow: $sh-warm; }
</style>
