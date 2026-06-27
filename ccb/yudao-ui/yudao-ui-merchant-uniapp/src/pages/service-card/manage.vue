<template>
  <view class="page">
    <view class="intro card">
      <view class="intro-t">🪪 服务卡包（套餐）</view>
      <view class="intro-d">
        把<text class="hl">已有的单项服务商品</text>（如 洗车 ¥58、保养 ¥588）打包成一个<text class="hl">套餐商品</text>出售。
        先在「商品管理」建好套餐商品（如「汽车养护套餐」），在这里选它 → 添加包含的服务 → 设有效期/次数。
        用户买套餐后自动获得每项服务卡，到店核销。
      </view>
    </view>

    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="!products.length" class="empty">
      还没有商品。请先到「商品管理」上架商品（套餐商品 + 各单项服务商品），再回来配置。
    </view>

    <view v-else class="list">
      <view v-for="p in products" :key="p.id" class="svc-prod card" :class="{ open: openId === p.id }">
        <view class="prod-head" @click="toggle(p)">
          <image v-if="p.picUrl" :src="p.picUrl" class="prod-pic" mode="aspectFill" />
          <view v-else class="prod-pic ph">🛍</view>
          <view class="prod-body">
            <view class="prod-name">{{ p.name }}</view>
            <view class="prod-meta">
              <text class="prod-price">¥{{ yuan(p.price) }}</text>
              <text v-if="p.cardCount > 0" class="badge on">套餐含 {{ p.cardCount }} 项服务</text>
              <text v-else class="badge off">未设为套餐</text>
              <text v-if="p.status === 1" class="badge down">已下架</text>
            </view>
          </view>
          <text class="arrow">{{ openId === p.id ? '▾' : '›' }}</text>
        </view>

        <view v-if="openId === p.id" class="prod-cfg">
          <view class="cfg-tip">「{{ p.name }}」包含的服务（从已有商品选）：</view>

          <view v-for="(c, i) in cards" :key="i" class="svc-card">
            <view class="svc-card-head">
              <view class="svc-item">
                <image v-if="c.picUrl" :src="c.picUrl" class="svc-item-pic" mode="aspectFill" />
                <view v-else class="svc-item-pic ph">🛍</view>
                <view class="svc-item-info">
                  <text class="svc-item-name">{{ c.name || '未选择商品' }}</text>
                  <text v-if="c.price != null" class="svc-item-price">原价 ¥{{ yuan(c.price) }}</text>
                </view>
              </view>
              <text class="svc-del" @click="removeCard(i)">删除</text>
            </view>

            <view class="svc-change" @click="openPicker(i)">{{ c.itemSpuId ? '更换商品' : '选择商品' }} ›</view>

            <text class="label-v">有效期（从用户付款日起算）</text>
            <view class="valid-row">
              <input class="input compact num" type="number" v-model="c.validValue" placeholder="1" />
              <view class="unit-seg">
                <view v-for="u in unitOptions" :key="u.v" class="unit-item"
                      :class="{ active: c.validUnit === u.v }" @click="c.validUnit = u.v">{{ u.label }}</view>
              </view>
            </view>

            <text class="label-v">核销次数</text>
            <view class="radio-row">
              <view class="radio-big" :class="{ active: !c.limited }" @click="c.limited = false">不限次数</view>
              <view class="radio-big" :class="{ active: c.limited }" @click="c.limited = true">限定次数</view>
            </view>
            <input v-if="c.limited" class="input compact" type="number" v-model="c.maxCount" placeholder="如 10（次数用完即失效）" />
            <text class="hint">时间到期 或 次数用完，谁先到都不能再用。</text>

            <input class="input" v-model="c.description" placeholder="使用须知（选填）" />
          </view>

          <view class="svc-add" @click="openPicker(-1)">＋ 添加一项服务（选商品）</view>
          <view class="cfg-actions">
            <view class="btn ghost" @click="closeCfg">取消</view>
            <view class="btn primary" :class="{ disabled: saving }" @click="save(p)">{{ saving ? '保存中…' : '保存套餐' }}</view>
          </view>
        </view>
      </view>
    </view>

    <!-- 商品选择器 -->
    <view v-if="pickerOpen" class="picker-mask" @click="pickerOpen = false">
      <view class="picker-wrap" @click.stop>
        <view class="picker-head">
          <text class="picker-title">选择服务商品</text>
          <text class="picker-close" @click="pickerOpen = false">✕</text>
        </view>
        <scroll-view scroll-y class="picker-list">
          <view v-if="!pickerProducts.length" class="picker-empty">没有可选的其他商品</view>
          <view v-for="p in pickerProducts" :key="p.id" class="picker-item" @click="choose(p)">
            <image v-if="p.picUrl" :src="p.picUrl" class="picker-pic" mode="aspectFill" />
            <view v-else class="picker-pic ph">🛍</view>
            <view class="picker-info">
              <text class="picker-name">{{ p.name }}</text>
              <text class="picker-price">¥{{ yuan(p.price) }}</text>
            </view>
          </view>
        </scroll-view>
      </view>
    </view>

    <view class="bottom-pad"></view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getSpuPage } from '../../api/product.js';
import { getCardDefs, saveCardDefs } from '../../api/card.js';

const loading = ref(true);
const saving = ref(false);
const products = ref([]);
const openId = ref(null);
const cards = ref([]);

const pickerOpen = ref(false);
const pickerTargetIdx = ref(-1); // -1=新增；>=0=更换该行

const unitOptions = [
  { v: 'day', label: '天' },
  { v: 'month', label: '月' },
  { v: 'year', label: '年' },
];
const UNIT_DAYS = { day: 1, month: 30, year: 365 };

function yuan(fen) { return ((Number(fen) || 0) / 100).toFixed(2); }
function daysToUnit(days) {
  const d = Number(days) || 0;
  if (d > 0 && d % 365 === 0) return { validValue: String(d / 365), validUnit: 'year' };
  if (d > 0 && d % 30 === 0) return { validValue: String(d / 30), validUnit: 'month' };
  return { validValue: String(d || 0), validUnit: 'day' };
}
function unitToDays(c) {
  const v = Math.max(1, parseInt(c.validValue) || 0);
  return v * (UNIT_DAYS[c.validUnit] || 1);
}

// 选择器：当前套餐之外、且未被本套餐选过的商品
const pickerProducts = computed(() => {
  const chosen = new Set(cards.value.map((c, i) => (i === pickerTargetIdx.value ? null : c.itemSpuId)).filter(Boolean));
  return products.value.filter((p) => p.id !== openId.value && !chosen.has(p.id));
});

async function loadAll() {
  loading.value = true;
  try {
    const acc = [];
    let pageNo = 1;
    for (;;) {
      const { list, total } = await getSpuPage({ pageNo, pageSize: 50 });
      acc.push(...(list || []));
      if (acc.length >= (total || 0) || !list || !list.length) break;
      pageNo += 1;
      if (pageNo > 20) break;
    }
    const counts = await Promise.all(
      acc.map((p) => getCardDefs(p.id).then((d) => (Array.isArray(d) ? d.length : 0)).catch(() => 0)),
    );
    acc.forEach((p, i) => { p.cardCount = counts[i]; });
    products.value = acc;
  } catch (e) {
    uni.showToast({ title: '加载商品失败：' + (e?.msg || e?.message || ''), icon: 'none' });
    products.value = [];
  } finally {
    loading.value = false;
  }
}

function findProduct(spuId) { return products.value.find((p) => p.id === spuId); }

function removeCard(i) { cards.value.splice(i, 1); }
function closeCfg() { openId.value = null; cards.value = []; }

async function toggle(p) {
  if (openId.value === p.id) { closeCfg(); return; }
  openId.value = p.id;
  cards.value = [];
  try {
    const list = await getCardDefs(p.id);
    cards.value = (list || []).map((d) => {
      const u = daysToUnit(d.validityDays);
      const prod = findProduct(d.itemSpuId);
      return {
        itemSpuId: d.itemSpuId || null,
        name: d.name || (prod ? prod.name : ''),
        picUrl: prod ? prod.picUrl : '',
        price: prod ? prod.price : null,
        validValue: u.validValue,
        validUnit: u.validUnit,
        limited: d.maxCount != null && d.maxCount > 0,
        maxCount: d.maxCount != null && d.maxCount > 0 ? String(d.maxCount) : '',
        description: d.description || '',
      };
    });
  } catch { cards.value = []; }
}

function openPicker(idx) { pickerTargetIdx.value = idx; pickerOpen.value = true; }
function choose(p) {
  if (pickerTargetIdx.value >= 0) {
    // 更换：保留有效期/次数，换商品
    const c = cards.value[pickerTargetIdx.value];
    c.itemSpuId = p.id; c.name = p.name; c.picUrl = p.picUrl; c.price = p.price;
  } else {
    cards.value.push({
      itemSpuId: p.id, name: p.name, picUrl: p.picUrl, price: p.price,
      validValue: '1', validUnit: 'year', limited: false, maxCount: '', description: '',
    });
  }
  pickerOpen.value = false;
}

function buildDefs() {
  const defs = [];
  for (let i = 0; i < cards.value.length; i++) {
    const c = cards.value[i];
    if (!c.itemSpuId) {
      uni.showToast({ title: `第 ${i + 1} 项未选择商品`, icon: 'none' });
      return null;
    }
    if (c.limited && !(parseInt(c.maxCount) > 0)) {
      uni.showToast({ title: `「${c.name}」选了限定次数，请填次数`, icon: 'none' });
      return null;
    }
    defs.push({
      itemSpuId: c.itemSpuId,
      name: (c.name || '').trim() || '服务',
      validityDays: unitToDays(c),
      maxCount: c.limited ? (parseInt(c.maxCount) || 1) : null,
      description: (c.description || '').trim(),
    });
  }
  return defs;
}

async function save(p) {
  const defs = buildDefs();
  if (defs == null) return;
  saving.value = true;
  try {
    await saveCardDefs(p.id, defs);
    p.cardCount = defs.length;
    uni.showToast({ title: defs.length ? '套餐已保存' : '已清空该套餐', icon: 'success' });
    closeCfg();
  } catch (e) {
    uni.showToast({ title: '保存失败：' + (e?.msg || e?.message || ''), icon: 'none', duration: 2500 });
  } finally {
    saving.value = false;
  }
}

onShow(() => { loadAll(); });
</script>

<style lang="scss" scoped>
.page { min-height: 100vh; background: #F6F7F9; padding: 16rpx 20rpx 40rpx; }
.card { background: #fff; border-radius: 20rpx; box-shadow: 0 2rpx 12rpx rgba(0,0,0,.04); }

.intro { padding: 24rpx; margin-bottom: 16rpx; }
.intro-t { font-size: 32rpx; font-weight: 800; color: #1F1208; }
.intro-d { margin-top: 10rpx; font-size: 24rpx; color: #7A6A5A; line-height: 1.6; }
.intro-d .hl { color: #FF6B35; font-weight: 700; }

.empty { text-align: center; padding: 80rpx 40rpx; color: #A0917F; font-size: 26rpx; line-height: 1.6; }

.list { display: flex; flex-direction: column; gap: 16rpx; }
.svc-prod { overflow: hidden; }
.prod-head { display: flex; align-items: center; gap: 18rpx; padding: 22rpx; }
.prod-pic { width: 96rpx; height: 96rpx; border-radius: 16rpx; flex-shrink: 0; background: #F6F2EC; }
.prod-pic.ph { display: flex; align-items: center; justify-content: center; font-size: 44rpx; }
.prod-body { flex: 1; min-width: 0; }
.prod-name { font-size: 28rpx; font-weight: 700; color: #1F1208; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.prod-meta { margin-top: 10rpx; display: flex; align-items: center; gap: 12rpx; flex-wrap: wrap; }
.prod-price { font-size: 26rpx; color: #FF6B35; font-weight: 800; }
.badge { font-size: 20rpx; padding: 2rpx 12rpx; border-radius: 999rpx; font-weight: 600; }
.badge.on { background: rgba(255,107,53,.12); color: #C2410C; }
.badge.off { background: #F0EDE8; color: #9A8B7A; }
.badge.down { background: #FBE9E9; color: #C0392B; }
.arrow { color: #C8BCAE; font-size: 30rpx; flex-shrink: 0; }

.prod-cfg { padding: 0 22rpx 22rpx; border-top: 1rpx solid #F0ECE6; }
.cfg-tip { margin-top: 16rpx; font-size: 24rpx; color: #5A4A3A; font-weight: 600; }

.svc-card { margin-top: 18rpx; padding: 18rpx; background: #FBF8F4; border-radius: 16rpx; }
.svc-card-head { display: flex; justify-content: space-between; align-items: center; }
.svc-item { display: flex; align-items: center; gap: 14rpx; flex: 1; min-width: 0; }
.svc-item-pic { width: 72rpx; height: 72rpx; border-radius: 12rpx; background: #fff; flex-shrink: 0; }
.svc-item-pic.ph { display: flex; align-items: center; justify-content: center; font-size: 34rpx; }
.svc-item-info { min-width: 0; }
.svc-item-name { font-size: 28rpx; font-weight: 700; color: #1F1208; }
.svc-item-price { display: block; font-size: 22rpx; color: #9A8B7A; margin-top: 4rpx; }
.svc-del { font-size: 22rpx; color: #C0392B; flex-shrink: 0; }
.svc-change { margin-top: 12rpx; font-size: 24rpx; color: #FF6B35; font-weight: 700; }

.input { width: 100%; box-sizing: border-box; height: 78rpx; padding: 0 20rpx; margin-top: 12rpx; background: #fff; border: 1rpx solid #ECE6DE; border-radius: 12rpx; font-size: 26rpx; color: #1F1208; }
.input.compact { height: 72rpx; }
.input.num { width: 160rpx; margin-top: 0; }
.label-v { display: block; margin-top: 18rpx; font-size: 24rpx; color: #7A6A5A; font-weight: 600; }
.valid-row { display: flex; align-items: center; gap: 16rpx; margin-top: 12rpx; }
.unit-seg { display: flex; background: #fff; border: 1rpx solid #ECE6DE; border-radius: 12rpx; overflow: hidden; }
.unit-item { padding: 0 26rpx; height: 72rpx; line-height: 72rpx; font-size: 26rpx; color: #7A6A5A; }
.unit-item.active { background: linear-gradient(135deg, #FF8A4A, #FF6B35); color: #fff; font-weight: 700; }
.radio-row { display: flex; gap: 16rpx; margin-top: 12rpx; }
.radio-big { flex: 1; text-align: center; height: 78rpx; line-height: 78rpx; background: #fff; border: 1rpx solid #ECE6DE; border-radius: 12rpx; font-size: 26rpx; color: #7A6A5A; }
.radio-big.active { background: linear-gradient(135deg, #FF8A4A, #FF6B35); color: #fff; font-weight: 800; border-color: transparent; }
.hint { display: block; margin-top: 10rpx; font-size: 22rpx; color: #A0917F; }

.svc-add { margin-top: 18rpx; text-align: center; padding: 20rpx; border: 1rpx dashed #FFB088; border-radius: 12rpx; color: #FF6B35; font-size: 26rpx; font-weight: 700; }
.cfg-actions { display: flex; gap: 16rpx; margin-top: 20rpx; }
.btn { flex: 1; text-align: center; height: 84rpx; line-height: 84rpx; border-radius: 999rpx; font-size: 28rpx; font-weight: 800; }
.btn.ghost { background: #F0EDE8; color: #7A6A5A; }
.btn.primary { background: linear-gradient(135deg, #FF8A4A, #FF6B35); color: #fff; }
.btn.disabled { opacity: .6; }

/* 商品选择器 */
.picker-mask { position: fixed; inset: 0; z-index: 999; background: rgba(0,0,0,.5); display: flex; align-items: flex-end; }
.picker-wrap { width: 100%; max-height: 70vh; background: #fff; border-radius: 24rpx 24rpx 0 0; display: flex; flex-direction: column; }
.picker-head { display: flex; align-items: center; justify-content: space-between; padding: 24rpx; border-bottom: 1rpx solid #F0ECE6; }
.picker-title { font-size: 30rpx; font-weight: 800; color: #1F1208; }
.picker-close { font-size: 32rpx; color: #9A8B7A; padding: 0 8rpx; }
.picker-list { flex: 1; padding: 8rpx 0; }
.picker-empty { text-align: center; padding: 60rpx; color: #A0917F; font-size: 26rpx; }
.picker-item { display: flex; align-items: center; gap: 16rpx; padding: 18rpx 24rpx; }
.picker-pic { width: 80rpx; height: 80rpx; border-radius: 12rpx; background: #F6F2EC; flex-shrink: 0; }
.picker-pic.ph { display: flex; align-items: center; justify-content: center; font-size: 36rpx; }
.picker-info { flex: 1; min-width: 0; }
.picker-name { font-size: 28rpx; color: #1F1208; font-weight: 600; }
.picker-price { display: block; font-size: 24rpx; color: #FF6B35; font-weight: 700; margin-top: 4rpx; }

.bottom-pad { height: 40rpx; }
</style>
