<template>
  <view class="page">
    <nav-bar title="分类" />
    <view class="layout">
      <scroll-view scroll-y class="side">
        <view v-for="c in cats" :key="c.id" class="side-item" :class="{ on: active === c.id }" @click="selectCat(c.id)">
          {{ c.name }}
        </view>
      </scroll-view>
      <scroll-view scroll-y class="main">
        <view v-if="loading" class="loading">加载中…</view>
        <view v-else-if="!products.length" class="empty-wrap">
          <empty-state title="该分类暂无商品" />
        </view>
        <view v-else>
          <view v-for="p in products" :key="p.id" class="prod" @click="goProduct(p)">
            <view class="prod-pic">
              <image v-if="p.picUrl" :src="p.picUrl" mode="aspectFill" class="pic-img" />
              <text v-else>🛍</text>
            </view>
            <view class="prod-body">
              <view class="prod-name">{{ p.name }}</view>
              <view class="prod-price">¥{{ fen2yuan(p.price, false) }}</view>
            </view>
          </view>
        </view>
      </scroll-view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { listCategories, pageSpu } from '@/api/product.js';
import { fen2yuan } from '@/utils/format.js';

const cats = ref([]);
const active = ref(0);
const products = ref([]);
const loading = ref(false);

async function selectCat(id) {
  active.value = id;
  loading.value = true;
  try {
    const r = await pageSpu({ pageNo: 1, pageSize: 30, categoryId: id });
    products.value = r?.list || [];
  } catch { products.value = []; }
  finally { loading.value = false; }
}
function goProduct(p) { uni.navigateTo({ url: `/pages/product/detail?id=${p.id}&tenantId=${p.tenantId || ''}` }); }

onMounted(async () => {
  try {
    const list = await listCategories();
    cats.value = (list || []).map((c) => ({ id: c.id, name: c.name }));
  } catch {}
  if (cats.value.length) {
    const o = (() => { try { const ps = getCurrentPages(); return ps[ps.length - 1]?.options || {}; } catch { return {}; } })();
    const initId = o.k ? Number(o.k) : cats.value[0].id;
    if (initId) selectCat(initId);
  }
});
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background: $bg; display: flex; flex-direction: column; }
.layout { flex: 1; display: flex; overflow: hidden; }
.side { width: 92px; background: #F6F7F9; }
.side-item { padding: 14px 8px; text-align: center; font-size: 13px; color: $t2; }
.side-item.on { background: #fff; color: $o; font-weight: 800; }
.main { flex: 1; padding: 12px; }
.loading { padding: 40px; text-align: center; color: $t4; }
.empty-wrap { padding-top: 40px; }
.prod { display: flex; gap: 10px; padding: 10px; background: #fff; border-radius: $r-md; margin-bottom: 8px; box-shadow: $sh-1; }
.prod-pic { width: 60px; height: 60px; border-radius: 10px; background: $o-50; color: $o; display: flex; align-items: center; justify-content: center; font-size: 26px; overflow: hidden; flex-shrink: 0; }
.pic-img { width: 100%; height: 100%; }
.prod-body { flex: 1; min-width: 0; }
.prod-name { font-size: 13px; font-weight: 700; color: $t1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.prod-price { font-size: 14px; color: $o; font-weight: 800; margin-top: 6px; }
</style>
