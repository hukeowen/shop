<template>
  <view class="page">
    <nav-bar title="搜索" />
    <view class="search-bar">
      <view class="sb-input">
        <text class="ic">🔍</text>
        <input v-model="kw" placeholder="搜店铺、找商品" confirm-type="search" @confirm="doSearch" />
        <text v-if="kw" class="clear" @click="kw = ''">×</text>
      </view>
      <view class="sb-go" @click="doSearch">搜索</view>
    </view>
    <view v-if="!searched" class="hist">
      <view v-if="history.length" class="hist-title">
        <text>历史搜索</text>
        <text class="clear-hist" @click="clearHist">清空</text>
      </view>
      <view v-if="history.length" class="hist-tags">
        <view v-for="(h, i) in history" :key="i" class="hist-tag" @click="useHist(h)">{{ h }}</view>
      </view>
      <view class="hist-title"><text>热门搜索</text></view>
      <view class="hist-tags">
        <view v-for="(h, i) in hot" :key="i" class="hist-tag hot" @click="useHist(h)">🔥 {{ h }}</view>
      </view>
    </view>
    <view v-else-if="loading" class="loading">搜索中…</view>
    <empty-state v-else-if="!results.length" title="没找到相关结果" desc="换个关键词试试" />
    <view v-else class="results">
      <view v-for="r in results" :key="r.id" class="r" @click="goProduct(r)">
        <view class="r-pic">
          <image v-if="r.picUrl" :src="r.picUrl" mode="aspectFill" class="r-pic-img" />
          <text v-else>🛍</text>
        </view>
        <view class="r-body">
          <view class="r-name">{{ r.name }}</view>
          <view class="r-shop">{{ r.shopName || r.tenantName || '' }}</view>
        </view>
        <view class="r-price">¥{{ fen2yuan(r.price, false) }}</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { pageSpu } from '@/api/product.js';
import { fen2yuan } from '@/utils/format.js';

const kw = ref('');
const searched = ref(false);
const loading = ref(false);
const results = ref([]);
const HIST_KEY = 'kexiaoer-search-history';
const history = ref([]);
const hot = ref(['烤地瓜', '阳光玫瑰', '老白茶', '邀 N 累积']);

function clearHist() { history.value = []; uni.setStorageSync(HIST_KEY, []); }
function useHist(h) { kw.value = h; doSearch(); }
async function doSearch() {
  const k = kw.value.trim();
  if (!k) return;
  searched.value = true;
  loading.value = true;
  try {
    const r = await pageSpu({ pageNo: 1, pageSize: 30, keyword: k });
    results.value = r?.list || [];
    const s = new Set([k, ...history.value]);
    history.value = [...s].slice(0, 10);
    uni.setStorageSync(HIST_KEY, history.value);
  } catch { results.value = []; }
  finally { loading.value = false; }
}
function goProduct(r) { uni.navigateTo({ url: `/pages/product/detail?id=${r.id}&tenantId=${r.tenantId || ''}` }); }
onMounted(() => { history.value = uni.getStorageSync(HIST_KEY) || []; });
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background: $bg; }
.search-bar { display: flex; gap: 8px; padding: 8px 14px; align-items: center; background: #fff; }
.sb-input { flex: 1; display: flex; gap: 8px; align-items: center; background: $bg-2; border-radius: $r-pill; padding: 8px 14px; }
.sb-input .ic { color: $o; }
.sb-input input { flex: 1; font-size: 13px; }
.clear { color: $t4; padding: 0 4px; }
.sb-go { color: $o; font-weight: 800; font-size: 14px; padding: 0 4px; }
.hist { padding: 14px; }
.hist-title { display: flex; justify-content: space-between; font-size: 13px; font-weight: 700; color: $t1; padding: 14px 0 8px; }
.clear-hist { color: $o; font-size: 12px; font-weight: 600; }
.hist-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.hist-tag { padding: 6px 14px; background: #fff; border-radius: $r-pill; font-size: 12px; color: $t2; box-shadow: $sh-1; }
.hist-tag.hot { color: $o; }
.loading { padding: 40px; text-align: center; color: $t4; }
.results { padding: 10px 14px; }
.r { display: flex; gap: 10px; padding: 10px; background: #fff; border-radius: $r-md; margin-bottom: 8px; box-shadow: $sh-1; align-items: center; }
.r-pic { width: 48px; height: 48px; border-radius: 10px; background: $o-50; color: $o; display: flex; align-items: center; justify-content: center; font-size: 22px; overflow: hidden; flex-shrink: 0; }
.r-pic-img { width: 100%; height: 100%; }
.r-body { flex: 1; min-width: 0; }
.r-name { font-size: 13px; font-weight: 700; color: $t1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.r-shop { font-size: 11px; color: $t3; margin-top: 2px; }
.r-price { font-size: 16px; font-weight: 800; color: $o; flex-shrink: 0; }
</style>
