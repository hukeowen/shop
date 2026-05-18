<template>
  <view class="page">
    <!-- 主表单：照片 + 名称 + 价格 + 分类 -->
    <view class="card">
      <view class="pic-area">
        <view
          v-if="form.picUrl"
          class="pic-wrap"
          :style="{ backgroundImage: `url(${form.picUrl})` }"
          @click="pickImage"
        >
          <view class="pic-replace">点击替换</view>
        </view>
        <view v-else class="pic-add" @click="pickImage">
          <view class="plus">＋</view>
          <text>拍张照</text>
        </view>
      </view>
    </view>

    <view class="card">
      <view class="field">
        <text class="label">名称</text>
        <input
          class="input"
          maxlength="20"
          placeholder="例：现烤蜜薯"
          v-model="form.name"
        />
      </view>
      <view class="field">
        <text class="label">价格</text>
        <view class="input-row">
          <text class="prefix">¥</text>
          <input
            class="input"
            type="digit"
            placeholder="0.00"
            :value="priceYuan"
            @input="onPriceInput"
          />
        </view>
      </view>
      <view class="field">
        <text class="label">分类</text>
        <picker
          mode="selector"
          :value="categoryIdx"
          :range="categories"
          range-key="name"
          @change="onPickCategory"
        >
          <view class="picker-row">
            <text class="picker-text">{{ categories[categoryIdx].name }}</text>
            <text class="arrow">›</text>
          </view>
        </picker>
      </view>
    </view>

    <!-- 营销配置（v7 双积分 / 邀请激励 / 入池） — 仅编辑态可用 -->
    <view v-if="isEdit" class="card promo">
      <view class="promo-head">
        <text class="promo-title">营销配置（v7）</text>
        <text class="promo-sub">{{ promoLoaded ? '已配置' : '加载中…' }}</text>
      </view>

      <view class="field-v">
        <text class="label-v">消费积分倍率</text>
        <input
          class="input compact"
          type="digit"
          v-model="promo.consumePointRatio"
          placeholder="每元返多少消费积分，0=关闭"
        />
      </view>

      <view class="switch-row">
        <view class="switch-body">
          <view class="switch-title">参与邀请激励（v7）</view>
          <view class="switch-desc">链上前 N 位推荐人按比例瓜分该商品的 1 笔订单推广奖励</view>
        </view>
        <switch
          :checked="promo.tuijianEnabled"
          color="#FF6B35"
          @change="(e) => (promo.tuijianEnabled = e.detail.value)"
        />
      </view>
      <template v-if="promo.tuijianEnabled">
        <view class="field-v">
          <text class="label-v">N 值（推几个）</text>
          <input
            class="input compact"
            type="number"
            v-model="promo.tuijianN"
            @blur="syncTuijianN"
            placeholder="如 4"
          />
        </view>
        <view class="field-v">
          <text class="label-v">N 个推广奖励比例 % （从近到远）</text>
          <view class="ratios-row">
            <view
              v-for="(r, i) in promo.tuijianRatios"
              :key="i"
              class="ratio-cell"
            >
              <text class="ratio-tag">第 {{ i + 1 }} 次</text>
              <input
                class="input ratio"
                type="digit"
                :value="r"
                @input="(e) => (promo.tuijianRatios[i] = e.detail.value)"
                placeholder="0"
              />
              <text class="suffix-sm">%</text>
            </view>
          </view>
          <text class="hint inline" :class="{ warn: ratiosSum > 100 }">
            合计 {{ ratiosSum.toFixed(1) }}% / 100%（超过 100% 不能保存）
          </text>
        </view>
      </template>

      <!-- v8: 邀请奖比例（邀请激励 完成后每件按此比例返） -->
      <view class="field-v">
        <text class="label-v">邀请奖比例</text>
        <view class="star-row-input">
          <input class="input compact" type="digit" v-model="promo.directRate" placeholder="例 10" />
          <text class="suffix">%</text>
        </view>
        <text class="hint inline">buyer 完成 N 后自购按此返；parent 首贡献 1 件价 × 此</text>
      </view>

      <!-- v8: 店内星级奖励（按商品独立） -->
      <view class="field-v">
        <text class="label-v">星级数（0=不启用）</text>
        <input class="input compact" type="number" v-model="promo.starCount" @blur="syncStarCount" placeholder="0" />
      </view>
      <template v-if="(parseInt(promo.starCount)||0) > 0">
        <!-- 每星一张卡：返奖比例 + 升星条件 三项垂直排 -->
        <view class="star-block">
          <view class="star-block-title">各星级配置</view>
          <view class="star-block-hint">每星填：返奖比例（%）/ 升星所需邀请人数 / 升星所需店内累计金额（元）</view>
          <view v-for="(rule, i) in promo.starUpgradeRules" :key="i" class="star-card">
            <view class="star-card-head">
              <view class="star-badge">{{ i + 1 }}星</view>
            </view>
            <view class="star-card-body">
              <view class="star-row">
                <text class="star-row-label">返奖比例</text>
                <view class="star-row-input">
                  <input class="input" type="digit"
                    :value="promo.starRatios[i]"
                    @input="(e) => (promo.starRatios[i] = e.detail.value)"
                    placeholder="0" />
                  <text class="suffix">%</text>
                </view>
              </view>
              <view class="star-row">
                <text class="star-row-label">邀请人数</text>
                <input class="input" type="number" :value="rule.directCount"
                  @input="(e) => (promo.starUpgradeRules[i].directCount = e.detail.value)"
                  placeholder="升星所需人数" />
              </view>
              <view class="star-row">
                <text class="star-row-label">累计金额</text>
                <view class="star-row-input">
                  <input class="input" type="digit" :value="rule.teamSalesYuan"
                    @input="(e) => (promo.starUpgradeRules[i].teamSalesYuan = e.detail.value)"
                    placeholder="升星所需金额" />
                  <text class="suffix">元</text>
                </view>
              </view>
            </view>
          </view>
        </view>
      </template>

      <!-- v8: 商品级奖池入池比例 -->
      <view class="field-v">
        <text class="label-v">星级奖池入池比例</text>
        <view class="star-row-input">
          <input class="input compact" type="digit" v-model="promo.poolRatio" placeholder="0" />
          <text class="suffix">%</text>
        </view>
        <text class="hint inline">每订单实付 × 此比例，进入该商品的奖池</text>
      </view>

      <!-- v8: 奖池分配规则（手工结算时按此分） -->
      <template v-if="(parseInt(promo.starCount)||0) > 0 && parseFloat(promo.poolRatio) > 0">
        <view class="star-block">
          <view class="star-block-title">奖池分配规则</view>
          <view class="star-block-hint">按星级切片：每星填占池比例 + 分配方式；加总必须 = 100%</view>
          <view v-for="(d, i) in promo.poolDistList" :key="i" class="star-card">
            <view class="star-card-head">
              <view class="star-badge">{{ d.star }}星</view>
            </view>
            <view class="star-card-body">
              <view class="star-row">
                <text class="star-row-label">占池</text>
                <view class="star-row-input">
                  <input class="input" type="digit" :value="d.ratio"
                    @input="(e) => (promo.poolDistList[i].ratio = e.detail.value)"
                    placeholder="0" />
                  <text class="suffix">%</text>
                </view>
              </view>
              <view class="star-row">
                <text class="star-row-label">方式</text>
                <view class="radio-pair">
                  <view class="radio-big" :class="{ active: d.mode === 'EQUAL' }"
                    @click="promo.poolDistList[i].mode = 'EQUAL'">均分</view>
                  <view class="radio-big" :class="{ active: d.mode === 'LOTTERY' }"
                    @click="promo.poolDistList[i].mode = 'LOTTERY'">抽奖</view>
                </view>
              </view>
              <view class="star-row" v-if="d.mode === 'LOTTERY'">
                <text class="star-row-label">中奖名额</text>
                <input class="input" type="number" :value="d.winners"
                  @input="(e) => (promo.poolDistList[i].winners = e.detail.value)"
                  placeholder="≥1（如 3）" />
              </view>
            </view>
          </view>
          <text class="hint inline" :class="{ warn: distSum !== 100 }">
            合计 {{ distSum.toFixed(1) }}% / 100%（必须严格等于 100%）
          </text>
        </view>
      </template>

      <view class="promo-actions">
        <button class="btn ghost-brand" :disabled="promoSaving" @click="onSavePromo">
          {{ promoSaving ? '保存中…' : '保存营销配置' }}
        </button>
      </view>

      <!-- v8: 当前 SPU 池余额 + 结算入口（编辑模式 + 入池比例 > 0 才显示） -->
      <view v-if="isEdit && parseFloat(promo.poolRatio) > 0" class="pool-ops">
        <view class="pool-summary">
          <view class="pool-item">
            <text class="label">当前池余额</text>
            <text class="value">¥{{ (poolBalance / 100).toFixed(2) }}</text>
            <text class="sub">≈ {{ poolBalance }} 分</text>
          </view>
          <view class="pool-item">
            <text class="label">累计入/出</text>
            <text class="sub">入 ¥{{ (poolTotalIn / 100).toFixed(2) }} / 出 ¥{{ (poolTotalOut / 100).toFixed(2) }}</text>
          </view>
        </view>
        <view class="pool-btn-row">
          <button class="btn ghost-brand" :disabled="settling || poolBalance <= 0" @click="onSettle">
            {{ settling ? '结算中…' : '立即结算' }}
          </button>
          <button class="btn ghost" @click="goSettleRecords">结算记录</button>
        </view>
      </view>
    </view>

    <!-- 高级设置（折叠） -->
    <view class="card advanced">
      <view class="advanced-head" @click="advancedOpen = !advancedOpen">
        <text class="advanced-title">高级设置</text>
        <text class="advanced-state">{{ advancedHint }}</text>
        <text class="arrow" :class="{ open: advancedOpen }">›</text>
      </view>
      <view v-if="advancedOpen" class="advanced-body">
        <!--
          ⚠ 旧版 v6 的「参与返利」「参与推N返一」开关已隐藏（v-if="false"）。
          v7 起改走 promo.tuijianEnabled 在上方营销设置区域统一配置，
          此处 form.brokerageEnabled / pushBackEnabled 字段保留兼容旧数据。
        -->
        <view v-if="false" class="switch-row">
          <view class="switch-body">
            <view class="switch-title">参与返利</view>
            <view class="switch-desc">分享后下单，按全局比例给上线分成</view>
          </view>
          <switch
            :checked="form.brokerageEnabled"
            color="#FF6B35"
            @change="(e) => (form.brokerageEnabled = e.detail.value)"
          />
        </view>
        <view v-if="false" class="switch-row">
          <view class="switch-body">
            <view class="switch-title">参与推N返一</view>
            <view class="switch-desc">推 N 个新会员付费，返 1 年订阅给推荐人</view>
          </view>
          <switch
            :checked="form.pushBackEnabled"
            color="#FF6B35"
            @change="(e) => (form.pushBackEnabled = e.detail.value)"
          />
        </view>
        <view class="field">
          <text class="label">库存</text>
          <input
            class="input"
            type="number"
            placeholder="留空 = 无限量"
            v-model="form.stock"
          />
        </view>
        <view class="field last">
          <text class="label">一句话简介</text>
          <input
            class="input"
            maxlength="50"
            placeholder="选填，例：现烤现卖"
            v-model="form.introduction"
          />
        </view>
      </view>
    </view>

    <view class="actions safe-bottom">
      <button v-if="isEdit" class="btn ghost" @click="onDelete">删除</button>
      <button class="btn primary" :disabled="!canSubmit" @click="onSubmit">
        {{ isEdit ? '保存' : '上架' }}
      </button>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import {
  CATEGORIES,
  DEFAULT_CATEGORY_ID,
  createSpu,
  deleteSpu,
  getSpu,
  loadCategories,
  updateSpu,
} from '../../api/product.js';
import {
  getProductPromoConfig,
  saveProductPromoConfig,
  getSpuPoolBalance,
  settleSpuPool,
} from '../../api/promo.js';

const categories = CATEGORIES;
const isEdit = ref(false);
const editingId = ref(0);
const advancedOpen = ref(false);

const form = reactive({
  picUrl: '',
  name: '',
  price: 0,
  categoryId: DEFAULT_CATEGORY_ID,
  stock: '',
  introduction: '',
  brokerageEnabled: true,
  pushBackEnabled: false,
});

// v7 商品级营销配置（独立于 SPU 主表）
const promoLoaded = ref(false);
const promoSaving = ref(false);
const promo = reactive({
  consumePointRatio: '0',
  tuijianEnabled: false,
  tuijianN: '4',
  tuijianRatios: ['25', '25', '25', '25'],
  // v8: 商品级邀请奖 / 星级递减 / 入池
  directRate: '10',
  starCount: '0',
  starRatios: [],
  starUpgradeRules: [],   // [{directCount, teamSalesYuan}]，提交时 ×100 转分
  poolRatio: '0',
  poolEnabled: false,
  // v8: 奖池分配规则；每星一条 {star, ratio, mode:EQUAL|LOTTERY, winners}
  poolDistList: [],
});

// v8 当前 SPU 池余额（编辑模式实时拉）
const poolBalance = ref(0);
const poolTotalIn = ref(0);
const poolTotalOut = ref(0);
const settling = ref(false);

// 奖池分配规则合计
const distSum = computed(() => {
  let s = 0;
  for (const d of promo.poolDistList) s += Number(d.ratio) || 0;
  return s;
});

function syncTuijianN() {
  const target = Math.max(0, Math.min(20, parseInt(promo.tuijianN) || 0));
  promo.tuijianN = String(target);
  while (promo.tuijianRatios.length < target) promo.tuijianRatios.push('0');
  if (promo.tuijianRatios.length > target) promo.tuijianRatios.length = target;
}

function syncStarCount() {
  const target = Math.max(0, Math.min(10, parseInt(promo.starCount) || 0));
  promo.starCount = String(target);
  while (promo.starRatios.length < target) promo.starRatios.push('0');
  if (promo.starRatios.length > target) promo.starRatios.length = target;
  while (promo.starUpgradeRules.length < target) promo.starUpgradeRules.push({ directCount: '0', teamSalesYuan: '0' });
  if (promo.starUpgradeRules.length > target) promo.starUpgradeRules.length = target;
  syncPoolDistRows();
}

// 奖池分配行：跟 starCount 联动；保留已填值，按 1..starCount 重排
function syncPoolDistRows() {
  const target = Math.max(0, Math.min(10, parseInt(promo.starCount) || 0));
  const existing = new Map(promo.poolDistList.map((d) => [Number(d.star), d]));
  const next = [];
  // 高星到低星生成（用户视觉习惯 5 → 1）
  for (let s = target; s >= 1; s--) {
    const e = existing.get(s);
    next.push(e || { star: s, ratio: '0', mode: 'EQUAL', winners: '1' });
  }
  promo.poolDistList = next;
}

async function loadPromo(spuId) {
  try {
    const data = await getProductPromoConfig(spuId);
    if (!data) return;
    promo.consumePointRatio = String(data.consumePointRatio ?? '0');
    promo.tuijianEnabled = !!data.tuijianEnabled;
    promo.tuijianN = String(data.tuijianN ?? 0);
    try {
      const ratios = JSON.parse(data.tuijianRatios || '[]');
      promo.tuijianRatios = Array.isArray(ratios) ? ratios.map(String) : [];
    } catch {
      promo.tuijianRatios = [];
    }
    syncTuijianN();
    // v8: 邀请奖 / 星级递减 / 入池
    promo.directRate = String(data.directRate ?? '10');
    promo.starCount = String(data.starCount ?? 0);
    try {
      const arr = JSON.parse(data.starRatios || '[]');
      promo.starRatios = Array.isArray(arr) ? arr.map(String) : [];
    } catch {
      promo.starRatios = [];
    }
    try {
      const arr = JSON.parse(data.starUpgradeRules || '[]');
      promo.starUpgradeRules = Array.isArray(arr)
        ? arr.map(r => ({
            directCount: String(r.directCount ?? '0'),
            teamSalesYuan: String(((r.teamSales ?? 0) / 100).toFixed(2)),
          }))
        : [];
    } catch {
      promo.starUpgradeRules = [];
    }
    syncStarCount();
    promo.poolRatio = String(data.poolRatio ?? '0');
    promo.poolEnabled = !!data.poolEnabled;
    // poolDistRules: 把 JSON 反序列化到 poolDistList；优先用后端，星级缺失自动补
    try {
      const arr = JSON.parse(data.poolDistRules || '[]');
      if (Array.isArray(arr) && arr.length > 0) {
        const map = new Map(arr.map((r) => [Number(r.star), r]));
        const target = parseInt(promo.starCount) || 0;
        const next = [];
        for (let s = target; s >= 1; s--) {
          const r = map.get(s);
          next.push(r
            ? { star: s, ratio: String(r.ratio ?? '0'),
                mode: r.mode || 'EQUAL', winners: String(r.winners ?? '1') }
            : { star: s, ratio: '0', mode: 'EQUAL', winners: '1' });
        }
        promo.poolDistList = next;
      }
    } catch {
      // 解析失败留空 — syncPoolDistRows 已补
    }
  } finally {
    promoLoaded.value = true;
  }
}

// N 个比例的实时加总，模板里展示 + 保存时强校验
const ratiosSum = computed(() => {
  if (!promo.tuijianEnabled) return 0;
  const n = parseInt(promo.tuijianN) || 0;
  let s = 0;
  for (let i = 0; i < n && i < promo.tuijianRatios.length; i++) {
    s += Number(promo.tuijianRatios[i]) || 0;
  }
  return s;
});

async function onSavePromo() {
  syncTuijianN();
  const n = parseInt(promo.tuijianN) || 0;
  if (promo.tuijianEnabled && n <= 0) {
    uni.showToast({ title: '邀请激励 启用时 N 必须 > 0', icon: 'none' });
    return;
  }
  // v7 文档：N 个比例加总不能超过 100%（会把商品价超额返出去）
  if (promo.tuijianEnabled && ratiosSum.value > 100) {
    uni.showToast({
      title: `N 个比例加总 ${ratiosSum.value.toFixed(1)}% > 100%，请调整`,
      icon: 'none',
      duration: 2500,
    });
    return;
  }
  // 奖池分配规则校验：入池比例 > 0 时才校验；强 sum=100
  const poolEnabled = (parseFloat(promo.poolRatio) || 0) > 0;
  let poolDistJson = '';
  if (poolEnabled && parseInt(promo.starCount) > 0) {
    const filled = promo.poolDistList
      .filter((d) => Number(d.ratio) > 0)
      .map((d) => {
        const o = {
          star: Number(d.star),
          ratio: Number(d.ratio) || 0,
          mode: d.mode || 'EQUAL',
        };
        if (o.mode === 'LOTTERY') o.winners = parseInt(d.winners) || 1;
        return o;
      });
    if (filled.length > 0) {
      const sum = filled.reduce((s, x) => s + Number(x.ratio), 0);
      if (Math.abs(sum - 100) > 0.001) {
        uni.showToast({
          title: `奖池分配 ${sum.toFixed(1)}% ≠ 100%，请调整`,
          icon: 'none',
          duration: 2500,
        });
        return;
      }
      for (const x of filled) {
        if (x.mode === 'LOTTERY' && (!x.winners || x.winners < 1)) {
          uni.showToast({ title: `${x.star} 星抽奖名额必须 ≥ 1`, icon: 'none' });
          return;
        }
      }
      poolDistJson = JSON.stringify(filled);
    }
  }

  promoSaving.value = true;
  try {
    syncStarCount();
    const sc = parseInt(promo.starCount) || 0;
    await saveProductPromoConfig({
      spuId: editingId.value,
      consumePointRatio: parseFloat(promo.consumePointRatio) || 0,
      tuijianEnabled: !!promo.tuijianEnabled,
      tuijianN: n,
      tuijianRatios: JSON.stringify(promo.tuijianRatios.map((r) => Number(r) || 0)),
      // v8 字段
      directRate: parseFloat(promo.directRate) || 0,
      starCount: sc,
      starRatios: sc > 0 ? JSON.stringify(promo.starRatios.slice(0, sc).map(r => Number(r) || 0)) : '[]',
      starUpgradeRules: sc > 0 ? JSON.stringify(promo.starUpgradeRules.slice(0, sc).map((r, i) => ({
        star: i + 1,
        directCount: parseInt(r.directCount) || 0,
        teamSales: Math.round((parseFloat(r.teamSalesYuan) || 0) * 100),  // 元 → 分
      }))) : '[]',
      poolRatio: parseFloat(promo.poolRatio) || 0,
      poolEnabled: !!promo.poolEnabled,
      poolDistRules: poolDistJson,
    });
    uni.showToast({ title: '营销配置已保存', icon: 'success' });
  } finally {
    promoSaving.value = false;
  }
}

// v8 拉池余额
async function loadPoolBalance() {
  if (!isEdit.value || !editingId.value) return;
  try {
    const r = await getSpuPoolBalance(editingId.value);
    if (r) {
      poolBalance.value = r.poolBalance || 0;
      poolTotalIn.value = r.totalIn || 0;
      poolTotalOut.value = r.totalOut || 0;
    }
  } catch {}
}

async function onSettle() {
  const m = await uni.showModal({
    title: '确认结算',
    content: `将按"奖池分配规则"把当前 ¥${(poolBalance.value / 100).toFixed(2)} 全部分发给对应星级用户的推广积分。结算后池清零，无法撤销。`,
  });
  if (!m.confirm) return;
  settling.value = true;
  try {
    const rec = await settleSpuPool(editingId.value, '');
    if (rec) {
      const dist = rec.totalDistributed || 0;
      uni.showToast({
        title: `已分发 ¥${(dist / 100).toFixed(2)}`,
        icon: 'success',
      });
      await loadPoolBalance();
    }
  } catch (e) {
    uni.showToast({ title: e?.msg || '结算失败', icon: 'none', duration: 2200 });
  } finally {
    settling.value = false;
  }
}

function goSettleRecords() {
  uni.navigateTo({ url: `/pages/product/pool-records?spuId=${editingId.value}` });
}

const categoryIdx = computed(() => {
  const i = categories.findIndex((c) => c.id === form.categoryId);
  return i >= 0 ? i : 0;
});

const priceYuan = computed(() =>
  form.price ? (form.price / 100).toFixed(2) : ''
);

const canSubmit = computed(
  () => form.picUrl && form.name.trim() && form.price > 0
);

const advancedHint = computed(() => {
  const tags = [];
  if (form.brokerageEnabled) tags.push('返利');
  if (form.pushBackEnabled) tags.push('推N返一');
  if (form.stock) tags.push(`库存 ${form.stock}`);
  return tags.join(' · ') || '默认';
});

function onPickCategory(e) {
  form.categoryId = categories[Number(e.detail.value)].id;
}

function onPriceInput(e) {
  const v = e.detail.value || '';
  const n = parseFloat(v);
  form.price = isNaN(n) ? 0 : Math.round(n * 100);
}

async function pickImage() {
  const tempPath = await new Promise((resolve) => {
    uni.chooseImage({
      count: 1,
      success: (r) => resolve(r.tempFilePaths[0]),
      fail: () => resolve(null),
    });
  });
  if (!tempPath) return;
  uni.showLoading({ title: '上传中…' });
  try {
    // 关键：本地临时路径（blob:// 或 wxfile://）公网不可访问；
    // 必须先 → base64 → /oss/upload（sidecar TOS 直传）→ 公网 URL，
    // 否则商品列表 / shop-home / AI 视频都加载 404。
    const { blobUrlToBase64, uploadImage } = await import('../../api/oss.js');
    const base64 = await blobUrlToBase64(tempPath);
    const { url: publicUrl } = await uploadImage(base64, { ext: 'jpg' });
    form.picUrl = publicUrl;
    uni.hideLoading();
    uni.showToast({ title: '上传成功', icon: 'success' });
  } catch (e) {
    uni.hideLoading();
    uni.showToast({ title: '上传失败：' + (e?.message || e), icon: 'none' });
  }
}

async function loadIfEdit(id) {
  const s = await getSpu(id);
  if (!s) {
    uni.showToast({ title: '商品不存在', icon: 'none' });
    return;
  }
  form.picUrl = s.picUrl;
  form.name = s.name;
  form.price = s.price;
  form.categoryId = s.categoryId || DEFAULT_CATEGORY_ID;
  form.stock = s.stock === 9999 ? '' : String(s.stock);
  form.introduction = s.introduction;
  form.brokerageEnabled = s.brokerageEnabled;
  form.pushBackEnabled = s.pushBackEnabled;
  // 编辑态如果有非默认设置，自动展开高级
  if (!form.brokerageEnabled || form.pushBackEnabled || form.stock || form.introduction) {
    advancedOpen.value = true;
  }
  uni.setNavigationBarTitle({ title: '编辑商品' });
  loadPromo(id);
  loadPoolBalance();
}

async function onSubmit() {
  if (!canSubmit.value) return;
  uni.showLoading({ title: isEdit.value ? '保存中' : '上架中' });
  const payload = {
    ...form,
    stock: form.stock ? Number(form.stock) : 9999,
  };
  try {
    if (isEdit.value) {
      await updateSpu({ id: editingId.value, ...payload });
      uni.hideLoading();
      uni.showToast({ title: '已保存', icon: 'success' });
    } else {
      await createSpu(payload);
      uni.hideLoading();
      uni.showToast({ title: '上架成功', icon: 'success' });
    }
    setTimeout(() => uni.navigateBack(), 800);
  } catch (e) {
    uni.hideLoading();
  }
}

async function onDelete() {
  const r = await uni.showModal({
    title: '删除商品',
    content: '删除后无法恢复，确认删除？',
    confirmColor: '#EF4444',
  });
  if (!r.confirm) return;
  await deleteSpu(editingId.value);
  uni.showToast({ title: '已删除', icon: 'success' });
  setTimeout(() => uni.navigateBack(), 800);
}

onLoad(async (q) => {
  // 显式预热商品分类（小程序环境无 window，模块加载时自动预热不会触发）
  // CATEGORIES 是引用，loadCategories 内部 splice 替换内容；模板里 v-for 会响应
  loadCategories().catch(() => {});
  if (q.id) {
    isEdit.value = true;
    editingId.value = Number(q.id);
    loadIfEdit(editingId.value);
  } else {
    uni.setNavigationBarTitle({ title: '上架商品' });
  }
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  padding: 24rpx 24rpx 200rpx;
  min-height: 100vh;
}

.card {
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 28rpx 32rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.pic-area {
  display: flex;
  justify-content: center;
}

.pic-wrap {
  position: relative;
  width: 360rpx;
  height: 360rpx;
  border-radius: $radius-lg;
  background-size: cover;
  background-position: center;

  .pic-replace {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    height: 60rpx;
    line-height: 60rpx;
    text-align: center;
    background: rgba(0, 0, 0, 0.5);
    color: #fff;
    font-size: 22rpx;
    border-radius: 0 0 $radius-lg $radius-lg;
  }
}

.pic-add {
  width: 360rpx;
  height: 360rpx;
  border: 2rpx dashed $text-placeholder;
  border-radius: $radius-lg;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: $text-secondary;
  font-size: 26rpx;
  background: #fafbfc;

  .plus {
    font-size: 96rpx;
    color: $brand-primary;
    line-height: 1;
    margin-bottom: 16rpx;
    font-weight: 200;
  }
}

.field {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 20rpx 0;
  border-bottom: 1rpx solid $border-color;

  &.last,
  &:last-child {
    border-bottom: none;
  }

  .label {
    flex-shrink: 0;
    width: 160rpx;
    font-size: 28rpx;
    color: $text-regular;
  }
}

// 视觉清晰的输入框：浅灰底 + 圆角 + 充足内边距 + ≥16px 字号防 iOS 缩放
.input {
  flex: 1;
  min-height: 88rpx;
  padding: 0 24rpx;
  background: #f6f7f9;
  border: 1rpx solid transparent;
  border-radius: $radius-md;
  font-size: 32rpx; // ≥16px，避免 iOS Safari 自动 zoom
  color: $text-primary;
  box-sizing: border-box;
  line-height: 1.4;

  &:focus,
  &:focus-within {
    background: #fff;
    border-color: $brand-primary;
  }

  &.compact {
    min-height: 80rpx;
    font-size: 30rpx;
  }
}

.input-row {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 0 24rpx;
  min-height: 88rpx;
  background: #f6f7f9;
  border-radius: $radius-md;

  .input {
    background: transparent;
    padding: 0;
    border: none;
    min-height: 0;
  }

  .prefix {
    font-size: 32rpx;
    color: $text-secondary;
    flex-shrink: 0;
  }
}

.picker-row {
  flex: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 88rpx;
  padding: 0 24rpx;
  background: #f6f7f9;
  border-radius: $radius-md;

  .picker-text {
    font-size: 32rpx;
    color: $text-primary;
  }

  .arrow {
    color: $text-placeholder;
    font-size: 36rpx;
  }
}

.advanced {
  padding: 0 32rpx;
}

.advanced-head {
  display: flex;
  align-items: center;
  height: 88rpx;

  .advanced-title {
    font-size: 28rpx;
    color: $text-primary;
    font-weight: 500;
  }

  .advanced-state {
    flex: 1;
    margin-left: 16rpx;
    font-size: 24rpx;
    color: $text-secondary;
  }

  .arrow {
    color: $text-placeholder;
    font-size: 36rpx;
    transition: transform 0.2s;

    &.open {
      transform: rotate(90deg);
    }
  }
}

.advanced-body {
  padding: 0 0 24rpx;
  border-top: 1rpx solid $border-color;
}

.switch-row {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 24rpx 0;
  border-bottom: 1rpx solid $border-color;

  .switch-body {
    flex: 1;
    min-width: 0;
  }

  .switch-title {
    font-size: 28rpx;
    font-weight: 500;
    color: $text-primary;
  }

  .switch-desc {
    margin-top: 6rpx;
    font-size: 22rpx;
    color: $text-secondary;
    line-height: 1.5;
  }
}

.promo {
  padding: 28rpx 32rpx;

  .promo-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-bottom: 16rpx;
    border-bottom: 1rpx solid $border-color;
    margin-bottom: 16rpx;

    .promo-title {
      font-size: 28rpx;
      font-weight: 600;
      color: $text-primary;
    }

    .promo-sub {
      font-size: 22rpx;
      color: $text-secondary;
    }
  }

  .row .label {
    flex: 0 0 200rpx;
  }

  // 营销卡片里的 compact 输入 — 比顶部商品名/价格 input 小一些，但仍保留≥16px 字号防 iOS auto-zoom
  .input.compact {
    min-height: 80rpx;
    background: #f6f7f9;
    border-radius: $radius-md;
    padding: 0 24rpx;
    font-size: 30rpx;
    box-sizing: border-box;
    &:focus { background: #fff; border: 1rpx solid $brand-primary; }
  }

  // === 垂直布局表单字段：label 在上，input 在下；窄屏更好读好点 ===
  .field-v {
    display: flex;
    flex-direction: column;
    padding: 16rpx 0;
    border-bottom: 1rpx solid $border-color;
    &:last-child { border-bottom: none; }

    .label-v {
      display: block;
      font-size: 26rpx;
      color: $text-regular;
      margin-bottom: 12rpx;
      font-weight: 500;
    }
  }

  // === 「带后缀」输入（如 %, 元） ===
  .star-row-input {
    flex: 1;
    display: flex;
    align-items: center;
    gap: 12rpx;
    .input { flex: 1; }
    .suffix {
      font-size: 28rpx;
      color: $text-secondary;
      padding: 0 8rpx;
      flex-shrink: 0;
    }
  }

  // === N 个比例：每行 2 个 cell，含"第 i 次" 标 + 输入 + % 后缀 ===
  .ratios-row {
    display: flex;
    flex-wrap: wrap;
    gap: 16rpx;
    margin-top: 8rpx;

    .ratio-cell {
      flex: 0 0 calc(50% - 8rpx);
      display: flex;
      align-items: center;
      gap: 8rpx;
      background: #f6f7f9;
      border-radius: $radius-md;
      padding: 0 16rpx;
      min-height: 80rpx;
      box-sizing: border-box;

      .ratio-tag {
        font-size: 22rpx;
        color: $text-secondary;
        flex-shrink: 0;
      }
      .input.ratio {
        flex: 1;
        background: transparent;
        border: none;
        padding: 0;
        min-height: 0;
        font-size: 30rpx;
        text-align: center;
      }
      .suffix-sm {
        font-size: 24rpx;
        color: $text-secondary;
        flex-shrink: 0;
      }
    }
  }

  // === 每星一张卡：返奖比例 / 升星条件 / 奖池分配（垂直布局，绝不挤）===
  .star-block {
    margin-top: 20rpx;
    padding-top: 20rpx;
    border-top: 1rpx dashed $border-color;

    .star-block-title {
      font-size: 28rpx;
      font-weight: 600;
      color: $text-primary;
    }
    .star-block-hint {
      margin-top: 6rpx;
      margin-bottom: 12rpx;
      font-size: 22rpx;
      color: $text-secondary;
      line-height: 1.5;
    }

    .star-card {
      background: linear-gradient(135deg, rgba(255, 107, 53, 0.04), rgba(255, 154, 74, 0.02));
      border: 1rpx solid rgba(255, 107, 53, 0.15);
      border-radius: $radius-md;
      margin-bottom: 16rpx;
      overflow: hidden;
    }

    .star-card-head {
      padding: 12rpx 20rpx;
      background: linear-gradient(135deg, #ffd6b8, #ff6b35);
      .star-badge {
        font-size: 26rpx;
        font-weight: 700;
        color: #fff;
      }
    }

    .star-card-body {
      padding: 16rpx 20rpx 20rpx;
    }

    .star-row {
      display: flex;
      align-items: center;
      gap: 16rpx;
      padding: 10rpx 0;

      .star-row-label {
        flex: 0 0 130rpx;
        font-size: 26rpx;
        color: $text-regular;
      }

      .input {
        flex: 1;
        min-height: 80rpx;
        background: #fff;
        border: 1rpx solid $border-color;
        border-radius: $radius-md;
        padding: 0 20rpx;
        font-size: 30rpx;
        box-sizing: border-box;
        &:focus { border-color: $brand-primary; }
      }

      .star-row-input { flex: 1; }
      .star-row-input .input { background: #fff; }
    }

    .radio-pair {
      flex: 1;
      display: flex;
      gap: 12rpx;

      .radio-big {
        flex: 1;
        min-height: 80rpx;
        line-height: 80rpx;
        text-align: center;
        background: #fff;
        border: 1rpx solid $border-color;
        border-radius: $radius-md;
        font-size: 28rpx;
        color: $text-regular;

        &.active {
          background: rgba(255, 107, 53, 0.12);
          color: $brand-primary;
          border-color: $brand-primary;
          font-weight: 600;
        }
      }
    }
  }

  // 旧 .ratios-row 单输入式样保留作为 fallback（用了 input.ratio 类的地方）
  .ratios-row .ratio {
    min-height: 80rpx;
    background: #f6f7f9;
    border-radius: $radius-md;
    padding: 0 16rpx;
    font-size: 30rpx;
    text-align: center;
    box-sizing: border-box;
    &:focus { background: #fff; border: 1rpx solid $brand-primary; }
  }

  .hint.inline {
    display: block;
    margin-top: 8rpx;
    font-size: 22rpx;
    color: $text-secondary;

    &.warn {
      color: #e63946;
      font-weight: 600;
    }
  }

  .radio-row.tight {
    display: flex;
    gap: 8rpx;
    .radio-chip.sm {
      padding: 8rpx 14rpx;
      font-size: 22rpx;
      border-radius: 999rpx;
      background: #f6f7f9;
      color: $text-secondary;
      border: 1rpx solid transparent;
      &.active {
        background: rgba(255, 107, 53, 0.12);
        color: $brand-primary;
        border-color: $brand-primary;
        font-weight: 600;
      }
    }
  }

  .pool-ops {
    margin-top: 20rpx;
    padding: 20rpx;
    background: linear-gradient(135deg, rgba(255, 107, 53, 0.05), rgba(255, 154, 74, 0.05));
    border-radius: $radius-md;

    .pool-summary {
      display: flex;
      gap: 16rpx;
      margin-bottom: 16rpx;
      .pool-item {
        flex: 1;
        .label {
          display: block;
          font-size: 22rpx;
          color: $text-secondary;
        }
        .value {
          display: block;
          margin-top: 4rpx;
          font-size: 36rpx;
          font-weight: 700;
          color: $brand-primary;
        }
        .sub {
          display: block;
          margin-top: 4rpx;
          font-size: 22rpx;
          color: $text-secondary;
        }
      }
    }
    .pool-btn-row {
      display: flex;
      gap: 12rpx;
      .btn {
        flex: 1;
        height: 76rpx;
        line-height: 76rpx;
        font-size: 26rpx;
        font-weight: 600;
        border-radius: $radius-md;
        &.ghost-brand {
          background: rgba(255, 107, 53, 0.12);
          color: $brand-primary;
          border: 2rpx solid rgba(255, 107, 53, 0.4);
        }
        &.ghost {
          background: #f6f7f9;
          color: $text-primary;
          border: 1rpx solid $border-color;
        }
        &[disabled] { opacity: 0.5; }
        &::after { border: none; }
      }
    }
  }

  .promo-actions {
    margin-top: 16rpx;
  }

  .btn.ghost-brand {
    width: 100%;
    height: 80rpx;
    line-height: 80rpx;
    background: rgba(255, 107, 53, 0.08);
    color: $brand-primary;
    border: 2rpx solid rgba(255, 107, 53, 0.4);
    font-size: 28rpx;
    font-weight: 600;
    border-radius: $radius-md;

    &[disabled] {
      opacity: 0.6;
    }

    &::after {
      border: none;
    }
  }
}

.actions {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  gap: 16rpx;
  padding: 24rpx 32rpx calc(env(safe-area-inset-bottom) + 24rpx);
  background: #fff;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.05);
}

.btn {
  flex: 1;
  height: 96rpx;
  line-height: 96rpx;
  font-size: 32rpx;
  font-weight: 600;
  border-radius: $radius-md;

  &.primary {
    background: $brand-primary;
    color: #fff;
  }

  &.ghost {
    background: #fff;
    color: $danger;
    border: 2rpx solid $danger;
    flex: 0 0 200rpx;
  }

  &[disabled] {
    background: $text-placeholder;
    color: #fff;
  }

  &::after {
    border: none;
  }
}
</style>
