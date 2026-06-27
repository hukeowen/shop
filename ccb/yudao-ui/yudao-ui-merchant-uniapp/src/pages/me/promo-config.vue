<template>
  <view class="page">
    <!-- Banner -->
    <view class="banner">
      <text class="title">店铺营销设置（全局）</text>
      <text class="sub">推广积分提现 · 消费积分抵扣 · 自然队列 · 满减 · 推广奖励兜底</text>
      <text class="sub" style="margin-top:8rpx;font-size:22rpx;color:#9CA3AF;">注：星级、推 N 反 1、积分池均为商品级配置 → 商品列表 → 单品编辑</text>
    </view>

    <!-- 套餐档位提示 -->
    <view v-if="saasLevel === 'BASIC'" class="lvl-tip">
      ⚡ 当前为<text class="b">拓小二旺铺版</text>，店内 / 星级 / 奖池配置已锁定。如需启用请
      <text class="link" @click="goSubscription">升级到拓小二旗舰版</text>
    </view>

    <!-- ============ 推广积分 ============ -->
    <view class="card">
      <view class="section-title">推广积分</view>

      <view class="field">
        <text class="label">推广积分 → 消费积分 转换比例</text>
        <input
          class="input"
          type="digit"
          v-model="form.pointConversionRatio"
          placeholder="如 1.00"
        />
      </view>

      <view class="field">
        <text class="label">提现门槛（元）</text>
        <input
          class="input"
          type="number"
          v-model="form.withdrawThresholdYuan"
          placeholder="如 100"
        />
        <text class="hint inline">后端按分存储；输入元自动 ×100。</text>
      </view>
    </view>

    <!-- ============ 消费积分抵扣下单 ============ -->
    <view class="card">
      <view class="section-title">消费积分抵扣下单</view>
      <view class="hint">开启后，顾客下单时可勾选「使用消费积分抵扣」直接折抵订单金额。优先扣余额，余额不足才用积分。</view>

      <view class="row">
        <text class="label">开启消费积分抵扣</text>
        <switch
          :checked="form.consumePointRedeemEnabled"
          @change="(e) => (form.consumePointRedeemEnabled = e.detail.value)"
          color="#FF6B35"
        />
      </view>

      <view v-if="form.consumePointRedeemEnabled" class="field">
        <text class="label">折抵比例</text>
        <view class="input" style="color:#FF6B35;font-weight:700;">100 积分 = ¥1（1 积分 = 1 分）</view>
        <text class="hint inline">
          消费积分按 1:1 折抵现金（1 积分 = 1 分钱），固定不可改 —— 即 100 积分抵 ¥1。<br/>
          上限不超过订单总价的 100%（顾客可用积分全额抵扣）。
        </text>
      </view>
    </view>

    <!-- ============ v8 邀请激励 全局配置（商户级兜底）============ -->
    <view class="card">
      <view class="section-title">推广奖励（邀请激励 · 商户级兜底，商品级配置优先）</view>
      <view class="hint">用户必须先自购该商品才有资格享受邀请激励，完成 N 次累计后进入「终态」。</view>

      <view class="field">
        <text class="label">后续百分比（%）</text>
        <input class="input" type="digit" v-model="form.directCommissionRatio"
               placeholder="如 10（用户完成邀请激励 后，自购或朋友首单返此 % 推广积分）" />
        <text class="hint inline">完成邀请激励 后，自购或朋友首单都按订单实付总额 × 此 % 返推广积分。</text>
      </view>

    </view>

    <!-- ============ 自然队列（自然推） ============ -->
    <view class="card">
      <view class="section-title">自然队列（自然推）</view>
      <view class="hint">
        当一个顾客「没人推荐就直接进店下单」（无邀请人）时，这单产生的「推 N 反 1」奖励怎么分？<br/>
        - <text style="color:#16a34a;font-weight:600;">开启</text>：奖励进入「自然队列」，由排在队首的用户领走（先到先得；激励老顾客主动推广）<br/>
        - <text style="color:#999;">关闭</text>：奖励吞掉，店内没人拿（不鼓励自然顾客）
      </view>
      <view class="field row-switch" style="margin-top: 16rpx;">
        <text class="label" style="font-weight:600;">启用自然队列</text>
        <switch
          :checked="form.naturalPushEnabled"
          @change="(e) => (form.naturalPushEnabled = e.detail.value)"
          color="#FF6B35"
        />
      </view>
    </view>

    <!-- ============ 满减规则（仅文案展示，不参与结算） ============ -->
    <view class="card">
      <view class="section-title">满减规则</view>
      <view class="hint">商户在店铺详情页底部购物车展示「满 X 立减 Y · 还差 ¥N」。两项同时填才生效；不启用请留空。</view>
      <view class="field">
        <text class="label">满减门槛（元）</text>
        <input class="input" type="digit" v-model="form.fullCutThresholdYuan" placeholder="如 30（不启用留空）" />
      </view>
      <view class="field">
        <text class="label">减免金额（元）</text>
        <input class="input" type="digit" v-model="form.fullCutAmountYuan" placeholder="如 5" />
      </view>
    </view>

    <!-- 店级营销结束。星级 / 积分池 / 推 N 反 1 均为商品级，去商品编辑页配置。 -->

    <view class="safe-bottom">
      <button class="btn primary" :disabled="saving" @click="onSave">
        {{ saving ? '保存中…' : '保存配置' }}
      </button>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import {
  getShopPromoConfig,
  saveShopPromoConfig,
  getPoolInfo,
  settlePool,
  listPoolRounds,
} from '../../api/promo.js';
import { request } from '../../api/request.js';

const saving = ref(false);

// SaaS 套餐档位 — 决定店内/星级/奖池配置是否可见
const saasLevel = ref('PRO');  // 默认 PRO；加载后真值覆盖
const canUsePro = computed(() => saasLevel.value !== 'BASIC' && saasLevel.value !== 'EXPIRED');

function goSubscription() {
  uni.navigateTo({ url: '/pages/me/subscription' });
}
async function loadSaasLevel() {
  try {
    const st = await request({ url: '/app-api/merchant/mini/saas/my-status' });
    if (st && st.level) saasLevel.value = st.level;
  } catch {}
}

// 表单 — 与 PromoConfigSaveReqVO 字段对齐
const form = ref({
  starLevelCount: 5,
  pointConversionRatio: '1.00',
  withdrawThresholdYuan: '100',
  poolEnabled: false,
  poolRatio: '5.00',
  poolDistributeMode: 'ALL',
  poolSettleCron: '0 0 0 1 * ?',
  poolLotteryRatio: '5.00',
  poolSettleMode: 'FULL',
  fullCutThresholdYuan: '',
  fullCutAmountYuan: '',
  // v8 邀请激励
  directCommissionRatio: '',
  naturalPushEnabled: false,
  // 消费积分抵扣（前端以「元 / 积分」展示；后端存「分钱 / 积分」即 元*100）
  consumePointRedeemEnabled: false,
  consumePointRedeemRatio: '0.01',
});

// 星级行展开（rate%, directCount, teamSales）
const starList = ref([]);
// 入池星级勾选（数字数组，例如 [3,4,5]）
const poolStars = ref([]);

function ensureStarRows(n) {
  const target = Math.max(1, Math.min(10, parseInt(n) || 5));
  // 截断或补齐
  while (starList.value.length < target) {
    starList.value.push({ rate: '0', directCount: '0', teamSales: '0', discount: '' });
  }
  if (starList.value.length > target) starList.value.length = target;
  // 修剪 poolStars
  poolStars.value = poolStars.value.filter((s) => s <= target);
  return target;
}

function onStarCountChange() {
  form.value.starLevelCount = ensureStarRows(form.value.starLevelCount);
}

function togglePoolStar(i) {
  const idx = poolStars.value.indexOf(i);
  if (idx >= 0) poolStars.value.splice(idx, 1);
  else poolStars.value.push(i);
  poolStars.value.sort((a, b) => a - b);
}

function safeJsonArr(s, fallback) {
  try {
    const v = JSON.parse(s);
    return Array.isArray(v) ? v : fallback;
  } catch {
    return fallback;
  }
}

async function load() {
  try {
    const data = await getShopPromoConfig();
    if (!data) return;

    const count = data.starLevelCount || 5;
    form.value.starLevelCount = count;
    form.value.pointConversionRatio = String(data.pointConversionRatio ?? '1.00');
    form.value.withdrawThresholdYuan = String(((data.withdrawThreshold ?? 10000) / 100));
    form.value.poolEnabled = !!data.poolEnabled;
    form.value.poolRatio = String(data.poolRatio ?? '0.00');
    form.value.poolDistributeMode = data.poolDistributeMode || 'ALL';
    form.value.poolSettleCron = data.poolSettleCron || '0 0 0 1 * ?';
    form.value.poolLotteryRatio = String(data.poolLotteryRatio ?? '0.00');
    form.value.poolSettleMode = data.poolSettleMode || 'FULL';
    form.value.fullCutThresholdYuan = data.fullCutThreshold ? String(data.fullCutThreshold / 100) : '';
    form.value.fullCutAmountYuan = data.fullCutAmount ? String(data.fullCutAmount / 100) : '';
    // v7
    form.value.directCommissionRatio = data.directCommissionRatio != null
      ? String(data.directCommissionRatio) : '';
    form.value.naturalPushEnabled = !!data.naturalPushEnabled;
    // 消费积分抵扣 — 后端单位「分钱 / 积分」转「元 / 积分」展示
    form.value.consumePointRedeemEnabled = !!data.consumePointRedeemEnabled;
    if (data.consumePointRedeemRatio != null) {
      form.value.consumePointRedeemRatio = String(Number(data.consumePointRedeemRatio) / 100);
    }

    const rates = safeJsonArr(data.commissionRates, [1, 2, 3, 4, 5]);
    const rules = safeJsonArr(data.starUpgradeRules, []);
    // 折扣率：百分制；前端显示成「折」（90 → '9'，95 → '9.5'）。索引 0 = 0 星不显示，所以从 1 开始
    const discRates = safeJsonArr(data.starDiscountRates, []);

    starList.value = [];
    for (let i = 0; i < count; i++) {
      const dPct = Number(discRates[i + 1]); // i+1 因为 0 索引留给"非会员"
      starList.value.push({
        rate: String(rates[i] ?? 0),
        directCount: String(rules[i]?.directCount ?? 0),
        teamSales: String(rules[i]?.teamSales ?? 0),
        discount: Number.isFinite(dPct) && dPct > 0 && dPct < 100 ? String(dPct / 10) : '',
      });
    }
    poolStars.value = safeJsonArr(data.poolEligibleStars, []).map(Number);
  } catch {
    // 后端默认值已在 service 层兜底，error 静默
  }
}

async function onSave() {
  // 先保证 starList 与 starLevelCount 对齐
  onStarCountChange();

  // 简单前端校验
  const count = form.value.starLevelCount;
  if (count < 1) {
    uni.showToast({ title: '星级数量至少 1', icon: 'none' });
    return;
  }

  // 序列化
  const commissionRates = JSON.stringify(starList.value.map((s) => Number(s.rate) || 0));
  const starUpgradeRules = JSON.stringify(
    starList.value.map((s) => ({
      directCount: parseInt(s.directCount) || 0,
      teamSales: parseInt(s.teamSales) || 0,
    }))
  );
  // 折扣率序列化：长度 = count + 1，索引 0 = 100（非会员不打折），索引 i+1 = i 星折扣
  // 用户填的是「折」（9.5），存的是「百分制」（95）；空/无效 → 100（不打折）
  const starDiscountArr = [100];
  for (let i = 0; i < count; i++) {
    const z = parseFloat(starList.value[i]?.discount);
    starDiscountArr.push(Number.isFinite(z) && z > 0 && z < 10 ? Math.round(z * 10) : 100);
  }
  const starDiscountRates = JSON.stringify(starDiscountArr);
  const poolEligibleStars = JSON.stringify(poolStars.value);

  const conv = parseFloat(form.value.pointConversionRatio);
  if (!(conv > 0)) {
    uni.showToast({ title: '转换比例必须 > 0', icon: 'none' });
    return;
  }

  const req = {
    starLevelCount: count,
    commissionRates,
    starUpgradeRules,
    starDiscountRates,
    pointConversionRatio: conv,
    withdrawThreshold: Math.round((parseFloat(form.value.withdrawThresholdYuan) || 0) * 100),
    poolEnabled: !!form.value.poolEnabled,
    poolRatio: parseFloat(form.value.poolRatio) || 0,
    poolEligibleStars,
    poolDistributeMode: form.value.poolDistributeMode || 'ALL',
    poolSettleCron: form.value.poolSettleCron || '0 0 0 1 * ?',
    poolLotteryRatio: parseFloat(form.value.poolLotteryRatio) || 0,
    poolSettleMode: form.value.poolSettleMode || 'FULL',
    // 满减：两项都填了才视为启用，否则传 0（约定 0=不启用）。
    // 不能传 null —— 后端 saveConfig 用 ignoreNullValue copy，传 null 会保留旧值清不掉。
    fullCutThreshold: parseFloat(form.value.fullCutThresholdYuan) > 0
      ? Math.round(parseFloat(form.value.fullCutThresholdYuan) * 100) : 0,
    fullCutAmount: parseFloat(form.value.fullCutAmountYuan) > 0
      ? Math.round(parseFloat(form.value.fullCutAmountYuan) * 100) : 0,
    // v7：直接百分制（10 = 10%）；空 = 0
    directCommissionRatio: parseFloat(form.value.directCommissionRatio) > 0
      ? parseFloat(form.value.directCommissionRatio) : 0,
    naturalPushEnabled: !!form.value.naturalPushEnabled,
    // 消费积分折抵恒为 1:1（1 积分=1 分=¥0.01 → 100 积分=¥1），固定不可改
    consumePointRedeemEnabled: !!form.value.consumePointRedeemEnabled,
    consumePointRedeemRatio: 1,
  };

  saving.value = true;
  try {
    await saveShopPromoConfig(req);
    uni.showToast({ title: '已保存', icon: 'success' });
  } finally {
    saving.value = false;
  }
}

// ============ 积分池运营 ============
const poolBalance = ref(0);
const lastSettledAt = ref(null);
const rounds = ref([]);
const settling = ref(false);

function formatTime(ts) {
  if (!ts) return '-';
  const d = new Date(ts);
  if (isNaN(d.getTime())) return ts;
  const pad = (n) => (n < 10 ? '0' + n : n);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

async function loadPool() {
  try {
    const [info, page] = await Promise.all([
      getPoolInfo(),
      listPoolRounds({ pageNo: 1, pageSize: 10 }),
    ]);
    if (info) {
      poolBalance.value = info.balance ?? 0;
      lastSettledAt.value = info.lastSettledAt;
    }
    rounds.value = page?.list || [];
  } catch {
    // 静默
  }
}

async function onSettle(mode) {
  const modeLabel = mode === 'LOTTERY' ? '抽奖结算' : '全员均分结算';
  const r = await uni.showModal({
    title: '确认结算',
    content: `本次将以"${modeLabel}"模式分发当前池余额 ${poolBalance.value} 分。结算后池清零，无法撤销。`,
  });
  if (!r.confirm) return;
  settling.value = true;
  try {
    const round = await settlePool(mode);
    if (round) {
      uni.showToast({
        title: `结算完成：${round.winnerCount} 人中奖`,
        icon: 'success',
      });
    } else {
      uni.showToast({ title: '无可结算（无参与用户 / 抽奖人数=0）', icon: 'none' });
    }
    await loadPool();
  } finally {
    settling.value = false;
  }
}

onMounted(async () => {
  ensureStarRows(form.value.starLevelCount);
  await Promise.all([load(), loadPool(), loadSaasLevel()]);
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.lvl-tip {
  background: #fff8e6;
  border: 1rpx solid #ffd980;
  border-radius: $radius-md;
  padding: 20rpx 28rpx;
  margin: 0 24rpx 24rpx;
  font-size: 26rpx;
  color: #d97706;
  line-height: 1.6;

  .b { font-weight: 700; }
  .link {
    color: $brand-primary;
    text-decoration: underline;
    margin: 0 4rpx;
  }
}

.page {
  padding: 24rpx 24rpx 220rpx;
  min-height: 100vh;
}

.banner {
  padding: 24rpx 8rpx 32rpx;

  .title {
    display: block;
    font-size: 36rpx;
    font-weight: 700;
    color: $text-primary;
  }
  .sub {
    display: block;
    margin-top: 8rpx;
    font-size: 24rpx;
    color: $text-secondary;
  }
}

.card {
  background: $bg-card;
  border-radius: $radius-lg;
  padding: 28rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.section-title {
  font-size: 28rpx;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 16rpx;
}

.hint {
  font-size: 24rpx;
  color: $text-secondary;
  line-height: 1.5;

  &.inline {
    margin-top: 8rpx;
    display: block;
  }
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16rpx 0;

  .label {
    font-size: 28rpx;
    color: $text-primary;
  }
}

.field {
  padding: 12rpx 0;

  .label {
    display: block;
    font-size: 26rpx;
    color: $text-secondary;
    margin-bottom: 10rpx;
  }
}

.input {
  width: 100%;
  height: 80rpx;
  padding: 0 24rpx;
  background: #f6f7f9;
  border-radius: $radius-md;
  font-size: 28rpx;
  color: $text-primary;
  line-height: 80rpx;
  box-sizing: border-box;
}

.star-row {
  display: flex;
  gap: 16rpx;
  padding: 16rpx 0;
  border-top: 1rpx dashed $border-color;

  .star-tag {
    flex: 0 0 80rpx;
    height: 60rpx;
    margin-top: 36rpx;
    line-height: 60rpx;
    text-align: center;
    background: linear-gradient(135deg, #ffd6b8, #ff6b35);
    color: #fff;
    border-radius: $radius-md;
    font-size: 24rpx;
    font-weight: 600;
  }

  .star-fields {
    flex: 1;
    display: grid;
    grid-template-columns: 1fr 1fr 1fr;
    gap: 12rpx;
  }
}

.checks {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.check-chip,
.radio-chip {
  padding: 12rpx 24rpx;
  background: #f6f7f9;
  border-radius: 999rpx;
  font-size: 26rpx;
  color: $text-secondary;
  border: 1rpx solid transparent;

  &.active {
    background: rgba(255, 107, 53, 0.12);
    color: $brand-primary;
    border-color: $brand-primary;
    font-weight: 600;
  }
}

.radio-row {
  display: flex;
  gap: 16rpx;
}

// ============ 积分池运营 ============
.pool-summary {
  display: flex;
  gap: 16rpx;
  margin-bottom: 24rpx;

  .pool-item {
    flex: 1;
    background: linear-gradient(135deg, rgba(255, 107, 53, 0.08), rgba(255, 154, 74, 0.06));
    border-radius: $radius-md;
    padding: 20rpx;

    .label {
      display: block;
      font-size: 22rpx;
      color: $text-secondary;
    }

    .value {
      display: block;
      margin-top: 6rpx;
      font-size: 40rpx;
      font-weight: 700;
      color: $brand-primary;

      .unit {
        font-size: 22rpx;
        font-weight: 400;
      }
    }

    .sub {
      display: block;
      margin-top: 6rpx;
      font-size: 22rpx;
      color: $text-secondary;
    }
  }
}

.settle-row {
  display: flex;
  gap: 16rpx;
  margin-top: 8rpx;

  .btn.ghost-brand {
    flex: 1;
    height: 80rpx;
    line-height: 80rpx;
    background: rgba(255, 107, 53, 0.08);
    color: $brand-primary;
    border: 2rpx solid rgba(255, 107, 53, 0.4);
    font-size: 26rpx;
    font-weight: 600;
    border-radius: $radius-md;

    &[disabled] {
      opacity: 0.5;
    }

    &::after {
      border: none;
    }
  }
}

.rounds-title {
  margin-top: 24rpx;
  margin-bottom: 12rpx;
  font-size: 26rpx;
  color: $text-secondary;
  font-weight: 500;
}

.empty {
  text-align: center;
  padding: 24rpx 0;
  font-size: 24rpx;
  color: $text-placeholder;
}

.round-item {
  padding: 14rpx 0;
  border-bottom: 1rpx solid $border-color;

  &:last-child {
    border-bottom: none;
  }

  .round-row1 {
    display: flex;
    justify-content: space-between;

    .amt {
      font-size: 28rpx;
      font-weight: 600;
      color: $text-primary;
    }

    .mode {
      font-size: 22rpx;
      color: $brand-primary;
      padding: 2rpx 12rpx;
      background: rgba(255, 107, 53, 0.08);
      border-radius: 999rpx;
    }
  }

  .round-row2 {
    margin-top: 6rpx;
    display: flex;
    justify-content: space-between;
    font-size: 22rpx;
    color: $text-secondary;
  }
}

.safe-bottom {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 24rpx 32rpx calc(env(safe-area-inset-bottom) + 24rpx);
  background: #fff;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.05);
}

.btn {
  width: 100%;
  height: 96rpx;
  line-height: 96rpx;
  font-size: 32rpx;
  font-weight: 600;
  border-radius: $radius-md;

  &.primary {
    background: $brand-primary;
    color: #fff;
  }

  &[disabled] {
    background: $text-placeholder;
    color: #fff;
    opacity: 0.7;
  }

  &::after {
    border: none;
  }
}
</style>
