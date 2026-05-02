<template>
  <view class="page">
    <view class="topbar safe-top">
      <text class="back" @click="goBack">‹</text>
      <text class="title">订单结算</text>
      <text class="right"></text>
    </view>

    <view v-if="loading" class="empty-tip">加载中...</view>

    <template v-else>
      <!-- 收货地址 -->
      <view class="ck-addr" v-if="deliveryType === 1">
        <view class="ic">📍</view>
        <view class="body">
          <view v-if="addressId" class="row1">
            <text class="name">{{ receiverName || '我' }}</text>
            <text class="phone">{{ receiverMobile || '' }}</text>
          </view>
          <view v-if="addressId" class="row2">{{ receiverAddress || '点击选择地址' }}</view>
          <view v-else class="row2 placeholder">点击选择收货地址</view>
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
          <view class="ck-pic" :style="itemPicStyle(it)">{{ pickEmoji(it) }}</view>
          <view class="info">
            <view class="iname">{{ it.spuName || it.name || '商品' }}</view>
            <view class="spec" v-if="it.skuName">{{ it.skuName }}</view>
          </view>
          <view class="right">
            <view class="price">¥{{ fen2yuan(it.price || 0) }}</view>
            <view class="qty">x {{ it.count || 1 }}</view>
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
        <view class="gh">店铺资产抵扣 <text class="sub">· 可叠加</text></view>
        <!-- 消费积分 -->
        <view class="m-row" v-if="userPoints > 0">
          <view class="m-icon i-points">🪙</view>
          <view class="m-info">
            <view class="m-name">{{ shopName }} · 消费积分</view>
            <view class="m-sub">余 {{ userPoints }} 分（{{ pointPerYuan }} 分=¥1）· 本单最多抵 ¥{{ fen2yuan(maxPointDeductFen) }}</view>
          </view>
          <view class="m-trail">
            <text class="amt" :class="{ off: !usePoints }">{{ usePoints ? `-¥${fen2yuan(pointDeductFen)}` : '未使用' }}</text>
            <view class="switch" :class="{ on: usePoints }" @click="usePoints = !usePoints">
              <view class="dot"></view>
            </view>
          </view>
        </view>
        <!-- 店铺余额抵扣（订单创建后调用 deduct-for-order 幂等扣减） -->
        <view class="m-row" :class="{ disabled: !balanceEnabled }">
          <view class="m-icon i-balance">余</view>
          <view class="m-info">
            <view class="m-name">{{ shopName }} · 店铺余额</view>
            <view class="m-sub" v-if="balanceEnabled">余 ¥{{ fen2yuan(userBalance) }} · 最多抵 ¥{{ fen2yuan(balanceDeductCap) }}</view>
            <view class="m-sub" v-else>该店余额为 0，请先去"我的钱包"充值或推荐好友赚积分</view>
          </view>
          <view class="m-trail">
            <text class="amt" :class="{ off: !useBalance }">{{ useBalance ? `-¥${fen2yuan(balanceDeductFen)}` : '未使用' }}</text>
            <view class="switch" :class="{ on: useBalance && balanceEnabled }" @click="toggleBalance">
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

      <!-- 组合明细 -->
      <view class="form-tip">
        <text class="b">组合支付明细：</text><br>
        · 消费积分抵扣：<text class="hl">{{ usePoints ? `-¥${fen2yuan(pointDeductFen)}` : '未启用' }}</text><br>
        · 店铺余额抵扣：<text class="hl">{{ useBalance ? `-¥${fen2yuan(balanceDeductFen)}` : '未启用' }}</text><br>
        · 在线支付：<text class="hl">¥{{ fen2yuan(remainFen) }}</text>
      </view>

      <view class="bottom-space"></view>

      <!-- 底部 CTA -->
      <view class="ck-bottom safe-bottom">
        <view class="total">合计 ¥{{ fen2yuan(grossFen) }} · 实付 <text class="b">¥{{ fen2yuan(remainFen) }}</text></view>
        <view class="pay-btn" @click="submitOrder">提交订单</view>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
import { onLoad, onShow } from '@dcloudio/uni-app';
import { request } from '../../api/request.js';
import { fen2yuan } from '../../utils/format.js';

const tenantId = ref(null);
const cartIds = ref([]);
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
const shopName = ref('');
const usePoints = ref(false);
const useBalance = ref(false);
const userPoints = ref(0);
const pointPerYuan = ref(100); // 100 分 = ¥1（trade 默认）
const userBalance = ref(0);

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
  const n = it.spuName || it.name || '';
  if (/(地瓜|薯)/.test(n)) return '🍠';
  if (/(玉米)/.test(n)) return '🌽';
  if (/(茶|奶茶)/.test(n)) return '🍵';
  if (/(果|莓|葡萄)/.test(n)) return '🍇';
  if (/(肉|串|烧)/.test(n)) return '🍖';
  if (/(咖啡)/.test(n)) return '☕';
  return '🛍';
}

const itemsFen = computed(() => items.value.reduce((s, i) => s + (i.price || 0) * (i.count || 1), 0));
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
const remainFen = computed(() => Math.max(0, grossFen.value - pointDeductFen.value - balanceDeductFen.value));

async function loadShopAndItems() {
  loading.value = true;
  try {
    // 1. 拉店铺信息
    const shop = await request({ url: `/app-api/merchant/shop/public/info?tenantId=${tenantId.value}` }).catch(() => null);
    shopName.value = shop?.shopName || `店铺 #${tenantId.value}`;
    onlinePayEnabled.value = !!shop?.onlinePayEnabled;
    if (!onlinePayEnabled.value) payType.value = 'offline';
    // 2. 拉用户在该店的资产（积分 / 余额）
    try {
      const rel = await request({ url: '/app-api/merchant/mini/member-rel/my', tenantId: tenantId.value });
      userBalance.value = rel?.balance || 0;
      userPoints.value = rel?.points || 0;
    } catch {}
    // 3. 拉商品（按 cartIds 或 skuId）
    if (cartIds.value.length) {
      const res = await request({ url: '/app-api/trade/cart/list', tenantId: tenantId.value });
      const all = (res && res.validList) || (res && res.list) || (Array.isArray(res) ? res : []);
      items.value = all.filter(i => cartIds.value.includes(i.id));
    } else if (skuId.value) {
      // 单品立即购买
      items.value = [{ skuId: skuId.value, count: count.value, name: '商品', price: 0 }];
    }
  } catch (e) {
    uni.showToast({ title: '加载失败', icon: 'none' });
  } finally {
    loading.value = false;
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
  try {
    uni.showLoading({ title: '提交中...' });
    // 1. 创建订单
    const settleItems = items.value.map(it => ({
      cartId: it.id || undefined,
      skuId: it.skuId || it.sku?.id,
      count: it.count || 1,
    }));
    const data = {
      items: settleItems,
      pointStatus: usePoints.value,
      deliveryType: deliveryType.value,
      addressId: addressId.value || undefined,
      receiverName: receiverName.value || undefined,
      receiverMobile: receiverMobile.value || undefined,
      remark: remark.value || undefined,
    };
    const res = await request({
      url: '/app-api/trade/order/create',
      method: 'POST',
      tenantId: tenantId.value,
      data,
    });
    const orderId = res?.id || res;

    // 2. 若启用店铺余额抵扣 → 后端幂等扣减
    if (useBalance.value && balanceDeductFen.value > 0 && orderId) {
      try {
        await request({
          url: `/app-api/merchant/mini/member-rel/deduct-for-order?tenantId=${tenantId.value}&orderId=${orderId}&amount=${balanceDeductFen.value}`,
          method: 'POST',
        });
      } catch (deductErr) {
        // 余额扣减失败：订单已创建，提示用户改用其它支付方式
        uni.hideLoading();
        uni.showModal({
          title: '余额抵扣失败',
          content: (deductErr?.message || '余额不足或网络异常') + '\n订单已创建，请前往订单列表选择其它支付方式',
          showCancel: false,
        });
        setTimeout(() => uni.redirectTo({ url: '/pages/user-order/list' }), 1500);
        return;
      }
    }
    uni.hideLoading();
    uni.showToast({ title: '订单已创建', icon: 'success' });
    setTimeout(() => uni.redirectTo({ url: '/pages/user-order/list' }), 800);
  } catch (e) {
    uni.hideLoading();
    uni.showModal({ title: '下单失败', content: e?.message || '请稍后再试', showCancel: false });
  }
}

function goBack() { uni.navigateBack(); }

onLoad((q) => {
  tenantId.value = Number(q.tenantId);
  if (q.cartIds) cartIds.value = String(q.cartIds).split(',').map(Number).filter(Boolean);
  if (q.skuId) { skuId.value = Number(q.skuId); count.value = Number(q.count) || 1; }
  loadShopAndItems();
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
.ck-row .info { flex: 1; min-width: 0; }
.ck-row .iname { font-size: 26rpx; color: $text-primary; }
.ck-row .spec { margin-top: 4rpx; font-size: 22rpx; color: $text-placeholder; }
.ck-row .right { text-align: right; flex-shrink: 0; }
.ck-row .price {
  font-size: 28rpx; font-weight: 700; color: $text-primary;
  font-variant-numeric: tabular-nums;
}
.ck-row .qty { font-size: 22rpx; color: $text-placeholder; margin-top: 4rpx; }

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
</style>
