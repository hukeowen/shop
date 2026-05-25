<template>
  <view class="page">
    <nav-bar title="购物车" />
    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!items.length" icon="🛒" title="购物车空空" desc="逛逛去添点心仪好物" />
    <view v-else>
      <view v-for="g in groupedByShop" :key="g.shopId" class="shop-group">
        <view class="shop-head"><text class="shop-pic">{{ g.shopName?.[0] || '店' }}</text><text class="shop-name">{{ g.shopName }}</text></view>
        <view v-for="it in g.items" :key="it.id" class="item">
          <view class="chk" :class="{ on: it.selected }" @click="it.selected = !it.selected">✓</view>
          <view class="pic">{{ it.em || '🛍' }}</view>
          <view class="body">
            <view class="name">{{ it.spuName }}</view>
            <view class="row">
              <text class="price">¥{{ it.price }}</text>
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
// import { listCart, updateCartCount } from '@/api/cart.js';

const loading = ref(true);
const items = ref([]);

const groupedByShop = computed(() => {
  const m = new Map();
  for (const it of items.value) {
    if (!m.has(it.shopId)) m.set(it.shopId, { shopId: it.shopId, shopName: it.shopName, items: [] });
    m.get(it.shopId).items.push(it);
  }
  return [...m.values()];
});
const allSelected = computed(() => items.value.length && items.value.every((i) => i.selected));
const selectedCount = computed(() => items.value.filter((i) => i.selected).reduce((s, i) => s + i.count, 0));
const totalYuan = computed(() => (items.value.filter((i) => i.selected).reduce((s, i) => s + i.price * 100 * i.count, 0) / 100).toFixed(2));

function inc(it) { it.count += 1; }
function dec(it) { if (it.count > 1) it.count -= 1; }
function toggleAll() { const v = !allSelected.value; items.value.forEach((i) => (i.selected = v)); }
function goCheckout() {
  if (!selectedCount.value) return uni.showToast({ title: '请选择商品', icon: 'none' });
  uni.navigateTo({ url: '/pages/checkout/index' });
}

onMounted(async () => {
  loading.value = true;
  try { items.value = []; /* items.value = await listCart(); */ }
  finally { loading.value = false; }
});
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
.chk { width: 22px; height: 22px; border-radius: 50%; border: 1.5px solid $line-d; display: flex; align-items: center; justify-content: center; color: transparent; font-size: 12px; font-weight: 800; }
.chk.on { background: $o; border-color: $o; color: #fff; }
.pic { width: 60px; height: 60px; border-radius: 10px; background: $o-50; color: $o; display: flex; align-items: center; justify-content: center; font-size: 28px; }
.body { flex: 1; min-width: 0; }
.name { font-size: 13px; font-weight: 700; color: $t1; }
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
