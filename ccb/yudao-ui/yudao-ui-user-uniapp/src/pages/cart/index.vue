<template>
  <view class="page">
    <nav-bar title="购物车" />
    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!items.length" icon="🛒" title="购物车空空" desc="逛逛去添点心仪好物" />
    <view v-else>
      <view v-for="g in groupedByShop" :key="g.shopId" class="shop-group">
        <view class="shop-head"><text class="shop-pic">{{ g.shopName?.[0] || '店' }}</text><text class="shop-name">{{ g.shopName || '店铺' }}</text></view>
        <view v-for="it in g.items" :key="it.id" class="item">
          <view class="chk" :class="{ on: it.selected }" @click="onToggleSelect(it)">✓</view>
          <view class="pic">
            <image v-if="it.picUrl" :src="it.picUrl" mode="aspectFill" class="pic-img" />
            <text v-else>🛍</text>
          </view>
          <view class="body">
            <view class="name">{{ it.spuName }}</view>
            <view v-if="it.skuName" class="sku">{{ it.skuName }}</view>
            <view class="row">
              <text class="price">¥{{ fen2yuan(it.price, false) }}</text>
              <view class="qty">
                <view class="qb" @click="dec(it)">−</view>
                <text class="qn">{{ it.count }}</text>
                <view class="qb" @click="inc(it)">+</view>
              </view>
            </view>
          </view>
        </view>
      </view>
    </view>
    <view v-if="items.length" class="footer">
      <view class="all" :class="{ on: allSelected }" @click="toggleAll">✓</view>
      <view class="total">合计 <text class="amt">¥{{ totalYuan }}</text></view>
      <view class="go" @click="goCheckout">结算（{{ selectedCount }}）</view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { listCart, updateCartCount, updateCartSelected, deleteCart } from '@/api/cart.js';
import { fen2yuan } from '@/utils/format.js';

const loading = ref(true);
const items = ref([]);

const groupedByShop = computed(() => {
  const m = new Map();
  for (const it of items.value) {
    const sid = it.shopId || it.tenantId || 0;
    if (!m.has(sid)) m.set(sid, { shopId: sid, shopName: it.shopName, items: [] });
    m.get(sid).items.push(it);
  }
  return [...m.values()];
});
const allSelected = computed(() => items.value.length && items.value.every((i) => i.selected));
const selectedItems = computed(() => items.value.filter((i) => i.selected));
const selectedCount = computed(() => selectedItems.value.reduce((s, i) => s + i.count, 0));
const totalYuan = computed(() => fen2yuan(selectedItems.value.reduce((s, i) => s + (i.price * i.count), 0), false));

async function inc(it) {
  it.count += 1;
  try { await updateCartCount(it.id, it.count); } catch { it.count -= 1; }
}
async function dec(it) {
  if (it.count <= 1) return;
  it.count -= 1;
  try { await updateCartCount(it.id, it.count); } catch { it.count += 1; }
}
async function onToggleSelect(it) {
  it.selected = !it.selected;
  try { await updateCartSelected([it.id], it.selected); } catch { it.selected = !it.selected; }
}
async function toggleAll() {
  const v = !allSelected.value;
  items.value.forEach((i) => (i.selected = v));
  try { await updateCartSelected(items.value.map((i) => i.id), v); } catch {}
}
function goCheckout() {
  if (!selectedCount.value) return uni.showToast({ title: '请选择商品', icon: 'none' });
  const skus = selectedItems.value.map((i) => `${i.skuId}:${i.count}`).join(',');
  const shopId = selectedItems.value[0]?.shopId || selectedItems.value[0]?.tenantId || '';
  uni.navigateTo({ url: `/pages/checkout/index?type=cart&skus=${skus}&tenantId=${shopId}` });
}

async function load() {
  loading.value = true;
  try {
    const r = await listCart();
    // listCart 返回可能是 { validList, invalidList } 或扁平 list
    items.value = (r?.validList || r?.list || r || []).map((i) => ({
      id: i.id,
      skuId: i.skuId,
      spuName: i.spuName,
      skuName: i.properties?.map((p) => p.valueName).join(' / ') || '',
      picUrl: i.picUrl,
      price: i.price,
      count: i.count,
      selected: !!i.selected,
      shopId: i.shopId || i.tenantId,
      shopName: i.shopName || i.merchantName,
      tenantId: i.tenantId,
    }));
  } catch { items.value = []; }
  finally { loading.value = false; }
}
onMounted(load);
onShow(load);
</script>

<style lang="scss" scoped>
@import '@/uni.scss';
.page { min-height: 100vh; background: $bg-2; padding-bottom: 80px; }
.loading { padding: 40px; text-align: center; color: $t4; }
.shop-group { background: #fff; margin: 10px 14px; border-radius: $r-md; overflow: hidden; box-shadow: $sh-1; }
.shop-head { display: flex; align-items: center; gap: 8px; padding: 12px; border-bottom: 1px solid $line; }
.shop-pic { width: 24px; height: 24px; border-radius: 6px; background: $o; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 800; }
.shop-name { font-size: 14px; font-weight: 700; color: $t1; }
.item { display: flex; gap: 10px; padding: 12px; align-items: center; }
.chk { width: 22px; height: 22px; border-radius: 50%; border: 1.5px solid $line-d; display: flex; align-items: center; justify-content: center; color: transparent; font-size: 12px; font-weight: 800; flex-shrink: 0; }
.chk.on { background: $o; border-color: $o; color: #fff; }
.pic { width: 60px; height: 60px; border-radius: 10px; background: $o-50; color: $o; display: flex; align-items: center; justify-content: center; font-size: 28px; overflow: hidden; flex-shrink: 0; }
.pic-img { width: 100%; height: 100%; }
.body { flex: 1; min-width: 0; }
.name { font-size: 13px; font-weight: 700; color: $t1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sku { font-size: 11px; color: $t4; margin-top: 2px; }
.row { display: flex; justify-content: space-between; align-items: center; margin-top: 8px; }
.price { font-size: 16px; font-weight: 800; color: $o; }
.qty { display: flex; align-items: center; border: 1px solid $line-d; border-radius: $r-pill; overflow: hidden; }
.qb { width: 28px; height: 26px; display: flex; align-items: center; justify-content: center; color: $t2; font-size: 16px; }
.qn { width: 32px; text-align: center; font-size: 13px; font-weight: 700; color: $t1; }
.footer { position: fixed; left: 0; right: 0; bottom: 0; display: flex; align-items: center; gap: 12px; padding: 10px 14px; padding-bottom: calc(10px + env(safe-area-inset-bottom)); background: #fff; border-top: 1px solid $line; }
.all { width: 22px; height: 22px; border-radius: 50%; border: 1.5px solid $line-d; display: flex; align-items: center; justify-content: center; color: transparent; font-size: 12px; font-weight: 800; }
.all.on { background: $o; border-color: $o; color: #fff; }
.total { flex: 1; font-size: 13px; color: $t2; }
.total .amt { color: $o; font-weight: 900; font-size: 18px; margin-left: 4px; }
.go { padding: 12px 24px; background: linear-gradient(135deg, $o, $o-d); color: #fff; border-radius: $r-pill; font-weight: 800; font-size: 14px; box-shadow: $sh-warm; }
</style>
