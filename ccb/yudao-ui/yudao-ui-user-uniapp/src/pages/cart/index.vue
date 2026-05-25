<template>
  <view class="page">
    <view class="topbar">
      <text class="back" @click="goBack">‹</text>
      <text class="title">购物车（{{ items.length }}）</text>
      <text class="right">管理</text>
    </view>

    <view v-if="loading" class="loading">加载中…</view>
    <empty-state v-else-if="!items.length" icon="🛒" title="购物车空空" desc="逛逛去添点心仪好物" />
    <view v-else>
      <view v-for="g in groupedByShop" :key="g.shopId" class="cart-shop">
        <view class="cart-shop-head">
          <view class="cart-shop-check" :class="{ on: g.allSelected }" @click="toggleShop(g)"></view>
          <view class="cart-shop-pic" :class="g.picTone">{{ g.shopName?.[0] || '店' }}</view>
          <text class="cart-shop-name">{{ g.shopName || '店铺' }}</text>
          <view v-if="g.promo" class="cart-shop-promo">{{ g.promo }}</view>
        </view>
        <view v-for="it in g.items" :key="it.id" class="cart-i">
          <view class="check" :class="{ on: it.selected }" @click="toggleItem(it)"></view>
          <view class="cart-pic" :class="picTone(it)">
            <image v-if="it.picUrl" :src="it.picUrl" mode="aspectFill" class="pic-img" />
            <text v-else>🛍</text>
          </view>
          <view class="cart-info">
            <view class="cart-name">{{ it.spuName }}</view>
            <view v-if="it.skuName" class="cart-spec">{{ it.skuName }}</view>
            <view class="cart-row3">
              <view class="cart-pr">¥{{ fen2yuan(it.price, false) }}</view>
              <view class="cart-num">
                <view class="num-btn" @click="dec(it)">−</view>
                <text class="n">{{ it.count }}</text>
                <view class="num-btn" @click="inc(it)">+</view>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 推 N 反 1 推进提示卡（如有匹配） -->
      <view v-if="queueProgress" class="home-queue-tip">
        <view class="hqt-ic">🔥</view>
        <view class="hqt-body">
          <view class="hqt-t">{{ queueProgress.shopName }} · <text class="b">本单 +1 步推进队列 {{ queueProgress.cur }} → {{ queueProgress.cur + 1 }}</text></view>
          <view class="hqt-d">{{ queueProgress.desc }}</view>
        </view>
      </view>

      <view style="height: 12px;"></view>
    </view>

    <view v-if="items.length" class="cart-bot">
      <view class="all-check" :class="{ on: allSelected }" @click="toggleAll"></view>
      <text class="all-lbl">全选</text>
      <view class="total">
        <view class="t">合计</view>
        <view class="v">¥{{ totalYuan }}</view>
      </view>
      <view class="checkout" @click="goCheckout">去结算（{{ selectedCount }}）</view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { listCart, updateCartCount, updateCartSelected } from '@/api/cart.js';
import { listMyQueues } from '@/api/promo.js';
import { fen2yuan } from '@/utils/format.js';

const loading = ref(true);
const items = ref([]);
const queues = ref([]);

function picTone(it) {
  const hash = (it.spuName || '').charCodeAt(0) || 0;
  return ['', 'green', 'cream'][hash % 3];
}

const groupedByShop = computed(() => {
  const m = new Map();
  for (const it of items.value) {
    const sid = it.shopId || it.tenantId || 0;
    if (!m.has(sid)) {
      m.set(sid, {
        shopId: sid,
        shopName: it.shopName,
        items: [],
        picTone: ['', 'alt-1', 'alt-2'][Math.abs(sid) % 3],
        promo: it.shopPromo || '',
      });
    }
    m.get(sid).items.push(it);
  }
  return [...m.values()].map((g) => ({ ...g, allSelected: g.items.every((i) => i.selected) }));
});
const allSelected = computed(() => items.value.length && items.value.every((i) => i.selected));
const selectedItems = computed(() => items.value.filter((i) => i.selected));
const selectedCount = computed(() => selectedItems.value.reduce((s, i) => s + i.count, 0));
const totalYuan = computed(() => fen2yuan(selectedItems.value.reduce((s, i) => s + (i.price * i.count), 0), false));

const queueProgress = computed(() => {
  if (!selectedItems.value.length || !queues.value.length) return null;
  // 匹配第一个 selectedItem 对应的 queue
  for (const it of selectedItems.value) {
    const q = queues.value.find((x) => x.spuId === it.spuId && x.tenantId === it.tenantId);
    if (q) {
      const cur = q.currentCount || 0;
      const n = q.requiredCount || q.tuijianN || 1;
      return {
        shopName: q.shopName || it.shopName,
        cur,
        desc: cur + 1 >= n
          ? `第 ${n} 件出队，立即返奖 +¥${fen2yuan(q.rewardAmount || 0, false)}`
          : `还差 ${n - cur - 1} 件即出队 · 全额返`,
      };
    }
  }
  return null;
});

async function inc(it) {
  it.count += 1;
  try { await updateCartCount(it.id, it.count); } catch { it.count -= 1; }
}
async function dec(it) {
  if (it.count <= 1) return;
  it.count -= 1;
  try { await updateCartCount(it.id, it.count); } catch { it.count += 1; }
}
async function toggleItem(it) {
  it.selected = !it.selected;
  try { await updateCartSelected([it.id], it.selected); } catch { it.selected = !it.selected; }
}
async function toggleShop(g) {
  const v = !g.allSelected;
  g.items.forEach((i) => (i.selected = v));
  try { await updateCartSelected(g.items.map((i) => i.id), v); } catch {}
}
async function toggleAll() {
  const v = !allSelected.value;
  items.value.forEach((i) => (i.selected = v));
  try { await updateCartSelected(items.value.map((i) => i.id), v); } catch {}
}

function goBack() {
  const ps = getCurrentPages();
  if (ps.length > 1) uni.navigateBack();
  else uni.reLaunch({ url: '/pages/index/index' });
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
    items.value = (r?.validList || r?.list || r || []).map((i) => ({
      id: i.id,
      skuId: i.skuId,
      spuId: i.spuId,
      spuName: i.spuName,
      skuName: i.properties?.map((p) => p.valueName).join(' / ') || '',
      picUrl: i.picUrl,
      price: i.price,
      count: i.count,
      selected: !!i.selected,
      shopId: i.shopId || i.tenantId,
      shopName: i.shopName || i.merchantName,
      shopPromo: i.shopPromo || i.tuijianN ? `推 ${i.tuijianN || ''} 反 1` : '',
      tenantId: i.tenantId,
    }));
  } catch { items.value = []; }
  finally { loading.value = false; }
  try { queues.value = await listMyQueues() || []; } catch {}
}
onMounted(load);
onShow(load);
</script>

<style lang="scss" scoped>
@import '@/uni.scss';

.page { min-height: 100vh; background: $bg-2; padding-bottom: 90px; }

/* topbar */
.topbar {
  display: flex; align-items: center; padding: 12px 14px;
  background: $card; border-bottom: 1px solid $line;
}
.topbar .back { font-size: 22px; color: $t1; padding: 4px 10px; line-height: 1; }
.topbar .title { flex: 1; text-align: center; font-size: 16px; font-weight: 700; color: $t1; }
.topbar .right { font-size: 13px; color: $o-d; padding: 4px 10px; font-weight: 600; }

.loading { padding: 40px; text-align: center; color: $t4; }

/* 店铺组 */
.cart-shop {
  margin: 12px 14px 0;
  background: $card;
  border: 1px solid $line;
  border-radius: $r-lg;
  overflow: hidden;
}
.cart-shop-head {
  display: flex; align-items: center; gap: 8px;
  padding: 12px 14px;
  border-bottom: 1px dashed $line;
  background: linear-gradient(90deg, $o-50, transparent);
}
.cart-shop-check {
  width: 20px; height: 20px; border-radius: 99px;
  border: 1.5px solid $line-d;
  display: flex; align-items: center; justify-content: center;
  color: transparent; font-size: 11px; font-weight: 900;
  flex-shrink: 0;
}
.cart-shop-check.on { background: $o; border-color: $o; color: #fff; }
.cart-shop-check.on::after { content: '✓'; font-size: 11px; }
.cart-shop-pic {
  width: 24px; height: 24px; border-radius: 6px;
  background: linear-gradient(135deg, #FFD1BA, $o);
  color: #fff; display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 800;
}
.cart-shop-pic.alt-1 { background: linear-gradient(135deg, #C9E0FF, #6196F0); }
.cart-shop-pic.alt-2 { background: linear-gradient(135deg, #D3F4D3, #4CB84C); }
.cart-shop-name { font-size: 13px; font-weight: 800; color: $t1; flex: 1; }
.cart-shop-promo {
  font-size: 10px; color: $o-d; font-weight: 700;
  padding: 2px 7px; border-radius: 4px;
  background: $o-50; border: 1px solid $o-100;
}

.cart-i {
  display: flex; align-items: center; gap: 10px;
  padding: 12px 14px;
  border-bottom: 1px solid $line;
}
.cart-i:last-child { border-bottom: 0; }
.check {
  width: 20px; height: 20px; border-radius: 99px;
  border: 1.5px solid $line-d;
  display: flex; align-items: center; justify-content: center;
  color: transparent; font-size: 11px; font-weight: 900;
  flex-shrink: 0;
}
.check.on { background: $o; border-color: $o; color: #fff; }
.check.on::after { content: '✓'; font-size: 11px; }
.cart-pic {
  width: 64px; height: 64px; border-radius: 10px;
  background: linear-gradient(135deg, $o-100, $o-l);
  display: flex; align-items: center; justify-content: center;
  font-size: 30px; flex-shrink: 0; overflow: hidden;
}
.cart-pic.green { background: linear-gradient(135deg, #D1FAE5, #6EE7B7); }
.cart-pic.cream { background: linear-gradient(135deg, #FEF3C7, #FCD34D); }
.pic-img { width: 100%; height: 100%; }
.cart-info { flex: 1; min-width: 0; }
.cart-name { font-size: 13px; font-weight: 700; color: $t1; line-height: 1.3; overflow: hidden; }
.cart-spec {
  font-size: 11px; color: $t3; margin-top: 4px;
  background: $bg-2; padding: 2px 8px; border-radius: 4px;
  display: inline-block;
}
.cart-row3 {
  display: flex; justify-content: space-between; align-items: center;
  margin-top: 8px;
}
.cart-pr { font-size: 16px; font-weight: 900; color: $o-d; }
.cart-num { display: flex; align-items: center; gap: 6px; }
.num-btn {
  width: 24px; height: 24px; border-radius: 6px;
  background: $bg-2; color: $t1;
  display: flex; align-items: center; justify-content: center;
  border: 1px solid $line; font-size: 13px;
}
.cart-num .n { width: 28px; text-align: center; font-size: 13px; font-weight: 800; }

/* 推 N 反 1 提醒 */
.home-queue-tip {
  margin: 14px;
  padding: 14px 16px;
  border-radius: $r-lg;
  background: linear-gradient(135deg, #FFF8F4 0%, #FFEFE3 100%);
  border: 1px solid $o-100;
  display: flex; align-items: center; gap: 12px;
}
.hqt-ic {
  width: 40px; height: 40px; border-radius: 12px;
  background: linear-gradient(135deg, $o, $o-d);
  color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px; flex-shrink: 0;
  box-shadow: $sh-warm;
}
.hqt-body { flex: 1; }
.hqt-t { font-size: 13px; font-weight: 800; color: $t1; }
.hqt-t .b { color: $o-d; }
.hqt-d { font-size: 11px; color: $t3; margin-top: 2px; }

/* 底部结算 bar */
.cart-bot {
  position: fixed; bottom: 0; left: 0; right: 0;
  padding: 12px 14px 18px;
  padding-bottom: calc(18px + env(safe-area-inset-bottom));
  background: rgba(255,255,255,.96);
  backdrop-filter: blur(20px);
  border-top: 1px solid $line;
  display: flex; align-items: center; gap: 10px;
  z-index: 50;
}
.all-check {
  width: 22px; height: 22px; border-radius: 99px;
  border: 1.5px solid $line-d;
  display: flex; align-items: center; justify-content: center;
  color: transparent; font-size: 12px; font-weight: 900;
  flex-shrink: 0;
}
.all-check.on { background: $o; border-color: $o; color: #fff; }
.all-check.on::after { content: '✓'; font-size: 12px; }
.all-lbl { font-size: 13px; color: $t1; font-weight: 600; }
.cart-bot .total { flex: 1; text-align: right; }
.cart-bot .total .t { font-size: 11px; color: $t3; }
.cart-bot .total .v {
  font-size: 22px; font-weight: 900; color: $o-d;
  line-height: 1;
}
.checkout {
  height: 44px; padding: 0 22px; border-radius: 99px;
  background: linear-gradient(135deg, $o, $o-d);
  color: #fff; font-size: 14px; font-weight: 800;
  display: flex; align-items: center;
  box-shadow: $sh-warm;
}
</style>
