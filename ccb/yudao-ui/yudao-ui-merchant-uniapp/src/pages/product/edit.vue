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
          :range="categoryOptions"
          range-key="name"
          @change="onPickCategory"
        >
          <view class="picker-row">
            <text class="picker-text">{{ (categoryOptions[categoryIdx]||{}).name }}</text>
            <text class="arrow">›</text>
          </view>
        </picker>
      </view>
    </view>

    <!-- 上架前提示用户：新增商品时不能配营销，先上架→自动跳转到编辑态再设 -->
    <view v-if="!isEdit" class="card promo-hint">
      <view class="promo-title">营销配置（v8）</view>
      <view class="promo-sub">先点底部"上架"完成商品创建，系统会自动跳到编辑页让你设置「消费积分倍率 / 推 N 反 1 / 星级 / 入池」等。</view>
    </view>

    <!-- 营销配置（v8 双积分 / 邀请激励 / 入池） — 仅编辑态可用 -->
    <view v-if="isEdit" class="card promo">
      <view class="promo-head">
        <text class="promo-title">营销配置（v8）</text>
        <text class="promo-sub">{{ promoLoaded ? '已配置' : '加载中…' }}</text>
      </view>

      <view class="field-v">
        <text class="label-v">买一份返积分（顾客下单可抵现）</text>
        <view class="star-row-input">
          <text class="prefix">每消费 1 元 = 返</text>
          <input
            class="input compact"
            type="digit"
            v-model="promo.consumePointRatio"
            placeholder="0"
          />
          <text class="suffix">元</text>
        </view>
        <text class="hint inline">
          填 <text style="font-weight:600;">1</text> = 1 元购物返 1 元（全额返）；
          填 <text style="font-weight:600;">0.1</text> = 返 10%；
          填 <text style="font-weight:600;">0</text> = 不返。
        </text>
      </view>

      <view class="switch-row">
        <view class="switch-body">
          <view class="switch-title">参与「邀请有礼」活动</view>
          <view class="switch-desc">老顾客邀请新人买本商品 → 累计 N 次完成本期，按比例分享激励</view>
        </view>
        <switch
          :checked="promo.tuijianEnabled"
          color="#FF6B35"
          @change="(e) => (promo.tuijianEnabled = e.detail.value)"
        />
      </view>
      <template v-if="promo.tuijianEnabled">
        <view class="field-v">
          <text class="label-v">累积次数 N</text>
          <input
            class="input compact"
            type="number"
            v-model="promo.tuijianN"
            @blur="syncTuijianN"
            placeholder="如 4（邀请新人 N 人买本商品完成本期）"
          />
        </view>
        <view class="field-v">
          <text class="label-v">N 次分享激励 % （从第 1 次到第 N 次）</text>
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
            合计 {{ ratiosSum.toFixed(1) }}% / 100%（来自商户营销让利预算；超 100% 拒绝保存）
          </text>
        </view>
      </template>

      <!-- v8: 邀请奖比例（出队后老客继续邀请的感谢奖） -->
      <view class="field-v">
        <text class="label-v">出队后邀请感谢奖 %</text>
        <view class="star-row-input">
          <input class="input compact" type="digit" v-model="promo.directRate" placeholder="例 10（上限 35%）" />
          <text class="suffix">%</text>
        </view>
        <text class="hint inline">完成 N 次累积后，老客继续邀请新人成单时拿订单实付的此比例 · 单层 · ≤35%</text>
      </view>

      <!-- v8: 会员等级（VIP）配置 — V044 合规：纯个人等级，无团队/链式 -->
      <view class="field-v">
        <text class="label-v">VIP 等级数（0=不启用）</text>
        <input class="input compact" type="number" v-model="promo.starCount" @blur="syncStarCount" placeholder="0" />
        <text class="hint inline">会员升级仅看个人 KPI（自购累计 OR 直推付费数），等同京东 PLUS / 美团 VIP</text>
      </view>
      <template v-if="(parseInt(promo.starCount)||0) > 0">
        <!-- 每星一张卡：邀请奖比例（按推荐人 VIP 等级差异化）+ 升级条件 -->
        <view class="star-block">
          <view class="star-block-title">各 VIP 等级配置</view>
          <view class="star-block-hint">升级条件：直推付费人数 <b>或</b> 个人自购累计金额，<b>任一达标即升</b>；不读下级等级、不读团队业绩（合规要求）</view>
          <view v-for="(rule, i) in promo.starUpgradeRules" :key="i" class="star-card">
            <view class="star-card-head">
              <view class="star-badge">{{ i + 1 }}星</view>
            </view>
            <view class="star-card-body">
              <view class="star-row">
                <text class="star-row-label">邀请奖比例</text>
                <view class="star-row-input">
                  <input class="input" type="digit"
                    :value="promo.starRatios[i]"
                    @input="(e) => (promo.starRatios[i] = e.detail.value)"
                    placeholder="0（上限 35）" />
                  <text class="suffix">%</text>
                </view>
              </view>
              <view class="star-row">
                <text class="star-row-label">直推付费人数</text>
                <view class="star-row-input">
                  <input class="input" type="number" :value="rule.requiredCount"
                    @input="(e) => (promo.starUpgradeRules[i].requiredCount = e.detail.value)"
                    placeholder="升此 VIP 等级所需直推付费人数" />
                  <text class="suffix">人</text>
                </view>
              </view>
              <view class="star-row">
                <text class="star-row-label">或 自购金额</text>
                <view class="star-row-input">
                  <input class="input" type="digit" :value="rule.selfPurchaseYuan"
                    @input="(e) => (promo.starUpgradeRules[i].selfPurchaseYuan = e.detail.value)"
                    placeholder="个人自购累计达标也升 (0=不启用)" />
                  <text class="suffix">元</text>
                </view>
              </view>
              <view class="star-row-tip">
                <b>邀请奖比例</b>：推荐人为本 VIP 等级时，邀请新人成单可获订单实付的此比例（来自商户营销让利预算，单层，≤35%）<br/>
                <b>升级条件</b>：上方两项个人 KPI <b>任一达标</b>即升本 VIP 等级
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

      <!-- 营销配置无单独按钮 — 与底部"保存"一并提交 -->

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
            <view class="switch-desc">分享后下单，按比例给推荐人分享激励</view>
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
  createCategory,
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

// CATEGORIES 是 plain JS 数组，splice 替换内容时 Vue 不会响应；
// 用 ref 持有副本，loadCategories 后手动同步。
const categories = ref([...CATEGORIES]);
function syncCategoriesRef() {
  categories.value = [...CATEGORIES];
}
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
const promo = reactive({
  consumePointRatio: '0',
  tuijianEnabled: false,
  tuijianN: '4',
  tuijianRatios: ['25', '25', '25', '25'],
  // v8: 商品级邀请奖 / 星级递减 / 入池
  directRate: '10',
  starCount: '0',
  starRatios: [],
  starUpgradeRules: [],   // [{requiredCount, selfPurchaseYuan}] V044 删除 teamSalesYuan，提交时 requiredCount → JSON，teamSalesYuan ×100 转分
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
  while (promo.starUpgradeRules.length < target) promo.starUpgradeRules.push({ requiredCount: '0', teamSalesYuan: '0', selfPurchaseYuan: '0' });
  if (promo.starUpgradeRules.length > target) promo.starUpgradeRules.length = target;
  // 给已有项补默认（兼容老数据 / 新增字段）
  promo.starUpgradeRules.forEach(r => {
    if (r.selfPurchaseYuan == null) r.selfPurchaseYuan = '0';
  });
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
    // DB ratio 语义：每元返多少分钱积分（1 fen point）。
    // UI 语义：每元返多少元。两者差 100 倍；展示侧 ÷100。
    promo.consumePointRatio = data.consumePointRatio != null
      ? String(Number(data.consumePointRatio) / 100) : '0';
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
            // 兼容老 JSON：requiredCount 缺省时 fallback 到 directCount
            requiredCount: String(r.requiredCount ?? r.directCount ?? '0'),
            teamSalesYuan: String(((r.teamSales ?? 0) / 100).toFixed(2)),
            // v8.1 OR 分支：自购累计金额（分 → 元）。老数据无此字段时为 0
            selfPurchaseYuan: String(((r.selfPurchaseAmount ?? 0) / 100).toFixed(2)),
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

/**
 * 校验营销策略 — 通过返 { ok: true, poolDistJson }，失败返 { ok: false }（toast 已弹）。
 * 拆出来是因为底部"保存"按钮要在 updateSpu 前先校验，避免商品改了但营销没过校验导致状态不一致。
 */
function validatePromo() {
  syncTuijianN();
  const n = parseInt(promo.tuijianN) || 0;
  if (promo.tuijianEnabled && n <= 0) {
    uni.showToast({ title: '邀请激励 启用时 N 必须 > 0', icon: 'none' });
    return { ok: false };
  }
  if (promo.tuijianEnabled && ratiosSum.value > 100) {
    uni.showToast({ title: `N 个比例加总 ${ratiosSum.value.toFixed(1)}% > 100%，请调整`, icon: 'none', duration: 2500 });
    return { ok: false };
  }
  if ((parseFloat(promo.directRate) || 0) > 35) {
    uni.showToast({ title: '出队后邀请感谢奖 ≤ 35%（合规上限）', icon: 'none', duration: 2500 });
    return { ok: false };
  }
  const sc = parseInt(promo.starCount) || 0;
  if (sc > 0) {
    for (let i = 0; i < sc && i < promo.starRatios.length; i++) {
      const r = parseFloat(promo.starRatios[i]) || 0;
      if (r > 35) {
        uni.showToast({ title: `${i + 1} 星邀请奖 ${r}% > 35%（合规上限）`, icon: 'none', duration: 2500 });
        return { ok: false };
      }
    }
  }
  const poolEnabled = (parseFloat(promo.poolRatio) || 0) > 0;
  let poolDistJson = '';
  if (poolEnabled && parseInt(promo.starCount) > 0) {
    const filled = promo.poolDistList
      .filter((d) => Number(d.ratio) > 0)
      .map((d) => {
        const o = { star: Number(d.star), ratio: Number(d.ratio) || 0, mode: d.mode || 'EQUAL' };
        if (o.mode === 'LOTTERY') o.winners = parseInt(d.winners) || 1;
        return o;
      });
    if (filled.length > 0) {
      const sum = filled.reduce((s, x) => s + Number(x.ratio), 0);
      if (Math.abs(sum - 100) > 0.001) {
        uni.showToast({ title: `奖池分配 ${sum.toFixed(1)}% ≠ 100%，请调整`, icon: 'none', duration: 2500 });
        return { ok: false };
      }
      for (const x of filled) {
        if (x.mode === 'LOTTERY' && (!x.winners || x.winners < 1)) {
          uni.showToast({ title: `${x.star} 星抽奖名额必须 ≥ 1`, icon: 'none' });
          return { ok: false };
        }
      }
      poolDistJson = JSON.stringify(filled);
    }
  }
  return { ok: true, poolDistJson };
}

/** 仅持久化营销配置（不做校验，调用前必须 validatePromo 通过）。 */
async function persistPromo(poolDistJson) {
  syncStarCount();
  const n = parseInt(promo.tuijianN) || 0;
  const sc = parseInt(promo.starCount) || 0;
  await saveProductPromoConfig({
    spuId: editingId.value,
    consumePointRatio: Math.round(((parseFloat(promo.consumePointRatio) || 0) * 100) * 100) / 100,
    tuijianEnabled: !!promo.tuijianEnabled,
    tuijianN: n,
    tuijianRatios: JSON.stringify(promo.tuijianRatios.map((r) => Number(r) || 0)),
    directRate: parseFloat(promo.directRate) || 0,
    starCount: sc,
    starRatios: sc > 0 ? JSON.stringify(promo.starRatios.slice(0, sc).map(r => Number(r) || 0)) : '[]',
    starUpgradeRules: sc > 0 ? JSON.stringify(promo.starUpgradeRules.slice(0, sc).map((r, i) => ({
      star: i + 1,
      requiredStar: 0,
      requiredCount: parseInt(r.requiredCount) || 0,
      teamSales: 0,
      selfPurchaseAmount: Math.round((parseFloat(r.selfPurchaseYuan) || 0) * 100),
    }))) : '[]',
    poolRatio: parseFloat(promo.poolRatio) || 0,
    poolEnabled: !!promo.poolEnabled,
    poolDistRules: poolDistJson || '',
  });
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

// 分类下拉的实际选项：真实分类 + 末尾的「＋ 新增分类」哑元（id=-1）
const ADD_CATEGORY_OPTION = { id: -1, name: '＋ 新增分类' };
const categoryOptions = computed(() => [...categories.value, ADD_CATEGORY_OPTION]);
const categoryIdx = computed(() => {
  const i = categories.value.findIndex((c) => c.id === form.categoryId);
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
  const idx = Number(e.detail.value);
  const picked = categoryOptions.value[idx];
  if (!picked) return;
  if (picked.id === -1) {
    // 点了「＋ 新增分类」哑元 → 弹输入框创建
    uni.showModal({
      title: '新增商品分类',
      editable: true,
      placeholderText: '如：饭团 / 卤味 / 文具',
      success: async (res) => {
        if (!res.confirm) return;
        const name = (res.content || '').trim();
        if (!name) {
          uni.showToast({ title: '分类名不能为空', icon: 'none' });
          return;
        }
        try {
          const newId = await createCategory(name);
          syncCategoriesRef(); // 刷新本地响应式副本，让 picker 立刻显示
          if (newId) {
            form.categoryId = Number(newId);
            uni.showToast({ title: `已创建「${name}」`, icon: 'success' });
          }
        } catch (e) {
          uni.showToast({ title: '创建失败：' + (e?.msg || e?.message || '未知'), icon: 'none' });
        }
      },
    });
    return;
  }
  form.categoryId = picked.id;
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
  // 编辑态：先校验营销策略，避免商品改了但营销没过校验导致状态不一致
  let promoCheck = null;
  if (isEdit.value) {
    promoCheck = validatePromo();
    if (!promoCheck.ok) return;
  }
  uni.showLoading({ title: isEdit.value ? '保存中' : '上架中' });
  const payload = {
    ...form,
    stock: form.stock ? Number(form.stock) : 9999,
  };
  try {
    if (isEdit.value) {
      await updateSpu({ id: editingId.value, ...payload });
      // 商品保存成功后立即持久化营销策略（一并保存语义）
      try {
        await persistPromo(promoCheck.poolDistJson);
      } catch (e) {
        uni.hideLoading();
        uni.showToast({ title: '商品已保存，但营销策略保存失败：' + (e?.msg || e?.message || ''), icon: 'none', duration: 2500 });
        return;
      }
      uni.hideLoading();
      uni.showToast({ title: '已保存（含营销策略）', icon: 'success' });
    } else {
      const newId = await createSpu(payload);
      uni.hideLoading();
      // 上架成功 → 跳到编辑态，让用户接着配「营销配置」（消费积分倍率 / 推 N 反 1 / 入池 等）
      // 否则新建商品后回列表，没机会设营销 → 用户找不到「消费积分倍率」入口（已经踩过）
      if (newId) {
        uni.showToast({ title: '上架成功，继续设置营销', icon: 'success', duration: 1200 });
        setTimeout(() => {
          uni.redirectTo({ url: `/pages/product/edit?id=${newId}` });
        }, 800);
        return;
      }
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
  // 显式预热商品分类，并把结果同步进响应式 ref（splice 不会触发 Vue 响应）
  loadCategories().then(syncCategoriesRef).catch(() => {});
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

.promo-hint {
  padding: 28rpx 32rpx;
  background: #fff8ec;
  border: 2rpx solid #fbd38d;
  .promo-title {
    font-size: 28rpx;
    font-weight: 700;
    color: $brand-primary;
    margin-bottom: 8rpx;
  }
  .promo-sub {
    font-size: 24rpx;
    color: $text-secondary;
    line-height: 1.6;
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

    .star-row-tip {
      margin-top: 8rpx;
      padding: 8rpx 12rpx;
      background: #fff5e8;
      border-radius: 8rpx;
      font-size: 22rpx;
      color: #8a5a00;
      line-height: 1.5;
      b { color: #e8721b; font-weight: 700; padding: 0 2rpx; }
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
