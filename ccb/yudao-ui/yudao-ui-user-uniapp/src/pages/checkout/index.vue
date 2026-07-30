<template>
  <view class="page">
    <view :style="sbhSpacer"></view>
    <view class="topbar safe-top">
      <text class="back" @click="goBack">‹</text>
      <text class="title">订单结算</text>
      <text class="right"></text>
    </view>

    <view v-if="loading" class="empty-tip">加载中...</view>

    <template v-else>
      <!-- 收货地址 -->
      <view class="ck-addr" v-if="deliveryType === 1" @click="pickAddress">
        <view class="ic">📍</view>
        <view class="body">
          <view v-if="addressId" class="row1">
            <text class="name">{{ receiverName || '我' }}</text>
            <text class="phone">{{ receiverMobile || '' }}</text>
          </view>
          <view v-if="addressId" class="row2">{{ receiverAddress || '点击选择地址' }}</view>
          <template v-else>
            <view class="row1 placeholder">请选择收货地址</view>
            <view class="row2 placeholder">点击此处选择 / 新增收货地址</view>
          </template>
        </view>
        <text class="arrow">›</text>
      </view>

      <!-- 配送方式 -->
      <view class="ck-shop">
        <view class="gh">
          <text class="name">配送方式</text>
        </view>
        <view class="pay-options">
          <view :class="['pay-option', deliveryType === 2 ? 'active' : '']" @click="deliveryType = 2">🏪 到店自提</view>
          <view :class="['pay-option', deliveryType === 1 ? 'active' : '']" @click="deliveryType = 1">🚚 快递发货</view>
        </view>
      </view>

      <!-- 商品清单（按店分组，但 checkout 单店） -->
      <view class="ck-shop">
        <view class="gh">
          <view class="pic" :style="picStyle">{{ initial }}</view>
          <text class="name">{{ shopName }}</text>
        </view>
        <view v-for="(it, i) in items" :key="i" class="ck-row">
          <image v-if="itemPic(it)" class="ck-pic-img" :src="itemPic(it)" mode="aspectFill" />
          <view v-else class="ck-pic" :style="itemPicStyle(it)">{{ pickEmoji(it) }}</view>
          <view class="info">
            <view class="iname">{{ itemName(it) }}</view>
            <view class="spec" v-if="it.skuName || it.sku?.properties?.length">{{ it.skuName || it.sku?.properties?.map(p => p.valueName).join(' / ') }}</view>
          </view>
          <view class="right">
            <view class="price">¥{{ fen2yuan(itemPrice(it)) }}</view>
            <!-- 直购模式（无 cartIds）显示数量调整器；购物车模式只读，避免与购物车记录不同步 -->
            <view v-if="!cartIds.length" class="qty-step">
              <text class="qs-btn" :class="{ disabled: (it.count || 1) <= 1 }" @click="changeQty(i, -1)">−</text>
              <text class="qs-n">{{ it.count || 1 }}</text>
              <text class="qs-btn" @click="changeQty(i, 1)">+</text>
            </view>
            <view v-else class="qty">x {{ it.count || 1 }}</view>
          </view>
        </view>
        <view class="ck-fee">
          <text>商品小计</text>
          <text class="v">¥{{ fen2yuan(itemsFen) }}</text>
        </view>
        <view class="ck-fee" v-if="deliveryType === 1">
          <text>配送费</text>
          <text class="v">¥{{ fen2yuan(deliveryFen) }}</text>
        </view>
        <view class="ck-fee total">
          <text>店铺合计</text>
          <text class="v">¥{{ fen2yuan(grossFen) }}</text>
        </view>
      </view>

      <!-- 备注 -->
      <view class="ck-shop">
        <view class="gh"><text class="name" style="font-weight:400;color:#5a6577;">订单备注</text></view>
        <view class="remark-input">
          <input v-model="remark" placeholder="如：少糖 / 不要葱…" maxlength="50" />
        </view>
      </view>

      <!-- 组合支付 - 店铺资产抵扣 -->
      <view class="ck-method">
        <view class="gh">店铺资产抵扣</view>
        <!-- 店铺余额抵扣（订单创建后调用 deduct-for-order 幂等扣减） -->
        <view class="m-row" :class="{ disabled: !balanceEnabled }">
          <view class="m-icon i-balance">余</view>
          <view class="m-info">
            <view class="m-name">{{ shopName }} · 店铺余额</view>
            <view class="m-sub" v-if="balanceEnabled">余 ¥{{ fen2yuan(userBalance) }} · 最多抵 ¥{{ fen2yuan(balanceDeductCap) }}</view>
            <view class="m-sub" v-else>该店余额为 0，请先去"我的钱包"充值或邀请好友获积分</view>
          </view>
          <view class="m-trail">
            <text class="amt" :class="{ off: !useBalance }">{{ useBalance ? `-¥${fen2yuan(balanceDeductFen)}` : '未使用' }}</text>
            <view class="switch" :class="{ on: useBalance && balanceEnabled }" @click="toggleBalance">
              <view class="dot"></view>
            </view>
          </view>
        </view>
        <!-- 消费积分抵扣（仅当商户开启且用户有积分时显示） -->
        <view class="m-row" :class="{ disabled: !consumePointAvailable }">
          <view class="m-icon i-points">分</view>
          <view class="m-info">
            <view class="m-name">消费积分抵扣</view>
            <view class="m-sub" v-if="consumePointAvailable">
              余 ¥{{ fen2yuan(userConsumePoints) }} · 最多抵 ¥{{ fen2yuan(maxConsumePointDeductFen) }}
            </view>
            <view class="m-sub" v-else>积分余额不足或剩余订单已无可抵</view>
          </view>
          <view class="m-trail">
            <text class="amt" :class="{ off: !useConsumePoint }">{{ useConsumePoint ? `-¥${fen2yuan(consumePointDeductFen)}` : '未使用' }}</text>
            <view class="switch" :class="{ on: useConsumePoint && consumePointAvailable }" @click="toggleConsumePoint">
              <view class="dot"></view>
            </view>
          </view>
        </view>
        <!-- 推广积分抵扣（用户主动；1 推广积分 = 1 分钱） -->
        <view class="m-row" :class="{ disabled: !promoPointAvailable }">
          <view class="m-icon i-points">推</view>
          <view class="m-info">
            <view class="m-name">推广积分抵扣</view>
            <view class="m-sub" v-if="promoPointAvailable">
              余 ¥{{ fen2yuan(userPromoPoints) }} · 最多抵 ¥{{ fen2yuan(maxPromoPointDeductFen) }}
            </view>
            <view class="m-sub" v-else>推广积分余额为 0 或剩余订单已无可抵</view>
          </view>
          <view class="m-trail">
            <text class="amt" :class="{ off: !usePromoPoint }">{{ usePromoPoint ? `-¥${fen2yuan(promoPointDeductFen)}` : '未使用' }}</text>
            <view class="switch" :class="{ on: usePromoPoint && promoPointAvailable }" @click="togglePromoPoint">
              <view class="dot"></view>
            </view>
          </view>
        </view>
        <!-- 抵扣后还需在线支付 -->
        <view class="m-fee-final">
          <text>抵扣后还需在线支付</text>
          <text class="v">¥{{ fen2yuan(remainFen) }}</text>
        </view>
      </view>

      <!-- 在线支付方式 -->
      <view class="ck-method">
        <view class="gh">在线支付剩余 ¥{{ fen2yuan(remainFen) }}</view>
        <view class="m-row" :class="{ active: payType === 'wx' }" @click="payType = 'wx'">
          <view class="m-icon i-wx">微</view>
          <view class="m-info">
            <view class="m-name">微信支付</view>
            <view class="m-sub">推荐</view>
          </view>
          <view class="radio" :class="{ on: payType === 'wx' }"></view>
        </view>
        <view class="m-row" :class="{ active: payType === 'offline' }" @click="payType = 'offline'" v-if="!onlinePayEnabled">
          <view class="m-icon i-offline">店</view>
          <view class="m-info">
            <view class="m-name">到店付款</view>
            <view class="m-sub">商家未开通在线支付</view>
          </view>
          <view class="radio" :class="{ on: payType === 'offline' }"></view>
        </view>
      </view>

      <!-- v8 推广积分抵扣预演（仅在有可抵扣商品时展示） -->
      <view class="promo-deduct-card" v-if="promoPreview && promoPreview.deductFen > 0">
        <view class="pd-head">
          <text class="pd-title">🎁 邀请激励 抵扣</text>
          <text class="pd-tag">少付 ¥{{ fen2yuan(promoPreview.deductFen) }}</text>
        </view>
        <view class="pd-row" v-for="(line, i) in promoPreview.items.filter(x => x.deductCount > 0)" :key="i">
          <text class="pd-line">商品 #{{ line.spuId }}：买 {{ line.count }} 件 → 抵扣 <text class="pd-em">{{ line.deductCount }}</text> 件</text>
          <text class="pd-line-sub">单价 ¥{{ fen2yuan(line.unitPrice) }} × 抵扣 {{ line.deductCount }} 件 = -¥{{ fen2yuan(line.deductFen) }}</text>
        </view>
        <view class="pd-totals">
          <view class="pd-total-row">
            <text>抵扣前</text>
            <text class="pd-old">¥{{ fen2yuan(promoPreview.originalPay) }}</text>
          </view>
          <view class="pd-total-row pd-final">
            <text>抵扣后</text>
            <text class="pd-new">¥{{ fen2yuan(promoPreview.finalPay) }}</text>
          </view>
        </view>
      </view>

      <!-- 优惠券选择 -->
      <view class="card coupon-card" v-if="usableCoupons.length">
        <view class="card-title">优惠券（{{ usableCoupons.length }} 张可用）</view>
        <view
          v-for="c in usableCoupons"
          :key="c.id"
          :class="['coupon-row', selectedCouponId === c.id ? 'on' : '', grossFen < (c.minAmount || 0) ? 'disabled' : '']"
          @click="onPickCoupon(c)"
        >
          <view class="cv-amt">¥{{ fen2yuan(c.discountAmount) }}</view>
          <view class="cv-info">
            <view class="cv-name">满 ¥{{ fen2yuan(c.minAmount) }} 可用</view>
            <view class="cv-sub">{{ formatExpire(c.expireTime) }}</view>
          </view>
          <view class="cv-radio">{{ selectedCouponId === c.id ? '✓' : '○' }}</view>
        </view>
      </view>

      <!-- 组合明细 -->
      <view class="form-tip">
        <text class="b">支付明细：</text><br>
        · 店铺余额抵扣：<text class="hl">{{ useBalance ? `-¥${fen2yuan(balanceDeductFen)}` : '未启用' }}</text><br>
        · 消费积分抵扣：<text class="hl">{{ useConsumePoint ? `-¥${fen2yuan(consumePointDeductFen)}` : '未启用' }}</text><br>
        · 推广积分抵扣：<text class="hl">{{ usePromoPoint ? `-¥${fen2yuan(promoPointDeductFen)}` : '未启用' }}</text><br>
        · 推广积分自动抵：<text class="hl">{{ promoPreview && promoPreview.deductFen > 0 ? `-¥${fen2yuan(promoPreview.deductFen)}（${promoPreview.deductCount} 件）` : '当前无可抵扣商品' }}</text><br>
        · 优惠券抵扣：<text class="hl">{{ selectedCoupon ? `-¥${fen2yuan(selectedCoupon.discountAmount)}` : '未选用' }}</text><br>
        · 在线支付：<text class="hl">¥{{ fen2yuan(finalRemainFen) }}</text>
      </view>

      <view class="bottom-space"></view>

      <!-- 底部 CTA -->
      <view class="ck-bottom safe-bottom">
        <view class="total">
          合计 ¥{{ fen2yuan(grossFen) }} ·
          实付 <text class="b">¥{{ fen2yuan(finalRemainFen) }}</text>
          <text v-if="promoPreview && promoPreview.deductFen > 0" class="save-tag">省 ¥{{ fen2yuan(promoPreview.deductFen) }}</text>
        </view>
        <view class="pay-btn" @click="submitOrder">提交订单</view>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import { onLoad, onShow } from '@dcloudio/uni-app';
import { request } from '@/utils/request.js';
import { fen2yuan } from '@/utils/format.js';
import { launchMpCashier } from '@/utils/mpPay.js';
import { sbhSpacerStyle } from '@/utils/safeTop.js';
const sbhSpacer = sbhSpacerStyle();

const tenantId = ref(null);
const cartIds = ref([]);
const spuId = ref(null);
const skuId = ref(null);
const count = ref(1);
const items = ref([]);
const loading = ref(false);
const remark = ref('');
const deliveryType = ref(2);
const addressId = ref(null);
const receiverName = ref('');
const receiverMobile = ref('');
const receiverAddress = ref('');
const onlinePayEnabled = ref(true);
const payType = ref('wx');
// v8 推广积分抵扣预演结果（onMounted 后由 loadPromoPreview 填）
const promoPreview = ref(null);
const shopName = ref('');
const usePoints = ref(false);
const useBalance = ref(false);
const userPoints = ref(0);
const pointPerYuan = ref(100); // 100 分 = ¥1（trade 默认）
const userBalance = ref(0);

// ===== 消费积分抵扣 =====
const consumePointEnabled = ref(false);              // 商户开关
const consumePointRatio = ref(1);                    // 1 积分=X 分钱（后端单位）
const userConsumePoints = ref(0);                    // 用户当前消费积分余额（分=积分数量）
const useConsumePoint = ref(false);                  // 用户在 checkout 勾选了抵扣

// 单个积分能抵的分钱数 = ratio（后端就是 "1 积分 = X 分钱"）
// 最多能抵扣的分钱 = floor(points * ratio)，并按 100% 订单价封顶
const maxConsumePointDeductFen = computed(() => {
  if (userConsumePoints.value <= 0 || consumePointRatio.value <= 0) return 0;
  // 抵扣后剩余 = 商品小计 - 余额抵扣，再用积分抵这部分
  const remainAfterBalance = Math.max(0, grossFen.value - balanceDeductFen.value);
  const byPoints = Math.floor(userConsumePoints.value * consumePointRatio.value);
  return Math.min(byPoints, remainAfterBalance);
});
const consumePointAvailable = computed(() => maxConsumePointDeductFen.value > 0);
const consumePointDeductFen = computed(() =>
  useConsumePoint.value && consumePointAvailable.value ? maxConsumePointDeductFen.value : 0
);
function toggleConsumePoint() {
  if (!consumePointAvailable.value) {
    uni.showToast({ title: '消费积分不足或订单无可抵', icon: 'none' });
    return;
  }
  useConsumePoint.value = !useConsumePoint.value;
}

// ===== 推广积分抵扣（1 推广积分 = 1 分钱） =====
const userPromoPoints = ref(0);
const usePromoPoint = ref(false);
const maxPromoPointDeductFen = computed(() => {
  if (userPromoPoints.value <= 0) return 0;
  // 抵扣顺序：余额 → 消费积分 → 推广积分，三者叠后须留 1 分线上支付
  const remain = Math.max(0, grossFen.value - balanceDeductFen.value - consumePointDeductFen.value);
  return Math.min(userPromoPoints.value, remain);
});
const promoPointAvailable = computed(() => maxPromoPointDeductFen.value > 0);
const promoPointDeductFen = computed(() =>
  usePromoPoint.value && promoPointAvailable.value ? maxPromoPointDeductFen.value : 0
);
function togglePromoPoint() {
  if (!promoPointAvailable.value) {
    uni.showToast({ title: '推广积分余额不足或订单无可抵', icon: 'none' });
    return;
  }
  usePromoPoint.value = !usePromoPoint.value;
}

// balanceEnabled 不再写死 false：只要余额 > 0 即开放抵扣
const balanceEnabled = computed(() => (userBalance.value || 0) > 0);

function toggleBalance() {
  if (!balanceEnabled.value) {
    uni.showToast({ title: '该店余额为 0', icon: 'none' });
    return;
  }
  useBalance.value = !useBalance.value;
}

const initial = computed(() => (shopName.value || '店')[0]);
const picStyle = computed(() => {
  const palette = ['#ffd1ba,#ff6b35', '#c9e0ff,#6196f0', '#d3f4d3,#4cb84c', '#ffd0dc,#ee5a8b'];
  const idx = (Number(tenantId.value) || 0) % palette.length;
  return `background: linear-gradient(135deg, ${palette[idx]});`;
});
const itemPicStyle = (it) => {
  const palette = ['#ffe1c8,#ffae74', '#d6e9ff,#80b3ff', '#d8f5d6,#6fcf6f', '#ffd6e0,#ff8aa7'];
  const idx = (Number(it.skuId || it.id) || 0) % palette.length;
  return `background: linear-gradient(135deg, ${palette[idx]});`;
};
function pickEmoji(it) {
  const n = itemName(it);
  if (/(地瓜|薯)/.test(n)) return '🍠';
  if (/(玉米)/.test(n)) return '🌽';
  if (/(茶|奶茶)/.test(n)) return '🍵';
  if (/(果|莓|葡萄)/.test(n)) return '🍇';
  if (/(肉|串|烧)/.test(n)) return '🍖';
  if (/(咖啡)/.test(n)) return '☕';
  return '🛍';
}
// 兼容 trade/cart/list 嵌套 spu/sku 结构
function itemName(it) { return it.spuName || it.spu?.name || it.name || '商品'; }
function itemPic(it) { return it.picUrl || it.sku?.picUrl || it.spu?.picUrl || ''; }
function itemPrice(it) { return it.price || it.sku?.price || it.spu?.price || 0; }

const itemsFen = computed(() => items.value.reduce((s, i) => s + itemPrice(i) * (i.count || 1), 0));
const deliveryFen = computed(() => deliveryType.value === 1 ? 300 : 0); // 简化：快递固定 ¥3，正式接后端 freight
const grossFen = computed(() => itemsFen.value + deliveryFen.value);
const maxPointDeductFen = computed(() => {
  if (!userPoints.value || !pointPerYuan.value) return 0;
  // 100 分 = 1 元 = 100 分（人民币分）
  return Math.floor(userPoints.value / pointPerYuan.value) * 100;
});
const pointDeductFen = computed(() => {
  if (!usePoints.value) return 0;
  return Math.min(maxPointDeductFen.value, grossFen.value);
});
const balanceDeductCap = computed(() => {
  // 抵扣上限 = min(余额, 商品小计抵扣后剩余)；要求至少留 ¥0.01 在线支付路径，但也可全额抵
  const remainAfterPoint = Math.max(0, grossFen.value - pointDeductFen.value);
  return Math.min(userBalance.value || 0, remainAfterPoint);
});
const balanceDeductFen = computed(() => useBalance.value && balanceEnabled.value ? balanceDeductCap.value : 0);
const remainFen = computed(() => Math.max(0, grossFen.value - pointDeductFen.value - balanceDeductFen.value - consumePointDeductFen.value - promoPointDeductFen.value));

// 优惠券
const usableCoupons = ref([]);
const selectedCouponId = ref(null);
const selectedCoupon = computed(() => usableCoupons.value.find(c => c.id === selectedCouponId.value) || null);
const couponDeductFen = computed(() => {
  const c = selectedCoupon.value;
  if (!c) return 0;
  if (grossFen.value < (c.minAmount || 0)) return 0;
  return c.discountAmount || 0;
});

// v8: 在 remainFen 基础上再减"推广积分预演抵扣 + 优惠券"，得到底部展示的最终实付
const finalRemainFen = computed(() => {
  const base = remainFen.value;
  const promoDeduct = (promoPreview.value && promoPreview.value.deductFen) || 0;
  return Math.max(0, base - promoDeduct - couponDeductFen.value);
});

function onPickCoupon(c) {
  if (grossFen.value < (c.minAmount || 0)) {
    uni.showToast({ title: `订单金额未达 ¥${(c.minAmount / 100).toFixed(2)}`, icon: 'none' });
    return;
  }
  selectedCouponId.value = selectedCouponId.value === c.id ? null : c.id;
}
function formatExpire(ts) {
  if (!ts) return '';
  const d = new Date(ts);
  if (isNaN(d.getTime())) return '';
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} 到期`;
}
async function loadUsableCoupons() {
  if (!tenantId.value) return;
  try {
    const list = await request({ url: `/app-api/merchant/mini/coupon/usable?tenantId=${tenantId.value}` });
    usableCoupons.value = Array.isArray(list) ? list : [];
  } catch { usableCoupons.value = []; }
}

async function loadShopAndItems() {
  loading.value = true;
  try {
    // 1. 拉店铺信息
    const shop = await request({ url: `/app-api/merchant/shop/public/info?tenantId=${tenantId.value}` }).catch(() => null);
    shopName.value = shop?.shopName || `店铺 #${tenantId.value}`;
    onlinePayEnabled.value = !!shop?.onlinePayEnabled;
    if (!onlinePayEnabled.value) payType.value = 'offline';
    // 2. 拉用户在该店的资产（按 query.tenantId 跨租户找用户在该店的 member-rel 记录）
    //    去掉 header tenantId，避免与用户 token tenant 冲突触发 401
    try {
      const rel = await request({ url: `/app-api/merchant/mini/member-rel/my?tenantId=${tenantId.value}` });
      userBalance.value = rel?.balance || 0;
      userPoints.value = rel?.points || 0;
    } catch {}
    // 2.1 拉商户营销配置（看是否启用消费积分抵扣 + 比例）
    try {
      const cfg = await request({ url: `/app-api/merchant/mini/promo/config?tenantId=${tenantId.value}` });
      consumePointEnabled.value = !!cfg?.consumePointRedeemEnabled;
      // 积分=分，折抵恒 1:1（100 积分=¥1），锁死 1 不再受商户配置影响（防 100× 超抵显示）
      consumePointRatio.value = 1;
    } catch {}
    // 2.2 拉用户在该 merchant tenant 的双积分余额（与扣减侧用同一 tenant）
    try {
      const acct = await request({
        url: '/app-api/merchant/mini/promo/account',
        tenantId: tenantId.value,
      });
      userConsumePoints.value = Number(acct?.consumePointBalance || 0);
      userPromoPoints.value = Number(acct?.promoPointBalance || 0);
    } catch {}
    // 3. 拉商品（按 cartIds 或 skuId）— 购物车按用户自身 tenant 走
    if (cartIds.value.length) {
      const res = await request({ url: '/app-api/trade/cart/list' });
      const all = (res && res.validList) || (res && res.list) || (Array.isArray(res) ? res : []);
      items.value = all.filter(i => cartIds.value.includes(i.id));
    } else if (skuId.value) {
      // 单品立即购买：按 spuId 拉详情拿 sku.price / spuName / picUrl / stock
      let realPrice = 0;
      let spuName = '商品';
      let picUrl = '';
      let stock = 999; // 数量步进时做库存上限校验，没拉到 sku.stock 时给个宽松默认
      if (spuId.value) {
        try {
          const spuDetail = await request({ url: `/app-api/product/spu/get-detail?id=${spuId.value}` });
          spuName = spuDetail?.name || '商品';
          picUrl = spuDetail?.picUrl || '';
          const sku = (spuDetail?.skus || []).find(s => s.id === skuId.value);
          realPrice = sku?.price ?? spuDetail?.price ?? 0;
          if (sku && typeof sku.stock === 'number' && sku.stock > 0) stock = sku.stock;
        } catch {}
      }
      items.value = [{ skuId: skuId.value, count: count.value, spuName, name: spuName, picUrl, price: realPrice, stock }];
    }
    // 4. v8 预演推广积分抵扣（仅在拉到 items 后调一次）— 不影响主流程，失败仅静默
    await loadPromoPreview();
  } catch (e) {
    uni.showToast({ title: '加载失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
}

/**
 * 直购模式：±调整商品数量，立即触发邀请激励 预演重算。
 * 库存上限按 it.stock（loadShopAndItems 时从 sku.stock 取）。
 */
function changeQty(idx, delta) {
  const it = items.value[idx];
  if (!it) return;
  const cur = it.count || 1;
  const next = cur + delta;
  if (next < 1) return;
  const max = it.stock || 999;
  if (next > max) {
    uni.showToast({ title: `库存仅 ${max} 件`, icon: 'none' });
    return;
  }
  it.count = next;
  // 用户连点 + 时合并请求；250ms 节流足够 UI 跟手
  debouncedPreview();
}

let _previewTimer = null;
function debouncedPreview() {
  if (_previewTimer) clearTimeout(_previewTimer);
  _previewTimer = setTimeout(() => loadPromoPreview(), 250);
}

/**
 * v8 抵扣预演：把当前 items 提交给后端 preview-deduction 接口算每个 SPU 的可抵扣件数。
 * 接口纯只读、不改库；只要 items.value 一变（数量/规格切换）就重算。
 */
async function loadPromoPreview() {
  if (!items.value.length || !tenantId.value) {
    promoPreview.value = null;
    return;
  }
  const payload = {
    tenantId: tenantId.value,
    items: items.value.map(it => ({
      skuId: it.skuId || it.sku?.id,
      count: it.count || 1,
    })).filter(x => x.skuId),
  };
  if (!payload.items.length) { promoPreview.value = null; return; }
  try {
    const res = await request({
      url: '/app-api/merchant/mini/checkout/preview-deduction',
      method: 'POST',
      data: payload,
    });
    promoPreview.value = res || null;
  } catch (e) {
    promoPreview.value = null;
  }
}

async function submitOrder() {
  if (!items.value.length) return;
  if (deliveryType.value === 1 && !addressId.value) {
    uni.showToast({ title: '请先选择收货地址', icon: 'none' });
    return;
  }
  if (useBalance.value && balanceDeductFen.value > 0 && balanceDeductFen.value > userBalance.value) {
    uni.showToast({ title: '余额不足', icon: 'none' });
    return;
  }
  // 必须保留至少 1 分线上支付（updateOrderPrice 不允许 0 元订单）
  // 仅在「商品小计 - 消费积分抵扣」结果 > 余额抵扣时才能走余额；否则前端钳制
  let safeBalanceFen = balanceDeductFen.value;
  if (useBalance.value && safeBalanceFen > 0) {
    const afterPoint = grossFen.value - pointDeductFen.value;
    if (safeBalanceFen >= afterPoint) {
      safeBalanceFen = Math.max(0, afterPoint - 1);
    }
  }
  if (useBalance.value && safeBalanceFen <= 0) {
    uni.showModal({
      title: '无法仅用余额支付',
      content: '当前订单金额过小，至少需保留 ¥0.01 走线上支付。请取消余额抵扣或加大订单金额。',
      showCancel: false,
    });
    return;
  }
  try {
    uni.showLoading({ title: '提交中...' });
    const settleItems = items.value.map(it => ({
      cartId: it.id || undefined,
      skuId: it.skuId || it.sku?.id,
      count: it.count || 1,
    }));
    // 单事务下单接口：trade/order/create + 余额扣减 + 改价 一把梭
    const reqData = {
      // 关键：tenantId 放 body，不放 header。header 设商户 tenantId 会被
      // TenantSecurityWebFilter 拦 403（user.tenantId ≠ ctx.tenantId）。
      // 后端 submit 按 body.tenantId 切 TenantContextHolder 写订单到商户租户。
      tenantId: tenantId.value,
      order: {
        items: settleItems,
        pointStatus: false,
        deliveryType: deliveryType.value,
        addressId: addressId.value || undefined,
        receiverName: receiverName.value || undefined,
        receiverMobile: receiverMobile.value || undefined,
        remark: remark.value || undefined,
      },
      useShopBalance: useBalance.value,
      balanceFen: useBalance.value ? safeBalanceFen : 0,
      // 消费积分抵扣：传分钱金额，后端按 ratio 反推积分数量 + 扣减 + 写流水
      useConsumePoint: useConsumePoint.value,
      consumePointDeductFen: useConsumePoint.value ? consumePointDeductFen.value : 0,
      // 推广积分抵扣：1:1 fen，无 ratio
      usePromoPoint: usePromoPoint.value,
      promoPointDeductFen: usePromoPoint.value ? promoPointDeductFen.value : 0,
      couponUserId: selectedCouponId.value || undefined,
    };
    // 不传 header tenantId — 用户 token 的 tenant 跟商户 tenant 不一样会冲突
    const res = await request({
      url: '/app-api/merchant/mini/checkout/submit',
      method: 'POST',
      data: reqData,
    });
    uni.hideLoading();
    const orderId = res?.orderId;
    const finalPayPrice = res?.payPrice ?? remainFen.value;
    const promoDeductFen = res?.promoDeductFen || 0;
    const promoDeductCount = res?.promoDeductCount || 0;
    if (!orderId) {
      uni.showModal({ title: '下单失败', content: '未拿到订单号', showCancel: false });
      return;
    }
    if (promoDeductFen > 0) {
      uni.showToast({
        title: `推广积分抵扣 ${promoDeductCount} 件，少付 ¥${(promoDeductFen / 100).toFixed(2)}`,
        icon: 'none',
        duration: 2500,
      });
    }
    const tid = tenantId.value || uni.getStorageSync('lastShopTenantId') || '';
    // 商户未开通在线支付 → 线下转账模式：跳收款码 + 上传凭证页
    if (res?.payMode === 'OFFLINE') {
      uni.showToast({ title: '请扫码付款并上传凭证', icon: 'none', duration: 1500 });
      setTimeout(() => uni.redirectTo({ url: `/pages/order/offline-pay?orderId=${orderId}` }), 800);
      return;
    }
    if (finalPayPrice > 0) {
      // 还需线上支付：拿到通联支付链接直接跳通联；否则 fallback 订单列表"立即付款"
      const cashierUrl = res?.cashierUrl;
      // #ifndef H5
      // 小程序 + App：拉起通联微信小程序收银台（微信原生支付，无 Apple Pay）。
      // 无 window/location，绝不能 location.href（会 "Cannot set property href of undefined"）。
      // App 端 launchMpCashier 内部走开放平台 OpenSDK launchMiniProgram；成功即 return，失败去订单列表重试。
      if (await launchMpCashier(orderId)) return;
      uni.showToast({ title: '拉起收银台失败，请到订单列表重试付款', icon: 'none', duration: 2000 });
      setTimeout(() => uni.reLaunch({ url: '/pages/order/list' }), 1500);
      return;
      // #endif
      // #ifdef H5
      if (cashierUrl) {
        uni.showToast({ title: `跳转通联支付 ¥${(finalPayPrice / 100).toFixed(2)}`, icon: 'none', duration: 1200 });
        setTimeout(() => { location.href = cashierUrl; }, 600);
      } else {
        // 通联未就绪（商户未配 / 接口超时）→ 跳订单列表，用户可点"立即付款"重试
        uni.showToast({
          title: `还需线上支付 ¥${(finalPayPrice / 100).toFixed(2)}（请到订单列表付款）`,
          icon: 'none',
          duration: 2000,
        });
        setTimeout(() => uni.reLaunch({ url: '/pages/order/list' }), 1500);
      }
      // #endif
    } else {
      // 余额抵扣全额或余额+积分付清 → 跳支付完成页
      try {
        uni.setStorageSync('pay-success-amount', res?.payPrice || 0);
        if (promoDeductFen > 0) {
          uni.setStorageSync('pay-success-reward', { amount: promoDeductFen, source: '推广积分抵扣' });
        }
      } catch {}
      setTimeout(() => uni.reLaunch({ url: '/pages/pay-success/index' }), 600);
    }
  } catch (e) {
    uni.hideLoading();
    uni.showModal({ title: '下单失败', content: e?.message || '请稍后再试', showCancel: false });
  }
}

function goBack() { uni.navigateBack(); }

// 把选中的地址结构填进 checkout 状态
function applyAddress(addr) {
  if (!addr || !addr.id) return;
  addressId.value = addr.id;
  receiverName.value = addr.name || '';
  receiverMobile.value = addr.mobile || '';
  receiverAddress.value = `${addr.areaName || ''} ${addr.detailAddress || ''}`.trim();
}

// 拉默认地址（首次进入 & 切到快递时调用）
async function loadDefaultAddress() {
  try {
    const addr = await request({ url: '/app-api/member/address/get-default' });
    if (addr && addr.id) applyAddress(addr);
  } catch {}
}

// 点击地址卡片 → 跳到地址管理页（select 模式）
function pickAddress() {
  uni.navigateTo({
    url: '/pages/address/list?select=1',
    fail: () => uni.showToast({ title: '地址页打开失败', icon: 'none' }),
  });
}

// 切到快递发货 → 没地址就拉默认地址
watch(deliveryType, (v) => {
  if (v === 1 && !addressId.value) loadDefaultAddress();
});

onLoad((q) => {
  tenantId.value = Number(q.tenantId);
  if (q.cartIds) cartIds.value = String(q.cartIds).split(',').map(Number).filter(Boolean);
  if (q.skuId) { skuId.value = Number(q.skuId); count.value = Number(q.count) || 1; }
  if (q.spuId) { spuId.value = Number(q.spuId); }
  loadShopAndItems();
  loadUsableCoupons();
});

// 切回 checkout 时检查地址选择 storage —— 地址页选了/新增了某个地址会写 storage
onShow(() => {
  try {
    const picked = uni.getStorageSync('checkout_picked_address');
    if (picked && picked.id) {
      applyAddress(picked);
      uni.removeStorageSync('checkout_picked_address');
      // 第一次回到 checkout 同时把配送方式切到快递
      if (deliveryType.value !== 1) deliveryType.value = 1;
    } else if (deliveryType.value === 1 && !addressId.value) {
      // 首次进入 & 默认就是快递 → 拉默认地址
      loadDefaultAddress();
    }
  } catch {}
});
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  min-height: 100vh; background: $bg-page; padding-bottom: 200rpx;
}
.safe-top { padding-top: calc(env(safe-area-inset-top) + 16rpx); }
.safe-bottom { padding-bottom: env(safe-area-inset-bottom); }

.topbar {
  display: flex; align-items: center; padding: 16rpx 32rpx;
  background: $bg-card; border-bottom: 1rpx solid $border-color;
}
.topbar .back { font-size: 44rpx; color: $text-primary; padding-right: 16rpx; }
.topbar .title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; color: $text-primary; }

.empty-tip { text-align: center; padding: 80rpx 0; color: $text-placeholder; font-size: 26rpx; }

.ck-addr {
  margin: 24rpx 32rpx; padding: 28rpx;
  background: $bg-card; border-radius: $radius-lg;
  box-shadow: 0 4rpx 16rpx rgba(15,23,42,.04);
  display: flex; align-items: center; gap: 24rpx;
  border-top: 6rpx solid $brand-primary;
}
.ck-addr .ic { font-size: 40rpx; color: $brand-primary; }
.ck-addr .body { flex: 1; }
.ck-addr .row1 { font-size: 28rpx; font-weight: 700; color: $text-primary; }
.ck-addr .row1 .phone { font-size: 24rpx; color: $text-secondary; margin-left: 16rpx; font-weight: 400; }
.ck-addr .row2 { margin-top: 4rpx; font-size: 24rpx; color: $text-secondary; }
.ck-addr .row2.placeholder { color: $text-placeholder; }
.ck-addr .arrow { color: $text-placeholder; font-size: 36rpx; }

.ck-shop {
  margin: 24rpx 32rpx; background: $bg-card;
  border-radius: $radius-lg; box-shadow: 0 4rpx 16rpx rgba(15,23,42,.04);
  overflow: hidden;
}
.ck-shop .gh {
  padding: 24rpx 32rpx; display: flex; align-items: center; gap: 16rpx;
  border-bottom: 1rpx solid $border-color;
}
.ck-shop .gh .pic {
  width: 48rpx; height: 48rpx; border-radius: $radius-sm;
  color: #fff; font-size: 22rpx; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
}
.ck-shop .gh .name { font-size: 28rpx; font-weight: 700; color: $text-primary; }

.pay-options {
  display: flex; padding: 24rpx 32rpx; gap: 16rpx;
}
.pay-option {
  flex: 1; padding: 20rpx; text-align: center;
  background: $bg-page; border-radius: $radius-md;
  font-size: 26rpx; color: $text-secondary;
  border: 2rpx solid transparent;
}
.pay-option.active { background: $brand-primary-light; color: $brand-primary; border-color: $brand-primary; font-weight: 700; }

.ck-row {
  display: flex; gap: 20rpx; padding: 24rpx 32rpx;
  border-bottom: 1rpx solid $border-color;
}
.ck-row .ck-pic {
  width: 112rpx; height: 112rpx; border-radius: $radius-md;
  color: #fff; font-size: 48rpx;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.ck-row .ck-pic-img {
  width: 112rpx; height: 112rpx; border-radius: $radius-md;
  flex-shrink: 0;
  background: $bg-page;
}
.ck-row .info { flex: 1; min-width: 0; }
.ck-row .iname { font-size: 26rpx; color: $text-primary; }
.ck-row .spec { margin-top: 4rpx; font-size: 22rpx; color: $text-placeholder; }
.ck-row .right { text-align: right; flex-shrink: 0; }
.ck-row .price {
  font-size: 28rpx; font-weight: 700; color: $text-primary;
  font-variant-numeric: tabular-nums;
}
.ck-row .qty { font-size: 22rpx; color: $text-placeholder; margin-top: 4rpx; }

/* 直购模式数量步进器 — 圆角胶囊，主色 +/-，达上下限置灰 */
.ck-row .qty-step {
  display: inline-flex; align-items: center;
  margin-top: 8rpx;
  border: 1rpx solid $border-color;
  border-radius: 28rpx;
  overflow: hidden;
  background: $bg-card;
}
.ck-row .qty-step .qs-btn {
  width: 52rpx; height: 52rpx; line-height: 52rpx;
  text-align: center; font-size: 30rpx; font-weight: 700;
  color: $brand-primary;
  background: rgba(255,107,53,.08);
}
.ck-row .qty-step .qs-btn.disabled { color: $text-placeholder; background: $bg-page; }
.ck-row .qty-step .qs-n {
  min-width: 64rpx; text-align: center;
  font-size: 28rpx; font-weight: 600; color: $text-primary;
  font-variant-numeric: tabular-nums;
}

.ck-fee {
  padding: 16rpx 32rpx; display: flex; justify-content: space-between;
  border-bottom: 1rpx solid $border-color;
  font-size: 24rpx; color: $text-secondary;
}
.ck-fee .v { color: $text-primary; font-weight: 600; font-variant-numeric: tabular-nums; }
.ck-fee.total { font-size: 28rpx; padding: 24rpx 32rpx; border-bottom: 0; }
.ck-fee.total .v { color: $brand-primary; font-size: 36rpx; font-weight: 800; }

.remark-input {
  padding: 24rpx 32rpx;
}
.remark-input input {
  width: 100%; height: 60rpx;
  font-size: 26rpx; color: $text-primary;
}

.ck-method {
  margin: 24rpx 32rpx; background: $bg-card;
  border-radius: $radius-lg; box-shadow: 0 4rpx 16rpx rgba(15,23,42,.04);
  overflow: hidden;
}
.ck-method .gh {
  padding: 24rpx 32rpx 8rpx;
  font-size: 26rpx; font-weight: 700; color: $text-primary;
}
.ck-method .gh .sub { font-weight: 400; color: $text-placeholder; font-size: 22rpx; }
.m-row {
  display: flex; align-items: center; padding: 24rpx 32rpx;
  border-top: 1rpx solid $border-color; min-height: 88rpx;
}
.m-row.disabled { opacity: .55; }
.m-row.active { background: rgba(255,107,53,.04); }
.m-icon {
  width: 64rpx; height: 64rpx; border-radius: $radius-md;
  display: flex; align-items: center; justify-content: center;
  font-size: 32rpx; color: #fff;
  margin-right: 20rpx; flex-shrink: 0;
}
.m-icon.i-points { background: rgba(245,158,11,.14); color: #F59E0B; }
.m-icon.i-balance { background: $brand-primary; }
.m-icon.i-wx { background: #07C160; }
.m-icon.i-offline { background: $text-secondary; }
.m-info { flex: 1; min-width: 0; }
.m-name { font-size: 28rpx; font-weight: 600; color: $text-primary; }
.m-sub { margin-top: 4rpx; font-size: 22rpx; color: $text-placeholder; font-variant-numeric: tabular-nums; }
.m-trail { display: flex; align-items: center; gap: 16rpx; }
.m-trail .amt {
  font-size: 24rpx; font-weight: 700; color: $brand-primary;
  font-variant-numeric: tabular-nums;
}
.m-trail .amt.off { color: $text-placeholder; font-weight: 600; }
.switch {
  width: 80rpx; height: 44rpx; border-radius: 22rpx;
  background: #D6D9DF; position: relative;
  transition: all .2s;
}
.switch .dot {
  position: absolute; left: 4rpx; top: 4rpx;
  width: 36rpx; height: 36rpx; border-radius: 50%;
  background: #fff; box-shadow: 0 2rpx 8rpx rgba(0,0,0,.2);
  transition: all .2s;
}
.switch.on { background: $brand-primary; }
.switch.on .dot { left: auto; right: 4rpx; }
.switch.off { background: #D6D9DF; }

.m-fee-final {
  padding: 24rpx 32rpx;
  border-top: 1rpx solid $border-color;
  background: linear-gradient(135deg, #fff5ef, #fff);
  display: flex; justify-content: space-between; align-items: center;
}
.m-fee-final text:first-child { font-size: 24rpx; color: $text-secondary; }
.m-fee-final .v {
  font-size: 36rpx; font-weight: 800; color: $brand-primary;
  font-variant-numeric: tabular-nums;
}

.radio {
  width: 36rpx; height: 36rpx; border-radius: 50%;
  border: 4rpx solid $border-color;
}
.radio.on {
  border-color: $brand-primary; background: $brand-primary;
  position: relative;
}
.radio.on::after {
  content: ''; position: absolute;
  left: 50%; top: 50%; transform: translate(-50%,-50%);
  width: 12rpx; height: 12rpx; border-radius: 50%;
  background: #fff;
}

.form-tip {
  margin: 24rpx 32rpx; padding: 24rpx;
  background: #FFF8EF; color: #B26A00;
  border-left: 6rpx solid $warning;
  border-radius: $radius-md;
  font-size: 24rpx; line-height: 1.7;
}
.form-tip .b { font-weight: 700; }
.form-tip .hl { color: $brand-primary; font-weight: 700; font-variant-numeric: tabular-nums; }

/* 优惠券选择区 — 传统优惠券造型（红橙渐变 + 锯齿分隔 + 选中高亮）*/
.coupon-card {
  margin: 24rpx 32rpx;
  padding: 24rpx;
  background: $bg-card;
  border-radius: $radius-lg;
  box-shadow: 0 4rpx 16rpx rgba(15, 23, 42, .04);
}
.coupon-card .card-title {
  font-size: 28rpx; font-weight: 700; color: $text-primary;
  padding-bottom: 16rpx;
  border-bottom: 1rpx solid $border-color;
}
.coupon-row {
  margin-top: 16rpx;
  display: flex; align-items: stretch;
  background: linear-gradient(135deg, #ff7e5f, #ff6b35);
  border-radius: $radius-md;
  overflow: hidden;
  position: relative;
  box-shadow: 0 4rpx 12rpx rgba(255, 107, 53, .18);
  border: 2rpx solid transparent;
  transition: transform .15s, box-shadow .15s, border-color .15s;
}
.coupon-row.on {
  border-color: #fff;
  box-shadow: 0 0 0 4rpx $brand-primary, 0 8rpx 24rpx rgba(255, 107, 53, .35);
}
.coupon-row.disabled {
  background: linear-gradient(135deg, #d1d5db, #9ca3af);
  box-shadow: none; opacity: .85;
}
.coupon-row .cv-amt {
  width: 200rpx; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 48rpx; font-weight: 800; color: #fff;
  font-variant-numeric: tabular-nums;
  position: relative;
}
.coupon-row .cv-amt::after {
  content: ''; position: absolute;
  right: 0; top: 12rpx; bottom: 12rpx;
  width: 2rpx;
  background-image: radial-gradient(circle, #fff 1.5rpx, transparent 2rpx);
  background-size: 2rpx 12rpx;
  background-repeat: repeat-y;
  opacity: .6;
}
.coupon-row .cv-info {
  flex: 1;
  padding: 20rpx 24rpx; min-width: 0;
  display: flex; flex-direction: column; justify-content: center; gap: 8rpx;
  color: #fff;
}
.coupon-row .cv-name { font-size: 26rpx; font-weight: 600; }
.coupon-row .cv-sub { font-size: 22rpx; opacity: .9; }
.coupon-row .cv-radio {
  width: 64rpx; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 32rpx; color: #fff; font-weight: 700;
}
.coupon-row.on .cv-radio {
  color: #fff;
  background: rgba(255, 255, 255, .22);
}

.bottom-space { height: 40rpx; }

.ck-bottom {
  position: fixed; bottom: 0; left: 0; right: 0;
  background: $bg-card; padding: 24rpx 32rpx;
  padding-bottom: calc(env(safe-area-inset-bottom) + 24rpx);
  box-shadow: 0 -4rpx 32rpx rgba(0,0,0,.06);
  display: flex; align-items: center; gap: 20rpx; z-index: 50;
}
.ck-bottom .total {
  flex: 1; font-size: 24rpx; color: $text-secondary;
}
.ck-bottom .total .b {
  color: $brand-primary; font-size: 40rpx; font-weight: 800;
  font-variant-numeric: tabular-nums;
}
.ck-bottom .pay-btn {
  background: $brand-primary; color: #fff;
  height: 88rpx; padding: 0 56rpx;
  border-radius: 44rpx; line-height: 88rpx;
  font-size: 30rpx; font-weight: 700;
}

/* v8 推广积分抵扣展示卡 */
.promo-deduct-card {
  margin: 24rpx 0;
  padding: 24rpx;
  background: linear-gradient(135deg, #fff5e6, #ffe8d4);
  border-radius: 16rpx;
  border: 1rpx solid #ffae74;
  .pd-head {
    display: flex; justify-content: space-between; align-items: center;
    margin-bottom: 16rpx;
  }
  .pd-title {
    font-size: 30rpx; font-weight: 700; color: #d97706;
  }
  .pd-tag {
    font-size: 26rpx; font-weight: 700;
    color: #fff; background: $brand-primary;
    padding: 4rpx 16rpx; border-radius: 8rpx;
  }
  .pd-row {
    margin-bottom: 12rpx; padding: 12rpx;
    background: rgba(255, 255, 255, 0.6); border-radius: 8rpx;
  }
  .pd-line {
    display: block; font-size: 26rpx; color: $text-primary;
  }
  .pd-em {
    color: $brand-primary; font-weight: 700; font-size: 30rpx;
  }
  .pd-line-sub {
    display: block; font-size: 22rpx; color: $text-secondary;
    margin-top: 4rpx;
  }
  .pd-totals {
    margin-top: 16rpx; padding-top: 16rpx;
    border-top: 1rpx dashed #ffae74;
  }
  .pd-total-row {
    display: flex; justify-content: space-between; align-items: center;
    font-size: 26rpx; padding: 4rpx 0;
  }
  .pd-old {
    text-decoration: line-through; color: $text-secondary;
  }
  .pd-final .pd-new {
    color: $brand-primary; font-size: 36rpx; font-weight: 800;
  }
}

/* 底部"省 ¥X"标签 */
.ck-bottom .total .save-tag {
  margin-left: 12rpx;
  font-size: 22rpx; color: #fff;
  background: #f56c6c;
  padding: 2rpx 12rpx; border-radius: 8rpx;
}
</style>
