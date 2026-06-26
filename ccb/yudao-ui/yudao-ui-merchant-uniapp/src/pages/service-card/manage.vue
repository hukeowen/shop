<template>
  <view class="page">
    <view class="intro card">
      <view class="intro-t">🪪 服务卡包</view>
      <view class="intro-d">
        从你<text class="hl">已有的商品</text>里选一个，挂上服务卡（有效期 + 核销次数）。
        用户购买该商品后会自动发卡到「卡包」，到店出示码，你在「服务卡核销」里核销。
      </view>
    </view>

    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="!products.length" class="empty">
      还没有商品。请先到「商品管理」上架商品，再回来挂服务卡。
    </view>

    <view v-else class="list">
      <view v-for="p in products" :key="p.id" class="svc-prod card" :class="{ open: openId === p.id }">
        <!-- 商品行：点击展开配置 -->
        <view class="prod-head" @click="toggle(p)">
          <image v-if="p.picUrl" :src="p.picUrl" class="prod-pic" mode="aspectFill" />
          <view v-else class="prod-pic ph">🛍</view>
          <view class="prod-body">
            <view class="prod-name">{{ p.name }}</view>
            <view class="prod-meta">
              <text class="prod-price">¥{{ yuan(p.price) }}</text>
              <text v-if="p.cardCount > 0" class="badge on">已挂 {{ p.cardCount }} 张卡</text>
              <text v-else class="badge off">未挂卡</text>
              <text v-if="p.status === 1" class="badge down">已下架</text>
            </view>
          </view>
          <text class="arrow">{{ openId === p.id ? '▾' : '›' }}</text>
        </view>

        <!-- 展开：卡定义编辑 -->
        <view v-if="openId === p.id" class="prod-cfg">
          <view v-for="(c, i) in cards" :key="i" class="svc-card">
            <view class="svc-card-head">
              <text class="svc-idx">卡 {{ i + 1 }}</text>
              <text class="svc-del" @click="removeCard(i)">删除</text>
            </view>
            <input class="input" v-model="c.name" placeholder="卡名称，如「洗车卡」「保养卡」" />

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

            <input class="input" v-model="c.description" placeholder="使用须知（选填），如「每次到店出示，不限车型」" />
          </view>

          <view class="svc-add" @click="addCard">＋ 添加一张服务卡</view>
          <view class="cfg-actions">
            <view class="btn ghost" @click="closeCfg">取消</view>
            <view class="btn primary" :class="{ disabled: saving }" @click="save(p)">{{ saving ? '保存中…' : '保存服务卡' }}</view>
          </view>
        </view>
      </view>
    </view>

    <view class="bottom-pad"></view>
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getSpuPage } from '../../api/product.js';
import { getCardDefs, saveCardDefs } from '../../api/card.js';

const loading = ref(true);
const saving = ref(false);
const products = ref([]);
const openId = ref(null);

// 卡编辑模型，与 product/edit.vue 一致：{name, validValue, validUnit, limited, maxCount, description}
const cards = ref([]);
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

async function loadAll() {
  loading.value = true;
  try {
    // 拉全部商品（含上下架），分页累积
    const acc = [];
    let pageNo = 1;
    for (;;) {
      const { list, total } = await getSpuPage({ pageNo, pageSize: 50 });
      acc.push(...(list || []));
      if (acc.length >= (total || 0) || !list || !list.length) break;
      pageNo += 1;
      if (pageNo > 20) break; // 安全上限
    }
    // 并行查每个商品已挂卡数量（做角标）
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

function addCard() {
  cards.value.push({ name: '', validValue: '1', validUnit: 'year', limited: false, maxCount: '', description: '' });
}
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
      return {
        name: d.name || '',
        validValue: u.validValue,
        validUnit: u.validUnit,
        limited: d.maxCount != null && d.maxCount > 0,
        maxCount: d.maxCount != null && d.maxCount > 0 ? String(d.maxCount) : '',
        description: d.description || '',
      };
    });
  } catch { cards.value = []; }
  if (!cards.value.length) addCard(); // 新建态默认给一张空卡
}

function buildDefs() {
  const defs = [];
  for (let i = 0; i < cards.value.length; i++) {
    const c = cards.value[i];
    if (!c.name || !c.name.trim()) {
      uni.showToast({ title: `第 ${i + 1} 张卡未填名称`, icon: 'none' });
      return null;
    }
    if (c.limited && !(parseInt(c.maxCount) > 0)) {
      uni.showToast({ title: `「${c.name}」选了限定次数，请填次数`, icon: 'none' });
      return null;
    }
    defs.push({
      name: c.name.trim(),
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
    await saveCardDefs(p.id, defs); // 全量覆盖
    p.cardCount = defs.length;
    uni.showToast({ title: defs.length ? '已保存服务卡' : '已清空该商品服务卡', icon: 'success' });
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
.prod-pic {
  width: 96rpx; height: 96rpx; border-radius: 16rpx; flex-shrink: 0;
  background: #F6F2EC;
}
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

.svc-card { margin-top: 18rpx; padding: 18rpx; background: #FBF8F4; border-radius: 16rpx; }
.svc-card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12rpx; }
.svc-idx { font-size: 24rpx; font-weight: 800; color: #5A4A3A; }
.svc-del { font-size: 22rpx; color: #C0392B; }

.input {
  width: 100%; box-sizing: border-box;
  height: 78rpx; padding: 0 20rpx; margin-top: 12rpx;
  background: #fff; border: 1rpx solid #ECE6DE; border-radius: 12rpx;
  font-size: 26rpx; color: #1F1208;
}
.input.compact { height: 72rpx; }
.input.num { width: 160rpx; margin-top: 0; }
.label-v { display: block; margin-top: 18rpx; font-size: 24rpx; color: #7A6A5A; font-weight: 600; }
.valid-row { display: flex; align-items: center; gap: 16rpx; margin-top: 12rpx; }
.unit-seg { display: flex; background: #fff; border: 1rpx solid #ECE6DE; border-radius: 12rpx; overflow: hidden; }
.unit-item { padding: 0 26rpx; height: 72rpx; line-height: 72rpx; font-size: 26rpx; color: #7A6A5A; }
.unit-item.active { background: linear-gradient(135deg, #FF8A4A, #FF6B35); color: #fff; font-weight: 700; }
.radio-row { display: flex; gap: 16rpx; margin-top: 12rpx; }
.radio-big {
  flex: 1; text-align: center; height: 78rpx; line-height: 78rpx;
  background: #fff; border: 1rpx solid #ECE6DE; border-radius: 12rpx;
  font-size: 26rpx; color: #7A6A5A;
}
.radio-big.active { background: linear-gradient(135deg, #FF8A4A, #FF6B35); color: #fff; font-weight: 800; border-color: transparent; }
.hint { display: block; margin-top: 10rpx; font-size: 22rpx; color: #A0917F; }

.svc-add {
  margin-top: 18rpx; text-align: center; padding: 20rpx;
  border: 1rpx dashed #FFB088; border-radius: 12rpx;
  color: #FF6B35; font-size: 26rpx; font-weight: 700;
}
.cfg-actions { display: flex; gap: 16rpx; margin-top: 20rpx; }
.btn { flex: 1; text-align: center; height: 84rpx; line-height: 84rpx; border-radius: 999rpx; font-size: 28rpx; font-weight: 800; }
.btn.ghost { background: #F0EDE8; color: #7A6A5A; }
.btn.primary { background: linear-gradient(135deg, #FF8A4A, #FF6B35); color: #fff; }
.btn.disabled { opacity: .6; }

.bottom-pad { height: 40rpx; }
</style>
